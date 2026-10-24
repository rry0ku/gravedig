package io.github.gravedig.core.model;

public enum Severity {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int level;

    Severity(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean isAtLeast(Severity other) {
        return this.level >= other.level;
    }

    public static Severity fromString(String val) {
        if (val == null || val.isBlank()) {
            return MEDIUM;
        }
        return Severity.valueOf(val.trim().toUpperCase());
    }
}
