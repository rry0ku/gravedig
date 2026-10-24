package io.github.gravedig.core.git;

import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.FindingPresence;
import io.github.gravedig.core.model.Rule;
import io.github.gravedig.core.model.ScanResult;
import io.github.gravedig.core.rule.RuleLoader;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitHistoryScannerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should detect ALIVE vs BURIED secrets across git commit history with exposure windows")
    void testGitHistoryScanningWithAliveAndBuried() throws Exception {
        RuleLoader ruleLoader = new RuleLoader();
        List<Rule> rules = ruleLoader.loadDefaultRules();

        // 1. Initialize git repo using JGit
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            File awsFile = tempDir.resolve("credentials.env").toFile();

            // Commit 1: Introduce AWS key
            Files.writeString(awsFile.toPath(), "AWS_KEY=AKIAIOSFODNN7BK5WP9Z\n");
            git.add().addFilepattern("credentials.env").call();
            git.commit()
                    .setMessage("Add AWS credentials")
                    .setAuthor("Dev One", "dev1@example.com")
                    .call();

            // Commit 2: Remove AWS key (BURIED!) and introduce live Stripe key (ALIVE!)
            Files.writeString(awsFile.toPath(), "# Cleaned AWS\nSTRIPE_KEY=" + "sk" + "_live_51Mv9K2eZv8Tx7Yq1Wp4Rn6Ls0Dk3Fm8\n");
            git.add().addFilepattern("credentials.env").call();
            git.commit()
                    .setMessage("Remove AWS key and add Stripe key")
                    .setAuthor("Dev Two", "dev2@example.com")
                    .call();
        }

        // 2. Scan git history
        GitHistoryScanner scanner = new GitHistoryScanner(rules, null, null, 2);
        ScanResult result = scanner.scanRepository(tempDir);

        assertThat(result).isNotNull();
        assertThat(result.getSummary().getCommitsScanned()).isEqualTo(2);
        for (Finding f : result.getFindings()) {
            System.out.println("FOUND: " + f.getRuleId() + " -> " + f.getPresence() + " -> " + f.getSecretRedacted());
        }
        assertThat(result.getSummary().getTotalFindings()).isEqualTo(2);
        assertThat(result.getSummary().getAliveFindings()).isEqualTo(1);
        assertThat(result.getSummary().getBuriedFindings()).isEqualTo(1);

        List<Finding> findings = result.getFindings();

        // Verify Stripe key is ALIVE
        Finding stripeFinding = findings.stream()
                .filter(f -> f.getRuleId().equals("stripe-secret-key"))
                .findFirst()
                .orElse(null);
        assertThat(stripeFinding).isNotNull();
        assertThat(stripeFinding.getPresence()).isEqualTo(FindingPresence.ALIVE);
        assertThat(stripeFinding.getExposureWindow().getFirstSeenCommit()).isNotEmpty();
        assertThat(stripeFinding.getExposureWindow().getRemovedInCommit()).isNull();
        assertThat(stripeFinding.getRiskScore()).isGreaterThan(50);

        // Verify AWS key is BURIED
        Finding awsFinding = findings.stream()
                .filter(f -> f.getRuleId().equals("aws-access-key-id"))
                .findFirst()
                .orElse(null);
        assertThat(awsFinding).isNotNull();
        assertThat(awsFinding.getPresence()).isEqualTo(FindingPresence.BURIED);
        assertThat(awsFinding.getExposureWindow().getFirstSeenCommit()).isNotEmpty();
        assertThat(awsFinding.getExposureWindow().getRemovedInCommit()).isNotEmpty();
        assertThat(awsFinding.getExposureWindow().getFirstSeenAuthor()).contains("Dev One");
        assertThat(awsFinding.getOccurrencesList()).isNotEmpty();
    }
}
