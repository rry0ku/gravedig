package io.github.gravedig.server.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleTestResponse {

    public static class MatchDetail {
        private String match;
        private String redacted;
        private int start;
        private int end;
        private double entropy;

        public MatchDetail() {
        }

        public MatchDetail(String match, String redacted, int start, int end, double entropy) {
            this.match = match;
            this.redacted = redacted;
            this.start = start;
            this.end = end;
            this.entropy = entropy;
        }

        public String getMatch() {
            return match;
        }

        public String getRedacted() {
            return redacted;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public double getEntropy() {
            return entropy;
        }
    }

    private boolean matched;
    private int matchCount;
    private String error;
    private String ruleId;
    private String ruleName;
    private String severity;
    private Double entropy;
    private String maskedSnippet;
    private List<MatchDetail> matches = new ArrayList<>();

    public RuleTestResponse() {
    }

    public static RuleTestResponse success(List<MatchDetail> matches) {
        RuleTestResponse resp = new RuleTestResponse();
        resp.matched = matches != null && !matches.isEmpty();
        resp.matchCount = matches != null ? matches.size() : 0;
        resp.matches = matches != null ? matches : new ArrayList<>();
        return resp;
    }

    public static RuleTestResponse error(String error) {
        RuleTestResponse resp = new RuleTestResponse();
        resp.matched = false;
        resp.matchCount = 0;
        resp.error = error;
        return resp;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public int getMatchCount() {
        return matchCount;
    }

    public void setMatchCount(int matchCount) {
        this.matchCount = matchCount;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Double getEntropy() {
        return entropy;
    }

    public void setEntropy(Double entropy) {
        this.entropy = entropy;
    }

    public String getMaskedSnippet() {
        return maskedSnippet;
    }

    public void setMaskedSnippet(String maskedSnippet) {
        this.maskedSnippet = maskedSnippet;
    }

    public List<MatchDetail> getMatches() {
        return matches;
    }

    public void setMatches(List<MatchDetail> matches) {
        this.matches = matches;
    }
}
