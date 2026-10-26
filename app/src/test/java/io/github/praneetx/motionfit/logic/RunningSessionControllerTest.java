package io.github.praneetx.motionfit.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM contract tests for Running command validation and snapshots. */
public class RunningSessionControllerTest {

    @Test
    public void startIsAllowedOnlyWhenNoSessionExists() {
        assertTrue(RunningSessionController.validate(
                RunningCommand.START, RunningSessionState.NONE).isAllowed());
        assertRejected(
                RunningSessionController.validate(
                        RunningCommand.START, RunningSessionState.ACTIVE),
                RunningCommandError.ALREADY_RUNNING);
        assertRejected(
                RunningSessionController.validate(
                        RunningCommand.START, RunningSessionState.PAUSED),
                RunningCommandError.ALREADY_RUNNING);
        assertRejected(
                RunningSessionController.validate(
                        RunningCommand.START, RunningSessionState.INTERRUPTED),
                RunningCommandError.ALREADY_RUNNING);
    }

    @Test
    public void pauseIsAllowedOnlyWhileActive() {
        assertTrue(RunningSessionController.validate(
                RunningCommand.PAUSE, RunningSessionState.ACTIVE).isAllowed());
        assertRejected(
                RunningSessionController.validate(
                        RunningCommand.PAUSE, RunningSessionState.PAUSED),
                RunningCommandError.ALREADY_PAUSED);
        assertRejected(
                RunningSessionController.validate(
                        RunningCommand.PAUSE, RunningSessionState.INTERRUPTED),
                RunningCommandError.SESSION_INTERRUPTED);
        assertRejected(
                RunningSessionController.validate(
                        RunningCommand.PAUSE, RunningSessionState.NONE),
                RunningCommandError.NO_SESSION);
    }

    @Test
    public void resumeIsAllowedAfterPauseOrInterruption() {
        assertTrue(RunningSessionController.validate(
                RunningCommand.RESUME, RunningSessionState.PAUSED).isAllowed());
        assertTrue(RunningSessionController.validate(
                RunningCommand.RESUME, RunningSessionState.INTERRUPTED).isAllowed());
        assertRejected(
                RunningSessionController.validate(
                        RunningCommand.RESUME, RunningSessionState.ACTIVE),
                RunningCommandError.ALREADY_ACTIVE);
        assertRejected(
                RunningSessionController.validate(
                        RunningCommand.RESUME, RunningSessionState.NONE),
                RunningCommandError.NO_SESSION);
    }

    @Test
    public void finishAndDiscardRequireAnExistingSession() {
        for (RunningCommand command : new RunningCommand[]{
                RunningCommand.FINISH, RunningCommand.DISCARD}) {
            assertTrue(RunningSessionController.validate(
                    command, RunningSessionState.ACTIVE).isAllowed());
            assertTrue(RunningSessionController.validate(
                    command, RunningSessionState.PAUSED).isAllowed());
            assertTrue(RunningSessionController.validate(
                    command, RunningSessionState.INTERRUPTED).isAllowed());
            assertRejected(
                    RunningSessionController.validate(command, RunningSessionState.NONE),
                    RunningCommandError.NO_SESSION);
        }
    }

    @Test
    public void repeatedTransitionsUseExplicitErrorCodes() {
        RunningCommandError pausedAgain = RunningSessionController.validate(
                RunningCommand.PAUSE, RunningSessionState.PAUSED).getError();
        RunningCommandError resumedAgain = RunningSessionController.validate(
                RunningCommand.RESUME, RunningSessionState.ACTIVE).getError();
        RunningCommandError secondStart = RunningSessionController.validate(
                RunningCommand.START, RunningSessionState.ACTIVE).getError();

        assertEquals(RunningCommandError.ALREADY_PAUSED, pausedAgain);
        assertEquals(RunningCommandError.ALREADY_ACTIVE, resumedAgain);
        assertEquals(RunningCommandError.ALREADY_RUNNING, secondStart);
    }

    @Test
    public void interruptedSessionMapsToRecoveryState() {
        assertEquals(RunningSessionState.INTERRUPTED,
                RunningSessionController.stateOf(true, RunningStatus.ACTIVE, true));
        assertEquals(RunningSessionState.PAUSED,
                RunningSessionController.stateOf(true, RunningStatus.PAUSED, false));
        assertEquals(RunningSessionState.NONE,
                RunningSessionController.stateOf(false, null, false));
    }

    @Test
    public void activeSnapshotUsesMonotonicDuration() {
        RunningClockState clock = RunningSessionClock.start(1000L);

        RunningSnapshot snapshot = RunningSessionController.snapshot(
                clock, 61000L, false, 12, 84,
                true, true, true, "2031-08-10T23:50:00");

        assertEquals(RunningSnapshotStatus.RUNNING, snapshot.getStatus());
        assertEquals(60000L, snapshot.getDurationMs());
        assertEquals(Integer.valueOf(12), snapshot.getStepCount());
        assertEquals(Integer.valueOf(84), snapshot.getCadence());
        assertTrue(snapshot.getForegroundTracking());
    }

    @Test
    public void pausedAndInterruptedSnapshotsStayFrozen() {
        RunningClockState running = RunningSessionClock.start(1000L);
        RunningClockState paused = RunningSessionClock.pause(running, 61000L);

        RunningSnapshot pausedSnapshot = RunningSessionController.snapshot(
                paused, 121000L, false, null, null,
                false, false, false, null);
        RunningSnapshot interruptedSnapshot = RunningSessionController.snapshot(
                paused, 121000L, true, null, null,
                false, false, false, null);

        assertEquals(RunningSnapshotStatus.PAUSED, pausedSnapshot.getStatus());
        assertEquals(60000L, pausedSnapshot.getDurationMs());
        assertNull(pausedSnapshot.getStepCount());
        assertNull(pausedSnapshot.getCadence());

        assertEquals(RunningSnapshotStatus.INTERRUPTED, interruptedSnapshot.getStatus());
        assertEquals(60000L, interruptedSnapshot.getDurationMs());
    }

    @Test
    public void noSessionProducesReadySnapshot() {
        RunningSnapshot snapshot = RunningSessionController.snapshot(
                null, 61000L, false, null, null,
                true, false, false, null);

        assertEquals(RunningSnapshotStatus.READY, snapshot.getStatus());
        assertEquals(0L, snapshot.getDurationMs());
        assertNull(snapshot.getStepCount());
        assertNull(snapshot.getCadence());
    }

    private static void assertRejected(
            RunningCommandValidation validation, RunningCommandError expected) {
        assertFalse(validation.isAllowed());
        assertEquals(expected, validation.getError());
    }
}
