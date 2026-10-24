package io.github.gravedig.server.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScanRequest {
    @JsonAlias({"targetPath", "path"})
    private String path;
    private boolean includeHistory = true;
    private boolean offlineValidation = true;
    private boolean entropyCheck = true;

    public ScanRequest() {
    }

    public ScanRequest(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isIncludeHistory() {
        return includeHistory;
    }

    public void setIncludeHistory(boolean includeHistory) {
        this.includeHistory = includeHistory;
    }

    public boolean isOfflineValidation() {
        return offlineValidation;
    }

    public void setOfflineValidation(boolean offlineValidation) {
        this.offlineValidation = offlineValidation;
    }

    public boolean isEntropyCheck() {
        return entropyCheck;
    }

    public void setEntropyCheck(boolean entropyCheck) {
        this.entropyCheck = entropyCheck;
    }
}
