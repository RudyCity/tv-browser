package io.rudycity.tvbrowser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Pure, conservative policy for the built-in WebView add-ons. No Chrome extension API. */
public final class ExtensionPolicy {
    public static final String AD_BLOCKER = "ad_blocker";
    public static final String DARK_MODE = "dark_mode";
    public static final String TV_NAVIGATION = "tv_navigation";
    public static final String USER_SCRIPT = "user_script";

    public static final String[] IDS = {
            AD_BLOCKER, DARK_MODE, TV_NAVIGATION, USER_SCRIPT
    };
    public static final String[] NAMES = {
            "Ad & tracker blocker", "Dark mode", "TV navigation helper", "Userscript manager"
    };
    public static final String[] DESCRIPTIONS = {
            "Blocks requests to a small built-in set of advertising and tracking hosts. "
                    + "This is not a complete filter-list or uBlock Origin replacement.",
            "Applies a reversible dark appearance after pages finish loading. "
                    + "Some sites may not render perfectly.",
            "Adds remote-control D-pad navigation to supported HTML pages. "
                    + "The website itself can still handle remote keys.",
            "Runs only the JavaScript you write, after loading an HTTPS page at its exact hostname. "
                    + "Scripts can read and alter site content."
    };

    private static final String[] TRACKER_HOSTS = {
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "google-analytics.com",
            "googletagmanager.com",
            "adnxs.com",
            "adsrvr.org",
            "scorecardresearch.com",
            "hotjar.com"
    };

    private ExtensionPolicy() {}

    public static boolean knownId(String id) {
        for (String allowed : IDS) if (allowed.equals(id)) return true;
        return false;
    }

    /** Canonical hostname of an HTTP(S) URL, or null for unsafe/non-web origins. */
    public static String host(String url) {
        if (url == null) return null;
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
                return null;
            String host = uri.getHost();
            if (host == null || host.isEmpty()) return null;
            return host.toLowerCase(Locale.US);
        } catch (URISyntaxException | IllegalArgumentException invalid) {
            return null;
        }
    }

    /** Userscripts are only eligible on HTTPS pages (never home, data, file, HTTP). */
    public static boolean userscriptOrigin(String url) {
        if (host(url) == null) return false;
        try { return "https".equalsIgnoreCase(new URI(url).getScheme()); }
        catch (URISyntaxException invalid) { return false; }
    }

    private static boolean matchesDomain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    /** Do not touch main-frame requests or same-site resource hosts. */
    public static boolean shouldBlockResource(String documentUrl, String requestUrl,
                                               boolean mainFrame) {
        if (mainFrame) return false;
        String pageHost = host(documentUrl);
        String target = host(requestUrl);
        if (pageHost == null || target == null || matchesDomain(target, pageHost)) return false;
        for (String blocked : TRACKER_HOSTS) {
            if (matchesDomain(target, blocked)) return true;
        }
        return false;
    }
}
