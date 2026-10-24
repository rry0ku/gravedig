package io.github.gravedig.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RiskBreakdown {
    private final int severityScore;
    private final int presenceScore;
    private final int exposureScore;
    private final int branchSpreadScore;
    private final int confidenceScore;
    private final int totalScore;
    private final String explanation;

    @JsonCreator
    public RiskBreakdown(
            @JsonProperty("severityScore") int severityScore,
            @JsonProperty("presenceScore") int presenceScore,
            @JsonProperty("exposureScore") int exposureScore,
            @JsonProperty("branchSpreadScore") int branchSpreadScore,
            @JsonProperty("confidenceScore") int confidenceScore,
            @JsonProperty("totalScore") int totalScore,
            @JsonProperty("explanation") String explanation) {
        this.severityScore = severityScore;
        this.presenceScore = presenceScore;
        this.exposureScore = exposureScore;
        this.branchSpreadScore = branchSpreadScore;
        this.confidenceScore = confidenceScore;
        this.totalScore = Math.max(1, Math.min(100, totalScore));
        this.explanation = explanation != null ? explanation : "";
    }

    public static RiskBreakdown compute(
            Severity severity,
            FindingPresence presence,
            long exposureMillis,
            int branchCount,
            double validatorConfidence) {

        int sev;
        switch (severity) {
            case CRITICAL: sev = 30; break;
            case HIGH: sev = 22; break;
            case MEDIUM: sev = 15; break;
            case LOW:
            default: sev = 8; break;
        }

        int pres = (presence == FindingPresence.ALIVE) ? 25 : 10;

        // Exposure duration weight
        int exp;
        long days = exposureMillis / (1000L * 60 * 60 * 24);
        if (days > 180) exp = 20;
        else if (days > 30) exp = 15;
        else if (days > 7) exp = 10;
        else exp = 5;

        // Branch spread
        int spread = Math.min(15, Math.max(3, branchCount * 3));

        // Confidence
        int conf = (int) Math.round(Math.min(1.0, Math.max(0.4, validatorConfidence)) * 10);

        int total = sev + pres + exp + spread + conf;

        String explanation = String.format("Severity (+%d), Presence %s (+%d), Exposure %dd (+%d), %d branch(es) (+%d), Confidence (+%d)",
                sev, presence, pres, days, exp, branchCount, spread, conf);

        return new RiskBreakdown(sev, pres, exp, spread, conf, total, explanation);
    }

    public int getSeverityScore() { return severityScore; }
    public int getPresenceScore() { return presenceScore; }
    public int getExposureScore() { return exposureScore; }
    public int getBranchSpreadScore() { return branchSpreadScore; }
    public int getConfidenceScore() { return confidenceScore; }
    public int getTotalScore() { return totalScore; }
    public String getExplanation() { return explanation; }
}
