package com.example.fitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.fitnesstracker.database.DatabaseContract;
import com.example.fitnesstracker.logic.ActivitySummaryCalculator;
import com.example.fitnesstracker.logic.DayActivity;
import com.example.fitnesstracker.models.DailySteps;
import com.example.fitnesstracker.models.FitnessGoal;
import com.example.fitnesstracker.models.Workout;
import com.example.fitnesstracker.repositories.DataResetRepository;
import com.example.fitnesstracker.repositories.GoalRepository;
import com.example.fitnesstracker.repositories.StepsRepository;
import com.example.fitnesstracker.repositories.SummaryRepository;
import com.example.fitnesstracker.repositories.WorkoutRepository;
import com.example.fitnesstracker.utils.DateUtils;
import com.example.fitnesstracker.utils.OperationResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.List;

/**
 * Instrumented tests that exercise the real SQLite database on an Android
 * device or emulator (run with: gradlew connectedDebugAndroidTest).
 *
 * <p>Each test starts from a clean database via the in-app "clear all data"
 * logic, so it never depends on whatever was previously stored.</p>
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseInstrumentedTest {

    private static final String SAME_DAY = "2031-05-05";

    private StepsRepository stepsRepository;
    private WorkoutRepository workoutRepository;
    private GoalRepository goalRepository;
    private SummaryRepository summaryRepository;
    private DataResetRepository dataResetRepository;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        stepsRepository = new StepsRepository(context);
        workoutRepository = new WorkoutRepository(context);
        goalRepository = new GoalRepository(context);
        summaryRepository = new SummaryRepository(context);
        dataResetRepository = new DataResetRepository(context);

        // Known starting state: no steps, no workouts, default goals.
        OperationResult reset = dataResetRepository.clearAllData();
        assertTrue("clearAllData should succeed", reset.isSuccess());
    }

    @Test
    public void savingSameDateTwice_keepsExactlyOneRecord() {
        assertTrue(stepsRepository.saveDailySteps(SAME_DAY, 1000, DailySteps.SOURCE_MANUAL).isSuccess());
        assertTrue(stepsRepository.saveDailySteps(SAME_DAY, 2500, DailySteps.SOURCE_MANUAL).isSuccess());

        DailySteps stored = stepsRepository.getForDate(SAME_DAY);
        assertNotNull(stored);
        assertEquals(2500, stored.getSteps());

        int rowsForDate = 0;
        for (DailySteps record : stepsRepository.getAll()) {
            if (SAME_DAY.equals(record.getActivityDate())) {
                rowsForDate++;
            }
        }
        assertEquals("UNIQUE(activity_date) must keep one row per day", 1, rowsForDate);
    }

    @Test
    public void negativeStepTotalIsRejected() {
        OperationResult result = stepsRepository.saveDailySteps(SAME_DAY, -5, DailySteps.SOURCE_MANUAL);
        assertTrue("negative steps must be rejected", !result.isSuccess());
        assertNull(stepsRepository.getForDate(SAME_DAY));
    }

    @Test
    public void zeroDurationWorkoutIsRejected() {
        OperationResult invalid = workoutRepository.insert(
                new Workout("Running", SAME_DAY, 0, null));
        assertTrue("zero duration must be rejected", !invalid.isSuccess());
        assertTrue(workoutRepository.getForDate(SAME_DAY).isEmpty());

        OperationResult valid = workoutRepository.insert(
                new Workout("Running", SAME_DAY, 30, "morning run"));
        assertTrue(valid.isSuccess());

        List<Workout> workouts = workoutRepository.getForDate(SAME_DAY);
        assertEquals(1, workouts.size());
        assertEquals("Running", workouts.get(0).getWorkoutType());
        assertEquals(30, workouts.get(0).getDurationMinutes());
        assertEquals("morning run", workouts.get(0).getNotes());
    }

    @Test
    public void goalMustBePositiveAndPersists() {
        assertTrue("zero step goal must be rejected",
                !goalRepository.saveGoal(0, 30).isSuccess());
        assertTrue("zero minutes goal must be rejected",
                !goalRepository.saveGoal(8000, 0).isSuccess());

        assertTrue(goalRepository.saveGoal(12345, 45).isSuccess());
        FitnessGoal goal = goalRepository.getGoal();
        assertEquals(12345, goal.getDailyStepGoal());
        assertEquals(45, goal.getDailyWorkoutMinutesGoal());
    }

    @Test
    public void weeklySeriesZeroFillsAndSumsCorrectly() {
        LocalDate anyDay = LocalDate.of(2030, 1, 10);
        LocalDate start = DateUtils.startOfWeek(anyDay);
        LocalDate end = DateUtils.endOfWeek(anyDay);
        String firstIso = start.format(DateUtils.ISO_DATE);
        String secondIso = start.plusDays(1).format(DateUtils.ISO_DATE);

        assertTrue(stepsRepository.saveDailySteps(firstIso, 4000, DailySteps.SOURCE_MANUAL).isSuccess());
        assertTrue(stepsRepository.saveDailySteps(secondIso, 6000, DailySteps.SOURCE_MANUAL).isSuccess());
        assertTrue(workoutRepository.insert(new Workout("Running", firstIso, 30, null)).isSuccess());
        assertTrue(workoutRepository.insert(new Workout("Yoga", secondIso, 20, null)).isSuccess());

        List<DayActivity> series = summaryRepository.getDailySeries(start, end);
        assertEquals("a week always has 7 day entries", 7, series.size());

        assertEquals(10000, ActivitySummaryCalculator.totalSteps(series));
        assertEquals(50, ActivitySummaryCalculator.totalMinutes(series));
        assertEquals(2, ActivitySummaryCalculator.totalWorkouts(series));
        assertEquals(5, ActivitySummaryCalculator.activeDays(series));
    }

    @Test
    public void clearAllData_removesRecordsAndResetsGoal() {
        assertTrue(stepsRepository.saveDailySteps(SAME_DAY, 9000, DailySteps.SOURCE_MANUAL).isSuccess());
        assertTrue(workoutRepository.insert(new Workout("Cycling", SAME_DAY, 60, "no distance tracked")).isSuccess());
        assertTrue(goalRepository.saveGoal(15000, 90).isSuccess());

        assertTrue(dataResetRepository.clearAllData().isSuccess());

        assertNull(stepsRepository.getForDate(SAME_DAY));
        assertTrue(workoutRepository.getAll().isEmpty());
        assertEquals(DatabaseContract.DEFAULT_STEP_GOAL, goalRepository.getGoal().getDailyStepGoal());
        assertEquals(DatabaseContract.DEFAULT_WORKOUT_MINUTES_GOAL,
                goalRepository.getGoal().getDailyWorkoutMinutesGoal());
    }
}
