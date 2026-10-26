package io.github.praneetx.motionfit.logic

/** The durable Running state used for command validation. */
enum class RunningSessionState {
    NONE,
    ACTIVE,
    PAUSED,
    INTERRUPTED
}

/** All user/service commands that mutate a Running session. */
enum class RunningCommand {
    START,
    PAUSE,
    RESUME,
    FINISH,
    DISCARD
}

/** Explicit validation outcomes so repeated taps cannot silently duplicate work. */
enum class RunningCommandError {
    NONE,
    NO_SESSION,
    ALREADY_RUNNING,
    ALREADY_ACTIVE,
    ALREADY_PAUSED,
    SESSION_INTERRUPTED
}

data class RunningCommandValidation(
    val isAllowed: Boolean,
    val error: RunningCommandError
) {
    companion object {
        @JvmStatic
        val OK = RunningCommandValidation(true, RunningCommandError.NONE)

        @JvmStatic
        fun rejected(error: RunningCommandError) = RunningCommandValidation(false, error)
    }
}

/** What the Running screen should render. */
enum class RunningSnapshotStatus {
    READY,
    RUNNING,
    PAUSED,
    INTERRUPTED
}

/**
 * Immutable service-to-UI Running state.
 *
 * [stepCount] is null when no step-counter baseline exists; a real zero is
 * stored and displayed as zero. [cadence] is null until the tracker has enough
 * samples, and [foregroundTracking] is true only when the service actually owns
 * a visible foreground notification.
 */
data class RunningSnapshot(
    val status: RunningSnapshotStatus,
    val durationMs: Long,
    val stepCount: Int?,
    val cadence: Int?,
    val sensorAvailable: Boolean,
    val foregroundTracking: Boolean,
    val notificationsAllowed: Boolean,
    val startedAt: String?
)

/**
 * Pure Running command rules and snapshot construction.
 *
 * This class deliberately has no Android imports. The service owns persistence,
 * elapsedRealtime(), sensor readings and notifications; these functions decide
 * whether a command is legal and what the screen should see.
 */
object RunningSessionController {

    @JvmStatic
    fun stateOf(hasSession: Boolean, status: RunningStatus?, interrupted: Boolean): RunningSessionState {
        if (!hasSession) return RunningSessionState.NONE
        if (interrupted) return RunningSessionState.INTERRUPTED
        return if (status == RunningStatus.ACTIVE) {
            RunningSessionState.ACTIVE
        } else {
            RunningSessionState.PAUSED
        }
    }

    @JvmStatic
    fun validate(command: RunningCommand, state: RunningSessionState): RunningCommandValidation {
        if (state == RunningSessionState.NONE) {
            return if (command == RunningCommand.START) {
                RunningCommandValidation.OK
            } else {
                RunningCommandValidation.rejected(RunningCommandError.NO_SESSION)
            }
        }

        return when (command) {
            RunningCommand.START -> RunningCommandValidation.rejected(RunningCommandError.ALREADY_RUNNING)
            RunningCommand.PAUSE -> when (state) {
                RunningSessionState.ACTIVE -> RunningCommandValidation.OK
                RunningSessionState.PAUSED ->
                    RunningCommandValidation.rejected(RunningCommandError.ALREADY_PAUSED)
                else -> RunningCommandValidation.rejected(RunningCommandError.SESSION_INTERRUPTED)
            }
            RunningCommand.RESUME -> when (state) {
                RunningSessionState.ACTIVE ->
                    RunningCommandValidation.rejected(RunningCommandError.ALREADY_ACTIVE)
                else -> RunningCommandValidation.OK
            }
            RunningCommand.FINISH, RunningCommand.DISCARD -> RunningCommandValidation.OK
        }
    }

    /**
     * Builds the UI snapshot. An interrupted row is deliberately frozen at the
     * last recorded duration and is never advanced by wall-clock downtime.
     */
    @JvmStatic
    fun snapshot(
        clock: RunningClockState?,
        nowMs: Long,
        interrupted: Boolean,
        stepCount: Int?,
        cadence: Int?,
        sensorAvailable: Boolean,
        foregroundTracking: Boolean,
        notificationsAllowed: Boolean,
        startedAt: String?
    ): RunningSnapshot {
        if (clock == null) {
            return RunningSnapshot(
                status = RunningSnapshotStatus.READY,
                durationMs = 0L,
                stepCount = null,
                cadence = null,
                sensorAvailable = sensorAvailable,
                foregroundTracking = foregroundTracking,
                notificationsAllowed = notificationsAllowed,
                startedAt = null
            )
        }

        val status = when {
            interrupted -> RunningSnapshotStatus.INTERRUPTED
            clock.status == RunningStatus.ACTIVE -> RunningSnapshotStatus.RUNNING
            else -> RunningSnapshotStatus.PAUSED
        }
        val duration = if (interrupted) {
            clock.durationMs.coerceAtLeast(0L)
        } else {
            RunningSessionClock.snapshot(clock, nowMs).durationMs.coerceAtLeast(0L)
        }
        val activeCadence = if (status == RunningSnapshotStatus.RUNNING) cadence else null

        return RunningSnapshot(
            status = status,
            durationMs = duration,
            stepCount = stepCount,
            cadence = activeCadence,
            sensorAvailable = sensorAvailable,
            foregroundTracking = foregroundTracking,
            notificationsAllowed = notificationsAllowed,
            startedAt = startedAt
        )
    }
}
