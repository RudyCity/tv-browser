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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
internal fun BrowserExtensionManagerDialog(
    items: List<BrowserChrome.ExtensionItem>,
    currentHost: String,
    handler: BrowserChrome.ExtensionManagerHandler,
    onDismiss: () -> Unit
) {
    val palette = RudyTheme.palette
    val enabledCount = remember(items) { items.count { it.enabled } }

    RudyBottomSheet(
        title = "Extensions",
        subtitle = "Built-in add-ons & userscript controls",
        badgeText = "$enabledCount / ${items.size} ON",
        icon = { RudyIcons.Extension(tint = palette.accent, size = 20.dp) },
        onDismiss = onDismiss,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = { handler.onResetAllExtensions() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RudySpacing.xs)
                    ) {
                        RudyIcons.Reload(size = 14.dp, tint = palette.muted)
                        Text(
                            "Reset add-ons & scripts to default",
                            style = RudyTypography.bodySmall,
                            color = palette.muted
                        )
                    }
                }
            }
        }
    ) {
        // Active Host Banner
        if (currentHost.isNotBlank()) {
            Surface(
                color = palette.surfaceElevated,
                shape = RudyCorners.tile,
                border = androidx.compose.foundation.BorderStroke(
                    RudyDimensions.hairline,
                    palette.outlineSubtle.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RudyCorners.tile)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RudySpacing.md, vertical = RudySpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RudySpacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RudyCorners.badge)
                                .background(palette.background),
                            contentAlignment = Alignment.Center
                        ) {
                            RudyIcons.Lock(size = 14.dp, tint = palette.positive)
                        }
                        Column {
                            Text(
                                currentHost,
                                style = RudyTypography.titleSmall,
                                color = palette.foreground,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Per-site permission overrides apply to this host",
                                style = RudyTypography.bodySmall,
                                color = palette.muted
                            )
                        }
                    }
                }
            }
        }

        // Quick Tools: Video & Userscript
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RudySpacing.sm)
        ) {
            Surface(
                color = palette.surfaceElevated,
                shape = RudyCorners.tile,
                border = androidx.compose.foundation.BorderStroke(
                    RudyDimensions.hairline,
                    palette.outlineSubtle.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RudyCorners.tile)
                    .clickable {
                        onDismiss()
                        handler.onOpenVideoControls()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(RudySpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RudySpacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RudyCorners.control)
                            .background(palette.background),
                        contentAlignment = Alignment.Center
                    ) {
                        RudyIcons.Video(size = 18.dp, tint = palette.accent)
                    }
                    Column {
                        Text("Video Player", style = RudyTypography.titleSmall, color = palette.foreground, fontWeight = FontWeight.SemiBold)
                        Text("Controls & speed", style = RudyTypography.bodySmall, color = palette.muted)
                    }
                }
            }

            Surface(
                color = palette.surfaceElevated,
                shape = RudyCorners.tile,
                border = androidx.compose.foundation.BorderStroke(
                    RudyDimensions.hairline,
                    palette.outlineSubtle.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RudyCorners.tile)
                    .clickable {
                        onDismiss()
                        handler.onOpenUserscriptEditor()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(RudySpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RudySpacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RudyCorners.control)
                            .background(palette.background),
                        contentAlignment = Alignment.Center
                    ) {
                        RudyIcons.Tools(size = 18.dp, tint = palette.accent)
                    }
                    Column {
                        Text("Userscripts", style = RudyTypography.titleSmall, color = palette.foreground, fontWeight = FontWeight.SemiBold)
                        Text("Custom JS editor", style = RudyTypography.bodySmall, color = palette.muted)
                    }
                }
            }
        }

        // Section Title: Add-ons
        Text(
            text = "BUILT-IN ADD-ONS",
            style = RudyTypography.labelMedium,
            color = palette.muted.copy(alpha = 0.85f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = RudySpacing.xs, top = RudySpacing.xs)
        )

        // Cards list
        items.forEach { item ->
            ExtensionItemCard(
                item = item,
                currentHost = currentHost,
                onToggle = { handler.onToggleExtension(item.id, it) },
                onSiteRule = { handler.onSetSiteRule(item.id, it) }
            )
        }

        Spacer(modifier = Modifier.height(RudySpacing.sm))
    }
}

@Composable
private fun ExtensionItemCard(
    item: BrowserChrome.ExtensionItem,
    currentHost: String,
    onToggle: (Boolean) -> Unit,
    onSiteRule: (String) -> Unit
) {
    val palette = RudyTheme.palette

    Surface(
        color = palette.surfaceElevated,
        shape = RudyCorners.tile,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                RudyDimensions.hairline,
                if (item.enabled) palette.accent.copy(alpha = 0.4f) else palette.outlineSubtle.copy(alpha = 0.5f),
                RudyCorners.tile
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RudySpacing.md),
            verticalArrangement = Arrangement.spacedBy(RudySpacing.sm)
        ) {
            // Header: Icon + Name + Badge + Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = RudySpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RudySpacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RudyCorners.control)
                            .background(if (item.enabled) palette.accent.copy(alpha = 0.15f) else palette.background)
                            .border(
                                RudyDimensions.hairline,
                                if (item.enabled) palette.accent.copy(alpha = 0.35f) else palette.outlineSubtle.copy(alpha = 0.4f),
                                RudyCorners.control
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        RudyIcons.Extension(
                            size = 17.dp,
                            tint = if (item.enabled) palette.accent else palette.muted
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(RudySpacing.xs)
                        ) {
                            Text(
                                item.name,
                                style = RudyTypography.titleSmall,
                                color = palette.foreground,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (item.builtIn) palette.background else palette.accent.copy(alpha = 0.15f),
                                        RudyCorners.badge
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    if (item.builtIn) "Built-in" else "WebExtension",
                                    style = RudyTypography.bodySmall.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified),
                                    color = if (item.builtIn) palette.muted else palette.accent
                                )
                            }
                        }
                    }
                }

                Switch(
                    checked = item.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = palette.onAccent,
                        checkedTrackColor = palette.accent,
                        uncheckedThumbColor = palette.muted,
                        uncheckedTrackColor = palette.surfaceElevated,
                        uncheckedBorderColor = palette.outline
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = "Toggle ${item.name}"
                    }
                )
            }

            // Description
            Text(
                item.description,
                style = RudyTypography.bodySmall,
                color = palette.muted
            )

            // Per-site segmented rule if on a website
            if (currentHost.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RudyDimensions.hairline)
                        .background(palette.outlineSubtle.copy(alpha = 0.35f))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Site rule:",
                        style = RudyTypography.labelMedium,
                        color = palette.muted
                    )

                    // Sleek Segmented Pill
                    Surface(
                        color = palette.background,
                        shape = RudyCorners.control,
                        modifier = Modifier.border(
                            RudyDimensions.hairline,
                            palette.outlineSubtle.copy(alpha = 0.5f),
                            RudyCorners.control
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            SegmentedChip(
                                label = "Inherit",
                                selected = item.siteRule == "inherit" || item.siteRule.isEmpty(),
                                onClick = { onSiteRule("inherit") }
                            )
                            SegmentedChip(
                                label = "Allow",
                                selected = item.siteRule == "allow",
                                onClick = { onSiteRule("allow") }
                            )
                            SegmentedChip(
                                label = "Block",
                                selected = item.siteRule == "deny",
                                onClick = { onSiteRule("deny") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = RudyTheme.palette
    val bg = if (selected) palette.accent else Color.Transparent
    val textCol = if (selected) palette.onAccent else palette.muted

    Box(
        modifier = Modifier
            .clip(RudyCorners.badge)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = RudySpacing.sm, vertical = 4.dp)
    ) {
        Text(
            label,
            style = RudyTypography.labelMedium,
            color = textCol,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
