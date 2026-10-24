package io.github.gravedig.server.service;

import io.github.gravedig.core.baseline.BaselineManager;
import io.github.gravedig.core.config.ConfigLoader;
import io.github.gravedig.core.config.GravedigConfig;
import io.github.gravedig.core.formatter.CsvOutputFormatter;
import io.github.gravedig.core.formatter.HtmlReportFormatter;
import io.github.gravedig.core.formatter.JsonOutputFormatter;
import io.github.gravedig.core.formatter.SarifOutputFormatter;
import io.github.gravedig.core.git.GitHistoryScanner;
import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.FindingPresence;
import io.github.gravedig.core.model.FindingStatus;
import io.github.gravedig.core.model.Rule;
import io.github.gravedig.core.model.ScanResult;
import io.github.gravedig.core.model.ScanSummary;
import io.github.gravedig.core.model.Severity;
import io.github.gravedig.core.rule.RuleLoader;
import io.github.gravedig.core.scan.DirectoryScanner;
import io.github.gravedig.server.db.FindingDao;
import io.github.gravedig.server.db.ScanDao;
import io.github.gravedig.server.model.FindingFilter;
import io.github.gravedig.server.model.ScanEvent;
import io.github.gravedig.server.model.ScanRecord;
import io.github.gravedig.server.model.ScanRequest;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ScanService implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final ScanDao scanDao;
    private final FindingDao findingDao;
    private final List<Rule> defaultRules;
    private final ExecutorService executor;
    private final Map<String, Set<Consumer<ScanEvent>>> eventListeners = new ConcurrentHashMap<>();

    public ScanService(ScanDao scanDao, FindingDao findingDao) {
        this(scanDao, findingDao, Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors())));
    }

    public ScanService(ScanDao scanDao, FindingDao findingDao, ExecutorService executor) {
        this.scanDao = scanDao;
        this.findingDao = findingDao;
        this.executor = executor;
        this.defaultRules = new RuleLoader().loadDefaultRules();
    }

    public List<Rule> getRules() {
        return defaultRules;
    }

    public ScanRecord startScan(ScanRequest request) {
        if (request.getPath() == null || request.getPath().isBlank()) {
            throw new IllegalArgumentException("Path must not be empty");
        }

        Path target = Paths.get(request.getPath()).toAbsolutePath().normalize();
        if (!Files.exists(target) || !Files.isDirectory(target)) {
            throw new IllegalArgumentException("Target directory does not exist: " + target);
        }

        boolean isGitRepo = Files.exists(target.resolve(".git"));
        String mode = (isGitRepo && request.isIncludeHistory()) ? "FULL_HISTORY" : "WORKTREE";
        String scanId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        ScanRecord record = new ScanRecord(scanId, target.toString(), mode, "RUNNING", now);
        scanDao.insert(record);

        executor.submit(() -> executeScan(record, target, isGitRepo, request));

        return record;
    }

    private void executeScan(ScanRecord record, Path target, boolean isGitRepo, ScanRequest request) {
        String scanId = record.getId();
        try {
            log.info("Starting scan {} on {} (mode={})", scanId, target, record.getScanMode());
            broadcast(ScanEvent.progress(scanId, 0, 0, 0));

            // Load optional repo configuration
            GravedigConfig config = new ConfigLoader().findAndLoad(target);
            BaselineManager baselineManager = new BaselineManager();

            ScanResult result;
            if (isGitRepo && request.isIncludeHistory()) {
                GitHistoryScanner scanner = new GitHistoryScanner(
                        defaultRules,
                        config,
                        baselineManager,
                        Math.max(1, Runtime.getRuntime().availableProcessors())
                );
                result = scanner.scanRepository(target);
            } else {
                DirectoryScanner scanner = new DirectoryScanner(
                        defaultRules,
                        config,
                        baselineManager
                );
                result = scanner.scan(target);
            }

            // Save findings to database
            findingDao.insertBatch(scanId, result.getFindings());

            int maxRisk = (result.getFindings() != null)
                    ? result.getFindings().stream().mapToInt(Finding::getRiskScore).max().orElse(0)
                    : 0;

            // Stream any discovered findings
            if (result.getFindings() != null) {
                int count = 0;
                int runningRisk = 0;
                for (Finding f : result.getFindings()) {
                    count++;
                    runningRisk = Math.max(runningRisk, f.getRiskScore());
                    broadcast(ScanEvent.finding(scanId, f, count, runningRisk));
                }
            }

            // Mark scan completed
            String completedAt = Instant.now().toString();

            scanDao.complete(
                    scanId,
                    completedAt,
                    (int) result.getSummary().getFilesScanned(),
                    (int) result.getSummary().getCommitsScanned(),
                    result.getSummary().getTotalFindings(),
                    maxRisk,
                    result.getSummary()
            );

            broadcast(ScanEvent.completed(scanId, result.getSummary(), maxRisk));
            log.info("Completed scan {}: {} findings found", scanId, result.getSummary().getTotalFindings());

        } catch (Exception e) {
            log.error("Scan failed: {}", scanId, e);
            String completedAt = Instant.now().toString();
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            scanDao.fail(scanId, completedAt, message);
            broadcast(ScanEvent.error(scanId, message));
        }
    }

    public Optional<ScanRecord> getScan(String scanId) {
        return scanDao.findById(scanId);
    }

    public List<ScanRecord> listScans(int limit) {
        return scanDao.listRecent(limit);
    }

    public List<Finding> getFindings(String scanId, FindingFilter filter) {
        return findingDao.findByScanId(scanId, filter);
    }

    public Optional<Finding> getFinding(String findingId) {
        return findingDao.findById(findingId);
    }

    public boolean updateFindingStatus(String findingId, FindingStatus status) {
        return findingDao.updateStatus(findingId, status);
    }

    public Optional<String> exportScan(String scanId, String format) {
        Optional<ScanRecord> scanOpt = scanDao.findById(scanId);
        if (scanOpt.isEmpty()) {
            return Optional.empty();
        }

        ScanRecord scan = scanOpt.get();
        List<Finding> findings = findingDao.findByScanId(scanId, null);
        ScanSummary summary = scan.getSummary();
        if (summary == null) {
            Map<Severity, Integer> counts = new EnumMap<>(Severity.class);
            int alive = 0;
            int buried = 0;
            for (Finding f : findings) {
                counts.merge(f.getSeverity(), 1, Integer::sum);
                if (f.getPresence() == FindingPresence.ALIVE) alive++;
                else buried++;
            }
            summary = new ScanSummary(
                    scan.getTotalFiles(),
                    scan.getTotalCommits(),
                    0,
                    findings.size(),
                    alive,
                    buried,
                    counts
            );
        }

        ScanResult result = new ScanResult(
                scanId,
                scan.getTargetPath(),
                findings,
                summary,
                Instant.now()
        );

        String fmt = (format != null) ? format.toLowerCase().trim() : "json";
        switch (fmt) {
            case "sarif":
                return Optional.of(SarifOutputFormatter.format(result));
            case "html":
                return Optional.of(HtmlReportFormatter.format(result));
            case "csv":
                return Optional.of(CsvOutputFormatter.format(result));
            case "json":
            default:
                return Optional.of(JsonOutputFormatter.format(result));
        }
    }

    public Optional<Map<String, Object>> saveExportToFile(String scanId, String format, String destinationDir) {
        Optional<ScanRecord> scanOpt = scanDao.findById(scanId);
        if (scanOpt.isEmpty()) {
            return Optional.empty();
        }

        ScanRecord scan = scanOpt.get();
        String fmt = (format != null) ? format.toLowerCase().trim() : "json";
        Optional<String> contentOpt = exportScan(scanId, fmt);
        if (contentOpt.isEmpty()) {
            return Optional.empty();
        }

        String content = contentOpt.get();

        // Determine destination folder
        Path destFolder;
        if (destinationDir != null && !destinationDir.isBlank()) {
            destFolder = Paths.get(destinationDir);
        } else {
            if (scan.getTargetPath() != null && Files.isDirectory(Paths.get(scan.getTargetPath()))) {
                destFolder = Paths.get(scan.getTargetPath());
            } else {
                String home = System.getProperty("user.home", ".");
                Path downloads = Paths.get(home, "Downloads");
                destFolder = Files.exists(downloads) ? downloads : Paths.get(home);
            }
        }

        try {
            if (!Files.exists(destFolder)) {
                Files.createDirectories(destFolder);
            }
        } catch (Exception ignored) {}

        String ext = switch (fmt) {
            case "sarif" -> "sarif.json";
            case "html" -> "html";
            case "csv" -> "csv";
            default -> "json";
        };

        String shortId = scanId.length() >= 8 ? scanId.substring(0, 8) : scanId;
        String filename = "gravedig-report-" + shortId + "." + ext;
        Path targetFile = destFolder.resolve(filename);

        try {
            Files.writeString(targetFile, content, StandardCharsets.UTF_8);
            long bytes = Files.size(targetFile);
            log.info("Saved scan export ({}) to {}", fmt, targetFile.toAbsolutePath());
            return Optional.of(Map.of(
                    "success", true,
                    "filePath", targetFile.toAbsolutePath().toString(),
                    "filename", filename,
                    "format", fmt,
                    "size", bytes,
                    "content", content
            ));
        } catch (IOException e) {
            log.error("Failed to write scan export to file: {}", targetFile, e);
            throw new RuntimeException("Failed to write export file: " + e.getMessage(), e);
        }
    }

    public boolean deleteScan(String scanId) {
        Optional<ScanRecord> scan = scanDao.findById(scanId);
        if (scan.isEmpty()) {
            return false;
        }
        findingDao.deleteByScanId(scanId);
        scanDao.delete(scanId);
        eventListeners.remove(scanId);
        log.info("Deleted scan {} and associated findings", scanId);
        return true;
    }

    public void clearAllScans() {
        findingDao.deleteAll();
        scanDao.deleteAll();
        eventListeners.clear();
        log.info("Cleared all scans and findings from dashboard database");
    }

    public Optional<Map<String, String>> revealFinding(String findingId) {
        Optional<Finding> findingOpt = findingDao.findById(findingId);
        if (findingOpt.isEmpty()) {
            return Optional.empty();
        }
        Finding f = findingOpt.get();

        // If secretRaw is already clean and does not contain asterisks, return it directly
        if (f.getSecretRaw() != null && !f.getSecretRaw().contains("*") && !f.getSecretRaw().isBlank()
                && f.getLineContent() != null && !f.getLineContent().contains("***") && !f.getLineContent().isBlank()) {
            return Optional.of(Map.of(
                    "id", f.getId(),
                    "secretRaw", f.getSecretRaw(),
                    "lineContent", f.getLineContent()
            ));
        }

        // Recover from original source file or git commit
        String scanId = findingDao.findScanIdByFindingId(findingId).orElse(null);
        String targetPathStr = null;
        if (scanId != null) {
            Optional<ScanRecord> scan = scanDao.findById(scanId);
            if (scan.isPresent()) {
                targetPathStr = scan.get().getTargetPath();
            }
        }

        String realSecret = null;
        String realLine = null;

        // 1. Try reading from disk (working tree)
        if (targetPathStr != null || f.getFilePath() != null) {
            Path filePath = null;
            if (targetPathStr != null) {
                Path candidate = Paths.get(targetPathStr).resolve(f.getFilePath());
                if (Files.exists(candidate)) {
                    filePath = candidate;
                }
            }
            if (filePath == null && Paths.get(f.getFilePath()).isAbsolute() && Files.exists(Paths.get(f.getFilePath()))) {
                filePath = Paths.get(f.getFilePath());
            }

            if (filePath != null && Files.isRegularFile(filePath)) {
                try {
                    List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
                    int lineIdx = f.getLineNumber() - 1;
                    if (lineIdx >= 0 && lineIdx < lines.size()) {
                        realLine = lines.get(lineIdx);
                        Rule rule = defaultRules.stream().filter(r -> r.getId().equals(f.getRuleId())).findFirst().orElse(null);
                        if (rule != null) {
                            java.util.regex.Matcher m = rule.getCompiledPattern().matcher(realLine);
                            if (m.find()) {
                                realSecret = (m.groupCount() >= 1 && m.group(1) != null) ? m.group(1) : m.group();
                            }
                        }
                        if (realSecret == null) {
                            realSecret = realLine.trim();
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not read file from disk for reveal: {}", e.getMessage());
                }
            }
        }

        // 2. Try reading from Git history if not found or buried
        if (realSecret == null && targetPathStr != null && f.getCommitHash() != null) {
            try {
                File gitDir = new File(targetPathStr, ".git");
                if (gitDir.exists()) {
                    try (Repository repo = new FileRepositoryBuilder().setGitDir(gitDir).readEnvironment().findGitDir().build();
                         RevWalk revWalk = new RevWalk(repo)) {
                        ObjectId commitId = repo.resolve(f.getCommitHash());
                        if (commitId != null) {
                            RevCommit commit = revWalk.parseCommit(commitId);
                            try (TreeWalk treeWalk = TreeWalk.forPath(repo, f.getFilePath(), commit.getTree())) {
                                if (treeWalk != null) {
                                    ObjectId blobId = treeWalk.getObjectId(0);
                                    ObjectLoader loader = repo.open(blobId);
                                    String content = new String(loader.getBytes(), StandardCharsets.UTF_8);
                                    String[] lines = content.split("\r?\n");
                                    int lineIdx = f.getLineNumber() - 1;
                                    if (lineIdx >= 0 && lineIdx < lines.length) {
                                        realLine = lines[lineIdx];
                                        Rule rule = defaultRules.stream().filter(r -> r.getId().equals(f.getRuleId())).findFirst().orElse(null);
                                        if (rule != null) {
                                            java.util.regex.Matcher m = rule.getCompiledPattern().matcher(realLine);
                                            if (m.find()) {
                                                realSecret = (m.groupCount() >= 1 && m.group(1) != null) ? m.group(1) : m.group();
                                            }
                                        }
                                        if (realSecret == null) {
                                            realSecret = realLine.trim();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Could not read git commit for reveal: {}", e.getMessage());
            }
        }

        if (realSecret != null && realLine != null) {
            findingDao.updateRawSecret(findingId, realSecret, realLine.trim());
            return Optional.of(Map.of(
                    "id", f.getId(),
                    "secretRaw", realSecret,
                    "lineContent", realLine.trim()
            ));
        }

        return Optional.of(Map.of(
                "id", f.getId(),
                "secretRaw", f.getSecretRaw() != null ? f.getSecretRaw() : f.getSecretRedacted(),
                "lineContent", f.getLineContent() != null ? f.getLineContent() : f.getSnippetRedacted()
        ));
    }

    public void addListener(String scanId, Consumer<ScanEvent> listener) {
        eventListeners.computeIfAbsent(scanId, k -> ConcurrentHashMap.newKeySet()).add(listener);
    }

    public void removeListener(String scanId, Consumer<ScanEvent> listener) {
        Set<Consumer<ScanEvent>> set = eventListeners.get(scanId);
        if (set != null) {
            set.remove(listener);
            if (set.isEmpty()) {
                eventListeners.remove(scanId);
            }
        }
    }

    private void broadcast(ScanEvent event) {
        Set<Consumer<ScanEvent>> listeners = eventListeners.get(event.getScanId());
        if (listeners != null) {
            for (Consumer<ScanEvent> listener : listeners) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    log.warn("Error notifying listener for scan {}", event.getScanId(), e);
                }
            }
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
