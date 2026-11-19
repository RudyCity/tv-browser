package io.rudycity.tvbrowser.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rudycity.tvbrowser.BrowserChrome
import java.util.Locale
import kotlin.math.abs

private enum class TabSegment {
    STANDARD,
    PRIVATE
}

/**
 * Modern Flagship Tab Manager for Rudy Browser.
 * Adheres to Rudy Design System v2 & Arc/Safari mobile standards:
 * - Segmented standard vs private tab segregation
 * - Bespoke brand identity squircle badges for high-profile sites
 * - Quick New Tab CTA with dedicated private/standard modes
 * - Fast tab switching, duplicate, and individual close controls
 * - Glitch-free progress indicators and ambient dark/light fidelity
 */
@Composable
internal fun BrowserTabManagerDialog(
    items: List<BrowserChrome.TabItem>,
    maxTabs: Int,
    handler: BrowserChrome.TabSheetHandler,
    onDismiss: () -> Unit
) {
    val palette = RudyTheme.palette

    val standardTabs = remember(items) { items.filter { !it.privateMode } }
    val privateTabs = remember(items) { items.filter { it.privateMode } }

    val initialSegment = remember {
        if (items.any { it.active && it.privateMode }) TabSegment.PRIVATE else TabSegment.STANDARD
    }
    var activeSegment by remember { mutableStateOf(initialSegment) }
    var query by remember { mutableStateOf("") }

    val currentTabPool = if (activeSegment == TabSegment.STANDARD) standardTabs else privateTabs
    val filteredTabs = remember(currentTabPool, query) {
        if (query.isBlank()) currentTabPool
        else currentTabPool.filter {
            it.title.contains(query, ignoreCase = true) || it.host.contains(query, ignoreCase = true)
        }
    }

    RudyBottomSheet(
        title = "Tabs",
        subtitle = "Manage open and private browsing sessions",
        badgeText = "${items.size} / $maxTabs",
        icon = { RudyIcons.Tabs(tint = palette.accent, size = 20.dp) },
        onDismiss = onDismiss,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentTabPool.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            handler.onCloseAll()
                            onDismiss()
                        }
                    ) {
                        Text(
                            text = if (activeSegment == TabSegment.PRIVATE) "Close All Private" else "Close All Tabs",
                            style = RudyTypography.labelMedium,
                            color = palette.danger
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                TextButton(onClick = onDismiss) {
                    Text("Done", style = RudyTypography.labelLarge, color = palette.accent)
                }
            }
        }
    ) {
        // Segmented Mode Switcher: [ 📄 Standard (x) ] | [ 🔒 Private (y) ]
        TabSegmentedControl(
            selectedSegment = activeSegment,
            standardCount = standardTabs.size,
            privateCount = privateTabs.size,
            onSelectSegment = { activeSegment = it }
        )

        // Quick Action Bar: Prominent Mode-Aware New Tab CTA
        QuickActionBar(
            segment = activeSegment,
            onNewTab = {
                handler.onNewTab()
                onDismiss()
            },
            onNewPrivateTab = {
                handler.onNewPrivateTab()
                onDismiss()
            }
        )

        // Search Filter (shown when active pool has more than 3 tabs or filter query active)
        if (currentTabPool.size > 3 || query.isNotBlank()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        text = if (activeSegment == TabSegment.PRIVATE) "Filter private tabs…" else "Filter open tabs…",
                        style = RudyTypography.bodyMedium
                    )
                },
                leadingIcon = { RudyIcons.Search(size = 16.dp, tint = palette.muted) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { query = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            RudyIcons.Clear(size = 12.dp, tint = palette.muted)
                        }
                    }
                },
                singleLine = true,
                shape = RudyCorners.control,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.surfaceElevated,
                    unfocusedContainerColor = palette.surfaceElevated,
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.outlineSubtle.copy(alpha = 0.6f),
                    focusedTextColor = palette.foreground,
                    unfocusedTextColor = palette.foreground
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Tabs Content: Empty State or Interactive Tab Cards
        if (filteredTabs.isEmpty()) {
            EmptyTabPoolState(
                segment = activeSegment,
                isSearching = query.isNotBlank(),
                onNewTab = {
                    if (activeSegment == TabSegment.PRIVATE) handler.onNewPrivateTab()
                    else handler.onNewTab()
                    onDismiss()
                }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(RudySpacing.sm)
            ) {
                filteredTabs.forEach { tab ->
                    val originalIndex = items.indexOf(tab)
                    TabCard(
                        tab = tab,
                        onClick = {
                            if (originalIndex >= 0) {
                                handler.onSelect(originalIndex)
                            }
                            onDismiss()
                        },
                        onClose = {
                            if (originalIndex >= 0) {
                                handler.onClose(originalIndex)
                            }
                        },
                        onDuplicate = {
                            if (originalIndex >= 0) {
                                handler.onDuplicate(originalIndex)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Segmented Control for switching between Standard and Private tabs.
 */
@Composable
private fun TabSegmentedControl(
    selectedSegment: TabSegment,
    standardCount: Int,
    privateCount: Int,
    onSelectSegment: (TabSegment) -> Unit
) {
    val palette = RudyTheme.palette

    Surface(
        color = palette.surfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Standard Tab Segment
            SegmentButton(
                selected = selectedSegment == TabSegment.STANDARD,
                label = "Tabs ($standardCount)",
                icon = { tint -> RudyIcons.Tabs(tint = tint, size = 16.dp) },
                activeColor = palette.accent,
                activeBg = palette.toolbar,
                modifier = Modifier.weight(1f),
                onClick = { onSelectSegment(TabSegment.STANDARD) }
            )

            // Private Tab Segment
            SegmentButton(
                selected = selectedSegment == TabSegment.PRIVATE,
                label = "Private ($privateCount)",
                icon = { tint -> RudyIcons.Lock(tint = tint, size = 15.dp) },
                activeColor = Color(0xFFA855F7), // Elegant amethyst violet
                activeBg = Color(0xFF261933),
                modifier = Modifier.weight(1f),
                onClick = { onSelectSegment(TabSegment.PRIVATE) }
            )
        }
    }
}

@Composable
private fun SegmentButton(
    selected: Boolean,
    label: String,
    icon: @Composable (Color) -> Unit,
    activeColor: Color,
    activeBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = RudyTheme.palette
    val contentColor by animateColorAsState(
        targetValue = if (selected) activeColor else palette.muted,
        label = "segment_fg"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) activeBg else Color.Transparent,
        label = "segment_bg"
    )

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon(contentColor)
            Text(
                text = label,
                style = RudyTypography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

/**
 * Mode-aware prominent New Tab action bar.
 */
@Composable
private fun QuickActionBar(
    segment: TabSegment,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit
) {
    val palette = RudyTheme.palette

    if (segment == TabSegment.STANDARD) {
        Surface(
            color = palette.surfaceElevated,
            shape = RudyCorners.control,
            border = BorderStroke(RudyDimensions.hairline, palette.accent.copy(alpha = 0.40f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RudyCorners.control)
                .clickable(onClick = onNewTab)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = RudySpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                RudyIcons.Plus(size = 18.dp, tint = palette.accent)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Tab",
                    style = RudyTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.foreground
                )
            }
        }
    } else {
        // Private mode action
        Surface(
            color = Color(0xFF261933),
            shape = RudyCorners.control,
            border = BorderStroke(RudyDimensions.hairline, Color(0xFFA855F7).copy(alpha = 0.50f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RudyCorners.control)
                .clickable(onClick = onNewPrivateTab)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = RudySpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                RudyIcons.Lock(size = 16.dp, tint = Color(0xFFA855F7))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Private Tab",
                    style = RudyTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFF3E8FF)
                )
            }
        }
    }
}

/**
 * Modern tactile tab card with bespoke domain identity, badges, and actions.
 */
@Composable
private fun TabCard(
    tab: BrowserChrome.TabItem,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onDuplicate: () -> Unit
) {
    val palette = RudyTheme.palette
    val borderColor = if (tab.active) {
        if (tab.privateMode) Color(0xFFA855F7) else palette.accent
    } else {
        palette.outlineSubtle.copy(alpha = 0.6f)
    }

    val cardBg = if (tab.active) {
        if (tab.privateMode) Color(0xFF261933) else palette.accent.copy(alpha = 0.12f)
    } else {
        palette.surfaceElevated
    }

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (tab.active) 1.5.dp else RudyDimensions.hairline, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Squircle Icon & Title/Host Details
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = RudySpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TabIdentitySquircle(tab = tab)

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val displayTitle = if (tab.title.isBlank() || tab.title.equals("about:blank", ignoreCase = true)) {
                                if (tab.host.isNotBlank()) tab.host else "New Tab"
                            } else {
                                tab.title
                            }

                            Text(
                                text = displayTitle,
                                style = RudyTypography.titleSmall,
                                color = palette.foreground,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            // Active badge
                            if (tab.active) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (tab.privateMode) Color(0xFFA855F7).copy(alpha = 0.20f)
                                            else palette.accent.copy(alpha = 0.20f),
                                            RudyCorners.badge
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Active",
                                        style = RudyTypography.labelMedium,
                                        color = if (tab.privateMode) Color(0xFFA855F7) else palette.accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Crashed badge
                            if (tab.crashed) {
                                Box(
                                    modifier = Modifier
                                        .background(palette.danger.copy(alpha = 0.20f), RudyCorners.badge)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Crashed",
                                        style = RudyTypography.labelMedium,
                                        color = palette.danger,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Host URL
                        if (tab.host.isNotBlank()) {
                            Text(
                                text = tab.host,
                                style = RudyTypography.bodySmall,
                                color = palette.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = if (tab.privateMode) "Private session" else "Start Page",
                                style = RudyTypography.bodySmall,
                                color = palette.muted
                            )
                        }
                    }
                }

                // Trailing Actions: [ Duplicate ] [ ✕ Close ]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Duplicate button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(palette.toolbar)
                            .border(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.5f), CircleShape)
                            .clickable(onClick = onDuplicate)
                            .semantics { contentDescription = "Duplicate tab: ${tab.title}" },
                        contentAlignment = Alignment.Center
                    ) {
                        RudyIcons.Copy(size = 13.dp, tint = palette.muted)
                    }

                    // Close button (X)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(palette.toolbar)
                            .border(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.5f), CircleShape)
                            .clickable(onClick = onClose)
                            .semantics { contentDescription = "Close tab: ${tab.title}" },
                        contentAlignment = Alignment.Center
                    ) {
                        RudyIcons.Clear(size = 13.dp, tint = palette.muted)
                    }
                }
            }

            // Clean hairline progress bar (ONLY rendered when actively loading between 1% and 99%)
            if (tab.loading && tab.progress in 1..99) {
                LinearProgressIndicator(
                    progress = { tab.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = if (tab.privateMode) Color(0xFFA855F7) else palette.accent,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

/**
 * 42dp Squircle favicon badge for tabs with brand recognition.
 */
@Composable
private fun TabIdentitySquircle(tab: BrowserChrome.TabItem) {
    val host = tab.host.lowercase(Locale.ROOT)
    val title = tab.title.lowercase(Locale.ROOT)

    when {
        tab.privateMode -> {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF7C3AED), Color(0xFF4F46E5))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                RudyIcons.Lock(size = 18.dp, tint = Color.White)
            }
        }
        host.contains("youtube") || title.contains("youtube") -> {
            SquircleBadge(
                gradient = listOf(Color(0xFFFF0033), Color(0xFFCC0000)),
                label = "Y"
            )
        }
        host.contains("google") || title.contains("google") -> {
            SquircleBadge(
                gradient = listOf(Color(0xFFFFFFFF), Color(0xFFF1F5FB)),
                label = "G",
                labelColor = Color(0xFF4285F4)
            )
        }
        host.contains("wikipedia") || title.contains("wikipedia") -> {
            SquircleBadge(
                gradient = listOf(Color(0xFF334155), Color(0xFF1E293B)),
                label = "W",
                isSerif = true
            )
        }
        host.contains("bing") -> {
            SquircleBadge(
                gradient = listOf(Color(0xFF008373), Color(0xFF005B50)),
                label = "B"
            )
        }
        host.contains("github") -> {
            SquircleBadge(
                gradient = listOf(Color(0xFF24292F), Color(0xFF0D1117)),
                label = "GH"
            )
        }
        host.contains("reddit") -> {
            SquircleBadge(
                gradient = listOf(Color(0xFFFF5722), Color(0xFFE64A19)),
                label = "R"
            )
        }
        host.contains("duckduckgo") -> {
            SquircleBadge(
                gradient = listOf(Color(0xFFDE5833), Color(0xFFB53D1B)),
                label = "DDG"
            )
        }
        host.contains("x.com") || host.contains("twitter") -> {
            SquircleBadge(
                gradient = listOf(Color(0xFF111827), Color(0xFF030712)),
                label = "X"
            )
        }
        host.contains("linear") -> {
            SquircleBadge(
                gradient = listOf(Color(0xFF5E6AD2), Color(0xFF424CB8)),
                label = "LI"
            )
        }
        tab.title.equals("Home", ignoreCase = true) || host.isBlank() -> {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0EA5E9), Color(0xFF2563EB))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "R",
                    style = RudyTypography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = Color.White
                )
            }
        }
        else -> {
            val initial = (host.ifBlank { tab.title }).filter { it.isLetter() }.take(1).uppercase(Locale.ROOT).ifBlank { "W" }
            val paletteGrad = pickProceduralGradient(host)
            SquircleBadge(gradient = paletteGrad, label = initial)
        }
    }
}

@Composable
private fun SquircleBadge(
    gradient: List<Color>,
    label: String,
    labelColor: Color = Color.White,
    isSerif: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(gradient))
            .border(RudyDimensions.hairline, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = RudyTypography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = if (isSerif) FontFamily.Serif else FontFamily.Default,
                fontSize = if (label.length > 1) 13.sp else 16.sp
            ),
            color = labelColor
        )
    }
}

private fun pickProceduralGradient(seed: String): List<Color> {
    val gradients = listOf(
        listOf(Color(0xFF059669), Color(0xFF047857)), // Emerald
        listOf(Color(0xFF4F46E5), Color(0xFF3730A3)), // Indigo
        listOf(Color(0xFF7C3AED), Color(0xFF6D28D9)), // Violet
        listOf(Color(0xFFE11D48), Color(0xFFBE123C)), // Rose
        listOf(Color(0xFFD97706), Color(0xFFB45309)), // Amber
        listOf(Color(0xFF0891B2), Color(0xFF0E7490))  // Cyan
    )
    val index = abs(seed.hashCode()) % gradients.size
    return gradients[index]
}

/**
 * Empty Tab Pool State.
 */
@Composable
private fun EmptyTabPoolState(
    segment: TabSegment,
    isSearching: Boolean,
    onNewTab: () -> Unit
) {
    val palette = RudyTheme.palette

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (segment == TabSegment.PRIVATE) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF261933))
                        .border(RudyDimensions.hairline, Color(0xFFA855F7).copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    RudyIcons.Lock(tint = Color(0xFFA855F7), size = 22.dp)
                }

                Text(
                    text = if (isSearching) "No private tabs match filter" else "Private Browsing",
                    style = RudyTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.foreground
                )

                Text(
                    text = if (isSearching) "Try another search term" else "Tabs in Private mode leave no search history or cookies on this device.",
                    style = RudyTypography.bodySmall,
                    color = palette.muted,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                if (!isSearching) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF261933),
                        shape = RudyCorners.control,
                        border = BorderStroke(RudyDimensions.hairline, Color(0xFFA855F7).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clip(RudyCorners.control)
                            .clickable(onClick = onNewTab)
                    ) {
                        Text(
                            text = "＋ Start Private Session",
                            style = RudyTypography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF3E8FF),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surfaceElevated)
                        .border(RudyDimensions.hairline, palette.outlineSubtle, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    RudyIcons.Tabs(tint = palette.accent, size = 22.dp)
                }

                Text(
                    text = if (isSearching) "No tabs match filter" else "No open tabs",
                    style = RudyTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.foreground
                )

                Text(
                    text = if (isSearching) "Try another search term" else "Open a new tab to explore the web.",
                    style = RudyTypography.bodySmall,
                    color = palette.muted
                )

                if (!isSearching) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = palette.surfaceElevated,
                        shape = RudyCorners.control,
                        border = BorderStroke(RudyDimensions.hairline, palette.accent.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clip(RudyCorners.control)
                            .clickable(onClick = onNewTab)
                    ) {
                        Text(
                            text = "＋ Open New Tab",
                            style = RudyTypography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.foreground,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
