package io.github.gravedig.server;

import io.github.gravedig.server.security.AuthManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthManagerTest {

    @Test
    @DisplayName("Generates cryptographically random 32-character hex token")
    void testTokenGeneration() {
        AuthManager auth = new AuthManager();
        assertThat(auth.getToken()).isNotNull().hasSize(32);
        assertThat(auth.isValid(auth.getToken())).isTrue();
        assertThat(auth.isValid("wrong-token")).isFalse();
        assertThat(auth.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("Allows custom token configuration")
    void testCustomToken() {
        AuthManager auth = new AuthManager("my-secret-test-token");
        assertThat(auth.getToken()).isEqualTo("my-secret-test-token");
        assertThat(auth.isValid("my-secret-test-token")).isTrue();
        assertThat(auth.isValid("other-token")).isFalse();
    }
}
