package io.github.praneetx.motionfit.repositories;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import io.github.praneetx.motionfit.database.DatabaseContract;
import io.github.praneetx.motionfit.database.FitnessDatabaseHelper;
import io.github.praneetx.motionfit.models.DailySteps;
import io.github.praneetx.motionfit.utils.DateUtils;
import io.github.praneetx.motionfit.utils.OperationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Read/write access to the {@code daily_steps} table.
 *
 * <p>Saving uses an "update, and only insert when nothing was updated" pattern
 * inside a transaction. Combined with the UNIQUE constraint on
 * {@code activity_date}, this guarantees exactly one row per calendar day.</p>
 */
public class StepsRepository {

    private static final String TAG = "StepsRepository";
    private final FitnessDatabaseHelper helper;

    public StepsRepository(Context context) {
        this.helper = FitnessDatabaseHelper.getInstance(context);
    }

    /**
     * Creates or updates the consolidated step total for one date.
     *
     * @param dateIso date in {@code YYYY-MM-DD} form
     * @param steps   non-negative step total
     * @param source  {@link DailySteps#SOURCE_MANUAL} or {@link DailySteps#SOURCE_SENSOR}
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

    /** Returns the step row for a date, or {@code null} when nothing is recorded. */
    public DailySteps getForDate(String dateIso) {
        if (!DateUtils.isValidIsoDate(dateIso)) {
            return null;
        }
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            Cursor cursor = db.query(
                    DatabaseContract.TABLE_DAILY_STEPS,
                    null,
                    DatabaseContract.COL_DS_DATE + " = ?",
                    new String[]{dateIso},
                    null, null, null);
            try (cursor) {
                if (cursor.moveToFirst()) {
                    return mapCursor(cursor);
                }
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "getForDate failed for " + dateIso, e);
        }
        return null;
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

    private static DailySteps mapCursor(Cursor cursor) {
        DailySteps item = new DailySteps();
        item.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.COL_DS_ID)));
        item.setActivityDate(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.COL_DS_DATE)));
        item.setSteps(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.COL_DS_STEPS)));
        item.setSource(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.COL_DS_SOURCE)));
        item.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.COL_DS_CREATED_AT)));
        item.setUpdatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.COL_DS_UPDATED_AT)));
        return item;
    }
}
