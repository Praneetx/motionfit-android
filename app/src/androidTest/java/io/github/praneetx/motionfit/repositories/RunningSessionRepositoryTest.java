package io.github.praneetx.motionfit.repositories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.database.DatabaseContract;
import io.github.praneetx.motionfit.database.FitnessDatabaseHelper;
import io.github.praneetx.motionfit.logic.RunningStatus;
import io.github.praneetx.motionfit.models.RunningSession;
import io.github.praneetx.motionfit.models.Workout;
import io.github.praneetx.motionfit.utils.OperationResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Instrumented contract tests for the single in-progress Running session and the
 * exactly-once finish transaction.
 */
@RunWith(AndroidJUnit4.class)
public class RunningSessionRepositoryTest {

    private static final String STARTED_AT = "2031-08-10T23:50:00";
    private static final String START_DATE = "2031-08-10";
    private static final long START_ELAPSED = 1000L;

    private RunningSessionRepository repository;
    private WorkoutRepository workoutRepository;
    private SQLiteDatabase db;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new DataResetRepository(context).clearAllData().isSuccess());
        repository = new RunningSessionRepository(context);
        workoutRepository = new WorkoutRepository(context);
        db = FitnessDatabaseHelper.getInstance(context).getWritableDatabase();
    }

    @Test
    public void startInsertsOneActiveSingletonSnapshot() {
        assertTrue(repository.start(STARTED_AT, START_ELAPSED, 7, 500L).isSuccess());

        RunningSession session = repository.getSession();
        assertNotNull(session);
        assertEquals(1L, session.getId());
        assertEquals(RunningStatus.ACTIVE, session.getStatus());
        assertEquals(STARTED_AT, session.getStartedAt());
        assertEquals(START_ELAPSED, session.getStartedElapsedMs());
        assertEquals(0L, session.getAccumulatedMs());
        assertEquals(Long.valueOf(START_ELAPSED), session.getLastResumedElapsedMs());
        assertEquals(0L, session.getDurationMs());
        assertEquals(0, session.getStepCount());
        assertEquals(Long.valueOf(500L), session.getStartRawSteps());
        assertEquals(Long.valueOf(500L), session.getLastRawSteps());
        assertEquals(7, session.getBootCount());
        assertFalse(session.isInterrupted());
        assertEquals(1L, sessionCount());
    }

    @Test
    public void secondStartDoesNotCreateSecondRowOrReplaceTheFirst() {
        startSession();

        OperationResult second = repository.start(
                "2031-08-11T00:10:00", 2000L, 8, 900L);

        assertFalse(second.isSuccess());
        RunningSession session = repository.getSession();
        assertNotNull(session);
        assertEquals(STARTED_AT, session.getStartedAt());
        assertEquals(START_ELAPSED, session.getStartedElapsedMs());
        assertEquals(Long.valueOf(500L), session.getStartRawSteps());
        assertEquals(1L, sessionCount());
    }

    @Test
    public void pauseAndResumePersistClockAndDurationSnapshots() {
        startSession();

        assertTrue(repository.pause(61000L).isSuccess());
        RunningSession paused = repository.getSession();
        assertNotNull(paused);
        assertEquals(RunningStatus.PAUSED, paused.getStatus());
        assertEquals(60000L, paused.getAccumulatedMs());
        assertNull(paused.getLastResumedElapsedMs());
        assertEquals(60000L, paused.getDurationMs());

        assertTrue(repository.resume(121000L).isSuccess());
        RunningSession resumed = repository.getSession();
        assertNotNull(resumed);
        assertEquals(RunningStatus.ACTIVE, resumed.getStatus());
        assertEquals(60000L, resumed.getAccumulatedMs());
        assertEquals(Long.valueOf(121000L), resumed.getLastResumedElapsedMs());
        assertEquals(60000L, resumed.getDurationMs());
    }

    @Test
    public void interruptedStateIsStoredAndReadWithFrozenStepSnapshots() {
        startSession();
        assertTrue(repository.update(31000L, 42, 542L).isSuccess());

        assertTrue(repository.markInterrupted(61000L).isSuccess());

        RunningSession interrupted = repository.getSession();
        assertNotNull(interrupted);
        assertTrue(interrupted.isInterrupted());
        assertEquals(RunningStatus.PAUSED, interrupted.getStatus());
        assertEquals(60000L, interrupted.getAccumulatedMs());
        assertEquals(60000L, interrupted.getDurationMs());
        assertNull(interrupted.getLastResumedElapsedMs());
        assertEquals(42, interrupted.getStepCount());
        assertEquals(Long.valueOf(542L), interrupted.getLastRawSteps());

        assertTrue(repository.resume(121000L).isSuccess());
        RunningSession resumed = repository.getSession();
        assertNotNull(resumed);
        assertFalse(resumed.isInterrupted());
        assertEquals(RunningStatus.ACTIVE, resumed.getStatus());
    }

    @Test
    public void finishInsertsOneRunningWorkoutAndDeletesTheSession() {
        startSession();
        assertTrue(repository.update(30000L, 123, 623L).isSuccess());

        assertTrue(repository.finish(62000L).isSuccess());

        assertNull(repository.getSession());
        assertEquals(0L, sessionCount());

        List<Workout> workouts = workoutRepository.getForDate(START_DATE);
        assertEquals(1, workouts.size());
        Workout workout = workouts.get(0);
        assertEquals("Running", workout.getWorkoutType());
        assertEquals(START_DATE, workout.getWorkoutDate());
        assertEquals(2, workout.getDurationMinutes());
        assertEquals(Integer.valueOf(61), workout.getDurationSeconds());
        assertEquals(Integer.valueOf(123), workout.getStepCount());
        assertNull(workout.getNotes());
    }

    @Test
    public void finishWithoutSensorStoresNullSteps() {
        assertTrue(repository.start(STARTED_AT, START_ELAPSED, 7, null).isSuccess());

        assertTrue(repository.finish(62000L).isSuccess());

        List<Workout> workouts = workoutRepository.getForDate(START_DATE);
        assertEquals(1, workouts.size());
        assertNull(workouts.get(0).getStepCount());
        assertEquals(Integer.valueOf(61), workouts.get(0).getDurationSeconds());
    }

    @Test
    public void finishWithSensorAndZeroStepsStoresZeroNotNull() {
        startSession();

        assertTrue(repository.finish(62000L).isSuccess());

        List<Workout> workouts = workoutRepository.getForDate(START_DATE);
        assertEquals(1, workouts.size());
        assertEquals(Integer.valueOf(0), workouts.get(0).getStepCount());
    }

    @Test
    public void finishingTwiceInsertsOnlyOneWorkout() {
        startSession();

        assertTrue(repository.finish(62000L).isSuccess());
        assertFalse(repository.finish(92000L).isSuccess());

        assertEquals(1, workoutRepository.getForDate(START_DATE).size());
        assertEquals(0L, sessionCount());
    }

    @Test
    public void failingWorkoutInsertRollsBackAndLeavesTheSessionForRetry() {
        startSession();
        db.execSQL("CREATE TRIGGER fail_running_workout_insert "
                + "BEFORE INSERT ON " + DatabaseContract.TABLE_WORKOUTS
                + " BEGIN SELECT RAISE(ABORT, 'forced failure'); END;");
        try {
            assertFalse(repository.finish(62000L).isSuccess());
        } finally {
            db.execSQL("DROP TRIGGER IF EXISTS fail_running_workout_insert");
        }

        assertNotNull(repository.getSession());
        assertEquals(1L, sessionCount());
        assertTrue(workoutRepository.getForDate(START_DATE).isEmpty());

        // The intact session can be retried after the database recovers.
        assertTrue(repository.finish(62000L).isSuccess());
        assertEquals(1, workoutRepository.getForDate(START_DATE).size());
    }

    @Test
    public void finishWithLatestLiveStepsSavesTheFinalPartialInterval() {
        startSession();
        assertTrue(repository.update(30000L, 10, 510L).isSuccess());

        assertTrue(repository.finish(62000L, 77).isSuccess());

        List<Workout> workouts = workoutRepository.getForDate(START_DATE);
        assertEquals(1, workouts.size());
        assertEquals(Integer.valueOf(77), workouts.get(0).getStepCount());
        assertEquals(Integer.valueOf(61), workouts.get(0).getDurationSeconds());
    }

    @Test
    public void lateFirstSensorReadingBecomesTheBaselineWithoutCountingPastSteps() {
        assertTrue(repository.start(STARTED_AT, START_ELAPSED, 7, null).isSuccess());

        assertTrue(repository.initializeStepBaseline(9000L).isSuccess());
        RunningSession baseline = repository.getSession();
        assertNotNull(baseline);
        assertEquals(Long.valueOf(9000L), baseline.getStartRawSteps());
        assertEquals(Long.valueOf(9000L), baseline.getLastRawSteps());
        assertEquals(0, baseline.getStepCount());

        assertTrue(repository.initializeStepBaseline(9999L).isSuccess());
        RunningSession unchanged = repository.getSession();
        assertNotNull(unchanged);
        assertEquals(Long.valueOf(9000L), unchanged.getStartRawSteps());
    }

    @Test
    public void rebootFreezeUsesPersistedDurationNotNewUptime() {
        startSession();
        assertTrue(repository.update(31000L, 42, 542L).isSuccess());

        // The new boot's uptime (900000ms) is much larger than the old one; it
        // must not be added to the saved run.
        assertTrue(repository.markInterruptedAfterReboot().isSuccess());
        RunningSession interrupted = repository.getSession();
        assertNotNull(interrupted);
        assertTrue(interrupted.isInterrupted());
        assertEquals(30000L, interrupted.getAccumulatedMs());
        assertEquals(30000L, interrupted.getDurationMs());
        assertNull(interrupted.getLastResumedElapsedMs());
        assertEquals(42, interrupted.getStepCount());

        assertTrue(repository.resume(900000L).isSuccess());
        assertTrue(repository.finish(910000L).isSuccess());
        assertEquals(Integer.valueOf(40),
                workoutRepository.getForDate(START_DATE).get(0).getDurationSeconds());
    }

    @Test
    public void discardDeletesTheSessionWithoutInsertingAWorkout() {
        startSession();

        assertTrue(repository.discard().isSuccess());

        assertNull(repository.getSession());
        assertEquals(0L, sessionCount());
        assertTrue(workoutRepository.getForDate(START_DATE).isEmpty());
    }

    @Test
    public void stepSnapshotsAreNonNegativeAndSurviveReload() {
        startSession();

        assertTrue(repository.update(31000L, 321, 821L).isSuccess());
        RunningSession updated = repository.getSession();
        assertNotNull(updated);
        assertEquals(321, updated.getStepCount());
        assertEquals(Long.valueOf(821L), updated.getLastRawSteps());
        assertEquals(30000L, updated.getDurationMs());

        assertFalse(repository.update(32000L, -1, 821L).isSuccess());
        assertFalse(repository.update(32000L, 321, -5L).isSuccess());

        RunningSession afterRejectedUpdates = repository.getSession();
        assertNotNull(afterRejectedUpdates);
        assertEquals(321, afterRejectedUpdates.getStepCount());
        assertEquals(Long.valueOf(821L), afterRejectedUpdates.getLastRawSteps());
    }

    private void startSession() {
        assertTrue(repository.start(STARTED_AT, START_ELAPSED, 7, 500L).isSuccess());
    }

    private long sessionCount() {
        return DatabaseUtils.queryNumEntries(db, DatabaseContract.TABLE_RUNNING_SESSIONS);
    }
}
