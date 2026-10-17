package com.example.fitnesstracker.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;

import com.example.fitnesstracker.R;

/**
 * The Wise hero steps ring.
 *
 * <p>Extends {@link ProgressBar} on purpose: the dashboard field
 * {@code stepsProgress} is declared as a {@code ProgressBar} and the view id is
 * preserved, so no Activity type changes are needed for the ring.</p>
 *
 * <p>Draws a 10dp rounded stroke that starts at 12 o'clock, an ink-deep track and
 * a lime-green progress arc. {@link #setProgressAnimated(int)} fills once on
 * screen load and is skipped when the user enabled "Remove animations".</p>
 */
public class StepsRingView extends ProgressBar {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private float strokeWidth;
    /** -1 means "use getProgress()"; otherwise an in-flight animated fraction. */
    private float animatedProgress = -1f;
    private ValueAnimator animator;

    public StepsRingView(Context context) {
        super(context);
        init(context);
    }

    public StepsRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public StepsRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public StepsRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        strokeWidth = getResources().getDimension(R.dimen.ring_stroke);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(context.getColor(R.color.ink_deep));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(context.getColor(R.color.primary));

        setMax(100);
        setIndeterminate(false);
    }

    /**
     * Animates the ring from empty to {@code progress} (0..100) over ~800ms.
     * Honours the system "Remove animations" setting.
     */
    public void setProgressAnimated(int progress) {
        cancelAnimation();
        if (progress <= 0 || isReduceMotion()) {
            setProgress(progress);
            animatedProgress = -1f;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, progress);
        animator.setDuration(800L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            animatedProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void cancelAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private boolean isReduceMotion() {
        try {
            float scale = Settings.Global.getFloat(
                    getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f);
            return scale == 0f;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0f) {
            return;
        }
        float inset = strokeWidth / 2f + getPaddingLeft() * 0f;
        float radius = size / 2f - inset;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // Track: full circle in ink_deep.
        canvas.drawCircle(cx, cy, radius, trackPaint);

        // Progress: from 12 o'clock, clockwise.
        float fraction;
        if (animatedProgress >= 0f) {
            fraction = animatedProgress / Math.max(1, getMax());
        } else {
            fraction = getProgress() / (float) Math.max(1, getMax());
        }
        fraction = Math.max(0f, Math.min(1f, fraction));
        if (fraction > 0.001f) {
            canvas.drawArc(arcBounds, -90f, 360f * fraction, false, progressPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAnimation();
        super.onDetachedFromWindow();
    }
}
