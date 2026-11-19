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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rudycity.tvbrowser.BrowserChrome

@Composable
internal fun BrowserHistoryManagerDialog(
    items: List<BrowserChrome.HistoryItem>,
    handler: BrowserChrome.HistoryHandler,
    onDismiss: () -> Unit
) {
    val palette = RudyTheme.palette
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) {
        if (query.isBlank()) items
        else items.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.url.contains(query, ignoreCase = true) ||
                    it.host.contains(query, ignoreCase = true)
        }
    }

    RudyBottomSheet(
        title = "History",
        subtitle = "Recent websites & visited pages",
        badgeText = "${items.size} visits",
        icon = { RudyIcons.History(tint = palette.accent, size = 20.dp) },
        onDismiss = onDismiss,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (items.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            handler.onClearAll()
                            onDismiss()
                        }
                    ) {
                        Text("Clear History", style = RudyTypography.labelMedium, color = palette.danger)
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
        // Search filter
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search history…", style = RudyTypography.bodyMedium) },
            leadingIcon = { RudyIcons.Search(size = 16.dp, tint = palette.muted) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clickable { query = "" }
                            .padding(RudySpacing.xs)
                    ) {
                        RudyIcons.Clear(size = 14.dp, tint = palette.muted)
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

        // History items
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = RudySpacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (query.isNotBlank()) "No entries match \"$query\"" else "History is empty",
                    style = RudyTypography.bodyMedium,
                    color = palette.muted
                )
            }
        } else {
            filtered.forEach { item ->
                HistoryRowCard(
                    item = item,
                    onClick = {
                        handler.onOpen(item.url)
                        onDismiss()
                    },
                    onNewTab = {
                        handler.onOpenInNewTab(item.url)
                        onDismiss()
                    },
                    onRemove = {
                        handler.onRemove(item.url)
                    }
                )
            }
        }
    }
}

@Composable
private fun HistoryRowCard(
    item: BrowserChrome.HistoryItem,
    onClick: () -> Unit,
    onNewTab: () -> Unit,
    onRemove: () -> Unit
) {
    val palette = RudyTheme.palette

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
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RudySpacing.md, vertical = RudySpacing.md),
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
                        .background(palette.background),
                    contentAlignment = Alignment.Center
                ) {
                    RudyIcons.History(size = 16.dp, tint = palette.muted)
                }

                Column {
                    Text(
                        text = item.title.ifEmpty { item.url },
                        style = RudyTypography.titleSmall,
                        color = palette.foreground,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.host.ifEmpty { item.url },
                        style = RudyTypography.bodySmall,
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RudySpacing.xs)
            ) {
                // Open in new tab
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(palette.background)
                        .clickable(onClick = onNewTab)
                        .semantics { contentDescription = "Open in new tab: ${item.title}" },
                    contentAlignment = Alignment.Center
                ) {
                    RudyIcons.Tabs(size = 14.dp, tint = palette.accent)
                }

                // Delete
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(palette.background)
                        .clickable(onClick = onRemove)
                        .semantics { contentDescription = "Delete from history: ${item.title}" },
                    contentAlignment = Alignment.Center
                ) {
                    RudyIcons.Clear(size = 13.dp, tint = palette.muted)
                }
            }
        }
    }
}
