package io.github.gravedig.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ScanCommandTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("ScanCommand should exit with 0 on clean repository")
    void testScanCleanRepo() throws IOException {
        Files.writeString(tempDir.resolve("Main.java"), "public class Main { public static void main(String[] args) {} }");

        int exitCode = new CommandLine(new GravedigCli()).execute("scan", tempDir.toString());
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    @DisplayName("ScanCommand should exit with 1 when secret exceeds fail-on severity")
    void testScanWithSecretExceedingFailOn() throws IOException {
        Files.writeString(tempDir.resolve("aws_creds.txt"), "aws_access_key_id=AKIAIOSFODNN7BK5WP9Z\n");

        int exitCode = new CommandLine(new GravedigCli()).execute("scan", tempDir.toString(), "--fail-on", "critical");
        assertThat(exitCode).isEqualTo(1);
    }

    @Test
    @DisplayName("ScanCommand should exit with 0 when secret is below fail-on severity")
    void testScanWithSecretBelowFailOn() throws IOException {
        // generic-secret-assignment is MEDIUM
        Files.writeString(tempDir.resolve("config.env"), "password = 'SuperSecretLongPassword123'\n");

        int exitCode = new CommandLine(new GravedigCli()).execute("scan", tempDir.toString(), "--fail-on", "critical");
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    @DisplayName("ScanCommand should support JSON format output")
    void testScanJsonFormat() throws IOException {
        Files.writeString(tempDir.resolve("config.env"), "password = 'SuperSecretLongPassword123'\n");

        int exitCode = new CommandLine(new GravedigCli()).execute("scan", tempDir.toString(), "--format", "json");
        // default fail-on is low, so exit code is 1
        assertThat(exitCode).isEqualTo(1);
    }

    @Test
    @DisplayName("ScanCommand should generate baseline and exit with 0 when using baseline")
    void testBaselineWorkflow() throws IOException {
        Files.writeString(tempDir.resolve("keys.txt"), "aws_access_key_id=AKIAIOSFODNN7BK5WP9Z\n");
        Path baselineFile = tempDir.resolve("baseline.json");

        // 1. Generate baseline -> exits with 0
        int genExit = new CommandLine(new GravedigCli()).execute("scan", tempDir.toString(), "--generate-baseline", baselineFile.toString());
        assertThat(genExit).isEqualTo(0);
        assertThat(baselineFile).exists();

        // 2. Scan with baseline -> findings marked ACCEPTED_RISK, so exits with 0 instead of failing
        int scanExit = new CommandLine(new GravedigCli()).execute("scan", tempDir.toString(), "--baseline", baselineFile.toString(), "--fail-on", "critical");
        assertThat(scanExit).isEqualTo(0);
    }

    @Test
    @DisplayName("ScanCommand should scan git history with --history flag")
    void testHistoryScanCommand() throws Exception {
        Path gitRepo = tempDir.resolve("repo");
        Files.createDirectories(gitRepo);
        try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.init().setDirectory(gitRepo.toFile()).call()) {
            Files.writeString(gitRepo.resolve("creds.txt"), "aws_access_key_id=AKIAIOSFODNN7BK5WP9Z\n");
            git.add().addFilepattern("creds.txt").call();
            git.commit().setMessage("Initial leaked key").call();
        }

        int exitCode = new CommandLine(new GravedigCli()).execute("scan", gitRepo.toString(), "--history", "--fail-on", "critical");
        assertThat(exitCode).isEqualTo(1);
    }
}
