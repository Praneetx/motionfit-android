package io.github.praneetx.motionfit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.adapters.StepHistoryAdapter;
import io.github.praneetx.motionfit.models.DailySteps;
import io.github.praneetx.motionfit.utils.DateUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * Task 15 checks that step sources are readable text, not a colour-only hint.
 */
@RunWith(AndroidJUnit4.class)
public class StepHistoryAdapterTest {

    @Test
    public void bindsManualAutomaticAndAdjustedSourceLabels() {
        Context context = new ContextThemeWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                R.style.Theme_Motionfit);
        FrameLayout parent = new FrameLayout(context);
        StepHistoryAdapter adapter = new StepHistoryAdapter(null);
        String date = DateUtils.todayIso();

        DailySteps manual = new DailySteps(date, 111, DailySteps.SOURCE_MANUAL);
        DailySteps automatic = new DailySteps(date, 1234, DailySteps.SOURCE_SENSOR);
        DailySteps adjusted = new DailySteps(date, 2222, DailySteps.SOURCE_SENSOR_ADJUSTED);

        final String[] sources = new String[3];
        final String[] steps = new String[3];
        final String[] descriptions = new String[3];

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            adapter.setItems(Arrays.asList(manual, automatic, adjusted));
            @SuppressWarnings({"rawtypes", "unchecked"})
            RecyclerView.Adapter rawAdapter = adapter;
            for (int i = 0; i < 3; i++) {
                RecyclerView.ViewHolder holder = adapter.onCreateViewHolder(parent, 0);
                rawAdapter.onBindViewHolder(holder, i);
                TextView sourceView = holder.itemView.findViewById(R.id.stepItemSource);
                TextView stepsView = holder.itemView.findViewById(R.id.stepItemSteps);
                assertNotNull(sourceView);
                assertNotNull(stepsView);
                sources[i] = sourceView.getText().toString();
                steps[i] = stepsView.getText().toString();
                descriptions[i] = String.valueOf(holder.itemView.getContentDescription());
            }
        });

        assertEquals("Manual", sources[0]);
        assertEquals("Automatic", sources[1]);
        assertEquals("Adjusted", sources[2]);
        assertEquals("111", steps[0]);
        assertEquals("1,234", steps[1]);
        assertEquals("2,222", steps[2]);
        assertTrue("Row TalkBack text must name the automatic source",
                descriptions[1].contains("Automatic"));
        assertTrue("Row TalkBack text must include the step total",
                descriptions[1].contains("1,234"));
    }
}
