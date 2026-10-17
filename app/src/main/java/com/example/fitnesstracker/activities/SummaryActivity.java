package com.example.fitnesstracker.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fitnesstracker.R;
import com.example.fitnesstracker.logic.ActivitySummaryCalculator;
import com.example.fitnesstracker.logic.DayActivity;
import com.example.fitnesstracker.models.FitnessGoal;
import com.example.fitnesstracker.repositories.GoalRepository;
import com.example.fitnesstracker.repositories.SummaryRepository;
import com.example.fitnesstracker.utils.DateUtils;
import com.example.fitnesstracker.utils.FormatUtils;
import com.example.fitnesstracker.views.BarChartView;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily / weekly / monthly activity summaries and a bar chart.
 *
 * <p>SQL groups the records per calendar day, the Kotlin
 * {@link ActivitySummaryCalculator} fills in zero-activity days and produces the
 * totals, and this Activity simply displays them.</p>
 */
public class SummaryActivity extends AppCompatActivity {

    private enum Mode {DAILY, WEEKLY, MONTHLY}

    private SummaryRepository summaryRepository;
    private GoalRepository goalRepository;

    private Mode mode = Mode.DAILY;
    private LocalDate anchor = LocalDate.now();

    private BarChartView chart;
    private TextView periodLabel;
    private TextView totalStepsView;
    private TextView totalMinutesView;
    private TextView totalWorkoutsView;
    private TextView averageStepsView;
    private TextView bestDayView;
    private TextView activeDaysView;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);

        summaryRepository = new SummaryRepository(this);
        goalRepository = new GoalRepository(this);

        chart = findViewById(R.id.chart);
        periodLabel = findViewById(R.id.periodLabel);
        totalStepsView = findViewById(R.id.totalSteps);
        totalMinutesView = findViewById(R.id.totalMinutes);
        totalWorkoutsView = findViewById(R.id.totalWorkouts);
        averageStepsView = findViewById(R.id.avgSteps);
        bestDayView = findViewById(R.id.bestDay);
        activeDaysView = findViewById(R.id.activeDays);
        emptyView = findViewById(R.id.summaryEmpty);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPrevPeriod).setOnClickListener(v -> shiftPeriod(-1));
        findViewById(R.id.btnNextPeriod).setOnClickListener(v -> shiftPeriod(1));

        MaterialButtonToggleGroup toggle = findViewById(R.id.modeToggle);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.tabWeekly) {
                mode = Mode.WEEKLY;
            } else if (checkedId == R.id.tabMonthly) {
                mode = Mode.MONTHLY;
            } else {
                mode = Mode.DAILY;
            }
            anchor = LocalDate.now();
            refresh();
        });
        toggle.check(R.id.tabDaily);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void shiftPeriod(int delta) {
        switch (mode) {
            case WEEKLY:
                anchor = anchor.plusWeeks(delta);
                break;
            case MONTHLY:
                anchor = anchor.plusMonths(delta);
                break;
            case DAILY:
            default:
                anchor = anchor.plusDays(delta);
                break;
        }
        refresh();
    }

    private void refresh() {
        LocalDate start;
        LocalDate end;
        switch (mode) {
            case WEEKLY:
                start = DateUtils.startOfWeek(anchor);
                end = DateUtils.endOfWeek(anchor);
                break;
            case MONTHLY:
                start = DateUtils.startOfMonth(anchor);
                end = DateUtils.endOfMonth(anchor);
                break;
            case DAILY:
            default:
                start = anchor;
                end = anchor;
                break;
        }

        List<DayActivity> series = summaryRepository.getDailySeries(start, end);
        FitnessGoal goal = goalRepository.getGoal();

        // Chart data
        float[] values = new float[series.size()];
        String[] labels = new String[series.size()];
        for (int i = 0; i < series.size(); i++) {
            DayActivity day = series.get(i);
            values[i] = day.getSteps();
            labels[i] = (mode == Mode.WEEKLY)
                    ? DateUtils.weekdayShort(day.getDateIso())
                    : DateUtils.shortDay(day.getDateIso());
        }
        chart.setData(values, labels, goal.getDailyStepGoal());

        // Totals from the Kotlin calculator
        int steps = ActivitySummaryCalculator.totalSteps(series);
        int minutes = ActivitySummaryCalculator.totalMinutes(series);
        int workouts = ActivitySummaryCalculator.totalWorkouts(series);
        int average = ActivitySummaryCalculator.averageSteps(series);
        int activeDays = ActivitySummaryCalculator.activeDays(series);
        DayActivity best = ActivitySummaryCalculator.bestStepDay(series);

        totalStepsView.setText(FormatUtils.intText(steps));
        totalMinutesView.setText(FormatUtils.intText(minutes));
        totalWorkoutsView.setText(FormatUtils.intText(workouts));
        averageStepsView.setText(FormatUtils.intText(average));
        activeDaysView.setText(FormatUtils.intText(activeDays));
        bestDayView.setText(best == null || best.getSteps() == 0
                ? "-"
                : DateUtils.formatForDisplay(best.getDateIso()));

        periodLabel.setText(periodLabelText(start, end));
        emptyView.setVisibility((steps == 0 && workouts == 0) ? View.VISIBLE : View.GONE);
    }

    private String periodLabelText(LocalDate start, LocalDate end) {
        switch (mode) {
            case WEEKLY:
                return DateUtils.formatRange(start, end);
            case MONTHLY:
                return DateUtils.formatMonth(anchor);
            case DAILY:
            default:
                return DateUtils.formatForDisplay(start.format(DateUtils.ISO_DATE));
        }
    }
}
