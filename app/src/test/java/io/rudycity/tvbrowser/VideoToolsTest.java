package io.rudycity.tvbrowser;

import org.junit.Test;
import static org.junit.Assert.*;

public class VideoToolsTest {
    @Test public void everyNamedCommandIsWhitelisted() {
        for (String command : VideoTools.COMMANDS) {
            String js = VideoTools.javascript(command);
            assertNotNull(command, js);
            assertTrue(js.startsWith("(function()"));
            assertTrue(js.contains("querySelectorAll('video')"));
        }
        assertNull(VideoTools.javascript("alert(document.cookie)"));
        assertNull(VideoTools.javascript(""));
        assertNull(VideoTools.javascript(null));
    }

    @Test public void mediaCommandsStayWithinBounds() {
        assertTrue(VideoTools.javascript(VideoTools.REWIND).contains("Math.max(0,"));
        assertTrue(VideoTools.javascript(VideoTools.FORWARD).contains("Math.min(v.duration,"));
        assertTrue(VideoTools.javascript(VideoTools.SPEED_FAST).contains("playbackRate=1.5"));
    }
}
