package io.github.praneetx.motionfit.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.praneetx.motionfit.R;
import io.github.praneetx.motionfit.adapters.StepHistoryAdapter;
import io.github.praneetx.motionfit.logic.TrackingMode;
import io.github.praneetx.motionfit.models.DailySteps;
import io.github.praneetx.motionfit.repositories.SettingsRepository;
import io.github.praneetx.motionfit.repositories.StepsRepository;
import io.github.praneetx.motionfit.services.ActivityTrackingService;
import io.github.praneetx.motionfit.utils.DateUtils;
import io.github.praneetx.motionfit.utils.FormatUtils;
import io.github.praneetx.motionfit.utils.OperationResult;
import io.github.praneetx.motionfit.utils.ValidationUtils;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Manual daily step history and calibration.
 *
 * <p>The experimental one-shot sensor card is gone. Automatic readings have one
 * owner, {@link ActivityTrackingService}; this screen shows that service's
 * effective status and routes manual corrections through it so the in-memory
 * checkpoint cannot race the user's total.</p>
 */
public class StepsActivity extends AppCompatActivity implements StepHistoryAdapter.Listener {

    public static final String EXTRA_DATE = "extra_date";

    private StepsRepository stepsRepository;
    private SettingsRepository settingsRepository;
    private StepHistoryAdapter historyAdapter;

    private String selectedDate;

    private TextView selectedDateView;
    private TextView selectedValueView;
    private View editStepsButton;
    private TextView stepsEmptyView;
    private RecyclerView historyView;
    private TextView trackingStatusView;
    private TextView trackingDetailView;

    private boolean trackingReceiverRegistered;

    private final BroadcastReceiver trackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
            updateTrackingStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_steps);

        stepsRepository = new StepsRepository(this);
        settingsRepository = new SettingsRepository(this);

        String requestedDate = getIntent().getStringExtra(EXTRA_DATE);
        selectedDate = DateUtils.isValidIsoDate(requestedDate)
                ? requestedDate
                : DateUtils.todayIso();

        bindViews();
        setUpRecycler();
        setUpActions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ActivityTrackingService.ACTION_DAILY_STEPS_CHANGED);
        filter.addAction(ActivityTrackingService.ACTION_TRACKING_STATUS_CHANGED);
        ContextCompat.registerReceiver(
                this, trackingReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        trackingReceiverRegistered = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        ActivityTrackingService.refresh(this);
        refresh();
        updateTrackingStatus();
    }

    @Override
    protected void onStop() {
        if (trackingReceiverRegistered) {
            unregisterReceiver(trackingReceiver);
            trackingReceiverRegistered = false;
        }
        super.onStop();
    }

    private void bindViews() {
        selectedDateView = findViewById(R.id.stepsSelectedDate);
        selectedValueView = findViewById(R.id.stepsSelectedValue);
        editStepsButton = findViewById(R.id.btnEditSteps);
        stepsEmptyView = findViewById(R.id.stepsEmpty);
        historyView = findViewById(R.id.stepHistory);
        trackingStatusView = findViewById(R.id.stepsTrackingStatus);
        trackingDetailView = findViewById(R.id.stepsTrackingDetail);
    }

    private void setUpRecycler() {
        historyAdapter = new StepHistoryAdapter(this);
        historyView.setLayoutManager(new LinearLayoutManager(this));
        historyView.setAdapter(historyAdapter);
    }

    private void setUpActions() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnEditSteps).setOnClickListener(v -> showStepDialog());
        findViewById(R.id.btnManageTracking).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void refresh() {
        DailySteps record = stepsRepository.getForDate(selectedDate);
        int steps = record == null ? 0 : record.getSteps();
        String displayDate = DateUtils.formatForDisplay(selectedDate);
        selectedDateView.setText(displayDate);
        selectedDateView.setContentDescription(
                getString(R.string.dashboard_date_description, displayDate));
        selectedValueView.setText(FormatUtils.intText(steps));
        selectedValueView.setContentDescription(getString(
                R.string.steps_selected_value_description,
                FormatUtils.intText(steps), displayDate));
        editStepsButton.setContentDescription(
                getString(R.string.dashboard_edit_steps_on_date, displayDate));

        historyAdapter.setItems(stepsRepository.getAll());
        boolean isEmpty = historyAdapter.getItemCount() == 0;
        stepsEmptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        historyView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void updateTrackingStatus() {
        TrackingMode mode = ActivityTrackingService.getEffectiveStatus(this);
        int statusRes;
        int detailRes;
        switch (mode) {
            case ACTIVE:
                statusRes = R.string.tracking_status_active;
                detailRes = R.string.steps_tracking_active_detail;
                break;
            case APP_OPEN_ONLY:
                statusRes = R.string.tracking_status_app_open_only;
                detailRes = io.github.praneetx.motionfit.services.NotificationHelper
                        .areTrackingNotificationsEnabled(this)
                        ? R.string.steps_tracking_app_open_fallback
                        : R.string.steps_tracking_app_open_notifications;
                break;
            case UNAVAILABLE:
                statusRes = R.string.tracking_status_unavailable;
                detailRes = !ActivityTrackingService.hasStepCounter(this)
                        ? R.string.steps_tracking_unavailable_sensor
                        : R.string.steps_tracking_unavailable_permission;
                break;
            case OFF:
            default:
                statusRes = R.string.tracking_status_off;
                detailRes = R.string.steps_tracking_off_detail;
                break;
        }

        String status = getString(statusRes);
        trackingStatusView.setText(status);
        trackingStatusView.setContentDescription(
                getString(R.string.tracking_status_description, status));
        trackingDetailView.setText(detailRes);
    }

    // ----- Manual entry / calibration -----

    private void showStepDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_steps, null);
        TextView dateLabel = dialogView.findViewById(R.id.dialogDateLabel);
        TextView helper = dialogView.findViewById(R.id.dialogHelper);
        final TextInputEditText input = dialogView.findViewById(R.id.stepsInput);

        dateLabel.setText(DateUtils.formatForDisplay(selectedDate));
        helper.setText(settingsRepository.isAutoStepsEnabled()
                ? R.string.steps_dialog_helper_auto
                : R.string.steps_dialog_helper_manual);
        DailySteps existing = stepsRepository.getForDate(selectedDate);
        if (existing != null) {
            input.setText(String.valueOf(existing.getSteps()));
            input.setSelection(input.getText() == null ? 0 : input.getText().length());
        }

        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.steps_dialog_title)
                .setView(dialogView)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();
        dialog.show();

        // Validation runs on click so the dialog only closes when saving succeeds.
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String raw = input.getText() == null ? "" : input.getText().toString();
            ValidationUtils.ParsedInt parsed =
                    ValidationUtils.parseNonNegative(raw, ValidationUtils.MAX_STEPS);
            if (!parsed.isValid()) {
                input.setError(stepErrorMessage(parsed.error));
                return;
            }
            OperationResult result = ActivityTrackingService.calibrate(
                    this, selectedDate, parsed.value);
            if (result.isSuccess()) {
                dialog.dismiss();
                Toast.makeText(this, R.string.steps_saved, Toast.LENGTH_SHORT).show();
                refresh();
                updateTrackingStatus();
            } else {
                Toast.makeText(this, R.string.steps_save_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String stepErrorMessage(ValidationUtils.ValidationError error) {
        switch (error) {
            case EMPTY:
                return getString(R.string.steps_error_empty);
            case NEGATIVE:
                return getString(R.string.steps_error_negative);
            case TOO_LARGE:
                return getString(R.string.steps_error_too_large);
            case NOT_A_NUMBER:
            default:
                return getString(R.string.steps_error_not_number);
        }
    }

    // ----- StepHistoryAdapter.Listener -----

    @Override
    public void onStepRecordSelected(DailySteps record) {
        selectedDate = record.getActivityDate();
        refresh();
    }
}
