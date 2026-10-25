package io.github.praneetx.motionfit.logic

/**
 * Where a daily step total came from.
 *
 * The wire values are the exact strings stored in `daily_steps.source`.
 */
enum class StepSource(val wireValue: String) {
    MANUAL("manual"),
    SENSOR("sensor"),
    SENSOR_ADJUSTED("sensor_adjusted");

    companion object {
        /** Unknown/legacy values are treated as a manual total, never as sensor data. */
        @JvmStatic
        fun fromWireValue(value: String?): StepSource =
            values().firstOrNull { it.wireValue == value } ?: MANUAL
    }
}

/** How a pure step decision should be persisted. */
enum class StepWriteMode {
    /** Duplicate or zero reading: no database write is needed. */
    NONE,

    /** Add [StepUpdate.stepsDelta] to the current total and move the checkpoint. */
    DELTA,

    /** Replace the total with [StepUpdate.state].steps (insert/calibration/day rollover). */
    ABSOLUTE
}

/** Immutable snapshot of one day's persisted step state. */
data class StepDayState(
    val dateIso: String,
    val steps: Int,
    val lastRawSensor: Long?,
    val source: StepSource
)

/**
 * Immutable result of applying one raw sensor reading.
 *
 * [expectedLastRawSensor] is the checkpoint the caller must match in a
 * conditional update. If zero rows change, another writer calibrated the row;
 * reload the row and apply the reading again rather than overwriting it.
 */
data class StepUpdate(
    val state: StepDayState,
    val writeMode: StepWriteMode,
    val stepsDelta: Int,
    val expectedLastRawSensor: Long?,
    val dayChanged: Boolean,
    val resetDetected: Boolean,
    val sourceChanged: Boolean
) {
    val writeRequired: Boolean
        get() = writeMode != StepWriteMode.NONE
}

/**
 * Pure rules for applying TYPE_STEP_COUNTER readings to a daily total.
 *
 * The class has no Android or database imports. The repository owns the
 * transaction; this engine only decides the next state and the exact delta.
 */
object StepCountEngine {

    /**
     * Applies a reading for [todayIso]. A date change is treated conservatively:
     * the new day's total starts at zero and only the checkpoint moves. Callers
     * that were continuously tracking across midnight use the overload below.
     */
    @JvmStatic
    fun applyReading(previous: StepDayState?, raw: Long, todayIso: String): StepUpdate =
        applyReading(previous, raw, todayIso, false)

    /**
     * Applies a reading, optionally carrying the previous checkpoint into a new
     * day. [carryPreviousCheckpoint] must be true only when the same service
     * observed the previous day's last reading and the date moved forward.
     */
    @JvmStatic
    fun applyReading(
        previous: StepDayState?,
        raw: Long,
        todayIso: String,
        carryPreviousCheckpoint: Boolean
    ): StepUpdate {
        val safeRaw = raw.coerceAtLeast(0L)

        if (previous == null) {
            return StepUpdate(
                state = StepDayState(todayIso, 0, safeRaw, StepSource.SENSOR),
                writeMode = StepWriteMode.ABSOLUTE,
                stepsDelta = 0,
                expectedLastRawSensor = null,
                dayChanged = true,
                resetDetected = false,
                sourceChanged = false
            )
        }

        if (previous.dateIso != todayIso) {
            val checkpoint = previous.lastRawSensor
            val carry = carryPreviousCheckpoint &&
                checkpoint != null &&
                todayIso > previous.dateIso &&
                safeRaw >= checkpoint
            val newSteps = if (carry) toSteps(safeRaw - checkpoint) else 0
            return StepUpdate(
                state = StepDayState(todayIso, newSteps, safeRaw, StepSource.SENSOR),
                writeMode = StepWriteMode.ABSOLUTE,
                stepsDelta = newSteps,
                expectedLastRawSensor = null,
                dayChanged = true,
                resetDetected = false,
                sourceChanged = previous.source != StepSource.SENSOR
            )
        }

        val checkpoint = previous.lastRawSensor
        if (checkpoint == null) {
            // Rule 6.3.2: preserve the total and start future deltas from here.
            val state = previous.copy(
                lastRawSensor = safeRaw,
                source = StepSource.SENSOR_ADJUSTED
            )
            return StepUpdate(
                state = state,
                writeMode = StepWriteMode.DELTA,
                stepsDelta = 0,
                expectedLastRawSensor = null,
                dayChanged = false,
                resetDetected = false,
                sourceChanged = previous.source != StepSource.SENSOR_ADJUSTED
            )
        }

        if (safeRaw < checkpoint) {
            // Reboot or sensor-epoch reset: keep the total, restart the epoch.
            return StepUpdate(
                state = previous.copy(lastRawSensor = safeRaw),
                writeMode = StepWriteMode.DELTA,
                stepsDelta = 0,
                expectedLastRawSensor = checkpoint,
                dayChanged = false,
                resetDetected = true,
                sourceChanged = false
            )
        }

        if (safeRaw == checkpoint) {
            return StepUpdate(
                state = previous,
                writeMode = StepWriteMode.NONE,
                stepsDelta = 0,
                expectedLastRawSensor = checkpoint,
                dayChanged = false,
                resetDetected = false,
                sourceChanged = false
            )
        }

        val nextSteps = addSteps(previous.steps, safeRaw - checkpoint)
        val appliedDelta = nextSteps - previous.steps
        val source = if (previous.source == StepSource.MANUAL) {
            StepSource.SENSOR_ADJUSTED
        } else {
            previous.source
        }
        return StepUpdate(
            state = previous.copy(
                steps = nextSteps,
                lastRawSensor = safeRaw,
                source = source
            ),
            writeMode = StepWriteMode.DELTA,
            stepsDelta = appliedDelta,
            expectedLastRawSensor = checkpoint,
            dayChanged = false,
            resetDetected = false,
            sourceChanged = source != previous.source
        )
    }

    /**
     * Applies an explicit user total. When [rawNow] is available it becomes the
     * new checkpoint, so the correction is not undone by the next reading.
     */
    @JvmStatic
    fun calibrate(
        previous: StepDayState?,
        total: Int,
        rawNow: Long?,
        todayIso: String
    ): StepUpdate {
        val safeTotal = total.coerceAtLeast(0)
        val safeRaw = rawNow?.coerceAtLeast(0L)
        val source = if (safeRaw == null) StepSource.MANUAL else StepSource.SENSOR_ADJUSTED
        val sameDay = previous?.dateIso == todayIso
        return StepUpdate(
            state = StepDayState(todayIso, safeTotal, safeRaw, source),
            writeMode = StepWriteMode.ABSOLUTE,
            stepsDelta = 0,
            expectedLastRawSensor = if (sameDay) previous?.lastRawSensor else null,
            dayChanged = !sameDay,
            resetDetected = false,
            sourceChanged = previous?.source != source
        )
    }

    private fun addSteps(current: Int, delta: Long): Int {
        if (delta <= 0L) return current.coerceAtLeast(0)
        if (delta >= Int.MAX_VALUE.toLong()) return Int.MAX_VALUE
        return (current.toLong() + delta)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun toSteps(value: Long): Int =
        value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}
