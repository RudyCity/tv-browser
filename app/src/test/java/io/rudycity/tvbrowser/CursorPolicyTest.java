package io.rudycity.tvbrowser;

import org.junit.Test;
import static org.junit.Assert.*;

public class CursorPolicyTest {
    @Test public void startsAndClampsInVisibleArea() {
        assertEquals(12, CursorPolicy.clamp(-200, 100, 12));
        assertEquals(88, CursorPolicy.clamp(999, 100, 12));
        assertEquals(50, CursorPolicy.clamp(50, 100, 12));
        assertEquals(0, CursorPolicy.clamp(50, 0, 12));
        assertEquals(5, CursorPolicy.clamp(100, 10, 12));
    }
    @Test public void movesWithoutOverflow() {
        assertEquals(75, CursorPolicy.move(50, 1, 25, 100, 12));
        assertEquals(12, CursorPolicy.move(50, -1, 999, 100, 12));
        assertEquals(88, CursorPolicy.move(Integer.MAX_VALUE, 1, 100, 100, 12));
        assertEquals(12, CursorPolicy.move(Integer.MIN_VALUE, -1, 100, 100, 12));
    }
    @Test public void rejectsUnexpectedDirections() {
        try { CursorPolicy.move(20, 2, 40, 100, 10); fail("invalid input accepted"); }
        catch (IllegalArgumentException expected) { /* pass */ }
    }
}
