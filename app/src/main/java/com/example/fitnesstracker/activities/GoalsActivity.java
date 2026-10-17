package com.example.fitnesstracker.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fitnesstracker.R;
import com.example.fitnesstracker.logic.GoalProgressCalculator;
import com.example.fitnesstracker.models.DaySummary;
import com.example.fitnesstracker.models.FitnessGoal;
import com.example.fitnesstracker.repositories.GoalRepository;
import com.example.fitnesstracker.repositories.SummaryRepository;
import com.example.fitnesstracker.utils.DateUtils;
import com.example.fitnesstracker.utils.OperationResult;
import com.example.fitnesstracker.utils.ValidationUtils;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Goals screen (Wise restyle).
 *
 * <p>One white card per goal: a large heavy value, circular -/+ steppers, a
 * progress bar for today's real percentage and the caption explaining that past
 * days keep their records.</p>
 *
 * <p>The steppers only change the text fields, so the existing validation and
 * {@link GoalRepository} save path are untouched. Today's totals are read with
 * the read-only {@link SummaryRepository}.</p>
 */
public class GoalsActivity extends AppCompatActivity {

    private static final int STEP_INCREMENT = 500;
    private static final int MINUTE_INCREMENT = 5;

    private GoalRepository goalRepository;
    private SummaryRepository summaryRepository;

    private TextInputEditText stepGoalInput;
    private TextInputEditText minutesGoalInput;
    private TextView stepGoalPercent;
    private TextView minuteGoalPercent;
    private android.widget.ProgressBar stepGoalProgress;
    private android.widget.ProgressBar minuteGoalProgress;

    private DaySummary today;
    private boolean bindingInputs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goals);

        goalRepository = new GoalRepository(this);
        summaryRepository = new SummaryRepository(this);
        today = summaryRepository.getDaySummary(DateUtils.todayIso());

        stepGoalInput = findViewById(R.id.stepGoalInput);
        minutesGoalInput = findViewById(R.id.minutesGoalInput);
        stepGoalPercent = findViewById(R.id.stepGoalPercent);
        minuteGoalPercent = findViewById(R.id.minuteGoalPercent);
        stepGoalProgress = findViewById(R.id.stepGoalProgress);
        minuteGoalProgress = findViewById(R.id.minuteGoalProgress);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSaveGoals).setOnClickListener(v -> saveGoals());

        findViewById(R.id.stepGoalMinus).setOnClickListener(
                v -> adjust(stepGoalInput, -STEP_INCREMENT, ValidationUtils.MAX_STEP_GOAL));
        findViewById(R.id.stepGoalPlus).setOnClickListener(
                v -> adjust(stepGoalInput, STEP_INCREMENT, ValidationUtils.MAX_STEP_GOAL));
        findViewById(R.id.minuteGoalMinus).setOnClickListener(
                v -> adjust(minutesGoalInput, -MINUTE_INCREMENT, ValidationUtils.MAX_MINUTES_GOAL));
        findViewById(R.id.minuteGoalPlus).setOnClickListener(
                v -> adjust(minutesGoalInput, MINUTE_INCREMENT, ValidationUtils.MAX_MINUTES_GOAL));

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not needed.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Not needed.
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!bindingInputs) {
                    refreshProgress();
                }
            }
        };
        stepGoalInput.addTextChangedListener(watcher);
        minutesGoalInput.addTextChangedListener(watcher);

        loadGoal();
    }

    private void loadGoal() {
        FitnessGoal goal = goalRepository.getGoal();
        bindingInputs = true;
        stepGoalInput.setText(String.valueOf(goal.getDailyStepGoal()));
        minutesGoalInput.setText(String.valueOf(goal.getDailyWorkoutMinutesGoal()));
        bindingInputs = false;
        refreshProgress();
    }

    /** Increments or decrements a goal field, clamped to a sane range. */
    private void adjust(TextInputEditText input, int delta, int max) {
        int next = Math.max(1, Math.min(max, readValue(input) + delta));
        input.setText(String.valueOf(next));
        input.setSelection(input.getText() == null ? 0 : input.getText().length());
    }

    private int readValue(TextInputEditText input) {
        if (input == null || input.getText() == null) {
            return 0;
        }
        try {
            return Integer.parseInt(input.getText().toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Shows today's real percentage for each goal. The bar is clamped to 100%,
     * but the printed percentage may exceed it (goal exceeded).
     */
    private void refreshProgress() {
        int stepGoal = readValue(stepGoalInput);
        int minuteGoal = readValue(minutesGoalInput);

        stepGoalProgress.setProgress(
                GoalProgressCalculator.clampedPercent(today.steps, stepGoal));
        stepGoalPercent.setText(getString(R.string.goal_today_percent,
                GoalProgressCalculator.formatPercent(today.steps, stepGoal)));

        minuteGoalProgress.setProgress(
                GoalProgressCalculator.clampedPercent(today.workoutMinutes, minuteGoal));
        minuteGoalPercent.setText(getString(R.string.goal_today_percent,
                GoalProgressCalculator.formatPercent(today.workoutMinutes, minuteGoal)));
    }

    private void saveGoals() {
        String rawSteps = stepGoalInput.getText() == null ? "" : stepGoalInput.getText().toString();
        String rawMinutes = minutesGoalInput.getText() == null ? "" : minutesGoalInput.getText().toString();

        ValidationUtils.ParsedInt steps =
                ValidationUtils.parsePositive(rawSteps, ValidationUtils.MAX_STEP_GOAL);
        if (!steps.isValid()) {
            stepGoalInput.setError(getString(R.string.goals_invalid));
            return;
        }

        ValidationUtils.ParsedInt minutes =
                ValidationUtils.parsePositive(rawMinutes, ValidationUtils.MAX_MINUTES_GOAL);
        if (!minutes.isValid()) {
            minutesGoalInput.setError(getString(R.string.goals_invalid));
            return;
        }

        OperationResult result = goalRepository.saveGoal(steps.value, minutes.value);
        if (result.isSuccess()) {
            Toast.makeText(this, R.string.goals_saved, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, result.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
