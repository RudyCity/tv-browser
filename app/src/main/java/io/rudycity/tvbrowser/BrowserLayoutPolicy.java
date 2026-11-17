package io.rudycity.tvbrowser;

/** Pure Java form-factor decisions shared with Android UI, testable without an emulator. */
public final class BrowserLayoutPolicy {
    private static final int WIDE_TOOLBAR_MIN_DP = 840;

    private BrowserLayoutPolicy() { }

    /** Small phones, foldables and portrait tablets use two toolbar rows. */
    public static boolean isCompact(int availableWidthDp) {
        return availableWidthDp < WIDE_TOOLBAR_MIN_DP;
    }

    /** Android TV boxes may lack either a reliable Leanback or UI-mode declaration. */
    public static boolean isTelevision(boolean tvUiMode, boolean leanback,
                                       boolean televisionHardware, boolean touchscreen) {
        return tvUiMode || leanback || televisionHardware || !touchscreen;
    }
}
