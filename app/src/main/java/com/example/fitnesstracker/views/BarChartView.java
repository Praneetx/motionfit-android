package com.example.fitnesstracker.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.fitnesstracker.R;

/**
 * A tiny, dependency-free bar chart drawn with the Canvas API.
 *
 * <p>The project intentionally avoids a chart library: this custom View is small,
 * easy to explain in a viva, and needs no internet or extra dependency. It draws
 * one bar per calendar day, an optional dashed "goal" line and a few axis labels.</p>
 */
public class BarChartView extends View {

    private float[] values = new float[0];
    private String[] labels = new String[0];
    private float goalValue = -1f;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint goalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();

    public BarChartView(Context context) {
        super(context);
        init(context);
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = getResources().getDisplayMetrics().density;

        barPaint.setColor(context.getColor(R.color.primary));
        barPaint.setStyle(Paint.Style.FILL);

        goalPaint.setColor(context.getColor(R.color.warning));
        goalPaint.setStyle(Paint.Style.STROKE);
        goalPaint.setStrokeWidth(Math.max(1f, 1.5f * density));
        goalPaint.setPathEffect(new DashPathEffect(new float[]{6f * density, 4f * density}, 0f));

        baselinePaint.setColor(context.getColor(R.color.divider));
        baselinePaint.setStrokeWidth(Math.max(1f, density));

        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        labelPaint.setColor(context.getColor(R.color.text_secondary));
        labelPaint.setTextSize(9f * scaledDensity);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        emptyPaint.setColor(context.getColor(R.color.text_secondary));
        emptyPaint.setTextSize(13f * scaledDensity);
        emptyPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * @param values    one value per bar (steps)
     * @param labels    short label per bar (may be sparse)
     * @param goalValue optional goal line; pass a value &lt;= 0 to hide it
     */
    public void setData(float[] values, String[] labels, float goalValue) {
        this.values = values == null ? new float[0] : values.clone();
        this.labels = labels == null ? new String[0] : labels.clone();
        this.goalValue = goalValue;
        setContentDescription(getResources().getString(R.string.progress_chart_title));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        int defaultHeight = (int) (180 * getResources().getDisplayMetrics().density);
        int height = resolveSize(defaultHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;

        int count = values.length;
        if (count == 0) {
            canvas.drawText(getResources().getString(R.string.progress_empty),
                    getWidth() / 2f, getHeight() / 2f, emptyPaint);
            return;
        }

        float left = 8f * density;
        float right = getWidth() - 8f * density;
        float top = 10f * density;
        float bottom = getHeight() - 18f * density;
        float chartWidth = Math.max(1f, right - left);
        float chartHeight = Math.max(1f, bottom - top);

        float max = 1f;
        for (float value : values) {
            if (value > max) {
                max = value;
            }
        }
        if (goalValue > max) {
            max = goalValue;
        }

        // Baseline
        canvas.drawLine(left, bottom, right, bottom, baselinePaint);

        float slot = chartWidth / count;
        float barWidth = Math.max(2f, slot * 0.62f);
        float radius = Math.min(4f * density, barWidth / 2f);

        // Bars
        for (int i = 0; i < count; i++) {
            float value = values[i];
            if (value <= 0f) {
                continue;
            }
            float barHeight = (value / max) * chartHeight;
            float centerX = left + slot * i + slot / 2f;
            float barLeft = centerX - barWidth / 2f;
            barRect.set(barLeft, bottom - barHeight, barLeft + barWidth, bottom);
            canvas.drawRoundRect(barRect, radius, radius, barPaint);
        }

        // Optional dashed goal line
        if (goalValue > 0f) {
            float goalY = bottom - (goalValue / max) * chartHeight;
            canvas.drawLine(left, goalY, right, goalY, goalPaint);
        }

        // A few axis labels so long months stay readable
        int labelEvery = count <= 7 ? 1 : (int) Math.ceil(count / 6.0);
        for (int i = 0; i < count; i++) {
            if (i % labelEvery != 0 && i != count - 1) {
                continue;
            }
            String text = i < labels.length ? labels[i] : "";
            float centerX = left + slot * i + slot / 2f;
            canvas.drawText(text, centerX, bottom + 13f * density, labelPaint);
        }
    }
}
