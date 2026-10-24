package io.github.gravedig.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@Command(
        name = "hook",
        description = "Manage git hooks (e.g. pre-commit)",
        mixinStandardHelpOptions = true,
        subcommands = {HookCommand.InstallHookCommand.class}
)
public class HookCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("Use 'gravedig hook install' to install the pre-commit hook.");
        return 0;
    }

    @Command(
            name = "install",
            description = "Install pre-commit secret scanning hook into .git/hooks",
            mixinStandardHelpOptions = true
    )
    public static class InstallHookCommand implements Callable<Integer> {

        @Parameters(
                index = "0",
                description = "Path to git repository (default: current directory)",
                defaultValue = "."
        )
        private String targetPathStr;

        @Option(
                names = {"--fail-on"},
                description = "Minimum severity to block commits: low, medium, high, critical (default: ${DEFAULT-VALUE})",
                defaultValue = "high"
        )
        private String failOn;

        @Override
        public Integer call() {
            Path target = Paths.get(targetPathStr).toAbsolutePath().normalize();
            Path gitDir = target.resolve(".git");

            if (!Files.exists(gitDir)) {
                System.err.println("Error: Directory is not a git repository: " + target);
                return 1;
            }

            Path hooksDir = gitDir.resolve("hooks");
            try {
                if (!Files.exists(hooksDir)) {
                    Files.createDirectories(hooksDir);
                }

                Path preCommitHook = hooksDir.resolve("pre-commit");

                String hookScript = "#!/bin/sh\n" +
                        "# gravedig pre-commit secret scanning hook\n" +
                        "# Blocks commits if credentials or secrets are detected\n\n" +
                        "echo \"[gravedig] Scanning for leaked credentials before commit...\"\n\n" +
                        "if command -v gravedig >/dev/null 2>&1; then\n" +
                        "    CMD=\"gravedig\"\n" +
                        "elif [ -n \"$GRAVEDIG_JAR\" ] && [ -f \"$GRAVEDIG_JAR\" ]; then\n" +
                        "    CMD=\"java -jar \\\"$GRAVEDIG_JAR\\\"\"\n" +
                        "else\n" +
                        "    REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || echo \".\")\n" +
                        "    if [ -f \"$REPO_ROOT/cli/target/gravedig.jar\" ]; then\n" +
                        "        CMD=\"java -jar \\\"$REPO_ROOT/cli/target/gravedig.jar\\\"\"\n" +
                        "    else\n" +
                        "        CMD=\"gravedig\"\n" +
                        "    fi\n" +
                        "fi\n\n" +
                        "eval \"$CMD scan . --fail-on " + failOn + "\"\n" +
                        "EXIT_CODE=$?\n\n" +
                        "if [ $EXIT_CODE -ne 0 ]; then\n" +
                        "    echo \"\"\n" +
                        "    echo \"[gravedig] Commit aborted! Secrets detected exceeding severity threshold (" + failOn + ").\"\n" +
                        "    echo \"  - Remove secret or add inline comment: # gravedig:ignore\"\n" +
                        "    echo \"  - To bypass in emergency: git commit --no-verify\"\n" +
                        "    echo \"\"\n" +
                        "    exit 1\n" +
                        "fi\n\n" +
                        "exit 0\n";

                Files.writeString(preCommitHook, hookScript, StandardCharsets.UTF_8);

                File hookFile = preCommitHook.toFile();
                boolean execSet = hookFile.setExecutable(true, false);
                if (!execSet) {
                    System.err.println("Warning: Could not set executable permission on " + preCommitHook);
                }

                System.out.println("✓ Successfully installed pre-commit hook at: " + preCommitHook);
                System.out.println("  Hook will block commits containing secrets of severity '" + failOn + "' or higher.");
                return 0;
            } catch (IOException e) {
                System.err.println("Failed to install pre-commit hook: " + e.getMessage());
                return 2;
            }
        }
    }
}
