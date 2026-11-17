package io.rudycity.tvbrowser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Fail-closed popup URL validator; executable suffix warnings are not malware scanning. */
public final class PopupPolicy {
    private static final int MAX_LENGTH = 4096;
    private static final String[] EXECUTABLE_SUFFIXES = {
            ".apk", ".apks", ".xapk", ".aab", ".exe", ".msi", ".msp",
            ".bat", ".cmd", ".ps1", ".scr", ".jar", ".dex", ".dmg",
            ".pkg", ".deb", ".rpm", ".sh"
    };
    private PopupPolicy() {}

    public static boolean isPreviewable(String url) {
        if (url == null || url.length() > MAX_LENGTH || !UrlTools.isWebUrl(url)) return false;
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c)) return false;
        }
        return true;
    }

    public static String host(String url) {
        if (!isPreviewable(url)) return null;
        try { return new URI(url).getHost(); }
        catch (URISyntaxException invalid) { return null; }
    }

    public static boolean mightInstallSoftware(String url) {
        if (!isPreviewable(url)) return false;
        try {
            String path = new URI(url).getPath();
            if (path == null) return false;
            String lower = path.toLowerCase(Locale.US);
            for (String suffix : EXECUTABLE_SUFFIXES)
                if (lower.endsWith(suffix)) return true;
        } catch (URISyntaxException invalid) {
            return false;
        }
        return false;
    }

    public static boolean usesPlainHttp(String url) {
        return isPreviewable(url) && url.regionMatches(true, 0, "http://", 0, 7);
    }
}
