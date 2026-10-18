package io.github.praneetx.motionfit.utils;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Small formatting helpers so numbers look friendly (for example {@code 8,000}
 * instead of {@code 8000}) without repeating NumberFormat code everywhere.
 */
public final class FormatUtils {

    private FormatUtils() {
        // Utility class.
    }

    public static String intText(int value) {
        return NumberFormat.getIntegerInstance(Locale.getDefault()).format(value);
    }

    public static String longText(long value) {
        return NumberFormat.getIntegerInstance(Locale.getDefault()).format(value);
    }

    /** Formats a 24-hour time such as {@code 18:05}. */
    public static String timeText(int hour, int minute) {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }
}
