package io.github.praneetx.motionfit.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import io.github.praneetx.motionfit.R;
import io.github.praneetx.motionfit.models.Workout;
import io.github.praneetx.motionfit.utils.DateUtils;
import io.github.praneetx.motionfit.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for workout history rows.
 *
 * <p>The row actions (edit / delete) can be hidden for the dashboard "Recent"
 * list, which only needs to show type, day and duration.</p>
 */
public class WorkoutAdapter extends RecyclerView.Adapter<WorkoutAdapter.WorkoutViewHolder> {

    /** Callbacks the hosting Activity implements. */
    public interface Listener {
        void onWorkoutSelected(Workout workout);

        void onWorkoutEdit(Workout workout);

        void onWorkoutDelete(Workout workout);
    }

    private final List<Workout> items = new ArrayList<>();
    private final Listener listener;
    private boolean showActions = true;

    public WorkoutAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Workout> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    /** Hides the per-row edit/delete icons when {@code false} (dashboard Recent). */
    public void setShowActions(boolean showActions) {
        this.showActions = showActions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WorkoutViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_workout, parent, false);
        return new WorkoutViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WorkoutViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class WorkoutViewHolder extends RecyclerView.ViewHolder {

        private final TextView typeView;
        private final TextView metaView;
        private final TextView notesView;
        private final ImageButton editButton;
        private final ImageButton deleteButton;

        WorkoutViewHolder(@NonNull View itemView) {
            super(itemView);
            typeView = itemView.findViewById(R.id.itemWorkoutType);
            metaView = itemView.findViewById(R.id.itemWorkoutMeta);
            notesView = itemView.findViewById(R.id.itemWorkoutNotes);
            editButton = itemView.findViewById(R.id.itemEdit);
            deleteButton = itemView.findViewById(R.id.itemDelete);
        }

        void bind(final Workout workout) {
            typeView.setText(workout.getWorkoutType());

            String displayDate = DateUtils.formatForDisplay(workout.getWorkoutDate());
            Integer exactSeconds = workout.getDurationSeconds();
            if (exactSeconds != null) {
                String exactDuration = FormatUtils.clockText(exactSeconds * 1000L);
                Integer stepCount = workout.getStepCount();
                if (stepCount != null) {
                    metaView.setText(itemView.getContext().getString(
                            R.string.workout_row_meta_running,
                            displayDate,
                            exactDuration,
                            FormatUtils.intText(stepCount)));
                } else {
                    metaView.setText(itemView.getContext().getString(
                            R.string.workout_row_meta_running_no_steps,
                            displayDate,
                            exactDuration));
                }
            } else {
                String duration = itemView.getContext().getString(
                        R.string.workout_duration_value, workout.getDurationMinutes());
                metaView.setText(itemView.getContext().getString(
                        R.string.workout_row_meta, displayDate, duration));
            }

            editButton.setContentDescription(itemView.getContext().getString(
                    R.string.workout_edit_action_description,
                    workout.getWorkoutType(), displayDate));
            deleteButton.setContentDescription(itemView.getContext().getString(
                    R.string.workout_delete_action_description,
                    workout.getWorkoutType(), displayDate));

            String note = workout.getNotes();
            if (note == null || note.trim().isEmpty()) {
                notesView.setVisibility(View.GONE);
            } else {
                notesView.setText(note);
                notesView.setVisibility(View.VISIBLE);
            }

            int actionVisibility = showActions ? View.VISIBLE : View.GONE;
            editButton.setVisibility(actionVisibility);
            deleteButton.setVisibility(actionVisibility);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onWorkoutSelected(workout);
                }
            });
            editButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onWorkoutEdit(workout);
                }
            });
            deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onWorkoutDelete(workout);
                }
            });
        }
    }
}
