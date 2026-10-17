package com.example.fitnesstracker.repositories;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import com.example.fitnesstracker.database.DatabaseContract;
import com.example.fitnesstracker.database.FitnessDatabaseHelper;
import com.example.fitnesstracker.logic.ActivitySummaryCalculator;
import com.example.fitnesstracker.logic.DayActivity;
import com.example.fitnesstracker.models.DaySummary;
import com.example.fitnesstracker.utils.DateUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds aggregated day/week/month activity data from SQLite.
 *
 * <p>SQL does the grouping per calendar day; the Kotlin
 * {@link ActivitySummaryCalculator} then fills in zero-activity days and
 * calculates totals/averages. This keeps the "one row per day" grouping in SQL
 * (fast) and the presentation maths in testable Kotlin.</p>
 */
public class SummaryRepository {

    private static final String TAG = "SummaryRepository";
    private final FitnessDatabaseHelper helper;

    public SummaryRepository(Context context) {
        this.helper = FitnessDatabaseHelper.getInstance(context);
    }

    /** Steps + workout totals for a single calendar day. */
    public DaySummary getDaySummary(String dateIso) {
        int steps = 0;
        int workoutCount = 0;
        int workoutMinutes = 0;

        if (!DateUtils.isValidIsoDate(dateIso)) {
            return new DaySummary(dateIso, 0, 0, 0);
        }

        try {
            SQLiteDatabase db = helper.getReadableDatabase();

            Cursor stepCursor = db.rawQuery(
                    "SELECT " + DatabaseContract.COL_DS_STEPS
                            + " FROM " + DatabaseContract.TABLE_DAILY_STEPS
                            + " WHERE " + DatabaseContract.COL_DS_DATE + " = ?",
                    new String[]{dateIso});
            try (stepCursor) {
                if (stepCursor.moveToFirst()) {
                    steps = stepCursor.getInt(0);
                }
            }

            Cursor workoutCursor = db.rawQuery(
                    "SELECT COUNT(*), IFNULL(SUM(" + DatabaseContract.COL_W_DURATION + "), 0)"
                            + " FROM " + DatabaseContract.TABLE_WORKOUTS
                            + " WHERE " + DatabaseContract.COL_W_DATE + " = ?",
                    new String[]{dateIso});
            try (workoutCursor) {
                if (workoutCursor.moveToFirst()) {
                    workoutCount = workoutCursor.getInt(0);
                    workoutMinutes = workoutCursor.getInt(1);
                }
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "getDaySummary failed for " + dateIso, e);
        }

        return new DaySummary(dateIso, steps, workoutCount, workoutMinutes);
    }

    /**
     * Returns one {@link DayActivity} per calendar date from {@code start} to
     * {@code end} inclusive. Days without activity are filled with zeros.
     */
    public List<DayActivity> getDailySeries(LocalDate start, LocalDate end) {
        List<String> dates = DateUtils.isoDatesBetween(start, end);
        if (dates.isEmpty()) {
            return new ArrayList<>();
        }
        List<DayActivity> recorded = loadRecorded(start, end);
        // Java hands the raw rows to the Kotlin calculator.
        return ActivitySummaryCalculator.zeroFill(dates, recorded);
    }

    /** Aggregated totals for the given range (convenience wrapper). */
    public List<DayActivity> getWeekSeries(LocalDate anyDayInWeek) {
        return getDailySeries(DateUtils.startOfWeek(anyDayInWeek), DateUtils.endOfWeek(anyDayInWeek));
    }

    public List<DayActivity> getMonthSeries(LocalDate anyDayInMonth) {
        return getDailySeries(DateUtils.startOfMonth(anyDayInMonth), DateUtils.endOfMonth(anyDayInMonth));
    }

    /** Loads the per-day rows that actually exist inside the range. */
    private List<DayActivity> loadRecorded(LocalDate start, LocalDate end) {
        String startIso = start.format(DateUtils.ISO_DATE);
        String endIso = end.format(DateUtils.ISO_DATE);
        // index 0 = steps, index 1 = workoutMinutes, index 2 = workoutCount
        Map<String, int[]> perDay = new HashMap<>();

        try {
            SQLiteDatabase db = helper.getReadableDatabase();

            Cursor stepCursor = db.rawQuery(
                    "SELECT " + DatabaseContract.COL_DS_DATE + ", " + DatabaseContract.COL_DS_STEPS
                            + " FROM " + DatabaseContract.TABLE_DAILY_STEPS
                            + " WHERE " + DatabaseContract.COL_DS_DATE + " BETWEEN ? AND ?",
                    new String[]{startIso, endIso});
            try (stepCursor) {
                while (stepCursor.moveToNext()) {
                    String date = stepCursor.getString(0);
                    int[] slot = perDay.computeIfAbsent(date, key -> new int[3]);
                    slot[0] = stepCursor.getInt(1);
                }
            }

            Cursor workoutCursor = db.rawQuery(
                    "SELECT " + DatabaseContract.COL_W_DATE
                            + ", COUNT(*)"
                            + ", IFNULL(SUM(" + DatabaseContract.COL_W_DURATION + "), 0)"
                            + " FROM " + DatabaseContract.TABLE_WORKOUTS
                            + " WHERE " + DatabaseContract.COL_W_DATE + " BETWEEN ? AND ?"
                            + " GROUP BY " + DatabaseContract.COL_W_DATE,
                    new String[]{startIso, endIso});
            try (workoutCursor) {
                while (workoutCursor.moveToNext()) {
                    String date = workoutCursor.getString(0);
                    int[] slot = perDay.computeIfAbsent(date, key -> new int[3]);
                    slot[2] = workoutCursor.getInt(1);
                    slot[1] = workoutCursor.getInt(2);
                }
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "loadRecorded failed", e);
        }

        List<DayActivity> recorded = new ArrayList<>(perDay.size());
        for (Map.Entry<String, int[]> entry : perDay.entrySet()) {
            int[] values = entry.getValue();
            recorded.add(new DayActivity(entry.getKey(), values[0], values[1], values[2]));
        }
        return recorded;
    }
}
