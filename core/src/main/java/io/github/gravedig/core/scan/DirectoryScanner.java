package io.github.gravedig.core.scan;

import io.github.gravedig.core.baseline.BaselineManager;
import io.github.gravedig.core.config.ConfigLoader;
import io.github.gravedig.core.config.GravedigConfig;
import io.github.gravedig.core.entropy.EntropyCalculator;
import io.github.gravedig.core.entropy.EntropyDetector;
import io.github.gravedig.core.filter.FalsePositiveFilter;
import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.FindingPresence;
import io.github.gravedig.core.model.FindingStatus;
import io.github.gravedig.core.model.Rule;
import io.github.gravedig.core.model.ScanResult;
import io.github.gravedig.core.model.ScanSummary;
import io.github.gravedig.core.model.Severity;
import io.github.gravedig.core.util.FingerprintUtil;
import io.github.gravedig.core.util.Redactor;
import io.github.gravedig.core.validator.OfflineValidator;
import io.github.gravedig.core.validator.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

public class DirectoryScanner {
    private static final Logger log = LoggerFactory.getLogger(DirectoryScanner.class);

    private static final String INLINE_IGNORE_MARKER = "gravedig:ignore";
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "ico", "webp", "pdf", "zip", "tar", "gz", "tgz",
            "7z", "jar", "war", "ear", "class", "exe", "dll", "so", "dylib", "bin",
            "mp3", "mp4", "mov", "avi", "woff", "woff2", "ttf", "eot"
    );

    private final List<Rule> rules;
    private final GravedigConfig config;
    private final FalsePositiveFilter falsePositiveFilter;
    private final EntropyDetector entropyDetector;
    private final BaselineManager baselineManager;

    public DirectoryScanner(List<Rule> rules) {
        this(rules, null, null);
    }

    public DirectoryScanner(List<Rule> rules, GravedigConfig config, BaselineManager baselineManager) {
        this.config = config != null ? config : GravedigConfig.defaultConfiguration();
        this.baselineManager = baselineManager != null ? baselineManager : new BaselineManager();

        List<Rule> mergedRules = new ArrayList<>(rules != null ? rules : List.of());
        if (this.config.getRules() != null && !this.config.getRules().isEmpty()) {
            mergedRules.addAll(this.config.getRules());
        }
        this.rules = List.copyOf(mergedRules);

        this.falsePositiveFilter = new FalsePositiveFilter(
                this.config.getIgnorePlaceholders(),
                this.config.getIgnorePaths()
        );

        double entropyThreshold = this.config.getEntropy() != null ? this.config.getEntropy().getThreshold() : 4.0;
        this.entropyDetector = new EntropyDetector(entropyThreshold, this.falsePositiveFilter);
    }

    public ScanResult scan(Path targetPath) throws IOException {
        long startTime = System.currentTimeMillis();
        Path normalizedTarget = targetPath.toAbsolutePath().normalize();

        List<Finding> rawFindings = new ArrayList<>();
        AtomicLong filesScanned = new AtomicLong(0);

        if (!Files.exists(normalizedTarget)) {
            throw new IllegalArgumentException("Path does not exist: " + normalizedTarget);
        }

        if (Files.isRegularFile(normalizedTarget)) {
            scanFile(normalizedTarget, normalizedTarget.getParent(), rawFindings, filesScanned);
        } else {
            Files.walkFileTree(normalizedTarget, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (falsePositiveFilter.isIgnoredFile(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (".git".equals(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        scanFile(file, normalizedTarget, rawFindings, filesScanned);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.warn("Cannot access file: {} ({})", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        // Apply baseline status and grouping
        List<Finding> processedFindings = processFindings(rawFindings);

        long durationMs = Math.max(1, System.currentTimeMillis() - startTime);

        Map<Severity, Integer> severityCounts = new EnumMap<>(Severity.class);
        for (Finding f : processedFindings) {
            severityCounts.merge(f.getSeverity(), 1, Integer::sum);
        }

        ScanSummary summary = new ScanSummary(
                filesScanned.get(),
                0L,
                durationMs,
                processedFindings.size(),
                processedFindings.size(),
                0,
                severityCounts
        );

        return new ScanResult(
                null,
                normalizedTarget.toString(),
                processedFindings,
                summary,
                Instant.now()
        );
    }

    private void scanFile(Path file, Path baseDir, List<Finding> findings, AtomicLong filesScanned) {
        if (!isScannable(file)) {
            return;
        }

        filesScanned.incrementAndGet();
        String relativePath = baseDir != null ? baseDir.relativize(file).toString() : file.toString();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file.toFile()), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.contains(INLINE_IGNORE_MARKER)) {
                    continue;
                }
                scanLine(line, relativePath, lineNumber, findings);
            }
        } catch (Exception e) {
            log.debug("Skipping unreadable or non-UTF-8 file {}: {}", file, e.getMessage());
        }
    }

    private void scanLine(String line, String filePath, int lineNumber, List<Finding> findings) {
        // 1. Regex rule checks
        for (Rule rule : rules) {
            if (!rule.matchesFastCheck(line)) {
                continue;
            }

            Matcher matcher = rule.getCompiledPattern().matcher(line);
            while (matcher.find()) {
                String rawSecret;
                int col;
                if (matcher.groupCount() >= 1 && matcher.group(1) != null) {
                    rawSecret = matcher.group(1);
                    col = matcher.start(1) + 1;
                } else {
                    rawSecret = matcher.group();
                    col = matcher.start() + 1;
                }

                // Check placeholders
                if (falsePositiveFilter.isPlaceholder(rawSecret)) {
                    continue;
                }

                // Check offline validator
                ValidationResult validation = OfflineValidator.validate(rule.getId(), rawSecret);
                if (validation.shouldDrop()) {
                    continue;
                }

                String fingerprint = FingerprintUtil.calculateFingerprint(rawSecret);

                // Check allowlist
                if (config.getAllowlist().isAllowed(fingerprint, filePath, rule.getId())) {
                    continue;
                }

                double entropy = EntropyCalculator.calculate(rawSecret);
                int riskScore = calculateRiskScore(rule.getSeverity(), FindingPresence.ALIVE, validation.getConfidence(), entropy);

                String redactedSecret = Redactor.redact(rawSecret);
                String snippet = Redactor.redactSnippet(line, rawSecret);

                Finding finding = Finding.builder()
                        .ruleId(rule.getId())
                        .ruleName(rule.getName())
                        .severity(rule.getSeverity())
                        .filePath(filePath)
                        .lineNumber(lineNumber)
                        .columnNumber(col)
                        .secretRedacted(redactedSecret)
                        .snippetRedacted(snippet)
                        .fingerprint(fingerprint)
                        .entropy(entropy)
                        .validationReason(validation.getReason())
                        .riskScore(riskScore)
                        .presence(FindingPresence.ALIVE)
                        .status(FindingStatus.OPEN)
                        .description(rule.getDescription())
                        .remediation(rule.getRemediation())
                        .build();

                findings.add(finding);
            }
        }

        // 2. Entropy detector checks for unformatted tokens
        if (config.getEntropy() != null && config.getEntropy().isEnabled()) {
            List<Finding> entropyFindings = entropyDetector.scanLineForEntropy(line, filePath, lineNumber);
            for (Finding ef : entropyFindings) {
                boolean alreadyCovered = findings.stream().anyMatch(existing ->
                        existing.getFilePath().equals(filePath) &&
                        existing.getLineNumber() == lineNumber &&
                        existing.getFingerprint().equals(ef.getFingerprint())
                );
                if (!alreadyCovered && !config.getAllowlist().isAllowed(ef.getFingerprint(), filePath, ef.getRuleId())) {
                    findings.add(ef);
                }
            }
        }
    }

    private List<Finding> processFindings(List<Finding> findings) {
        List<Finding> processed = new ArrayList<>();
        Map<String, Integer> fingerprintCounts = new HashMap<>();

        for (Finding f : findings) {
            fingerprintCounts.merge(f.getFingerprint(), 1, Integer::sum);
        }

        for (Finding f : findings) {
            FindingStatus status = f.getStatus();
            if (baselineManager.isBaselined(f)) {
                status = FindingStatus.ACCEPTED_RISK;
            }

            int count = fingerprintCounts.getOrDefault(f.getFingerprint(), 1);

            Finding updated = Finding.builder()
                    .id(f.getId())
                    .ruleId(f.getRuleId())
                    .ruleName(f.getRuleName())
                    .severity(f.getSeverity())
                    .filePath(f.getFilePath())
                    .lineNumber(f.getLineNumber())
                    .columnNumber(f.getColumnNumber())
                    .secretRedacted(f.getSecretRedacted())
                    .snippetRedacted(f.getSnippetRedacted())
                    .fingerprint(f.getFingerprint())
                    .entropy(f.getEntropy())
                    .validationReason(f.getValidationReason())
                    .riskScore(f.getRiskScore())
                    .occurrences(count)
                    .presence(f.getPresence())
                    .status(status)
                    .commitHash(f.getCommitHash())
                    .commitAuthor(f.getCommitAuthor())
                    .commitDate(f.getCommitDate())
                    .branch(f.getBranch())
                    .description(f.getDescription())
                    .remediation(f.getRemediation())
                    .detectedAt(f.getDetectedAt())
                    .build();

            processed.add(updated);
        }

        // Sort by risk score descending
        processed.sort((a, b) -> Integer.compare(b.getRiskScore(), a.getRiskScore()));
        return processed;
    }

    private int calculateRiskScore(Severity severity, FindingPresence presence, double confidence, double entropy) {
        int base;
        switch (severity) {
            case CRITICAL: base = 85; break;
            case HIGH: base = 70; break;
            case MEDIUM: base = 50; break;
            case LOW:
            default: base = 30; break;
        }

        if (presence == FindingPresence.ALIVE) {
            base += 10;
        }

        int score = (int) Math.round(base * Math.min(1.0, Math.max(0.4, confidence)));
        return Math.max(1, Math.min(100, score));
    }

    private boolean isScannable(Path file) {
        try {
            long size = Files.size(file);
            if (size == 0 || size > MAX_FILE_SIZE_BYTES) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }

        if (falsePositiveFilter.isIgnoredFile(file)) {
            return false;
        }

        String fileName = file.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            String ext = fileName.substring(dotIndex + 1).toLowerCase();
            if (BINARY_EXTENSIONS.contains(ext)) {
                return false;
            }
        }

        return !isBinaryFile(file);
    }

    private boolean isBinaryFile(Path file) {
        byte[] buffer = new byte[8192];
        try (InputStream in = new FileInputStream(file.toFile())) {
            int read = in.read(buffer);
            if (read <= 0) {
                return false;
            }
            for (int i = 0; i < read; i++) {
                if (buffer[i] == 0) {
                    return true;
                }
            }
        } catch (IOException e) {
            return true;
        }
        return false;
    }
}
