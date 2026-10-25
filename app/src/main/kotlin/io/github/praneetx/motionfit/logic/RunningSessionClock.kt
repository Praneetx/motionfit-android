package io.github.praneetx.motionfit.logic

/** Whether the Running timer is currently accumulating time. */
enum class RunningStatus {
    ACTIVE,
    PAUSED
}

/**
 * Immutable Running timer snapshot.
 *
 * All times are elapsedRealtime() milliseconds, except [accumulatedMs] and
 * [durationMs], which are durations. [lastResumedElapsedMs] is null while
 * paused.
 */
data class RunningClockState(
    val status: RunningStatus,
    val startedElapsedMs: Long,
    val accumulatedMs: Long,
    val lastResumedElapsedMs: Long?,
    val durationMs: Long
)

/**
 * Pure monotonic Running clock rules.
 *
 * The service owns elapsedRealtime(); these functions only calculate pause,
 * resume, snapshots and recovery. Downtime after a reboot is never added to the
 * duration because recovery freezes at the last persisted value.
 */
object RunningSessionClock {

    @JvmStatic
    fun start(nowMs: Long): RunningClockState {
        val now = nowMs.coerceAtLeast(0L)
        return RunningClockState(
            status = RunningStatus.ACTIVE,
            startedElapsedMs = now,
            accumulatedMs = 0L,
            lastResumedElapsedMs = now,
            durationMs = 0L
        )
    }

    @JvmStatic
    fun snapshot(state: RunningClockState, nowMs: Long): RunningClockState {
        if (state.status != RunningStatus.ACTIVE) {
            return state.copy(durationMs = state.accumulatedMs.coerceAtLeast(0L))
        }
        val lastResumed = state.lastResumedElapsedMs
            ?: return state.copy(durationMs = state.accumulatedMs.coerceAtLeast(0L))
        val elapsed = (nowMs - lastResumed).coerceAtLeast(0L)
        val duration = safeAdd(state.accumulatedMs.coerceAtLeast(0L), elapsed)
        return state.copy(durationMs = duration)
    }

    @JvmStatic
    fun pause(state: RunningClockState, nowMs: Long): RunningClockState {
        if (state.status != RunningStatus.ACTIVE) {
            return snapshot(state, nowMs)
        }
        val accumulated = snapshot(state, nowMs).durationMs
        return state.copy(
            status = RunningStatus.PAUSED,
            accumulatedMs = accumulated,
            lastResumedElapsedMs = null,
            durationMs = accumulated
        )
    }

    @JvmStatic
    fun resume(state: RunningClockState, nowMs: Long): RunningClockState {
        if (state.status != RunningStatus.PAUSED) {
            return snapshot(state, nowMs)
        }
        return state.copy(
            status = RunningStatus.ACTIVE,
            lastResumedElapsedMs = nowMs.coerceAtLeast(0L),
            durationMs = state.accumulatedMs.coerceAtLeast(0L)
        )
    }

    /** Returns the final snapshot used by the save transaction. */
    @JvmStatic
    fun finish(state: RunningClockState, nowMs: Long): RunningClockState =
        snapshot(state, nowMs)

    /**
     * Restores a persisted session. On the same boot an active clock can
     * continue; after a reboot it is frozen and the UI must offer explicit
     * Resume/Finish/Discard choices.
     */
    @JvmStatic
    fun recover(
        state: RunningClockState,
        nowMs: Long,
        sameBoot: Boolean
    ): RunningClockState {
        if (sameBoot) return snapshot(state, nowMs)

        val frozen = state.durationMs.coerceAtLeast(0L)
        return state.copy(
            status = RunningStatus.PAUSED,
            accumulatedMs = frozen,
            lastResumedElapsedMs = null,
            durationMs = frozen
        )
    }

    private fun safeAdd(left: Long, right: Long): Long {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE
        return left + right
    }
}
