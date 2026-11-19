package io.rudycity.tvbrowser.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rudycity.tvbrowser.BrowserChrome

@Composable
internal fun BrowserToolsManagerDialog(
    state: BrowserChrome.ToolsState,
    handler: BrowserChrome.ToolsHandler,
    onDismiss: () -> Unit
) {
    val palette = RudyTheme.palette

    RudyBottomSheet(
        title = "Browser Tools",
        subtitle = if (state.currentHost.isNotBlank()) "Page controls for ${state.currentHost}" else "Page view, media & engine controls",
        icon = { RudyIcons.Tools(tint = palette.accent, size = 20.dp) },
        onDismiss = onDismiss
    ) {
        // Section 1: Page Control Center
        SectionTitle("PAGE & VIEWPORT")

        // Stepper Bar: Zoom & Scroll
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RudySpacing.sm)
        ) {
            // Zoom Stepper
            Surface(
                color = palette.surfaceElevated,
                shape = RudyCorners.tile,
                modifier = Modifier
                    .weight(1f)
                    .border(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.6f), RudyCorners.tile)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RudySpacing.xs, vertical = RudySpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RudyCorners.control)
                            .clickable { handler.onZoomOut() }
                            .semantics { contentDescription = "Zoom out" },
                        contentAlignment = Alignment.Center
                    ) {
                        RudyIcons.ZoomOut(size = 18.dp, tint = palette.accent)
                    }
                    Text(
                        "Zoom",
                        style = RudyTypography.labelMedium,
                        color = palette.foreground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RudyCorners.control)
                            .clickable { handler.onZoomIn() }
                            .semantics { contentDescription = "Zoom in" },
                        contentAlignment = Alignment.Center
                    ) {
                        RudyIcons.ZoomIn(size = 18.dp, tint = palette.accent)
                    }
                }
            }

            // Scroll Stepper
            Surface(
                color = palette.surfaceElevated,
                shape = RudyCorners.tile,
                modifier = Modifier
                    .weight(1f)
                    .border(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.6f), RudyCorners.tile)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RudySpacing.xs, vertical = RudySpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RudyCorners.control)
                            .clickable { handler.onScrollUp() }
                            .semantics { contentDescription = "Scroll page up" },
                        contentAlignment = Alignment.Center
                    ) {
                        RudyIcons.ArrowUp(size = 18.dp, tint = palette.accent)
                    }
                    Text(
                        "Scroll",
                        style = RudyTypography.labelMedium,
                        color = palette.foreground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RudyCorners.control)
                            .clickable { handler.onScrollDown() }
                            .semantics { contentDescription = "Scroll page down" },
                        contentAlignment = Alignment.Center
                    ) {
                        RudyIcons.ArrowDown(size = 18.dp, tint = palette.accent)
                    }
                }
            }
        }

        // Action Tiles: Find in page & Save PDF
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RudySpacing.sm)
        ) {
            ControlTile(
                modifier = Modifier.weight(1f),
                title = "Find in Page",
                subtitle = "Search page text",
                icon = { RudyIcons.Search(size = 18.dp, tint = palette.accent) },
                onClick = {
                    onDismiss()
                    handler.onFindInPage()
                }
            )

            ControlTile(
                modifier = Modifier.weight(1f),
                title = "Save as PDF",
                subtitle = if (state.canSavePdf) "Export document" else "Unavailable",
                icon = { RudyIcons.Document(size = 18.dp, tint = if (state.canSavePdf) palette.accent else palette.muted) },
                enabled = state.canSavePdf,
                onClick = {
                    onDismiss()
                    handler.onSavePdf()
                }
            )
        }

        // Section 2: Media & Site Controls
        SectionTitle("MEDIA & PERMISSIONS")

        Surface(
            color = palette.surfaceElevated,
            shape = RudyCorners.tile,
            modifier = Modifier
                .fillMaxWidth()
                .border(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.6f), RudyCorners.tile)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingActionRow(
                    title = "HTML5 Video Controls",
                    subtitle = "Seek, play/pause, playback rate & mute",
                    icon = { RudyIcons.Video(size = 18.dp, tint = palette.accent) },
                    onClick = {
                        onDismiss()
                        handler.onVideoControls()
                    }
                )
                HairlineDivider()
                SettingActionRow(
                    title = "Site Permissions",
                    subtitle = if (state.currentHost.isNotBlank()) "Origin: ${state.currentHost}" else "Manage active site permissions",
                    icon = { RudyIcons.Lock(size = 18.dp, tint = palette.accent) },
                    enabled = state.hasSitePermissions || state.currentHost.isNotBlank(),
                    onClick = {
                        onDismiss()
                        handler.onSitePermissions()
                    }
                )
            }
        }

        // Section 3: Engine & Preferences
        SectionTitle("ENGINE & PREFERENCES")

        Surface(
            color = palette.surfaceElevated,
            shape = RudyCorners.tile,
            modifier = Modifier
                .fillMaxWidth()
                .border(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.6f), RudyCorners.tile)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Engine Selector Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RudySpacing.md, vertical = RudySpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = RudySpacing.sm)) {
                        Text(
                            "Rendering Engine",
                            style = RudyTypography.titleSmall,
                            color = palette.foreground,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            state.engineName,
                            style = RudyTypography.bodySmall,
                            color = palette.accent,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RudyCorners.control)
                            .background(palette.accent.copy(alpha = 0.15f), RudyCorners.control)
                            .border(RudyDimensions.hairline, palette.accent.copy(alpha = 0.4f), RudyCorners.control)
                            .clickable {
                                onDismiss()
                                handler.onChangeEngine()
                            }
                            .padding(horizontal = RudySpacing.md, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(RudySpacing.xs)
                        ) {
                            RudyIcons.Reload(size = 14.dp, tint = palette.accent)
                            Text(
                                "Switch",
                                style = RudyTypography.labelMedium,
                                color = palette.accent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                HairlineDivider()

                // Desktop Mode Toggle
                SettingToggleRow(
                    title = "Desktop Site Mode",
                    subtitle = "Request full desktop web version",
                    checked = state.desktopMode,
                    onCheckedChange = { handler.onToggleDesktop(it) }
                )

                HairlineDivider()

                // Dark Theme Toggle
                SettingToggleRow(
                    title = "Dark Browser Chrome",
                    subtitle = "Cool-slate high-contrast palette",
                    checked = state.darkTheme,
                    onCheckedChange = { handler.onToggleDarkMode(it) }
                )

                if (state.tvDevice) {
                    HairlineDivider()
                    SettingToggleRow(
                        title = "TV Remote Pointer",
                        subtitle = "Virtual cursor for Android TV remote",
                        checked = state.cursorMode,
                        onCheckedChange = { handler.onTogglePointerMode() }
                    )
                }

                HairlineDivider()

                // Memory & Tabs Row
                SettingActionRow(
                    title = "Memory & Inactive Tabs",
                    subtitle = "Inspect RAM footprint and active tab instances",
                    icon = { RudyIcons.Tabs(size = 18.dp, tint = palette.accent) },
                    onClick = {
                        onDismiss()
                        handler.onShowMemoryAndTabs()
                    }
                )
            }
        }

        // Section 4: Privacy & Data
        SectionTitle("PRIVACY & DATA")

        Surface(
            color = palette.danger.copy(alpha = 0.08f),
            shape = RudyCorners.tile,
            modifier = Modifier
                .fillMaxWidth()
                .border(RudyDimensions.hairline, palette.danger.copy(alpha = 0.25f), RudyCorners.tile)
        ) {
            SettingActionRow(
                title = "Clear Browsing Data",
                subtitle = "History, cache, cookies and offline storage",
                icon = { RudyIcons.Clear(size = 18.dp, tint = palette.danger) },
                titleColor = palette.danger,
                onClick = {
                    onDismiss()
                    handler.onClearBrowsingData()
                }
            )
        }

        Spacer(modifier = Modifier.height(RudySpacing.md))
    }
}

@Composable
private fun SectionTitle(title: String) {
    val palette = RudyTheme.palette
    Text(
        text = title,
        style = RudyTypography.labelMedium,
        color = palette.muted.copy(alpha = 0.85f),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = RudySpacing.xs, top = RudySpacing.xs)
    )
}

@Composable
private fun HairlineDivider() {
    val palette = RudyTheme.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RudySpacing.md)
            .height(RudyDimensions.hairline)
            .background(palette.outlineSubtle.copy(alpha = 0.35f))
    )
}

@Composable
private fun ControlTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val palette = RudyTheme.palette

    Surface(
        color = palette.surfaceElevated.copy(alpha = if (enabled) 1f else 0.4f),
        shape = RudyCorners.tile,
        modifier = modifier
            .clip(RudyCorners.tile)
            .border(
                RudyDimensions.hairline,
                palette.outlineSubtle.copy(alpha = if (enabled) 0.6f else 0.25f),
                RudyCorners.tile
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(RudySpacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RudySpacing.xs)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RudyCorners.control)
                    .background(palette.background)
                    .border(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.4f), RudyCorners.control),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                title,
                style = RudyTypography.titleSmall,
                color = if (enabled) palette.foreground else palette.muted,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                style = RudyTypography.bodySmall,
                color = palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingActionRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    titleColor: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val palette = RudyTheme.palette

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RudyCorners.tile)
            .clickable(enabled = enabled, onClick = onClick)
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
                    .background(palette.background)
                    .border(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.4f), RudyCorners.control),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column {
                Text(
                    title,
                    style = RudyTypography.titleSmall,
                    color = if (enabled) (titleColor ?: palette.foreground) else palette.muted,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
                    style = RudyTypography.bodySmall,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            "›",
            style = RudyTypography.titleMedium,
            color = palette.muted.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = RudyTheme.palette

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RudySpacing.md, vertical = RudySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = RudySpacing.md)) {
            Text(
                title,
                style = RudyTypography.titleSmall,
                color = palette.foreground,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
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
                uncheckedTrackColor = palette.surfaceElevated,
                uncheckedBorderColor = palette.outline
            )
        )
    }
}
