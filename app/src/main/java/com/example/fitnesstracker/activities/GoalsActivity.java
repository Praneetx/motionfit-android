package com.example.fitnesstracker.activities;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fitnesstracker.R;
import com.example.fitnesstracker.models.FitnessGoal;
import com.example.fitnesstracker.repositories.GoalRepository;
import com.example.fitnesstracker.utils.OperationResult;
import com.example.fitnesstracker.utils.ValidationUtils;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Lets the user change the daily step goal and the daily workout-minutes goal.
 */
public class GoalsActivity extends AppCompatActivity {

    private GoalRepository goalRepository;
    private TextInputEditText stepGoalInput;
    private TextInputEditText minutesGoalInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goals);

        goalRepository = new GoalRepository(this);
        stepGoalInput = findViewById(R.id.stepGoalInput);
        minutesGoalInput = findViewById(R.id.minutesGoalInput);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSaveGoals).setOnClickListener(v -> saveGoals());

        loadGoal();
    }

    private void loadGoal() {
        FitnessGoal goal = goalRepository.getGoal();
        stepGoalInput.setText(String.valueOf(goal.getDailyStepGoal()));
        minutesGoalInput.setText(String.valueOf(goal.getDailyWorkoutMinutesGoal()));
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
