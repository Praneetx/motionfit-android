package com.example.fitnesstracker.models;

/**
 * Local reminder preferences, stored in the {@code app_settings} table.
 */
public class AppSettings {

    private boolean reminderEnabled;
    private int reminderHour;
    private int reminderMinute;

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
}
