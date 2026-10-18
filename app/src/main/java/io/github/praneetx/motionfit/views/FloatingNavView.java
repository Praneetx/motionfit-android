package io.github.praneetx.motionfit.views;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import io.github.praneetx.motionfit.R;
import io.github.praneetx.motionfit.activities.MainActivity;
import io.github.praneetx.motionfit.activities.SettingsActivity;
import io.github.praneetx.motionfit.activities.StepsActivity;
import io.github.praneetx.motionfit.activities.SummaryActivity;
import io.github.praneetx.motionfit.activities.WorkoutListActivity;
import io.github.praneetx.motionfit.utils.UiAnimations;

/**
 * The floating Wise pill navigation.
 *
 * <p>An ink pill floats above the content (20dp side margins, 16dp bottom margin
 * plus the system navigation inset) and holds five destinations. The active
 * destination sits inside a lime pill highlight that slides between items.</p>
 *
 * <p>Implemented as a custom row (not BottomNavigationView) so the floating,
 * fully-rounded shape and the sliding highlight are exact.</p>
 */
public class FloatingNavView extends FrameLayout {

    public static final int DEST_TODAY = R.id.navToday;
    public static final int DEST_STEPS = R.id.navSteps;
    public static final int DEST_WORKOUTS = R.id.navWorkouts;
    public static final int DEST_PROGRESS = R.id.navProgress;
    public static final int DEST_SETTINGS = R.id.navSettings;

    private static final int[] DESTINATIONS = {
            DEST_TODAY, DEST_STEPS, DEST_WORKOUTS, DEST_PROGRESS, DEST_SETTINGS
    };

    private View highlight;
    private LinearLayout itemsRow;
    private int activeDestination = DEST_TODAY;
    private boolean layoutReady;

    public FloatingNavView(Context context) {
        super(context);
        init(context);
    }

    public FloatingNavView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FloatingNavView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setBackgroundResource(R.drawable.bg_nav_pill);
        setElevation(0f);
        setClipToPadding(false);
        int pad = getResources().getDimensionPixelSize(R.dimen.spacing_xs);
        setPadding(pad, pad, pad, pad);

        LayoutInflater.from(context).inflate(R.layout.view_floating_nav, this, true);

        highlight = findViewById(R.id.navHighlight);
        itemsRow = findViewById(R.id.navItems);

        for (int destination : DESTINATIONS) {
            View item = findViewById(destination);
            if (item != null) {
                item.setOnClickListener(v -> navigate(destination));
            }
        }

        // System navigation inset: added below the pill's 16dp margin.
        // The app is not edge-to-edge, so this resolves to 0 here and never
        // double-pads, but it stays correct if edge-to-edge is enabled later.
        ViewCompat.setOnApplyWindowInsetsListener(this, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
                int baseBottom = getResources().getDimensionPixelSize(R.dimen.nav_margin_bottom);
                margins.bottomMargin = baseBottom + bars.bottom;
                view.setLayoutParams(margins);
            }
            return windowInsets;
        });

        setActiveDestination(DEST_TODAY, false);
    }

    /** Marks a destination active and slides the lime highlight there. */
    public void setActiveDestination(int destination) {
        setActiveDestination(destination, true);
    }

    public void setActiveDestination(int destination, boolean animate) {
        activeDestination = destination;
        if (highlight == null || itemsRow == null) {
            return;
        }
        updateItemAppearance();
        highlight.post(() -> moveHighlight(destination, animate));
    }

    private void updateItemAppearance() {
        for (int destination : DESTINATIONS) {
            View item = findViewById(destination);
            if (item == null) {
                continue;
            }
            boolean active = destination == activeDestination;
            item.setSelected(active);

            ImageView icon = item.findViewById(R.id.navIcon);
            TextView label = item.findViewById(R.id.navLabel);
            int color = getContext().getColor(active ? R.color.ink : R.color.nav_inactive);
            if (icon != null) {
                icon.setImageTintList(ColorStateList.valueOf(color));
                icon.setImageTintMode(PorterDuff.Mode.SRC_IN);
            }
            if (label != null) {
                label.setTextColor(color);
            }
        }
    }

    private void moveHighlight(int destination, boolean animate) {
        View item = findViewById(destination);
        if (item == null || item.getWidth() == 0) {
            layoutReady = false;
            return;
        }

        ViewGroup.LayoutParams params = highlight.getLayoutParams();
        params.width = item.getWidth();
        highlight.setLayoutParams(params);

        float targetX = item.getLeft();
        highlight.setVisibility(View.VISIBLE);

        if (!animate || !layoutReady || UiAnimations.isReduceMotion(getContext())) {
            highlight.setTranslationX(targetX);
        } else {
            highlight.animate()
                    .translationX(targetX)
                    .setDuration(220L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
        layoutReady = true;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0) {
            // Children are laid out after onSizeChanged; post so item widths exist.
            post(() -> moveHighlight(activeDestination, false));
        }
    }

    /**
     * Convenience for Activities: finds the nav in the current layout and marks
     * the given destination active. Safe to call when a pushed screen has no nav.
     */
    public static void bind(android.app.Activity activity, int destination) {
        if (activity == null) {
            return;
        }
        View nav = activity.findViewById(R.id.floatingNav);
        if (nav instanceof FloatingNavView) {
            ((FloatingNavView) nav).setActiveDestination(destination);
        }
    }

    private void navigate(int destination) {
        if (destination == activeDestination) {
            return;
        }
        Context context = getContext();
        Class<?> target;
        if (destination == DEST_STEPS) {
            target = StepsActivity.class;
        } else if (destination == DEST_WORKOUTS) {
            target = WorkoutListActivity.class;
        } else if (destination == DEST_PROGRESS) {
            target = SummaryActivity.class;
        } else if (destination == DEST_SETTINGS) {
            target = SettingsActivity.class;
        } else {
            target = MainActivity.class;
        }

        Intent intent = new Intent(context, target);
        // Bring an existing instance forward instead of stacking duplicates.
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        context.startActivity(intent);

        if (context instanceof android.app.Activity) {
            UiAnimations.fadeTransition((android.app.Activity) context);
        }
    }
}
