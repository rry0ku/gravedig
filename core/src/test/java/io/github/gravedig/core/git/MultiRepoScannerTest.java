package io.github.gravedig.core.git;

import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.Rule;
import io.github.gravedig.core.model.ScanResult;
import io.github.gravedig.core.rule.RuleLoader;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MultiRepoScannerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should discover and scan multiple repositories under a parent folder")
    void testMultiRepoDiscoveryAndScan() throws Exception {
        RuleLoader ruleLoader = new RuleLoader();
        List<Rule> rules = ruleLoader.loadDefaultRules();

        Path repo1 = tempDir.resolve("service-auth");
        Path repo2 = tempDir.resolve("service-billing");
        Files.createDirectories(repo1);
        Files.createDirectories(repo2);

        // Repo 1
        try (Git git1 = Git.init().setDirectory(repo1.toFile()).call()) {
            Files.writeString(repo1.resolve("app.env"), "AWS_KEY=AKIAIOSFODNN7BK5WP9Z\n");
            git1.add().addFilepattern("app.env").call();
            git1.commit().setMessage("Add AWS creds").call();
        }

        // Repo 2
        try (Git git2 = Git.init().setDirectory(repo2.toFile()).call()) {
            Files.writeString(repo2.resolve("stripe.env"), "STRIPE_KEY=" + "sk" + "_live_51Mv9K2eZv8Tx7Yq1Wp4Rn6Ls0Dk3Fm8\n");
            git2.add().addFilepattern("stripe.env").call();
            git2.commit().setMessage("Add Stripe key").call();
        }

        List<Path> discovered = MultiRepoScanner.discoverGitRepositories(tempDir, 2);
        assertThat(discovered).containsExactlyInAnyOrder(repo1, repo2);

        MultiRepoScanner multiScanner = new MultiRepoScanner(rules, null, null, 2);
        ScanResult result = multiScanner.scanAll(tempDir);

        assertThat(result).isNotNull();
        assertThat(result.getSummary().getTotalFindings()).isEqualTo(2);

        List<Finding> findings = result.getFindings();
        assertThat(findings).extracting(Finding::getFilePath)
                .anyMatch(p -> p.startsWith("service-auth/"))
                .anyMatch(p -> p.startsWith("service-billing/"));
    }
}
