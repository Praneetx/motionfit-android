package com.example.fitnesstracker.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.fitnesstracker.R;
import com.example.fitnesstracker.activities.MainActivity;

/**
 * Creates the notification channel and posts local reminder notifications.
 * Nothing here uses a remote/cloud notification service - notifications are
 * generated entirely on the device.
 */
public final class NotificationHelper {

    public static final String CHANNEL_ID = "activity_reminders";
    public static final int NOTIFICATION_ID = 1001;

    private static final String TAG = "NotificationHelper";

    private NotificationHelper() {
        // Utility class.
    }

    /** Creates the notification channel (safe to call repeatedly). */
    public static void ensureChannel(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_desc));
        manager.createNotificationChannel(channel);
    }

    /** Shows the daily activity reminder (or a test notification). */
    public static boolean showReminder(Context context, boolean isTest) {
        ensureChannel(context);

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = context.getString(isTest
                ? R.string.notification_test_title
                : R.string.notification_title);
        String text = context.getString(isTest
                ? R.string.notification_test_text
                : R.string.notification_text);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(context);
        if (!managerCompat.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled; reminder not shown.");
            return false;
        }
        try {
            managerCompat.notify(NOTIFICATION_ID, builder.build());
            return true;
        } catch (SecurityException e) {
            // Happens when POST_NOTIFICATIONS was not granted on Android 13+.
            Log.w(TAG, "Missing notification permission", e);
            return false;
        }
    }
}
