package io.rudycity.tvbrowser.compose

import android.app.Activity
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rudycity.tvbrowser.BrowserChrome

/**
 * Kotlin + Jetpack Compose + Material 3 presentation only.
 * WebView, engine state, popup authorization, downloads, history and permissions remain
 * owned by the existing Java MainActivity so migrating the UI never duplicates browser data.
 *
 * Loaded reflectively by the preview/modern variant; legacy API 19 never loads this class.
 */
class ComposeBrowserToolbar(
    activity: Activity,
    private val actions: BrowserChrome.Actions
) : BrowserChrome {
    private var snapshot by mutableStateOf(ChromeSnapshot())
    private var focusSignal by mutableStateOf(0)

    private val composeView = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            RudyBrowserTheme {
                BrowserChromeContent(snapshot, actions, focusSignal)
            }
        }
    }

    override fun view(): View = composeView

    override fun focusAddress() { focusSignal += 1 }

    override fun update(
        url: String?,
        loading: Boolean,
        progress: Int,
        canGoBack: Boolean,
        canGoForward: Boolean,
        bookmarked: Boolean
    ) {
        snapshot = ChromeSnapshot(
            url.orEmpty(),
            loading,
            progress.coerceIn(0, 100),
            canGoBack,
            canGoForward,
            bookmarked,
        )
    }
}

private data class ChromeSnapshot(
    val url: String = "",
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val bookmarked: Boolean = false
)

@Composable
private fun BrowserChromeContent(
    state: ChromeSnapshot,
    actions: BrowserChrome.Actions,
    focusSignal: Int
) {
    Surface(color = RudyColors.toolbar) {
        Column {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isTablet = maxWidth >= 600.dp
                val wideTablet = maxWidth >= 760.dp
                Row(
                    modifier = Modifier.fillMaxWidth().height(RudyDimensions.toolbarHeight)
                        .padding(horizontal = RudySpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(RudySpacing.compact),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarAction("Go back", state.canGoBack, { actions.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                    if (isTablet) {
                        ToolbarAction("Go forward", state.canGoForward, { actions.forward() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                        if (wideTablet) {
                            ToolbarAction("Home", true, { actions.home() }) {
                                Icon(Icons.Default.Home, contentDescription = null)
                            }
                        }
                    }
                    BrowserOmnibox(
                        url = state.url,
                        onNavigate = actions::navigate,
                        focusSignal = focusSignal,
                        modifier = Modifier.weight(1f)
                    )
                    if (isTablet) {
                        ToolbarAction("Reload", true, { actions.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    }
                    BrowserOverflow(state, actions)
                }
            }
            if (state.loading) {
                LinearProgressIndicator(
                    progress = { (state.progress.coerceAtLeast(4) / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(RudyDimensions.progressHeight),
                    color = RudyColors.accent,
                    trackColor = RudyColors.toolbar
                )
            }
        }
    }
}

@Composable
private fun BrowserOmnibox(
    url: String,
    onNavigate: (String) -> Unit,
    focusSignal: Int,
    modifier: Modifier
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusSignal) {
        if (focusSignal > 0) focusRequester.requestFocus()
    }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(BrowserDisplay.compact(url)) }
    LaunchedEffect(url) {
        if (!editing) draft = BrowserDisplay.compact(url)
    }

    TextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = modifier
            .height(RudyDimensions.omniboxHeight)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused && !editing) {
                    editing = true
                    draft = url
                } else if (!state.isFocused && editing) {
                    editing = false
                    draft = BrowserDisplay.compact(url)
                }
            }
            .semantics { contentDescription = "Search or enter website address" },
        singleLine = true,
        shape = RudyCorners.omnibox,
        placeholder = { Text("Search or enter address", maxLines = 1, fontSize = 13.sp) },
        leadingIcon = {
            Icon(
                if (url.startsWith("https://") && !editing) Icons.Default.Lock
                else Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(RudyDimensions.leadingIconSize),
                tint = RudyColors.muted
            )
        },
        textStyle = TextStyle(color = RudyColors.foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = {
            val target = draft.trim()
            if (target.isNotEmpty()) {
                onNavigate(target)
                focus.clearFocus()
                keyboard?.hide()
                editing = false
            }
        }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = RudyColors.omnibox,
            unfocusedContainerColor = RudyColors.omnibox,
            focusedTextColor = RudyColors.foreground,
            unfocusedTextColor = RudyColors.foreground,
            focusedPlaceholderColor = RudyColors.muted,
            unfocusedPlaceholderColor = RudyColors.muted,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = RudyColors.accent
        )
    )
}

@Composable
private fun ToolbarAction(
    title: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(RudyDimensions.touchTarget).semantics { contentDescription = title }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(RudyDimensions.iconSize)) {
            icon()
        }
    }
}

@Composable
private fun BrowserOverflow(state: ChromeSnapshot, actions: BrowserChrome.Actions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ToolbarAction("Browser menu", true, { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(RudyColors.toolbar)
        ) {
            @Composable
            fun Entry(title: String, click: () -> Unit, icon: @Composable () -> Unit) {
                DropdownMenuItem(
                    text = { Text(title, color = RudyColors.foreground) },
                    leadingIcon = icon,
                    onClick = { expanded = false; click() }
                )
            }
            Entry("Reload", actions::reload, { Icon(Icons.Default.Refresh, null) })
            Entry("Home", actions::home, { Icon(Icons.Default.Home, null) })
            Entry(
                if (state.bookmarked) "Remove bookmark" else "Bookmark page",
                actions::bookmark,
                { Icon(if (state.bookmarked) Icons.Default.Star else Icons.Default.StarBorder, null) }
            )
            Entry("Extensions", actions::extensions, { Icon(Icons.Default.Extension, null) })
            Entry("All browser tools", actions::menu, { Icon(Icons.Default.MoreVert, null) })
        }
    }
}
