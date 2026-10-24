package io.github.gravedig.server.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.gravedig.core.model.ScanSummary;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScanRecord {
    private String id;
    private String targetPath;
    private String scanMode;
    private String status;
    private String createdAt;
    private String completedAt;
    private int totalFiles;
    private int totalCommits;
    private int totalFindings;
    private int riskScore;
    private String errorMessage;
    private ScanSummary summary;

    public ScanRecord() {
    }

    public ScanRecord(String id, String targetPath, String scanMode, String status, String createdAt) {
        this.id = id;
        this.targetPath = targetPath;
        this.scanMode = scanMode;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public String getScanMode() {
        return scanMode;
    }

    public void setScanMode(String scanMode) {
        this.scanMode = scanMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public int getTotalCommits() {
        return totalCommits;
    }

    public void setTotalCommits(int totalCommits) {
        this.totalCommits = totalCommits;
    }

    public int getTotalFindings() {
        return totalFindings;
    }

    public void setTotalFindings(int totalFindings) {
        this.totalFindings = totalFindings;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public ScanSummary getSummary() {
        return summary;
    }

    public void setSummary(ScanSummary summary) {
        this.summary = summary;
    }
}
