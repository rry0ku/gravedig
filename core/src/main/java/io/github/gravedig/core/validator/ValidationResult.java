package io.github.gravedig.core.validator;

public class ValidationResult {
    private final boolean valid;
    private final boolean shouldDrop;
    private final String reason;
    private final double confidence;

    public ValidationResult(boolean valid, boolean shouldDrop, String reason, double confidence) {
        this.valid = valid;
        this.shouldDrop = shouldDrop;
        this.reason = reason != null ? reason : "";
        this.confidence = confidence;
    }

    public static ValidationResult valid(String reason, double confidence) {
        return new ValidationResult(true, false, reason, confidence);
    }

    public static ValidationResult drop(String reason) {
        return new ValidationResult(false, true, reason, 0.0);
    }

    public static ValidationResult downgrade(String reason, double confidence) {
        return new ValidationResult(false, false, reason, confidence);
    }

    public boolean isValid() { return valid; }
    public boolean shouldDrop() { return shouldDrop; }
    public String getReason() { return reason; }
    public double getConfidence() { return confidence; }
}
