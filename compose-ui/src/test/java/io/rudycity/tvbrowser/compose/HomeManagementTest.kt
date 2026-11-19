package io.rudycity.tvbrowser.compose

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class HomeManagementTest {

    private lateinit var mockPrefs: FakeSharedPreferences

    @Before
    fun setup() {
        mockPrefs = FakeSharedPreferences()
    }

    @Test
    fun defaultSpeedDialSitesLoadedWhenNoSavedPreferences() {
        val sites = HomeStorage.loadSpeedDial(mockPrefs)
        assertEquals(8, sites.size)
        assertEquals("Google", sites[0].label)
        assertEquals("google.com", sites[0].host)
        assertEquals("https://www.google.com/", sites[0].url)
        assertEquals("G", sites[0].mark)
        assertFalse(sites[0].isCustom)

        val siteNames = sites.map { it.label }
        assertTrue(siteNames.contains("YouTube"))
        assertTrue(siteNames.contains("Wikipedia"))
        assertTrue(siteNames.contains("Bing"))
        assertTrue(siteNames.contains("GitHub"))
        assertTrue(siteNames.contains("Reddit"))
        assertTrue(siteNames.contains("DuckDuckGo"))
        assertTrue(siteNames.contains("X (Twitter)"))
    }

    @Test
    fun saveAndReloadCustomSpeedDialShortcuts() {
        val customSites = listOf(
            QuickSite("custom-1", "Linear App", "linear.app", "https://linear.app", "LA", isCustom = true),
            QuickSite("custom-2", "Claude Code", "claude.ai", "https://claude.ai", "CC", isCustom = true)
        )
        HomeStorage.saveSpeedDial(mockPrefs, customSites)

        val loaded = HomeStorage.loadSpeedDial(mockPrefs)
        assertEquals(2, loaded.size)
        assertEquals("Linear App", loaded[0].label)
        assertEquals("linear.app", loaded[0].host)
        assertEquals("https://linear.app", loaded[0].url)
        assertEquals("LA", loaded[0].mark)
        assertTrue(loaded[0].isCustom)

        assertEquals("Claude Code", loaded[1].label)
        assertEquals("claude.ai", loaded[1].host)
        assertTrue(loaded[1].isCustom)
    }

    @Test
    fun makeMarkGeneratesAccurateInitials() {
        assertEquals("GO", HomeStorage.makeMark("Google"))
        assertEquals("GH", HomeStorage.makeMark("Git Hub"))
        assertEquals("LA", HomeStorage.makeMark("Linear App"))
        assertEquals("X", HomeStorage.makeMark("X"))
        assertEquals("DU", HomeStorage.makeMark("DuckDuckGo"))
        assertEquals("?", HomeStorage.makeMark(""))
        assertEquals("?", HomeStorage.makeMark("   "))
    }

    @Test
    fun parseHostHandlesVariousUrlFormats() {
        assertEquals("google.com", HomeStorage.parseHost("https://google.com"))
        assertEquals("bing.com", HomeStorage.parseHost("http://bing.com/search?q=test"))
        assertEquals("github.com", HomeStorage.parseHost("github.com/login"))
        assertEquals("localhost", HomeStorage.parseHost("http://localhost:8080/app"))
    }

    @Test
    fun homePreferencesDefaultsAndSaving() {
        val initial = HomeStorage.loadPreferences(mockPrefs)
        assertTrue(initial.showSpeedDial)
        assertTrue(initial.showShortcutPills)
        assertTrue(initial.showRecentHistory)
        assertTrue(initial.showBookmarks)

        val updated = HomePreferences(
            showSpeedDial = false,
            showShortcutPills = true,
            showRecentHistory = false,
            showBookmarks = true
        )
        HomeStorage.savePreferences(mockPrefs, updated)

        val reloaded = HomeStorage.loadPreferences(mockPrefs)
        assertFalse(reloaded.showSpeedDial)
        assertTrue(reloaded.showShortcutPills)
        assertFalse(reloaded.showRecentHistory)
        assertTrue(reloaded.showBookmarks)
    }

    @Test
    fun loadRecentHistoryParsesJsonArrayAndLimitsCount() {
        val historyJson = """
            [
                {"title": "Search - Bing", "url": "https://www.bing.com"},
                {"title": "Kotlin Docs", "url": "https://kotlinlang.org"},
                {"title": "Android Developers", "url": "https://developer.android.com"},
                {"title": "GitHub Pull Requests", "url": "https://github.com/pulls"},
                {"title": "Fifth Old Page", "url": "https://example.com/old"}
            ]
        """.trimIndent()
        mockPrefs.putString("history", historyJson)

        val items = HomeStorage.loadRecentHistory(mockPrefs, limit = 3)
        assertEquals(3, items.size)
        assertEquals("Search - Bing", items[0].title)
        assertEquals("https://www.bing.com", items[0].url)
        assertEquals("www.bing.com", items[0].host)

        assertEquals("Kotlin Docs", items[1].title)
        assertEquals("https://kotlinlang.org", items[1].url)
    }

    @Test
    fun loadBookmarksParsesJsonArrayAndHandlesEmptyGracefully() {
        val emptyList = HomeStorage.loadBookmarks(mockPrefs)
        assertTrue(emptyList.isEmpty())

        val bookmarksJson = """
            [
                {"title": "My Favorite Site", "url": "https://favorites.org"},
                {"title": "Tech News", "url": "https://news.ycombinator.com"}
            ]
        """.trimIndent()
        mockPrefs.putString("bookmarks", bookmarksJson)

        val items = HomeStorage.loadBookmarks(mockPrefs, limit = 4)
        assertEquals(2, items.size)
        assertEquals("My Favorite Site", items[0].title)
        assertEquals("favorites.org", items[0].host)
        assertEquals("Tech News", items[1].title)
        assertEquals("news.ycombinator.com", items[1].host)
    }
}

/**
 * Lightweight in-memory fake SharedPreferences for JVM tests.
 */
private class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    fun putString(key: String, value: String) {
        data[key] = value
    }

    override fun getAll(): MutableMap<String, *> = data

    override fun getString(key: String?, defValue: String?): String? =
        (data[key] as? String) ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        (data[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        (data[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        (data[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        (data[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (data[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(data)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class FakeEditor(private val storage: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) temp[key] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) removals.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) storage.clear()
            removals.forEach { storage.remove(it) }
            storage.putAll(temp)
        }
    }
}
