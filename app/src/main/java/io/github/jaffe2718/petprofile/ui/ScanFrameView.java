package io.github.jaffe2718.petprofile.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class ScanFrameView extends View {
    /** golden section, used for the framing of the whole screen. */
    public static final float GOLDEN = 0.618f;
    private static final float FRAME_RATIO = 0.72f;
    private static final float CORNER_DARK = 0.62f;

    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame = new RectF();
    private final Path cornerPath = new Path();
    /** View-local centre set by the activity, or negative to use the golden section of this view. */
    private float frameCenterY = -1f;

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

    /**
     * The scan frame for a view of this size: square, centred horizontally, and placed so the space
     * above its centre and the space below it stand in the golden ratio (0.618 : 1). That reads
     * better than a plain centre and leaves the larger share of the screen for the controls.
     *
     * <p>Shared with the activity, which positions the controls against the same rectangle.
     */
    public static RectF frameBounds(int width, int height) {
        return frameBounds(width, height, height * GOLDEN / (1f + GOLDEN));
    }

    /**
     * Same, with an explicit centre. The activity passes a centre derived from the whole screen so
     * the framing stays correct even when the window does not reach the top of the display (some
     * vendors inset it, leaving a black bar that must not count towards the composition).
     */
    public static RectF frameBounds(int width, int height, float centerY) {
        RectF bounds = new RectF();
        if (width <= 0 || height <= 0) {
            return bounds;
        }
        float size = Math.min(width, height) * FRAME_RATIO;
        float left = (width - size) / 2f;
        float top = centerY - size / 2f;
        // Keep the frame fully on screen on unusual aspect ratios.
        float margin = Math.min(width, height) * 0.02f;
        top = Math.max(margin, Math.min(top, height - size - margin));
        bounds.set(left, top, left + size, top + size);
        return bounds;
    }

    /** Overrides the vertical centre (view-local) computed by the activity; ignored when negative. */
    public void setFrameCenterY(float centerY) {
        this.frameCenterY = centerY;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        frame.set(frameCenterY > 0f ? frameBounds(w, h, frameCenterY) : frameBounds(w, h));

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
