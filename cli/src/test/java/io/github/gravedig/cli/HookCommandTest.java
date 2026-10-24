package io.github.gravedig.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HookCommandTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should install pre-commit hook into git repository and set executable permission")
    void testInstallHook() throws IOException {
        Path gitDir = tempDir.resolve(".git");
        Files.createDirectories(gitDir);

        int exitCode = new CommandLine(new GravedigCli()).execute("hook", "install", tempDir.toString(), "--fail-on", "high");
        assertThat(exitCode).isEqualTo(0);

        Path hookFile = gitDir.resolve("hooks").resolve("pre-commit");
        assertThat(hookFile).exists();
        assertThat(hookFile.toFile().canExecute()).isTrue();

        String script = Files.readString(hookFile);
        assertThat(script)
                .contains("#!/bin/sh")
                .contains("gravedig")
                .contains("--fail-on high")
                .contains("git commit --no-verify");
    }

    @Test
    @DisplayName("Should return error code 1 when target is not a git repository")
    void testInstallHookNonGitRepo() {
        int exitCode = new CommandLine(new GravedigCli()).execute("hook", "install", tempDir.toString());
        assertThat(exitCode).isEqualTo(1);
    }
}
