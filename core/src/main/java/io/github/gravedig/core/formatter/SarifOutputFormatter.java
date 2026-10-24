package io.github.gravedig.core.formatter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.ScanResult;
import io.github.gravedig.core.model.Severity;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

public class SarifOutputFormatter {

    private static final String SARIF_SCHEMA = "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json";
    private static final String VERSION = "2.1.0";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static String format(ScanResult result) {
        try {
            return MAPPER.writeValueAsString(buildSarifJson(result));
        } catch (Exception e) {
            return "{\"error\": \"Failed generating SARIF: " + e.getMessage() + "\"}";
        }
    }

    public static void print(ScanResult result, PrintStream out) {
        out.println(format(result));
    }

    public static ObjectNode buildSarifJson(ScanResult result) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("$schema", SARIF_SCHEMA);
        root.put("version", VERSION);

        ArrayNode runs = root.putArray("runs");
        ObjectNode run = MAPPER.createObjectNode();

        // Tool metadata
        ObjectNode tool = run.putObject("tool");
        ObjectNode driver = tool.putObject("driver");
        driver.put("name", "gravedig");
        driver.put("version", "0.1.0");
        driver.put("informationUri", "https://github.com/gravedig/gravedig");

        ArrayNode rulesArray = driver.putArray("rules");
        Set<String> ruleIdsAdded = new HashSet<>();

        // Results
        ArrayNode resultsArray = run.putArray("results");

        if (result != null && result.getFindings() != null) {
            for (Finding f : result.getFindings()) {
                if (ruleIdsAdded.add(f.getRuleId())) {
                    ObjectNode ruleNode = MAPPER.createObjectNode();
                    ruleNode.put("id", f.getRuleId());
                    ruleNode.put("name", f.getRuleName());
                    ruleNode.putObject("shortDescription").put("text", f.getDescription());
                    ruleNode.putObject("fullDescription").put("text", f.getRemediation());
                    ruleNode.putObject("defaultConfiguration").put("level", mapToSarifLevel(f.getSeverity()));
                    rulesArray.add(ruleNode);
                }

                ObjectNode resultNode = MAPPER.createObjectNode();
                resultNode.put("ruleId", f.getRuleId());
                resultNode.put("level", mapToSarifLevel(f.getSeverity()));

                String message = String.format("%s (%s, Presence: %s, Risk: %d/100)",
                        f.getRuleName(), f.getSecretRedacted(), f.getPresence(), f.getRiskScore());
                resultNode.putObject("message").put("text", message);

                ArrayNode locations = resultNode.putArray("locations");
                ObjectNode location = MAPPER.createObjectNode();
                ObjectNode physicalLocation = location.putObject("physicalLocation");
                physicalLocation.putObject("artifactLocation").put("uri", f.getFilePath());

                ObjectNode region = physicalLocation.putObject("region");
                region.put("startLine", Math.max(1, f.getLineNumber()));
                region.put("startColumn", Math.max(1, f.getColumnNumber()));

                locations.add(location);

                // Properties bag with gravedig metadata
                ObjectNode properties = resultNode.putObject("properties");
                properties.put("presence", f.getPresence() != null ? f.getPresence().name() : "ALIVE");
                properties.put("status", f.getStatus() != null ? f.getStatus().name() : "OPEN");
                properties.put("riskScore", f.getRiskScore());
                properties.put("fingerprint", f.getFingerprint());
                properties.put("secretRedacted", f.getSecretRedacted());
                if (f.getExposureWindow() != null) {
                    properties.put("exposureDuration", f.getExposureWindow().getDurationFormatted());
                    properties.put("firstSeenCommit", f.getExposureWindow().getFirstSeenCommit());
                }

                resultsArray.add(resultNode);
            }
        }

        runs.add(run);
        return root;
    }

    private static String mapToSarifLevel(Severity severity) {
        if (severity == null) {
            return "note";
        }
        switch (severity) {
            case CRITICAL:
            case HIGH:
                return "error";
            case MEDIUM:
                return "warning";
            case LOW:
            default:
                return "note";
        }
    }
}
