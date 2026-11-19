package io.rudycity.tvbrowser.compose

import io.rudycity.tvbrowser.BrowserChrome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryAndBookmarkTest {

    @Test
    fun historyItemPreservesAttributes() {
        val item = BrowserChrome.HistoryItem(
            "Kotlin Programming Language",
            "https://kotlinlang.org/docs/home.html",
            "kotlinlang.org"
        )
        assertEquals("Kotlin Programming Language", item.title)
        assertEquals("https://kotlinlang.org/docs/home.html", item.url)
        assertEquals("kotlinlang.org", item.host)
    }

    @Test
    fun bookmarkItemPreservesAttributes() {
        val bookmark = BrowserChrome.BookmarkItem(
            "Android Developers",
            "https://developer.android.com",
            "developer.android.com"
        )
        assertEquals("Android Developers", bookmark.title)
        assertEquals("https://developer.android.com", bookmark.url)
        assertEquals("developer.android.com", bookmark.host)
    }

    @Test
    fun settingsStateReflectsBrowserConfiguration() {
        val state = BrowserChrome.SettingsState(
            true, // darkMode
            false, // desktopMode
            "AUTO", // engineMode
            "GeckoView", // activeEngineName
            true, // tvDevice
            true, // pointerMode
            4, // openTabCount
            2 // extensionCount
        )
        assertTrue(state.darkMode)
        assertFalse(state.desktopMode)
        assertEquals("AUTO", state.engineMode)
        assertEquals("GeckoView", state.activeEngineName)
        assertTrue(state.tvDevice)
        assertTrue(state.pointerMode)
        assertEquals(4, state.openTabCount)
        assertEquals(2, state.extensionCount)
    }

    @Test
    fun historySearchFiltersByTitleUrlAndHost() {
        val items = listOf(
            BrowserChrome.HistoryItem("GitHub - RudyCity", "https://github.com/RudyCity", "github.com"),
            BrowserChrome.HistoryItem("Wikipedia Main Page", "https://en.wikipedia.org/wiki/Main_Page", "en.wikipedia.org"),
            BrowserChrome.HistoryItem("Rust Programming", "https://www.rust-lang.org/", "rust-lang.org")
        )

        fun filterHistory(query: String): List<BrowserChrome.HistoryItem> {
            if (query.isBlank()) return items
            return items.filter { item ->
                item.title.contains(query, ignoreCase = true) ||
                    item.url.contains(query, ignoreCase = true) ||
                    item.host.contains(query, ignoreCase = true)
            }
        }

        assertEquals(3, filterHistory("").size)
        assertEquals(1, filterHistory("github").size)
        assertEquals("GitHub - RudyCity", filterHistory("github")[0].title)
        assertEquals(1, filterHistory("wikipedia.org").size)
        assertEquals(1, filterHistory("wiki/main").size)
        assertEquals(1, filterHistory("RUST").size)
        assertEquals(0, filterHistory("nonexistent").size)
    }

    @Test
    fun bookmarkSearchFiltersCorrectly() {
        val bookmarks = listOf(
            BrowserChrome.BookmarkItem("Search Engine", "https://duckduckgo.com", "duckduckgo.com"),
            BrowserChrome.BookmarkItem("News Portal", "https://news.ycombinator.com", "news.ycombinator.com")
        )

        fun filterBookmarks(query: String): List<BrowserChrome.BookmarkItem> {
            if (query.isBlank()) return bookmarks
            return bookmarks.filter { item ->
                item.title.contains(query, ignoreCase = true) ||
                    item.url.contains(query, ignoreCase = true) ||
                    item.host.contains(query, ignoreCase = true)
            }
        }

        assertEquals(2, filterBookmarks("").size)
        assertEquals(1, filterBookmarks("duckduckgo").size)
        assertEquals(1, filterBookmarks("ycombinator").size)
        assertEquals(0, filterBookmarks("google").size)
    }

    @Test
    fun manualBookmarkRequiresValidUrl() {
        fun isValidBookmarkUrl(url: String): Boolean {
            return url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))
        }

        assertFalse(isValidBookmarkUrl(""))
        assertFalse(isValidBookmarkUrl("   "))
        assertFalse(isValidBookmarkUrl("ftp://example.com"))
        assertTrue(isValidBookmarkUrl("https://example.com"))
        assertTrue(isValidBookmarkUrl("http://localhost:8080"))
    }
}
