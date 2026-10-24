package io.github.gravedig.cli.formatter;

import com.fasterxml.jackson.databind.node.ObjectNode;
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

class SarifOutputFormatterTest {

    @Test
    @DisplayName("Should generate valid SARIF 2.1.0 JSON format")
    void testSarifGeneration() {
        Finding finding = Finding.builder()
                .ruleId("aws-access-key-id")
                .ruleName("AWS Access Key ID")
                .severity(Severity.CRITICAL)
                .filePath("config/aws.env")
                .lineNumber(12)
                .columnNumber(5)
                .secretRedacted("AKIA**************9Z")
                .snippetRedacted("AWS_KEY=AKIA**************9Z")
                .fingerprint("test-fingerprint")
                .presence(FindingPresence.ALIVE)
                .riskScore(90)
                .description("AWS Access Key ID leaked")
                .remediation("Revoke key immediately")
                .build();

        ScanSummary summary = new ScanSummary(1, 0, 10, 1, 1, 0, Map.of(Severity.CRITICAL, 1));
        ScanResult result = new ScanResult("scan-123", "/path/to/project", List.of(finding), summary, Instant.now());

        ObjectNode sarif = SarifOutputFormatter.buildSarifJson(result);

        assertThat(sarif.get("version").asText()).isEqualTo("2.1.0");
        assertThat(sarif.get("$schema").asText()).contains("sarif-schema-2.1.0.json");

        ObjectNode run = (ObjectNode) sarif.get("runs").get(0);
        assertThat(run.get("tool").get("driver").get("name").asText()).isEqualTo("gravedig");

        ObjectNode sarifResult = (ObjectNode) run.get("results").get(0);
        assertThat(sarifResult.get("ruleId").asText()).isEqualTo("aws-access-key-id");
        assertThat(sarifResult.get("level").asText()).isEqualTo("error");
        assertThat(sarifResult.get("locations").get(0).get("physicalLocation").get("artifactLocation").get("uri").asText())
                .isEqualTo("config/aws.env");
        assertThat(sarifResult.get("properties").get("presence").asText()).isEqualTo("ALIVE");
        assertThat(sarifResult.get("properties").get("riskScore").asInt()).isEqualTo(90);

        // Verify print stream output
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SarifOutputFormatter.print(result, new PrintStream(baos));
        String output = baos.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("\"version\" : \"2.1.0\"")
                .contains("\"aws-access-key-id\"");
    }
}
