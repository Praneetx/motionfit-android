package io.github.praneetx.motionfit.logic

import kotlin.math.roundToInt

/**
 * Pure conversion rules for exact Running durations.
 *
 * UI formatting stays out of the data layer; this class only provides the
 * values stored in `workouts.duration_seconds` and `duration_minutes`.
 */
object WorkoutDuration {

    /** Exact stored seconds, rounded to the nearest second with a 1s minimum. */
    @JvmStatic
    fun seconds(durationMs: Long): Int {
        if (durationMs <= 0L) return 1
        return (durationMs / 1000.0).roundToInt().coerceAtLeast(1)
    }

    /** Whole minutes rounded up, with a 1-minute minimum. */
    @JvmStatic
    fun minutes(durationSeconds: Int): Int {
        if (durationSeconds <= 0) return 1
        return ((durationSeconds.toLong() + 59L) / 60L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }
}
