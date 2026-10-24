package io.github.gravedig.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class Rule {
    private final String id;
    private final String name;
    private final String regex;
    private final List<String> keywords;
    private final double entropyThreshold;
    private final Severity severity;
    private final String description;
    private final String remediation;

    @JsonIgnore
    private final Pattern compiledPattern;

    @JsonCreator
    public Rule(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("regex") String regex,
            @JsonProperty("keywords") List<String> keywords,
            @JsonProperty("entropyThreshold") Double entropyThreshold,
            @JsonProperty("severity") Severity severity,
            @JsonProperty("description") String description,
            @JsonProperty("remediation") String remediation) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.regex = Objects.requireNonNull(regex, "regex must not be null");
        this.keywords = keywords != null ? List.copyOf(keywords) : Collections.emptyList();
        this.entropyThreshold = entropyThreshold != null ? entropyThreshold : 0.0;
        this.severity = severity != null ? severity : Severity.MEDIUM;
        this.description = description != null ? description : "";
        this.remediation = remediation != null ? remediation : "";
        this.compiledPattern = Pattern.compile(this.regex);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRegex() {
        return regex;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public double getEntropyThreshold() {
        return entropyThreshold;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }

    public String getRemediation() {
        return remediation;
    }

    public Pattern getCompiledPattern() {
        return compiledPattern;
    }

    public boolean matchesFastCheck(String line) {
        if (keywords.isEmpty()) {
            return true;
        }
        for (String kw : keywords) {
            if (line.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rule rule = (Rule) o;
        return Objects.equals(id, rule.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Rule{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", severity=" + severity +
                '}';
    }
}
