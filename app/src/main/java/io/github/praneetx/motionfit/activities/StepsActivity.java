package io.github.praneetx.motionfit.activities;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.praneetx.motionfit.R;
import io.github.praneetx.motionfit.adapters.StepHistoryAdapter;
import io.github.praneetx.motionfit.models.DailySteps;
import io.github.praneetx.motionfit.repositories.StepsRepository;
import io.github.praneetx.motionfit.services.StepCounterSensorHelper;
import io.github.praneetx.motionfit.utils.DateUtils;
import io.github.praneetx.motionfit.utils.FormatUtils;
import io.github.praneetx.motionfit.utils.OperationResult;
import io.github.praneetx.motionfit.utils.ValidationUtils;
import io.github.praneetx.motionfit.views.FloatingNavView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Manual daily step tracking plus the optional (EXPERIMENTAL) device sensor.
 * Manual entry is always available and never depends on the sensor.
 */
public class StepsActivity extends AppCompatActivity implements StepHistoryAdapter.Listener {

    public static final String EXTRA_DATE = "extra_date";

    private static final int REQUEST_ACTIVITY_RECOGNITION = 101;

    private StepsRepository stepsRepository;
    private StepHistoryAdapter historyAdapter;
    private StepCounterSensorHelper sensorHelper;

    private String selectedDate;
    private boolean sensorRunning;
    private int sessionSteps;

    private TextView selectedDateView;
    private TextView selectedValueView;
    private TextView stepsEmptyView;
    private RecyclerView historyView;
    private TextView sensorStatusView;
    private TextView sensorReadingView;
    private MaterialButton toggleSensorButton;
    private MaterialButton saveSensorButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_steps);

        stepsRepository = new StepsRepository(this);

        String requestedDate = getIntent().getStringExtra(EXTRA_DATE);
        selectedDate = DateUtils.isValidIsoDate(requestedDate) ? requestedDate : DateUtils.todayIso();

        bindViews();
        setUpRecycler();
        setUpSensor();
        setUpActions();

        FloatingNavView.bind(this, FloatingNavView.DEST_STEPS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Never keep a sensor listener alive while the screen is not visible.
        stopSensor();
    }

    private void bindViews() {
        selectedDateView = findViewById(R.id.stepsSelectedDate);
        selectedValueView = findViewById(R.id.stepsSelectedValue);
        stepsEmptyView = findViewById(R.id.stepsEmpty);
        historyView = findViewById(R.id.stepHistory);
        sensorStatusView = findViewById(R.id.sensorStatus);
        sensorReadingView = findViewById(R.id.sensorReading);
        toggleSensorButton = findViewById(R.id.btnToggleSensor);
        saveSensorButton = findViewById(R.id.btnSaveSensorSteps);
    }

    private void setUpRecycler() {
        historyAdapter = new StepHistoryAdapter(this);
        historyView.setLayoutManager(new LinearLayoutManager(this));
        historyView.setAdapter(historyAdapter);
    }

    private void setUpActions() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnEditSteps).setOnClickListener(v -> showStepDialog());
        toggleSensorButton.setOnClickListener(v -> toggleSensor());
        saveSensorButton.setOnClickListener(v -> saveSensorSteps());
    }

    private void setUpSensor() {
        sensorHelper = new StepCounterSensorHelper(this);
        if (!sensorHelper.isAvailable()) {
            sensorStatusView.setText(R.string.sensor_unavailable);
            toggleSensorButton.setVisibility(View.GONE);
            saveSensorButton.setVisibility(View.GONE);
        } else {
            sensorStatusView.setText(R.string.sensor_note);
        }
    }

    private void refresh() {
        DailySteps record = stepsRepository.getForDate(selectedDate);
        int steps = record == null ? 0 : record.getSteps();
        selectedDateView.setText(DateUtils.formatForDisplay(selectedDate));
        selectedValueView.setText(FormatUtils.intText(steps));

        historyAdapter.setItems(stepsRepository.getAll());
        boolean isEmpty = historyAdapter.getItemCount() == 0;
        stepsEmptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        historyView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    // ----- Manual entry -----

    private void showStepDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_steps, null);
        TextView dateLabel = dialogView.findViewById(R.id.dialogDateLabel);
        final TextInputEditText input = dialogView.findViewById(R.id.stepsInput);

        dateLabel.setText(DateUtils.formatForDisplay(selectedDate));
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
            OperationResult result = stepsRepository.saveDailySteps(
                    selectedDate, parsed.value, DailySteps.SOURCE_MANUAL);
            if (result.isSuccess()) {
                dialog.dismiss();
                Toast.makeText(this, R.string.steps_saved, Toast.LENGTH_SHORT).show();
                refresh();
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

    // ----- Optional sensor (EXPERIMENTAL) -----

    private void toggleSensor() {
        if (sensorRunning) {
            stopSensor();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                    REQUEST_ACTIVITY_RECOGNITION);
            return;
        }
        startSensor();
    }

    private void startSensor() {
        sessionSteps = 0;
        saveSensorButton.setVisibility(View.GONE);
        sensorReadingView.setVisibility(View.GONE);

        sensorHelper.start(new StepCounterSensorHelper.Listener() {
            @Override
            public void onSessionSteps(int steps) {
                sessionSteps = steps;
                sensorReadingView.setVisibility(View.VISIBLE);
                sensorReadingView.setText(getString(R.string.sensor_reading, FormatUtils.intText(steps)));
                if (steps > 0) {
                    saveSensorButton.setVisibility(View.VISIBLE);
                    saveSensorButton.setText(getString(R.string.sensor_add_steps, FormatUtils.intText(steps)));
                }
            }

            @Override
            public void onSensorUnavailable(String reason) {
                sensorStatusView.setText(R.string.sensor_unavailable);
                toggleSensorButton.setVisibility(View.GONE);
                sensorRunning = false;
            }
        });

        sensorRunning = true;
        toggleSensorButton.setText(R.string.sensor_stop);
        sensorStatusView.setText(R.string.sensor_note);
    }

    private void stopSensor() {
        if (sensorHelper != null) {
            sensorHelper.stop();
        }
        sensorRunning = false;
        if (toggleSensorButton != null && sensorHelper != null && sensorHelper.isAvailable()) {
            toggleSensorButton.setText(R.string.sensor_start);
        }
    }

    private void saveSensorSteps() {
        if (sessionSteps <= 0) {
            return;
        }
        DailySteps existing = stepsRepository.getForDate(selectedDate);
        int baseSteps = existing == null ? 0 : existing.getSteps();
        OperationResult result = stepsRepository.saveDailySteps(
                selectedDate, baseSteps + sessionSteps, DailySteps.SOURCE_SENSOR);
        if (!result.isSuccess()) {
            Toast.makeText(this, result.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, R.string.steps_saved, Toast.LENGTH_SHORT).show();
        // One-shot action: stop and hide so the same reading cannot be added twice.
        stopSensor();
        saveSensorButton.setVisibility(View.GONE);
        sensorReadingView.setVisibility(View.GONE);
        sessionSteps = 0;
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_ACTIVITY_RECOGNITION) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startSensor();
        } else {
            Toast.makeText(this, R.string.sensor_permission_denied, Toast.LENGTH_LONG).show();
        }
    }

    // ----- StepHistoryAdapter.Listener -----

    @Override
    public void onStepRecordSelected(DailySteps record) {
        selectedDate = record.getActivityDate();
        refresh();
    }
}
