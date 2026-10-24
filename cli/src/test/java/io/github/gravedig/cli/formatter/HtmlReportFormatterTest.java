package io.github.gravedig.cli.formatter;

import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.FindingPresence;
import io.github.gravedig.core.model.ScanResult;
import io.github.gravedig.core.model.ScanSummary;
import io.github.gravedig.core.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlReportFormatterTest {

    @Test
    @DisplayName("Should generate self-contained HTML report with dark theme styling and redacted findings")
    void testHtmlReportGeneration() {
        Finding finding = Finding.builder()
                .ruleId("aws-access-key-id")
                .ruleName("AWS Access Key ID")
                .severity(Severity.CRITICAL)
                .filePath("server.properties")
                .lineNumber(4)
                .secretRedacted("AKIA**************9Z")
                .snippetRedacted("key=AKIA**************9Z")
                .presence(FindingPresence.ALIVE)
                .riskScore(95)
                .build();

        ScanSummary summary = new ScanSummary(1, 0, 5, 1, 1, 0, Map.of(Severity.CRITICAL, 1));
        ScanResult result = new ScanResult("scan-html", "/project", List.of(finding), summary, Instant.now());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        HtmlReportFormatter.print(result, new PrintStream(baos));
        String html = baos.toString(StandardCharsets.UTF_8);

        assertThat(html)
                .contains("<!DOCTYPE html>")
                .contains("gravedig")
                .contains("AKIA**************9Z")
                .contains("server.properties")
                .contains("badge-critical")
                .contains("git filter-repo");
    }
}
