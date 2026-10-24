package io.github.gravedig.core.util;

public final class Redactor {

    private Redactor() {
    }

    /**
     * Redacts a secret by displaying the first 4 characters and last 2 characters,
     * replacing all intermediate characters with asterisks (*).
     * For shorter secrets (<= 6 chars), preserves at most 1 leading and 1 trailing character.
     */
    public static String redact(String rawSecret) {
        if (rawSecret == null || rawSecret.isEmpty()) {
            return "******";
        }
        int len = rawSecret.length();
        if (len <= 2) {
            return "*".repeat(len);
        }
        if (len <= 6) {
            return rawSecret.charAt(0) + "*".repeat(len - 2) + rawSecret.charAt(len - 1);
        }

        int prefixLen = 4;
        int suffixLen = 2;
        int maskedCount = len - (prefixLen + suffixLen);

        return rawSecret.substring(0, prefixLen) +
                "*".repeat(maskedCount) +
                rawSecret.substring(len - suffixLen);
    }

    /**
     * Replaces occurrences of the raw secret within a snippet or line with its redacted counterpart.
     */
    public static String redactSnippet(String line, String rawSecret) {
        if (line == null || rawSecret == null || rawSecret.isEmpty()) {
            return line != null ? line.trim() : "";
        }
        String redacted = redact(rawSecret);
        return line.replace(rawSecret, redacted).trim();
    }
}
