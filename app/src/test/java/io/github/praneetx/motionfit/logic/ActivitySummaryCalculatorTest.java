package io.github.praneetx.motionfit.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Verifies the Kotlin {@link ActivitySummaryCalculator}, including zero-filling
 * and rounding behaviour.
 */
public class ActivitySummaryCalculatorTest {

    private static DayActivity day(String date, int steps, int minutes, int count) {
        return new DayActivity(date, steps, minutes, count);
    }

    @Test
    public void zeroFill_addsMissingDaysAsZero() {
        List<String> dates = Arrays.asList("2026-07-01", "2026-07-02", "2026-07-03");
        List<DayActivity> recorded = Collections.singletonList(day("2026-07-02", 500, 20, 1));

        List<DayActivity> series = ActivitySummaryCalculator.zeroFill(dates, recorded);

        assertEquals(3, series.size());
        assertEquals(0, series.get(0).getSteps());
        assertEquals(500, series.get(1).getSteps());
        assertEquals(20, series.get(1).getWorkoutMinutes());
        assertEquals(1, series.get(1).getWorkoutCount());
        assertEquals(0, series.get(2).getWorkoutMinutes());
    }

    @Test
    public void zeroFill_keepsDatesInGivenOrder() {
        List<String> dates = Arrays.asList("2026-07-03", "2026-07-01", "2026-07-02");
        List<DayActivity> series = ActivitySummaryCalculator.zeroFill(dates, new ArrayList<>());
        assertEquals("2026-07-03", series.get(0).getDateIso());
        assertEquals("2026-07-01", series.get(1).getDateIso());
        assertEquals("2026-07-02", series.get(2).getDateIso());
    }

    @Test
    public void totals_sumEveryDayExactlyOnce() {
        List<DayActivity> days = Arrays.asList(
                day("2026-07-01", 1000, 10, 1),
                day("2026-07-02", 2000, 20, 2),
                day("2026-07-03", 0, 0, 0));
        assertEquals(3000, ActivitySummaryCalculator.totalSteps(days));
        assertEquals(30, ActivitySummaryCalculator.totalMinutes(days));
        assertEquals(3, ActivitySummaryCalculator.totalWorkouts(days));
    }

    @Test
    public void averageSteps_roundsToNearest() {
        List<DayActivity> days = Arrays.asList(
                day("2026-07-01", 1000, 0, 0),
                day("2026-07-02", 2000, 0, 0));
        assertEquals(1500, ActivitySummaryCalculator.averageSteps(days));

        List<DayActivity> thirds = Arrays.asList(
                day("a", 1, 0, 0), day("b", 2, 0, 0), day("c", 2, 0, 0));
        assertEquals(2, ActivitySummaryCalculator.averageSteps(thirds)); // 1.67 -> 2
    }

    @Test
    public void bestStepDay_returnsHighestDay() {
        List<DayActivity> days = Arrays.asList(
                day("2026-07-01", 1000, 0, 0),
                day("2026-07-02", 5000, 0, 0),
                day("2026-07-03", 3000, 0, 0));
        DayActivity best = ActivitySummaryCalculator.bestStepDay(days);
        assertNotNull(best);
        assertEquals("2026-07-02", best.getDateIso());
        assertEquals(5000, best.getSteps());
    }

    @Test
    public void activeDays_countsDaysWithAnyActivity() {
        List<DayActivity> days = Arrays.asList(
                day("2026-07-01", 1000, 0, 0),
                day("2026-07-02", 0, 0, 0),
                day("2026-07-03", 0, 15, 1),
                day("2026-07-04", 0, 0, 0));
        assertEquals(2, ActivitySummaryCalculator.activeDays(days));
    }

    @Test
    public void emptyList_isHandledSafely() {
        List<DayActivity> empty = new ArrayList<>();
        assertEquals(0, ActivitySummaryCalculator.totalSteps(empty));
        assertEquals(0, ActivitySummaryCalculator.averageSteps(empty));
        assertNull(ActivitySummaryCalculator.bestStepDay(empty));
        assertEquals(0, ActivitySummaryCalculator.activeDays(empty));
    }
}
