package io.github.praneetx.motionfit.logic

import kotlin.math.roundToInt

/**
 * Rolling 30-second cadence estimate built from TYPE_STEP_COUNTER deltas.
 *
 * The tracker is stateful but has no Android imports. Production passes
 * elapsedRealtime() milliseconds; tests pass fixed values. It deliberately
 * returns null until both the minimum sample count and the minimum window time
 * are available, so the UI can show an em dash instead of a fake cadence.
 *
 * Pause/restart must call [reset] so a stale pre-pause cadence is never shown.
 */
class CadenceTracker @JvmOverloads constructor(
    private val windowMs: Long = 30_000L,
    private val minimumWindowMs: Long = 5_000L,
    private val minimumSamples: Int = 2
) {

    private data class Sample(val atMs: Long, val steps: Long)

    private val samples = ArrayDeque<Sample>()
    private var lastRaw: Long? = null
    private var firstReadingMs: Long? = null
    private var hasValidData = false

    /** Clears all history; the next reading starts a new baseline. */
    fun reset() {
        samples.clear()
        lastRaw = null
        firstReadingMs = null
        hasValidData = false
    }

    /**
     * Adds a raw counter reading and returns cadence in steps per minute, or
     * null while there is not enough data. Duplicate readings are harmless.
     */
    fun onReading(rawSteps: Long, nowMs: Long): Int? {
        val now = nowMs.coerceAtLeast(0L)
        val raw = rawSteps.coerceAtLeast(0L)
        val previous = lastRaw

        if (previous == null) {
            lastRaw = raw
            firstReadingMs = now
            return null
        }

        if (raw < previous) {
            // A sensor epoch reset is not a negative number of steps.
            reset()
            lastRaw = raw
            firstReadingMs = now
            return null
        }

        val delta = raw - previous
        lastRaw = raw
        if (delta > 0L) {
            samples.addLast(Sample(now, delta))
        }
        return cadenceAt(now)
    }

    /**
     * Returns the current rolling estimate without adding a sample. This lets
     * the UI tick down to 0 after 30 seconds with no new steps.
     */
    fun cadenceAt(nowMs: Long): Int? {
        val now = nowMs.coerceAtLeast(0L)
        val first = firstReadingMs ?: return null
        if (now < first) return null

        prune(now)
        if (now - first < minimumWindowMs) return null

        if (!hasValidData) {
            if (samples.size < minimumSamples) return null
            hasValidData = true
        }

        val windowStart = maxOf(first, now - windowMs)
        val durationMs = now - windowStart
        if (durationMs <= 0L) return null

        val stepsInWindow = samples.sumOf { it.steps }
        return ((stepsInWindow.toDouble() * 60_000.0) / durationMs).roundToInt()
    }

    private fun prune(nowMs: Long) {
        val cutoff = nowMs - windowMs
        while (samples.isNotEmpty() && samples.first().atMs < cutoff) {
            samples.removeFirst()
        }
    }
}
