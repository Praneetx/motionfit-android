package io.github.praneetx.motionfit.utils;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import io.github.praneetx.motionfit.R;

/**
 * Small motion helpers shared by the screens.
 *
 * <p>All motion here is optional: when the system "Remove animations" setting is
 * on (animator duration scale == 0), count-ups and slides are skipped and the
 * final value is shown immediately.</p>
 */
public final class UiAnimations {

    private UiAnimations() {
        // Utility class.
    }

    /** True when the user has turned system animations off. */
    public static boolean isReduceMotion(Context context) {
        try {
            float scale = Settings.Global.getFloat(
                    context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f);
            return scale == 0f;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Animates a number from its current value up to {@code to}. */
    public static void countUp(final TextView view, final int from, final int to) {
        if (view == null) {
            return;
        }
        if (isReduceMotion(view.getContext()) || from == to) {
            view.setText(FormatUtils.intText(to));
            return;
        }
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(800L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation ->
                view.setText(FormatUtils.intText((int) animation.getAnimatedValue())));
        animator.start();
    }

    /** Cross-fades to the next screen (~250ms), skipped for reduced motion. */
    public static void fadeTransition(Activity activity) {
        if (activity == null || isReduceMotion(activity)) {
            return;
        }
        activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    /** Quick press feedback: 0.97 scale while pressed. */
    public static void attachPressFeedback(View view) {
        if (view == null) {
            return;
        }
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90L).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(90L).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }
}
