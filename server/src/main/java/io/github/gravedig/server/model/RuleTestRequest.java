package io.github.gravedig.server.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleTestRequest {
    private String pattern;
    private String ruleId;
    @JsonAlias({"snippet", "text", "content"})
    private String sample;

    public RuleTestRequest() {
    }

    public RuleTestRequest(String pattern, String sample) {
        this.pattern = pattern;
        this.sample = sample;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getSample() {
        return sample;
    }

    public void setSample(String sample) {
        this.sample = sample;
    }
}
