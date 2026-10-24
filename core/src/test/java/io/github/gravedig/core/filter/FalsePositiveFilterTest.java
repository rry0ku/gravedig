package io.github.gravedig.core.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FalsePositiveFilterTest {

    private final FalsePositiveFilter filter = new FalsePositiveFilter();

    @Test
    @DisplayName("Should detect common dummy placeholders")
    void testPlaceholders() {
        assertThat(filter.isPlaceholder("YOUR_API_KEY")).isTrue();
        assertThat(filter.isPlaceholder("your-api-key-here")).isTrue();
        assertThat(filter.isPlaceholder("example_token_12345")).isTrue();
        assertThat(filter.isPlaceholder("xxxxxxxxxxxxxxxxxxxx")).isTrue();
        assertThat(filter.isPlaceholder("changeme")).isTrue();
        assertThat(filter.isPlaceholder("dummy_password")).isTrue();
        assertThat(filter.isPlaceholder("0000000000000000")).isTrue();

        // Real fake secret should NOT be flagged as placeholder
        assertThat(filter.isPlaceholder("AKIAJ3V7B4QW3Z47WXYZ")).isFalse();
    }

    @Test
    @DisplayName("Should ignore lockfiles and vendor folders")
    void testIgnoredFilesAndPaths() {
        assertThat(filter.isIgnoredFile(Paths.get("package-lock.json"))).isTrue();
        assertThat(filter.isIgnoredFile(Paths.get("yarn.lock"))).isTrue();
        assertThat(filter.isIgnoredFile(Paths.get("Cargo.lock"))).isTrue();
        assertThat(filter.isIgnoredFile(Paths.get("node_modules/express/index.js"))).isTrue();
        assertThat(filter.isIgnoredFile(Paths.get("target/classes/App.class"))).isTrue();
        assertThat(filter.isIgnoredFile(Paths.get("vendor/bundle/ruby.rb"))).isTrue();

        // Legitimate source code should NOT be ignored
        assertThat(filter.isIgnoredFile(Paths.get("src/main/resources/application.properties"))).isFalse();
    }

    @Test
    @DisplayName("Should support custom placeholder and ignore patterns")
    void testCustomFilters() {
        FalsePositiveFilter custom = new FalsePositiveFilter(
                Set.of("custom_placeholder"),
                Set.of("test_fixtures")
        );

        assertThat(custom.isPlaceholder("my_custom_placeholder_value")).isTrue();
        assertThat(custom.isIgnoredFile(Paths.get("src/test_fixtures/data.json"))).isTrue();
    }
}
