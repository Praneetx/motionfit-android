package io.github.praneetx.motionfit.repositories;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import io.github.praneetx.motionfit.database.DatabaseContract;
import io.github.praneetx.motionfit.database.FitnessDatabaseHelper;
import io.github.praneetx.motionfit.logic.RunningClockState;
import io.github.praneetx.motionfit.logic.RunningSessionClock;
import io.github.praneetx.motionfit.logic.RunningStatus;
import io.github.praneetx.motionfit.logic.WorkoutDuration;
import io.github.praneetx.motionfit.models.RunningSession;
import io.github.praneetx.motionfit.utils.DateUtils;
import io.github.praneetx.motionfit.utils.OperationResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * Read/write access to the singleton {@code running_sessions} row.
 *
 * <p>All transitions run in a transaction. Finish re-reads the row inside its
 * transaction, inserts exactly one Running workout, deletes the session and only
 * then commits. If anything fails, the transaction rolls back and the session is
 * left in place for a retry.</p>
 */
public class RunningSessionRepository {

    private static final String TAG = "RunningSessionRepo";
    private static final String RUNNING_TYPE = "Running";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_PAUSED = "paused";

    private final FitnessDatabaseHelper helper;

    public RunningSessionRepository(Context context) {
        this.helper = FitnessDatabaseHelper.getInstance(context);
    }

    /**
     * Starts the only allowed session. A second start is rejected and leaves the
     * existing row untouched.
     */
    public OperationResult start(
            String startedAt,
            long startedElapsedMs,
            int bootCount,
            Long startRawSteps) {
        if (!isValidStartedAt(startedAt)) {
            return OperationResult.error("The run start time is invalid.");
        }
        if (startedElapsedMs < 0) {
            return OperationResult.error("The run start time is invalid.");
        }
        if (startRawSteps != null && startRawSteps < 0) {
            return OperationResult.error("Step count cannot be negative.");
        }

        RunningClockState clock = RunningSessionClock.start(startedElapsedMs);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                if (readSession(db) != null) {
                    return OperationResult.error("A run is already in progress.");
                }

                String now = DateUtils.nowTimestamp();
                ContentValues values = new ContentValues();
                values.put(DatabaseContract.COL_RS_ID, RunningSession.SINGLETON_ID);
                values.put(DatabaseContract.COL_RS_STATUS, STATUS_ACTIVE);
                values.put(DatabaseContract.COL_RS_STARTED_AT, startedAt);
                values.put(DatabaseContract.COL_RS_STARTED_ELAPSED_MS, clock.getStartedElapsedMs());
                values.put(DatabaseContract.COL_RS_ACCUMULATED_MS, 0L);
                values.put(DatabaseContract.COL_RS_DURATION_MS, 0L);
                putNullableLong(values, DatabaseContract.COL_RS_LAST_RESUMED_ELAPSED_MS,
                        clock.getLastResumedElapsedMs());
                values.put(DatabaseContract.COL_RS_STEP_COUNT, 0);
                putNullableLong(values, DatabaseContract.COL_RS_START_RAW_STEPS, startRawSteps);
                putNullableLong(values, DatabaseContract.COL_RS_LAST_RAW_STEPS, startRawSteps);
                values.put(DatabaseContract.COL_RS_BOOT_COUNT, bootCount);
                values.put(DatabaseContract.COL_RS_INTERRUPTED, 0);
                values.put(DatabaseContract.COL_RS_CREATED_AT, now);
                values.put(DatabaseContract.COL_RS_UPDATED_AT, now);

                long rowId = db.insertOrThrow(
                        DatabaseContract.TABLE_RUNNING_SESSIONS, null, values);
                if (rowId != RunningSession.SINGLETON_ID) {
                    throw new SQLiteException("Unexpected running_sessions row id: " + rowId);
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return OperationResult.ok();
        } catch (SQLiteException e) {
            Log.e(TAG, "start failed", e);
            return OperationResult.error("Could not start the run. Please try again.");
        }
    }

    /** Returns the current session, or {@code null} when no run is in progress. */
    public RunningSession getSession() {
        try {
            return readSession(helper.getReadableDatabase());
        } catch (SQLiteException e) {
            Log.e(TAG, "getSession failed", e);
            return null;
        }
    }

    /** Pauses the clock and persists the accumulated duration snapshot. */
    public OperationResult pause(long nowElapsedMs) {
        return transitionClock(nowElapsedMs, true);
    }

    /** Resumes the clock and clears any interrupted/recovery flag. */
    public OperationResult resume(long nowElapsedMs) {
        return transitionClock(nowElapsedMs, false);
    }

    /**
     * Persists the periodic duration/step snapshot. The clock is calculated with
     * the pure rules; this method only writes the resulting values.
     */
    public OperationResult update(long nowElapsedMs, int stepCount, Long lastRawSteps) {
        if (stepCount < 0) {
            return OperationResult.error("Step count cannot be negative.");
        }
        if (lastRawSteps != null && lastRawSteps < 0) {
            return OperationResult.error("Step count cannot be negative.");
        }

        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                RunningSession session = readSession(db);
                if (session == null) {
                    return OperationResult.error("No run is in progress.");
                }

                RunningClockState snapshot = RunningSessionClock.snapshot(
                        toClockState(session), nowElapsedMs);
                ContentValues values = new ContentValues();
                values.put(DatabaseContract.COL_RS_DURATION_MS,
                        Math.max(0L, snapshot.getDurationMs()));
                values.put(DatabaseContract.COL_RS_STEP_COUNT, stepCount);
                putNullableLong(values, DatabaseContract.COL_RS_LAST_RAW_STEPS, lastRawSteps);
                values.put(DatabaseContract.COL_RS_UPDATED_AT, DateUtils.nowTimestamp());

                int rows = db.update(
                        DatabaseContract.TABLE_RUNNING_SESSIONS,
                        values,
                        DatabaseContract.COL_RS_ID + " = ?",
                        new String[]{String.valueOf(RunningSession.SINGLETON_ID)});
                if (rows != 1) {
                    throw new SQLiteException("Could not update the running session.");
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return OperationResult.ok();
        } catch (SQLiteException e) {
            Log.e(TAG, "update failed", e);
            return OperationResult.error("Could not update the run. Please try again.");
        }
    }

    /**
     * Freezes the session after a reboot/elapsed-time invalidation and marks it
     * as interrupted. The UI must offer Resume / Finish / Discard.
     */
    public OperationResult markInterrupted(long nowElapsedMs) {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                RunningSession session = readSession(db);
                if (session == null) {
                    return OperationResult.error("No run is in progress.");
                }

                RunningClockState frozen = RunningSessionClock.pause(
                        toClockState(session), nowElapsedMs);
                ContentValues values = clockValues(frozen);
                values.put(DatabaseContract.COL_RS_INTERRUPTED, 1);
                values.put(DatabaseContract.COL_RS_UPDATED_AT, DateUtils.nowTimestamp());

                int rows = db.update(
                        DatabaseContract.TABLE_RUNNING_SESSIONS,
                        values,
                        DatabaseContract.COL_RS_ID + " = ?",
                        new String[]{String.valueOf(RunningSession.SINGLETON_ID)});
                if (rows != 1) {
                    throw new SQLiteException("Could not mark the running session interrupted.");
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return OperationResult.ok();
        } catch (SQLiteException e) {
            Log.e(TAG, "markInterrupted failed", e);
            return OperationResult.error("Could not update the run. Please try again.");
        }
    }

    /**
     * Saves exactly one Running workout and deletes the session in one
     * transaction. An absent session is treated as already finished.
     */
    public OperationResult finish(long nowElapsedMs) {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                RunningSession session = readSession(db);
                if (session == null) {
                    return OperationResult.error("This run has already finished.");
                }
                if (!isValidStartedAt(session.getStartedAt())) {
                    return OperationResult.error("The run start time is invalid.");
                }

                String workoutDate = session.getStartedAt().substring(0, 10);
                RunningClockState finalClock = RunningSessionClock.finish(
                        toClockState(session), nowElapsedMs);
                int durationSeconds = WorkoutDuration.seconds(finalClock.getDurationMs());
                int durationMinutes = WorkoutDuration.minutes(durationSeconds);
                Integer steps = session.getStartRawSteps() == null
                        ? null
                        : Math.max(0, session.getStepCount());

                String now = DateUtils.nowTimestamp();
                ContentValues workout = new ContentValues();
                workout.put(DatabaseContract.COL_W_TYPE, RUNNING_TYPE);
                workout.put(DatabaseContract.COL_W_DATE, workoutDate);
                workout.put(DatabaseContract.COL_W_DURATION, durationMinutes);
                workout.put(DatabaseContract.COL_W_DURATION_SECONDS, durationSeconds);
                putNullableInteger(workout, DatabaseContract.COL_W_STEP_COUNT, steps);
                workout.putNull(DatabaseContract.COL_W_NOTES);
                workout.put(DatabaseContract.COL_W_CREATED_AT, now);
                workout.put(DatabaseContract.COL_W_UPDATED_AT, now);

                long workoutId = db.insertOrThrow(
                        DatabaseContract.TABLE_WORKOUTS, null, workout);
                if (workoutId <= 0L) {
                    throw new SQLiteException("Could not insert the finished workout.");
                }

                int deleted = db.delete(
                        DatabaseContract.TABLE_RUNNING_SESSIONS,
                        DatabaseContract.COL_RS_ID + " = ?",
                        new String[]{String.valueOf(RunningSession.SINGLETON_ID)});
                if (deleted != 1) {
                    throw new SQLiteException("Could not delete the finished running session.");
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return OperationResult.ok();
        } catch (SQLiteException e) {
            Log.e(TAG, "finish failed; session kept", e);
            return OperationResult.error("Could not save the run. Please try again.");
        }
    }

    /** Deletes the session without creating a workout. */
    public OperationResult discard() {
        try {
            helper.getWritableDatabase().delete(
                    DatabaseContract.TABLE_RUNNING_SESSIONS,
                    DatabaseContract.COL_RS_ID + " = ?",
                    new String[]{String.valueOf(RunningSession.SINGLETON_ID)});
            return OperationResult.ok();
        } catch (SQLiteException e) {
            Log.e(TAG, "discard failed", e);
            return OperationResult.error("Could not discard the run. Please try again.");
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private OperationResult transitionClock(long nowElapsedMs, boolean pause) {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                RunningSession session = readSession(db);
                if (session == null) {
                    return OperationResult.error("No run is in progress.");
                }

                RunningClockState before = toClockState(session);
                RunningClockState after = pause
                        ? RunningSessionClock.pause(before, nowElapsedMs)
                        : RunningSessionClock.resume(before, nowElapsedMs);

                ContentValues values = clockValues(after);
                if (!pause) {
                    values.put(DatabaseContract.COL_RS_INTERRUPTED, 0);
                }
                values.put(DatabaseContract.COL_RS_UPDATED_AT, DateUtils.nowTimestamp());

                int rows = db.update(
                        DatabaseContract.TABLE_RUNNING_SESSIONS,
                        values,
                        DatabaseContract.COL_RS_ID + " = ?",
                        new String[]{String.valueOf(RunningSession.SINGLETON_ID)});
                if (rows != 1) {
                    throw new SQLiteException("Could not update the running clock.");
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return OperationResult.ok();
        } catch (SQLiteException e) {
            Log.e(TAG, pause ? "pause failed" : "resume failed", e);
            return OperationResult.error("Could not update the run. Please try again.");
        }
    }

    private static ContentValues clockValues(RunningClockState clock) {
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.COL_RS_STATUS, writeStatus(clock.getStatus()));
        values.put(DatabaseContract.COL_RS_ACCUMULATED_MS,
                Math.max(0L, clock.getAccumulatedMs()));
        values.put(DatabaseContract.COL_RS_DURATION_MS,
                Math.max(0L, clock.getDurationMs()));
        putNullableLong(values, DatabaseContract.COL_RS_LAST_RESUMED_ELAPSED_MS,
                clock.getLastResumedElapsedMs());
        return values;
    }

    private RunningSession readSession(SQLiteDatabase db) {
        try (Cursor cursor = db.query(
                DatabaseContract.TABLE_RUNNING_SESSIONS,
                null,
                DatabaseContract.COL_RS_ID + " = ?",
                new String[]{String.valueOf(RunningSession.SINGLETON_ID)},
                null, null, null)) {
            if (cursor.moveToFirst()) {
                return mapCursor(cursor);
            }
        }
        return null;
    }

    private static RunningSession mapCursor(Cursor cursor) {
        RunningSession session = new RunningSession();
        session.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.COL_RS_ID)));
        session.setStatus(readStatus(cursor.getString(
                cursor.getColumnIndexOrThrow(DatabaseContract.COL_RS_STATUS))));
        session.setStartedAt(cursor.getString(
                cursor.getColumnIndexOrThrow(DatabaseContract.COL_RS_STARTED_AT)));
        session.setStartedElapsedMs(cursor.getLong(
                cursor.getColumnIndexOrThrow(DatabaseContract.COL_RS_STARTED_ELAPSED_MS)));
        session.setAccumulatedMs(cursor.getLong(
                cursor.getColumnIndexOrThrow(DatabaseContract.COL_RS_ACCUMULATED_MS)));
        session.setDurationMs(cursor.getLong(
                cursor.getColumnIndexOrThrow(DatabaseContract.COL_RS_DURATION_MS)));
        session.setLastResumedElapsedMs(nullableLong(cursor,
                DatabaseContract.COL_RS_LAST_RESUMED_ELAPSED_MS));
        session.setStepCount(cursor.getInt(
                cursor.getColumnIndexOrThrow(DatabaseContract.COL_RS_STEP_COUNT)));
        session.setStartRawSteps(nullableLong(cursor,
                DatabaseContract.COL_RS_START_RAW_STEPS));
        session.setLastRawSteps(nullableLong(cursor,
                DatabaseContract.COL_RS_LAST_RAW_STEPS));
        session.setBootCount(cursor.getInt(
                cursor.getColumnIndexOrThrow(DatabaseContract.COL_RS_BOOT_COUNT)));
        session.setInterrupted(cursor.getInt(
                cursor.getColumnIndexOrThrow(DatabaseContract.COL_RS_INTERRUPTED)) != 0);
        session.setCreatedAt(cursor.getString(
                cursor.getColumnIndexOrThrow(DatabaseContract.COL_RS_CREATED_AT)));
        session.setUpdatedAt(cursor.getString(
                cursor.getColumnIndexOrThrow(DatabaseContract.COL_RS_UPDATED_AT)));
        return session;
    }

    private static RunningClockState toClockState(RunningSession session) {
        return new RunningClockState(
                session.getStatus(),
                session.getStartedElapsedMs(),
                session.getAccumulatedMs(),
                session.getLastResumedElapsedMs(),
                session.getDurationMs());
    }

    private static RunningStatus readStatus(String value) {
        return STATUS_ACTIVE.equalsIgnoreCase(value)
                ? RunningStatus.ACTIVE
                : RunningStatus.PAUSED;
    }

    private static String writeStatus(RunningStatus status) {
        return status == RunningStatus.ACTIVE ? STATUS_ACTIVE : STATUS_PAUSED;
    }

    private static Long nullableLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? null : cursor.getLong(index);
    }

    private static void putNullableLong(ContentValues values, String column, Long value) {
        if (value == null) {
            values.putNull(column);
        } else {
            values.put(column, value);
        }
    }

    private static void putNullableInteger(ContentValues values, String column, Integer value) {
        if (value == null) {
            values.putNull(column);
        } else {
            values.put(column, value);
        }
    }

    private static boolean isValidStartedAt(String value) {
        if (value == null || value.length() < 10
                || !DateUtils.isValidIsoDate(value.substring(0, 10))) {
            return false;
        }
        try {
            LocalDateTime.parse(value, DateUtils.TIMESTAMP);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
