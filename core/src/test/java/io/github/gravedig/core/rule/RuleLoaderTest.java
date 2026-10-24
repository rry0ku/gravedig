package io.github.gravedig.core.rule;

import io.github.gravedig.core.model.Rule;
import io.github.gravedig.core.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleLoaderTest {

    @Test
    @DisplayName("Should load at least 15 default rules successfully from YAML")
    void testLoadDefaultRules() {
        RuleLoader loader = new RuleLoader();
        List<Rule> rules = loader.loadDefaultRules();

        assertThat(rules).isNotNull().hasSizeGreaterThanOrEqualTo(15);

        for (Rule rule : rules) {
            assertThat(rule.getId()).isNotEmpty();
            assertThat(rule.getName()).isNotEmpty();
            assertThat(rule.getRegex()).isNotEmpty();
            assertThat(rule.getCompiledPattern()).isNotNull();
            assertThat(rule.getSeverity()).isNotNull();
            assertThat(rule.getDescription()).isNotEmpty();
            assertThat(rule.getRemediation()).isNotEmpty();
        }

        // Verify specific known rules are present
        assertThat(rules).extracting(Rule::getId)
                .contains(
                        "aws-access-key-id",
                        "aws-secret-access-key",
                        "github-pat",
                        "gitlab-pat",
                        "slack-token",
                        "slack-webhook",
                        "stripe-secret-key",
                        "twilio-api-key",
                        "sendgrid-api-key",
                        "npm-access-token",
                        "pypi-api-token",
                        "jwt-token",
                        "private-key-header",
                        "db-connection-url",
                        "generic-secret-assignment"
                );
    }
}
