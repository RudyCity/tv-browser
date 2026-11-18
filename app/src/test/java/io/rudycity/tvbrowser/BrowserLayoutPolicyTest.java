package io.rudycity.tvbrowser;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BrowserLayoutPolicyTest {
    @Test public void phonesHaveOneRowAndNoFooter() {
        for (int width : new int[] {320, 360, 390, 480, 599}) {
            int mode = BrowserLayoutPolicy.toolbarMode(width, false);
            assertEquals(BrowserLayoutPolicy.MOBILE, mode);
            assertFalse(BrowserLayoutPolicy.hasSecondRow(mode));
            assertFalse(BrowserLayoutPolicy.hasStatusFooter(mode));
        }
    }

    @Test public void tabletsUseOneRowWithAdditionalActions() {
        for (int width : new int[] {600, 768, 839, 840, 1280}) {
            int mode = BrowserLayoutPolicy.toolbarMode(width, false);
            assertEquals(BrowserLayoutPolicy.TABLET, mode);
            assertFalse(BrowserLayoutPolicy.hasSecondRow(mode));
            assertFalse(BrowserLayoutPolicy.hasStatusFooter(mode));
        }
    }

    @Test public void tvPreservesLargeRemoteFocusAndCompactTwoRows() {
        for (int width : new int[] {320, 600, 768, 839}) {
            int mode = BrowserLayoutPolicy.toolbarMode(width, true);
            assertEquals(BrowserLayoutPolicy.TV_COMPACT, mode);
            assertTrue(BrowserLayoutPolicy.hasSecondRow(mode));
            assertTrue(BrowserLayoutPolicy.hasStatusFooter(mode));
        }
        assertEquals(BrowserLayoutPolicy.TV_WIDE, BrowserLayoutPolicy.toolbarMode(840, true));
        assertFalse(BrowserLayoutPolicy.hasSecondRow(BrowserLayoutPolicy.TV_WIDE));
        assertTrue(BrowserLayoutPolicy.hasStatusFooter(BrowserLayoutPolicy.TV_WIDE));
    }

    @Test public void touchPhonesAndTabletsAreNotTreatedAsTv() {
        assertFalse(BrowserLayoutPolicy.isTelevision(false, false, false, true));
        assertTrue(BrowserLayoutPolicy.isTelevision(true, false, false, true));
        assertTrue(BrowserLayoutPolicy.isTelevision(false, true, false, true));
        assertTrue(BrowserLayoutPolicy.isTelevision(false, false, true, true));
        assertTrue(BrowserLayoutPolicy.isTelevision(false, false, false, false));
    }

    @Test public void oldCompactHelperOnlyRepresentsTvThreshold() {
        assertTrue(BrowserLayoutPolicy.isCompact(320));
        assertTrue(BrowserLayoutPolicy.isCompact(839));
        assertFalse(BrowserLayoutPolicy.isCompact(840));
    }
}
