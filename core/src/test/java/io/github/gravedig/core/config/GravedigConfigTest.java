package io.github.gravedig.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GravedigConfigTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should parse .gravedig.yml configuration correctly")
    void testConfigParsing() throws IOException {
        String yamlContent = "" +
                "ignorePaths:\n" +
                "  - 'tests/fixtures/**'\n" +
                "  - 'docs/**'\n" +
                "ignorePlaceholders:\n" +
                "  - 'MY_STUB_TOKEN'\n" +
                "allowlist:\n" +
                "  fingerprints:\n" +
                "    - 'salted-hash-to-ignore'\n" +
                "  paths:\n" +
                "    - 'legacy/'\n" +
                "  ruleIds:\n" +
                "    - 'generic-secret-assignment'\n" +
                "entropy:\n" +
                "  enabled: true\n" +
                "  threshold: 4.5\n" +
                "failOn: 'high'\n";

        Path configFile = tempDir.resolve(".gravedig.yml");
        Files.writeString(configFile, yamlContent);

        ConfigLoader loader = new ConfigLoader();
        GravedigConfig config = loader.findAndLoad(tempDir);

        assertThat(config).isNotNull();
        assertThat(config.getIgnorePaths()).contains("tests/fixtures/**", "docs/**");
        assertThat(config.getIgnorePlaceholders()).contains("MY_STUB_TOKEN");
        assertThat(config.getAllowlist().getFingerprints()).contains("salted-hash-to-ignore");
        assertThat(config.getAllowlist().getRuleIds()).contains("generic-secret-assignment");
        assertThat(config.getEntropy().isEnabled()).isTrue();
        assertThat(config.getEntropy().getThreshold()).isEqualTo(4.5);
        assertThat(config.getFailOn()).isEqualTo("high");
    }
}
