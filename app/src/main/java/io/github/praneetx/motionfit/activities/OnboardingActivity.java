package io.github.praneetx.motionfit.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;

import io.github.praneetx.motionfit.R;
import io.github.praneetx.motionfit.models.FitnessGoal;
import io.github.praneetx.motionfit.repositories.GoalRepository;
import io.github.praneetx.motionfit.repositories.SettingsRepository;
import io.github.praneetx.motionfit.services.ActivityTrackingService;
import io.github.praneetx.motionfit.utils.OperationResult;
import io.github.praneetx.motionfit.utils.ValidationUtils;

/**
 * Short, skippable first-run setup.
 *
 * <p>The privacy and automatic-counting explanations are visible before any
 * permission request. Skip only records that onboarding was shown: existing
 * goals, workouts and daily totals are never changed. Finish validates and saves
 * the shown goals, then asks for the two optional runtime permissions only when
 * automatic counting was chosen.</p>
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final int REQUEST_TRACKING_PERMISSIONS = 4102;

    private GoalRepository goalRepository;
    private SettingsRepository settingsRepository;

    private TextInputEditText stepGoalInput;
    private TextInputEditText minutesGoalInput;
    private MaterialSwitch autoSwitch;

    private boolean waitingForPermissions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        goalRepository = new GoalRepository(this);
        settingsRepository = new SettingsRepository(this);

        stepGoalInput = findViewById(R.id.onboardingStepGoalInput);
        minutesGoalInput = findViewById(R.id.onboardingMinutesGoalInput);
        autoSwitch = findViewById(R.id.onboardingAutoSwitch);

        findViewById(R.id.btnFinishOnboarding).setOnClickListener(v -> finishOnboarding());
        findViewById(R.id.btnSkipOnboarding).setOnClickListener(v -> skipOnboarding());

        loadExistingValues();
    }

    private void loadExistingValues() {
        FitnessGoal goal = goalRepository.getGoal();
        stepGoalInput.setText(String.valueOf(goal.getDailyStepGoal()));
        minutesGoalInput.setText(String.valueOf(goal.getDailyWorkoutMinutesGoal()));
        autoSwitch.setChecked(settingsRepository.isAutoStepsEnabled());
    }

    private void finishOnboarding() {
        String rawSteps = textOf(stepGoalInput);
        ValidationUtils.ParsedInt steps =
                ValidationUtils.parsePositive(rawSteps, ValidationUtils.MAX_STEP_GOAL);
        if (!steps.isValid()) {
            stepGoalInput.setError(getString(R.string.goals_invalid));
            stepGoalInput.requestFocus();
            return;
        }

        String rawMinutes = textOf(minutesGoalInput);
        ValidationUtils.ParsedInt minutes =
                ValidationUtils.parsePositive(rawMinutes, ValidationUtils.MAX_MINUTES_GOAL);
        if (!minutes.isValid()) {
            minutesGoalInput.setError(getString(R.string.goals_invalid));
            minutesGoalInput.requestFocus();
            return;
        }

        OperationResult goalResult = goalRepository.saveGoal(steps.value, minutes.value);
        if (!goalResult.isSuccess()) {
            Toast.makeText(this, R.string.goals_save_failed, Toast.LENGTH_LONG).show();
            return;
        }

        boolean autoEnabled = autoSwitch.isChecked();
        OperationResult autoResult = settingsRepository.setAutoStepsEnabled(autoEnabled);
        if (!autoResult.isSuccess()) {
            Toast.makeText(this, R.string.settings_auto_save_failed, Toast.LENGTH_LONG).show();
            return;
        }

        settingsRepository.setOnboardingCompleted(true);
        if (autoEnabled) {
            ActivityTrackingService.refresh(this);
            requestMissingTrackingPermissions();
        } else {
            openMain();
        }
    }

    private void skipOnboarding() {
        settingsRepository.setOnboardingCompleted(true);
        openMain();
    }

    private void requestMissingTrackingPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            openMain();
            return;
        }

        ArrayList<String> missing = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (missing.isEmpty()) {
            openMain();
            return;
        }

        waitingForPermissions = true;
        requestPermissions(missing.toArray(new String[0]), REQUEST_TRACKING_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_TRACKING_PERMISSIONS || !waitingForPermissions) {
            return;
        }
        waitingForPermissions = false;
        // A denial is a supported state: the app opens with manual entry and the
        // Settings/Steps status cards explain the consequence.
        ActivityTrackingService.refresh(this);
        openMain();
    }

    private void openMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private static String textOf(TextInputEditText input) {
        if (input == null || input.getText() == null) {
            return "";
        }
        return input.getText().toString();
    }
}
