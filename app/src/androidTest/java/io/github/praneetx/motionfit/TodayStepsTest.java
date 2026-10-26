package io.github.praneetx.motionfit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.activities.MainActivity;
import io.github.praneetx.motionfit.repositories.DataResetRepository;
import io.github.praneetx.motionfit.repositories.SettingsRepository;
import io.github.praneetx.motionfit.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Task 7 checks for the two main step surfaces: the Today hero must be an
 * obvious edit entry point, and the large step numbers must size themselves
 * instead of clipping at small widths or large font scales.
 */
@RunWith(AndroidJUnit4.class)
public class TodayStepsTest {

    private Context context;

    @Before
    public void clearData() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new DataResetRepository(context).clearAllData().isSuccess());
        // These checks exercise the dashboard after the one-time setup.
        assertTrue(new SettingsRepository(context).setOnboardingCompleted(true).isSuccess());
    }

    @Test
    public void editStepsButtonOpensStepsForSelectedDate() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View edit = activity.findViewById(R.id.btnEditSteps);
                assertNotNull("Today must expose an Edit steps affordance", edit);
                assertTrue("Edit steps must meet the 48dp target",
                        edit.getMinimumHeight() >= dp(context, 48));
            });

            onView(withId(R.id.btnEditSteps)).check(matches(isDisplayed())).perform(click());

            onView(withId(R.id.stepsSelectedDate)).check(matches(withText(
                    DateUtils.formatForDisplay(DateUtils.todayIso()))));
            onView(withId(R.id.stepsSelectedValue)).check(matches(isDisplayed()));
            pressBack();
        }
    }

    @Test
    public void emptyDayOffersLogStepsAction() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withText(R.string.dashboard_no_steps_yet))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
            onView(withText(R.string.action_log_steps))
                    .perform(scrollTo(), click());
            onView(withId(R.id.stepsSelectedValue)).check(matches(isDisplayed()));
            pressBack();
        }
    }

    @Test
    public void stepValuesUseAutosize() {
        Context themed = new ContextThemeWrapper(context, R.style.Theme_Motionfit);
        LayoutInflater inflater = LayoutInflater.from(themed);

        View today = inflater.inflate(R.layout.activity_main, null, false);
        TextView heroValue = today.findViewById(R.id.stepsValue);
        assertNotNull(heroValue);
        assertEquals("Today hero total must autosize",
                TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, heroValue.getAutoSizeTextType());
        assertTrue("Today hero autosize range must be usable",
                heroValue.getAutoSizeMaxTextSize() > heroValue.getAutoSizeMinTextSize());

        View steps = inflater.inflate(R.layout.activity_steps, null, false);
        TextView selectedValue = steps.findViewById(R.id.stepsSelectedValue);
        assertNotNull(selectedValue);
        assertEquals("Steps selected total must autosize",
                TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, selectedValue.getAutoSizeTextType());
        assertTrue("Steps selected autosize range must be usable",
                selectedValue.getAutoSizeMaxTextSize() > selectedValue.getAutoSizeMinTextSize());
    }

    @Test
    public void stepValuesFitAt360dpAnd13xFontScale() {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.fontScale = 1.3f;
        config.screenWidthDp = 360;
        config.screenHeightDp = 640;
        config.smallestScreenWidthDp = 360;

        Context scaled = context.createConfigurationContext(config);
        Context themed = new ContextThemeWrapper(scaled, R.style.Theme_Motionfit);
        LayoutInflater inflater = LayoutInflater.from(themed);
        int width = dp(scaled, 360);

        TextView heroValue = measureWorstCase(inflater, R.layout.activity_main,
                R.id.stepsValue, width);
        assertTextFits("Today hero", heroValue);

        TextView selectedValue = measureWorstCase(inflater, R.layout.activity_steps,
                R.id.stepsSelectedValue, width);
        assertTextFits("Steps selected value", selectedValue);
    }

    private static TextView measureWorstCase(LayoutInflater inflater, int layoutRes,
                                              int valueId, int width) {
        FrameLayout parent = new FrameLayout(inflater.getContext());
        View root = inflater.inflate(layoutRes, parent, false);
        TextView value = root.findViewById(valueId);
        assertNotNull(value);
        value.setText("9,999,999");
        root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        root.layout(0, 0, width, root.getMeasuredHeight());
        return value;
    }

    private static void assertTextFits(String label, TextView value) {
        assertTrue(label + " must be laid out", value.getWidth() > 0);
        Layout textLayout = value.getLayout();
        assertNotNull(label + " must have a text layout", textLayout);
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
