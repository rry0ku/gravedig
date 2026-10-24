package io.github.gravedig.core.baseline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class BaselineEntry {
    private final String fingerprint;
    private final String ruleId;
    private final String filePath;

    @JsonCreator
    public BaselineEntry(
            @JsonProperty("fingerprint") String fingerprint,
            @JsonProperty("ruleId") String ruleId,
            @JsonProperty("filePath") String filePath) {
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId must not be null");
        this.filePath = Objects.requireNonNull(filePath, "filePath must not be null");
    }

    public String getFingerprint() { return fingerprint; }
    public String getRuleId() { return ruleId; }
    public String getFilePath() { return filePath; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaselineEntry that = (BaselineEntry) o;
        return Objects.equals(fingerprint, that.fingerprint) &&
                Objects.equals(ruleId, that.ruleId) &&
                Objects.equals(filePath, that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fingerprint, ruleId, filePath);
    }
}
