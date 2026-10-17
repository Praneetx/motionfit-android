package com.example.fitnesstracker.repositories;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import com.example.fitnesstracker.database.DatabaseContract;
import com.example.fitnesstracker.database.FitnessDatabaseHelper;
import com.example.fitnesstracker.models.Workout;
import com.example.fitnesstracker.utils.DateUtils;
import com.example.fitnesstracker.utils.OperationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Read/write access to the {@code workouts} table.
 *
 * <p>Filters are always passed as bound parameters ({@code ?}) so user input can
 * never be concatenated into SQL.</p>
 */
public class WorkoutRepository {

    private static final String TAG = "WorkoutRepository";
    private final FitnessDatabaseHelper helper;

    public WorkoutRepository(Context context) {
        this.helper = FitnessDatabaseHelper.getInstance(context);
    }

    /** Inserts a new workout after validating its fields. */
    public OperationResult insert(Workout workout) {
        String validationMessage = validate(workout);
        if (validationMessage != null) {
            return OperationResult.error(validationMessage);
        }
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            String now = DateUtils.nowTimestamp();
            ContentValues values = toContentValues(workout);
            values.put(DatabaseContract.COL_W_CREATED_AT, now);
            values.put(DatabaseContract.COL_W_UPDATED_AT, now);
            long id = db.insertOrThrow(DatabaseContract.TABLE_WORKOUTS, null, values);
            if (id == -1L) {
                return OperationResult.error("Could not save the workout. Please try again.");
            }
            workout.setId(id);
            return OperationResult.ok();
        } catch (SQLiteException e) {
            Log.e(TAG, "insert failed", e);
            return OperationResult.error("Could not save the workout. Please try again.");
        }
    }

    /** Updates an existing workout (matched by its id). */
    public OperationResult update(Workout workout) {
        if (workout == null || workout.getId() <= 0) {
            return OperationResult.error("This workout could not be found.");
        }
        String validationMessage = validate(workout);
        if (validationMessage != null) {
            return OperationResult.error(validationMessage);
        }
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            ContentValues values = toContentValues(workout);
            values.put(DatabaseContract.COL_W_UPDATED_AT, DateUtils.nowTimestamp());
            int rows = db.update(
                    DatabaseContract.TABLE_WORKOUTS,
                    values,
                    DatabaseContract.COL_W_ID + " = ?",
                    new String[]{String.valueOf(workout.getId())});
            if (rows == 0) {
                return OperationResult.error("This workout could not be found.");
            }
            return OperationResult.ok();
        } catch (SQLiteException e) {
            Log.e(TAG, "update failed", e);
            return OperationResult.error("Could not update the workout. Please try again.");
        }
    }

    public OperationResult delete(long workoutId) {
        try {
            int rows = helper.getWritableDatabase().delete(
                    DatabaseContract.TABLE_WORKOUTS,
                    DatabaseContract.COL_W_ID + " = ?",
                    new String[]{String.valueOf(workoutId)});
            if (rows == 0) {
                return OperationResult.error("This workout could not be found.");
            }
            return OperationResult.ok();
        } catch (SQLiteException e) {
            Log.e(TAG, "delete failed", e);
            return OperationResult.error("Could not delete the workout. Please try again.");
        }
    }

    public OperationResult deleteAll() {
        try {
            helper.getWritableDatabase().delete(DatabaseContract.TABLE_WORKOUTS, null, null);
            return OperationResult.ok();
        } catch (SQLiteException e) {
            Log.e(TAG, "deleteAll failed", e);
            return OperationResult.error("Could not delete workout records.");
        }
    }

    public Workout getById(long workoutId) {
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            Cursor cursor = db.query(
                    DatabaseContract.TABLE_WORKOUTS,
                    null,
                    DatabaseContract.COL_W_ID + " = ?",
                    new String[]{String.valueOf(workoutId)},
                    null, null, null);
            try (cursor) {
                if (cursor.moveToFirst()) {
                    return mapCursor(cursor);
                }
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "getById failed", e);
        }
        return null;
    }

    /**
     * Returns workouts newest first, optionally filtered.
     *
     * @param typeFilter optional exact workout type (null/blank = all types)
     * @param dateFilter optional exact date in {@code YYYY-MM-DD} (null/blank = all dates)
     * @param limit      maximum rows to return (0 or less = no limit)
     */
    public List<Workout> getWorkouts(String typeFilter, String dateFilter, int limit) {
        StringBuilder selection = new StringBuilder();
        List<String> args = new ArrayList<>();

        if (typeFilter != null && !typeFilter.trim().isEmpty()) {
            selection.append(DatabaseContract.COL_W_TYPE).append(" = ?");
            args.add(typeFilter.trim());
        }
        if (dateFilter != null && !dateFilter.trim().isEmpty()) {
            if (selection.length() > 0) {
                selection.append(" AND ");
            }
            selection.append(DatabaseContract.COL_W_DATE).append(" = ?");
            args.add(dateFilter.trim());
        }

        String orderBy = DatabaseContract.COL_W_DATE + " DESC, " + DatabaseContract.COL_W_ID + " DESC";
        String limitClause = limit > 0 ? String.valueOf(limit) : null;

        List<Workout> results = new ArrayList<>();
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            Cursor cursor = db.query(
                    DatabaseContract.TABLE_WORKOUTS,
                    null,
                    selection.length() > 0 ? selection.toString() : null,
                    args.isEmpty() ? null : args.toArray(new String[0]),
                    null, null,
                    orderBy,
                    limitClause);
            try (cursor) {
                while (cursor.moveToNext()) {
                    results.add(mapCursor(cursor));
                }
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "getWorkouts failed", e);
        }
        return results;
    }

    public List<Workout> getAll() {
        return getWorkouts(null, null, 0);
    }

    public List<Workout> getForDate(String dateIso) {
        return getWorkouts(null, dateIso, 0);
    }

    public List<Workout> getRecent(int limit) {
        return getWorkouts(null, null, limit);
    }

    private static String validate(Workout workout) {
        if (workout == null) {
            return "The workout details are missing.";
        }
        if (workout.getWorkoutType() == null || workout.getWorkoutType().trim().isEmpty()) {
            return "Please choose a workout type.";
        }
        if (!DateUtils.isValidIsoDate(workout.getWorkoutDate())) {
            return "Please choose a valid workout date.";
        }
        if (workout.getDurationMinutes() <= 0) {
            return "Duration must be a positive number of minutes.";
        }
        return null;
    }

    private static ContentValues toContentValues(Workout workout) {
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.COL_W_TYPE, workout.getWorkoutType().trim());
        values.put(DatabaseContract.COL_W_DATE, workout.getWorkoutDate());
        values.put(DatabaseContract.COL_W_DURATION, workout.getDurationMinutes());
        String notes = workout.getNotes();
        if (notes == null || notes.trim().isEmpty()) {
            values.putNull(DatabaseContract.COL_W_NOTES);
        } else {
            values.put(DatabaseContract.COL_W_NOTES, notes.trim());
        }
        return values;
    }

    private static Workout mapCursor(Cursor cursor) {
        Workout workout = new Workout();
        workout.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.COL_W_ID)));
        workout.setWorkoutType(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.COL_W_TYPE)));
        workout.setWorkoutDate(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.COL_W_DATE)));
        workout.setDurationMinutes(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.COL_W_DURATION)));
        int notesIndex = cursor.getColumnIndexOrThrow(DatabaseContract.COL_W_NOTES);
        workout.setNotes(cursor.isNull(notesIndex) ? null : cursor.getString(notesIndex));
        workout.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.COL_W_CREATED_AT)));
        workout.setUpdatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.COL_W_UPDATED_AT)));
        return workout;
    }
}
