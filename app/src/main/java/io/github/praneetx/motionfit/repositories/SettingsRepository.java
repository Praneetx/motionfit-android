package io.github.praneetx.motionfit.repositories;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import io.github.praneetx.motionfit.database.DatabaseContract;
import io.github.praneetx.motionfit.database.FitnessDatabaseHelper;
import io.github.praneetx.motionfit.models.AppSettings;
import io.github.praneetx.motionfit.utils.OperationResult;

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

    public boolean isAutoStepsEnabled() {
        return getSettings().isAutoStepsEnabled();
    }

    /** Opts the user into or out of automatic daily step counting. */
    public OperationResult setAutoStepsEnabled(boolean enabled) {
        return saveBooleanSetting(DatabaseContract.SETTING_AUTO_STEPS_ENABLED, enabled);
    }

    public boolean isOnboardingCompleted() {
        return getSettings().isOnboardingCompleted();
    }

    /** Records whether the one-time V2 onboarding has been shown/finished. */
    public OperationResult setOnboardingCompleted(boolean completed) {
        return saveBooleanSetting(DatabaseContract.SETTING_ONBOARDING_COMPLETED, completed);
    }

    private OperationResult saveBooleanSetting(String key, boolean value) {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                putSetting(db, key, String.valueOf(value));
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return OperationResult.ok();
        } catch (SQLiteException e) {
            Log.e(TAG, "Saving setting " + key + " failed", e);
            return OperationResult.error("Could not save settings. Please try again.");
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
                case DatabaseContract.SETTING_AUTO_STEPS_ENABLED:
                    settings.setAutoStepsEnabled("true".equalsIgnoreCase(value));
                    break;
                case DatabaseContract.SETTING_ONBOARDING_COMPLETED:
                    settings.setOnboardingCompleted("true".equalsIgnoreCase(value));
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
