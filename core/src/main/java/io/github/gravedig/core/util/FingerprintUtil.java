package io.github.gravedig.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class FingerprintUtil {

    private static final String DEFAULT_SALT = "gravedig-secret-fingerprint-salt-v1";

    private FingerprintUtil() {
    }

    public static String calculateFingerprint(String rawSecret) {
        return calculateFingerprint(rawSecret, DEFAULT_SALT);
    }

    public static String calculateFingerprint(String rawSecret, String salt) {
        if (rawSecret == null || rawSecret.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(rawSecret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
