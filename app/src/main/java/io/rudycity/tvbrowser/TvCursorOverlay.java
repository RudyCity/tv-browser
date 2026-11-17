package io.rudycity.tvbrowser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Non-interactive crosshair overlay for remote-control pointer mode. */
public final class TvCursorOverlay extends View {
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int x, y;

    public TvCursorOverlay(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setVisibility(GONE);
        float density = getResources().getDisplayMetrics().density;
        outline.setColor(Color.BLACK);
        outline.setStrokeWidth(4f * density);
        outline.setStyle(Paint.Style.STROKE);
        inner.setColor(Color.rgb(90, 202, 255));
        inner.setStrokeWidth(2f * density);
        inner.setStyle(Paint.Style.STROKE);
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
        float radius = 14f * getResources().getDisplayMetrics().density;
        canvas.drawCircle(x, y, radius, outline);
        canvas.drawCircle(x, y, radius, inner);
        canvas.drawLine(x - radius * 1.5f, y, x + radius * 1.5f, y, outline);
        canvas.drawLine(x, y - radius * 1.5f, x, y + radius * 1.5f, outline);
        canvas.drawLine(x - radius * 1.5f, y, x + radius * 1.5f, y, inner);
        canvas.drawLine(x, y - radius * 1.5f, x, y + radius * 1.5f, inner);
    }
}
