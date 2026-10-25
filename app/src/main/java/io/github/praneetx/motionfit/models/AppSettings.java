package io.github.praneetx.motionfit.models;

/**
 * Local reminder preferences, stored in the {@code app_settings} table.
 */
public class AppSettings {

    private boolean reminderEnabled;
    private int reminderHour;
    private int reminderMinute;
    private boolean autoStepsEnabled;
    private boolean onboardingCompleted;

    public AppSettings() {
    }

    public AppSettings(boolean reminderEnabled, int reminderHour, int reminderMinute) {
        this.reminderEnabled = reminderEnabled;
        this.reminderHour = reminderHour;
        this.reminderMinute = reminderMinute;
    }

    public boolean isReminderEnabled() {
        return reminderEnabled;
    }

    public void setReminderEnabled(boolean reminderEnabled) {
        this.reminderEnabled = reminderEnabled;
    }

    public int getReminderHour() {
        return reminderHour;
    }

    public void setReminderHour(int reminderHour) {
        this.reminderHour = reminderHour;
    }

    public int getReminderMinute() {
        return reminderMinute;
    }

    public void setReminderMinute(int reminderMinute) {
        this.reminderMinute = reminderMinute;
    }

    public boolean isAutoStepsEnabled() {
        return autoStepsEnabled;
    }

    public void setAutoStepsEnabled(boolean autoStepsEnabled) {
        this.autoStepsEnabled = autoStepsEnabled;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    public void setOnboardingCompleted(boolean onboardingCompleted) {
        this.onboardingCompleted = onboardingCompleted;
    }
}
