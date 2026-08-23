package io.github.jaffe2718.petprofile.ui;

import android.content.Context;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.google.android.material.textfield.TextInputEditText;

/**
 * A multi-line EditText that keeps its internal vertical scrollbar usable even when it is nested
 * inside a scrollable container (ScrollView / RecyclerView). It asks the ancestor view groups not
 * to intercept touches while its own content overflows, so the inner scroll takes priority.
 */
public class ScrollableEditText extends TextInputEditText {
    public ScrollableEditText(Context context) {
        super(context);
        init();
    }

    public ScrollableEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScrollableEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setMovementMethod(ScrollingMovementMethod.getInstance());
        setVerticalScrollBarEnabled(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            if (getParent() != null && canScrollVertically()) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
        }
        return super.onTouchEvent(event);
    }

    private boolean canScrollVertically() {
        return getLayout() != null && getLayout().getHeight() > getHeight();
    }
}
