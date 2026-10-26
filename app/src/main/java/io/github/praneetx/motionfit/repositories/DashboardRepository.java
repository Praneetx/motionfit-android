package io.github.praneetx.motionfit.repositories;

import android.content.Context;
import android.util.Log;

import io.github.praneetx.motionfit.models.DashboardData;
import io.github.praneetx.motionfit.models.DaySummary;
import io.github.praneetx.motionfit.models.FitnessGoal;
import io.github.praneetx.motionfit.models.Workout;
import io.github.praneetx.motionfit.utils.DateUtils;
import io.github.praneetx.motionfit.utils.LoadResult;

import java.util.List;

/**
 * Collects everything the dashboard needs from the other repositories.
 * The result wrapper lets the Activity tell a failed read apart from a real
 * day with zero steps and no workouts.
 */
public class DashboardRepository {

    private static final String TAG = "DashboardRepository";
    private static final int RECENT_WORKOUT_LIMIT = 5;

    private final SummaryRepository summaryRepository;
    private final GoalRepository goalRepository;
    private final WorkoutRepository workoutRepository;

    public DashboardRepository(Context context) {
        this(new SummaryRepository(context),
                new GoalRepository(context),
                new WorkoutRepository(context));
    }

    DashboardRepository(SummaryRepository summaryRepository,
                        GoalRepository goalRepository,
                        WorkoutRepository workoutRepository) {
        this.summaryRepository = summaryRepository;
        this.goalRepository = goalRepository;
        this.workoutRepository = workoutRepository;
    }

    /**
     * Loads the dashboard for one date.
     *
     * <p>A successful result may contain zero steps and an empty workout list;
     * that is a real empty day. Any database/query failure is returned as an
     * error instead of being disguised as zeros.</p>
     */
    public LoadResult<DashboardData> loadResult(String dateIso) {
        if (!DateUtils.isValidIsoDate(dateIso)) {
            return LoadResult.error("The selected date is not valid.");
        }
        try {
            DaySummary day = summaryRepository.loadDaySummary(dateIso);
            FitnessGoal goal = goalRepository.getGoal();
            List<Workout> recentWorkouts =
                    workoutRepository.loadWorkouts(null, null, RECENT_WORKOUT_LIMIT);
            return LoadResult.success(new DashboardData(
                    dateIso,
                    day.steps,
                    day.workoutCount,
                    day.workoutMinutes,
                    goal.getDailyStepGoal(),
                    goal.getDailyWorkoutMinutesGoal(),
                    recentWorkouts));
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not load dashboard for " + dateIso, e);
            return LoadResult.error("Could not load your activity data. Please try again.");
        }
    }

    /** Legacy wrapper: loads the dashboard for one date, or {@code null} if it failed. */
    public DashboardData load(String dateIso) {
        LoadResult<DashboardData> result = loadResult(dateIso);
        return result.isSuccess() ? result.getData() : null;
    }
}
