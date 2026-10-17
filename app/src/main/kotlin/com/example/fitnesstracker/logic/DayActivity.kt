package com.example.fitnesstracker.logic

/**
 * Immutable snapshot of one calendar day's activity, produced by the data layer
 * and consumed by [ActivitySummaryCalculator].
 *
 * Kotlin properties generate normal Java getters, so Java code can read them
 * with {@code day.getSteps()}.
 */
data class DayActivity(
    val dateIso: String,
    val steps: Int,
    val workoutMinutes: Int,
    val workoutCount: Int
)
