package io.github.praneetx.motionfit.models;

import java.util.Collections;
import java.util.List;

/**
 * Everything the dashboard needs for one day, loaded in a single call so the
 * screen can render once and show one clear error message if loading failed.
 */
public final class DashboardData {

    public final String dateIso;
    public final int steps;
    public final int workoutCount;
    public final int workoutMinutes;
    public final int stepGoal;
    public final int workoutMinutesGoal;
    public final List<Workout> recentWorkouts;

    public DashboardData(String dateIso,
                         int steps,
                         int workoutCount,
                         int workoutMinutes,
                         int stepGoal,
                         int workoutMinutesGoal,
                         List<Workout> recentWorkouts) {
        this.dateIso = dateIso;
        this.steps = steps;
        this.workoutCount = workoutCount;
        this.workoutMinutes = workoutMinutes;
        this.stepGoal = stepGoal;
        this.workoutMinutesGoal = workoutMinutesGoal;
        this.recentWorkouts = recentWorkouts == null ? Collections.emptyList() : recentWorkouts;
    }
}
