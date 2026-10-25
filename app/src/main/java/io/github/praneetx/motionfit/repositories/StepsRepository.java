package io.github.praneetx.motionfit.repositories;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import io.github.praneetx.motionfit.database.DatabaseContract;
import io.github.praneetx.motionfit.database.FitnessDatabaseHelper;
import io.github.praneetx.motionfit.logic.StepCountEngine;
import io.github.praneetx.motionfit.logic.StepDayState;
import io.github.praneetx.motionfit.logic.StepSource;
import io.github.praneetx.motionfit.logic.StepUpdate;
import io.github.praneetx.motionfit.logic.StepWriteMode;
import io.github.praneetx.motionfit.models.DailySteps;
import io.github.praneetx.motionfit.utils.DateUtils;
import io.github.praneetx.motionfit.utils.OperationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Read/write access to the {@code daily_steps} table.
 *
 * <p>The table stores one consolidated total and, for automatic tracking, the
 * latest raw {@code TYPE_STEP_COUNTER} checkpoint. Sensor writes are conditional
 * on the checkpoint that was read; if another writer changed it, the row is
 * reloaded and the reading is recomputed instead of overwriting a correction.</p>
 *
 * <p>Saving a manual total uses the "update, and only insert when nothing was
 * updated" pattern inside a transaction. Combined with the UNIQUE constraint on
 * {@code activity_date}, this guarantees exactly one row per calendar day.</p>
 */
public class StepsRepository {

    private static final String TAG = "StepsRepository";

    /** Bound retries for a genuine concurrent writer changing the checkpoint. */
    private static final int MAX_CONFLICT_ATTEMPTS = 5;

    private final FitnessDatabaseHelper helper;

    public StepsRepository(Context context) {
        this.helper = FitnessDatabaseHelper.getInstance(context);
    }

    /**
     * Creates or updates the consolidated step total for one date.
     *
     * <p>This is the legacy public save method. It remains source-compatible but
     * now always clears the sensor checkpoint, because a typed total must not be
     * undone by the next sensor reading.</p>
     *
     * @param dateIso date in {@code YYYY-MM-DD} form
     * @param steps   non-negative step total
     * @param source  usually {@link DailySteps#SOURCE_MANUAL}
     */
    public OperationResult saveDailySteps(String dateIso, int steps, String source) {
        if (steps < 0) {
            return OperationResult.error("Step total cannot be negative.");
        }
        if (!DateUtils.isValidIsoDate(dateIso)) {
            return OperationResult.error("The selected date is not valid.");
        }
        String safeSource = (source == null || source.trim().isEmpty())
                ? DailySteps.SOURCE_MANUAL
                : source.trim();

        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                ContentValues update = new ContentValues();
                update.put(DatabaseContract.COL_DS_STEPS, steps);
                update.put(DatabaseContract.COL_DS_SOURCE, safeSource);
                update.putNull(DatabaseContract.COL_DS_LAST_RAW_SENSOR);
                update.put(DatabaseContract.COL_DS_UPDATED_AT, DateUtils.nowTimestamp());

                int rowsUpdated = db.update(
                        DatabaseContract.TABLE_DAILY_STEPS,
                        update,
                        DatabaseContract.COL_DS_DATE + " = ?",
                        new String[]{dateIso});

                if (rowsUpdated == 0) {
                    // No row for this date yet, so create one.
                    ContentValues insert = new ContentValues(update);
                    String now = DateUtils.nowTimestamp();
                    insert.put(DatabaseContract.COL_DS_DATE, dateIso);
                    insert.put(DatabaseContract.COL_DS_CREATED_AT, now);
                    insert.put(DatabaseContract.COL_DS_UPDATED_AT, now);
                    // CONFLICT_IGNORE protects against a race with another insert.
                    db.insertWithOnConflict(
                            DatabaseContract.TABLE_DAILY_STEPS,
                            null,
                            insert,
                            SQLiteDatabase.CONFLICT_IGNORE);
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return OperationResult.ok();
        } catch (SQLiteException e) {
            Log.e(TAG, "saveDailySteps failed for " + dateIso, e);
            return OperationResult.error("Could not save the step total. Please try again.");
        }
    }

    /**
     * Saves an absolute manual total with automatic tracking off. The sensor
     * checkpoint is cleared and the source becomes {@code manual}.
     */
    public OperationResult saveManualTotal(String dateIso, int total) {
        return saveDailySteps(dateIso, total, DailySteps.SOURCE_MANUAL);
    }

    /**
     * Applies one raw {@code TYPE_STEP_COUNTER} reading to a date.
     *
     * <p>Reads the current row, asks {@link StepCountEngine} for the next state,
     * and writes it conditionally on the old checkpoint. On a checkpoint
     * conflict it reloads and retries; it never overwrites a newer correction.</p>
     */
    public OperationResult applySensorReading(String dateIso, long raw) {
        if (!DateUtils.isValidIsoDate(dateIso)) {
            return OperationResult.error("The selected date is not valid.");
        }
        return applySensorReading(dateIso, raw, getForDate(dateIso));
    }

    /**
     * Applies a reading from an already-loaded row snapshot. The public path
     * always supplies a fresh row; if the snapshot is stale, the conditional
     * write detects it, reloads and retries.
     */
    OperationResult applySensorReading(String dateIso, long raw, DailySteps loaded) {
        if (!DateUtils.isValidIsoDate(dateIso)) {
            return OperationResult.error("The selected date is not valid.");
        }
        try {
            DailySteps current = loaded;
            for (int attempt = 0; attempt < MAX_CONFLICT_ATTEMPTS; attempt++) {
                StepUpdate update = StepCountEngine.applyReading(
                        toStepDayState(current), raw, dateIso);
                if (!update.getWriteRequired()) {
                    return OperationResult.ok();
                }

                int rows = writeSensorUpdate(dateIso, current, update);
                if (rows > 0) {
                    return OperationResult.ok();
                }

                // Another writer changed the checkpoint (or created/deleted the
                // row) after it was read. Reload and recompute from current data.
                current = readForDate(dateIso);
            }
            return OperationResult.error(
                    "The step record changed while saving. Please try again.");
        } catch (SQLiteException e) {
            Log.e(TAG, "applySensorReading failed for " + dateIso + " raw=" + raw, e);
            return OperationResult.error("Could not apply the step reading. Please try again.");
        }
    }

    /**
     * Sets an absolute user total. When {@code rawNow} is available it becomes
     * the new checkpoint and the source becomes {@code sensor_adjusted}; when it
     * is {@code null} the checkpoint is cleared and the source is {@code manual}.
     */
    public OperationResult calibrate(String dateIso, int total, Long rawNow) {
        if (total < 0) {
            return OperationResult.error("Step total cannot be negative.");
        }
        if (!DateUtils.isValidIsoDate(dateIso)) {
            return OperationResult.error("The selected date is not valid.");
        }
        return calibrate(dateIso, total, rawNow, getForDate(dateIso));
    }

    /**
     * Applies a calibration from an already-loaded row snapshot. The public
     * path starts from a fresh row; a conflict reloads and retries.
     */
    OperationResult calibrate(String dateIso, int total, Long rawNow, DailySteps loaded) {
        if (total < 0) {
            return OperationResult.error("Step total cannot be negative.");
        }
        if (!DateUtils.isValidIsoDate(dateIso)) {
            return OperationResult.error("The selected date is not valid.");
        }
        try {
            DailySteps current = loaded;
            for (int attempt = 0; attempt < MAX_CONFLICT_ATTEMPTS; attempt++) {
                StepUpdate update = StepCountEngine.calibrate(
                        toStepDayState(current), total, rawNow, dateIso);
                int rows = writeCalibrationUpdate(dateIso, current, update);
                if (rows > 0) {
                    return OperationResult.ok();
                }
                current = readForDate(dateIso);
            }
            return OperationResult.error(
                    "The step record changed while saving. Please try again.");
        } catch (SQLiteException e) {
            Log.e(TAG, "calibrate failed for " + dateIso, e);
            return OperationResult.error("Could not save the step total. Please try again.");
        }
    }

    /** Returns the step row for a date, or {@code null} when nothing is recorded. */
    public DailySteps getForDate(String dateIso) {
        if (!DateUtils.isValidIsoDate(dateIso)) {
            return null;
        }
        try {
            return readForDate(dateIso);
        } catch (SQLiteException e) {
            Log.e(TAG, "getForDate failed for " + dateIso, e);
            return null;
        }
    }

    /** All step records, newest calendar date first. */
    public List<DailySteps> getAll() {
        List<DailySteps> results = new ArrayList<>();
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            Cursor cursor = db.query(
                    DatabaseContract.TABLE_DAILY_STEPS,
                    null, null, null, null, null,
                    DatabaseContract.COL_DS_DATE + " DESC");
            try (cursor) {
                while (cursor.moveToNext()) {
                    results.add(mapCursor(cursor));
                }
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "getAll failed", e);
        }
        return results;
    }

    /** Deletes every step record (used by "clear all data"). */
    public OperationResult deleteAll() {
        try {
            helper.getWritableDatabase().delete(DatabaseContract.TABLE_DAILY_STEPS, null, null);
            return OperationResult.ok();
        } catch (SQLiteException e) {
            Log.e(TAG, "deleteAll failed", e);
            return OperationResult.error("Could not delete step records.");
        }
    }

    // ------------------------------------------------------------------
    // Write helpers
    // ------------------------------------------------------------------

    private int writeSensorUpdate(String dateIso, DailySteps current, StepUpdate update) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            int rows;
            if (current == null) {
                rows = insertState(db, dateIso, update);
            } else if (update.getWriteMode() == StepWriteMode.ABSOLUTE) {
                rows = updateAbsolute(db, dateIso, update);
            } else {
                rows = updateDelta(db, dateIso, update);
            }
            db.setTransactionSuccessful();
            return rows;
        } finally {
            db.endTransaction();
        }
    }

    private int writeCalibrationUpdate(String dateIso, DailySteps current, StepUpdate update) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            int rows = current == null
                    ? insertState(db, dateIso, update)
                    : updateAbsolute(db, dateIso, update);
            db.setTransactionSuccessful();
            return rows;
        } finally {
            db.endTransaction();
        }
    }

    private static int insertState(SQLiteDatabase db, String dateIso, StepUpdate update) {
        ContentValues values = stateValues(update);
        String now = DateUtils.nowTimestamp();
        values.put(DatabaseContract.COL_DS_DATE, dateIso);
        values.put(DatabaseContract.COL_DS_CREATED_AT, now);
        values.put(DatabaseContract.COL_DS_UPDATED_AT, now);
        long rowId = db.insertWithOnConflict(
                DatabaseContract.TABLE_DAILY_STEPS,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE);
        return rowId == -1 ? 0 : 1;
    }

    private static int updateDelta(SQLiteDatabase db, String dateIso, StepUpdate update) {
        StepDayState state = update.getState();
        Long expected = update.getExpectedLastRawSensor();
        String sql = "UPDATE " + DatabaseContract.TABLE_DAILY_STEPS
                + " SET " + DatabaseContract.COL_DS_STEPS + " = "
                + DatabaseContract.COL_DS_STEPS + " + ?, "
                + DatabaseContract.COL_DS_LAST_RAW_SENSOR + " = ?, "
                + DatabaseContract.COL_DS_SOURCE + " = ?, "
                + DatabaseContract.COL_DS_UPDATED_AT + " = ?"
                + " WHERE " + DatabaseContract.COL_DS_DATE + " = ?"
                + " AND " + DatabaseContract.COL_DS_LAST_RAW_SENSOR
                + (expected == null ? " IS NULL" : " = ?");

        SQLiteStatement statement = db.compileStatement(sql);
        try {
            statement.bindLong(1, Math.max(0, update.getStepsDelta()));
            bindNullableLong(statement, 2, state.getLastRawSensor());
            statement.bindString(3, state.getSource().getWireValue());
            statement.bindString(4, DateUtils.nowTimestamp());
            statement.bindString(5, dateIso);
            if (expected != null) {
                statement.bindLong(6, expected);
            }
            return statement.executeUpdateDelete();
        } finally {
            statement.close();
        }
    }

    private static int updateAbsolute(SQLiteDatabase db, String dateIso, StepUpdate update) {
        ContentValues values = stateValues(update);
        values.put(DatabaseContract.COL_DS_UPDATED_AT, DateUtils.nowTimestamp());

        Long expected = update.getExpectedLastRawSensor();
        String selection = DatabaseContract.COL_DS_DATE + " = ? AND "
                + DatabaseContract.COL_DS_LAST_RAW_SENSOR
                + (expected == null ? " IS NULL" : " = ?");
        String[] args = expected == null
                ? new String[]{dateIso}
                : new String[]{dateIso, String.valueOf(expected)};
        return db.update(
                DatabaseContract.TABLE_DAILY_STEPS,
                values,
                selection,
                args);
    }

    private static ContentValues stateValues(StepUpdate update) {
        StepDayState state = update.getState();
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.COL_DS_STEPS, Math.max(0, state.getSteps()));
        if (state.getLastRawSensor() == null) {
            values.putNull(DatabaseContract.COL_DS_LAST_RAW_SENSOR);
        } else {
            values.put(DatabaseContract.COL_DS_LAST_RAW_SENSOR, state.getLastRawSensor());
        }
        values.put(DatabaseContract.COL_DS_SOURCE, state.getSource().getWireValue());
        return values;
    }

    private static void bindNullableLong(SQLiteStatement statement, int index, Long value) {
        if (value == null) {
            statement.bindNull(index);
        } else {
            statement.bindLong(index, value);
        }
    }

    private DailySteps readForDate(String dateIso) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(
                DatabaseContract.TABLE_DAILY_STEPS,
                null,
                DatabaseContract.COL_DS_DATE + " = ?",
                new String[]{dateIso},
                null, null, null)) {
            if (cursor.moveToFirst()) {
                return mapCursor(cursor);
            }
        }
        return null;
    }

    private static StepDayState toStepDayState(DailySteps row) {
        if (row == null) {
            return null;
        }
        return new StepDayState(
                row.getActivityDate(),
                row.getSteps(),
                row.getLastRawSensor(),
                StepSource.fromWireValue(row.getSource()));
    }

    private static DailySteps mapCursor(Cursor cursor) {
        DailySteps item = new DailySteps();
        item.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.COL_DS_ID)));
        item.setActivityDate(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.COL_DS_DATE)));
        item.setSteps(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.COL_DS_STEPS)));
        item.setSource(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.COL_DS_SOURCE)));
        int checkpointIndex = cursor.getColumnIndexOrThrow(DatabaseContract.COL_DS_LAST_RAW_SENSOR);
        item.setLastRawSensor(cursor.isNull(checkpointIndex) ? null : cursor.getLong(checkpointIndex));
        item.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.COL_DS_CREATED_AT)));
        item.setUpdatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.COL_DS_UPDATED_AT)));
        return item;
    }
}
