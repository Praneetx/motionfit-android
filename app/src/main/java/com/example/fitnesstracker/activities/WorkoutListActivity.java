package com.example.fitnesstracker.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitnesstracker.R;
import com.example.fitnesstracker.adapters.WorkoutAdapter;
import com.example.fitnesstracker.models.Workout;
import com.example.fitnesstracker.repositories.WorkoutRepository;
import com.example.fitnesstracker.utils.DateUtils;
import com.example.fitnesstracker.utils.OperationResult;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Workout history: a RecyclerView of every workout with an optional type filter,
 * plus entry points to add, edit and delete records.
 */
public class WorkoutListActivity extends AppCompatActivity implements WorkoutAdapter.Listener {

    private WorkoutRepository workoutRepository;
    private WorkoutAdapter adapter;
    private Spinner typeFilter;
    private TextView emptyView;
    private RecyclerView listView;

    /** null means "all types". */
    private String activeTypeFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workout_list);

        workoutRepository = new WorkoutRepository(this);

        typeFilter = findViewById(R.id.typeFilter);
        emptyView = findViewById(R.id.workoutsEmpty);
        listView = findViewById(R.id.workoutList);

        adapter = new WorkoutAdapter(this);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.setAdapter(adapter);

        setUpTypeFilter();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddWorkout).setOnClickListener(v -> openEditor(0L));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadWorkouts();
    }

    private void setUpTypeFilter() {
        List<String> entries = new ArrayList<>();
        entries.add(getString(R.string.workouts_filter_all));
        for (String type : Workout.TYPES) {
            entries.add(type);
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, entries);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeFilter.setAdapter(spinnerAdapter);

        typeFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                activeTypeFilter = position == 0 ? null : entries.get(position);
                loadWorkouts();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Keep the current filter.
            }
        });
    }

    private void loadWorkouts() {
        List<Workout> workouts = workoutRepository.getWorkouts(activeTypeFilter, null, 0);
        adapter.setItems(workouts);
        boolean isEmpty = workouts.isEmpty();
        emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        listView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void openEditor(long workoutId) {
        Intent intent = new Intent(this, WorkoutEditActivity.class);
        if (workoutId > 0L) {
            intent.putExtra(WorkoutEditActivity.EXTRA_WORKOUT_ID, workoutId);
        } else {
            intent.putExtra(WorkoutEditActivity.EXTRA_DATE, DateUtils.todayIso());
        }
        startActivity(intent);
    }

    // ----- WorkoutAdapter.Listener -----

    @Override
    public void onWorkoutSelected(Workout workout) {
        openEditor(workout.getId());
    }

    @Override
    public void onWorkoutEdit(Workout workout) {
        openEditor(workout.getId());
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
                        loadWorkouts();
                    } else {
                        Toast.makeText(this, result.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }
}
