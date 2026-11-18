package io.rudycity.tvbrowser;

import org.junit.Test;
import static org.junit.Assert.*;

/** Security regression: a private session can never leak across renderer fallback. */
public final class BrowserSessionPolicyTest {
    @Test public void privateEngineCrashIsFailClosed() {
        assertFalse(BrowserSessionPolicy.canAutoFallback(true, 0));
        assertFalse(BrowserSessionPolicy.canAutoFallback(true, 1));
        assertFalse(BrowserSessionPolicy.canAutoFallback(true, 2));
        assertTrue(BrowserSessionPolicy.canAutoFallback(false, 1));
        assertFalse(BrowserSessionPolicy.canAutoFallback(false, 2));
        assertFalse(BrowserSessionPolicy.canAutoFallback(false, 0));
    }

    @Test public void privatePagesCannotBeTransferredOnManualEngineSwitch() {
        assertFalse(BrowserSessionPolicy.canTransferUrl(true));
        assertTrue(BrowserSessionPolicy.canTransferUrl(false));
    }

    @Test public void popupRetainsItsOpenersPrivacyBoundary() {
        assertTrue(BrowserSessionPolicy.popupMustStayPrivate(true));
        assertFalse(BrowserSessionPolicy.popupMustStayPrivate(false));
    }
}
