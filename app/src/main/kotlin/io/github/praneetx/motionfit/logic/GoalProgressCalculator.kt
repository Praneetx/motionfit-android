package io.github.praneetx.motionfit.logic

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pure Kotlin goal-progress maths shared by the dashboard and the goals screen.
 *
 * Having this logic in Kotlin (instead of Android Activities) makes it easy to
 * unit test on a plain JVM, and Java classes call it directly thanks to
 * {@code @JvmStatic}. This is the main place where the project demonstrates
 * Java <-> Kotlin interoperability.
 *
 * Safety rules implemented here:
 *  * a goal of zero or less never causes a divide-by-zero;
 *  * negative "actual" values are treated as zero;
 *  * percentages above 100 are allowed (a goal can be exceeded) while the
 *    progress bar itself is clamped to 100%.
 */
object GoalProgressCalculator {

    /** Raw percentage of a goal that was achieved. May be above 100. */
    @JvmStatic
    fun percent(actual: Int, goal: Int): Double {
        if (goal <= 0) return 0.0
        val safeActual = if (actual < 0) 0 else actual
        return safeActual.toDouble() * 100.0 / goal.toDouble()
    }

    /** Percentage rounded to the nearest whole number (can exceed 100). */
    @JvmStatic
    fun roundedPercent(actual: Int, goal: Int): Int {
        if (goal <= 0) return 0
        return percent(actual, goal).roundToInt()
    }

    /** Percentage clamped into the 0..100 range (for a progress bar label). */
    @JvmStatic
    fun clampedPercent(actual: Int, goal: Int): Int =
        percent(actual, goal).coerceIn(0.0, 100.0).roundToInt()

    /** Fraction in the 0.0..1.0 range, ready for ProgressBar.setProgress. */
    @JvmStatic
    fun fraction(actual: Int, goal: Int): Float =
        (percent(actual, goal) / 100.0).coerceIn(0.0, 1.0).toFloat()

    /** True when the daily goal has been met or exceeded. */
    @JvmStatic
    fun isReached(actual: Int, goal: Int): Boolean = goal > 0 && actual >= goal

    /** Nicely formatted label such as {@code 87.5%} (never negative). */
    @JvmStatic
    fun formatPercent(actual: Int, goal: Int): String =
        String.format(Locale.US, "%.1f%%", percent(actual, goal))
}
