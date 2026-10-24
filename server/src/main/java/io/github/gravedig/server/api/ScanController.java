package io.github.gravedig.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.FindingStatus;
import io.github.gravedig.server.model.FindingFilter;
import io.github.gravedig.server.model.ScanRecord;
import io.github.gravedig.server.model.ScanRequest;
import io.github.gravedig.server.service.ScanService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ScanController {

    private final ScanService scanService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    public void startScan(Context ctx) {
        try {
            ScanRequest req = ctx.bodyAsClass(ScanRequest.class);
            ScanRecord record = scanService.startScan(req);
            ctx.status(HttpStatus.ACCEPTED).json(record);
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Failed starting scan: " + e.getMessage()));
        }
    }

    public void listScans(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);
        List<ScanRecord> scans = scanService.listScans(limit);
        ctx.json(scans);
    }

    public void getScan(Context ctx) {
        String id = ctx.pathParam("id");
        Optional<ScanRecord> scan = scanService.getScan(id);
        if (scan.isPresent()) {
            ctx.json(scan.get());
        } else {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "Scan not found: " + id));
        }
    }

    public void getFindings(Context ctx) {
        String scanId = ctx.pathParam("id");
        FindingFilter filter = new FindingFilter();
        filter.setSeverity(ctx.queryParam("severity"));
        filter.setRuleId(ctx.queryParam("rule"));
        filter.setFilePath(ctx.queryParam("file"));
        filter.setStatus(ctx.queryParam("status"));
        filter.setPresence(ctx.queryParam("presence"));

        Integer limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(500);
        Integer offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        filter.setLimit(limit);
        filter.setOffset(offset);

        List<Finding> findings = scanService.getFindings(scanId, filter);
        ctx.json(findings);
    }

    public void patchFinding(Context ctx) {
        String id = ctx.pathParam("id");
        try {
            JsonNode body = mapper.readTree(ctx.body());
            if (body == null || !body.has("status")) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Missing 'status' field"));
                return;
            }
            String statusStr = body.get("status").asText();
            FindingStatus status = FindingStatus.valueOf(statusStr.toUpperCase());
            boolean updated = scanService.updateFindingStatus(id, status);
            if (updated) {
                Optional<Finding> finding = scanService.getFinding(id);
                ctx.json(finding.orElseThrow());
            } else {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "Finding not found: " + id));
            }
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Invalid finding status: " + e.getMessage()));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Error updating finding: " + e.getMessage()));
        }
    }

    public void revealFinding(Context ctx) {
        String id = ctx.pathParam("id");
        Optional<Map<String, String>> result = scanService.revealFinding(id);
        if (result.isPresent()) {
            ctx.json(result.get());
        } else {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "Finding not found: " + id));
        }
    }

    public void exportScan(Context ctx) {
        String scanId = ctx.pathParam("id");
        String format = ctx.queryParam("format");
        if (format == null || format.isBlank()) {
            format = "json";
        }
        format = format.toLowerCase().trim();

        Optional<String> content = scanService.exportScan(scanId, format);
        if (content.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "Scan not found: " + scanId));
            return;
        }

        switch (format) {
            case "sarif":
                ctx.contentType("application/sarif+json");
                ctx.header("Content-Disposition", "attachment; filename=\"scan-" + scanId + ".sarif.json\"");
                break;
            case "html":
                ctx.contentType("text/html; charset=utf-8");
                ctx.header("Content-Disposition", "attachment; filename=\"scan-" + scanId + ".html\"");
                break;
            case "csv":
                ctx.contentType("text/csv; charset=utf-8");
                ctx.header("Content-Disposition", "attachment; filename=\"scan-" + scanId + ".csv\"");
                break;
            case "json":
            default:
                ctx.contentType("application/json; charset=utf-8");
                ctx.header("Content-Disposition", "attachment; filename=\"scan-" + scanId + ".json\"");
                break;
        }

        ctx.result(content.get());
    }

    public void saveExport(Context ctx) {
        String scanId = ctx.pathParam("id");
        String format = ctx.queryParam("format");
        String destinationDir = null;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode node = mapper.readTree(ctx.body());
                if (node.has("format") && (format == null || format.isBlank())) {
                    format = node.get("format").asText(null);
                }
                if (node.has("destinationDir")) {
                    destinationDir = node.get("destinationDir").asText(null);
                }
            }
        } catch (Exception ignored) {}

        if (format == null || format.isBlank()) {
            format = "json";
        }
        format = format.toLowerCase().trim();

        try {
            Optional<Map<String, Object>> result = scanService.saveExportToFile(scanId, format, destinationDir);
            if (result.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "Scan not found: " + scanId));
                return;
            }
            ctx.json(result.get());
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Failed to save export: " + e.getMessage()));
        }
    }

    public void deleteScan(Context ctx) {
        String scanId = ctx.pathParam("id");
        boolean deleted = scanService.deleteScan(scanId);
        if (deleted) {
            ctx.json(Map.of("success", true, "message", "Scan and associated findings cleared"));
        } else {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "Scan not found: " + scanId));
        }
    }

    public void clearAllScans(Context ctx) {
        scanService.clearAllScans();
        ctx.json(Map.of("success", true, "message", "All scans and findings cleared from dashboard"));
    }
}
