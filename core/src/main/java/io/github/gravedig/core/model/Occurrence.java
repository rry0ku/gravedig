package io.github.gravedig.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class Occurrence {
    private final String commitHash;
    private final String commitAuthor;
    private final Instant commitDate;
    private final String filePath;
    private final int lineNumber;
    private final String snippetRedacted;

    @JsonCreator
    public Occurrence(
            @JsonProperty("commitHash") String commitHash,
            @JsonProperty("commitAuthor") String commitAuthor,
            @JsonProperty("commitDate") Instant commitDate,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("lineNumber") int lineNumber,
            @JsonProperty("snippetRedacted") String snippetRedacted) {
        this.commitHash = commitHash != null ? commitHash : "";
        this.commitAuthor = commitAuthor != null ? commitAuthor : "";
        this.commitDate = commitDate;
        this.filePath = filePath != null ? filePath : "";
        this.lineNumber = lineNumber;
        this.snippetRedacted = snippetRedacted != null ? snippetRedacted : "";
    }

    public String getCommitHash() { return commitHash; }
    public String getCommitAuthor() { return commitAuthor; }
    public Instant getCommitDate() { return commitDate; }
    public String getFilePath() { return filePath; }
    public int getLineNumber() { return lineNumber; }
    public String getSnippetRedacted() { return snippetRedacted; }
}
