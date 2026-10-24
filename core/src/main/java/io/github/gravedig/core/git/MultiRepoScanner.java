package io.github.gravedig.core.git;

import io.github.gravedig.core.baseline.BaselineManager;
import io.github.gravedig.core.config.GravedigConfig;
import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.Rule;
import io.github.gravedig.core.model.ScanResult;
import io.github.gravedig.core.model.ScanSummary;
import io.github.gravedig.core.model.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class MultiRepoScanner {
    private static final Logger log = LoggerFactory.getLogger(MultiRepoScanner.class);

    private final List<Rule> rules;
    private final GravedigConfig config;
    private final BaselineManager baselineManager;
    private final int threads;

    public MultiRepoScanner(List<Rule> rules, GravedigConfig config, BaselineManager baselineManager, int threads) {
        this.rules = rules;
        this.config = config;
        this.baselineManager = baselineManager;
        this.threads = threads;
    }

    public static List<Path> discoverGitRepositories(Path root, int maxDepth) throws IOException {
        List<Path> repos = new ArrayList<>();
        if (Files.exists(root.resolve(".git"))) {
            repos.add(root);
            return repos;
        }

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            int depth = 0;

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(root)) return FileVisitResult.CONTINUE;
                if (Files.exists(dir.resolve(".git"))) {
                    repos.add(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                if (name.startsWith(".") || "node_modules".equals(name) || "target".equals(name)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                depth++;
                if (depth > maxDepth) {
                    depth--;
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                if (!dir.equals(root)) depth--;
                return FileVisitResult.CONTINUE;
            }
        });

        return repos;
    }

    public ScanResult scanAll(Path parentDir) throws IOException {
        long start = System.currentTimeMillis();
        List<Path> repos = discoverGitRepositories(parentDir, 3);
        if (repos.isEmpty()) {
            throw new IllegalArgumentException("No git repositories found under: " + parentDir);
        }

        List<Finding> combinedFindings = new ArrayList<>();
        long totalFiles = 0;
        long totalCommits = 0;
        int aliveCount = 0;
        int buriedCount = 0;
        Map<Severity, Integer> severityCounts = new EnumMap<>(Severity.class);

        GitHistoryScanner scanner = new GitHistoryScanner(rules, config, baselineManager, threads);

        for (Path repo : repos) {
            try {
                ScanResult res = scanner.scanRepository(repo);
                String repoName = repo.getFileName().toString();

                for (Finding f : res.getFindings()) {
                    Finding prefixed = Finding.builder()
                            .id(f.getId())
                            .ruleId(f.getRuleId())
                            .ruleName(f.getRuleName())
                            .severity(f.getSeverity())
                            .filePath(repoName + "/" + f.getFilePath())
                            .lineNumber(f.getLineNumber())
                            .columnNumber(f.getColumnNumber())
                            .secretRedacted(f.getSecretRedacted())
                            .snippetRedacted(f.getSnippetRedacted())
                            .fingerprint(f.getFingerprint())
                            .entropy(f.getEntropy())
                            .validationReason(f.getValidationReason())
                            .riskScore(f.getRiskScore())
                            .occurrences(f.getOccurrences())
                            .occurrencesList(f.getOccurrencesList())
                            .exposureWindow(f.getExposureWindow())
                            .riskBreakdown(f.getRiskBreakdown())
                            .presence(f.getPresence())
                            .status(f.getStatus())
                            .commitHash(f.getCommitHash())
                            .commitAuthor(f.getCommitAuthor())
                            .commitDate(f.getCommitDate())
                            .branch(f.getBranch())
                            .description(f.getDescription())
                            .remediation(f.getRemediation())
                            .detectedAt(f.getDetectedAt())
                            .build();

                    combinedFindings.add(prefixed);
                }

                totalFiles += res.getSummary().getFilesScanned();
                totalCommits += res.getSummary().getCommitsScanned();
                aliveCount += res.getSummary().getAliveFindings();
                buriedCount += res.getSummary().getBuriedFindings();

                for (Map.Entry<Severity, Integer> entry : res.getSummary().getSeverityCounts().entrySet()) {
                    severityCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            } catch (Exception e) {
                log.warn("Failed scanning repo {}: {}", repo, e.getMessage());
            }
        }

        combinedFindings.sort((a, b) -> Integer.compare(b.getRiskScore(), a.getRiskScore()));

        ScanSummary summary = new ScanSummary(
                totalFiles,
                totalCommits,
                Math.max(1, System.currentTimeMillis() - start),
                combinedFindings.size(),
                aliveCount,
                buriedCount,
                severityCounts
        );

        return new ScanResult(
                null,
                parentDir.toAbsolutePath().normalize().toString() + " (" + repos.size() + " repositories)",
                combinedFindings,
                summary,
                Instant.now()
        );
    }
}
