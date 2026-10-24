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
import io.github.gravedig.core.model.FindingStatus;
import io.github.gravedig.core.model.Rule;
import io.github.gravedig.core.model.ScanResult;
import io.github.gravedig.core.rule.RuleLoader;
import io.github.gravedig.core.scan.DirectoryScanner;
import io.github.gravedig.server.db.FindingDao;
import io.github.gravedig.server.db.ScanDao;
import io.github.gravedig.server.model.FindingFilter;
import io.github.gravedig.server.model.ScanEvent;
import io.github.gravedig.server.model.ScanRecord;
import io.github.gravedig.server.model.ScanRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
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

            // Stream any discovered findings
            if (result.getFindings() != null) {
                int count = 0;
                for (Finding f : result.getFindings()) {
                    count++;
                    broadcast(ScanEvent.finding(scanId, f, count));
                }
            }

            // Mark scan completed
            String completedAt = Instant.now().toString();
            int maxRisk = (result.getFindings() != null)
                    ? result.getFindings().stream().mapToInt(Finding::getRiskScore).max().orElse(0)
                    : 0;

            scanDao.complete(
                    scanId,
                    completedAt,
                    (int) result.getSummary().getFilesScanned(),
                    (int) result.getSummary().getCommitsScanned(),
                    result.getSummary().getTotalFindings(),
                    maxRisk,
                    result.getSummary()
            );

            broadcast(ScanEvent.completed(scanId, result.getSummary()));
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
        ScanResult result = new ScanResult(
                scanId,
                scan.getTargetPath(),
                findings,
                scan.getSummary(),
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
