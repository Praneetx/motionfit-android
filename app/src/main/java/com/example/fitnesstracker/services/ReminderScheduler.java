package com.example.fitnesstracker.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Calendar;

/**
 * Schedules the single daily reminder alarm.
 *
 * <p>An <b>inexact</b> alarm is used on purpose: it needs no special
 * exact-alarm permission and is friendlier to battery life. The trade-off (the
 * notification can be delivered a little late) is documented in
 * docs/limitations.md.</p>
 */
public final class ReminderScheduler {

    public static final String ACTION_REMINDER = "com.example.fitnesstracker.action.REMINDER";
    private static final int REQUEST_CODE = 2001;
    private static final String TAG = "ReminderScheduler";

    private ReminderScheduler() {
        // Utility class.
    }

    /** Schedules the next occurrence of the daily reminder. */
    public static void schedule(Context context, int hour, int minute) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(TAG, "AlarmManager unavailable");
            return;
        }
        PendingIntent operation = buildPendingIntent(context);
        long triggerAt = nextTriggerMillis(hour, minute);
        // setAndAllowWhileIdle survives Doze; it is inexact, so no permission needed.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation);
        Log.i(TAG, "Next reminder scheduled for " + triggerAt);
    }

    /** Cancels any pending reminder. */
    public static void cancel(Context context) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent operation = buildPendingIntent(context);
        alarmManager.cancel(operation);
        operation.cancel();
    }

    private static PendingIntent buildPendingIntent(Context context) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Epoch millis for the next time-of-day occurrence (today or tomorrow). */
    static long nextTriggerMillis(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return calendar.getTimeInMillis();
    }
}
