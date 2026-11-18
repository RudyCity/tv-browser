package io.rudycity.tvbrowser;

/** Pure-Java, device-aware toolbar decisions. Website content owns the remaining screen. */
public final class BrowserLayoutPolicy {
    public static final int MOBILE = 0;
    public static final int TABLET = 1;
    public static final int TV_COMPACT = 2;
    public static final int TV_WIDE = 3;

    private static final int TABLET_MIN_DP = 600;
    private static final int TV_WIDE_MIN_DP = 840;

    private BrowserLayoutPolicy() {}

    /** One narrow row for phones; extra controls only where there is actual space. */
    public static int toolbarMode(int availableWidthDp, boolean tv) {
        int width = Math.max(0, availableWidthDp);
        if (tv) return width >= TV_WIDE_MIN_DP ? TV_WIDE : TV_COMPACT;
        return width >= TABLET_MIN_DP ? TABLET : MOBILE;
    }

    /** At least two tab cards on narrow screens, three on tablets/wide TVs. */
    public static int tabGridColumns(int availableWidthDp) {
        return availableWidthDp >= TABLET_MIN_DP ? 3 : 2;
    }

    public static boolean hasSecondRow(int mode) { return mode == TV_COMPACT; }
    public static boolean hasStatusFooter(int mode) { return mode == TV_COMPACT || mode == TV_WIDE; }

    /** Kept for backwards compatible TV sizing tests. Not a mobile toolbar rule. */
    public static boolean isCompact(int availableWidthDp) {
        return availableWidthDp < TV_WIDE_MIN_DP;
    }

    /** Android TV boxes may lack a reliable Leanback or UI-mode declaration. */
    public static boolean isTelevision(boolean tvUiMode, boolean leanback,
                                       boolean televisionHardware, boolean touchscreen) {
        return tvUiMode || leanback || televisionHardware || !touchscreen;
    }
}
