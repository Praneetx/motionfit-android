package io.github.praneetx.motionfit.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Central date/time helpers.
 *
 * <p>Dates that identify a calendar day are always stored as ISO-8601 text
 * ({@code YYYY-MM-DD}, for example {@code 2026-07-11}). Timestamps are stored as
 * {@code YYYY-MM-DDTHH:mm:ss}. Using fixed-width ISO strings means the SQLite
 * {@code BETWEEN} / {@code ORDER BY} comparisons work correctly as plain text.</p>
 *
 * <p>The app uses the device's default time zone (local calendar dates).</p>
 */
public final class DateUtils {

    private DateUtils() {
        // Utility class.
    }

    /**
     * Storage format for a calendar date, e.g. {@code 2026-07-11}.
     *
     * <p>{@code uuuu} (proleptic year) plus {@link ResolverStyle#STRICT} is used on
     * purpose: the default SMART resolver would quietly turn an impossible date
     * such as {@code 2026-02-30} into {@code 2026-02-28} instead of rejecting it.</p>
     */
    public static final DateTimeFormatter ISO_DATE = DateTimeFormatter
            .ofPattern("uuuu-MM-dd", Locale.US)
            .withResolverStyle(ResolverStyle.STRICT);
    /** Storage format for a timestamp, e.g. {@code 2026-07-11T14:30:05}. */
    public static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.US);

    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DAY =
            DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter WEEKDAY_SHORT =
            DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);

    public static String todayIso() {
        return LocalDate.now().format(ISO_DATE);
    }

    public static String nowTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP);
    }

    /** True when {@code raw} is a real calendar date in {@code YYYY-MM-DD} form. */
    public static boolean isValidIsoDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }
        try {
            LocalDate.parse(raw.trim(), ISO_DATE);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public static LocalDate parseIsoDate(String raw) {
        return LocalDate.parse(raw.trim(), ISO_DATE);
    }

    /** Converts {@code 2026-07-11} to something like {@code Sat, 11 Jul 2026}. */
    public static String formatForDisplay(String isoDate) {
        try {
            return parseIsoDate(isoDate).format(DISPLAY_DATE);
        } catch (RuntimeException e) {
            return isoDate == null ? "" : isoDate;
        }
    }

    /** Short label such as {@code 11 Jul} used by chart axes. */
    public static String shortDay(String isoDate) {
        try {
            return parseIsoDate(isoDate).format(SHORT_DAY);
        } catch (RuntimeException e) {
            return isoDate == null ? "" : isoDate;
        }
    }

    /** Weekday label such as {@code Sat}. */
    public static String weekdayShort(String isoDate) {
        try {
            return parseIsoDate(isoDate).format(WEEKDAY_SHORT);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Monday of the week that contains {@code date}. */
    public static LocalDate startOfWeek(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Sunday of the week that contains {@code date}. */
    public static LocalDate endOfWeek(LocalDate date) {
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    public static LocalDate startOfMonth(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    public static LocalDate endOfMonth(LocalDate date) {
        return date.withDayOfMonth(date.lengthOfMonth());
    }

    /** Every ISO date from {@code start} to {@code end} inclusive (both must be valid). */
    public static List<String> isoDatesBetween(LocalDate start, LocalDate end) {
        List<String> dates = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            dates.add(cursor.format(ISO_DATE));
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    /** Label such as {@code 05 Jul - 11 Jul, 2026}. */
    public static String formatRange(LocalDate start, LocalDate end) {
        return start.format(SHORT_DAY) + " - " + end.format(SHORT_DAY) + ", " + end.getYear();
    }

    /** Label such as {@code July 2026}. */
    public static String formatMonth(LocalDate date) {
        return date.format(MONTH_LABEL);
    }
}
