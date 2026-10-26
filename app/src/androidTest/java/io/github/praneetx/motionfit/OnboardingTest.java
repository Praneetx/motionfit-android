package io.github.praneetx.motionfit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.activities.MainActivity;
import io.github.praneetx.motionfit.activities.OnboardingActivity;
import io.github.praneetx.motionfit.models.FitnessGoal;
import io.github.praneetx.motionfit.repositories.DataResetRepository;
import io.github.praneetx.motionfit.repositories.GoalRepository;
import io.github.praneetx.motionfit.repositories.SettingsRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Task 11 first-run checks.
 *
 * <p>The permission dialogs themselves are intentionally not automated: they
 * depend on system UI and are reported as manual/NOT RUN. These tests cover the
 * skippable flow, persistence and the no-data-loss guarantee.</p>
 */
@RunWith(AndroidJUnit4.class)
public class OnboardingTest {

    private Context context;
    private SettingsRepository settingsRepository;
    private GoalRepository goalRepository;

    @Before
    public void resetData() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new DataResetRepository(context).clearAllData().isSuccess());
        settingsRepository = new SettingsRepository(context);
        goalRepository = new GoalRepository(context);
    }

    @Test
    public void firstRunShowsExplanationThenSkipReachesMain() {
        assertFalse("A fresh install must start with onboarding incomplete",
                settingsRepository.isOnboardingCompleted());

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.onboardingTitle)).check(matches(isDisplayed()));
            onView(withText(R.string.onboarding_welcome_title)).check(matches(isDisplayed()));
            onView(withId(R.id.btnSkipOnboarding)).perform(click());
            onView(withId(R.id.todayTitle)).check(matches(isDisplayed()));
        }

        assertTrue("Skipping must persist the completion flag",
                settingsRepository.isOnboardingCompleted());
    }

    @Test
    public void existingV1GoalValuesAreShownAndSkipDoesNotResetThem() {
        assertTrue(goalRepository.saveGoal(4321, 17).isSuccess());

        try (ActivityScenario<OnboardingActivity> scenario =
                     ActivityScenario.launch(OnboardingActivity.class)) {
            onView(withId(R.id.onboardingStepGoalInput)).check(matches(withText("4321")));
            onView(withId(R.id.onboardingMinutesGoalInput)).check(matches(withText("17")));
            onView(withId(R.id.btnSkipOnboarding)).perform(click());
        }

        FitnessGoal goal = goalRepository.getGoal();
        assertEquals("Existing V1 goals must survive onboarding", 4321, goal.getDailyStepGoal());
        assertEquals("Existing V1 goals must survive onboarding", 17,
                goal.getDailyWorkoutMinutesGoal());
        assertTrue(settingsRepository.isOnboardingCompleted());
    }
}
