package io.rudycity.tvbrowser;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BrowserLayoutPolicyTest {
    @Test public void smallScreensUseTwoRows() {
        assertTrue(BrowserLayoutPolicy.isCompact(320));
        assertTrue(BrowserLayoutPolicy.isCompact(390));
        assertTrue(BrowserLayoutPolicy.isCompact(600));
        assertTrue(BrowserLayoutPolicy.isCompact(768));
        assertTrue(BrowserLayoutPolicy.isCompact(839));
    }

    @Test public void largeScreensUseOneRow() {
        assertFalse(BrowserLayoutPolicy.isCompact(840));
        assertFalse(BrowserLayoutPolicy.isCompact(1024));
        assertFalse(BrowserLayoutPolicy.isCompact(1920));
    }

    @Test public void touchPhonesAndTabletsAreNotTreatedAsTv() {
        assertFalse(BrowserLayoutPolicy.isTelevision(false, false, false, true));
        assertTrue(BrowserLayoutPolicy.isTelevision(true, false, false, true));
        assertTrue(BrowserLayoutPolicy.isTelevision(false, true, false, true));
        assertTrue(BrowserLayoutPolicy.isTelevision(false, false, true, true));
        assertTrue(BrowserLayoutPolicy.isTelevision(false, false, false, false));
    }
}
