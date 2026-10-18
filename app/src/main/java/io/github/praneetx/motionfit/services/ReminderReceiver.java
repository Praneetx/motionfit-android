package io.github.praneetx.motionfit.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.praneetx.motionfit.models.AppSettings;
import io.github.praneetx.motionfit.repositories.SettingsRepository;

/**
 * Fires at the scheduled reminder time: shows the notification and immediately
 * schedules the next day's reminder (a single self-rescheduling alarm is more
 * predictable than a repeating alarm that Android may drift).
 */
public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        AppSettings settings = new SettingsRepository(context).getSettings();
        if (!settings.isReminderEnabled()) {
            // The user turned reminders off between scheduling and delivery.
            return;
        }
        NotificationHelper.showReminder(context, false);
        ReminderScheduler.schedule(context, settings.getReminderHour(), settings.getReminderMinute());
    }
}
