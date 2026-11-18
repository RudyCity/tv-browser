package io.rudycity.tvbrowser;

import org.junit.Test;
import static org.junit.Assert.*;

public final class EnginePolicyTest {
    @Test public void oldAndroidCannotAttemptModernChromium() {
        for (int sdk : new int[]{19, 21, 23, 25, 28}) {
            assertFalse(EnginePolicy.tryChromium(sdk, EnginePolicy.AUTO, true));
            assertFalse(EnginePolicy.tryChromium(sdk, EnginePolicy.CHROMIUM, true));
            assertTrue(EnginePolicy.fallbackReason(sdk, EnginePolicy.AUTO, true).contains("Android 10"));
        }
    }
    @Test public void modernAndroidPrefersPackagedChromium() {
        assertTrue(EnginePolicy.tryChromium(29, EnginePolicy.AUTO, true));
        assertTrue(EnginePolicy.tryChromium(35, EnginePolicy.CHROMIUM, true));
        assertFalse(EnginePolicy.tryChromium(35, EnginePolicy.AUTO, false));
        assertFalse(EnginePolicy.tryChromium(35, EnginePolicy.WEBVIEW, true));
    }
    @Test public void corruptPreferencesNeverEnableChromiumUnexpectedly() {
        assertEquals(EnginePolicy.AUTO, EnginePolicy.sanitize("bogus"));
        assertEquals(EnginePolicy.AUTO, EnginePolicy.sanitize(null));
        assertEquals(EnginePolicy.WEBVIEW, EnginePolicy.sanitize(EnginePolicy.WEBVIEW));
        assertEquals(EnginePolicy.CHROMIUM, EnginePolicy.sanitize(EnginePolicy.CHROMIUM));
    }
    @Test public void geckoRequiresAndroidEightAndActualPackage() {
        assertEquals(EnginePolicy.GECKO, EnginePolicy.sanitize(EnginePolicy.GECKO));
        assertFalse(EnginePolicy.tryGecko(25, EnginePolicy.AUTO, true));
        assertFalse(EnginePolicy.tryGecko(26, EnginePolicy.AUTO, false));
        assertTrue(EnginePolicy.tryGecko(26, EnginePolicy.AUTO, true));
        assertTrue(EnginePolicy.tryGecko(35, EnginePolicy.GECKO, true));
        assertFalse(EnginePolicy.tryGecko(35, EnginePolicy.CHROMIUM, true));
        assertFalse(EnginePolicy.tryGecko(35, EnginePolicy.WEBVIEW, true));
        assertTrue(EnginePolicy.fallbackReason(25, EnginePolicy.GECKO, false).contains("Android 8.0"));
        assertTrue(EnginePolicy.fallbackReason(35, EnginePolicy.GECKO, false).contains("not packaged"));
    }
    @Test public void missingProviderGetsHonestReason() {
        assertTrue(EnginePolicy.fallbackReason(35, EnginePolicy.AUTO, false).contains("not packaged"));
        assertTrue(EnginePolicy.fallbackReason(35, EnginePolicy.WEBVIEW, true).contains("manually"));
    }
}
