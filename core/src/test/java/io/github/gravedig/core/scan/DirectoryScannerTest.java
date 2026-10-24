package io.github.gravedig.core.scan;

import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.FindingPresence;
import io.github.gravedig.core.model.Rule;
import io.github.gravedig.core.model.ScanResult;
import io.github.gravedig.core.model.Severity;
import io.github.gravedig.core.rule.RuleLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DirectoryScannerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should detect fake secrets in directory and ignore inline gravedig:ignore comments and binary files")
    void testScanDirectory() throws IOException {
        RuleLoader ruleLoader = new RuleLoader();
        List<Rule> rules = ruleLoader.loadDefaultRules();
        DirectoryScanner scanner = new DirectoryScanner(rules);

        // 1. Text file with fake AWS key
        Path appProperties = tempDir.resolve("application.properties");
        Files.writeString(appProperties, "aws.key=AKIAIOSFODNN7BK5WP9Z\n");

        // 2. Text file with fake GitHub PAT
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "GITHUB_TOKEN=ghp_VpQ7X9kLm2Nb8Zw3Ty5Rc1Dg4Fh6Jk0Mn2Pq\n");

        // 3. Text file with inline ignore marker
        Path ignoredFile = tempDir.resolve("config.py");
        Files.writeString(ignoredFile, "SECRET_KEY = 'AKIA9999888877776666' # gravedig:ignore\n");

        // 4. Binary file containing secret bytes (must be ignored)
        Path binFile = tempDir.resolve("image.png");
        byte[] binaryBytes = new byte[]{0x00, 0x01, 0x02, 0x41, 0x4b, 0x49, 0x41, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30};
        Files.write(binFile, binaryBytes);

        // 5. Clean file
        Path cleanFile = tempDir.resolve("README.txt");
        Files.writeString(cleanFile, "Hello world, no secrets here.\n");

        ScanResult result = scanner.scan(tempDir);

        assertThat(result).isNotNull();
        assertThat(result.getSummary().getFilesScanned()).isGreaterThanOrEqualTo(3);

        List<Finding> findings = result.getFindings();
        assertThat(findings).hasSize(2);

        Finding awsFinding = findings.stream()
                .filter(f -> f.getRuleId().equals("aws-access-key-id"))
                .findFirst()
                .orElse(null);
        assertThat(awsFinding).isNotNull();
        assertThat(awsFinding.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(awsFinding.getPresence()).isEqualTo(FindingPresence.ALIVE);
        assertThat(awsFinding.getSecretRedacted()).isEqualTo("AKIA**************9Z");

        Finding githubFinding = findings.stream()
                .filter(f -> f.getRuleId().equals("github-pat"))
                .findFirst()
                .orElse(null);
        assertThat(githubFinding).isNotNull();
        assertThat(githubFinding.getSecretRedacted()).isEqualTo("ghp_**********************************Pq");

        // Ensure binary file and inline ignored line produced no findings
        assertThat(findings).noneMatch(f -> f.getFilePath().contains("image.png"));
        assertThat(findings).noneMatch(f -> f.getFilePath().contains("config.py"));
    }
}
