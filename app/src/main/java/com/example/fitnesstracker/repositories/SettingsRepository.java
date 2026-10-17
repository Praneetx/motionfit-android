package com.example.fitnesstracker.repositories;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import com.example.fitnesstracker.database.DatabaseContract;
import com.example.fitnesstracker.database.FitnessDatabaseHelper;
import com.example.fitnesstracker.models.AppSettings;
import com.example.fitnesstracker.utils.OperationResult;

/**
 * Read/write access to the key/value {@code app_settings} table. Missing keys
 * simply fall back to the documented default values.
 */
public class SettingsRepository {

    private static final String TAG = "SettingsRepository";
    private final FitnessDatabaseHelper helper;

    public SettingsRepository(Context context) {
        this.helper = FitnessDatabaseHelper.getInstance(context);
    }

    public AppSettings getSettings() {
        AppSettings settings = new AppSettings(
                DatabaseContract.DEFAULT_REMINDER_ENABLED,
                DatabaseContract.DEFAULT_REMINDER_HOUR,
                DatabaseContract.DEFAULT_REMINDER_MINUTE);
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            Cursor cursor = db.query(
                    DatabaseContract.TABLE_APP_SETTINGS,
                    new String[]{DatabaseContract.COL_S_KEY, DatabaseContract.COL_S_VALUE},
                    null, null, null, null, null);
            try (cursor) {
                while (cursor.moveToNext()) {
                    applySetting(settings, cursor.getString(0), cursor.getString(1));
                }
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "getSettings failed", e);
        }
        return settings;
    }

    /** Saves the reminder preference set in a single transaction. */
    public OperationResult saveReminder(boolean enabled, int hour, int minute) {
        if (hour < 0 || hour > 23) {
            return OperationResult.error("Reminder hour must be between 0 and 23.");
        }
        if (minute < 0 || minute > 59) {
            return OperationResult.error("Reminder minute must be between 0 and 59.");
        }
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                putSetting(db, DatabaseContract.SETTING_REMINDER_ENABLED, String.valueOf(enabled));
                putSetting(db, DatabaseContract.SETTING_REMINDER_HOUR, String.valueOf(hour));
                putSetting(db, DatabaseContract.SETTING_REMINDER_MINUTE, String.valueOf(minute));
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return OperationResult.ok();
        } catch (SQLiteException e) {
            Log.e(TAG, "saveReminder failed", e);
            return OperationResult.error("Could not save reminder settings. Please try again.");
        }
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

    private static void applySetting(AppSettings settings, String key, String value) {
        if (key == null) {
            return;
        }
        try {
            switch (key) {
                case DatabaseContract.SETTING_REMINDER_ENABLED:
                    settings.setReminderEnabled("true".equalsIgnoreCase(value));
                    break;
                case DatabaseContract.SETTING_REMINDER_HOUR:
                    settings.setReminderHour(clamp(Integer.parseInt(value), 0, 23));
                    break;
                case DatabaseContract.SETTING_REMINDER_MINUTE:
                    settings.setReminderMinute(clamp(Integer.parseInt(value), 0, 59));
                    break;
                default:
                    break;
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Ignoring malformed setting value for key " + key);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
