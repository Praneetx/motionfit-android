package io.github.praneetx.motionfit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.text.Layout;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.activities.MainActivity;
import io.github.praneetx.motionfit.activities.SummaryActivity;
import io.github.praneetx.motionfit.repositories.DataResetRepository;
import io.github.praneetx.motionfit.repositories.SettingsRepository;
import io.github.praneetx.motionfit.services.ActivityTrackingService;
import io.github.praneetx.motionfit.views.FloatingNavView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Task 14 checks the approved five-destination navigation and Steps' demotion
 * to a detail screen. Running mode itself is covered by RunningScreenTest.
 */
@RunWith(AndroidJUnit4.class)
public class NavigationTest {

    private Context context;
    private SettingsRepository settingsRepository;

    @Before
    public void resetData() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new DataResetRepository(context).clearAllData().isSuccess());
        settingsRepository = new SettingsRepository(context);
        assertTrue(settingsRepository.setOnboardingCompleted(true).isSuccess());
        assertTrue(settingsRepository.setAutoStepsEnabled(false).isSuccess());
    }

    @After
    public void stopTracking() {
        ActivityTrackingService.stop(context);
        assertTrue(settingsRepository.setAutoStepsEnabled(false).isSuccess());
    }

    @Test
    public void navHasTodayWorkoutsRunProgressSettingsAndNoStepsTab() {
        try (ActivityScenario<MainActivity> ignored =
                     ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.navToday)).check(matches(isDisplayed()));
            onView(withId(R.id.navWorkouts)).check(matches(isDisplayed()));
            onView(withId(R.id.navRun)).check(matches(isDisplayed()));
            onView(withId(R.id.navProgress)).check(matches(isDisplayed()));
            onView(withId(R.id.navSettings)).check(matches(isDisplayed()));
            onView(withContentDescription("Steps")).check(doesNotExist());
        }
    }

    @Test
    public void runTabOpensRunningScreen() {
        try (ActivityScenario<MainActivity> ignored =
                     ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.navRun)).perform(click());
            onView(withId(R.id.runningTimerValue)).check(matches(isDisplayed()));
            pressBack();
        }
    }

    @Test
    public void stepsDetailHasVisibleBackAndNoFloatingNav() {
        try (ActivityScenario<MainActivity> ignored =
                     ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.btnEditSteps)).perform(click());

            onView(withId(R.id.btnBack)).check(matches(isDisplayed()));
            onView(withId(R.id.floatingNav)).check(doesNotExist());
            onView(withId(R.id.stepsSelectedValue)).check(matches(isDisplayed()));

            onView(withId(R.id.btnBack)).perform(click());
            onView(withId(R.id.navToday)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void progressCanReachStepsDetail() {
        try (ActivityScenario<SummaryActivity> ignored =
                     ActivityScenario.launch(SummaryActivity.class)) {
            onView(withId(R.id.btnOpenStepsHistory)).perform(scrollTo(), click());

            onView(withId(R.id.stepsSelectedValue)).check(matches(isDisplayed()));
            onView(withId(R.id.btnBack)).check(matches(isDisplayed()));
            onView(withId(R.id.floatingNav)).check(doesNotExist());
            pressBack();
        }
    }

    @Test
    public void fiveNavLabelsFitAt360dpAnd13xFontScale() {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.fontScale = 1.3f;
        config.screenWidthDp = 360;
        config.screenHeightDp = 640;
        config.smallestScreenWidthDp = 360;

        Context scaled = context.createConfigurationContext(config);
        Context themed = new ContextThemeWrapper(scaled, R.style.Theme_Motionfit);
        int[] labelIds = {
                R.id.navTodayLabel, R.id.navRunLabel, R.id.navWorkoutsLabel,
                R.id.navProgressLabel, R.id.navSettingsLabel
        };
        final TextView[] labels = new TextView[labelIds.length];

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            FloatingNavView nav = new FloatingNavView(themed);
            int width = Math.round(320 * scaled.getResources().getDisplayMetrics().density);
            int height = Math.round(64 * scaled.getResources().getDisplayMetrics().density);
            nav.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            nav.layout(0, 0, width, height);
            for (int i = 0; i < labelIds.length; i++) {
                labels[i] = nav.findViewById(labelIds[i]);
            }
        });

        for (int i = 0; i < labels.length; i++) {
            TextView label = labels[i];
            assertNotNull("Nav label " + i + " must exist", label);
            Layout textLayout = label.getLayout();
            assertNotNull("Nav label " + i + " must have a text layout", textLayout);
            float available = label.getWidth()
                    - label.getPaddingLeft()
                    - label.getPaddingRight();
            float used = textLayout.getLineWidth(0);
            assertTrue("Nav label " + i + " must not clip at 360dp/1.3x (used "
                            + used + "px, available " + available + "px)",
                    used <= available + 1f);
        }
    }
}
