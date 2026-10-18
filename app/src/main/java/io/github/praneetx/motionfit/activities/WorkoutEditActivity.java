package io.github.praneetx.motionfit.activities;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import io.github.praneetx.motionfit.R;
import io.github.praneetx.motionfit.models.Workout;
import io.github.praneetx.motionfit.repositories.WorkoutRepository;
import io.github.praneetx.motionfit.utils.DateUtils;
import io.github.praneetx.motionfit.utils.OperationResult;
import io.github.praneetx.motionfit.utils.ValidationUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;

/**
 * Add or edit a single workout. The same screen handles "details" by showing an
 * existing record in full, so there is no separate screen to maintain.
 */
public class WorkoutEditActivity extends AppCompatActivity {

    public static final String EXTRA_WORKOUT_ID = "extra_workout_id";
    public static final String EXTRA_DATE = "extra_date";

    private static final int MAX_NOTES_LENGTH = 500;

    private WorkoutRepository workoutRepository;

    private long workoutId;
    private String selectedDate;
    private Workout existingWorkout;

    private TextView titleView;
    private Spinner typeSpinner;
    private MaterialButton dateButton;
    private TextInputEditText durationInput;
    private TextInputEditText notesInput;
    private MaterialButton deleteButton;
    private TextView metaCaptionView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workout_edit);

        workoutRepository = new WorkoutRepository(this);

        workoutId = getIntent().getLongExtra(EXTRA_WORKOUT_ID, 0L);
        String requestedDate = getIntent().getStringExtra(EXTRA_DATE);
        selectedDate = DateUtils.isValidIsoDate(requestedDate) ? requestedDate : DateUtils.todayIso();

        titleView = findViewById(R.id.editTitle);
        typeSpinner = findViewById(R.id.workoutTypeSpinner);
        dateButton = findViewById(R.id.btnPickDate);
        durationInput = findViewById(R.id.durationInput);
        notesInput = findViewById(R.id.notesInput);
        deleteButton = findViewById(R.id.btnDeleteWorkout);
        metaCaptionView = findViewById(R.id.editMetaCaption);

        setUpTypeSpinner();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        dateButton.setOnClickListener(v -> showDatePicker());
        findViewById(R.id.btnSaveWorkout).setOnClickListener(v -> save());
        deleteButton.setOnClickListener(v -> confirmDelete());

        if (workoutId > 0L) {
            loadExistingWorkout();
        } else {
            titleView.setText(R.string.title_add_workout);
            deleteButton.setVisibility(View.GONE);
            updateDateButton();
        }
    }

    private void setUpTypeSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, Workout.TYPES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(adapter);
    }

    private void loadExistingWorkout() {
        existingWorkout = workoutRepository.getById(workoutId);
        if (existingWorkout == null) {
            Toast.makeText(this, R.string.workout_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        titleView.setText(R.string.title_edit_workout);
        deleteButton.setVisibility(View.VISIBLE);

        selectedDate = existingWorkout.getWorkoutDate();
        selectType(existingWorkout.getWorkoutType());
        durationInput.setText(String.valueOf(existingWorkout.getDurationMinutes()));
        if (existingWorkout.getNotes() != null) {
            notesInput.setText(existingWorkout.getNotes());
        }
        updateDateButton();
        showMetaCaption();
    }

    /** Shows the "Added / Updated" caption for an existing workout (details view). */
    private void showMetaCaption() {
        if (metaCaptionView == null || existingWorkout == null) {
            return;
        }
        String created = formatTimestamp(existingWorkout.getCreatedAt());
        String caption = getString(R.string.workout_added, created);
        String updated = formatTimestamp(existingWorkout.getUpdatedAt());
        if (updated != null && !updated.equals(created)) {
            caption = caption + "   " + getString(R.string.workout_updated_at, updated);
        }
        metaCaptionView.setText(caption);
        metaCaptionView.setVisibility(View.VISIBLE);
    }

    /** Formats a stored YYYY-MM-DDTHH:mm:ss timestamp for display. */
    private String formatTimestamp(String iso) {
        if (iso == null || iso.trim().isEmpty()) {
            return "-";
        }
        try {
            return java.time.LocalDateTime.parse(iso, DateUtils.TIMESTAMP)
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
        } catch (RuntimeException e) {
            return iso;
        }
    }

    private void selectType(String type) {
        if (type == null) {
            return;
        }
        for (int i = 0; i < Workout.TYPES.length; i++) {
            if (Workout.TYPES[i].equals(type)) {
                typeSpinner.setSelection(i);
                return;
            }
        }
    }

    private void updateDateButton() {
        dateButton.setText(DateUtils.formatForDisplay(selectedDate));
    }

    private void showDatePicker() {
        LocalDate current = DateUtils.parseIsoDate(selectedDate);
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedDate = LocalDate.of(year, month + 1, dayOfMonth).format(DateUtils.ISO_DATE);
            updateDateButton();
        }, current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()).show();
    }

    private void save() {
        Object selectedType = typeSpinner.getSelectedItem();
        if (selectedType == null) {
            Toast.makeText(this, R.string.workout_type_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!DateUtils.isValidIsoDate(selectedDate)) {
            Toast.makeText(this, R.string.workout_date_invalid, Toast.LENGTH_SHORT).show();
            return;
        }

        String rawDuration = durationInput.getText() == null ? "" : durationInput.getText().toString();
        ValidationUtils.ParsedInt parsedDuration =
                ValidationUtils.parsePositive(rawDuration, ValidationUtils.MAX_MINUTES);
        if (!parsedDuration.isValid()) {
            durationInput.setError(getString(R.string.workout_duration_required));
            return;
        }

        String notes = notesInput.getText() == null ? null : notesInput.getText().toString().trim();
        if (notes != null && notes.length() > MAX_NOTES_LENGTH) {
            notesInput.setError(getString(R.string.workout_notes_too_long));
            return;
        }
        if (notes != null && notes.isEmpty()) {
            notes = null;
        }

        Workout workout = new Workout(
                selectedType.toString(), selectedDate, parsedDuration.value, notes);

        boolean isUpdate = existingWorkout != null;
        OperationResult result;
        if (isUpdate) {
            workout.setId(existingWorkout.getId());
            result = workoutRepository.update(workout);
        } else {
            result = workoutRepository.insert(workout);
        }

        if (result.isSuccess()) {
            Toast.makeText(this,
                    isUpdate ? R.string.workout_updated : R.string.workout_saved,
                    Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, result.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete() {
        if (existingWorkout == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.workout_delete_title)
                .setMessage(R.string.workout_delete_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    OperationResult result = workoutRepository.delete(existingWorkout.getId());
                    if (result.isSuccess()) {
                        Toast.makeText(this, R.string.workout_deleted, Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, result.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }
}
