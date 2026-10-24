package io.github.gravedig.core.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineValidatorTest {

    @Test
    @DisplayName("Should validate AWS Access Key ID structure and length")
    void testAwsValidator() {
        ValidationResult valid = OfflineValidator.validate("aws-access-key-id", "AKIAIOSFODNN7EXAMPLE");
        assertThat(valid.isValid()).isTrue();
        assertThat(valid.shouldDrop()).isFalse();

        // Invalid length
        ValidationResult shortKey = OfflineValidator.validate("aws-access-key-id", "AKIA123");
        assertThat(shortKey.shouldDrop()).isTrue();

        // Invalid prefix
        ValidationResult badPrefix = OfflineValidator.validate("aws-access-key-id", "ZZZZIOSFODNN7EXAMPLE");
        assertThat(badPrefix.shouldDrop()).isTrue();
    }

    @Test
    @DisplayName("Should validate JWT segments and decodable header")
    void testJwtValidator() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"1234567890\",\"name\":\"John Doe\"}".getBytes());
        String signature = "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        String validJwt = header + "." + payload + "." + signature;

        ValidationResult res = OfflineValidator.validate("jwt-token", validJwt);
        assertThat(res.isValid()).isTrue();
        assertThat(res.shouldDrop()).isFalse();

        // Malformed JWT with 2 parts only
        ValidationResult invalidJwt = OfflineValidator.validate("jwt-token", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0");
        assertThat(invalidJwt.shouldDrop()).isTrue();
    }

    @Test
    @DisplayName("Should validate Private Key header boundary")
    void testPrivateKeyHeader() {
        ValidationResult valid = OfflineValidator.validate("private-key-header", "-----BEGIN RSA PRIVATE KEY-----");
        assertThat(valid.isValid()).isTrue();

        ValidationResult invalid = OfflineValidator.validate("private-key-header", "NOT A REAL KEY");
        assertThat(invalid.shouldDrop()).isTrue();
    }

    @Test
    @DisplayName("Should validate Stripe live key structure")
    void testStripeKeyValidator() {
        ValidationResult valid = OfflineValidator.validate("stripe-secret-key", "sk" + "_live_51AbcdefghijklmnopqrstuvwxyzA123456789");
        assertThat(valid.isValid()).isTrue();

        ValidationResult invalid = OfflineValidator.validate("stripe-secret-key", "sk_test_123");
        assertThat(invalid.shouldDrop()).isTrue();
    }
}
