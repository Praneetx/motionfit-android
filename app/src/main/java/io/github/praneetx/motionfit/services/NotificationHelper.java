package io.github.praneetx.motionfit.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import io.github.praneetx.motionfit.R;
import io.github.praneetx.motionfit.activities.MainActivity;

/**
 * Creates the notification channel and posts local reminder notifications.
 * Nothing here uses a remote/cloud notification service - notifications are
 * generated entirely on the device.
 */
public final class NotificationHelper {

    public static final String CHANNEL_ID = "activity_reminders";
    public static final int NOTIFICATION_ID = 1001;

    /** Separate low-importance channel for the optional automatic tracker. */
    public static final String TRACKING_CHANNEL_ID = "automatic_step_tracking";
    public static final int TRACKING_NOTIFICATION_ID = 1002;

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

    /** Creates the low-importance automatic-tracking channel. */
    public static void ensureTrackingChannel(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                TRACKING_CHANNEL_ID,
                context.getString(R.string.tracking_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(context.getString(R.string.tracking_notification_channel_desc));
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
    }

    /**
     * True only when a persistent tracking notification can actually be seen.
     * A permission denial or a channel the user blocked both count as blocked.
     */
    public static boolean areTrackingNotificationsEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(context);
        if (!managerCompat.areNotificationsEnabled()) {
            return false;
        }
        ensureTrackingChannel(context);
        NotificationChannel channel = managerCompat.getNotificationChannel(TRACKING_CHANNEL_ID);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    /**
     * Builds the persistent tracking notification with a generic public version
     * so the lock screen never shows step values.
     */
    public static Notification buildTrackingNotification(Context context) {
        ensureTrackingChannel(context);

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification publicVersion = new NotificationCompat.Builder(context, TRACKING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.tracking_notification_public_title))
                .setContentText(context.getString(R.string.tracking_notification_public_text))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setShowWhen(false)
                .build();

        return new NotificationCompat.Builder(context, TRACKING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.tracking_notification_title))
                .setContentText(context.getString(R.string.tracking_notification_text))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        context.getString(R.string.tracking_notification_text)))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
