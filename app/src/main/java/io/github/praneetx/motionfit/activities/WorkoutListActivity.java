package io.github.praneetx.motionfit.activities;

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

import io.github.praneetx.motionfit.R;
import io.github.praneetx.motionfit.adapters.WorkoutAdapter;
import io.github.praneetx.motionfit.models.Workout;
import io.github.praneetx.motionfit.repositories.WorkoutRepository;
import io.github.praneetx.motionfit.utils.DateUtils;
import io.github.praneetx.motionfit.utils.LoadResult;
import io.github.praneetx.motionfit.utils.OperationResult;
import io.github.praneetx.motionfit.views.FloatingNavView;
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
    private View emptyAddButton;
    private RecyclerView listView;
    private View listCard;
    private View errorState;

    /** null means "all types". */
    private String activeTypeFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workout_list);

        workoutRepository = new WorkoutRepository(this);

        typeFilter = findViewById(R.id.typeFilter);
        emptyView = findViewById(R.id.workoutsEmpty);
        emptyAddButton = findViewById(R.id.btnEmptyAddWorkout);
        listView = findViewById(R.id.workoutList);
        listCard = findViewById(R.id.workoutListCard);
        errorState = findViewById(R.id.workoutsErrorState);

        adapter = new WorkoutAdapter(this);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.setAdapter(adapter);

        setUpTypeFilter();

        findViewById(R.id.btnAddWorkout).setOnClickListener(v -> openEditor(0L));
        findViewById(R.id.btnEmptyAddWorkout).setOnClickListener(v -> openEditor(0L));
        findViewById(R.id.btnRetryWorkouts).setOnClickListener(v -> loadWorkouts());

        FloatingNavView.bind(this, FloatingNavView.DEST_WORKOUTS);
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
        LoadResult<List<Workout>> result =
                workoutRepository.loadWorkoutsResult(activeTypeFilter, null, 0);
        if (!result.isSuccess()) {
            adapter.setItems(new ArrayList<>());
            errorState.setVisibility(View.VISIBLE);
            listCard.setVisibility(View.GONE);
            return;
        }

        errorState.setVisibility(View.GONE);
        listCard.setVisibility(View.VISIBLE);

        List<Workout> workouts = result.getData();
        adapter.setItems(workouts);
        boolean isEmpty = workouts == null || workouts.isEmpty();
        emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        emptyAddButton.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
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
