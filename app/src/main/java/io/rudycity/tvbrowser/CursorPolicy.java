package io.rudycity.tvbrowser;

/** Pure geometry for a D-pad-controlled cursor; easy to test without Android. */
public final class CursorPolicy {
    private CursorPolicy() {}

    public static int clamp(int value, int length, int margin) {
        if (length <= 0) return 0;
        int safeMargin = Math.max(0, Math.min(margin, length / 2));
        return Math.max(safeMargin, Math.min(value, length - safeMargin));
    }

    public static int move(int old, int direction, int step, int length, int margin) {
        if (direction < -1 || direction > 1) throw new IllegalArgumentException("Invalid direction");
        if (step < 0) throw new IllegalArgumentException("Invalid cursor step");
        long next = (long) old + (long) direction * (long) step;
        if (next > Integer.MAX_VALUE) next = Integer.MAX_VALUE;
        if (next < Integer.MIN_VALUE) next = Integer.MIN_VALUE;
        return clamp((int) next, length, margin);
    }
}
