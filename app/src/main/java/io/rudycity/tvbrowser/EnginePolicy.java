package io.rudycity.tvbrowser;

/** Pure policy, independently unit-testable without Android or a renderer. */
public final class EnginePolicy {
    public static final String AUTO = "auto";
    public static final String WEBVIEW = "webview";
    public static final String CHROMIUM = "chromium";
    // Modern Chromium for Android requires API 29+; older devices need a distinct maintained fork.
    public static final int MIN_MODERN_CHROMIUM_API = 29;

    private EnginePolicy() {}

    public static String sanitize(String preference) {
        if (WEBVIEW.equals(preference) || CHROMIUM.equals(preference)) return preference;
        return AUTO;
    }

    public static boolean tryChromium(int sdk, String preference, boolean providerPresent) {
        return sdk >= MIN_MODERN_CHROMIUM_API && providerPresent && !WEBVIEW.equals(sanitize(preference));
    }

    public static String fallbackReason(int sdk, String preference, boolean providerPresent) {
        if (WEBVIEW.equals(sanitize(preference))) return "WebView selected manually";
        if (sdk < MIN_MODERN_CHROMIUM_API) return "Chromium requires Android 10+; using legacy WebView";
        if (!providerPresent) return "Embedded Chromium is not packaged in this APK";
        return "Chromium was not selected";
    }
}
