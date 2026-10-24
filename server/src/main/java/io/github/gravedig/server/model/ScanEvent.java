package io.github.gravedig.server.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.ScanSummary;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScanEvent {
    public enum Type {
        PROGRESS,
        FINDING,
        COMPLETED,
        ERROR
    }

    private Type type;
    private String scanId;
    private Integer filesScanned;
    private Integer commitsScanned;
    private Integer findingsCount;
    private Finding finding;
    private ScanSummary summary;
    private String error;

    public ScanEvent() {
    }

    public static ScanEvent progress(String scanId, int files, int commits, int findings) {
        ScanEvent e = new ScanEvent();
        e.type = Type.PROGRESS;
        e.scanId = scanId;
        e.filesScanned = files;
        e.commitsScanned = commits;
        e.findingsCount = findings;
        return e;
    }

    public static ScanEvent finding(String scanId, Finding finding, int currentTotal) {
        ScanEvent e = new ScanEvent();
        e.type = Type.FINDING;
        e.scanId = scanId;
        e.finding = finding;
        e.findingsCount = currentTotal;
        return e;
    }

    public static ScanEvent completed(String scanId, ScanSummary summary) {
        ScanEvent e = new ScanEvent();
        e.type = Type.COMPLETED;
        e.scanId = scanId;
        e.summary = summary;
        if (summary != null) {
            e.filesScanned = (int) summary.getFilesScanned();
            e.commitsScanned = (int) summary.getCommitsScanned();
            e.findingsCount = summary.getTotalFindings();
        }
        return e;
    }

    public static ScanEvent error(String scanId, String message) {
        ScanEvent e = new ScanEvent();
        e.type = Type.ERROR;
        e.scanId = scanId;
        e.error = message;
        return e;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public Integer getFilesScanned() {
        return filesScanned;
    }

    public void setFilesScanned(Integer filesScanned) {
        this.filesScanned = filesScanned;
    }

    public Integer getCommitsScanned() {
        return commitsScanned;
    }

    public void setCommitsScanned(Integer commitsScanned) {
        this.commitsScanned = commitsScanned;
    }

    public Integer getFindingsCount() {
        return findingsCount;
    }

    public void setFindingsCount(Integer findingsCount) {
        this.findingsCount = findingsCount;
    }

    public Finding getFinding() {
        return finding;
    }

    public void setFinding(Finding finding) {
        this.finding = finding;
    }

    public ScanSummary getSummary() {
        return summary;
    }

    public void setSummary(ScanSummary summary) {
        this.summary = summary;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
