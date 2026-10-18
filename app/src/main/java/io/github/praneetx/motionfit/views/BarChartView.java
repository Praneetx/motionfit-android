package io.github.praneetx.motionfit.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import io.github.praneetx.motionfit.R;

/**
 * A tiny, dependency-free bar chart drawn with the Canvas API.
 *
 * <p>The project intentionally avoids a chart library. This view draws one bar
 * per calendar day, an optional dashed goal line, small stubs for zero days and
 * a few axis labels. Colours are configurable so the same view can be lime on
 * the dark hero card, cyan for workout time and orange for workout count.</p>
 */
public class BarChartView extends View {

    private float[] values = new float[0];
    private String[] labels = new String[0];
    private float goalValue = -1f;

    /** Index of the "today" bar: drawn at full strength, others dimmed. -1 = none. */
    private int highlightIndex = -1;
    private float dimAlphaFactor = 0.5f;

    private int barColor;
    private int trackColor;
    private int labelColor;
    private int goalColor;
    private int emptyColor;

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
        barColor = context.getColor(R.color.primary);
        trackColor = context.getColor(R.color.canvas_soft);
        labelColor = context.getColor(R.color.body);
        goalColor = context.getColor(R.color.warning);
        emptyColor = context.getColor(R.color.mute);

        barPaint.setStyle(Paint.Style.FILL);
        baselinePaint.setStyle(Paint.Style.STROKE);
        goalPaint.setStyle(Paint.Style.STROKE);
    }

    /**
     * Sets the palette. Call before or after {@link #setData}; both re-draw.
     *
     * @param barColor   colour of positive bars
     * @param trackColor colour of the baseline and zero-day stubs
     * @param labelColor colour of the axis labels
     * @param goalColor  colour of the dashed goal line
     * @param emptyColor colour of the "no activity" message
     */
    public void configure(int barColor, int trackColor, int labelColor, int goalColor, int emptyColor) {
        this.barColor = barColor;
        this.trackColor = trackColor;
        this.labelColor = labelColor;
        this.goalColor = goalColor;
        this.emptyColor = emptyColor;
        invalidate();
    }

    /** Highlights one bar (today) and dims the rest, like the spec asks. */
    public void setHighlightIndex(int index) {
        this.highlightIndex = index;
        invalidate();
    }

    public void setDimAlphaFactor(float factor) {
        this.dimAlphaFactor = Math.max(0f, Math.min(1f, factor));
    }

    /**
     * @param values    one value per bar
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
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;

        baselinePaint.setColor(trackColor);
        baselinePaint.setStrokeWidth(Math.max(1f, density));

        labelPaint.setColor(labelColor);
        labelPaint.setTextSize(9f * scaledDensity);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        emptyPaint.setColor(emptyColor);
        emptyPaint.setTextSize(13f * scaledDensity);
        emptyPaint.setTextAlign(Paint.Align.CENTER);

        goalPaint.setColor(goalColor);
        goalPaint.setStrokeWidth(Math.max(1f, 1.5f * density));
        goalPaint.setPathEffect(new DashPathEffect(new float[]{6f * density, 4f * density}, 0f));

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

        canvas.drawLine(left, bottom, right, bottom, baselinePaint);

        float slot = chartWidth / count;
        float barWidth = Math.max(2f, slot * 0.62f);
        float radius = Math.min(4f * density, barWidth / 2f);
        float stubHeight = 3f * density;

        for (int i = 0; i < count; i++) {
            float value = values[i];
            float centerX = left + slot * i + slot / 2f;

            if (value <= 0f) {
                // Zero day: a small stub keeps the calendar readable.
                barPaint.setColor(trackColor);
                barRect.set(centerX - barWidth / 2f, bottom - stubHeight,
                        centerX + barWidth / 2f, bottom);
                canvas.drawRoundRect(barRect, radius, radius, barPaint);
                continue;
            }

            boolean dimmed = highlightIndex >= 0 && i != highlightIndex;
            barPaint.setColor(barColor);
            barPaint.setAlpha(dimmed ? (int) (255 * dimAlphaFactor) : 255);

            float barHeight = (value / max) * chartHeight;
            barRect.set(centerX - barWidth / 2f, bottom - barHeight,
                    centerX + barWidth / 2f, bottom);
            canvas.drawRoundRect(barRect, radius, radius, barPaint);
        }
        barPaint.setAlpha(255);

        if (goalValue > 0f) {
            float goalY = bottom - (goalValue / max) * chartHeight;
            canvas.drawLine(left, goalY, right, goalY, goalPaint);
        }

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
