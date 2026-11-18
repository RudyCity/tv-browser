package io.rudycity.tvbrowser;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Fail closed for cleartext main-frame navigation in modern builds, including
 * API 21–25 WebViews that cannot reliably enforce cleartext manifest flags.
 * The legacy flavor intentionally preserves HTTP for old TV firmware.
 */
public final class BrowserNetworkPolicy {
    private BrowserNetworkPolicy() { }

    public static boolean allowHttpForFlavor(String flavor) {
        return "legacy".equals(flavor);
    }

    public static boolean blocksCleartext(String url, boolean allowHttp) {
        if (allowHttp || url == null) return false;
        try {
            URI parsed = new URI(url.trim());
            return "http".equalsIgnoreCase(parsed.getScheme());
        } catch (URISyntaxException invalid) {
            // Malformed URLs are handled by UrlTools.isWebUrl; never bypass its validation.
            return false;
        }
    }
}
