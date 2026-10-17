package com.example.fitnesstracker.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Pure JVM tests for {@link DateUtils} - no Android device required.
 */
public class DateUtilsTest {

    @Test
    public void todayIso_hasExpectedFormatAndIsValid() {
        String today = DateUtils.todayIso();
        assertTrue("today must be YYYY-MM-DD but was " + today,
                today.matches("\\d{4}-\\d{2}-\\d{2}"));
        assertTrue(DateUtils.isValidIsoDate(today));
    }

    @Test
    public void isValidIsoDate_acceptsRealDates() {
        assertTrue(DateUtils.isValidIsoDate("2026-07-11"));
        assertTrue(DateUtils.isValidIsoDate("2024-02-29")); // leap day
    }

    @Test
    public void isValidIsoDate_rejectsBadInput() {
        assertFalse(DateUtils.isValidIsoDate(null));
        assertFalse(DateUtils.isValidIsoDate(""));
        assertFalse(DateUtils.isValidIsoDate("   "));
        assertFalse(DateUtils.isValidIsoDate("2026-13-01"));   // month 13 does not exist
        assertFalse(DateUtils.isValidIsoDate("2026-02-30"));   // February has no 30th
        assertFalse(DateUtils.isValidIsoDate("2025-02-29"));   // 2025 is not a leap year
        assertFalse(DateUtils.isValidIsoDate("11-07-2026"));   // wrong order
        assertFalse(DateUtils.isValidIsoDate("2026/07/11"));   // wrong separator
    }

    @Test
    public void weekBoundaries_areMondayToSunday() {
        LocalDate[] samples = {
                LocalDate.of(2026, 7, 11),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2025, 12, 31)
        };
        for (LocalDate sample : samples) {
            LocalDate start = DateUtils.startOfWeek(sample);
            LocalDate end = DateUtils.endOfWeek(sample);

            assertEquals(DayOfWeek.MONDAY, start.getDayOfWeek());
            assertEquals(DayOfWeek.SUNDAY, end.getDayOfWeek());
            assertEquals("a week is exactly 7 days", start.plusDays(6), end);
            assertFalse("start must not be after the sample", sample.isBefore(start));
            assertFalse("end must not be before the sample", sample.isAfter(end));
        }
    }

    @Test
    public void monthBoundaries_handleLeapYears() {
        assertEquals(LocalDate.of(2026, 2, 28), DateUtils.endOfMonth(LocalDate.of(2026, 2, 10)));
        assertEquals(LocalDate.of(2024, 2, 29), DateUtils.endOfMonth(LocalDate.of(2024, 2, 10)));
        assertEquals(LocalDate.of(2026, 7, 31), DateUtils.endOfMonth(LocalDate.of(2026, 7, 4)));
        assertEquals(LocalDate.of(2026, 7, 1), DateUtils.startOfMonth(LocalDate.of(2026, 7, 19)));
    }

    @Test
    public void isoDatesBetween_includesBothEnds() {
        List<String> dates = DateUtils.isoDatesBetween(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5));
        assertEquals(5, dates.size());
        assertEquals("2026-07-01", dates.get(0));
        assertEquals("2026-07-05", dates.get(4));
    }

    @Test
    public void isoDatesBetween_crossesMonthBoundary() {
        List<String> dates = DateUtils.isoDatesBetween(
                LocalDate.of(2026, 1, 30), LocalDate.of(2026, 2, 2));
        assertEquals(4, dates.size());
        assertEquals("2026-01-30", dates.get(0));
        assertEquals("2026-02-01", dates.get(2));
        assertEquals("2026-02-02", dates.get(3));
    }

    @Test
    public void formatForDisplay_isHumanReadable() {
        String text = DateUtils.formatForDisplay("2026-07-11");
        assertTrue(text.contains("11"));
        assertTrue(text.contains("Jul"));
        assertTrue(text.contains("2026"));
    }
}
