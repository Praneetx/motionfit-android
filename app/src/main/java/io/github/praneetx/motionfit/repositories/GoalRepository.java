package io.github.praneetx.motionfit.repositories;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import io.github.praneetx.motionfit.database.DatabaseContract;
import io.github.praneetx.motionfit.database.FitnessDatabaseHelper;
import io.github.praneetx.motionfit.models.FitnessGoal;
import io.github.praneetx.motionfit.utils.DateUtils;
import io.github.praneetx.motionfit.utils.OperationResult;

/**
 * Read/write access to the single-row {@code fitness_goals} table (id = 1).
 */
public class GoalRepository {

    private static final String TAG = "GoalRepository";
    private final FitnessDatabaseHelper helper;

    public GoalRepository(Context context) {
        this.helper = FitnessDatabaseHelper.getInstance(context);
    }

    /** Returns the current goal, falling back to the defaults if it is missing. */
    public FitnessGoal getGoal() {
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            Cursor cursor = db.query(
                    DatabaseContract.TABLE_FITNESS_GOALS,
                    new String[]{
                            DatabaseContract.COL_G_ID,
                            DatabaseContract.COL_G_STEP_GOAL,
                            DatabaseContract.COL_G_MINUTES_GOAL,
                            DatabaseContract.COL_G_UPDATED_AT
                    },
                    DatabaseContract.COL_G_ID + " = ?",
                    new String[]{"1"},
                    null, null, null);
            try (cursor) {
                if (cursor.moveToFirst()) {
                    FitnessGoal goal = new FitnessGoal();
                    goal.setId(cursor.getLong(0));
                    goal.setDailyStepGoal(cursor.getInt(1));
                    goal.setDailyWorkoutMinutesGoal(cursor.getInt(2));
                    goal.setUpdatedAt(cursor.getString(3));
                    return goal;
                }
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "getGoal failed", e);
        }
        // Safe fallback so the UI never has to handle a null goal.
        FitnessGoal fallback = new FitnessGoal(
                DatabaseContract.DEFAULT_STEP_GOAL,
                DatabaseContract.DEFAULT_WORKOUT_MINUTES_GOAL);
        fallback.setId(1);
        return fallback;
    }

    /** Saves new goal values, rejecting zero or negative targets. */
    public OperationResult saveGoal(int dailyStepGoal, int dailyWorkoutMinutesGoal) {
        if (dailyStepGoal <= 0) {
            return OperationResult.error("Daily step goal must be greater than zero.");
        }
        if (dailyWorkoutMinutesGoal <= 0) {
            return OperationResult.error("Daily workout goal must be greater than zero.");
        }
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(DatabaseContract.COL_G_STEP_GOAL, dailyStepGoal);
            values.put(DatabaseContract.COL_G_MINUTES_GOAL, dailyWorkoutMinutesGoal);
            values.put(DatabaseContract.COL_G_UPDATED_AT, DateUtils.nowTimestamp());

            int rowsUpdated = db.update(
                    DatabaseContract.TABLE_FITNESS_GOALS,
                    values,
                    DatabaseContract.COL_G_ID + " = ?",
                    new String[]{"1"});
            if (rowsUpdated == 0) {
                values.put(DatabaseContract.COL_G_ID, 1);
                db.insertWithOnConflict(
                        DatabaseContract.TABLE_FITNESS_GOALS,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE);
            }
            return OperationResult.ok();
        } catch (SQLiteException e) {
            Log.e(TAG, "saveGoal failed", e);
            return OperationResult.error("Could not save the goals. Please try again.");
        }
    }

    /** Restores the default goal values (used by "clear all data"). */
    public OperationResult resetToDefaults() {
        return saveGoal(DatabaseContract.DEFAULT_STEP_GOAL, DatabaseContract.DEFAULT_WORKOUT_MINUTES_GOAL);
    }
}
