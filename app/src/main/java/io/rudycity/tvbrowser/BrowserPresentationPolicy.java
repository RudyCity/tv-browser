package io.rudycity.tvbrowser;

/**
 * API-19-safe, testable presentation policy. Exactly one toolbar implementation
 * owns page loading progress at a time. Modern Compose draws its own indicator;
 * legacy and TV draw the Java one.
 */
public final class BrowserPresentationPolicy {
    private BrowserPresentationPolicy() { }

    public static boolean showJavaProgress(boolean composeActive, boolean loading) {
        return loading && !composeActive;
    }

    public static boolean shouldTryCompose(boolean tv, int sdk, boolean modernFlavor) {
        return modernFlavor && !tv && sdk >= 21;
    }
}
