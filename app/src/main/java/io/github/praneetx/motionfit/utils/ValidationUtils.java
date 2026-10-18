package io.github.praneetx.motionfit.utils;

/**
 * Input validation helpers shared by every form in the app.
 *
 * <p>Each parser returns a {@link ParsedInt} that either holds a valid value or a
 * specific {@link ValidationError}. Activities map the error to a friendly
 * message so the user always knows exactly what went wrong.</p>
 */
public final class ValidationUtils {

    private ValidationUtils() {
        // Utility class.
    }

    /** Why a piece of text was rejected. */
    public enum ValidationError {
        NONE,
        EMPTY,
        NOT_A_NUMBER,
        NEGATIVE,
        ZERO_NOT_ALLOWED,
        TOO_LARGE
    }

    /** Result of parsing one text field. */
    public static final class ParsedInt {
        public final int value;
        public final ValidationError error;

        private ParsedInt(int value, ValidationError error) {
            this.value = value;
            this.error = error;
        }

        public boolean isValid() {
            return error == ValidationError.NONE;
        }

        static ParsedInt invalid(ValidationError error) {
            return new ParsedInt(0, error);
        }
    }

    // Practical upper bounds so absurd values can never be stored.
    public static final int MAX_STEPS = 1_000_000;
    public static final int MAX_MINUTES = 24 * 60;
    public static final int MAX_STEP_GOAL = 1_000_000;
    public static final int MAX_MINUTES_GOAL = 24 * 60;

    /**
     * Parses a whole number that must be zero or greater (used for step totals,
     * where 0 is a valid value).
     */
    public static ParsedInt parseNonNegative(String raw, int max) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return ParsedInt.invalid(ValidationError.EMPTY);
        }
        if (text.charAt(0) == '-') {
            // Explicitly detect negatives so the user gets a clear message.
            String digits = text.substring(1);
            if (isAllDigits(digits)) {
                return ParsedInt.invalid(ValidationError.NEGATIVE);
            }
            return ParsedInt.invalid(ValidationError.NOT_A_NUMBER);
        }
        if (!isAllDigits(text)) {
            return ParsedInt.invalid(ValidationError.NOT_A_NUMBER);
        }
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return ParsedInt.invalid(ValidationError.TOO_LARGE);
        }
        if (value > max) {
            return ParsedInt.invalid(ValidationError.TOO_LARGE);
        }
        return new ParsedInt(value, ValidationError.NONE);
    }

    /**
     * Parses a whole number that must be strictly greater than zero (durations
     * and goals). Zero is rejected with {@link ValidationError#ZERO_NOT_ALLOWED}.
     */
    public static ParsedInt parsePositive(String raw, int max) {
        ParsedInt parsed = parseNonNegative(raw, max);
        if (!parsed.isValid()) {
            return parsed;
        }
        if (parsed.value == 0) {
            return ParsedInt.invalid(ValidationError.ZERO_NOT_ALLOWED);
        }
        return parsed;
    }

    private static boolean isAllDigits(String text) {
        if (text.isEmpty()) {
            return false;
        }
        // Manual loop instead of a regex keeps this cheap and easy to read.
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
