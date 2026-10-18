package io.github.praneetx.motionfit.models;

/**
 * A single consolidated step total for one calendar date.
 */
public class DailySteps {

    /** Value used when the user typed the number manually. */
    public static final String SOURCE_MANUAL = "manual";
    /** Value used when the number came from the device step sensor. */
    public static final String SOURCE_SENSOR = "sensor";

    private long id;
    private String activityDate; // YYYY-MM-DD
    private int steps;
    private String source;
    private String createdAt;
    private String updatedAt;

    public DailySteps() {
    }

    public DailySteps(String activityDate, int steps, String source) {
        this.activityDate = activityDate;
        this.steps = steps;
        this.source = source;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getActivityDate() {
        return activityDate;
    }

    public void setActivityDate(String activityDate) {
        this.activityDate = activityDate;
    }

    public int getSteps() {
        return steps;
    }

    public void setSteps(int steps) {
        this.steps = steps;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
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
