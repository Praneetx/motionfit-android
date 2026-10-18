package io.github.praneetx.motionfit.repositories;

import android.content.Context;
import android.util.Log;

import io.github.praneetx.motionfit.models.DashboardData;
import io.github.praneetx.motionfit.models.DaySummary;
import io.github.praneetx.motionfit.models.FitnessGoal;
import io.github.praneetx.motionfit.models.Workout;
import io.github.praneetx.motionfit.utils.DateUtils;

import java.util.List;

/**
 * Collects everything the dashboard needs from the other repositories.
 * If an unexpected failure happens it returns {@code null} so the Activity can
 * show a clear error message instead of a confusing half-empty screen.
 */
public class DashboardRepository {

    private static final String TAG = "DashboardRepository";
    private static final int RECENT_WORKOUT_LIMIT = 5;

    private final SummaryRepository summaryRepository;
    private final GoalRepository goalRepository;
    private final WorkoutRepository workoutRepository;

    public DashboardRepository(Context context) {
        this.summaryRepository = new SummaryRepository(context);
        this.goalRepository = new GoalRepository(context);
        this.workoutRepository = new WorkoutRepository(context);
    }

    /** Loads the dashboard for one date, or {@code null} if it failed. */
    public DashboardData load(String dateIso) {
        if (!DateUtils.isValidIsoDate(dateIso)) {
            return null;
        }
        try {
            DaySummary day = summaryRepository.getDaySummary(dateIso);
            FitnessGoal goal = goalRepository.getGoal();
            List<Workout> recentWorkouts = workoutRepository.getRecent(RECENT_WORKOUT_LIMIT);
            return new DashboardData(
                    dateIso,
                    day.steps,
                    day.workoutCount,
                    day.workoutMinutes,
                    goal.getDailyStepGoal(),
                    goal.getDailyWorkoutMinutesGoal(),
                    recentWorkouts);
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not load dashboard for " + dateIso, e);
            return null;
        }
    }
}
