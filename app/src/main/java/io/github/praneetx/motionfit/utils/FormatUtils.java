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

    /**
     * Formats an elapsed duration as {@code mm:ss}, or {@code h:mm:ss} after an
     * hour. Only complete seconds are shown; a negative duration becomes zero.
     */
    public static String clockText(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d",
                    hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    /** A TalkBack-friendly duration such as {@code 12 minutes 5 seconds}. */
    public static String durationSpokenText(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        StringBuilder text = new StringBuilder();
        if (hours > 0L) {
            text.append(hours).append(hours == 1L ? " hour " : " hours ");
        }
        if (minutes > 0L || hours > 0L) {
            text.append(minutes).append(minutes == 1L ? " minute " : " minutes ");
        }
        text.append(seconds).append(seconds == 1L ? " second" : " seconds");
        return text.toString();
    }
}
