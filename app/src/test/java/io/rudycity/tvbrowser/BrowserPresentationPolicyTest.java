package io.rudycity.tvbrowser;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BrowserPresentationPolicyTest {
    @Test public void onlyOneProgressIndicatorVisible() {
        assertFalse(BrowserPresentationPolicy.showJavaProgress(true, true));
        assertFalse(BrowserPresentationPolicy.showJavaProgress(true, false));
        assertTrue(BrowserPresentationPolicy.showJavaProgress(false, true));
        assertFalse(BrowserPresentationPolicy.showJavaProgress(false, false));
    }

    @Test public void onlyModernTouchDevicesTryCompose() {
        assertFalse(BrowserPresentationPolicy.shouldTryCompose(false, 19, true));
        assertFalse(BrowserPresentationPolicy.shouldTryCompose(false, 20, true));
        assertFalse(BrowserPresentationPolicy.shouldTryCompose(true, 35, true));
        assertFalse(BrowserPresentationPolicy.shouldTryCompose(false, 35, false));
        assertTrue(BrowserPresentationPolicy.shouldTryCompose(false, 21, true));
        assertTrue(BrowserPresentationPolicy.shouldTryCompose(false, 35, true));
    }
}
