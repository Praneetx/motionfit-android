package io.github.praneetx.motionfit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.activities.WorkoutEditActivity;
import io.github.praneetx.motionfit.activities.WorkoutListActivity;
import io.github.praneetx.motionfit.models.Workout;
import io.github.praneetx.motionfit.repositories.DataResetRepository;
import io.github.praneetx.motionfit.repositories.WorkoutRepository;
import io.github.praneetx.motionfit.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Task 15 checks exact Running metadata without changing manual workout display.
 */
@RunWith(AndroidJUnit4.class)
public class WorkoutRunningMetadataTest {

    private Context context;
    private WorkoutRepository workoutRepository;

    @Before
    public void resetData() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new DataResetRepository(context).clearAllData().isSuccess());
        workoutRepository = new WorkoutRepository(context);
    }

    @Test
    public void workoutRowsShowExactRunningDurationAndSteps() {
        String date = DateUtils.todayIso();
        Workout manual = new Workout("Yoga", date, 30, null);
        Workout running = new Workout("Running", date, 3, null);
        running.setDurationSeconds(125);
        running.setStepCount(1234);
        assertTrue(workoutRepository.insert(manual).isSuccess());
        assertTrue(workoutRepository.insert(running).isSuccess());

        String displayDate = DateUtils.formatForDisplay(date);
        String manualMeta = context.getString(
                R.string.workout_row_meta,
                displayDate,
                context.getString(R.string.workout_duration_value, 30));
        String runningMeta = context.getString(
                R.string.workout_row_meta_running,
                displayDate,
                "02:05",
                "1,234");

        try (ActivityScenario<WorkoutListActivity> scenario =
                     ActivityScenario.launch(WorkoutListActivity.class)) {
            onView(withText(manualMeta)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText(runningMeta)).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }

    @Test
    public void detailsShowReadOnlyRunMetrics() {
        Workout running = insertRunningWorkout();

        try (ActivityScenario<WorkoutEditActivity> scenario =
                     ActivityScenario.launch(editIntent(running.getId()))) {
            onView(withId(R.id.recordedRunCard))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
            onView(withId(R.id.recordedDurationValue))
                    .check(matches(withText("02:05")));
            onView(withId(R.id.recordedStepsValue))
                    .check(matches(withText("1,234")));
        }
    }

    @Test
    public void editingOtherFieldsPreservesExactRunMetrics() {
        Workout running = insertRunningWorkout();
        long id = running.getId();

        try (ActivityScenario<WorkoutEditActivity> scenario =
                     ActivityScenario.launch(editIntent(id))) {
            scenario.onActivity(activity -> {
                TextView notes = activity.findViewById(R.id.notesInput);
                notes.setText("Evening run");
                activity.findViewById(R.id.btnSaveWorkout).performClick();
            });
        }

        Workout saved = workoutRepository.getById(id);
        assertNotNull(saved);
        assertEquals(Integer.valueOf(125), saved.getDurationSeconds());
        assertEquals(Integer.valueOf(1234), saved.getStepCount());
        assertEquals("Evening run", saved.getNotes());
    }

    @Test
    public void changingWholeMinutesResetsSecondsButPreservesSteps() {
        Workout running = insertRunningWorkout();
        long id = running.getId();

        try (ActivityScenario<WorkoutEditActivity> scenario =
                     ActivityScenario.launch(editIntent(id))) {
            scenario.onActivity(activity -> {
                TextView duration = activity.findViewById(R.id.durationInput);
                duration.setText("4");
                activity.findViewById(R.id.btnSaveWorkout).performClick();
            });
        }

        Workout saved = workoutRepository.getById(id);
        assertNotNull(saved);
        assertEquals(4, saved.getDurationMinutes());
        assertEquals("A manual whole-minute change becomes the new exact duration",
                Integer.valueOf(240), saved.getDurationSeconds());
        assertEquals("Steps stay read-only until an explicit edit feature exists",
                Integer.valueOf(1234), saved.getStepCount());
    }

    @Test
    public void manualWorkoutDetailsDoNotShowRunMetrics() {
        Workout manual = new Workout("Yoga", DateUtils.todayIso(), 30, null);
        assertTrue(workoutRepository.insert(manual).isSuccess());

        try (ActivityScenario<WorkoutEditActivity> scenario =
                     ActivityScenario.launch(editIntent(manual.getId()))) {
            onView(withId(R.id.recordedRunCard))
                    .check(matches(withEffectiveVisibility(GONE)));
        }
    }

    private Workout insertRunningWorkout() {
        Workout running = new Workout("Running", DateUtils.todayIso(), 3, null);
        running.setDurationSeconds(125);
        running.setStepCount(1234);
        assertTrue(workoutRepository.insert(running).isSuccess());
        Workout saved = workoutRepository.getById(running.getId());
        assertNotNull(saved);
        return saved;
    }

    private Intent editIntent(long workoutId) {
        return new Intent(context, WorkoutEditActivity.class)
                .putExtra(WorkoutEditActivity.EXTRA_WORKOUT_ID, workoutId);
    }
}
