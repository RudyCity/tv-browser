package io.rudycity.tvbrowser.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * World-class modal bottom sheet container for Rudy Browser.
 * Replaces amateurish centered Alert Dialogs with an edge-to-edge,
 * gestural bottom sheet adhering to modern mobile flagship UX (Arc, Safari, Linear).
 */
@Composable
internal fun RudyBottomSheet(
    title: String,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    badgeText: String? = null,
    onDismiss: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = RudyTheme.palette

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Dimmed backdrop scrim with tap-outside dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Main sheet container
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Intercept taps on sheet body
                    ),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = palette.toolbar,
                border = androidx.compose.foundation.BorderStroke(
                    RudyDimensions.hairline,
                    palette.outlineSubtle.copy(alpha = 0.6f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = RudySpacing.lg)
                        .padding(top = RudySpacing.md, bottom = RudySpacing.md)
                ) {
                    // Top drag handle indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(38.dp)
                            .height(4.5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(palette.muted.copy(alpha = 0.35f))
                    )

                    Spacer(modifier = Modifier.height(RudySpacing.md))

                    // Sheet Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = RudySpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(RudySpacing.sm)
                        ) {
                            if (icon != null) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RudyCorners.control)
                                        .background(palette.accent.copy(alpha = 0.12f))
                                        .border(
                                            RudyDimensions.hairline,
                                            palette.accent.copy(alpha = 0.3f),
                                            RudyCorners.control
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    icon()
                                }
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(RudySpacing.xs)
                                ) {
                                    Text(
                                        text = title,
                                        style = RudyTypography.titleMedium,
                                        color = palette.foreground,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (badgeText != null) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    palette.accent.copy(alpha = 0.15f),
                                                    RudyCorners.badge
                                                )
                                                .border(
                                                    RudyDimensions.hairline,
                                                    palette.accent.copy(alpha = 0.35f),
                                                    RudyCorners.badge
                                                )
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = badgeText,
                                                style = RudyTypography.labelMedium,
                                                color = palette.accent,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                if (subtitle != null) {
                                    Text(
                                        text = subtitle,
                                        style = RudyTypography.bodySmall,
                                        color = palette.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Circular Close Button (✕)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(palette.surfaceElevated)
                                .border(
                                    RudyDimensions.hairline,
                                    palette.outlineSubtle.copy(alpha = 0.6f),
                                    CircleShape
                                )
                                .clickable(onClick = onDismiss)
                                .semantics { contentDescription = "Close $title" },
                            contentAlignment = Alignment.Center
                        ) {
                            RudyIcons.Clear(size = 14.dp, tint = palette.muted)
                        }
                    }

                    Spacer(modifier = Modifier.height(RudySpacing.md))

                    // Hairline separator under header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(RudyDimensions.hairline)
                            .background(palette.outlineSubtle.copy(alpha = 0.4f))
                    )

                    Spacer(modifier = Modifier.height(RudySpacing.md))

                    // Scrollable content area
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(RudySpacing.md)
                    ) {
                        content()
                    }

                    // Optional sticky footer
                    if (footer != null) {
                        Spacer(modifier = Modifier.height(RudySpacing.sm))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(RudyDimensions.hairline)
                                .background(palette.outlineSubtle.copy(alpha = 0.4f))
                        )
                        Spacer(modifier = Modifier.height(RudySpacing.md))
                        footer()
                        Spacer(modifier = Modifier.height(RudySpacing.xs))
                    }
                }
            }
        }
    }
}
