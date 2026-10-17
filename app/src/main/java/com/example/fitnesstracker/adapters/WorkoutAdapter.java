package com.example.fitnesstracker.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitnesstracker.R;
import com.example.fitnesstracker.models.Workout;
import com.example.fitnesstracker.utils.DateUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for workout history rows.
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

            String duration = itemView.getContext().getString(
                    R.string.workout_duration_value, workout.getDurationMinutes());
            metaView.setText(DateUtils.formatForDisplay(workout.getWorkoutDate()) + "  -  " + duration);

            String note = workout.getNotes();
            if (note == null || note.trim().isEmpty()) {
                notesView.setVisibility(View.GONE);
            } else {
                notesView.setText(note);
                notesView.setVisibility(View.VISIBLE);
            }

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
