package io.github.praneetx.motionfit.activities;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import io.github.praneetx.motionfit.BuildConfig;
import io.github.praneetx.motionfit.R;
import io.github.praneetx.motionfit.logic.TrackingMode;
import io.github.praneetx.motionfit.logic.TrackingStartResult;
import io.github.praneetx.motionfit.models.AppSettings;
import io.github.praneetx.motionfit.repositories.DataResetRepository;
import io.github.praneetx.motionfit.repositories.RunningSessionRepository;
import io.github.praneetx.motionfit.repositories.SettingsRepository;
import io.github.praneetx.motionfit.services.ActivityTrackingService;
import io.github.praneetx.motionfit.services.NotificationHelper;
import io.github.praneetx.motionfit.services.ReminderScheduler;
import io.github.praneetx.motionfit.utils.FormatUtils;
import io.github.praneetx.motionfit.utils.OperationResult;
import io.github.praneetx.motionfit.views.FloatingNavView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings: goals, automatic step-tracking controls, local reminders, the
 * destructive "clear all data" action, and application information.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATIONS = 201;
    private static final int REQUEST_TRACKING_PERMISSIONS = 202;

    private SettingsRepository settingsRepository;
    private DataResetRepository dataResetRepository;
    private RunningSessionRepository runningSessionRepository;
    private AppSettings settings;

    private MaterialSwitch reminderSwitch;
    private TextView reminderTimeView;
    private TextView notificationsBlockedView;

    private MaterialSwitch autoStepsSwitch;
    private TextView autoStepsStatusView;
    private TextView autoStepsDetailView;
    private MaterialButton allowActivityButton;
    private MaterialButton allowNotificationsButton;

    /** True while a switch is being set programmatically, to ignore the callback. */
    private boolean bindingSwitch;
    private boolean trackingReceiverRegistered;

    private final BroadcastReceiver trackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateTrackingCard();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settingsRepository = new SettingsRepository(this);
        dataResetRepository = new DataResetRepository(this);
        runningSessionRepository = new RunningSessionRepository(this);

        reminderSwitch = findViewById(R.id.switchReminder);
        reminderTimeView = findViewById(R.id.reminderTimeValue);
        notificationsBlockedView = findViewById(R.id.notificationsBlocked);
        autoStepsSwitch = findViewById(R.id.switchAutoSteps);
        autoStepsStatusView = findViewById(R.id.autoStepsStatus);
        autoStepsDetailView = findViewById(R.id.autoStepsDetail);
        allowActivityButton = findViewById(R.id.btnAllowActivity);
        allowNotificationsButton = findViewById(R.id.btnAllowTrackingNotifications);

        TextView versionView = findViewById(R.id.settingsVersion);
        versionView.setText(getString(
                R.string.settings_version_format, BuildConfig.VERSION_NAME));

        findViewById(R.id.btnEditGoals).setOnClickListener(
                v -> startActivity(new Intent(this, GoalsActivity.class)));
        findViewById(R.id.btnChangeTime).setOnClickListener(v -> showTimePicker());
        findViewById(R.id.btnTestNotification).setOnClickListener(v -> sendTestNotification());
        findViewById(R.id.btnClearData).setOnClickListener(v -> confirmClearData());
        findViewById(R.id.btnBatterySettings).setOnClickListener(v -> openBatterySettings());

        reminderSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (bindingSwitch) {
                return;
            }
            saveReminder(isChecked, settings.getReminderHour(), settings.getReminderMinute());
        });

        autoStepsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (bindingSwitch) {
                return;
            }
            saveAutomaticTracking(isChecked);
        });
        allowActivityButton.setOnClickListener(v -> requestOrOpenActivityPermission());
        allowNotificationsButton.setOnClickListener(v -> openNotificationSettings());

        loadSettings();

        FloatingNavView.bind(this, FloatingNavView.DEST_SETTINGS);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(
                ActivityTrackingService.ACTION_TRACKING_STATUS_CHANGED);
        ContextCompat.registerReceiver(
                this, trackingReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        trackingReceiverRegistered = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSettings();
        ActivityTrackingService.refresh(this);
    }

    @Override
    protected void onStop() {
        if (trackingReceiverRegistered) {
            unregisterReceiver(trackingReceiver);
            trackingReceiverRegistered = false;
        }
        super.onStop();
    }

    private void loadSettings() {
        settings = settingsRepository.getSettings();
        bindingSwitch = true;
        reminderSwitch.setChecked(settings.isReminderEnabled());
        autoStepsSwitch.setChecked(settings.isAutoStepsEnabled());
        bindingSwitch = false;
        reminderTimeView.setText(
                FormatUtils.timeText(settings.getReminderHour(), settings.getReminderMinute()));
        updateNotificationsBlocked();
        updateTrackingCard();
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

    // ----- Automatic tracking -----

    private void saveAutomaticTracking(boolean enabled) {
        OperationResult result = settingsRepository.setAutoStepsEnabled(enabled);
        if (!result.isSuccess()) {
            Toast.makeText(this, R.string.settings_auto_save_failed, Toast.LENGTH_LONG).show();
            loadSettings();
            return;
        }
        settings.setAutoStepsEnabled(enabled);

        if (enabled) {
            if (ActivityTrackingService.hasStepCounter(this)) {
                requestTrackingPermissionsIfNeeded();
            }
            ActivityTrackingService.refresh(this);
            Toast.makeText(this, R.string.settings_auto_enabled, Toast.LENGTH_SHORT).show();
        } else {
            ActivityTrackingService.stop(this);
            Toast.makeText(this, R.string.settings_auto_disabled, Toast.LENGTH_SHORT).show();
        }
        updateTrackingCard();
    }

    private void requestTrackingPermissionsIfNeeded() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, missing.toArray(new String[0]), REQUEST_TRACKING_PERMISSIONS);
        }
    }

    private void requestOrOpenActivityPermission() {
        if (ActivityTrackingService.hasActivityPermission(this)) {
            updateTrackingCard();
            return;
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACTIVITY_RECOGNITION)) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                    REQUEST_TRACKING_PERMISSIONS);
        } else {
            openAppDetailsSettings();
        }
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        launchSystemSettings(intent);
    }

    private void openAppDetailsSettings() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        launchSystemSettings(intent);
    }

    private void openBatterySettings() {
        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        if (!launchSystemSettings(intent)) {
            launchSystemSettings(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    /**
     * Starts a system settings screen and returns false only when no activity can
     * handle it. Battery settings are informational; the app never requests an
     * exemption automatically.
     */
    private boolean launchSystemSettings(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(
                    this, R.string.settings_auto_settings_unavailable, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void updateTrackingCard() {
        TrackingMode mode = ActivityTrackingService.getEffectiveStatus(this);
        boolean notificationsEnabled =
                NotificationHelper.areTrackingNotificationsEnabled(this);
        int statusRes;
        int detailRes;

        switch (mode) {
            case ACTIVE:
                statusRes = R.string.tracking_status_active;
                detailRes = R.string.settings_auto_active_detail;
                break;
            case APP_OPEN_ONLY:
                statusRes = R.string.tracking_status_app_open_only;
                if (!notificationsEnabled) {
                    detailRes = R.string.settings_auto_app_open_notifications;
                } else if (ActivityTrackingService.getLastStartResult(this)
                        == TrackingStartResult.REJECTED) {
                    detailRes = R.string.settings_auto_app_open_fallback;
                } else {
                    detailRes = R.string.settings_auto_app_open_starting;
                }
                break;
            case UNAVAILABLE:
                statusRes = R.string.tracking_status_unavailable;
                if (!ActivityTrackingService.hasStepCounter(this)) {
                    detailRes = R.string.settings_auto_unavailable_sensor;
                } else if (!ActivityTrackingService.hasActivityPermission(this)) {
                    detailRes = R.string.settings_auto_unavailable_activity;
                } else {
                    detailRes = R.string.settings_auto_unavailable_service;
                }
                break;
            case OFF:
            default:
                statusRes = R.string.tracking_status_off;
                detailRes = R.string.settings_auto_off_detail;
                break;
        }

        String status = getString(statusRes);
        autoStepsStatusView.setText(status);
        autoStepsStatusView.setContentDescription(
                getString(R.string.tracking_status_description, status));
        autoStepsDetailView.setText(detailRes);

        boolean enabled = settings != null && settings.isAutoStepsEnabled();
        boolean sensorAvailable = ActivityTrackingService.hasStepCounter(this);
        allowActivityButton.setVisibility(
                enabled && sensorAvailable
                        && !ActivityTrackingService.hasActivityPermission(this)
                        ? View.VISIBLE : View.GONE);
        allowNotificationsButton.setVisibility(
                enabled && sensorAvailable && !notificationsEnabled
                        ? View.VISIBLE : View.GONE);
    }

    // ----- Clear data / permissions -----

    private void confirmClearData() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_clear_title)
                .setMessage(R.string.settings_clear_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.settings_clear_confirm, (dialog, which) -> clearAllData())
                .show();
    }

    private void clearAllData() {
        // Stop the single automatic tracker and discard any unfinished run before
        // the database transaction wipes their rows.
        ActivityTrackingService.stopForDataReset(this);
        runningSessionRepository.discard();

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
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ReminderScheduler.schedule(
                        this, settings.getReminderHour(), settings.getReminderMinute());
            } else {
                Toast.makeText(this, R.string.settings_notifications_blocked, Toast.LENGTH_LONG).show();
            }
            updateNotificationsBlocked();
            return;
        }
        if (requestCode != REQUEST_TRACKING_PERMISSIONS) {
            return;
        }

        boolean allGranted = grantResults.length > 0;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        ActivityTrackingService.refresh(this);
        updateTrackingCard();
        if (!allGranted) {
            Toast.makeText(this, R.string.settings_auto_permission_denied, Toast.LENGTH_LONG).show();
        }
    }
}
