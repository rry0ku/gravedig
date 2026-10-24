package io.github.gravedig.server;

import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.FindingPresence;
import io.github.gravedig.core.model.FindingStatus;
import io.github.gravedig.core.model.ScanSummary;
import io.github.gravedig.core.model.Severity;
import io.github.gravedig.server.db.DatabaseManager;
import io.github.gravedig.server.db.FindingDao;
import io.github.gravedig.server.db.ScanDao;
import io.github.gravedig.server.model.FindingFilter;
import io.github.gravedig.server.model.ScanRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScanDaoAndFindingDaoTest {

    private DatabaseManager db;
    private ScanDao scanDao;
    private FindingDao findingDao;

    @BeforeEach
    void setUp() {
        db = DatabaseManager.createInMemory();
        scanDao = new ScanDao(db);
        findingDao = new FindingDao(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    @DisplayName("Persist and query scan records")
    void testScanLifecycle() {
        String scanId = "scan-123";
        ScanRecord record = new ScanRecord(scanId, "/path/to/project", "FULL_HISTORY", "RUNNING", Instant.now().toString());
        scanDao.insert(record);

        Optional<ScanRecord> fetched = scanDao.findById(scanId);
        assertThat(fetched).isPresent();
        assertThat(fetched.get().getStatus()).isEqualTo("RUNNING");
        assertThat(fetched.get().getTargetPath()).isEqualTo("/path/to/project");

        scanDao.updateProgress(scanId, 100, 20, 2);
        Optional<ScanRecord> inProgress = scanDao.findById(scanId);
        assertThat(inProgress.get().getTotalFiles()).isEqualTo(100);
        assertThat(inProgress.get().getTotalCommits()).isEqualTo(20);

        ScanSummary summary = new ScanSummary(100, 20, 1500, 2, 1, 1, Map.of(Severity.CRITICAL, 1, Severity.HIGH, 1));
        scanDao.complete(scanId, Instant.now().toString(), 100, 20, 2, 95, summary);

        Optional<ScanRecord> completed = scanDao.findById(scanId);
        assertThat(completed.get().getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.get().getRiskScore()).isEqualTo(95);
        assertThat(completed.get().getSummary()).isNotNull();
        assertThat(completed.get().getSummary().getAliveFindings()).isEqualTo(1);
    }

    @Test
    @DisplayName("Persist, filter, and patch findings")
    void testFindingOperations() {
        String scanId = "scan-findings-test";
        ScanRecord record = new ScanRecord(scanId, "/repo", "WORKTREE", "RUNNING", Instant.now().toString());
        scanDao.insert(record);

        Finding f1 = new Finding(
                "f-1", "aws-access-key-id", "AWS Access Key ID", Severity.HIGH,
                "config/aws.env", 12, 5, "AKIA****************", "export AKIA****************",
                "fp-1", 4.2, "Valid AWS Key", 85, 1, Collections.emptyList(),
                null, null, FindingPresence.ALIVE, FindingStatus.OPEN,
                "abc1234", "alice", Instant.now(), "main", "AWS credentials", "Rotate immediately", Instant.now()
        );

        Finding f2 = new Finding(
                "f-2", "slack-webhook", "Slack Incoming Webhook", Severity.MEDIUM,
                "src/alerts.js", 45, 10, "https://hooks.slack.com/services/*********", "url = 'https://hooks.slack.com/services/*********'",
                "fp-2", 3.8, "", 50, 1, Collections.emptyList(),
                null, null, FindingPresence.BURIED, FindingStatus.OPEN,
                "def5678", "bob", Instant.now(), "main", "Slack webhook", "Revoke webhook", Instant.now()
        );

        findingDao.insertBatch(scanId, List.of(f1, f2));

        // Filter all
        List<Finding> all = findingDao.findByScanId(scanId, null);
        assertThat(all).hasSize(2);

        // Filter by severity
        FindingFilter highFilter = new FindingFilter();
        highFilter.setSeverity("HIGH");
        List<Finding> highFindings = findingDao.findByScanId(scanId, highFilter);
        assertThat(highFindings).hasSize(1);
        assertThat(highFindings.get(0).getId()).isEqualTo("f-1");

        // Filter by presence
        FindingFilter buriedFilter = new FindingFilter();
        buriedFilter.setPresence("BURIED");
        List<Finding> buriedFindings = findingDao.findByScanId(scanId, buriedFilter);
        assertThat(buriedFindings).hasSize(1);
        assertThat(buriedFindings.get(0).getId()).isEqualTo("f-2");

        // Patch finding status
        boolean updated = findingDao.updateStatus("f-1", FindingStatus.FALSE_POSITIVE);
        assertThat(updated).isTrue();

        Optional<Finding> updatedFinding = findingDao.findById("f-1");
        assertThat(updatedFinding).isPresent();
        assertThat(updatedFinding.get().getStatus()).isEqualTo(FindingStatus.FALSE_POSITIVE);
    }

    @Test
    @DisplayName("Finding preserves raw secrets, masked secrets, and line snippets")
    void testFindingSecretFields() throws Exception {
        Finding finding = Finding.builder()
                .id("f-reveal-test")
                .ruleId("aws-key")
                .ruleName("AWS Key")
                .severity(Severity.CRITICAL)
                .filePath("src/main.py")
                .lineNumber(10)
                .columnNumber(5)
                .secretRaw("AKIAIOSFODNN7EXAMPLE")
                .secretRedacted("AKIA****************")
                .lineContent("aws_key = \"AKIAIOSFODNN7EXAMPLE\"")
                .snippetRedacted("aws_key = \"AKIA****************\"")
                .build();

        assertThat(finding.getSecretRaw()).isEqualTo("AKIAIOSFODNN7EXAMPLE");
        assertThat(finding.getSecretRedacted()).isEqualTo("AKIA****************");
        assertThat(finding.getSecretMasked()).isEqualTo("AKIA****************");
        assertThat(finding.getLineContent()).isEqualTo("aws_key = \"AKIAIOSFODNN7EXAMPLE\"");
        assertThat(finding.getSnippetRedacted()).isEqualTo("aws_key = \"AKIA****************\"");

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        String json = mapper.writeValueAsString(finding);

        assertThat(json).contains("\"secretRaw\":\"AKIAIOSFODNN7EXAMPLE\"");
        assertThat(json).contains("\"secretMasked\":\"AKIA****************\"");
        assertThat(json).contains("\"secretRedacted\":\"AKIA****************\"");
        assertThat(json).contains("\"lineContent\":\"aws_key = \\\"AKIAIOSFODNN7EXAMPLE\\\"\"");
        assertThat(json).contains("\"snippetRedacted\":\"aws_key = \\\"AKIA****************\\\"\"");

        Finding deserialized = mapper.readValue(json, Finding.class);
        assertThat(deserialized.getSecretRaw()).isEqualTo("AKIAIOSFODNN7EXAMPLE");
        assertThat(deserialized.getSecretMasked()).isEqualTo("AKIA****************");
        assertThat(deserialized.getLineContent()).isEqualTo("aws_key = \"AKIAIOSFODNN7EXAMPLE\"");
    }

    @Test
    @DisplayName("ScanService reveals real secret without asterisks from source file")
    void testRevealFindingFromSource(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        java.nio.file.Path credsFile = tempDir.resolve("credentials.txt");
        java.nio.file.Files.writeString(credsFile, "aws_access_key_id = AKIAIOSFODNN7EXAMPLE\n");

        String scanId = "scan-reveal-source";
        ScanRecord record = new ScanRecord(scanId, tempDir.toString(), "WORKTREE", "COMPLETED", Instant.now().toString());
        scanDao.insert(record);

        Finding maskedFinding = Finding.builder()
                .id("f-masked-legacy")
                .ruleId("aws-access-key-id")
                .ruleName("AWS Access Key ID")
                .severity(Severity.CRITICAL)
                .filePath("credentials.txt")
                .lineNumber(1)
                .columnNumber(21)
                .secretRedacted("AKIA****************")
                .snippetRedacted("aws_access_key_id = AKIA****************")
                .build();

        findingDao.insertBatch(scanId, List.of(maskedFinding));

        io.github.gravedig.server.service.ScanService scanService = new io.github.gravedig.server.service.ScanService(scanDao, findingDao);
        Optional<Map<String, String>> revealed = scanService.revealFinding("f-masked-legacy");

        assertThat(revealed).isPresent();
        assertThat(revealed.get().get("secretRaw")).isEqualTo("AKIAIOSFODNN7EXAMPLE");
        assertThat(revealed.get().get("lineContent")).isEqualTo("aws_access_key_id = AKIAIOSFODNN7EXAMPLE");
    }
}
