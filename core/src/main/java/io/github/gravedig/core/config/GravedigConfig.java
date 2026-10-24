package io.github.gravedig.core.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.gravedig.core.model.Rule;

import java.util.Collections;
import java.util.List;

public class GravedigConfig {

    private final List<Rule> rules;
    private final List<String> ignorePaths;
    private final List<String> ignorePlaceholders;
    private final AllowlistConfig allowlist;
    private final EntropyConfig entropy;
    private final String failOn;

    @JsonCreator
    public GravedigConfig(
            @JsonProperty("rules") List<Rule> rules,
            @JsonProperty("ignorePaths") List<String> ignorePaths,
            @JsonProperty("ignorePlaceholders") List<String> ignorePlaceholders,
            @JsonProperty("allowlist") AllowlistConfig allowlist,
            @JsonProperty("entropy") EntropyConfig entropy,
            @JsonProperty("failOn") String failOn) {
        this.rules = rules != null ? List.copyOf(rules) : Collections.emptyList();
        this.ignorePaths = ignorePaths != null ? List.copyOf(ignorePaths) : Collections.emptyList();
        this.ignorePlaceholders = ignorePlaceholders != null ? List.copyOf(ignorePlaceholders) : Collections.emptyList();
        this.allowlist = allowlist != null ? allowlist : new AllowlistConfig(null, null, null);
        this.entropy = entropy != null ? entropy : new EntropyConfig(true, 4.0);
        this.failOn = failOn != null ? failOn : "low";
    }

    public static GravedigConfig defaultConfiguration() {
        return new GravedigConfig(null, null, null, null, null, "low");
    }

    public List<Rule> getRules() { return rules; }
    public List<String> getIgnorePaths() { return ignorePaths; }
    public List<String> getIgnorePlaceholders() { return ignorePlaceholders; }
    public AllowlistConfig getAllowlist() { return allowlist; }
    public EntropyConfig getEntropy() { return entropy; }
    public String getFailOn() { return failOn; }

    public static class AllowlistConfig {
        private final List<String> fingerprints;
        private final List<String> paths;
        private final List<String> ruleIds;

        @JsonCreator
        public AllowlistConfig(
                @JsonProperty("fingerprints") List<String> fingerprints,
                @JsonProperty("paths") List<String> paths,
                @JsonProperty("ruleIds") List<String> ruleIds) {
            this.fingerprints = fingerprints != null ? List.copyOf(fingerprints) : Collections.emptyList();
            this.paths = paths != null ? List.copyOf(paths) : Collections.emptyList();
            this.ruleIds = ruleIds != null ? List.copyOf(ruleIds) : Collections.emptyList();
        }

        public List<String> getFingerprints() { return fingerprints; }
        public List<String> getPaths() { return paths; }
        public List<String> getRuleIds() { return ruleIds; }

        public boolean isAllowed(String fingerprint, String path, String ruleId) {
            if (fingerprints.contains(fingerprint)) return true;
            if (ruleIds.contains(ruleId)) return true;
            if (path != null) {
                for (String allowedPath : paths) {
                    if (path.contains(allowedPath)) return true;
                }
            }
            return false;
        }
    }

    public static class EntropyConfig {
        private final boolean enabled;
        private final double threshold;

        @JsonCreator
        public EntropyConfig(
                @JsonProperty("enabled") Boolean enabled,
                @JsonProperty("threshold") Double threshold) {
            this.enabled = enabled != null ? enabled : true;
            this.threshold = threshold != null ? threshold : 4.0;
        }

        public boolean isEnabled() { return enabled; }
        public double getThreshold() { return threshold; }
    }
}
