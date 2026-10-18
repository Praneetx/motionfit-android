package io.github.praneetx.motionfit.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.praneetx.motionfit.models.AppSettings;
import io.github.praneetx.motionfit.repositories.SettingsRepository;

/**
 * AlarmManager alarms do not survive a reboot, so this receiver restores the
 * daily reminder when the device finishes booting (only if the user enabled it).
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        AppSettings settings = new SettingsRepository(context).getSettings();
        if (settings.isReminderEnabled()) {
            ReminderScheduler.schedule(context, settings.getReminderHour(), settings.getReminderMinute());
        }
    }
}
