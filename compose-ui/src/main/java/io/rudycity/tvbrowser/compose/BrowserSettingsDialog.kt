package io.rudycity.tvbrowser.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rudycity.tvbrowser.BrowserChrome

@Composable
internal fun BrowserSettingsDialog(
    state: BrowserChrome.SettingsState,
    handler: BrowserChrome.SettingsHandler,
    onDismiss: () -> Unit
) {
    val palette = RudyTheme.palette

    RudyBottomSheet(
        title = "Settings",
        subtitle = "Preferences, engine & data management",
        icon = { RudyIcons.Settings(tint = palette.accent, size = 20.dp) },
        onDismiss = onDismiss
    ) {
        // Section 1: Appearance & Theme
        SettingsSectionTitle("APPEARANCE & THEME")
        Surface(
            color = palette.surfaceElevated,
            shape = RudyCorners.tile,
            modifier = Modifier
                .fillMaxWidth()
                .border(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.6f), RudyCorners.tile)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Segmented Theme Mode Selector (System, Dark, Light)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RudySpacing.md, vertical = RudySpacing.sm)
                ) {
                    Text(
                        text = "Theme Appearance",
                        style = RudyTypography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.foreground
                    )
                    Text(
                        text = "Choose System adaptive, Dark obsidian, or Light porcelain",
                        style = RudyTypography.bodySmall,
                        color = palette.muted
                    )
                    Spacer(Modifier.height(RudySpacing.sm))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RudyCorners.control)
                            .background(palette.omnibox)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val activeMode = state.themeMode ?: if (state.darkMode) "dark" else "light"
                        ThemeSegmentButton(
                            label = "System",
                            selected = activeMode.equals("system", ignoreCase = true),
                            icon = { RudyIcons.Monitor(tint = it, size = 16.dp) },
                            modifier = Modifier.weight(1f),
                            onClick = { handler.onSetThemeMode("system") }
                        )
                        ThemeSegmentButton(
                            label = "Dark",
                            selected = activeMode.equals("dark", ignoreCase = true),
                            icon = { RudyIcons.Moon(tint = it, size = 16.dp) },
                            modifier = Modifier.weight(1f),
                            onClick = { handler.onSetThemeMode("dark") }
                        )
                        ThemeSegmentButton(
                            label = "Light",
                            selected = activeMode.equals("light", ignoreCase = true),
                            icon = { RudyIcons.Sun(tint = it, size = 16.dp) },
                            modifier = Modifier.weight(1f),
                            onClick = { handler.onSetThemeMode("light") }
                        )
                    }
                }
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Desktop Mode",
                    subtitle = "Request desktop website layout",
                    checked = state.desktopMode,
                    onCheckedChange = { handler.onToggleDesktopMode(it) }
                )
            }
        }

        // Section 2: Engine & Performance
        SettingsSectionTitle("BROWSER ENGINE & RUNTIME")
        Surface(
            color = palette.surfaceElevated,
            shape = RudyCorners.tile,
            modifier = Modifier
                .fillMaxWidth()
                .border(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.6f), RudyCorners.tile)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                            "${state.activeEngineName} (${state.engineMode})",
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
                                "Change",
                                style = RudyTypography.labelMedium,
                                color = palette.accent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (state.tvDevice) {
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "TV Remote Pointer",
                        subtitle = "Virtual cursor for Android TV remote",
                        checked = state.pointerMode,
                        onCheckedChange = { handler.onTogglePointerMode() }
                    )
                }
            }
        }

        // Section 3: Extensions & Add-ons
        SettingsSectionTitle("ADD-ONS & EXTENSIONS")
        Surface(
            color = palette.surfaceElevated,
            shape = RudyCorners.tile,
            modifier = Modifier
                .fillMaxWidth()
                .border(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.6f), RudyCorners.tile)
        ) {
            SettingsNavRow(
                title = "Manage Extensions",
                subtitle = "${state.extensionCount} add-ons configured",
                icon = { RudyIcons.Extension(size = 18.dp, tint = palette.accent) },
                onClick = {
                    onDismiss()
                    handler.onOpenExtensions()
                }
            )
        }

        // Section 4: Privacy & About
        SettingsSectionTitle("PRIVACY & ABOUT")
        Surface(
            color = palette.surfaceElevated,
            shape = RudyCorners.tile,
            modifier = Modifier
                .fillMaxWidth()
                .border(RudyDimensions.hairline, palette.outlineSubtle.copy(alpha = 0.6f), RudyCorners.tile)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsNavRow(
                    title = "Clear Browsing Data",
                    subtitle = "History, cache, cookies and storage",
                    icon = { RudyIcons.Clear(size = 18.dp, tint = palette.danger) },
                    titleColor = palette.danger,
                    onClick = {
                        onDismiss()
                        handler.onClearBrowsingData()
                    }
                )
                SettingsDivider()
                SettingsNavRow(
                    title = "About Rudy Browser",
                    subtitle = "Architecture, renderers & licenses",
                    icon = { RudyIcons.Info(size = 18.dp, tint = palette.accent) },
                    onClick = {
                        onDismiss()
                        handler.onShowAbout()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(RudySpacing.md))
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
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
private fun SettingsDivider() {
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
private fun SettingsSwitchRow(
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
            Text(title, style = RudyTypography.titleSmall, color = palette.foreground, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = RudyTypography.bodySmall, color = palette.muted)
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

@Composable
private fun SettingsNavRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    titleColor: Color? = null,
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
                Text(
                    title,
                    style = RudyTypography.titleSmall,
                    color = titleColor ?: palette.foreground,
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
private fun ThemeSegmentButton(
    label: String,
    selected: Boolean,
    icon: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = RudyTheme.palette
    val tint = if (selected) palette.onAccent else palette.muted
    val textCol = if (selected) palette.onAccent else palette.foreground
    val bg = if (selected) palette.accent else Color.Transparent

    Surface(
        color = bg,
        shape = RudyCorners.control,
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon(tint)
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = RudyTypography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textCol
            )
        }
    }
}
