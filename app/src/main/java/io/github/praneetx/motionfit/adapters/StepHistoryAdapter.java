package io.github.praneetx.motionfit.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import io.github.praneetx.motionfit.R;
import io.github.praneetx.motionfit.models.DailySteps;
import io.github.praneetx.motionfit.utils.DateUtils;
import io.github.praneetx.motionfit.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for the historical list of daily step totals.
 */
public class StepHistoryAdapter extends RecyclerView.Adapter<StepHistoryAdapter.StepViewHolder> {

    public interface Listener {
        void onStepRecordSelected(DailySteps record);
    }

    private final List<DailySteps> items = new ArrayList<>();
    private final Listener listener;

    public StepHistoryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<DailySteps> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StepViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_step_history, parent, false);
        return new StepViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StepViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class StepViewHolder extends RecyclerView.ViewHolder {

        private final TextView dateView;
        private final TextView sourceView;
        private final TextView stepsView;

        StepViewHolder(@NonNull View itemView) {
            super(itemView);
            dateView = itemView.findViewById(R.id.stepItemDate);
            sourceView = itemView.findViewById(R.id.stepItemSource);
            stepsView = itemView.findViewById(R.id.stepItemSteps);
        }

        void bind(final DailySteps record) {
            dateView.setText(DateUtils.formatForDisplay(record.getActivityDate()));
            int sourceRes = DailySteps.SOURCE_SENSOR.equals(record.getSource())
                    ? R.string.steps_source_sensor
                    : R.string.steps_source_manual;
            sourceView.setText(sourceRes);
            stepsView.setText(FormatUtils.intText(record.getSteps()));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onStepRecordSelected(record);
                }
            });
        }
    }
}
