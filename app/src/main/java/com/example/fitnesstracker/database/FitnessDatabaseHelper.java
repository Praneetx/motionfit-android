package com.example.fitnesstracker.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.example.fitnesstracker.utils.DateUtils;

/**
 * Owns the SQLite database file. It creates every table on first launch, seeds
 * sensible defaults (goals + reminder settings) and contains the (currently
 * empty) migration hook for future schema versions.
 *
 * <p>A single shared instance is used across the whole app so that we never
 * open the database file from several helpers at once.</p>
 */
public class FitnessDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "FitnessDatabaseHelper";

    private static volatile FitnessDatabaseHelper instance;

    /** Returns the process-wide database helper (double-checked locking). */
    public static FitnessDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (FitnessDatabaseHelper.class) {
                if (instance == null) {
                    instance = new FitnessDatabaseHelper(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private FitnessDatabaseHelper(Context context) {
        super(context, DatabaseContract.DATABASE_NAME, null, DatabaseContract.DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Creating database schema (version " + DatabaseContract.DATABASE_VERSION + ")");
        db.execSQL(DatabaseContract.SQL_CREATE_DAILY_STEPS);
        db.execSQL(DatabaseContract.SQL_CREATE_WORKOUTS);
        db.execSQL(DatabaseContract.SQL_CREATE_FITNESS_GOALS);
        db.execSQL(DatabaseContract.SQL_CREATE_APP_SETTINGS);
        db.execSQL(DatabaseContract.SQL_INDEX_WORKOUT_DATE);
        db.execSQL(DatabaseContract.SQL_INDEX_WORKOUT_TYPE);

        // Provide sensible starting values on a brand new install.
        ensureDefaultGoal(db);
        ensureDefaultSettings(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading database " + oldVersion + " -> " + newVersion);
        // Every CREATE uses "IF NOT EXISTS" and every seed uses "INSERT OR IGNORE",
        // so re-running onCreate is safe and handles purely additive schema changes.
        // When a real breaking change is introduced, add a numbered migration below.
        switch (oldVersion) {
            case 1:
                // Example for the future:
                // db.execSQL("ALTER TABLE workouts ADD COLUMN ...");
                break;
            default:
                break;
        }
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Downgrades are not supported in this educational MVP. Recreating the
        // schema is destructive, which is documented in docs/limitations.md.
        Log.w(TAG, "Downgrade " + oldVersion + " -> " + newVersion + "; recreating schema");
        dropAllTables(db);
        onCreate(db);
    }

    // ------------------------------------------------------------------
    // Helpers used by both onCreate and the "clear all data" feature.
    // ------------------------------------------------------------------

    /** Inserts the default goal row if it does not exist yet (id = 1). */
    public static void ensureDefaultGoal(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.COL_G_ID, 1);
        values.put(DatabaseContract.COL_G_STEP_GOAL, DatabaseContract.DEFAULT_STEP_GOAL);
        values.put(DatabaseContract.COL_G_MINUTES_GOAL, DatabaseContract.DEFAULT_WORKOUT_MINUTES_GOAL);
        values.put(DatabaseContract.COL_G_UPDATED_AT, DateUtils.nowTimestamp());
        db.insertWithOnConflict(DatabaseContract.TABLE_FITNESS_GOALS, null, values,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    /** Inserts default reminder settings if they are missing. */
    public static void ensureDefaultSettings(SQLiteDatabase db) {
        insertSettingIfMissing(db, DatabaseContract.SETTING_REMINDER_ENABLED,
                String.valueOf(DatabaseContract.DEFAULT_REMINDER_ENABLED));
        insertSettingIfMissing(db, DatabaseContract.SETTING_REMINDER_HOUR,
                String.valueOf(DatabaseContract.DEFAULT_REMINDER_HOUR));
        insertSettingIfMissing(db, DatabaseContract.SETTING_REMINDER_MINUTE,
                String.valueOf(DatabaseContract.DEFAULT_REMINDER_MINUTE));
    }

    private static void insertSettingIfMissing(SQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.COL_S_KEY, key);
        values.put(DatabaseContract.COL_S_VALUE, value);
        db.insertWithOnConflict(DatabaseContract.TABLE_APP_SETTINGS, null, values,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void dropAllTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + DatabaseContract.TABLE_DAILY_STEPS);
        db.execSQL("DROP TABLE IF EXISTS " + DatabaseContract.TABLE_WORKOUTS);
        db.execSQL("DROP TABLE IF EXISTS " + DatabaseContract.TABLE_FITNESS_GOALS);
        db.execSQL("DROP TABLE IF EXISTS " + DatabaseContract.TABLE_APP_SETTINGS);
    }
}
