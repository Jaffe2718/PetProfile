package io.github.jaffe2718.petprofile.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class ScanFrameView extends View {
    private static final float FRAME_RATIO = 0.72f;
    private static final float CORNER_DARK = 0.62f;

    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame = new RectF();
    private final Path cornerPath = new Path();

    public ScanFrameView(Context context) {
        this(context, null);
    }

    public ScanFrameView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScanFrameView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        dimPaint.setColor(0x99000000);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(dp(4f));
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);
        cornerPaint.setStrokeJoin(Paint.Join.ROUND);
        cornerPaint.setColor(0xFFF2F7F4);
        cornerPaint.setShadowLayer(dp(6f), 0f, 0f, 0x66000000);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        float size = Math.min(w, h) * FRAME_RATIO;
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;
        frame.set(left, top, left + size, top + size);

        // Dim outside the frame.
        canvas.drawRect(0f, 0f, w, frame.top, dimPaint);
        canvas.drawRect(0f, frame.bottom, w, h, dimPaint);
        canvas.drawRect(0f, frame.top, frame.left, frame.bottom, dimPaint);
        canvas.drawRect(frame.right, frame.top, w, frame.bottom, dimPaint);

        // Rounded corner brackets.
        drawCorner(canvas, cornerPath, false, false);
        drawCorner(canvas, cornerPath, true, false);
        drawCorner(canvas, cornerPath, true, true);
        drawCorner(canvas, cornerPath, false, true);
    }

    private void drawCorner(Canvas canvas, Path path, boolean right, boolean bottom) {
        float x = right ? frame.right : frame.left;
        float y = bottom ? frame.bottom : frame.top;
        float len = frame.width() * CORNER_DARK;
        path.reset();
        path.moveTo(x, bottom ? y - len : y + len);
        path.lineTo(x, y);
        path.lineTo(right ? x - len : x + len, y);
        canvas.drawPath(path, cornerPaint);
    }

    private float dp(float value) {
        return getResources().getDisplayMetrics().density * value;
    }
}
