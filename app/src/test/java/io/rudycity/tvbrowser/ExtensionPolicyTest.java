package io.rudycity.tvbrowser;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExtensionPolicyTest {
    @Test public void allowsOnlyHttpHosts() {
        assertEquals("example.com", ExtensionPolicy.host("HTTPS://Example.Com/path"));
        assertNull(ExtensionPolicy.host("file:///sdcard/index.html"));
        assertNull(ExtensionPolicy.host("javascript:alert(1)"));
        assertNull(ExtensionPolicy.host("data:text/html,hi"));
        assertNull(ExtensionPolicy.host("https://"));
        assertEquals("example.com", ExtensionPolicy.host("https://EXAMPLE.COM./"));
        assertNull(ExtensionPolicy.host("https://user:pass@example.com/"));
        assertNull(ExtensionPolicy.host("http://alice@example.com/"));
    }

    @Test public void blocksOnlyRecognizedThirdPartyTrackerSubresources() {
        assertTrue(ExtensionPolicy.shouldBlockResource("https://example.org",
                "https://stats.google-analytics.com/collect", false));
        assertFalse(ExtensionPolicy.shouldBlockResource("https://example.org",
                "https://doubleclick.net/", true));
        assertFalse(ExtensionPolicy.shouldBlockResource("https://doubleclick.net",
                "https://doubleclick.net/asset.js", false));
        assertFalse(ExtensionPolicy.shouldBlockResource("https://example.org",
                "https://google-analytics.com.evil.org/x", false));
        assertFalse(ExtensionPolicy.shouldBlockResource("https://example.org",
                "https://example.org/main.js", false));
        assertFalse(ExtensionPolicy.shouldBlockResource(null,
                "https://doubleclick.net/x", false));
        assertFalse(ExtensionPolicy.shouldBlockResource("https://example.org",
                "http://example.org/keep.js", false));
        assertFalse(ExtensionPolicy.shouldBlockResource("https://example.org",
                "data:text/plain,hello", false));
    }

    @Test public void userscriptsRequireHttpsPages() {
        assertTrue(ExtensionPolicy.userscriptOrigin("https://example.org/a"));
        assertFalse(ExtensionPolicy.userscriptOrigin("http://example.org/a"));
        assertFalse(ExtensionPolicy.userscriptOrigin("file:///a"));
        assertFalse(ExtensionPolicy.userscriptOrigin(null));
    }

    @Test public void onlyKnownBuiltIns() {
        assertEquals(5, ExtensionPolicy.IDS.length);
        assertFalse(ExtensionPolicy.knownId("chrome_crx"));
        assertTrue(ExtensionPolicy.knownId(ExtensionPolicy.TV_NAVIGATION));
        assertTrue(ExtensionPolicy.knownId(ExtensionPolicy.VIDEO_TOOLS));
    }
}
