package io.github.jaffe2718.petprofile.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LineChartView extends View {
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<ChartPoint> points = new ArrayList<>();
    private String unit = "";
    private double minValue = 0;
    private double maxValue = 1;

    public LineChartView(Context context) {
        super(context);
        init();
    }

    public LineChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LineChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        axisPaint.setColor(Color.rgb(100, 100, 100));
        axisPaint.setStrokeWidth(3f);
        axisPaint.setTextSize(34f);
        linePaint.setColor(Color.rgb(46, 125, 50));
        linePaint.setStrokeWidth(5f);
        linePaint.setStyle(Paint.Style.STROKE);
        pointPaint.setColor(Color.rgb(255, 143, 0));
        labelPaint.setColor(Color.rgb(60, 60, 60));
        labelPaint.setTextSize(30f);
    }

    public void setData(List<ChartPoint> newPoints, String unit) {
        points.clear();
        if (newPoints != null) {
            points.addAll(newPoints);
        }
        this.unit = unit == null ? "" : unit;
        minValue = Double.MAX_VALUE;
        maxValue = -Double.MAX_VALUE;
        for (ChartPoint point : points) {
            minValue = Math.min(minValue, point.value);
            maxValue = Math.max(maxValue, point.value);
        }
        if (points.isEmpty()) {
            minValue = 0;
            maxValue = 1;
        } else if (minValue == maxValue) {
            maxValue = minValue + 1;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = 70f;
        float top = 40f;
        float right = getWidth() - 30f;
        float bottom = getHeight() - 80f;
        if (right <= left || bottom <= top) {
            return;
        }
        canvas.drawLine(left, top, left, bottom, axisPaint);
        canvas.drawLine(left, bottom, right, bottom, axisPaint);

        if (points.isEmpty()) {
            canvas.drawText("No data", left + 12, (top + bottom) / 2, labelPaint);
            return;
        }
        canvas.drawText(formatValue(maxValue) + (unit.isEmpty() ? "" : " " + unit), 4, top + 12, labelPaint);
        canvas.drawText(formatValue(minValue) + (unit.isEmpty() ? "" : " " + unit), 4, bottom, labelPaint);

        Path path = new Path();
        float startX = left;
        float step = points.size() == 1 ? 0 : (right - left) / (points.size() - 1);
        for (int i = 0; i < points.size(); i++) {
            ChartPoint point = points.get(i);
            float x = startX + step * i;
            float normalized = (float) ((point.value - minValue) / (maxValue - minValue));
            float y = bottom - normalized * (bottom - top);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
            canvas.drawCircle(x, y, 9f, pointPaint);
        }
        canvas.drawPath(path, linePaint);

        if (points.size() > 1) {
            SimpleDateFormat format = new SimpleDateFormat("MM-dd", Locale.getDefault());
            canvas.drawText(format.format(new Date(points.get(0).timestamp)), left, bottom + 45, labelPaint);
            canvas.drawText(format.format(new Date(points.get(points.size() - 1).timestamp)), right - 80, bottom + 45, labelPaint);
        }
    }

    private String formatValue(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.getDefault(), "%.2f", value);
    }
}
