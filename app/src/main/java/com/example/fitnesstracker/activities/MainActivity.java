package com.example.fitnesstracker.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitnesstracker.R;
import com.example.fitnesstracker.adapters.WorkoutAdapter;
import com.example.fitnesstracker.logic.GoalProgressCalculator;
import com.example.fitnesstracker.models.DashboardData;
import com.example.fitnesstracker.models.Workout;
import com.example.fitnesstracker.repositories.DashboardRepository;
import com.example.fitnesstracker.repositories.WorkoutRepository;
import com.example.fitnesstracker.utils.DateUtils;
import com.example.fitnesstracker.utils.FormatUtils;
import com.example.fitnesstracker.utils.OperationResult;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.util.List;

/**
 * Dashboard: shows the selected day's steps, workout totals, goal progress and
 * the most recent workouts, all read from SQLite.
 */
public class MainActivity extends AppCompatActivity implements WorkoutAdapter.Listener {

    private DashboardRepository dashboardRepository;
    private WorkoutRepository workoutRepository;
    private WorkoutAdapter workoutAdapter;

    /** Selected calendar day in YYYY-MM-DD form. */
    private String selectedDate;

    private TextView dateView;
    private TextView stepsValue;
    private TextView stepsGoalView;
    private ProgressBar stepsProgress;
    private TextView stepsPercentView;
    private TextView workoutCountValue;
    private TextView workoutMinutesValue;
    private TextView workoutGoalView;
    private ProgressBar workoutProgress;
    private TextView workoutPercentView;
    private TextView recentEmpty;
    private RecyclerView recentWorkouts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dashboardRepository = new DashboardRepository(this);
        workoutRepository = new WorkoutRepository(this);
        selectedDate = DateUtils.todayIso();

        bindViews();
        setUpRecycler();
        setUpActions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-read from SQLite every time the screen becomes visible, so returning
        // from another screen always shows fresh numbers.
        loadDashboard();
    }

    private void bindViews() {
        dateView = findViewById(R.id.dashboardDate);
        stepsValue = findViewById(R.id.stepsValue);
        stepsGoalView = findViewById(R.id.stepsGoal);
        stepsProgress = findViewById(R.id.stepsProgress);
        stepsPercentView = findViewById(R.id.stepsPercent);
        workoutCountValue = findViewById(R.id.workoutCountValue);
        workoutMinutesValue = findViewById(R.id.workoutMinutesValue);
        workoutGoalView = findViewById(R.id.workoutGoal);
        workoutProgress = findViewById(R.id.workoutProgress);
        workoutPercentView = findViewById(R.id.workoutPercent);
        recentEmpty = findViewById(R.id.recentEmpty);
        recentWorkouts = findViewById(R.id.recentWorkouts);
    }

    private void setUpRecycler() {
        workoutAdapter = new WorkoutAdapter(this);
        recentWorkouts.setLayoutManager(new LinearLayoutManager(this));
        recentWorkouts.setAdapter(workoutAdapter);
    }

    private void setUpActions() {
        ImageButton settings = findViewById(R.id.btnSettings);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        findViewById(R.id.btnPrevDay).setOnClickListener(v -> shiftSelectedDate(-1));
        findViewById(R.id.btnNextDay).setOnClickListener(v -> shiftSelectedDate(1));

        findViewById(R.id.btnLogSteps).setOnClickListener(v -> openSteps());
        findViewById(R.id.btnAddWorkout).setOnClickListener(v -> openWorkoutEditor(0L));
        findViewById(R.id.btnGoals).setOnClickListener(
                v -> startActivity(new Intent(this, GoalsActivity.class)));
        findViewById(R.id.btnProgress).setOnClickListener(
                v -> startActivity(new Intent(this, SummaryActivity.class)));
    }

    private void shiftSelectedDate(int days) {
        LocalDate date = DateUtils.parseIsoDate(selectedDate).plusDays(days);
        selectedDate = date.format(DateUtils.ISO_DATE);
        loadDashboard();
    }

    private void loadDashboard() {
        DashboardData data = dashboardRepository.load(selectedDate);
        if (data == null) {
            Toast.makeText(this, R.string.dashboard_error, Toast.LENGTH_LONG).show();
            return;
        }

        dateView.setText(DateUtils.formatForDisplay(data.dateIso));

        // Steps card
        stepsValue.setText(FormatUtils.intText(data.steps));
        stepsGoalView.setText(getString(R.string.dashboard_goal_of_steps,
                FormatUtils.intText(data.stepGoal)));
        stepsProgress.setProgress(GoalProgressCalculator.clampedPercent(data.steps, data.stepGoal));
        stepsPercentView.setText(GoalProgressCalculator.formatPercent(data.steps, data.stepGoal));

        // Workouts card
        workoutCountValue.setText(FormatUtils.intText(data.workoutCount));
        workoutMinutesValue.setText(FormatUtils.intText(data.workoutMinutes));
        workoutGoalView.setText(getString(R.string.dashboard_goal_of_minutes,
                FormatUtils.intText(data.workoutMinutesGoal)));
        workoutProgress.setProgress(
                GoalProgressCalculator.clampedPercent(data.workoutMinutes, data.workoutMinutesGoal));
        workoutPercentView.setText(
                GoalProgressCalculator.formatPercent(data.workoutMinutes, data.workoutMinutesGoal));

        // Recent workouts
        List<Workout> recent = data.recentWorkouts;
        workoutAdapter.setItems(recent);
        boolean isEmpty = recent.isEmpty();
        recentEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recentWorkouts.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void openSteps() {
        Intent intent = new Intent(this, StepsActivity.class);
        intent.putExtra(StepsActivity.EXTRA_DATE, selectedDate);
        startActivity(intent);
    }

    private void openWorkoutEditor(long workoutId) {
        Intent intent = new Intent(this, WorkoutEditActivity.class);
        if (workoutId > 0L) {
            intent.putExtra(WorkoutEditActivity.EXTRA_WORKOUT_ID, workoutId);
        } else {
            intent.putExtra(WorkoutEditActivity.EXTRA_DATE, selectedDate);
        }
        startActivity(intent);
    }

    // ----- WorkoutAdapter.Listener -----

    @Override
    public void onWorkoutSelected(Workout workout) {
        openWorkoutEditor(workout.getId());
    }

    @Override
    public void onWorkoutEdit(Workout workout) {
        openWorkoutEditor(workout.getId());
    }

    @Override
    public void onWorkoutDelete(Workout workout) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.workout_delete_title)
                .setMessage(R.string.workout_delete_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    OperationResult result = workoutRepository.delete(workout.getId());
                    if (result.isSuccess()) {
                        Toast.makeText(this, R.string.workout_deleted, Toast.LENGTH_SHORT).show();
                        loadDashboard();
                    } else {
                        Toast.makeText(this, result.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }
}
