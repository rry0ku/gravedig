package io.github.gravedig.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ScanResult {
    private final String scanId;
    private final String targetPath;
    private final List<Finding> findings;
    private final ScanSummary summary;
    private final Instant createdAt;

    @JsonCreator
    public ScanResult(
            @JsonProperty("scanId") String scanId,
            @JsonProperty("targetPath") String targetPath,
            @JsonProperty("findings") List<Finding> findings,
            @JsonProperty("summary") ScanSummary summary,
            @JsonProperty("createdAt") Instant createdAt) {
        this.scanId = (scanId != null && !scanId.isBlank()) ? scanId : UUID.randomUUID().toString();
        this.targetPath = targetPath != null ? targetPath : "";
        this.findings = findings != null ? List.copyOf(findings) : Collections.emptyList();
        this.summary = summary;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public String getScanId() { return scanId; }
    public String getTargetPath() { return targetPath; }
    public List<Finding> getFindings() { return findings; }
    public ScanSummary getSummary() { return summary; }
    public Instant getCreatedAt() { return createdAt; }
}
