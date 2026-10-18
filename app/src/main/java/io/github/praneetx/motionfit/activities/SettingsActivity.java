package io.github.praneetx.motionfit.activities;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import io.github.praneetx.motionfit.R;
import io.github.praneetx.motionfit.models.AppSettings;
import io.github.praneetx.motionfit.repositories.DataResetRepository;
import io.github.praneetx.motionfit.repositories.SettingsRepository;
import io.github.praneetx.motionfit.services.NotificationHelper;
import io.github.praneetx.motionfit.services.ReminderScheduler;
import io.github.praneetx.motionfit.utils.FormatUtils;
import io.github.praneetx.motionfit.utils.OperationResult;
import io.github.praneetx.motionfit.views.FloatingNavView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Settings: goals shortcut, local reminder preferences, the destructive
 * "clear all data" action, and application information.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATIONS = 201;

    private SettingsRepository settingsRepository;
    private DataResetRepository dataResetRepository;
    private AppSettings settings;

    private MaterialSwitch reminderSwitch;
    private TextView reminderTimeView;
    private TextView notificationsBlockedView;

    /** True while the switch is being set programmatically, to ignore the callback. */
    private boolean bindingSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settingsRepository = new SettingsRepository(this);
        dataResetRepository = new DataResetRepository(this);

        reminderSwitch = findViewById(R.id.switchReminder);
        reminderTimeView = findViewById(R.id.reminderTimeValue);
        notificationsBlockedView = findViewById(R.id.notificationsBlocked);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnEditGoals).setOnClickListener(
                v -> startActivity(new Intent(this, GoalsActivity.class)));
        findViewById(R.id.btnChangeTime).setOnClickListener(v -> showTimePicker());
        findViewById(R.id.btnTestNotification).setOnClickListener(v -> sendTestNotification());
        findViewById(R.id.btnClearData).setOnClickListener(v -> confirmClearData());

        reminderSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (bindingSwitch) {
                return;
            }
            saveReminder(isChecked, settings.getReminderHour(), settings.getReminderMinute());
        });

        loadSettings();

        FloatingNavView.bind(this, FloatingNavView.DEST_SETTINGS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotificationsBlocked();
    }

    private void loadSettings() {
        settings = settingsRepository.getSettings();
        bindingSwitch = true;
        reminderSwitch.setChecked(settings.isReminderEnabled());
        bindingSwitch = false;
        reminderTimeView.setText(
                FormatUtils.timeText(settings.getReminderHour(), settings.getReminderMinute()));
        updateNotificationsBlocked();
    }

    private void showTimePicker() {
        new TimePickerDialog(this, (view, hourOfDay, minute) ->
                saveReminder(reminderSwitch.isChecked(), hourOfDay, minute),
                settings.getReminderHour(), settings.getReminderMinute(), true).show();
    }

    private void saveReminder(boolean enabled, int hour, int minute) {
        OperationResult result = settingsRepository.saveReminder(enabled, hour, minute);
        if (!result.isSuccess()) {
            Toast.makeText(this, R.string.settings_reminder_save_failed, Toast.LENGTH_LONG).show();
            loadSettings();
            return;
        }

        settings.setReminderEnabled(enabled);
        settings.setReminderHour(hour);
        settings.setReminderMinute(minute);
        reminderTimeView.setText(FormatUtils.timeText(hour, minute));

        if (enabled) {
            NotificationHelper.ensureChannel(this);
            ReminderScheduler.schedule(this, hour, minute);
            requestNotificationPermissionIfNeeded();
        } else {
            ReminderScheduler.cancel(this);
        }

        updateNotificationsBlocked();
        Toast.makeText(this, R.string.settings_reminder_saved, Toast.LENGTH_SHORT).show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private void sendTestNotification() {
        boolean shown = NotificationHelper.showReminder(this, true);
        if (!shown) {
            Toast.makeText(this, R.string.settings_notifications_blocked, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.notification_test_title, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClearData() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_clear_title)
                .setMessage(R.string.settings_clear_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.settings_clear_confirm, (dialog, which) -> clearAllData())
                .show();
    }

    private void clearAllData() {
        OperationResult result = dataResetRepository.clearAllData();
        if (result.isSuccess()) {
            // Any scheduled reminder must also be removed.
            ReminderScheduler.cancel(this);
            loadSettings();
            Toast.makeText(this, R.string.settings_cleared, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.settings_clear_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void updateNotificationsBlocked() {
        boolean enabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        notificationsBlockedView.setVisibility(enabled ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ReminderScheduler.schedule(this, settings.getReminderHour(), settings.getReminderMinute());
        } else {
            Toast.makeText(this, R.string.settings_notifications_blocked, Toast.LENGTH_LONG).show();
        }
        updateNotificationsBlocked();
    }
}
