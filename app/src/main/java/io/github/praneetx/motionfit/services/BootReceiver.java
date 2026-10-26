package io.github.praneetx.motionfit.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import io.github.praneetx.motionfit.models.AppSettings;
import io.github.praneetx.motionfit.repositories.RunningSessionRepository;
import io.github.praneetx.motionfit.repositories.SettingsRepository;

/**
 * Restores work that Android alarms/services cannot preserve across a reboot.
 *
 * <p>The reminder is re-scheduled when enabled. Any in-progress run is frozen and
 * marked interrupted so the UI can offer Resume / Finish / Discard with the last
 * recorded duration. Automatic daily tracking is started best-effort only when
 * the user enabled it and all foreground requirements are already present; a
 * blocked background start is caught and reported through the normal status.</p>
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

        // No live process can own a session after a reboot; freeze it for recovery.
        new RunningSessionRepository(context).markInterrupted(SystemClock.elapsedRealtime());

        if (settings.isAutoStepsEnabled()) {
            ActivityTrackingService.startFromBoot(context);
        }
    }
}
