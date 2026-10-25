package io.github.praneetx.motionfit.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** JVM tests for the monotonic Running session clock. */
public class RunningSessionClockTest {

    @Test
    public void startCreatesAnActiveClockAtZero() {
        RunningClockState state = RunningSessionClock.start(1000L);

        assertEquals(RunningStatus.ACTIVE, state.getStatus());
        assertEquals(1000L, state.getStartedElapsedMs());
        assertEquals(0L, state.getAccumulatedMs());
        assertEquals(Long.valueOf(1000L), state.getLastResumedElapsedMs());
        assertEquals(0L, state.getDurationMs());
    }

    @Test
    public void activeSnapshotAddsElapsedTime() {
        RunningClockState state = RunningSessionClock.start(1000L);

        RunningClockState snapshot = RunningSessionClock.snapshot(state, 61000L);

        assertEquals(RunningStatus.ACTIVE, snapshot.getStatus());
        assertEquals(60000L, snapshot.getDurationMs());
        assertEquals(Long.valueOf(1000L), snapshot.getLastResumedElapsedMs());
    }

    @Test
    public void pauseFreezesElapsedTime() {
        RunningClockState running = RunningSessionClock.start(1000L);

        RunningClockState paused = RunningSessionClock.pause(running, 61000L);

        assertEquals(RunningStatus.PAUSED, paused.getStatus());
        assertEquals(60000L, paused.getAccumulatedMs());
        assertNull(paused.getLastResumedElapsedMs());
        assertEquals(60000L, paused.getDurationMs());

        RunningClockState later = RunningSessionClock.snapshot(paused, 121000L);
        assertEquals(60000L, later.getDurationMs());
    }

    @Test
    public void resumeContinuesFromThePausedDuration() {
        RunningClockState running = RunningSessionClock.start(1000L);
        RunningClockState paused = RunningSessionClock.pause(running, 61000L);
        RunningClockState resumed = RunningSessionClock.resume(paused, 121000L);

        assertEquals(RunningStatus.ACTIVE, resumed.getStatus());
        assertEquals(60000L, resumed.getAccumulatedMs());
        assertEquals(Long.valueOf(121000L), resumed.getLastResumedElapsedMs());

        RunningClockState snapshot = RunningSessionClock.snapshot(resumed, 181000L);
        assertEquals(120000L, snapshot.getDurationMs());
    }

    @Test
    public void pauseAndResumeAreIdempotentWhenAlreadyInThatState() {
        RunningClockState running = RunningSessionClock.start(1000L);
        RunningClockState paused = RunningSessionClock.pause(running, 61000L);

        RunningClockState pausedAgain = RunningSessionClock.pause(paused, 91000L);
        assertEquals(60000L, pausedAgain.getDurationMs());

        RunningClockState resumed = RunningSessionClock.resume(paused, 121000L);
        RunningClockState resumedAgain = RunningSessionClock.resume(resumed, 151000L);
        assertEquals(120000L, RunningSessionClock.snapshot(resumedAgain, 181000L).getDurationMs());
    }

    @Test
    public void finishReturnsTheFinalSnapshot() {
        RunningClockState running = RunningSessionClock.start(1000L);

        RunningClockState finished = RunningSessionClock.finish(running, 61000L);

        assertEquals(60000L, finished.getDurationMs());
    }

    @Test
    public void sameBootRecoveryContinuesTheActiveClock() {
        RunningClockState running = RunningSessionClock.start(1000L);

        RunningClockState recovered = RunningSessionClock.recover(running, 121000L, true);

        assertEquals(RunningStatus.ACTIVE, recovered.getStatus());
        assertEquals(120000L, recovered.getDurationMs());
    }

    @Test
    public void differentBootRecoveryFreezesAtTheLastSnapshot() {
        RunningClockState running = RunningSessionClock.start(1000L);
        RunningClockState paused = RunningSessionClock.pause(running, 61000L);

        RunningClockState recovered = RunningSessionClock.recover(paused, 999000L, false);

        assertEquals(RunningStatus.PAUSED, recovered.getStatus());
        assertEquals(60000L, recovered.getAccumulatedMs());
        assertEquals(60000L, recovered.getDurationMs());
        assertNull(recovered.getLastResumedElapsedMs());
    }

    @Test
    public void clockNeverGoesNegativeWhenNowIsBeforeLastResume() {
        RunningClockState running = RunningSessionClock.start(1000L);

        RunningClockState snapshot = RunningSessionClock.snapshot(running, 500L);

        assertEquals(0L, snapshot.getDurationMs());
    }
}
