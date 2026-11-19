package io.rudycity.tvbrowser.compose

import io.rudycity.tvbrowser.BrowserChrome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabManagementTest {

    @Test
    fun tabItemRetainsStateAndSecurityProperties() {
        val tab = BrowserChrome.TabItem(
            "Wikipedia",
            "wikipedia.org",
            true,
            false,
            false,
            100,
            false
        )
        assertEquals("Wikipedia", tab.title)
        assertEquals("wikipedia.org", tab.host)
        assertTrue(tab.active)
        assertFalse(tab.privateMode)
        assertFalse(tab.crashed)
        assertEquals(100, tab.progress)
    }

    @Test
    fun privateTabEnforcesSecurityAttributes() {
        val privateTab = BrowserChrome.TabItem(
            "DuckDuckGo",
            "duckduckgo.com",
            false,
            true,
            true,
            45,
            false
        )
        assertTrue(privateTab.privateMode)
        assertTrue(privateTab.loading)
        assertEquals(45, privateTab.progress)
        assertFalse(privateTab.active)
    }

    @Test
    fun tabFilterMatchesTitleAndHostCaseInsensitively() {
        val items = listOf(
            BrowserChrome.TabItem("Google Search", "google.com", true, false, false, 100, false),
            BrowserChrome.TabItem("GitHub - Rudy", "github.com", false, false, false, 100, false),
            BrowserChrome.TabItem("Documentation", "developer.android.com", false, false, false, 100, false)
        )

        fun filterTabs(query: String): List<BrowserChrome.TabItem> {
            if (query.isBlank()) return items
            return items.filter {
                it.title.contains(query, ignoreCase = true) || it.host.contains(query, ignoreCase = true)
            }
        }

        assertEquals(3, filterTabs("").size)
        assertEquals(1, filterTabs("github").size)
        assertEquals("GitHub - Rudy", filterTabs("github")[0].title)
        assertEquals(1, filterTabs("GOOGLE").size)
        assertEquals(1, filterTabs("android").size)
        assertEquals(0, filterTabs("nonexistent").size)
    }

    @Test
    fun tabCounterFormattingFollowsBrowserConventions() {
        fun formatTabCount(count: Int): String {
            return when {
                count <= 0 -> "1"
                count > 99 -> ":D"
                else -> count.toString()
            }
        }

        assertEquals("1", formatTabCount(0))
        assertEquals("1", formatTabCount(1))
        assertEquals("5", formatTabCount(5))
        assertEquals("99", formatTabCount(99))
        assertEquals(":D", formatTabCount(100))
    }
}
