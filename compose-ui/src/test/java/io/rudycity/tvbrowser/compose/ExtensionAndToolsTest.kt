package io.rudycity.tvbrowser.compose

import io.rudycity.tvbrowser.BrowserChrome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionAndToolsTest {

    @Test
    fun extensionItemPreservesAttributes() {
        val item = BrowserChrome.ExtensionItem(
            "ad_blocker",
            "Ad & tracker blocker",
            "Blocks requests to advertising and tracking hosts.",
            true,
            true,
            "inherit"
        )
        assertEquals("ad_blocker", item.id)
        assertEquals("Ad & tracker blocker", item.name)
        assertEquals("Blocks requests to advertising and tracking hosts.", item.description)
        assertTrue(item.enabled)
        assertTrue(item.builtIn)
        assertEquals("inherit", item.siteRule)
    }

    @Test
    fun extensionItemHandlesNullSiteRuleWithDefaultInherit() {
        val item = BrowserChrome.ExtensionItem(
            "dark_mode",
            "Dark mode",
            "Inverts web colors.",
            false,
            true,
            null
        )
        assertEquals("inherit", item.siteRule)
        assertFalse(item.enabled)
    }

    @Test
    fun toolsStateReflectsBrowserPageAndCapabilities() {
        val state = BrowserChrome.ToolsState(
            "https://github.com/RudyCity",
            "github.com",
            "Firefox GeckoView",
            true, // desktopMode
            true, // darkTheme
            false, // tvDevice
            false, // cursorMode
            true, // canSavePdf
            true // hasSitePermissions
        )
        assertEquals("https://github.com/RudyCity", state.currentUrl)
        assertEquals("github.com", state.currentHost)
        assertEquals("Firefox GeckoView", state.engineName)
        assertTrue(state.desktopMode)
        assertTrue(state.darkTheme)
        assertFalse(state.tvDevice)
        assertFalse(state.cursorMode)
        assertTrue(state.canSavePdf)
        assertTrue(state.hasSitePermissions)
    }

    @Test
    fun toolsStateHandlesNullStringsSafely() {
        val state = BrowserChrome.ToolsState(
            null,
            null,
            null,
            false,
            false,
            true,
            true,
            false,
            false
        )
        assertEquals("", state.currentUrl)
        assertEquals("", state.currentHost)
        assertEquals("System WebView", state.engineName)
        assertTrue(state.tvDevice)
        assertTrue(state.cursorMode)
        assertFalse(state.canSavePdf)
    }

    @Test
    fun extensionFilteringMatchesNameDescriptionOrId() {
        val items = listOf(
            BrowserChrome.ExtensionItem("ad_blocker", "Ad & tracker blocker", "Blocks ads", true, true, "inherit"),
            BrowserChrome.ExtensionItem("dark_mode", "Dark mode", "High contrast inverted view", false, true, "allow"),
            BrowserChrome.ExtensionItem("user_script", "Userscript manager", "Executes custom javascript", true, true, "deny")
        )

        fun filterExtensions(query: String): List<BrowserChrome.ExtensionItem> {
            if (query.isBlank()) return items
            return items.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true) ||
                        it.id.contains(query, ignoreCase = true)
            }
        }

        assertEquals(3, filterExtensions("").size)
        assertEquals(1, filterExtensions("blocker").size)
        assertEquals("ad_blocker", filterExtensions("blocker")[0].id)
        assertEquals(1, filterExtensions("javascript").size)
        assertEquals("user_script", filterExtensions("javascript")[0].id)
        assertEquals(1, filterExtensions("DARK").size)
        assertEquals("dark_mode", filterExtensions("DARK")[0].id)
        assertEquals(0, filterExtensions("nonexistent").size)
    }
}
