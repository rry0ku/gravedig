package io.github.gravedig.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class ScanSummary {
    private final long filesScanned;
    private final long commitsScanned;
    private final long durationMs;
    private final int totalFindings;
    private final int aliveFindings;
    private final int buriedFindings;
    private final Map<Severity, Integer> severityCounts;

    @JsonCreator
    public ScanSummary(
            @JsonProperty("filesScanned") long filesScanned,
            @JsonProperty("commitsScanned") long commitsScanned,
            @JsonProperty("durationMs") long durationMs,
            @JsonProperty("totalFindings") int totalFindings,
            @JsonProperty("aliveFindings") int aliveFindings,
            @JsonProperty("buriedFindings") int buriedFindings,
            @JsonProperty("severityCounts") Map<Severity, Integer> severityCounts) {
        this.filesScanned = filesScanned;
        this.commitsScanned = commitsScanned;
        this.durationMs = durationMs;
        this.totalFindings = totalFindings;
        this.aliveFindings = aliveFindings;
        this.buriedFindings = buriedFindings;
        this.severityCounts = new EnumMap<>(Severity.class);
        if (severityCounts != null) {
            this.severityCounts.putAll(severityCounts);
        }
    }

    public long getFilesScanned() { return filesScanned; }
    public long getCommitsScanned() { return commitsScanned; }
    public long getDurationMs() { return durationMs; }
    public int getTotalFindings() { return totalFindings; }
    public int getAliveFindings() { return aliveFindings; }
    public int getBuriedFindings() { return buriedFindings; }
    public Map<Severity, Integer> getSeverityCounts() { return Collections.unmodifiableMap(severityCounts); }

    public int getCountForSeverity(Severity s) {
        return severityCounts.getOrDefault(s, 0);
    }
}
