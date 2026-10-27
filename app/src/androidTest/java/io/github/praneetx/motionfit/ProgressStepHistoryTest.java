package io.github.praneetx.motionfit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.activities.SummaryActivity;
import io.github.praneetx.motionfit.models.DailySteps;
import io.github.praneetx.motionfit.repositories.DataResetRepository;
import io.github.praneetx.motionfit.repositories.StepsRepository;
import io.github.praneetx.motionfit.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;

/**
 * Task 15 checks the compact Progress step history and its empty-state action.
 */
@RunWith(AndroidJUnit4.class)
public class ProgressStepHistoryTest {

    private Context context;
    private StepsRepository stepsRepository;

    @Before
    public void resetData() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new DataResetRepository(context).clearAllData().isSuccess());
        stepsRepository = new StepsRepository(context);
    }

    @Test
    public void progressShowsLastSevenRecordedDaysNewestFirst() {
        LocalDate today = LocalDate.parse(DateUtils.todayIso());
        for (int daysAgo = 0; daysAgo < 8; daysAgo++) {
            String date = today.minusDays(daysAgo).format(DateUtils.ISO_DATE);
            assertTrue(stepsRepository.saveDailySteps(
                    date, 100 + daysAgo, DailySteps.SOURCE_MANUAL).isSuccess());
        }

        try (ActivityScenario<SummaryActivity> scenario =
                     ActivityScenario.launch(SummaryActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView history = activity.findViewById(R.id.progressStepHistoryList);
                assertNotNull("Progress must expose a step history list", history);
                assertNotNull(history.getAdapter());
                assertEquals("Only the seven most recent recorded days belong here",
                        7, history.getAdapter().getItemCount());

                TextView empty = activity.findViewById(R.id.progressStepHistoryEmpty);
                assertEquals("Recorded days must not show the history empty state",
                        android.view.View.GONE, empty.getVisibility());
            });

            String newest = DateUtils.formatForDisplay(
                    today.format(DateUtils.ISO_DATE));
            String omitted = DateUtils.formatForDisplay(
                    today.minusDays(7).format(DateUtils.ISO_DATE));
            onView(allOf(withText(newest),
                    isDescendantOfA(withId(R.id.progressStepHistoryList))))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
            onView(withText(omitted)).check(doesNotExist());
        }
    }

    @Test
    public void progressHistoryEmptyStateDoesNotFabricateZeroRows() {
        try (ActivityScenario<SummaryActivity> scenario =
                     ActivityScenario.launch(SummaryActivity.class)) {
            onView(withId(R.id.progressStepHistoryEmpty))
                    .perform(scrollTo())
                    .check(matches(withText(R.string.progress_step_history_empty)));
            onView(withId(R.id.progressStepHistoryList))
                    .check(matches(withEffectiveVisibility(GONE)));
            onView(withId(R.id.btnOpenStepsHistory))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(R.string.progress_step_history_action)));
        }
    }

    @Test
    public void progressEmptyStateOffersOneStepsAction() {
        try (ActivityScenario<SummaryActivity> scenario =
                     ActivityScenario.launch(SummaryActivity.class)) {
            onView(withId(R.id.btnEmptyProgressSteps))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(R.string.action_log_steps)))
                    .perform(click());

            onView(withId(R.id.stepsSelectedValue)).check(matches(isDisplayed()));
            onView(withId(R.id.btnBack)).check(matches(isDisplayed()));
            pressBack();
        }
    }
}
