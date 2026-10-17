package com.example.fitnesstracker.models;

/**
 * The single active fitness goal record (id = 1).
 */
public class FitnessGoal {

    private long id;
    private int dailyStepGoal;
    private int dailyWorkoutMinutesGoal;
    private String updatedAt;

    public FitnessGoal() {
    }

    public FitnessGoal(int dailyStepGoal, int dailyWorkoutMinutesGoal) {
        this.dailyStepGoal = dailyStepGoal;
        this.dailyWorkoutMinutesGoal = dailyWorkoutMinutesGoal;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getDailyStepGoal() {
        return dailyStepGoal;
    }

    public void setDailyStepGoal(int dailyStepGoal) {
        this.dailyStepGoal = dailyStepGoal;
    }

    public int getDailyWorkoutMinutesGoal() {
        return dailyWorkoutMinutesGoal;
    }

    public void setDailyWorkoutMinutesGoal(int dailyWorkoutMinutesGoal) {
        this.dailyWorkoutMinutesGoal = dailyWorkoutMinutesGoal;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
