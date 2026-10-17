package com.example.fitnesstracker.models;

/**
 * Aggregated activity for one calendar day. Used by the dashboard and the
 * daily/weekly/monthly progress views.
 */
public final class DaySummary {

    public final String dateIso;
    public final int steps;
    public final int workoutCount;
    public final int workoutMinutes;

    public DaySummary(String dateIso, int steps, int workoutCount, int workoutMinutes) {
        this.dateIso = dateIso;
        this.steps = steps;
        this.workoutCount = workoutCount;
        this.workoutMinutes = workoutMinutes;
    }
}
