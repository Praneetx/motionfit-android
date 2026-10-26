package io.github.praneetx.motionfit.repositories;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.models.Workout;
import io.github.praneetx.motionfit.utils.LoadResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Task 8: the Workouts screen must see a failed read as an error, not as an
 * empty history. The legacy list API keeps returning an empty list.
 */
@RunWith(AndroidJUnit4.class)
public class WorkoutRepositoryResultTest {

    private Context context;

    @Before
    public void resetData() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new DataResetRepository(context).clearAllData().isSuccess());
    }

    @Test
    public void emptyHistoryIsASuccessfulEmptyList() {
        LoadResult<List<Workout>> result =
                new WorkoutRepository(context).loadWorkoutsResult(null, null, 0);

        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
        assertNotNull(result.getData());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    public void readFailureIsAnErrorAndLegacyReadStillReturnsEmpty() {
        WorkoutRepository failing = new WorkoutRepository(context) {
            @Override
            List<Workout> loadWorkouts(String typeFilter, String dateFilter, int limit) {
                throw new SQLiteException("simulated read failure");
            }
        };

        LoadResult<List<Workout>> result = failing.loadWorkoutsResult(null, null, 0);
        assertFalse(result.isSuccess());
        assertNull(result.getData());
        assertNotNull(result.getErrorMessage());

        assertTrue("legacy read keeps its empty-list contract",
                failing.getWorkouts(null, null, 0).isEmpty());
    }
}
