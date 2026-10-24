package io.github.gravedig.core.baseline;

import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BaselineTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should save findings to baseline file and reload them accurately")
    void testSaveAndLoadBaseline() throws IOException {
        Finding finding = Finding.builder()
                .ruleId("aws-access-key-id")
                .ruleName("AWS Access Key ID")
                .severity(Severity.CRITICAL)
                .filePath("src/main/resources/aws.properties")
                .lineNumber(5)
                .fingerprint("abc123saltedhash")
                .secretRedacted("AKIA**************LE")
                .build();

        Path baselinePath = tempDir.resolve(".gravedig-baseline.json");
        BaselineManager.save(baselinePath, List.of(finding));

        assertThat(baselinePath).exists();

        BaselineManager loaded = BaselineManager.load(baselinePath);
        assertThat(loaded.isBaselined(finding)).isTrue();

        Finding otherFinding = Finding.builder()
                .ruleId("stripe-secret-key")
                .ruleName("Stripe Secret Key")
                .severity(Severity.CRITICAL)
                .filePath("payment.js")
                .lineNumber(12)
                .fingerprint("differenthash")
                .secretRedacted("sk_l********************89")
                .build();

        assertThat(loaded.isBaselined(otherFinding)).isFalse();
    }
}
