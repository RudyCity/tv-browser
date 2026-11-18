package io.rudycity.tvbrowser;

/** Pure policy, independently unit-testable without Android or a renderer. */
public final class EnginePolicy {
    public static final String AUTO = "auto";
    public static final String WEBVIEW = "webview";
    public static final String CHROMIUM = "chromium";
    public static final String GECKO = "gecko";
    // GeckoView stable requires Android 8.0+; the Gecko flavor is API 26+.
    public static final int MIN_GECKO_API = 26;
    // Modern Chromium for Android requires API 29+; older devices need a distinct maintained fork.
    public static final int MIN_MODERN_CHROMIUM_API = 29;

    private EnginePolicy() {}

    public static String sanitize(String preference) {
        if (WEBVIEW.equals(preference) || CHROMIUM.equals(preference) || GECKO.equals(preference)) return preference;
        return AUTO;
    }

    public static boolean tryGecko(int sdk, String preference, boolean providerPresent) {
        String safe = sanitize(preference);
        return sdk >= MIN_GECKO_API && providerPresent
                && (AUTO.equals(safe) || GECKO.equals(safe));
    }

    public static boolean tryChromium(int sdk, String preference, boolean providerPresent) {
        return sdk >= MIN_MODERN_CHROMIUM_API && providerPresent && !WEBVIEW.equals(sanitize(preference));
    }

    public static String fallbackReason(int sdk, String preference, boolean providerPresent) {
        if (WEBVIEW.equals(sanitize(preference))) return "WebView selected manually";
        if (GECKO.equals(sanitize(preference))) {
            return sdk < MIN_GECKO_API ? "GeckoView requires Android 8.0+; using WebView"
                    : "GeckoView is not packaged in this APK; using WebView";
        }
        if (sdk < MIN_MODERN_CHROMIUM_API) return "Chromium requires Android 10+; using legacy WebView";
        if (!providerPresent) return "Embedded Chromium is not packaged in this APK";
        return "Chromium was not selected";
    }
}
