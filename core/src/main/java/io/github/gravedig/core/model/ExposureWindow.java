package io.github.gravedig.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class ExposureWindow {
    private final String firstSeenCommit;
    private final Instant firstSeenDate;
    private final String firstSeenAuthor;
    private final String removedInCommit;
    private final Instant removedInDate;
    private final Long durationMillis;
    private final String durationFormatted;
    private final List<String> branches;
    private final List<String> tags;

    @JsonCreator
    public ExposureWindow(
            @JsonProperty("firstSeenCommit") String firstSeenCommit,
            @JsonProperty("firstSeenDate") Instant firstSeenDate,
            @JsonProperty("firstSeenAuthor") String firstSeenAuthor,
            @JsonProperty("removedInCommit") String removedInCommit,
            @JsonProperty("removedInDate") Instant removedInDate,
            @JsonProperty("durationMillis") Long durationMillis,
            @JsonProperty("durationFormatted") String durationFormatted,
            @JsonProperty("branches") List<String> branches,
            @JsonProperty("tags") List<String> tags) {
        this.firstSeenCommit = firstSeenCommit;
        this.firstSeenDate = firstSeenDate;
        this.firstSeenAuthor = firstSeenAuthor;
        this.removedInCommit = removedInCommit;
        this.removedInDate = removedInDate;
        this.durationMillis = durationMillis;
        this.durationFormatted = durationFormatted != null ? durationFormatted : "Unknown";
        this.branches = branches != null ? List.copyOf(branches) : Collections.emptyList();
        this.tags = tags != null ? List.copyOf(tags) : Collections.emptyList();
    }

    public static ExposureWindow compute(
            String firstCommit, Instant firstDate, String firstAuthor,
            String removedCommit, Instant removedDate,
            List<String> branches, List<String> tags, boolean isAlive) {

        Long durationMs = null;
        String formatted;

        if (firstDate != null) {
            Instant end = isAlive ? Instant.now() : (removedDate != null ? removedDate : Instant.now());
            Duration duration = Duration.between(firstDate, end);
            durationMs = Math.max(0, duration.toMillis());

            long days = duration.toDays();
            long hours = duration.toHoursPart();
            long minutes = duration.toMinutesPart();

            if (days > 0) {
                formatted = days + "d " + hours + "h" + (isAlive ? " (still exposed)" : "");
            } else if (hours > 0) {
                formatted = hours + "h " + minutes + "m" + (isAlive ? " (still exposed)" : "");
            } else {
                formatted = Math.max(1, minutes) + "m" + (isAlive ? " (still exposed)" : "");
            }
        } else {
            formatted = isAlive ? "Still exposed" : "Buried";
        }

        return new ExposureWindow(
                firstCommit, firstDate, firstAuthor,
                removedCommit, removedDate,
                durationMs, formatted,
                branches, tags
        );
    }

    public String getFirstSeenCommit() { return firstSeenCommit; }
    public Instant getFirstSeenDate() { return firstSeenDate; }
    public String getFirstSeenAuthor() { return firstSeenAuthor; }
    public String getRemovedInCommit() { return removedInCommit; }
    public Instant getRemovedInDate() { return removedInDate; }
    public Long getDurationMillis() { return durationMillis; }
    public String getDurationFormatted() { return durationFormatted; }
    public List<String> getBranches() { return branches; }
    public List<String> getTags() { return tags; }
}
