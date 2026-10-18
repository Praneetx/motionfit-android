package io.github.praneetx.motionfit.repositories;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import io.github.praneetx.motionfit.database.DatabaseContract;
import io.github.praneetx.motionfit.database.FitnessDatabaseHelper;
import io.github.praneetx.motionfit.utils.DateUtils;
import io.github.praneetx.motionfit.utils.OperationResult;

/**
 * Implements the destructive "clear all data" action.
 *
 * <p>Documented reset policy: all step records and all workouts are deleted, the
 * fitness goals are restored to their default values (8000 steps / 30 minutes)
 * and reminder preferences are reset to "disabled at 18:00". Everything happens
 * inside a single transaction so a failure cannot leave a half-cleared database.</p>
 */
public class DataResetRepository {

    private static final String TAG = "DataResetRepository";
    private final FitnessDatabaseHelper helper;

    public DataResetRepository(Context context) {
        this.helper = FitnessDatabaseHelper.getInstance(context);
    }

    public OperationResult clearAllData() {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                db.delete(DatabaseContract.TABLE_DAILY_STEPS, null, null);
                db.delete(DatabaseContract.TABLE_WORKOUTS, null, null);

                resetGoal(db);
                resetReminderSettings(db);

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return OperationResult.ok();
        } catch (SQLiteException e) {
            Log.e(TAG, "clearAllData failed", e);
            return OperationResult.error("Could not clear the local data. Please try again.");
        }
    }

    private static void resetGoal(SQLiteDatabase db) {
        ContentValues goal = new ContentValues();
        goal.put(DatabaseContract.COL_G_STEP_GOAL, DatabaseContract.DEFAULT_STEP_GOAL);
        goal.put(DatabaseContract.COL_G_MINUTES_GOAL, DatabaseContract.DEFAULT_WORKOUT_MINUTES_GOAL);
        goal.put(DatabaseContract.COL_G_UPDATED_AT, DateUtils.nowTimestamp());

        int rows = db.update(
                DatabaseContract.TABLE_FITNESS_GOALS,
                goal,
                DatabaseContract.COL_G_ID + " = ?",
                new String[]{"1"});
        if (rows == 0) {
            goal.put(DatabaseContract.COL_G_ID, 1);
            db.insertWithOnConflict(
                    DatabaseContract.TABLE_FITNESS_GOALS,
                    null,
                    goal,
                    SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private static void resetReminderSettings(SQLiteDatabase db) {
        putSetting(db, DatabaseContract.SETTING_REMINDER_ENABLED,
                String.valueOf(DatabaseContract.DEFAULT_REMINDER_ENABLED));
        putSetting(db, DatabaseContract.SETTING_REMINDER_HOUR,
                String.valueOf(DatabaseContract.DEFAULT_REMINDER_HOUR));
        putSetting(db, DatabaseContract.SETTING_REMINDER_MINUTE,
                String.valueOf(DatabaseContract.DEFAULT_REMINDER_MINUTE));
    }

    private static void putSetting(SQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.COL_S_KEY, key);
        values.put(DatabaseContract.COL_S_VALUE, value);
        db.insertWithOnConflict(
                DatabaseContract.TABLE_APP_SETTINGS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }
}
