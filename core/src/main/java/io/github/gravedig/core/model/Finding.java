package io.github.gravedig.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Finding {
    private final String id;
    private final String ruleId;
    private final String ruleName;
    private final Severity severity;
    private final String filePath;
    private final int lineNumber;
    private final int columnNumber;
    private final String secretRedacted;
    private final String snippetRedacted;
    private final String fingerprint;
    private final double entropy;
    private final String validationReason;
    private final int riskScore;
    private final int occurrences;
    private final List<Occurrence> occurrencesList;
    private final ExposureWindow exposureWindow;
    private final RiskBreakdown riskBreakdown;
    private FindingPresence presence;
    private FindingStatus status;
    private final String commitHash;
    private final String commitAuthor;
    private final Instant commitDate;
    private final String branch;
    private final String description;
    private final String remediation;
    private final Instant detectedAt;

    @JsonCreator
    public Finding(
            @JsonProperty("id") String id,
            @JsonProperty("ruleId") String ruleId,
            @JsonProperty("ruleName") String ruleName,
            @JsonProperty("severity") Severity severity,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("lineNumber") int lineNumber,
            @JsonProperty("columnNumber") int columnNumber,
            @JsonProperty("secretRedacted") String secretRedacted,
            @JsonProperty("snippetRedacted") String snippetRedacted,
            @JsonProperty("fingerprint") String fingerprint,
            @JsonProperty("entropy") double entropy,
            @JsonProperty("validationReason") String validationReason,
            @JsonProperty("riskScore") int riskScore,
            @JsonProperty("occurrences") int occurrences,
            @JsonProperty("occurrencesList") List<Occurrence> occurrencesList,
            @JsonProperty("exposureWindow") ExposureWindow exposureWindow,
            @JsonProperty("riskBreakdown") RiskBreakdown riskBreakdown,
            @JsonProperty("presence") FindingPresence presence,
            @JsonProperty("status") FindingStatus status,
            @JsonProperty("commitHash") String commitHash,
            @JsonProperty("commitAuthor") String commitAuthor,
            @JsonProperty("commitDate") Instant commitDate,
            @JsonProperty("branch") String branch,
            @JsonProperty("description") String description,
            @JsonProperty("remediation") String remediation,
            @JsonProperty("detectedAt") Instant detectedAt) {
        this.id = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId must not be null");
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName must not be null");
        this.severity = severity != null ? severity : Severity.MEDIUM;
        this.filePath = Objects.requireNonNull(filePath, "filePath must not be null");
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
        this.secretRedacted = Objects.requireNonNull(secretRedacted, "secretRedacted must not be null");
        this.snippetRedacted = snippetRedacted != null ? snippetRedacted : "";
        this.fingerprint = fingerprint != null ? fingerprint : "";
        this.entropy = entropy;
        this.validationReason = validationReason != null ? validationReason : "";
        this.riskScore = Math.max(0, Math.min(100, riskScore));
        this.occurrences = Math.max(1, occurrences);
        this.occurrencesList = occurrencesList != null ? List.copyOf(occurrencesList) : Collections.emptyList();
        this.exposureWindow = exposureWindow;
        this.riskBreakdown = riskBreakdown;
        this.presence = presence != null ? presence : FindingPresence.ALIVE;
        this.status = status != null ? status : FindingStatus.OPEN;
        this.commitHash = commitHash;
        this.commitAuthor = commitAuthor;
        this.commitDate = commitDate;
        this.branch = branch;
        this.description = description != null ? description : "";
        this.remediation = remediation != null ? remediation : "";
        this.detectedAt = detectedAt != null ? detectedAt : Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() { return id; }
    public String getRuleId() { return ruleId; }
    public String getRuleName() { return ruleName; }
    public Severity getSeverity() { return severity; }
    public String getFilePath() { return filePath; }
    public int getLineNumber() { return lineNumber; }
    public int getColumnNumber() { return columnNumber; }
    public String getSecretRedacted() { return secretRedacted; }
    public String getSnippetRedacted() { return snippetRedacted; }
    public String getFingerprint() { return fingerprint; }
    public double getEntropy() { return entropy; }
    public String getValidationReason() { return validationReason; }
    public int getRiskScore() { return riskScore; }
    public int getOccurrences() { return occurrences; }
    public List<Occurrence> getOccurrencesList() { return occurrencesList; }
    public ExposureWindow getExposureWindow() { return exposureWindow; }
    public RiskBreakdown getRiskBreakdown() { return riskBreakdown; }
    public FindingPresence getPresence() { return presence; }
    public void setPresence(FindingPresence presence) { this.presence = presence; }
    public FindingStatus getStatus() { return status; }
    public void setStatus(FindingStatus status) { this.status = status; }
    public String getCommitHash() { return commitHash; }
    public String getCommitAuthor() { return commitAuthor; }
    public Instant getCommitDate() { return commitDate; }
    public String getBranch() { return branch; }
    public String getDescription() { return description; }
    public String getRemediation() { return remediation; }
    public Instant getDetectedAt() { return detectedAt; }

    public static class Builder {
        private String id;
        private String ruleId = "unknown";
        private String ruleName = "Unknown Rule";
        private Severity severity = Severity.MEDIUM;
        private String filePath = "";
        private int lineNumber = 1;
        private int columnNumber = 1;
        private String secretRedacted = "******";
        private String snippetRedacted = "";
        private String fingerprint;
        private double entropy = 0.0;
        private String validationReason;
        private int riskScore = 50;
        private int occurrences = 1;
        private List<Occurrence> occurrencesList;
        private ExposureWindow exposureWindow;
        private RiskBreakdown riskBreakdown;
        private FindingPresence presence = FindingPresence.ALIVE;
        private FindingStatus status = FindingStatus.OPEN;
        private String commitHash;
        private String commitAuthor;
        private Instant commitDate;
        private String branch;
        private String description;
        private String remediation;
        private Instant detectedAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder ruleId(String ruleId) { this.ruleId = ruleId; return this; }
        public Builder ruleName(String ruleName) { this.ruleName = ruleName; return this; }
        public Builder severity(Severity severity) { this.severity = severity; return this; }
        public Builder filePath(String filePath) { this.filePath = filePath; return this; }
        public Builder lineNumber(int lineNumber) { this.lineNumber = lineNumber; return this; }
        public Builder columnNumber(int columnNumber) { this.columnNumber = columnNumber; return this; }
        public Builder secretRedacted(String secretRedacted) { this.secretRedacted = secretRedacted; return this; }
        public Builder snippetRedacted(String snippetRedacted) { this.snippetRedacted = snippetRedacted; return this; }
        public Builder fingerprint(String fingerprint) { this.fingerprint = fingerprint; return this; }
        public Builder entropy(double entropy) { this.entropy = entropy; return this; }
        public Builder validationReason(String validationReason) { this.validationReason = validationReason; return this; }
        public Builder riskScore(int riskScore) { this.riskScore = riskScore; return this; }
        public Builder occurrences(int occurrences) { this.occurrences = occurrences; return this; }
        public Builder occurrencesList(List<Occurrence> occurrencesList) { this.occurrencesList = occurrencesList; return this; }
        public Builder exposureWindow(ExposureWindow exposureWindow) { this.exposureWindow = exposureWindow; return this; }
        public Builder riskBreakdown(RiskBreakdown riskBreakdown) { this.riskBreakdown = riskBreakdown; return this; }
        public Builder presence(FindingPresence presence) { this.presence = presence; return this; }
        public Builder status(FindingStatus status) { this.status = status; return this; }
        public Builder commitHash(String commitHash) { this.commitHash = commitHash; return this; }
        public Builder commitAuthor(String commitAuthor) { this.commitAuthor = commitAuthor; return this; }
        public Builder commitDate(Instant commitDate) { this.commitDate = commitDate; return this; }
        public Builder branch(String branch) { this.branch = branch; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder remediation(String remediation) { this.remediation = remediation; return this; }
        public Builder detectedAt(Instant detectedAt) { this.detectedAt = detectedAt; return this; }

        public Finding build() {
            return new Finding(id, ruleId, ruleName, severity, filePath, lineNumber, columnNumber,
                    secretRedacted, snippetRedacted, fingerprint, entropy, validationReason, riskScore,
                    occurrences, occurrencesList, exposureWindow, riskBreakdown, presence, status,
                    commitHash, commitAuthor, commitDate, branch, description, remediation, detectedAt);
        }
    }
}
