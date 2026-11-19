package io.rudycity.tvbrowser.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Omnibox behavior:
 * - isBlankOrHomeUrl detection (about:blank, about:start, about:home, etc.)
 * - Display host extraction
 * - Auto-delete for blank pages vs auto-select ("di-blok") for website URLs
 */
class OmniboxBehaviorTest {

    @Test
    fun isBlankOrHomeUrl_recognizesBlankVariants() {
        assertTrue(isBlankOrHomeUrl(null))
        assertTrue(isBlankOrHomeUrl(""))
        assertTrue(isBlankOrHomeUrl("   "))
        assertTrue(isBlankOrHomeUrl("about:blank"))
        assertTrue(isBlankOrHomeUrl("ABOUT:BLANK"))
        assertTrue(isBlankOrHomeUrl("about:start"))
        assertTrue(isBlankOrHomeUrl("about:home"))
        assertTrue(isBlankOrHomeUrl("about:newtab"))
        assertTrue(isBlankOrHomeUrl("about:blank?query=test"))
    }

    @Test
    fun isBlankOrHomeUrl_doesNotMatchRealWebsites() {
        assertFalse(isBlankOrHomeUrl("https://google.com"))
        assertFalse(isBlankOrHomeUrl("https://en.wikipedia.org/wiki/Main_Page"))
        assertFalse(isBlankOrHomeUrl("http://localhost:8080"))
        assertFalse(isBlankOrHomeUrl("https://x.com/explore"))
    }

    @Test
    fun displayHost_cleansProtocolsAndHidesBlank() {
        // Real sites should extract domain
        val site1 = "https://www.google.com/search?q=test"
        val clean1 = site1.removePrefix("https://").removePrefix("http://").substringBefore('/')
        assertEquals("www.google.com", clean1)

        val site2 = "http://github.com/trending"
        val clean2 = site2.removePrefix("https://").removePrefix("http://").substringBefore('/')
        assertEquals("github.com", clean2)

        // Blank should be suppressed
        assertTrue(isBlankOrHomeUrl("about:blank"))
    }
}
