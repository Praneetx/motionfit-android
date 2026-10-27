package io.github.praneetx.motionfit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.text.Layout;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.activities.SummaryActivity;
import io.github.praneetx.motionfit.activities.WorkoutListActivity;
import io.github.praneetx.motionfit.models.DailySteps;
import io.github.praneetx.motionfit.models.Workout;
import io.github.praneetx.motionfit.repositories.DataResetRepository;
import io.github.praneetx.motionfit.repositories.StepsRepository;
import io.github.praneetx.motionfit.repositories.WorkoutRepository;
import io.github.praneetx.motionfit.utils.DateUtils;
import io.github.praneetx.motionfit.views.BarChartView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

/**
 * Task 8 checks for Workouts and Progress: actions stay reachable, row text is
 * built from resources, Progress has a real empty state, and chart TalkBack
 * text describes the actual metric instead of a generic bar count.
 */
@RunWith(AndroidJUnit4.class)
public class WorkoutsProgressTest {

    private Context context;

    @Before
    public void resetData() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new DataResetRepository(context).clearAllData().isSuccess());
    }

    @Test
    public void progressEmptyUsesTopLevelCardAndHidesZeroCharts() {
        try (ActivityScenario<SummaryActivity> scenario =
                     ActivityScenario.launch(SummaryActivity.class)) {
            onView(withText(R.string.progress_empty_title)).check(matches(isDisplayed()));
            onView(withId(R.id.summaryEmptyCard)).check(matches(isDisplayed()));
            onView(withId(R.id.summaryErrorCard)).check(matches(withEffectiveVisibility(GONE)));
            onView(withId(R.id.chart)).check(matches(withEffectiveVisibility(GONE)));
            onView(withId(R.id.totalSteps)).check(matches(withEffectiveVisibility(GONE)));
        }
    }

    @Test
    public void progressChartSummaryDescribesStepsAndBestDay() {
        StepsRepository stepsRepository = new StepsRepository(context);
        assertTrue(stepsRepository.saveDailySteps(
                DateUtils.todayIso(), 1234, DailySteps.SOURCE_MANUAL).isSuccess());

        try (ActivityScenario<SummaryActivity> scenario =
                     ActivityScenario.launch(SummaryActivity.class)) {
            onView(withId(R.id.summaryEmptyCard)).check(matches(withEffectiveVisibility(GONE)));
            onView(withId(R.id.summaryHeroCard)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                BarChartView chart = activity.findViewById(R.id.chart);
                assertNotNull(chart);
                CharSequence description = chart.getContentDescription();
                assertNotNull(description);
                String text = description.toString();
                assertTrue("chart summary must name the steps metric: " + text,
                        text.toLowerCase(Locale.ROOT).contains("steps"));
                assertTrue("chart summary must name the best day: " + text,
                        text.contains("Best day"));
                assertTrue("chart summary must include the total and best value: " + text,
                        text.contains("1,234"));
            });
        }
    }

    @Test
    public void workoutsEmptyStateIsDistinctFromLoadError() {
        try (ActivityScenario<WorkoutListActivity> scenario =
                     ActivityScenario.launch(WorkoutListActivity.class)) {
            onView(withId(R.id.workoutsErrorState)).check(matches(withEffectiveVisibility(GONE)));
            onView(withId(R.id.workoutListCard)).check(matches(isDisplayed()));
            onView(withText(R.string.workouts_empty)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void workoutsEmptyStateOffersOneAddAction() {
        try (ActivityScenario<WorkoutListActivity> scenario =
                     ActivityScenario.launch(WorkoutListActivity.class)) {
            onView(withId(R.id.btnEmptyAddWorkout))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(R.string.action_add_workout)))
                    .perform(click());

            onView(withId(R.id.editTitle))
                    .check(matches(withText(R.string.title_add_workout)));
            pressBack();
        }
    }

    @Test
    public void rootDestinationsNoLongerExposeDeadBackControls() {
        try (ActivityScenario<SummaryActivity> scenario =
                     ActivityScenario.launch(SummaryActivity.class)) {
            onView(withId(R.id.btnBack)).check(doesNotExist());
        }
        try (ActivityScenario<WorkoutListActivity> scenario =
                     ActivityScenario.launch(WorkoutListActivity.class)) {
            onView(withId(R.id.btnBack)).check(doesNotExist());
        }
    }

    @Test
    public void progressValuesAreConstrained() {
        try (ActivityScenario<SummaryActivity> scenario =
                     ActivityScenario.launch(SummaryActivity.class)) {
            scenario.onActivity(activity -> {
                TextView totalSteps = activity.findViewById(R.id.totalSteps);
                assertNotNull(totalSteps);
                assertEquals("Progress total must autosize",
                        TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, totalSteps.getAutoSizeTextType());

                TextView period = activity.findViewById(R.id.periodLabel);
                assertNotNull(period);
                assertEquals("Period label must stay on one line", 1, period.getMaxLines());
                assertEquals("Period label must ellipsize when constrained",
                        TextUtils.TruncateAt.END, period.getEllipsize());
                assertEquals("Period label must autosize",
                        TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, period.getAutoSizeTextType());
            });
        }
    }

    @Test
    public void workoutRowsExpose48dpActionsAndContext() {
        String date = DateUtils.todayIso();
        Workout workout = new Workout("Running", date, 30, null);
        assertTrue(new WorkoutRepository(context).insert(workout).isSuccess());

        String displayDate = DateUtils.formatForDisplay(date);
        String expectedMeta = context.getString(
                R.string.workout_row_meta,
                displayDate,
                context.getString(R.string.workout_duration_value, 30));
        String expectedEdit = context.getString(
                R.string.workout_edit_action_description, "Running", displayDate);
        String expectedDelete = context.getString(
                R.string.workout_delete_action_description, "Running", displayDate);

        try (ActivityScenario<WorkoutListActivity> scenario =
                     ActivityScenario.launch(WorkoutListActivity.class)) {
            onView(withId(R.id.itemWorkoutMeta)).check(matches(withText(expectedMeta)));
            onView(withId(R.id.itemEdit))
                    .check(matches(isDisplayed()))
                    .check(matches(withContentDescription(expectedEdit)));
            onView(withId(R.id.itemDelete))
                    .check(matches(isDisplayed()))
                    .check(matches(withContentDescription(expectedDelete)));

            scenario.onActivity(activity -> {
                assertTrue("edit action must be 48dp",
                        activity.findViewById(R.id.itemEdit).getLayoutParams().height
                                >= dp(context, 48));
                assertTrue("delete action must be 48dp",
                        activity.findViewById(R.id.itemDelete).getLayoutParams().height
                                >= dp(context, 48));
            });
        }
    }

    @Test
    public void progressValuesFitAt360dpAnd13xFontScale() {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.fontScale = 1.3f;
        config.screenWidthDp = 360;
        config.screenHeightDp = 640;
        config.smallestScreenWidthDp = 360;

        Context scaled = context.createConfigurationContext(config);
        Context themed = new ContextThemeWrapper(scaled, R.style.Theme_Motionfit);
        LayoutInflater inflater = LayoutInflater.from(themed);
        int width = dp(scaled, 360);

        FrameLayout parent = new FrameLayout(themed);
        View root = inflater.inflate(R.layout.activity_summary, parent, false);
        TextView totalSteps = root.findViewById(R.id.totalSteps);
        TextView period = root.findViewById(R.id.periodLabel);
        assertNotNull(totalSteps);
        assertNotNull(period);

        totalSteps.setText("9,999,999");
        period.setText(DateUtils.formatRange(
                java.time.LocalDate.of(2026, 10, 26),
                java.time.LocalDate.of(2026, 11, 1)));
        root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        root.layout(0, 0, width, root.getMeasuredHeight());

        assertNoClip("Progress total", totalSteps);
        assertNoClip("Progress period", period);
    }

    private static void assertNoClip(String label, TextView value) {
        assertTrue(label + " must be laid out", value.getWidth() > 0);
        Layout textLayout = value.getLayout();
        assertNotNull(label + " must have a text layout", textLayout);
        assertEquals(label + " must stay on one line", 1, textLayout.getLineCount());
        float available = value.getWidth()
                - value.getPaddingLeft()
                - value.getPaddingRight();
        float used = textLayout.getLineWidth(0);
        assertTrue(label + " must not clip at 360dp/1.3x (used " + used
                        + "px, available " + available + "px)",
                used <= available + 1f);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
