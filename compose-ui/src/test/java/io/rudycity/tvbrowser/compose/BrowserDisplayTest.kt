package io.rudycity.tvbrowser.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserDisplayTest {
    @Test fun compactAddressPreservesHostname() {
        assertEquals("example.com", BrowserDisplay.compact("https://www.example.com/a?b=1"))
        assertEquals("example.com", BrowserDisplay.compact("http://example.com/"))
        assertEquals("", BrowserDisplay.compact("file:///sdcard/private"))
        assertEquals("", BrowserDisplay.compact("not a url"))
    }
}
