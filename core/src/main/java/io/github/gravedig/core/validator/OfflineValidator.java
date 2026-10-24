package io.github.gravedig.core.validator;

import io.github.gravedig.core.entropy.EntropyCalculator;

import java.util.Base64;
import java.util.regex.Pattern;

public final class OfflineValidator {

    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("^(AKIA|ASIA|ABIA|ACCA)[0-9A-Z]{16}$");
    private static final Pattern BASE64URL_SEGMENT = Pattern.compile("^[A-Za-z0-9_-]+$");

    private OfflineValidator() {
    }

    public static ValidationResult validate(String ruleId, String rawSecret) {
        if (rawSecret == null || rawSecret.trim().isEmpty()) {
            return ValidationResult.drop("Empty secret");
        }

        switch (ruleId) {
            case "aws-access-key-id":
                return validateAwsAccessKey(rawSecret);
            case "jwt-token":
                return validateJwt(rawSecret);
            case "private-key-header":
                return validatePrivateKeyHeader(rawSecret);
            case "stripe-secret-key":
                return validateStripeKey(rawSecret);
            default:
                return validateGeneric(rawSecret);
        }
    }

    private static ValidationResult validateAwsAccessKey(String secret) {
        String trimmed = secret.trim();
        if (trimmed.length() != 20) {
            return ValidationResult.drop("AWS Key length must be exactly 20 characters");
        }
        if (!AWS_KEY_PATTERN.matcher(trimmed).matches()) {
            return ValidationResult.drop("AWS Key prefix or charset invalid");
        }
        if (!EntropyCalculator.hasSufficientDiversity(trimmed.substring(4), 6)) {
            return ValidationResult.drop("AWS Key suffix lacks entropy/diversity");
        }
        return ValidationResult.valid("Valid AWS Access Key ID structure", 0.95);
    }

    private static ValidationResult validateJwt(String secret) {
        String[] parts = secret.split("\\.");
        if (parts.length != 3) {
            return ValidationResult.drop("JWT must have exactly three dot-separated segments");
        }
        for (String part : parts) {
            if (!BASE64URL_SEGMENT.matcher(part).matches()) {
                return ValidationResult.drop("JWT segment contains invalid Base64URL characters");
            }
        }

        // Try decoding JWT header (part 0)
        try {
            byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
            String headerJson = new String(headerBytes).toLowerCase();
            if (!headerJson.contains("alg") && !headerJson.contains("typ")) {
                return ValidationResult.downgrade("JWT header missing 'alg' or 'typ' fields", 0.4);
            }
        } catch (IllegalArgumentException e) {
            return ValidationResult.drop("Malformed Base64 in JWT header");
        }

        return ValidationResult.valid("Valid JWT structure with decoded header", 0.9);
    }

    private static ValidationResult validatePrivateKeyHeader(String secret) {
        if (secret.contains("BEGIN") && secret.contains("PRIVATE KEY")) {
            return ValidationResult.valid("Valid cryptographic private key envelope", 0.98);
        }
        return ValidationResult.drop("Missing standard private key boundaries");
    }

    private static ValidationResult validateStripeKey(String secret) {
        if (!secret.startsWith("sk_live_") && !secret.startsWith("rk_live_")) {
            return ValidationResult.drop("Stripe key must start with sk_live_ or rk_live_");
        }
        if (secret.length() < 24) {
            return ValidationResult.drop("Stripe live key length too short");
        }
        if (!EntropyCalculator.hasSufficientDiversity(secret.substring(8), 8)) {
            return ValidationResult.drop("Stripe key lacks sufficient entropy");
        }
        return ValidationResult.valid("Valid Stripe live secret format", 0.95);
    }

    private static ValidationResult validateGeneric(String secret) {
        if (!EntropyCalculator.hasSufficientDiversity(secret, 4)) {
            return ValidationResult.drop("Secret string lacks character diversity");
        }
        double entropy = EntropyCalculator.calculate(secret);
        if (entropy < 2.0 && secret.length() > 10) {
            return ValidationResult.downgrade("Low entropy value: " + String.format("%.2f", entropy), 0.3);
        }
        return ValidationResult.valid("Passed generic validation checks", 0.75);
    }
}
