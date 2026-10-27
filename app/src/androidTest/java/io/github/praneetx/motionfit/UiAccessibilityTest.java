package io.github.praneetx.motionfit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.text.Layout;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import io.github.praneetx.motionfit.activities.GoalsActivity;
import io.github.praneetx.motionfit.activities.SettingsActivity;
import io.github.praneetx.motionfit.activities.WorkoutEditActivity;
import io.github.praneetx.motionfit.views.BarChartView;
import io.github.praneetx.motionfit.views.StepsRingView;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Deterministic accessibility checks for the shared UI foundation.
 *
 * <p>TalkBack behaviour, font-scale screenshots and other manual checks remain
 * manual; these assertions cover values and semantics that can be verified
 * without a human.</p>
 */
@RunWith(AndroidJUnit4.class)
public class UiAccessibilityTest {

    @Test
    public void captionTokenMeetsContrastOnWhiteAndSage() {
        Context context = themedContext();
        int mute = context.getColor(R.color.mute);
        int white = context.getColor(R.color.white);
        int sage = context.getColor(R.color.canvas_soft);

        assertTrue("mute on white must reach 4.5:1",
                ColorUtils.calculateContrast(mute, white) >= 4.5);
        assertTrue("mute on sage must reach 4.5:1",
                ColorUtils.calculateContrast(mute, sage) >= 4.5);
    }

    @Test
    public void smallIconActionsMeetMinimumTouchTarget() {
        Context context = themedContext();
        View row = LayoutInflater.from(context)
                .inflate(R.layout.item_workout, null, false);
        ImageButton edit = row.findViewById(R.id.itemEdit);
        ImageButton delete = row.findViewById(R.id.itemDelete);

        assertNotNull(edit);
        assertNotNull(delete);
        int target = dp(context, 48);
        assertTrue("edit action width", edit.getLayoutParams().width >= target);
        assertTrue("edit action height", edit.getLayoutParams().height >= target);
        assertTrue("delete action width", delete.getLayoutParams().width >= target);
        assertTrue("delete action height", delete.getLayoutParams().height >= target);
    }

    @Test
    public void segmentButtonsMeetMinimumTouchTarget() {
        Context context = themedContext();
        View summary = LayoutInflater.from(context)
                .inflate(R.layout.activity_summary, null, false);
        MaterialButton tab = summary.findViewById(R.id.tabDaily);

        assertNotNull(tab);
        assertTrue("segment minimum height",
                tab.getMinHeight() >= dp(context, 48));
    }

    @Test
    public void navLabelsAutosizeAndEllipsize() {
        Context context = themedContext();
        FrameLayout root = new FrameLayout(context);
        LayoutInflater.from(context).inflate(R.layout.view_floating_nav, root, true);
        View workouts = root.findViewById(R.id.navWorkouts);
        assertNotNull(workouts);
        TextView label = workouts.findViewById(R.id.navWorkoutsLabel);

        assertNotNull(label);
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, label.getAutoSizeTextType());
        assertEquals(1, label.getMaxLines());
        assertEquals(TextUtils.TruncateAt.END, label.getEllipsize());
    }

    @Test
    public void barChartProvidesDataSpecificAccessibilitySummary() {
        BarChartView chart = new BarChartView(themedContext());
        chart.setData(new float[]{0f, 10f}, new String[]{"Mon", "Tue"}, 8f);

        CharSequence description = chart.getContentDescription();
        assertNotNull(description);
        assertTrue("chart summary must mention a data value",
                description.toString().contains("10"));
    }

    @Test
    public void stepsRingIsDecorativeToTalkBack() {
        StepsRingView ring = new StepsRingView(themedContext());

        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                ring.getImportantForAccessibility());
    }

    @Test
    public void settingsRootNoLongerExposesDeadBackControl() {
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> assertNull(
                    "Settings is a root destination and must not expose a hidden back control",
                    activity.findViewById(R.id.btnBack)));
        }
    }

    @Test
    public void settingsReminderLabelAndVersionAreAccessible() {
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                TextView reminderTitle = activity.findViewById(R.id.reminderSectionTitle);
                MaterialSwitch reminderSwitch = activity.findViewById(R.id.switchReminder);
                assertNotNull(reminderTitle);
                assertNotNull(reminderSwitch);
                assertEquals("The switch must use its visible label",
                        R.id.switchReminder, reminderTitle.getLabelFor());
                assertTrue("The switch must not duplicate its visible label for TalkBack",
                        TextUtils.isEmpty(reminderSwitch.getContentDescription()));

                TextView version = activity.findViewById(R.id.settingsVersion);
                assertNotNull(version);
                assertEquals(activity.getString(
                                R.string.settings_version_format, BuildConfig.VERSION_NAME),
                        version.getText().toString());
            });
        }
    }

    @Test
    public void goalInputsAreLabelledAndActionsStay48dp() {
        try (ActivityScenario<GoalsActivity> scenario =
                     ActivityScenario.launch(GoalsActivity.class)) {
            scenario.onActivity(activity -> {
                TextView stepLabel = activity.findViewById(R.id.stepGoalLabel);
                TextView minutesLabel = activity.findViewById(R.id.minutesGoalLabel);
                assertNotNull(stepLabel);
                assertNotNull(minutesLabel);
                assertEquals("Step goal label must be associated with its input",
                        R.id.stepGoalInput, stepLabel.getLabelFor());
                assertEquals("Minutes goal label must be associated with its input",
                        R.id.minutesGoalInput, minutesLabel.getLabelFor());

                assertTouchTarget(activity, R.id.stepGoalMinus);
                assertTouchTarget(activity, R.id.stepGoalPlus);
                assertTouchTarget(activity, R.id.minuteGoalMinus);
                assertTouchTarget(activity, R.id.minuteGoalPlus);
                assertTouchTarget(activity, R.id.btnBack);
            });
        }
    }

    @Test
    public void workoutInputsAreLabelledAndEditorKeeps48dpBackAction() {
        try (ActivityScenario<WorkoutEditActivity> scenario =
                     ActivityScenario.launch(WorkoutEditActivity.class)) {
            scenario.onActivity(activity -> {
                TextView typeLabel = activity.findViewById(R.id.typeLabel);
                TextView dateLabel = activity.findViewById(R.id.dateLabel);
                TextInputEditText duration = activity.findViewById(R.id.durationInput);
                TextInputEditText notes = activity.findViewById(R.id.notesInput);
                assertNotNull(typeLabel);
                assertNotNull(dateLabel);
                assertNotNull(duration);
                assertNotNull(notes);

                assertEquals("Workout type label must be associated with the spinner",
                        R.id.workoutTypeSpinner, typeLabel.getLabelFor());
                assertEquals("Date label must be associated with the date action",
                        R.id.btnPickDate, dateLabel.getLabelFor());
                assertEquals(activity.getString(R.string.workout_duration_label),
                        duration.getContentDescription());
                assertEquals(activity.getString(R.string.workout_notes_field),
                        notes.getContentDescription());
                assertTouchTarget(activity, R.id.btnBack);
            });
        }
    }

    @Test
    public void settingsReminderControlsStackAt360dpAnd13xFontScale() {
        Context scaled = scaledContext();
        FrameLayout parent = new FrameLayout(scaled);
        View root = LayoutInflater.from(scaled)
                .inflate(R.layout.activity_settings, parent, false);
        TextView time = root.findViewById(R.id.reminderTimeValue);
        MaterialButton changeTime = root.findViewById(R.id.btnChangeTime);
        assertNotNull(time);
        assertNotNull(changeTime);
        time.setText("08:00");

        measureAt360Dp(root, scaled, 640);

        assertTrue("Change time must stack below the time at 1.3x",
                changeTime.getTop() >= time.getBottom());
        assertTouchTarget(root, R.id.btnChangeTime);
        assertTextFits("Reminder time", time);
    }

    @Test
    public void goalMillionFitsBetweenSteppersAt360dpAnd13xFontScale() {
        Context scaled = scaledContext();
        FrameLayout parent = new FrameLayout(scaled);
        View root = LayoutInflater.from(scaled)
                .inflate(R.layout.activity_goals, parent, false);
        TextInputEditText stepGoal = root.findViewById(R.id.stepGoalInput);
        assertNotNull(stepGoal);
        stepGoal.setText("1000000");

        measureAt360Dp(root, scaled, 900);

        assertTouchTarget(root, R.id.stepGoalMinus);
        assertTouchTarget(root, R.id.stepGoalPlus);
        assertTextFits("Step goal value", stepGoal);
    }

    @Test
    public void goalsAndWorkoutEditorUseAdjustResize() {
        assertAdjustResize(GoalsActivity.class);
        assertAdjustResize(WorkoutEditActivity.class);
    }

    private static <T extends Activity> void assertAdjustResize(Class<T> activityClass) {
        try (ActivityScenario<T> scenario = ActivityScenario.launch(activityClass)) {
            scenario.onActivity(activity -> {
                int adjust = activity.getWindow().getAttributes().softInputMode
                        & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST;
                assertEquals(activityClass.getSimpleName(),
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, adjust);
            });
        }
    }

    private static void assertTouchTarget(Activity activity, int id) {
        assertTouchTargetView(id, activity.findViewById(id));
    }

    private static void assertTouchTarget(View root, int id) {
        assertTouchTargetView(id, root.findViewById(id));
    }

    private static void assertTouchTargetView(int id, View view) {
        assertNotNull("Missing touch target: " + id, view);
        int target = dp(view.getContext(), 48);
        int width = view.getWidth() > 0
                ? view.getWidth() : view.getLayoutParams().width;
        int height = view.getHeight() > 0
                ? view.getHeight() : view.getLayoutParams().height;
        assertTrue("Touch target width for " + id, width >= target);
        assertTrue("Touch target height for " + id, height >= target);
    }

    private static void assertTextFits(String label, TextView value) {
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

    private static void measureAt360Dp(View root, Context context, int heightDp) {
        int width = dp(context, 360);
        int height = dp(context, heightDp);
        root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private static Context scaledContext() {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.fontScale = 1.3f;
        config.screenWidthDp = 360;
        config.screenHeightDp = 640;
        config.smallestScreenWidthDp = 360;
        return new ContextThemeWrapper(
                base.createConfigurationContext(config), R.style.Theme_Motionfit);
    }

    private static Context themedContext() {
        return new ContextThemeWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                R.style.Theme_Motionfit);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
