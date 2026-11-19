package io.rudycity.tvbrowser.compose

import android.app.Activity
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rudycity.tvbrowser.BrowserChrome

/** Check if a URL represents an empty or start page (e.g. about:blank). */
internal fun isBlankOrHomeUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return true
    val lower = url.trim().lowercase()
    return lower == "about:blank" ||
           lower == "about:start" ||
           lower == "about:home" ||
           lower == "about:newtab" ||
           lower.startsWith("about:blank")
}

private data class ChromeSnapshot(
    val url: String = "",
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val bookmarked: Boolean = false,
    val tabCount: Int = 1
)

private data class TabSheetState(
    val items: List<BrowserChrome.TabItem>,
    val maxTabs: Int,
    val handler: BrowserChrome.TabSheetHandler
)

private data class HistorySheetState(
    val items: List<BrowserChrome.HistoryItem>,
    val handler: BrowserChrome.HistoryHandler
)

private data class BookmarkSheetState(
    val items: List<BrowserChrome.BookmarkItem>,
    val handler: BrowserChrome.BookmarkHandler
)

private data class SettingsSheetState(
    val state: BrowserChrome.SettingsState,
    val handler: BrowserChrome.SettingsHandler
)

private data class ExtensionSheetState(
    val items: List<BrowserChrome.ExtensionItem>,
    val currentHost: String,
    val handler: BrowserChrome.ExtensionManagerHandler
)

private data class ToolsSheetState(
    val state: BrowserChrome.ToolsState,
    val handler: BrowserChrome.ToolsHandler
)

/**
 * Modern minimalist toolbar for Rudy Browser.
 * Adheres to Rudy Design System v2: bespoke line iconography, cool-slate surfaces,
 * 48dp touch targets, WCAG AA contrast, and zero stock Material slop.
 */
class ComposeBrowserToolbar(
    activity: Activity,
    private val actions: BrowserChrome.Actions
) : BrowserChrome {

    private var darkMode by mutableStateOf(
        activity.getSharedPreferences("tv_browser", android.content.Context.MODE_PRIVATE)
            .getBoolean("dark_mode", true)
    )
    private var snapshot by mutableStateOf(ChromeSnapshot())
    private var tabItems by mutableStateOf<List<BrowserChrome.TabItem>>(emptyList())
    private var focusSignal by mutableIntStateOf(0)

    // Modal Sheet States
    private var tabSheet by mutableStateOf<TabSheetState?>(null)
    private var historySheet by mutableStateOf<HistorySheetState?>(null)
    private var bookmarkSheet by mutableStateOf<BookmarkSheetState?>(null)
    private var settingsSheet by mutableStateOf<SettingsSheetState?>(null)
    private var extensionSheet by mutableStateOf<ExtensionSheetState?>(null)
    private var toolsSheet by mutableStateOf<ToolsSheetState?>(null)

    // Common Dialog States
    private var actionSheet by mutableStateOf<ActionSheet?>(null)
    private var richActionSheet by mutableStateOf<RichActionSheet?>(null)
    private var confirmationSheet by mutableStateOf<ConfirmationRequest?>(null)
    private var securityConfirmationSheet by mutableStateOf<SecurityConfirmationRequest?>(null)
    private var aboutSheet by mutableStateOf<AboutInfo?>(null)

    private val composeView = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            RudyBrowserTheme(darkMode) {
                Surface(
                    color = RudyTheme.palette.toolbar,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isTablet = maxWidth >= 600.dp
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (isTablet && tabItems.isNotEmpty()) {
                                TabletTabStrip(
                                    tabs = tabItems,
                                    onSelect = { actions.selectTab(it) },
                                    onClose = { actions.closeTab(it) },
                                    onNewTab = { actions.newTab() }
                                )
                            }
                            BrowserMinimalistHeader(
                                snapshot = snapshot,
                                actions = actions,
                                focusSignal = focusSignal,
                                onOpenTabs = { actions.tabs() }
                            )
                        }
                    }
                }

                // Modal Bottom Sheet Overlays
                tabSheet?.let { sheet ->
                    BrowserTabManagerDialog(
                        items = sheet.items,
                        maxTabs = sheet.maxTabs,
                        handler = sheet.handler,
                        onDismiss = { tabSheet = null }
                    )
                }

                historySheet?.let { sheet ->
                    BrowserHistoryManagerDialog(
                        items = sheet.items,
                        handler = sheet.handler,
                        onDismiss = { historySheet = null }
                    )
                }

                bookmarkSheet?.let { sheet ->
                    BrowserBookmarkManagerDialog(
                        items = sheet.items,
                        handler = sheet.handler,
                        onDismiss = { bookmarkSheet = null }
                    )
                }

                settingsSheet?.let { sheet ->
                    BrowserSettingsDialog(
                        state = sheet.state,
                        handler = sheet.handler,
                        onDismiss = { settingsSheet = null }
                    )
                }

                extensionSheet?.let { sheet ->
                    BrowserExtensionManagerDialog(
                        items = sheet.items,
                        currentHost = sheet.currentHost,
                        handler = sheet.handler,
                        onDismiss = { extensionSheet = null }
                    )
                }

                toolsSheet?.let { sheet ->
                    BrowserToolsManagerDialog(
                        state = sheet.state,
                        handler = sheet.handler,
                        onDismiss = { toolsSheet = null }
                    )
                }

                actionSheet?.let { sheet ->
                    BrowserActionsDialog(
                        sheet = sheet,
                        onDismiss = { actionSheet = null },
                        onChoice = { index ->
                            actionSheet = null
                            sheet.callback.onChoice(index)
                        }
                    )
                }

                richActionSheet?.let { sheet ->
                    BrowserRichActionsDialog(
                        sheet = sheet,
                        onDismiss = { richActionSheet = null },
                        onChoice = { index ->
                            richActionSheet = null
                            sheet.callback.onChoice(index)
                        }
                    )
                }

                confirmationSheet?.let { request ->
                    BrowserConfirmationDialog(
                        request = request,
                        onDismiss = { confirmationSheet = null },
                        onConfirm = {
                            confirmationSheet = null
                            request.callback.run()
                        }
                    )
                }

                securityConfirmationSheet?.let { request ->
                    BrowserSecurityConfirmationDialog(
                        request = request,
                        onDismiss = { securityConfirmationSheet = null },
                        onConfirm = {
                            securityConfirmationSheet = null
                            request.callback.run()
                        }
                    )
                }

                aboutSheet?.let { info ->
                    BrowserAboutDialog(
                        info = info,
                        onDismiss = { aboutSheet = null }
                    )
                }
            }
        }
    }

    override fun view(): View = composeView

    override fun setDarkTheme(dark: Boolean) { darkMode = dark }

    override fun focusAddress() { focusSignal += 1 }

    override fun update(
        url: String?,
        loading: Boolean,
        progress: Int,
        canGoBack: Boolean,
        canGoForward: Boolean,
        bookmarked: Boolean
    ) {
        update(url, loading, progress, canGoBack, canGoForward, bookmarked, snapshot.tabCount)
    }

    override fun update(
        url: String?,
        loading: Boolean,
        progress: Int,
        canGoBack: Boolean,
        canGoForward: Boolean,
        bookmarked: Boolean,
        tabCount: Int
    ) {
        snapshot = ChromeSnapshot(
            url = url.orEmpty(),
            loading = loading,
            progress = progress.coerceIn(0, 100),
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            bookmarked = bookmarked,
            tabCount = tabCount.coerceAtLeast(1)
        )
    }

    override fun updateTabs(items: MutableList<BrowserChrome.TabItem>?, maxTabs: Int) {
        tabItems = items?.toList().orEmpty()
        if (items != null && items.isNotEmpty()) {
            snapshot = snapshot.copy(tabCount = items.size)
        }
    }

    override fun showTabManager(
        items: MutableList<BrowserChrome.TabItem>?,
        maxTabs: Int,
        handler: BrowserChrome.TabSheetHandler?
    ): Boolean {
        if (items == null || handler == null) return false
        tabSheet = TabSheetState(items.toList(), maxTabs, handler)
        return true
    }

    override fun showHistoryManager(
        items: MutableList<BrowserChrome.HistoryItem>?,
        handler: BrowserChrome.HistoryHandler?
    ): Boolean {
        if (items == null || handler == null) return false
        historySheet = HistorySheetState(items.toList(), handler)
        return true
    }

    override fun showBookmarkManager(
        items: MutableList<BrowserChrome.BookmarkItem>?,
        handler: BrowserChrome.BookmarkHandler?
    ): Boolean {
        if (items == null || handler == null) return false
        bookmarkSheet = BookmarkSheetState(items.toList(), handler)
        return true
    }

    override fun showSettings(
        state: BrowserChrome.SettingsState?,
        handler: BrowserChrome.SettingsHandler?
    ): Boolean {
        if (state == null || handler == null) return false
        settingsSheet = SettingsSheetState(state, handler)
        return true
    }

    override fun showExtensionManager(
        items: MutableList<BrowserChrome.ExtensionItem>?,
        currentHost: String?,
        handler: BrowserChrome.ExtensionManagerHandler?
    ): Boolean {
        if (items == null || handler == null) return false
        extensionSheet = ExtensionSheetState(items.toList(), currentHost.orEmpty(), handler)
        return true
    }

    override fun showToolsManager(
        state: BrowserChrome.ToolsState?,
        handler: BrowserChrome.ToolsHandler?
    ): Boolean {
        if (state == null || handler == null) return false
        toolsSheet = ToolsSheetState(state, handler)
        return true
    }

    override fun showActions(
        title: String,
        labels: Array<String>,
        handler: BrowserChrome.ChoiceHandler
    ) {
        if (labels.isNotEmpty()) actionSheet = ActionSheet(title, labels.toList(), handler)
    }

    override fun showRichActions(
        title: String?,
        items: MutableList<BrowserChrome.ActionItem>?,
        emptyTitle: String?,
        emptyDetail: String?,
        handler: BrowserChrome.ChoiceHandler?
    ): Boolean {
        if (items == null || handler == null) return false
        richActionSheet = RichActionSheet(
            title = title.orEmpty(),
            items = items.toList(),
            emptyTitle = emptyTitle.orEmpty(),
            emptyDetail = emptyDetail.orEmpty(),
            callback = handler
        )
        return true
    }

    override fun showConfirmation(
        title: String,
        message: String,
        actionLabel: String,
        onConfirm: Runnable
    ) {
        confirmationSheet = ConfirmationRequest(title, message, actionLabel, onConfirm)
    }

    override fun showSecurityConfirmation(
        title: String?,
        summary: String?,
        sourceLabel: String?,
        destinationLabel: String?,
        warning: String?,
        safeDismissLabel: String?,
        confirmLabel: String?,
        destructive: Boolean,
        onConfirm: Runnable?
    ): Boolean {
        if (title == null || onConfirm == null) return false
        securityConfirmationSheet = SecurityConfirmationRequest(
            title = title,
            summary = summary.orEmpty(),
            sourceLabel = sourceLabel.orEmpty(),
            destinationLabel = destinationLabel.orEmpty(),
            warning = warning.orEmpty(),
            safeDismissLabel = safeDismissLabel ?: "Cancel",
            confirmLabel = confirmLabel ?: "Confirm",
            destructive = destructive,
            callback = onConfirm
        )
        return true
    }

    override fun showAbout(
        appName: String?,
        versionName: String?,
        packageId: String?,
        flavor: String?,
        androidSdk: Int,
        activeEngine: String?,
        engineDetail: String?,
        cleartextAllowed: Boolean,
        openTabs: Int
    ): Boolean {
        aboutSheet = AboutInfo(
            appName = appName.orEmpty(),
            versionName = versionName.orEmpty(),
            packageId = packageId.orEmpty(),
            flavor = flavor.orEmpty(),
            androidSdk = androidSdk,
            activeEngine = activeEngine.orEmpty(),
            engineDetail = engineDetail.orEmpty(),
            cleartextAllowed = cleartextAllowed,
            openTabs = openTabs
        )
        return true
    }
}

/**
 * Flagship Minimalist Header:
 * [Back] [Omnibox (takes 70-75% screen width)] [Tab Counter Badge] [··· More]
 */
@Composable
private fun BrowserMinimalistHeader(
    snapshot: ChromeSnapshot,
    actions: BrowserChrome.Actions,
    focusSignal: Int,
    onOpenTabs: () -> Unit
) {
    val palette = RudyTheme.palette
    var showMenuSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(RudyDimensions.toolbarHeight)
                .padding(horizontal = RudySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RudySpacing.xs)
        ) {
            // Sleek Back Button
            Box(
                modifier = Modifier
                    .size(RudyDimensions.touchTarget)
                    .clip(RudyCorners.control)
                    .clickable(enabled = snapshot.canGoBack) { actions.back() }
                    .semantics { contentDescription = "Navigate back" },
                contentAlignment = Alignment.Center
            ) {
                RudyIcons.Back(
                    tint = if (snapshot.canGoBack) palette.foreground else palette.muted.copy(alpha = 0.35f),
                    size = RudyDimensions.iconSize
                )
            }

            // Spacious Omnibox (Taking all available horizontal space)
            Box(modifier = Modifier.weight(1f)) {
                MinimalistOmnibox(
                    snapshot = snapshot,
                    onNavigate = { actions.navigate(it) },
                    focusSignal = focusSignal
                )
            }

            // Tab Counter Button (26x26 dp squircle)
            TabCounterButton(
                count = snapshot.tabCount,
                onClick = onOpenTabs
            )

            // More Menu Button (···)
            Box(
                modifier = Modifier
                    .size(RudyDimensions.touchTarget)
                    .clip(RudyCorners.control)
                    .clickable { showMenuSheet = true }
                    .semantics { contentDescription = "Open browser options" },
                contentAlignment = Alignment.Center
            ) {
                RudyIcons.More(tint = palette.foreground, size = RudyDimensions.iconSize)
            }
        }

        // Hairline Progress Indicator
        if (snapshot.loading) {
            LinearProgressIndicator(
                progress = { snapshot.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RudyDimensions.progressHeight),
                color = palette.accent,
                trackColor = Color.Transparent
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RudyDimensions.hairline)
                    .background(palette.outlineSubtle.copy(alpha = 0.4f))
            )
        }
    }

    // Modern Flagship Control Center Menu Bottom Sheet
    if (showMenuSheet) {
        BrowserMenuBottomSheet(
            snapshot = snapshot,
            actions = actions,
            onDismiss = { showMenuSheet = false }
        )
    }
}

/**
 * Modern Omnibox with Lock/Search icon, editing clear button, and focus elevation.
 * Automatically clears (auto-deletes) about:blank/start URLs on click/focus,
 * and automatically selects-all ("di-blok") any website URL for immediate replacement.
 */
@Composable
private fun MinimalistOmnibox(
    snapshot: ChromeSnapshot,
    onNavigate: (String) -> Unit,
    focusSignal: Int
) {
    val palette = RudyTheme.palette
    val isBlankPage = isBlankOrHomeUrl(snapshot.url)

    // Formatted host for non-focused display (clean domain, never "about:blank")
    val displayHost = remember(snapshot.url) {
        if (isBlankOrHomeUrl(snapshot.url)) {
            ""
        } else {
            val clean = snapshot.url.removePrefix("https://").removePrefix("http://")
            clean.substringBefore('/')
        }
    }

    var textFieldValue by remember {
        mutableStateOf(
            if (isBlankPage) TextFieldValue("")
            else TextFieldValue(displayHost)
        )
    }
    var hasFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Sync state when URL changes externally while not actively typing
    LaunchedEffect(snapshot.url) {
        if (!hasFocus) {
            val isBlank = isBlankOrHomeUrl(snapshot.url)
            textFieldValue = if (isBlank) {
                TextFieldValue("")
            } else {
                TextFieldValue(displayHost)
            }
        }
    }

    // When focus is requested from external trigger (e.g. Home search bar or shortcut)
    LaunchedEffect(focusSignal) {
        if (focusSignal > 0) {
            focusRequester.requestFocus()
            val isBlank = isBlankOrHomeUrl(snapshot.url)
            textFieldValue = if (isBlank) {
                // Auto-delete: blank/about:blank is cleared immediately
                TextFieldValue(text = "", selection = TextRange.Zero)
            } else {
                // Auto select-all: URL is fully "di-blok" for instant overwrite
                val fullUrl = snapshot.url
                TextFieldValue(text = fullUrl, selection = TextRange(0, fullUrl.length))
            }
            keyboard?.show()
        }
    }

    val isSecure = snapshot.url.startsWith("https://", ignoreCase = true)
    val omniboxBackground = if (hasFocus) palette.omniboxFocused else palette.omnibox
    val borderColor = if (hasFocus) palette.accent else palette.outlineSubtle.copy(alpha = 0.6f)

    Surface(
        color = omniboxBackground,
        shape = RudyCorners.omnibox,
        modifier = Modifier
            .fillMaxWidth()
            .height(RudyDimensions.omniboxHeight)
            .border(RudyDimensions.hairline, borderColor, RudyCorners.omnibox)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RudySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RudySpacing.sm)
        ) {
            // Lock or Search icon
            if (isSecure && !isBlankPage) {
                RudyIcons.Lock(size = 16.dp, tint = palette.positive)
            } else {
                RudyIcons.Search(size = 16.dp, tint = palette.muted)
            }

            // Address Input with auto-select and auto-delete
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                },
                singleLine = true,
                textStyle = RudyTypography.bodyMedium.copy(color = palette.foreground),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Go,
                    keyboardType = KeyboardType.Uri
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        val target = textFieldValue.text.trim()
                        if (target.isNotBlank()) {
                            onNavigate(target)
                        }
                    }
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                text = "Search or type web address…",
                                style = RudyTypography.bodyMedium,
                                color = palette.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        val wasFocused = hasFocus
                        hasFocus = state.isFocused
                        if (state.isFocused && !wasFocused) {
                            val isBlank = isBlankOrHomeUrl(snapshot.url)
                            if (isBlank) {
                                // Auto delete: blank URL is cleared immediately
                                textFieldValue = TextFieldValue(text = "", selection = TextRange.Zero)
                            } else {
                                // Auto select-all: URL is fully "di-blok"
                                val urlToEdit = snapshot.url
                                textFieldValue = TextFieldValue(
                                    text = urlToEdit,
                                    selection = TextRange(0, urlToEdit.length)
                                )
                            }
                        } else if (!state.isFocused && wasFocused) {
                            // On blur, revert to host display if not empty
                            val isBlank = isBlankOrHomeUrl(snapshot.url)
                            if (isBlank) {
                                textFieldValue = TextFieldValue("")
                            } else {
                                val clean = snapshot.url.removePrefix("https://").removePrefix("http://")
                                textFieldValue = TextFieldValue(clean.substringBefore('/'))
                            }
                        }
                    }
            )

            // Clear button while focused and has text
            if (hasFocus && textFieldValue.text.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RudyCorners.badge)
                        .clickable {
                            textFieldValue = TextFieldValue(text = "", selection = TextRange.Zero)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    RudyIcons.Clear(size = 14.dp, tint = palette.muted)
                }
            }
        }
    }
}

/**
 * 26x26 dp squircle tab count button.
 */
@Composable
private fun TabCounterButton(
    count: Int,
    onClick: () -> Unit
) {
    val palette = RudyTheme.palette
    val formattedCount = when {
        count <= 0 -> "1"
        count > 99 -> ":D"
        else -> count.toString()
    }

    Box(
        modifier = Modifier
            .size(RudyDimensions.touchTarget),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RudyCorners.badge)
                .background(palette.surfaceElevated, RudyCorners.badge)
                .border(1.6.dp, palette.foreground.copy(alpha = 0.85f), RudyCorners.badge)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Open tab manager, $count open tabs" },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formattedCount,
                style = RudyTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = palette.foreground
            )
        }
    }
}

/**
 * Flagship Control Center Menu (Modal Bottom Sheet).
 * Replaces crude standard dropdown menus with a world-class browser action sheet.
 */
@Composable
private fun BrowserMenuBottomSheet(
    snapshot: ChromeSnapshot,
    actions: BrowserChrome.Actions,
    onDismiss: () -> Unit
) {
    val palette = RudyTheme.palette

    RudyBottomSheet(
        title = "Rudy Browser",
        subtitle = snapshot.url.ifEmpty { "Fast, secure and private web browsing" },
        icon = { RudyIcons.RudyMark(size = 22.dp) },
        onDismiss = onDismiss
    ) {
        // Quick 4-Action Pill Bar
        Surface(
            color = palette.surfaceElevated,
            shape = RudyCorners.tile,
            modifier = Modifier
                .fillMaxWidth()
                .border(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.6f), RudyCorners.tile)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = RudySpacing.sm, horizontal = RudySpacing.xs),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickIconAction(
                    label = "Reload",
                    icon = { RudyIcons.Reload(size = 20.dp, tint = palette.accent) },
                    onClick = {
                        onDismiss()
                        actions.reload()
                    }
                )
                QuickIconAction(
                    label = if (snapshot.bookmarked) "Saved" else "Bookmark",
                    icon = {
                        RudyIcons.Star(
                            filled = snapshot.bookmarked,
                            size = 20.dp,
                            tint = if (snapshot.bookmarked) palette.accent else palette.foreground
                        )
                    },
                    onClick = {
                        onDismiss()
                        actions.bookmark()
                    }
                )
                QuickIconAction(
                    label = "Home",
                    icon = { RudyIcons.Home(size = 20.dp, tint = palette.foreground) },
                    onClick = {
                        onDismiss()
                        actions.home()
                    }
                )
                QuickIconAction(
                    label = "Forward",
                    icon = {
                        RudyIcons.Forward(
                            size = 20.dp,
                            tint = if (snapshot.canGoForward) palette.foreground else palette.muted.copy(alpha = 0.35f)
                        )
                    },
                    enabled = snapshot.canGoForward,
                    onClick = {
                        onDismiss()
                        actions.forward()
                    }
                )
            }
        }

        // Section 1: Library & Tabs
        Text(
            text = "LIBRARY & TABS",
            style = RudyTypography.labelMedium,
            color = palette.muted.copy(alpha = 0.85f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = RudySpacing.xs, top = RudySpacing.xs)
        )

        Surface(
            color = palette.surfaceElevated,
            shape = RudyCorners.tile,
            modifier = Modifier
                .fillMaxWidth()
                .border(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.6f), RudyCorners.tile)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                MenuRowItem(
                    title = "Tabs",
                    subtitle = "${snapshot.tabCount} open tab${if (snapshot.tabCount > 1) "s" else ""}",
                    icon = { RudyIcons.Tabs(size = 18.dp, tint = palette.accent) },
                    onClick = {
                        onDismiss()
                        actions.tabs()
                    }
                )
                MenuDivider()
                MenuRowItem(
                    title = "Bookmarks",
                    subtitle = "Saved websites & favorites",
                    icon = { RudyIcons.Bookmark(size = 18.dp, tint = palette.accent) },
                    onClick = {
                        onDismiss()
                        actions.bookmarks()
                    }
                )
                MenuDivider()
                MenuRowItem(
                    title = "Browsing History",
                    subtitle = "Recent pages & web visits",
                    icon = { RudyIcons.History(size = 18.dp, tint = palette.accent) },
                    onClick = {
                        onDismiss()
                        actions.history()
                    }
                )
            }
        }

        // Section 2: Tools & Add-ons
        Text(
            text = "TOOLS & EXTENSIONS",
            style = RudyTypography.labelMedium,
            color = palette.muted.copy(alpha = 0.85f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = RudySpacing.xs, top = RudySpacing.xs)
        )

        Surface(
            color = palette.surfaceElevated,
            shape = RudyCorners.tile,
            modifier = Modifier
                .fillMaxWidth()
                .border(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.6f), RudyCorners.tile)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                MenuRowItem(
                    title = "Extensions & Add-ons",
                    subtitle = "Ad blocker, dark mode, scripts & media",
                    icon = { RudyIcons.Extension(size = 18.dp, tint = palette.accent) },
                    onClick = {
                        onDismiss()
                        actions.extensions()
                    }
                )
                MenuDivider()
                MenuRowItem(
                    title = "All Browser Tools",
                    subtitle = "Zoom, PDF export, video player & engine",
                    icon = { RudyIcons.Tools(size = 18.dp, tint = palette.accent) },
                    onClick = {
                        onDismiss()
                        actions.menu()
                    }
                )
            }
        }

        // Section 3: Preferences & System
        Text(
            text = "SYSTEM",
            style = RudyTypography.labelMedium,
            color = palette.muted.copy(alpha = 0.85f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = RudySpacing.xs, top = RudySpacing.xs)
        )

        Surface(
            color = palette.surfaceElevated,
            shape = RudyCorners.tile,
            modifier = Modifier
                .fillMaxWidth()
                .border(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.6f), RudyCorners.tile)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                MenuRowItem(
                    title = "Settings",
                    subtitle = "Appearance, engines, permissions & privacy",
                    icon = { RudyIcons.Settings(size = 18.dp, tint = palette.accent) },
                    onClick = {
                        onDismiss()
                        actions.settings()
                    }
                )
                MenuDivider()
                MenuRowItem(
                    title = "About Rudy Browser",
                    subtitle = "Version, renderer architecture & licenses",
                    icon = { RudyIcons.Info(size = 18.dp, tint = palette.accent) },
                    onClick = {
                        onDismiss()
                        actions.about()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(RudySpacing.md))
    }
}

@Composable
private fun QuickIconAction(
    label: String,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val palette = RudyTheme.palette

    Column(
        modifier = Modifier
            .clip(RudyCorners.control)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = RudySpacing.sm, vertical = RudySpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RudyCorners.control)
                .background(palette.background),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Text(
            label,
            style = RudyTypography.bodySmall,
            color = if (enabled) palette.foreground else palette.muted.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun MenuRowItem(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val palette = RudyTheme.palette

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RudyCorners.tile)
            .clickable(onClick = onClick)
            .padding(horizontal = RudySpacing.md, vertical = RudySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = RudySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RudySpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RudyCorners.control)
                    .background(palette.background),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column {
                Text(title, style = RudyTypography.titleSmall, color = palette.foreground, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = RudyTypography.bodySmall, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Text("›", style = RudyTypography.titleMedium, color = palette.muted.copy(alpha = 0.6f))
    }
}

@Composable
private fun MenuDivider() {
    val palette = RudyTheme.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RudySpacing.md)
            .height(RudyDimensions.hairline)
            .background(palette.outlineSubtle.copy(alpha = 0.35f))
    )
}

/**
 * Tablet tab strip displayed above the toolbar on wide displays (>= 600dp).
 */
@Composable
private fun TabletTabStrip(
    tabs: List<BrowserChrome.TabItem>,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onNewTab: () -> Unit
) {
    val palette = RudyTheme.palette

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(palette.background)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = RudySpacing.xs, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RudySpacing.compact)
    ) {
        tabs.forEachIndexed { index, tab ->
            TabletTabChip(
                tab = tab,
                onClick = { onSelect(index) },
                onClose = { onClose(index) }
            )
        }

        // New Tab (+) button
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RudyCorners.badge)
                .clickable(onClick = onNewTab)
                .semantics { contentDescription = "Open new tab" },
            contentAlignment = Alignment.Center
        ) {
            RudyIcons.Plus(size = 16.dp, tint = palette.accent)
        }
    }
}

@Composable
private fun TabletTabChip(
    tab: BrowserChrome.TabItem,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val palette = RudyTheme.palette
    val bg = if (tab.active) palette.toolbar else palette.surfaceElevated.copy(alpha = 0.6f)
    val border = if (tab.active) palette.accent.copy(alpha = 0.5f) else palette.outlineSubtle.copy(alpha = 0.4f)

    Surface(
        color = bg,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
        modifier = Modifier
            .widthIn(min = 120.dp, max = 200.dp)
            .height(34.dp)
            .border(
                RudyDimensions.hairline,
                border,
                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RudySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = RudySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RudySpacing.xs)
            ) {
                if (tab.privateMode) {
                    RudyIcons.Lock(size = 12.dp, tint = palette.accent)
                }
                Text(
                    text = tab.title.ifEmpty { "New Tab" },
                    style = RudyTypography.bodySmall,
                    color = palette.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RudyCorners.badge)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                RudyIcons.Clear(size = 10.dp, tint = palette.muted)
            }
        }
    }
}
