package io.rudycity.tvbrowser.compose

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.view.View
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rudycity.tvbrowser.BrowserHome
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Modern Flagship Home & Start Page Experience for Rudy Browser.
 * Adheres to Rudy Design System v2: bespoke iconography, customizable Speed Dial,
 * recent history resumption, bookmarks quick access, and zero generic slop.
 */
class ComposeBrowserHome(
    activity: Activity,
    private val actions: BrowserHome.Actions
) : BrowserHome {

    private val prefs = activity.getSharedPreferences("tv_browser", Context.MODE_PRIVATE)
    private var darkMode by mutableStateOf(
        prefs.getBoolean("dark_mode", true)
    )
    private var refreshSignal by mutableIntStateOf(0)

    private val screen = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            RudyBrowserTheme(darkMode) {
                RudyHomeContent(
                    prefs = prefs,
                    actions = actions,
                    refreshSignal = refreshSignal
                )
            }
        }
    }

    override fun view(): View = screen
    override fun setDarkTheme(dark: Boolean) { darkMode = dark }
    override fun refreshData() { refreshSignal++ }
}

/**
 * Data representation of a Speed Dial shortcut on the Home screen.
 */
internal data class QuickSite(
    val id: String,
    val label: String,
    val host: String,
    val url: String,
    val mark: String,
    val isCustom: Boolean = false
)

/**
 * Data representation of recent history and bookmarks shown on Home.
 */
internal data class HomeLinkItem(
    val title: String,
    val url: String,
    val host: String
)

/**
 * Home screen section visibility toggles.
 */
internal data class HomePreferences(
    val showSpeedDial: Boolean = true,
    val showShortcutPills: Boolean = true,
    val showRecentHistory: Boolean = true,
    val showBookmarks: Boolean = true
)

internal object HomeStorage {
    private const val PREF_SPEED_DIAL = "home_speed_dial_v2"
    private const val PREF_SHOW_SPEED_DIAL = "home_show_speed_dial"
    private const val PREF_SHOW_SHORTCUT_PILLS = "home_show_shortcut_pills"
    private const val PREF_SHOW_HISTORY = "home_show_recent_history"
    private const val PREF_SHOW_BOOKMARKS = "home_show_bookmarks"

    val defaultSites = listOf(
        QuickSite("g", "Google", "google.com", "https://www.google.com/", "G"),
        QuickSite("y", "YouTube", "youtube.com", "https://www.youtube.com/", "Y"),
        QuickSite("w", "Wikipedia", "wikipedia.org", "https://www.wikipedia.org/", "W"),
        QuickSite("b", "Bing", "bing.com", "https://www.bing.com/", "B"),
        QuickSite("gh", "GitHub", "github.com", "https://github.com/", "GH"),
        QuickSite("r", "Reddit", "reddit.com", "https://www.reddit.com/", "R"),
        QuickSite("ddg", "DuckDuckGo", "duckduckgo.com", "https://duckduckgo.com/", "DDG"),
        QuickSite("x", "X (Twitter)", "x.com", "https://x.com/", "X")
    )

    fun loadSpeedDial(prefs: SharedPreferences): List<QuickSite> {
        val raw = prefs.getString(PREF_SPEED_DIAL, null) ?: return defaultSites
        if (raw.isBlank() || raw == "[]") return defaultSites
        val parsed = parseSpeedDialJson(raw)
        return if (parsed.isNotEmpty()) parsed else defaultSites
    }

    fun saveSpeedDial(prefs: SharedPreferences, sites: List<QuickSite>) {
        prefs.edit().putString(PREF_SPEED_DIAL, serializeSpeedDial(sites)).apply()
    }

    fun serializeSpeedDial(sites: List<QuickSite>): String {
        val sb = StringBuilder("[")
        sites.forEachIndexed { i, s ->
            if (i > 0) sb.append(",")
            sb.append("{\"id\":\"").append(escapeJson(s.id))
                .append("\",\"label\":\"").append(escapeJson(s.label))
                .append("\",\"host\":\"").append(escapeJson(s.host))
                .append("\",\"url\":\"").append(escapeJson(s.url))
                .append("\",\"mark\":\"").append(escapeJson(s.mark))
                .append("\",\"custom\":").append(s.isCustom)
                .append("}")
        }
        sb.append("]")
        return sb.toString()
    }

    fun parseSpeedDialJson(raw: String): List<QuickSite> {
        val result = mutableListOf<QuickSite>()
        val pattern = Regex("""\{"id":"(.*?)","label":"(.*?)","host":"(.*?)","url":"(.*?)","mark":"(.*?)","custom":(true|false)\}""")
        for (match in pattern.findAll(raw)) {
            val (id, label, host, url, mark, custom) = match.destructured
            if (url.isNotBlank()) {
                result.add(
                    QuickSite(
                        id = id.ifBlank { UUID.randomUUID().toString() },
                        label = label,
                        host = host.ifBlank { parseHost(url) },
                        url = url,
                        mark = mark.ifBlank { makeMark(label) },
                        isCustom = custom.toBoolean()
                    )
                )
            }
        }
        return result
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    fun loadPreferences(prefs: SharedPreferences): HomePreferences {
        return HomePreferences(
            showSpeedDial = prefs.getBoolean(PREF_SHOW_SPEED_DIAL, true),
            showShortcutPills = prefs.getBoolean(PREF_SHOW_SHORTCUT_PILLS, true),
            showRecentHistory = prefs.getBoolean(PREF_SHOW_HISTORY, true),
            showBookmarks = prefs.getBoolean(PREF_SHOW_BOOKMARKS, true)
        )
    }

    fun savePreferences(prefs: SharedPreferences, p: HomePreferences) {
        prefs.edit()
            .putBoolean(PREF_SHOW_SPEED_DIAL, p.showSpeedDial)
            .putBoolean(PREF_SHOW_SHORTCUT_PILLS, p.showShortcutPills)
            .putBoolean(PREF_SHOW_HISTORY, p.showRecentHistory)
            .putBoolean(PREF_SHOW_BOOKMARKS, p.showBookmarks)
            .apply()
    }

    fun loadRecentHistory(prefs: SharedPreferences, limit: Int = 4): List<HomeLinkItem> {
        val raw = prefs.getString("history", "[]") ?: "[]"
        return parseLinksJson(raw, limit)
    }

    fun loadBookmarks(prefs: SharedPreferences, limit: Int = 4): List<HomeLinkItem> {
        val raw = prefs.getString("bookmarks", "[]") ?: "[]"
        return parseLinksJson(raw, limit)
    }

    fun parseLinksJson(raw: String, limit: Int): List<HomeLinkItem> {
        val result = mutableListOf<HomeLinkItem>()
        val pattern = Regex("""\{.*?"url"\s*:\s*"(.*?)".*?"title"\s*:\s*"(.*?)".*?\}|\{.*?"title"\s*:\s*"(.*?)".*?"url"\s*:\s*"(.*?)".*?\}""")
        for (match in pattern.findAll(raw)) {
            val groups = match.groupValues
            val (url, title) = if (groups[1].isNotBlank()) {
                groups[1] to groups[2]
            } else {
                groups[4] to groups[3]
            }
            val isValidUrl = url.isNotBlank() &&
                !url.startsWith("about:") &&
                !url.equals("https", ignoreCase = true) &&
                !url.equals("http", ignoreCase = true) &&
                url.contains(".") &&
                url.length > 8
            if (isValidUrl && result.size < limit) {
                val cleanTitle = if (title.isBlank() ||
                    title.equals("about:blank", ignoreCase = true) ||
                    title.equals("Webpage not available", ignoreCase = true) ||
                    title.equals("https", ignoreCase = true)
                ) {
                    parseHost(url)
                } else {
                    title
                }
                result.add(HomeLinkItem(title = cleanTitle, url = url, host = parseHost(url)))
            }
        }
        return result
    }

    fun parseHost(url: String): String {
        val clean = url.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore(":")
        return clean.ifBlank { url }
    }

    fun makeMark(label: String): String {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return "?"
        val words = trimmed.split(Regex("\\s+"))
        return if (words.size > 1) {
            "${words[0].first()}${words[1].first()}".uppercase()
        } else {
            trimmed.take(minOf(2, trimmed.length)).uppercase()
        }
    }
}

@Composable
internal fun RudyHomeContent(
    prefs: SharedPreferences,
    actions: BrowserHome.Actions,
    refreshSignal: Int
) {
    val palette = RudyTheme.palette

    var speedDialSites by remember(refreshSignal) { mutableStateOf(HomeStorage.loadSpeedDial(prefs)) }
    var homePrefs by remember(refreshSignal) { mutableStateOf(HomeStorage.loadPreferences(prefs)) }
    var recentHistory by remember(refreshSignal) { mutableStateOf(HomeStorage.loadRecentHistory(prefs, 4)) }
    var bookmarks by remember(refreshSignal) { mutableStateOf(HomeStorage.loadBookmarks(prefs, 4)) }
    var activeWallpaper by remember(refreshSignal) { mutableStateOf(prefs.getString("home_wallpaper", "nordic_aurora") ?: "nordic_aurora") }

    // Dialog sheets
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var editingSite by remember { mutableStateOf<QuickSite?>(null) }
    var isAddingNewSite by remember { mutableStateOf(false) }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entranceAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "homeAlpha"
    )
    val entranceOffset by animateFloatAsState(
        targetValue = if (entered) 0f else 8f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "homeOffset"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        val context = LocalContext.current
        val resId = remember(activeWallpaper) {
            if (activeWallpaper != "none") {
                context.resources.getIdentifier(activeWallpaper, "drawable", context.packageName)
            } else 0
        }
        val hasWallpaper = activeWallpaper != "none" && resId != 0

        // Active Background Wallpaper (Unsplash - Free for Commercial Use)
        if (hasWallpaper) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Smart atmospheric vignette overlay for perfect contrast & readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = if (palette.isDark) {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x80060A14),
                                    Color(0xCC090D18),
                                    Color(0xF5060912)
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x66FFFFFF),
                                    Color(0xD8F8FAFC),
                                    Color(0xF8F8FAFC)
                                )
                            )
                        }
                    )
            )
        } else {
            // Subtle ambient atmospheric background when no photo wallpaper is selected
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = if (palette.isDark) {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF131D2E),
                                    Color(0xFF090D15)
                                ),
                                radius = 1400f
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFFFFFF),
                                    Color(0xFFF1F5F9),
                                    Color(0xFFE2E8F0)
                                )
                            )
                        }
                    )
            )
        }
        val wide = maxWidth >= 600.dp
        val horizontalPadding = if (wide) RudySpacing.xxl else 20.dp
        val verticalPadding = if (wide) RudySpacing.xxl else RudySpacing.lg

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .graphicsLayer {
                        alpha = entranceAlpha
                        translationY = entranceOffset
                    }
            ) {
                // Header Bar with Brand Wordmark & Customize Action Button
                HomeHeaderBar(
                    hasWallpaper = hasWallpaper,
                    onCustomizeClick = { showCustomizeSheet = true }
                )

                Spacer(Modifier.height(RudySpacing.lg))

                // Search Omnibox Trigger
                AddressTriggerButton(
                    onClick = actions::focusAddress,
                    hasWallpaper = hasWallpaper,
                    wide = wide
                )

                // Action Shortcut Pills (New Tab, Private, Bookmarks, History)
                if (homePrefs.showShortcutPills) {
                    Spacer(Modifier.height(RudySpacing.md))
                    HomeActionPillsRow(
                        actions = actions,
                        hasWallpaper = hasWallpaper,
                        wide = wide
                    )
                }

                Spacer(Modifier.height(RudySpacing.xl))

                // Speed Dial / Quick Sites Section
                if (homePrefs.showSpeedDial) {
                    SpeedDialSection(
                        sites = speedDialSites,
                        wide = wide,
                        hasWallpaper = hasWallpaper,
                        onOpenSite = { actions.openWebsite(it.url) },
                        onEditSite = { editingSite = it },
                        onAddClick = { isAddingNewSite = true }
                    )
                    Spacer(Modifier.height(RudySpacing.xxl))
                }

                // Recent History Section
                if (homePrefs.showRecentHistory && recentHistory.isNotEmpty()) {
                    HomeLinkSection(
                        title = "RECENT VISITS",
                        viewAllText = "View history ›",
                        onViewAllClick = { actions.openHistory() },
                        items = recentHistory,
                        onOpen = { actions.openWebsite(it.url) },
                        hasWallpaper = hasWallpaper,
                        icon = { RudyIcons.History(tint = palette.accent, size = 18.dp) }
                    )
                    Spacer(Modifier.height(RudySpacing.xxl))
                }

                // Saved Bookmarks Section
                if (homePrefs.showBookmarks && bookmarks.isNotEmpty()) {
                    HomeLinkSection(
                        title = "SAVED BOOKMARKS",
                        viewAllText = "View bookmarks ›",
                        onViewAllClick = { actions.openBookmarks() },
                        items = bookmarks,
                        onOpen = { actions.openWebsite(it.url) },
                        hasWallpaper = hasWallpaper,
                        icon = { RudyIcons.Star(tint = palette.accent, size = 18.dp) }
                    )
                    Spacer(Modifier.height(RudySpacing.xxl))
                }

                // Privacy Note Footer
                HomeFooterNote(hasWallpaper = hasWallpaper)
                Spacer(Modifier.height(RudySpacing.xl))
            }
        }
    }

    // Modal Sheet: Add or Edit Quick Site Shortcut
    if (isAddingNewSite || editingSite != null) {
        val targetSite = editingSite
        AddEditQuickSiteDialog(
            existingSite = targetSite,
            onDismiss = {
                isAddingNewSite = false
                editingSite = null
            },
            onSave = { savedSite ->
                val updated = speedDialSites.toMutableList()
                val existingIndex = updated.indexOfFirst { it.id == savedSite.id }
                if (existingIndex >= 0) {
                    updated[existingIndex] = savedSite
                } else {
                    updated.add(savedSite)
                }
                speedDialSites = updated
                HomeStorage.saveSpeedDial(prefs, updated)
                isAddingNewSite = false
                editingSite = null
            },
            onDelete = { siteId ->
                val updated = speedDialSites.filter { it.id != siteId }
                speedDialSites = updated
                HomeStorage.saveSpeedDial(prefs, updated)
                isAddingNewSite = false
                editingSite = null
            }
        )
    }

    // Modal Sheet: Customize Home Screen
    if (showCustomizeSheet) {
        CustomizeHomeDialog(
            currentPreferences = homePrefs,
            activeWallpaper = activeWallpaper,
            onDismiss = { showCustomizeSheet = false },
            onUpdatePreferences = { newPrefs ->
                homePrefs = newPrefs
                HomeStorage.savePreferences(prefs, newPrefs)
            },
            onUpdateWallpaper = { newWp ->
                activeWallpaper = newWp
                prefs.edit().putString("home_wallpaper", newWp).apply()
            },
            onResetSpeedDial = {
                speedDialSites = HomeStorage.defaultSites
                HomeStorage.saveSpeedDial(prefs, HomeStorage.defaultSites)
            }
        )
    }
}

/**
 * Top brand hero header row with greeting, dynamic date, brand mark, and Customize button.
 */
@Composable
private fun HomeHeaderBar(
    hasWallpaper: Boolean,
    onCustomizeClick: () -> Unit
) {
    val palette = RudyTheme.palette
    val formattedDate = remember {
        try {
            java.text.SimpleDateFormat("EEEE, MMM d", java.util.Locale.getDefault()).format(java.util.Date())
        } catch (_: Exception) {
            "Home"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RudySpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF38BDF8).copy(alpha = 0.25f),
                                Color(0xFF2563EB).copy(alpha = 0.25f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Color(0xFF38BDF8).copy(alpha = 0.45f),
                        RoundedCornerShape(13.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                RudyIcons.RudyMark(size = 34.dp)
            }

            Column {
                Text(
                    text = "Rudy Browser",
                    style = RudyTypography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = if (hasWallpaper) Color.White else palette.foreground
                )
                Text(
                    text = formattedDate,
                    style = RudyTypography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.1.sp
                    ),
                    color = if (hasWallpaper) Color.White.copy(alpha = 0.75f) else palette.muted
                )
            }
        }

        // Sleek Customize Pill Button
        Surface(
            onClick = onCustomizeClick,
            shape = RoundedCornerShape(20.dp),
            color = if (hasWallpaper) Color.Black.copy(alpha = 0.42f) else palette.surfaceElevated,
            border = BorderStroke(
                1.dp,
                if (hasWallpaper) Color.White.copy(alpha = 0.22f) else palette.outlineSubtle
            ),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RudyIcons.Sliders(
                    size = 14.dp,
                    tint = if (hasWallpaper) Color.White else palette.foreground
                )
                Text(
                    text = "Customize",
                    style = RudyTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (hasWallpaper) Color.White else palette.foreground
                )
            }
        }
    }
}

/**
 * Interactive Address/Search trigger styled like a modern ambient command bar.
 */
@Composable
private fun AddressTriggerButton(
    onClick: () -> Unit,
    hasWallpaper: Boolean,
    wide: Boolean
) {
    val palette = RudyTheme.palette
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(26.dp),
        color = if (hasWallpaper) Color.Black.copy(alpha = 0.52f) else palette.omnibox,
        border = BorderStroke(
            1.dp,
            if (hasWallpaper) Color.White.copy(alpha = 0.24f) else palette.outline.copy(alpha = 0.5f)
        ),
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .rudyInteractiveFeedback(interactionSource, RoundedCornerShape(26.dp))
            .semantics { contentDescription = "Search or type web address" }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                RudyIcons.Search(tint = palette.accent, size = 16.dp)
            }

            Text(
                text = "Search the web or type URL…",
                style = RudyTypography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = if (hasWallpaper) Color.White.copy(alpha = 0.80f) else palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (hasWallpaper) Color.White.copy(alpha = 0.18f)
                        else palette.surfaceElevated
                    )
                    .border(
                        1.dp,
                        if (hasWallpaper) Color.White.copy(alpha = 0.28f)
                        else palette.outlineSubtle,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "↵ Search",
                    style = RudyTypography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    color = if (hasWallpaper) Color.White else palette.accent
                )
            }
        }
    }
}

/**
 * Unified Segmented Glass Action Strip for fast one-tap browser workflows.
 */
@Composable
private fun HomeActionPillsRow(
    actions: BrowserHome.Actions,
    hasWallpaper: Boolean,
    wide: Boolean
) {
    val palette = RudyTheme.palette

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (hasWallpaper) Color.Black.copy(alpha = 0.45f) else palette.surfaceElevated,
        border = BorderStroke(
            1.dp,
            if (hasWallpaper) Color.White.copy(alpha = 0.18f) else palette.outlineSubtle
        ),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionSegment(
                icon = { RudyIcons.Plus(size = 14.dp, tint = palette.accent) },
                label = "New Tab",
                onClick = actions::newTab,
                hasWallpaper = hasWallpaper,
                palette = palette,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(
                        if (hasWallpaper) Color.White.copy(alpha = 0.15f)
                        else palette.outlineSubtle.copy(alpha = 0.5f)
                    )
            )

            ActionSegment(
                icon = { RudyIcons.Lock(size = 14.dp, tint = palette.accent) },
                label = "Private",
                onClick = actions::newPrivateTab,
                hasWallpaper = hasWallpaper,
                palette = palette,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(
                        if (hasWallpaper) Color.White.copy(alpha = 0.15f)
                        else palette.outlineSubtle.copy(alpha = 0.5f)
                    )
            )

            ActionSegment(
                icon = { RudyIcons.Bookmark(size = 14.dp, tint = palette.accent) },
                label = "Bookmarks",
                onClick = actions::openBookmarks,
                hasWallpaper = hasWallpaper,
                palette = palette,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(
                        if (hasWallpaper) Color.White.copy(alpha = 0.15f)
                        else palette.outlineSubtle.copy(alpha = 0.5f)
                    )
            )

            ActionSegment(
                icon = { RudyIcons.History(size = 14.dp, tint = palette.accent) },
                label = "History",
                onClick = actions::openHistory,
                hasWallpaper = hasWallpaper,
                palette = palette,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActionSegment(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    hasWallpaper: Boolean,
    palette: RudyPalette,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = RudyTypography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            ),
            color = if (hasWallpaper) Color.White else palette.foreground,
            maxLines = 1
        )
    }
}

/**
 * Styling helper mapping top brands to distinctive luxury colors and monograms.
 */
internal object QuickSiteStyles {
    data class Brand(
        val backgroundBrush: Brush,
        val markColor: Color,
        val isIconWhite: Boolean = true
    )

    fun forSite(site: QuickSite, isDark: Boolean): Brand {
        val h = site.host.lowercase()
        return when {
            h.contains("google") -> Brand(
                backgroundBrush = Brush.verticalGradient(
                    colors = if (isDark) listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                    else listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9))
                ),
                markColor = Color(0xFF4285F4),
                isIconWhite = isDark
            )
            h.contains("youtube") -> Brand(
                backgroundBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFF0033), Color(0xFFCC0000))
                ),
                markColor = Color.White
            )
            h.contains("wikipedia") -> Brand(
                backgroundBrush = Brush.verticalGradient(
                    colors = if (isDark) listOf(Color(0xFF334155), Color(0xFF1E293B))
                    else listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
                ),
                markColor = if (isDark) Color.White else Color(0xFF1E293B),
                isIconWhite = isDark
            )
            h.contains("github") -> Brand(
                backgroundBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF24292F), Color(0xFF0F1419))
                ),
                markColor = Color.White
            )
            h.contains("reddit") -> Brand(
                backgroundBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFF5722), Color(0xFFFF3D00))
                ),
                markColor = Color.White
            )
            h.contains("bing") -> Brand(
                backgroundBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF008373), Color(0xFF005B50))
                ),
                markColor = Color.White
            )
            h.contains("duckduckgo") -> Brand(
                backgroundBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFDE5833), Color(0xFFC0392B))
                ),
                markColor = Color.White
            )
            h.contains("x.com") || h.contains("twitter") -> Brand(
                backgroundBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF111827), Color(0xFF000000))
                ),
                markColor = Color.White
            )
            h.contains("linear") -> Brand(
                backgroundBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF5E6AD2), Color(0xFF4338CA))
                ),
                markColor = Color.White
            )
            else -> {
                val seed = kotlin.math.abs(site.label.hashCode()) % 6
                val colors = when (seed) {
                    0 -> listOf(Color(0xFF059669), Color(0xFF047857)) // Emerald
                    1 -> listOf(Color(0xFF4F46E5), Color(0xFF3730A3)) // Indigo
                    2 -> listOf(Color(0xFF7C3AED), Color(0xFF5B21B6)) // Violet
                    3 -> listOf(Color(0xFFE11D48), Color(0xFFBE123C)) // Rose
                    4 -> listOf(Color(0xFFD97706), Color(0xFFB45309)) // Amber
                    else -> listOf(Color(0xFF0891B2), Color(0xFF0E7490)) // Cyan
                }
                Brand(
                    backgroundBrush = Brush.verticalGradient(colors = colors),
                    markColor = Color.White
                )
            }
        }
    }
}

/**
 * World-class Speed Dial Springboard Section (Arc/Safari style app icons).
 */
@Composable
private fun SpeedDialSection(
    sites: List<QuickSite>,
    wide: Boolean,
    hasWallpaper: Boolean,
    onOpenSite: (QuickSite) -> Unit,
    onEditSite: (QuickSite) -> Unit,
    onAddClick: () -> Unit
) {
    val palette = RudyTheme.palette
    val columns = if (wide) 6 else 4
    val allTiles: List<QuickSite?> = sites + listOf(null)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RudySpacing.xs)
            ) {
                Text(
                    text = "FAVORITES",
                    style = RudyTypography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = if (hasWallpaper) Color.White.copy(alpha = 0.85f) else palette.muted
                )
                Text(
                    text = "${sites.size}",
                    style = RudyTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = palette.accent
                )
            }

            Text(
                text = "Hold to edit",
                style = RudyTypography.bodySmall.copy(fontSize = 11.sp),
                color = if (hasWallpaper) Color.White.copy(alpha = 0.6f) else palette.muted.copy(alpha = 0.7f)
            )
        }

        Spacer(Modifier.height(RudySpacing.md))

        // Multi-column App Icon Springboard Grid
        allTiles.chunked(columns).forEachIndexed { rowIndex, rowGroup ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                rowGroup.forEach { site ->
                    if (site != null) {
                        QuickSiteSpringboardTile(
                            site = site,
                            isDark = palette.isDark,
                            hasWallpaper = hasWallpaper,
                            palette = palette,
                            onClick = { onOpenSite(site) },
                            onLongClick = { onEditSite(site) }
                        )
                    } else {
                        AddShortcutSpringboardTile(
                            isDark = palette.isDark,
                            hasWallpaper = hasWallpaper,
                            palette = palette,
                            onClick = onAddClick
                        )
                    }
                }
                if (rowGroup.size < columns) {
                    repeat(columns - rowGroup.size) {
                        Spacer(modifier = Modifier.width(76.dp))
                    }
                }
            }
            if (rowIndex < allTiles.chunked(columns).lastIndex) {
                Spacer(Modifier.height(RudySpacing.md))
            }
        }
    }
}

/**
 * Individual Squircle App Icon in the Springboard Grid.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickSiteSpringboardTile(
    site: QuickSite,
    isDark: Boolean,
    hasWallpaper: Boolean,
    palette: RudyPalette,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val brand = remember(site, isDark) { QuickSiteStyles.forSite(site, isDark) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .padding(vertical = RudySpacing.xs)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics { contentDescription = "Open ${site.label}" }
    ) {
        // 56x56 Squircle App Icon
        Surface(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                1.dp,
                if (brand.isIconWhite) Color.White.copy(alpha = 0.22f)
                else palette.outlineSubtle.copy(alpha = 0.5f)
            ),
            shadowElevation = 3.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brand.backgroundBrush),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = site.mark,
                    style = TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (site.mark.length > 2) 13.sp else 18.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = brand.markColor
                )
            }
        }

        Spacer(Modifier.height(7.dp))

        Text(
            text = site.label,
            style = RudyTypography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp,
                shadow = if (hasWallpaper) androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.85f),
                    offset = androidx.compose.ui.geometry.Offset(0f, 1.5f),
                    blurRadius = 3f
                ) else null
            ),
            color = if (hasWallpaper) Color.White else palette.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Add Shortcut squircle tile with dashed/subtle border.
 */
@Composable
private fun AddShortcutSpringboardTile(
    isDark: Boolean,
    hasWallpaper: Boolean,
    palette: RudyPalette,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .padding(vertical = RudySpacing.xs)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = "Add custom shortcut" }
    ) {
        Surface(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = if (hasWallpaper) Color.Black.copy(alpha = 0.35f) else palette.surfaceElevated.copy(alpha = 0.6f),
            border = BorderStroke(
                1.dp,
                if (hasWallpaper) Color.White.copy(alpha = 0.25f) else palette.outlineSubtle.copy(alpha = 0.7f)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                RudyIcons.Plus(
                    size = 20.dp,
                    tint = if (hasWallpaper) Color.White.copy(alpha = 0.85f) else palette.accent
                )
            }
        }

        Spacer(Modifier.height(7.dp))

        Text(
            text = "Add",
            style = RudyTypography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                shadow = if (hasWallpaper) androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.85f),
                    offset = androidx.compose.ui.geometry.Offset(0f, 1.5f),
                    blurRadius = 3f
                ) else null
            ),
            color = if (hasWallpaper) Color.White.copy(alpha = 0.9f) else palette.muted,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Section for Recent Visits or Bookmarks list on Home.
 */
@Composable
private fun HomeLinkSection(
    title: String,
    viewAllText: String,
    onViewAllClick: () -> Unit,
    items: List<HomeLinkItem>,
    onOpen: (HomeLinkItem) -> Unit,
    hasWallpaper: Boolean,
    icon: @Composable () -> Unit
) {
    val palette = RudyTheme.palette

    // Filter out invalid, blank, or error items
    val validItems = remember(items) {
        items.filter {
            !it.url.startsWith("about:") &&
            !it.title.equals("about:blank", ignoreCase = true) &&
            !it.title.equals("Webpage not available", ignoreCase = true) &&
            !it.title.equals("https", ignoreCase = true) &&
            !it.host.equals("https", ignoreCase = true) &&
            it.url.contains(".") &&
            it.url.length > 8
        }
    }
    if (validItems.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = RudyTypography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = if (hasWallpaper) Color.White.copy(alpha = 0.85f) else palette.muted
            )
            Text(
                text = viewAllText,
                style = RudyTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = palette.accent,
                modifier = Modifier
                    .clip(RudyCorners.badge)
                    .clickable(onClick = onViewAllClick)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Spacer(Modifier.height(RudySpacing.sm))

        Surface(
            shape = RudyCorners.tile,
            color = if (hasWallpaper) Color.Black.copy(alpha = 0.45f) else palette.surfaceElevated,
            border = BorderStroke(
                1.dp,
                if (hasWallpaper) Color.White.copy(alpha = 0.15f) else palette.outlineSubtle
            ),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                validItems.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(item) }
                            .padding(horizontal = RudySpacing.md, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RudySpacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (hasWallpaper) Color.White.copy(alpha = 0.12f)
                                    else palette.background
                                )
                                .border(
                                    1.dp,
                                    if (hasWallpaper) Color.White.copy(alpha = 0.18f)
                                    else palette.outlineSubtle,
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            icon()
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = RudyTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (hasWallpaper) Color.White else palette.foreground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.host,
                                style = RudyMonoStyle.copy(fontSize = 11.sp),
                                color = if (hasWallpaper) Color.White.copy(alpha = 0.65f) else palette.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = "›",
                            style = RudyTypography.titleSmall,
                            color = if (hasWallpaper) Color.White.copy(alpha = 0.4f) else palette.muted.copy(alpha = 0.5f)
                        )
                    }

                    if (index < validItems.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            thickness = RudyDimensions.hairline,
                            color = if (hasWallpaper) Color.White.copy(alpha = 0.1f) else palette.outlineSubtle.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Footer disclaimer on Home screen.
 */
@Composable
private fun HomeFooterNote(
    hasWallpaper: Boolean = false
) {
    val palette = RudyTheme.palette

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RudyIcons.Lock(
            tint = if (hasWallpaper) Color.White.copy(alpha = 0.5f) else palette.muted.copy(alpha = 0.45f),
            size = 13.dp
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Privacy first • Zero telemetry • Fast local rendering",
            style = RudyTypography.bodySmall,
            color = if (hasWallpaper) Color.White.copy(alpha = 0.6f) else palette.muted.copy(alpha = 0.6f)
        )
    }
}

/**
 * Bottom Sheet Dialog to Add or Edit a Speed Dial Shortcut.
 */
@Composable
private fun AddEditQuickSiteDialog(
    existingSite: QuickSite?,
    onDismiss: () -> Unit,
    onSave: (QuickSite) -> Unit,
    onDelete: (String) -> Unit
) {
    val palette = RudyTheme.palette
    var label by remember { mutableStateOf(existingSite?.label.orEmpty()) }
    var url by remember { mutableStateOf(existingSite?.url.orEmpty()) }
    var hasError by remember { mutableStateOf(false) }

    val mark = HomeStorage.makeMark(label.ifBlank { url })

    RudyBottomSheet(
        title = if (existingSite == null) "Add Quick Shortcut" else "Edit Shortcut",
        subtitle = "Custom website link for your Home screen",
        icon = { RudyIcons.Bookmark(tint = palette.accent, size = 20.dp) },
        onDismiss = onDismiss,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (existingSite != null) {
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RudyCorners.control)
                            .background(palette.danger.copy(alpha = 0.12f))
                            .border(RudyDimensions.hairline, palette.danger.copy(alpha = 0.35f), RudyCorners.control)
                            .clickable { onDelete(existingSite.id) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RudyIcons.Trash(tint = palette.danger, size = 15.dp)
                            Text(
                                text = "Delete",
                                style = RudyTypography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.danger
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(RudySpacing.xs)) {
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RudyCorners.control)
                            .background(palette.surfaceElevated)
                            .border(RudyDimensions.hairline, palette.outlineSubtle, RudyCorners.control)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            style = RudyTypography.bodyMedium,
                            color = palette.muted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RudyCorners.control)
                            .background(palette.accent)
                            .clickable {
                                val trimmedUrl = url.trim()
                                if (trimmedUrl.isBlank()) {
                                    hasError = true
                                    return@clickable
                                }
                                val finalUrl = if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
                                    trimmedUrl
                                } else {
                                    "https://$trimmedUrl"
                                }
                                val finalLabel = label.trim().ifBlank { HomeStorage.parseHost(finalUrl) }
                                val site = QuickSite(
                                    id = existingSite?.id ?: UUID.randomUUID().toString(),
                                    label = finalLabel,
                                    host = HomeStorage.parseHost(finalUrl),
                                    url = finalUrl,
                                    mark = mark,
                                    isCustom = true
                                )
                                onSave(site)
                            }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Save Shortcut",
                            style = RudyTypography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.onAccent
                        )
                    }
                }
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(RudySpacing.md)
        ) {
            // Live Preview Card
            Text(
                text = "PREVIEW",
                style = RudyTypography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.muted
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RudyCorners.modal)
                    .background(palette.surfaceElevated)
                    .border(RudyDimensions.hairline, palette.outlineSubtle, RudyCorners.modal)
                    .padding(RudySpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RudySpacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RudyCorners.control)
                        .background(palette.accent.copy(alpha = 0.15f))
                        .border(RudyDimensions.hairline, palette.accent.copy(alpha = 0.4f), RudyCorners.control),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mark,
                        style = RudyTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label.ifBlank { "Website Name" },
                        style = RudyTypography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = url.ifBlank { "website.com" },
                        style = RudyMonoStyle,
                        color = palette.muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Label Field
            Column(verticalArrangement = Arrangement.spacedBy(RudySpacing.compact)) {
                Text(
                    text = "Shortcut Name",
                    style = RudyTypography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.foreground
                )
                BasicTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = palette.foreground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RudyCorners.control)
                        .background(palette.omnibox)
                        .border(RudyDimensions.hairline, palette.outlineSubtle, RudyCorners.control)
                        .padding(horizontal = RudySpacing.md, vertical = 12.dp)
                )
            }

            // URL Field
            Column(verticalArrangement = Arrangement.spacedBy(RudySpacing.compact)) {
                Text(
                    text = "Web Address / URL",
                    style = RudyTypography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.foreground
                )
                BasicTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        hasError = false
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = palette.foreground,
                        fontSize = 14.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RudyCorners.control)
                        .background(palette.omnibox)
                        .border(
                            RudyDimensions.hairline,
                            if (hasError) palette.danger else palette.outlineSubtle,
                            RudyCorners.control
                        )
                        .padding(horizontal = RudySpacing.md, vertical = 12.dp)
                )
                if (hasError) {
                    Text(
                        text = "Please enter a valid website address.",
                        style = RudyTypography.bodySmall,
                        color = palette.danger
                    )
                }
            }
        }
    }
}

/**
 * Bottom Sheet Dialog to customize Home start page sections and preferences.
 */
@Composable
private fun CustomizeHomeDialog(
    currentPreferences: HomePreferences,
    activeWallpaper: String,
    onDismiss: () -> Unit,
    onUpdatePreferences: (HomePreferences) -> Unit,
    onUpdateWallpaper: (String) -> Unit,
    onResetSpeedDial: () -> Unit
) {
    val palette = RudyTheme.palette
    var prefsState by remember { mutableStateOf(currentPreferences) }

    RudyBottomSheet(
        title = "Customize Home",
        subtitle = "Tailor your start page & atmosphere",
        icon = { RudyIcons.Sliders(tint = palette.accent, size = 20.dp) },
        onDismiss = onDismiss,
        footer = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RudyCorners.control)
                    .background(palette.accent)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Done",
                    style = RudyTypography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onAccent
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(RudySpacing.lg)
        ) {
            // Wallpaper & Atmosphere Section (Unsplash - Free Commercial Use)
            Text(
                text = "WALLPAPER & ATMOSPHERE",
                style = RudyTypography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.muted
            )

            WallpaperPickerSection(
                activeWallpaper = activeWallpaper,
                onSelectWallpaper = onUpdateWallpaper
            )

            Text(
                text = "START PAGE SECTIONS",
                style = RudyTypography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.muted
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RudyCorners.modal)
                    .background(palette.surfaceElevated)
                    .border(RudyDimensions.hairline, palette.outlineSubtle, RudyCorners.modal)
            ) {
                HomeToggleRow(
                    title = "Quick Access (Speed Dial)",
                    subtitle = "Grid of frequently used website shortcuts",
                    checked = prefsState.showSpeedDial,
                    onCheckedChange = {
                        val updated = prefsState.copy(showSpeedDial = it)
                        prefsState = updated
                        onUpdatePreferences(updated)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(start = RudySpacing.md), thickness = RudyDimensions.hairline, color = palette.outlineSubtle)

                HomeToggleRow(
                    title = "Quick Action Pills",
                    subtitle = "One-tap New Tab, Private, Bookmarks & History",
                    checked = prefsState.showShortcutPills,
                    onCheckedChange = {
                        val updated = prefsState.copy(showShortcutPills = it)
                        prefsState = updated
                        onUpdatePreferences(updated)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(start = RudySpacing.md), thickness = RudyDimensions.hairline, color = palette.outlineSubtle)

                HomeToggleRow(
                    title = "Recent Visits",
                    subtitle = "Quickly resume pages visited in normal tabs",
                    checked = prefsState.showRecentHistory,
                    onCheckedChange = {
                        val updated = prefsState.copy(showRecentHistory = it)
                        prefsState = updated
                        onUpdatePreferences(updated)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(start = RudySpacing.md), thickness = RudyDimensions.hairline, color = palette.outlineSubtle)

                HomeToggleRow(
                    title = "Saved Bookmarks",
                    subtitle = "Show your favorited bookmarks on Home",
                    checked = prefsState.showBookmarks,
                    onCheckedChange = {
                        val updated = prefsState.copy(showBookmarks = it)
                        prefsState = updated
                        onUpdatePreferences(updated)
                    }
                )
            }

            Text(
                text = "RESTORE DEFAULTS",
                style = RudyTypography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.muted
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RudyCorners.control)
                    .background(palette.surfaceElevated)
                    .border(RudyDimensions.hairline, palette.outlineSubtle, RudyCorners.control)
                    .clickable {
                        onResetSpeedDial()
                        val def = HomePreferences()
                        prefsState = def
                        onUpdatePreferences(def)
                    }
                    .padding(horizontal = RudySpacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Reset Speed Dial & Layout to Defaults",
                    style = RudyTypography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent
                )
            }
        }
    }
}

@Composable
private fun HomeToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = RudyTheme.palette

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = RudySpacing.md, vertical = RudySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = RudySpacing.md)) {
            Text(
                text = title,
                style = RudyTypography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.foreground
            )
            Text(
                text = subtitle,
                style = RudyTypography.bodySmall,
                color = palette.muted
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.onAccent,
                checkedTrackColor = palette.accent,
                uncheckedThumbColor = palette.muted,
                uncheckedTrackColor = palette.omnibox
            )
        )
    }
}

/**
 * Curated high-resolution wallpapers (Unsplash License - Free for commercial use).
 */
@Composable
private fun WallpaperPickerSection(
    activeWallpaper: String,
    onSelectWallpaper: (String) -> Unit
) {
    val palette = RudyTheme.palette
    val wallpapers = listOf(
        WallpaperOption("none", "None", "Clean solid", null, Color(0xFF1E293B)),
        WallpaperOption("wallpaper_nordic_aurora", "Nordic", "Twilight Fjord", "wallpaper_nordic_aurora", Color(0xFF0C2B38)),
        WallpaperOption("wallpaper_architecture", "Modern", "Brutalist Lines", "wallpaper_architecture", Color(0xFF1B2434)),
        WallpaperOption("wallpaper_deep_space", "Cosmos", "Deep Nebula", "wallpaper_deep_space", Color(0xFF110D29)),
        WallpaperOption("wallpaper_alpine_dawn", "Alpine", "Snowy Horizon", "wallpaper_alpine_dawn", Color(0xFF1E3554))
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(RudySpacing.sm)
        ) {
            wallpapers.forEach { wp ->
                val selected = activeWallpaper.equals(wp.id, ignoreCase = true)
                val context = LocalContext.current
                val resId = wp.drawableName?.let {
                    context.resources.getIdentifier(it, "drawable", context.packageName)
                } ?: 0

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(108.dp)
                        .clip(RudyCorners.control)
                        .clickable { onSelectWallpaper(wp.id) }
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .clip(RudyCorners.control)
                            .border(
                                width = if (selected) 2.dp else RudyDimensions.hairline,
                                color = if (selected) palette.accent else palette.outlineSubtle,
                                shape = RudyCorners.control
                            )
                            .background(wp.fallbackColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (resId != 0) {
                            Image(
                                painter = painterResource(id = resId),
                                contentDescription = wp.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.20f))
                            )
                        } else {
                            Text(
                                text = "Solid",
                                style = RudyTypography.labelMedium,
                                color = palette.muted
                            )
                        }

                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✓",
                                    style = RudyTypography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.onAccent
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = wp.title,
                        style = RudyTypography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) palette.accent else palette.foreground
                    )
                    Text(
                        text = if (wp.id == "none") "Default" else "Unsplash",
                        style = RudyTypography.labelMedium.copy(fontSize = 10.sp),
                        color = palette.muted
                    )
                }
            }
        }
    }
}

private data class WallpaperOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val drawableName: String?,
    val fallbackColor: Color
)
