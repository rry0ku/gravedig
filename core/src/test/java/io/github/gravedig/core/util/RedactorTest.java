package io.github.gravedig.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedactorTest {

    @Test
    @DisplayName("Should correctly redact standard long secrets with first 4 and last 2 characters preserved")
    void testStandardRedaction() {
        String awsKey = "AKIAIOSFODNN7EXAMPLE";
        String redacted = Redactor.redact(awsKey);
        // length is 20: 4 prefix + 14 * + 2 suffix
        assertThat(redacted).isEqualTo("AKIA**************LE");
    }

    @Test
    @DisplayName("Should correctly handle short secrets")
    void testShortSecrets() {
        assertThat(Redactor.redact("ab")).isEqualTo("**");
        assertThat(Redactor.redact("abcde")).isEqualTo("a***e");
        assertThat(Redactor.redact("abcdef")).isEqualTo("a****f");
        assertThat(Redactor.redact("1234567")).isEqualTo("1234*67");
    }

    @Test
    @DisplayName("Should correctly redact raw secret in snippet")
    void testRedactSnippet() {
        String line = "export AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE # do not commit";
        String secret = "AKIAIOSFODNN7EXAMPLE";
        String redactedSnippet = Redactor.redactSnippet(line, secret);

        assertThat(redactedSnippet)
                .doesNotContain("AKIAIOSFODNN7EXAMPLE")
                .contains("AKIA**************LE");
    }
}
