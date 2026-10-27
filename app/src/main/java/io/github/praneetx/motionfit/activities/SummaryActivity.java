package io.github.praneetx.motionfit.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.praneetx.motionfit.R;
import io.github.praneetx.motionfit.adapters.StepHistoryAdapter;
import io.github.praneetx.motionfit.logic.ActivitySummaryCalculator;
import io.github.praneetx.motionfit.logic.DayActivity;
import io.github.praneetx.motionfit.models.DailySteps;
import io.github.praneetx.motionfit.models.FitnessGoal;
import io.github.praneetx.motionfit.repositories.GoalRepository;
import io.github.praneetx.motionfit.repositories.StepsRepository;
import io.github.praneetx.motionfit.repositories.SummaryRepository;
import io.github.praneetx.motionfit.utils.DateUtils;
import io.github.praneetx.motionfit.utils.FormatUtils;
import io.github.praneetx.motionfit.utils.LoadResult;
import io.github.praneetx.motionfit.utils.UiAnimations;
import io.github.praneetx.motionfit.views.BarChartView;
import io.github.praneetx.motionfit.views.FloatingNavView;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.time.LocalDate;
import java.util.List;

/**
 * Progress screen (Wise restyle).
 *
 * <p>Hero dark card shows the period step total, the daily average and the lime
 * bar chart with a dashed goal line and today's bar at full strength. Two white
 * cards below show workout time (cyan) and workout count (orange).</p>
 *
 * <p>All numbers still come from SQLite via {@link SummaryRepository} and the
 * Kotlin {@link ActivitySummaryCalculator}; only presentation changed.</p>
 */
public class SummaryActivity extends AppCompatActivity {

    private enum Mode {DAILY, WEEKLY, MONTHLY}

    private SummaryRepository summaryRepository;
    private GoalRepository goalRepository;
    private StepsRepository stepsRepository;

    private Mode mode = Mode.DAILY;
    private LocalDate anchor = LocalDate.now();

    private BarChartView chart;
    private BarChartView minutesChart;
    private BarChartView workoutsChart;
    private TextView periodLabel;
    private TextView totalStepsView;
    private TextView totalMinutesView;
    private TextView totalWorkoutsView;
    private TextView averageStepsView;
    private TextView bestDayView;
    private TextView activeDaysView;
    private View emptyCard;
    private View errorCard;
    private View heroCard;
    private View chartsRow;
    private View detailsCard;
    private View stepHistoryCard;
    private View stepHistoryEmpty;
    private RecyclerView stepHistoryList;
    private StepHistoryAdapter stepHistoryAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);

        summaryRepository = new SummaryRepository(this);
        goalRepository = new GoalRepository(this);
        stepsRepository = new StepsRepository(this);

        chart = findViewById(R.id.chart);
        minutesChart = findViewById(R.id.minutesChart);
        workoutsChart = findViewById(R.id.workoutsChart);
        periodLabel = findViewById(R.id.periodLabel);
        totalStepsView = findViewById(R.id.totalSteps);
        totalMinutesView = findViewById(R.id.totalMinutes);
        totalWorkoutsView = findViewById(R.id.totalWorkouts);
        averageStepsView = findViewById(R.id.avgSteps);
        bestDayView = findViewById(R.id.bestDay);
        activeDaysView = findViewById(R.id.activeDays);
        emptyCard = findViewById(R.id.summaryEmptyCard);
        errorCard = findViewById(R.id.summaryErrorCard);
        heroCard = findViewById(R.id.summaryHeroCard);
        chartsRow = findViewById(R.id.summaryChartsRow);
        detailsCard = findViewById(R.id.summaryDetailsCard);
        stepHistoryCard = findViewById(R.id.summaryStepHistoryCard);
        stepHistoryEmpty = findViewById(R.id.progressStepHistoryEmpty);
        stepHistoryList = findViewById(R.id.progressStepHistoryList);

        stepHistoryAdapter = new StepHistoryAdapter(
                record -> openStepsHistory(record.getActivityDate()));
        stepHistoryList.setLayoutManager(new LinearLayoutManager(this));
        stepHistoryList.setAdapter(stepHistoryAdapter);

        configureCharts();

        findViewById(R.id.btnPrevPeriod).setOnClickListener(v -> shiftPeriod(-1));
        findViewById(R.id.btnNextPeriod).setOnClickListener(v -> shiftPeriod(1));
        findViewById(R.id.btnRetrySummary).setOnClickListener(v -> refresh());
        findViewById(R.id.btnOpenStepsHistory).setOnClickListener(v -> openStepsHistory());
        findViewById(R.id.btnEmptyProgressSteps).setOnClickListener(v -> openStepsHistory());

        FloatingNavView.bind(this, FloatingNavView.DEST_PROGRESS);

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

    /**
     * Data colours are used ONLY inside charts: lime on the ink hero card,
     * cyan for workout time, orange for workout count.
     */
    private void configureCharts() {
        chart.configure(
                getColor(R.color.primary),
                getColor(R.color.ink_deep),
                getColor(R.color.canvas_soft),
                getColor(R.color.primary_active),
                getColor(R.color.canvas_soft));

        minutesChart.configure(
                getColor(R.color.accent_cyan),
                getColor(R.color.canvas_soft),
                getColor(R.color.body),
                getColor(R.color.canvas_soft),
                getColor(R.color.mute));

        workoutsChart.configure(
                getColor(R.color.accent_orange),
                getColor(R.color.canvas_soft),
                getColor(R.color.body),
                getColor(R.color.canvas_soft),
                getColor(R.color.mute));

        chart.setDimAlphaFactor(0.5f);
        minutesChart.setDimAlphaFactor(1f);
        workoutsChart.setDimAlphaFactor(1f);
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

        String periodText = periodLabelText(start, end);
        periodLabel.setText(periodText);
        periodLabel.setContentDescription(
                getString(R.string.progress_period_description, periodText));

        LoadResult<List<DayActivity>> result =
                summaryRepository.loadDailySeriesResult(start, end);
        if (!result.isSuccess() || result.getData() == null) {
            showErrorState();
            return;
        }

        List<DayActivity> series = result.getData();
        FitnessGoal goal = goalRepository.getGoal();

        // Chart series + labels in one pass
        float[] stepValues = new float[series.size()];
        float[] minuteValues = new float[series.size()];
        float[] countValues = new float[series.size()];
        String[] labels = new String[series.size()];
        for (int i = 0; i < series.size(); i++) {
            DayActivity day = series.get(i);
            stepValues[i] = day.getSteps();
            minuteValues[i] = day.getWorkoutMinutes();
            countValues[i] = day.getWorkoutCount();
            labels[i] = (mode == Mode.WEEKLY)
                    ? DateUtils.weekdayShort(day.getDateIso())
                    : DateUtils.shortDay(day.getDateIso());
        }

        chart.setData(stepValues, labels, goal.getDailyStepGoal());
        // Today's bar full strength, the rest dimmed (dashed goal line still shown).
        chart.setHighlightIndex(indexOfToday(series));
        minutesChart.setData(minuteValues, labels, 0f);
        workoutsChart.setData(countValues, labels, 0f);

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

        if (best != null && best.getSteps() > 0) {
            chart.setAccessibilitySummary(getString(
                    R.string.chart_steps_accessibility_summary,
                    FormatUtils.intText(steps),
                    FormatUtils.intText(average),
                    DateUtils.formatForDisplay(best.getDateIso()),
                    FormatUtils.intText(best.getSteps()),
                    periodText));
        } else {
            chart.setAccessibilitySummary(getString(
                    R.string.chart_steps_accessibility_summary_empty, periodText));
        }
        minutesChart.setAccessibilitySummary(getString(
                R.string.chart_minutes_accessibility_summary,
                FormatUtils.intText(minutes),
                periodText));
        workoutsChart.setAccessibilitySummary(getString(
                R.string.chart_workouts_accessibility_summary,
                FormatUtils.intText(workouts),
                FormatUtils.intText(activeDays),
                periodText));

        showContentState(steps == 0 && workouts == 0);
        loadStepHistory();
    }

    /** Last seven recorded step days, independent of the selected chart period. */
    private void loadStepHistory() {
        List<DailySteps> all = stepsRepository.getAll();
        int limit = Math.min(7, all.size());
        stepHistoryAdapter.setItems(all.subList(0, limit));
        boolean isEmpty = limit == 0;
        stepHistoryList.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        stepHistoryEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void openStepsHistory() {
        openStepsHistory(DateUtils.todayIso());
    }

    private void openStepsHistory(String dateIso) {
        Intent intent = new Intent(this, StepsActivity.class);
        intent.putExtra(StepsActivity.EXTRA_DATE, dateIso);
        startActivity(intent);
        UiAnimations.fadeTransition(this);
    }

    private void showErrorState() {
        emptyCard.setVisibility(View.GONE);
        errorCard.setVisibility(View.VISIBLE);
        heroCard.setVisibility(View.GONE);
        chartsRow.setVisibility(View.GONE);
        detailsCard.setVisibility(View.GONE);
        stepHistoryCard.setVisibility(View.GONE);
    }

    private void showContentState(boolean isEmpty) {
        errorCard.setVisibility(View.GONE);
        emptyCard.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        stepHistoryCard.setVisibility(View.VISIBLE);

        int contentVisibility = isEmpty ? View.GONE : View.VISIBLE;
        heroCard.setVisibility(contentVisibility);
        chartsRow.setVisibility(contentVisibility);
        detailsCard.setVisibility(contentVisibility);
    }

    private int indexOfToday(List<DayActivity> series) {
        String today = DateUtils.todayIso();
        for (int i = 0; i < series.size(); i++) {
            if (today.equals(series.get(i).getDateIso())) {
                return i;
            }
        }
        return -1;
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
