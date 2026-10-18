package io.github.praneetx.motionfit.logic

import kotlin.math.roundToInt

/**
 * Kotlin aggregation logic for the daily / weekly / monthly progress screens.
 *
 * The Java data layer loads raw per-day rows from SQLite and hands them here.
 * This class then:
 *  * fills in "zero activity" days so charts show a continuous calendar, and
 *  * calculates totals, averages, the best day, and the number of active days.
 */
object ActivitySummaryCalculator {

    /**
     * Returns exactly one entry per date in [dates] (same order), using the
     * recorded activity when available and a zero-filled day otherwise.
     */
    @JvmStatic
    fun zeroFill(dates: List<String>, recorded: List<DayActivity>): List<DayActivity> {
        val byDate = recorded.associateBy { it.dateIso }
        return dates.map { date -> byDate[date] ?: DayActivity(date, 0, 0, 0) }
    }

    @JvmStatic
    fun totalSteps(days: List<DayActivity>): Int = days.sumOf { it.steps }

    @JvmStatic
    fun totalMinutes(days: List<DayActivity>): Int = days.sumOf { it.workoutMinutes }

    @JvmStatic
    fun totalWorkouts(days: List<DayActivity>): Int = days.sumOf { it.workoutCount }

    @JvmStatic
    fun averageSteps(days: List<DayActivity>): Int =
        if (days.isEmpty()) 0 else (totalSteps(days).toDouble() / days.size).roundToInt()

    @JvmStatic
    fun averageMinutes(days: List<DayActivity>): Int =
        if (days.isEmpty()) 0 else (totalMinutes(days).toDouble() / days.size).roundToInt()

    /** Day with the highest step count; ties keep the earliest date. */
    @JvmStatic
    fun bestStepDay(days: List<DayActivity>): DayActivity? = days.maxByOrNull { it.steps }

    /** Number of days that had either steps or at least one workout. */
    @JvmStatic
    fun activeDays(days: List<DayActivity>): Int =
        days.count { it.steps > 0 || it.workoutCount > 0 }
}
