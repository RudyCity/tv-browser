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

    @Test public void oldPopupConsentExpiresOnNavigationAndTabChange() {
        String page = "https://example.org/articles";
        assertTrue(BrowserSessionPolicy.popupApprovalCurrent(true, 2, 2, page, page));
        assertFalse(BrowserSessionPolicy.popupApprovalCurrent(false, 2, 2, page, page));
        assertFalse(BrowserSessionPolicy.popupApprovalCurrent(true, 2, 3, page, page));
        assertFalse(BrowserSessionPolicy.popupApprovalCurrent(true, 2, 2, page,
                "https://example.org/other"));
        assertFalse(BrowserSessionPolicy.popupApprovalCurrent(true, -1, -1, page, page));
        assertFalse(BrowserSessionPolicy.popupApprovalCurrent(true, 1, 1, null, page));
        assertFalse(BrowserSessionPolicy.popupApprovalCurrent(true, 1, 1,
                "file:///etc/passwd", "file:///etc/passwd"));
    }

    @Test public void downloadConsentExpiresOnRedirectOrDifferentActiveTab() {
        String page = "https://downloads.example.org/file";
        assertTrue(BrowserSessionPolicy.downloadApprovalCurrent(true, 3, 3, page, page));
        assertFalse(BrowserSessionPolicy.downloadApprovalCurrent(false, 3, 3, page, page));
        assertFalse(BrowserSessionPolicy.downloadApprovalCurrent(true, 3, 4, page, page));
        assertFalse(BrowserSessionPolicy.downloadApprovalCurrent(true, 3, 3,
                page, "https://downloads.example.org/other"));
        assertFalse(BrowserSessionPolicy.downloadApprovalCurrent(true, -1, -1, page, page));
        assertFalse(BrowserSessionPolicy.downloadApprovalCurrent(true, 3, 3, null, page));
        // Legacy Android TV can still download HTTP with explicit fresh consent.
        assertTrue(BrowserSessionPolicy.downloadApprovalCurrent(true, 1, 1,
                "http://device.lan/a", "http://device.lan/a"));
    }

    @Test public void popupRetainsItsOpenersPrivacyBoundary() {
        assertTrue(BrowserSessionPolicy.popupMustStayPrivate(true));
        assertFalse(BrowserSessionPolicy.popupMustStayPrivate(false));
    }
}
