package io.github.praneetx.motionfit.models;

/**
 * One workout record. Workouts store duration only - the app deliberately does
 * NOT store or calculate distance or calories.
 */
public class Workout {

    /**
     * The workout categories offered by the app. "Cycling" is only a category;
     * it never introduces distance tracking.
     */
    public static final String[] TYPES = {
            "Walking",
            "Running",
            "Yoga",
            "Strength Training",
            "Stretching",
            "Cycling",
            "Other"
    };

    private long id;
    private String workoutType;
    private String workoutDate; // YYYY-MM-DD
    private int durationMinutes;
    private Integer durationSeconds;
    private Integer stepCount;
    private String notes;
    private String createdAt;
    private String updatedAt;

    public Workout() {
    }

    public Workout(String workoutType, String workoutDate, int durationMinutes, String notes) {
        this.workoutType = workoutType;
        this.workoutDate = workoutDate;
        this.durationMinutes = durationMinutes;
        this.notes = notes;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getWorkoutType() {
        return workoutType;
    }

    public void setWorkoutType(String workoutType) {
        this.workoutType = workoutType;
    }

    public String getWorkoutDate() {
        return workoutDate;
    }

    public void setWorkoutDate(String workoutDate) {
        this.workoutDate = workoutDate;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    /**
     * Exact duration in seconds for a Running-mode workout, or {@code null} for
     * manual and migrated workouts.
     */
    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    /**
     * Recorded steps for a Running-mode workout, or {@code null} when steps were
     * unavailable (or the workout is manual/migrated).
     */
    public Integer getStepCount() {
        return stepCount;
    }

    public void setStepCount(Integer stepCount) {
        this.stepCount = stepCount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
