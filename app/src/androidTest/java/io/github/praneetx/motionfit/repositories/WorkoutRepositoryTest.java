package io.github.praneetx.motionfit.repositories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.models.Workout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented tests for the optional exact Running fields on a workout.
 */
@RunWith(AndroidJUnit4.class)
public class WorkoutRepositoryTest {

    private static final String DATE = "2031-07-10";

    private WorkoutRepository repository;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new DataResetRepository(context).clearAllData().isSuccess());
        repository = new WorkoutRepository(context);
    }

    @Test
    public void exactDurationAndStepsRoundTrip() {
        Workout workout = new Workout("Running", DATE, 1, "interval run");
        workout.setDurationSeconds(65);
        workout.setStepCount(123);

        assertTrue(repository.insert(workout).isSuccess());
        assertTrue(workout.getId() > 0);

        Workout stored = repository.getById(workout.getId());
        assertNotNull(stored);
        assertEquals(Integer.valueOf(65), stored.getDurationSeconds());
        assertEquals(Integer.valueOf(123), stored.getStepCount());
        assertEquals(1, stored.getDurationMinutes());
    }

    @Test
    public void manualWorkoutLeavesExactFieldsNull() {
        Workout workout = new Workout("Yoga", DATE, 30, null);

        assertTrue(repository.insert(workout).isSuccess());

        Workout stored = repository.getById(workout.getId());
        assertNotNull(stored);
        assertNull(stored.getDurationSeconds());
        assertNull(stored.getStepCount());
    }

    @Test
    public void updatePreservesExactFieldsCarriedByTheModel() {
        Workout workout = new Workout("Running", DATE, 1, "first note");
        workout.setDurationSeconds(65);
        workout.setStepCount(123);
        assertTrue(repository.insert(workout).isSuccess());

        Workout stored = repository.getById(workout.getId());
        assertNotNull(stored);
        stored.setDurationMinutes(2);
        stored.setNotes("updated note");

        assertTrue(repository.update(stored).isSuccess());

        Workout updated = repository.getById(workout.getId());
        assertNotNull(updated);
        assertEquals(2, updated.getDurationMinutes());
        assertEquals("updated note", updated.getNotes());
        assertEquals(Integer.valueOf(65), updated.getDurationSeconds());
        assertEquals(Integer.valueOf(123), updated.getStepCount());
    }

    @Test
    public void negativeExactDurationIsRejected() {
        Workout workout = new Workout("Running", DATE, 1, null);
        workout.setDurationSeconds(-1);

        assertFalse(repository.insert(workout).isSuccess());
        assertTrue(repository.getAll().isEmpty());
    }

    @Test
    public void negativeStepCountIsRejected() {
        Workout workout = new Workout("Running", DATE, 1, null);
        workout.setStepCount(-1);

        assertFalse(repository.insert(workout).isSuccess());
        assertTrue(repository.getAll().isEmpty());
    }
}
