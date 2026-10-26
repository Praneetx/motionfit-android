package io.github.praneetx.motionfit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
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

import io.github.praneetx.motionfit.activities.GoalsActivity;
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
        TextView label = workouts.findViewById(R.id.navLabel);

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

    private static Context themedContext() {
        return new ContextThemeWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                R.style.Theme_Motionfit);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
