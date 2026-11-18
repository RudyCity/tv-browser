package io.rudycity.tvbrowser;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BrowserNetworkPolicyTest {
    @Test public void legacyOnlyCanUseCleartext() {
        assertTrue(BrowserNetworkPolicy.allowHttpForFlavor("legacy"));
        assertFalse(BrowserNetworkPolicy.allowHttpForFlavor("preview"));
        assertFalse(BrowserNetworkPolicy.allowHttpForFlavor("chromium"));
        assertFalse(BrowserNetworkPolicy.allowHttpForFlavor(null));
    }

    @Test public void modernBuildBlocksAllHttpMainFrameDestinations() {
        assertTrue(BrowserNetworkPolicy.blocksCleartext("http://example.com/", false));
        assertTrue(BrowserNetworkPolicy.blocksCleartext("HTTP://example.com/", false));
        assertTrue(BrowserNetworkPolicy.blocksCleartext("  http://192.168.1.1/admin", false));
        assertFalse(BrowserNetworkPolicy.blocksCleartext("https://example.com/", false));
        assertFalse(BrowserNetworkPolicy.blocksCleartext("about:blank", false));
        assertFalse(BrowserNetworkPolicy.blocksCleartext(null, false));
    }

    @Test public void legacyPreservesOldTvHttpCompatibility() {
        assertFalse(BrowserNetworkPolicy.blocksCleartext("http://old-tv.local/", true));
    }
}
