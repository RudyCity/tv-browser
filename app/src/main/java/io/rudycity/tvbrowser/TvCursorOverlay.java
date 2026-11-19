package io.rudycity.tvbrowser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Non-interactive dual-ring reticle for TV remote pointer mode. */
public final class TvCursorOverlay extends View {
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int x, y;

    public TvCursorOverlay(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setVisibility(GONE);
        float density = getResources().getDisplayMetrics().density;
        outline.setColor(RudyDialogUi.BG);
        outline.setStrokeWidth(4.5f * density);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeCap(Paint.Cap.ROUND);
        inner.setColor(RudyDialogUi.ACCENT);
        inner.setStrokeWidth(2f * density);
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeCap(Paint.Cap.ROUND);
        centerDot.setColor(RudyDialogUi.TEXT);
        centerDot.setStyle(Paint.Style.FILL);
    }

    public void place(int px, int py) {
        int margin = (int) (12f * getResources().getDisplayMetrics().density + 0.5f);
        x = CursorPolicy.clamp(px, getWidth(), margin);
        y = CursorPolicy.clamp(py, getHeight(), margin);
        invalidate();
    }

    public void recenter() { place(getWidth() / 2, getHeight() / 2); }
    public int cursorX() { return x; }
    public int cursorY() { return y; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getVisibility() != VISIBLE) return;
        float density = getResources().getDisplayMetrics().density;
        float radius = 14f * density;
        float tickInner = radius * 0.65f;
        float tickOuter = radius * 1.45f;

        canvas.drawCircle(x, y, radius, outline);
        canvas.drawLine(x - tickOuter, y, x - tickInner, y, outline);
        canvas.drawLine(x + tickInner, y, x + tickOuter, y, outline);
        canvas.drawLine(x, y - tickOuter, x, y - tickInner, outline);
        canvas.drawLine(x, y + tickInner, x, y + tickOuter, outline);

        canvas.drawCircle(x, y, radius, inner);
        canvas.drawLine(x - tickOuter, y, x - tickInner, y, inner);
        canvas.drawLine(x + tickInner, y, x + tickOuter, y, inner);
        canvas.drawLine(x, y - tickOuter, x, y - tickInner, inner);
        canvas.drawLine(x, y + tickInner, x, y + tickOuter, inner);

        canvas.drawCircle(x, y, 2.5f * density, centerDot);
    }
}
