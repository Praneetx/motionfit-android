package io.github.praneetx.motionfit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.activities.RunningActivity;
import io.github.praneetx.motionfit.models.Workout;
import io.github.praneetx.motionfit.repositories.DataResetRepository;
import io.github.praneetx.motionfit.repositories.RunningSessionRepository;
import io.github.praneetx.motionfit.repositories.SettingsRepository;
import io.github.praneetx.motionfit.repositories.WorkoutRepository;
import io.github.praneetx.motionfit.services.ActivityTrackingService;
import io.github.praneetx.motionfit.utils.DateUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Task 13 Running screen checks.
 *
 * <p>The emulator has no step counter, so these tests exercise the legal
 * timer-only path. Real sensor readings, notification appearance, process death
 * and reboot recovery remain manual/physical-device rows.</p>
 */
@RunWith(AndroidJUnit4.class)
public class RunningScreenTest {

    private Context context;
    private SettingsRepository settingsRepository;
    private RunningSessionRepository sessionRepository;
    private WorkoutRepository workoutRepository;

    @Before
    public void resetData() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new DataResetRepository(context).clearAllData().isSuccess());
        settingsRepository = new SettingsRepository(context);
        sessionRepository = new RunningSessionRepository(context);
        workoutRepository = new WorkoutRepository(context);
        assertTrue(settingsRepository.setOnboardingCompleted(true).isSuccess());
        assertTrue(settingsRepository.setAutoStepsEnabled(false).isSuccess());
    }

    @After
    public void stopTracking() {
        ActivityTrackingService.stop(context);
        assertTrue(settingsRepository.setAutoStepsEnabled(false).isSuccess());
    }

    @Test
    public void readyScreenShowsTimerStartPlaceholdersAndStatusCard() throws Exception {
        try (ActivityScenario<RunningActivity> scenario =
                     ActivityScenario.launch(RunningActivity.class)) {
            waitForEnabled(scenario, R.id.btnStartRun);

            onView(withId(R.id.runningTimerValue))
                    .check(matches(withText("00:00")));
            onView(withId(R.id.btnStartRun)).check(matches(isDisplayed()));
            onView(withId(R.id.runningStepValue))
                    .check(matches(withText(R.string.running_dash)));
            onView(withId(R.id.runningCadenceValue))
                    .check(matches(withText(R.string.running_dash)));
            onView(withId(R.id.runningStatusCard))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));

            // Explanatory copy may mention why these metrics do not exist, but
            // there must be no actual distance, pace or calorie field.
            onView(withText("Distance")).check(doesNotExist());
            onView(withText("Pace")).check(doesNotExist());
            onView(withText("Calories")).check(doesNotExist());
        }
    }

    @Test
    public void stopConfirmationSavesExactlyOneRunningWorkout() throws Exception {
        try (ActivityScenario<RunningActivity> scenario =
                     ActivityScenario.launch(RunningActivity.class)) {
            waitForEnabled(scenario, R.id.btnStartRun);
            onView(withId(R.id.btnStartRun)).perform(scrollTo(), click());
            waitForEnabled(scenario, R.id.btnPauseResumeRun);

            onView(withId(R.id.btnStopRun)).perform(scrollTo(), click());
            onView(withText(R.string.running_stop_confirm_title))
                    .check(matches(isDisplayed()));
            onView(withText(R.string.running_confirm_button)).perform(click());
            waitForDisplayed(scenario, R.id.runningSummaryCard);

            onView(withText(R.string.running_saved_title))
                    .check(matches(isDisplayed()));
            List<Workout> workouts = workoutRepository.getForDate(DateUtils.todayIso());
            assertEquals("Stop must save exactly one Running workout", 1, workouts.size());
            assertEquals("Running", workouts.get(0).getWorkoutType());
            assertNull("The durable session must be removed after saving",
                    sessionRepository.getSession());
        }
    }

    @Test
    public void interruptedSessionShowsRecoveryActionsInsteadOfAutoResume() throws Exception {
        long startedElapsed = Math.max(0L, SystemClock.elapsedRealtime() - 60_000L);
        assertTrue(sessionRepository.start(
                DateUtils.nowTimestamp(), startedElapsed, -1, 200L).isSuccess());
        assertTrue(sessionRepository.update(startedElapsed + 30_000L, 20, 220L).isSuccess());
        assertTrue(sessionRepository.markInterruptedAfterReboot().isSuccess());

        try (ActivityScenario<RunningActivity> scenario =
                     ActivityScenario.launch(RunningActivity.class)) {
            waitForEnabled(scenario, R.id.btnRecoveryResume);

            onView(withId(R.id.runningRecoveryCard)).check(matches(isDisplayed()));
            onView(withId(R.id.btnRecoveryResume))
                    .perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.btnRecoveryFinish))
                    .perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.btnRecoveryDiscard))
                    .perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.btnStartRun)).check(matches(
                    androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility(
                            androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE)));
        }
    }

    @Test
    public void timerAndMetricsFitAt360dpAnd13xFontScale() {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.fontScale = 1.3f;
        config.screenWidthDp = 360;
        config.screenHeightDp = 640;
        config.smallestScreenWidthDp = 360;

        Context scaled = context.createConfigurationContext(config);
        Context themed = new ContextThemeWrapper(scaled, R.style.Theme_Motionfit);
        final View[] rootHolder = new View[1];

        // MaterialButton starts state animators, so inflate and measure on the
        // main looper even though the assertions are plain layout checks.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            LayoutInflater inflater = LayoutInflater.from(themed);
            FrameLayout parent = new FrameLayout(themed);
            View root = inflater.inflate(R.layout.activity_running, parent, false);
            TextView timer = root.findViewById(R.id.runningTimerValue);
            TextView steps = root.findViewById(R.id.runningStepValue);
            TextView cadence = root.findViewById(R.id.runningCadenceValue);
            timer.setText("00:00");
            steps.setText("9,999");
            cadence.setText("999");

            int width = Math.round(360 * scaled.getResources().getDisplayMetrics().density);
            root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            root.layout(0, 0, width, root.getMeasuredHeight());
            rootHolder[0] = root;
        });

        View root = rootHolder[0];
        assertNotNull(root);
        assertTextFits("Running timer", root.findViewById(R.id.runningTimerValue));
        assertTextFits("Running steps", root.findViewById(R.id.runningStepValue));
        assertTextFits("Running cadence", root.findViewById(R.id.runningCadenceValue));
    }

    private static void assertTextFits(String label, TextView value) {
        assertTrue(label + " must be laid out", value.getWidth() > 0);
        Layout textLayout = value.getLayout();
        assertNotNull(label + " must have a text layout", textLayout);
        float available = value.getWidth()
                - value.getPaddingLeft()
                - value.getPaddingRight();
        float used = textLayout.getLineWidth(0);
        assertTrue(label + " must not clip at 360dp/1.3x (used "
                        + used + "px, available " + available + "px)",
                used <= available + 1f);
    }

    private void waitForEnabled(ActivityScenario<RunningActivity> scenario, int viewId)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            AtomicBoolean enabled = new AtomicBoolean(false);
            scenario.onActivity(activity -> {
                View view = activity.findViewById(viewId);
                enabled.set(view != null
                        && view.getVisibility() == View.VISIBLE
                        && view.isEnabled());
            });
            if (enabled.get()) {
                return;
            }
            Thread.sleep(50L);
        }
        fail("View " + viewId + " did not become enabled in time");
    }

    private void waitForDisplayed(ActivityScenario<RunningActivity> scenario, int viewId)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            AtomicBoolean displayed = new AtomicBoolean(false);
            scenario.onActivity(activity -> {
                View view = activity.findViewById(viewId);
                displayed.set(view != null && view.getVisibility() == View.VISIBLE);
            });
            if (displayed.get()) {
                return;
            }
            Thread.sleep(50L);
        }
        fail("View " + viewId + " did not become visible in time");
    }
}
