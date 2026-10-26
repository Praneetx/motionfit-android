package io.github.praneetx.motionfit.repositories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.models.DashboardData;
import io.github.praneetx.motionfit.models.DaySummary;
import io.github.praneetx.motionfit.models.Workout;
import io.github.praneetx.motionfit.utils.LoadResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Task 7: a real empty day must be a success with zeros, while a failed read
 * must never look like zero activity.
 */
@RunWith(AndroidJUnit4.class)
public class DashboardRepositoryTest {

    private static final String EMPTY_DAY = "2031-06-06";

    private Context context;

    @Before
    public void resetData() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new DataResetRepository(context).clearAllData().isSuccess());
    }

    @Test
    public void emptyDayIsSuccessfulZeroData() {
        LoadResult<DashboardData> result = new DashboardRepository(context).loadResult(EMPTY_DAY);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(EMPTY_DAY, result.getData().dateIso);
        assertEquals(0, result.getData().steps);
        assertEquals(0, result.getData().workoutCount);
        assertTrue(result.getData().recentWorkouts.isEmpty());
    }

    @Test
    public void invalidDateIsAnErrorNotZeroData() {
        LoadResult<DashboardData> result = new DashboardRepository(context).loadResult("not-a-date");

        assertFalse(result.isSuccess());
        assertNull(result.getData());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    public void summaryReadFailureIsAnErrorNotZeroData() {
        DashboardRepository repository = failingRepository(new FailingSummaryRepository(context),
                new WorkoutRepository(context));

        LoadResult<DashboardData> result = repository.loadResult(EMPTY_DAY);

        assertFalse(result.isSuccess());
        assertNull(result.getData());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    public void recentWorkoutReadFailureIsAnErrorNotZeroData() {
        DashboardRepository repository = failingRepository(new SummaryRepository(context),
                new FailingWorkoutRepository(context));

        LoadResult<DashboardData> result = repository.loadResult(EMPTY_DAY);

        assertFalse(result.isSuccess());
        assertNull(result.getData());
        assertNotNull(result.getErrorMessage());
    }

    private DashboardRepository failingRepository(SummaryRepository summaryRepository,
                                                  WorkoutRepository workoutRepository) {
        return new DashboardRepository(
                summaryRepository,
                new GoalRepository(context),
                workoutRepository);
    }

    private static final class FailingSummaryRepository extends SummaryRepository {

        FailingSummaryRepository(Context context) {
            super(context);
        }

        @Override
        DaySummary loadDaySummary(String dateIso) {
            throw new SQLiteException("forced summary failure");
        }
    }

    private static final class FailingWorkoutRepository extends WorkoutRepository {

        FailingWorkoutRepository(Context context) {
            super(context);
        }

        @Override
        List<Workout> loadWorkouts(String typeFilter, String dateFilter, int limit) {
            throw new SQLiteException("forced workout failure");
        }
    }
}
