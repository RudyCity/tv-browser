package io.rudycity.tvbrowser;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Locale;

/** Keep user input resolution independent of Android to allow fast unit tests. */
public final class UrlTools {
    private UrlTools() {}

    public static boolean isWebUrl(String url) {
        if (url == null) return false;
        try {
            URI parsed = new URI(url.trim());
            String scheme = parsed.getScheme();
            return scheme != null &&
                    ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) &&
                    parsed.getHost() != null && !parsed.getHost().isEmpty() &&
                    parsed.getUserInfo() == null;
        } catch (URISyntaxException error) {
            return false;
        }
    }

    public static String navigate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String input = raw.trim();
        if (input.matches("(?i)^[a-z][a-z0-9+.-]*:.*") &&
                !input.toLowerCase(Locale.US).startsWith("http:") &&
                !input.toLowerCase(Locale.US).startsWith("https:")) {
            throw new IllegalArgumentException("Only HTTP and HTTPS addresses are allowed");
        }
        if (input.indexOf(' ') >= 0) return search(input);
        // A single word should be a search, not a made-up HTTPS hostname.
        if (!input.matches("(?i)^https?://.*") &&
                !input.contains(".") && !input.contains(":") && !input.contains("/") &&
                !"localhost".equalsIgnoreCase(input)) return search(input);
        String candidate = input.matches("(?i)^https?://.*") ? input : "https://" + input;
        return isWebUrl(candidate) ? candidate : search(input);
    }

    private static String search(String text) {
        try {
            return "https://www.google.com/search?q=" + URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
