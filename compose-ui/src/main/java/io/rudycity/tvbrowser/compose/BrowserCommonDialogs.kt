package io.rudycity.tvbrowser.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.rudycity.tvbrowser.BrowserChrome

internal data class ActionSheet(
    val title: String,
    val options: List<String>,
    val callback: BrowserChrome.ChoiceHandler
)

internal data class RichActionSheet(
    val title: String,
    val items: List<BrowserChrome.ActionItem>,
    val emptyTitle: String,
    val emptyDetail: String,
    val callback: BrowserChrome.ChoiceHandler
)

internal data class ConfirmationRequest(
    val title: String,
    val message: String,
    val actionLabel: String,
    val callback: Runnable
)

internal data class SecurityConfirmationRequest(
    val title: String,
    val summary: String,
    val sourceLabel: String,
    val destinationLabel: String,
    val warning: String,
    val safeDismissLabel: String,
    val confirmLabel: String,
    val destructive: Boolean,
    val callback: Runnable
)

internal data class AboutInfo(
    val appName: String,
    val versionName: String,
    val packageId: String,
    val flavor: String,
    val androidSdk: Int,
    val activeEngine: String,
    val engineDetail: String,
    val cleartextAllowed: Boolean,
    val openTabs: Int
)

@Composable
internal fun BrowserActionsDialog(
    sheet: ActionSheet,
    onDismiss: () -> Unit,
    onChoice: (Int) -> Unit
) {
    val palette = RudyTheme.palette

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = RudyCorners.control,
                modifier = Modifier.heightIn(min = RudyDimensions.touchTarget)
            ) {
                Text("Cancel", style = RudyTypography.labelLarge, color = palette.accent)
            }
        },
        title = {
            Text(
                sheet.title,
                style = RudyTypography.titleMedium,
                color = palette.foreground
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RudySpacing.xs)
            ) {
                sheet.options.forEachIndexed { index, option ->
                    Surface(
                        color = palette.surfaceElevated,
                        shape = RudyCorners.tile,
                        border = androidx.compose.foundation.BorderStroke(
                            RudyDimensions.hairline,
                            palette.outlineSubtle
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RudyCorners.tile)
                            .clickable { onChoice(index) }
                            .heightIn(min = RudyDimensions.touchTarget)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = RudySpacing.md, vertical = RudySpacing.md)
                        ) {
                            Text(
                                text = option,
                                style = RudyTypography.bodyMedium,
                                color = palette.foreground
                            )
                        }
                    }
                }
            }
        },
        containerColor = palette.toolbar,
        shape = RudyCorners.modal
    )
}

@Composable
internal fun BrowserRichActionsDialog(
    sheet: RichActionSheet,
    onDismiss: () -> Unit,
    onChoice: (Int) -> Unit
) {
    val palette = RudyTheme.palette

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = RudyCorners.control,
                modifier = Modifier.heightIn(min = RudyDimensions.touchTarget)
            ) {
                Text("Cancel", style = RudyTypography.labelLarge, color = palette.accent)
            }
        },
        title = {
            Text(sheet.title, style = RudyTypography.titleMedium, color = palette.foreground)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RudySpacing.sm)
            ) {
                if (sheet.items.isEmpty()) {
                    Column(
                        modifier = Modifier.padding(RudySpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(RudySpacing.xs)
                    ) {
                        Text(sheet.emptyTitle, style = RudyTypography.titleSmall, color = palette.foreground)
                        Text(sheet.emptyDetail, style = RudyTypography.bodySmall, color = palette.muted)
                    }
                } else {
                    sheet.items.forEachIndexed { index, item ->
                        Surface(
                            color = if (item.selected) palette.accent.copy(alpha = 0.15f) else palette.surfaceElevated,
                            shape = RudyCorners.tile,
                            border = androidx.compose.foundation.BorderStroke(
                                RudyDimensions.hairline,
                                if (item.selected) palette.accent else palette.outlineSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RudyCorners.tile)
                                .clickable { onChoice(index) }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = RudySpacing.md, vertical = RudySpacing.md)
                            ) {
                                Text(
                                    item.label,
                                    style = RudyTypography.titleSmall,
                                    color = if (item.selected) palette.accent else palette.foreground,
                                    fontWeight = if (item.selected) FontWeight.Bold else FontWeight.SemiBold
                                )
                                if (item.supporting != null && item.supporting.isNotBlank()) {
                                    Text(
                                        item.supporting,
                                        style = RudyTypography.bodySmall,
                                        color = palette.muted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = palette.toolbar,
        shape = RudyCorners.modal
    )
}

@Composable
internal fun BrowserConfirmationDialog(
    request: ConfirmationRequest,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val palette = RudyTheme.palette

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                shape = RudyCorners.control,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = palette.danger,
                    contentColor = palette.foreground
                ),
                modifier = Modifier.heightIn(min = RudyDimensions.touchTarget)
            ) {
                Text(request.actionLabel, style = RudyTypography.labelLarge)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RudyCorners.control,
                modifier = Modifier.heightIn(min = RudyDimensions.touchTarget)
            ) {
                Text("Cancel", style = RudyTypography.labelMedium, color = palette.muted)
            }
        },
        title = {
            Text(request.title, style = RudyTypography.titleMedium, color = palette.foreground)
        },
        text = {
            Text(request.message, style = RudyTypography.bodyMedium, color = palette.muted)
        },
        containerColor = palette.toolbar,
        shape = RudyCorners.modal
    )
}

@Composable
internal fun BrowserSecurityConfirmationDialog(
    request: SecurityConfirmationRequest,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val palette = RudyTheme.palette

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                shape = RudyCorners.control,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (request.destructive) palette.danger else palette.accent,
                    contentColor = if (request.destructive) palette.foreground else palette.onAccent
                ),
                modifier = Modifier.heightIn(min = RudyDimensions.touchTarget)
            ) {
                Text(request.confirmLabel, style = RudyTypography.labelLarge)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RudyCorners.control,
                modifier = Modifier.heightIn(min = RudyDimensions.touchTarget)
            ) {
                Text(request.safeDismissLabel, style = RudyTypography.labelMedium, color = palette.muted)
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RudySpacing.sm)
            ) {
                RudyIcons.Lock(tint = if (request.destructive) palette.danger else palette.accent, size = 20.dp)
                Text(request.title, style = RudyTypography.titleMedium, color = palette.foreground)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RudySpacing.md)
            ) {
                Text(request.summary, style = RudyTypography.bodyMedium, color = palette.foreground)

                Surface(
                    color = palette.surfaceElevated,
                    shape = RudyCorners.tile,
                    border = androidx.compose.foundation.BorderStroke(
                        RudyDimensions.hairline,
                        palette.outlineSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RudyCorners.tile)
                ) {
                    Column(
                        modifier = Modifier.padding(RudySpacing.md),
                        verticalArrangement = Arrangement.spacedBy(RudySpacing.sm)
                    ) {
                        Text("Origin: ${request.sourceLabel}", style = RudyTypography.bodySmall, color = palette.muted)
                        Text("Target: ${request.destinationLabel}", style = RudyTypography.bodySmall, color = palette.muted)
                    }
                }

                if (request.warning.isNotBlank()) {
                    Surface(
                        color = palette.danger.copy(alpha = 0.12f),
                        shape = RudyCorners.control,
                        border = androidx.compose.foundation.BorderStroke(
                            RudyDimensions.hairline,
                            palette.danger.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RudyCorners.control)
                    ) {
                        Box(modifier = Modifier.padding(RudySpacing.md)) {
                            Text(request.warning, style = RudyTypography.bodySmall, color = palette.danger)
                        }
                    }
                }
            }
        },
        containerColor = palette.toolbar,
        shape = RudyCorners.modal
    )
}

@Composable
internal fun BrowserAboutDialog(
    info: AboutInfo,
    onDismiss: () -> Unit
) {
    val palette = RudyTheme.palette

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            FilledTonalButton(
                onClick = onDismiss,
                shape = RudyCorners.control,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = palette.accent,
                    contentColor = palette.onAccent
                ),
                modifier = Modifier.heightIn(min = RudyDimensions.touchTarget)
            ) {
                Text("Close", style = RudyTypography.labelLarge)
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RudySpacing.sm)
            ) {
                RudyIcons.Info(tint = palette.accent, size = 22.dp)
                Column {
                    Text(info.appName, style = RudyTypography.titleMedium, color = palette.foreground)
                    Text("Version ${info.versionName} (${info.flavor})", style = RudyTypography.bodySmall, color = palette.muted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RudySpacing.md)
            ) {
                Surface(
                    color = palette.surfaceElevated,
                    shape = RudyCorners.tile,
                    border = androidx.compose.foundation.BorderStroke(
                        RudyDimensions.hairline,
                        palette.outlineSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RudyCorners.tile)
                ) {
                    Column(
                        modifier = Modifier.padding(RudySpacing.md),
                        verticalArrangement = Arrangement.spacedBy(RudySpacing.sm)
                    ) {
                        Text("Active Engine: ${info.activeEngine}", style = RudyTypography.titleSmall, color = palette.accent)
                        Text(info.engineDetail, style = RudyTypography.bodySmall, color = palette.muted)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(RudyDimensions.hairline)
                                .background(palette.outlineSubtle)
                        )
                        Text("Package: ${info.packageId}", style = RudyTypography.bodySmall, color = palette.foreground)
                        Text("Android SDK: ${info.androidSdk}", style = RudyTypography.bodySmall, color = palette.muted)
                        Text("Active Tabs: ${info.openTabs}", style = RudyTypography.bodySmall, color = palette.muted)
                        Text(
                            if (info.cleartextAllowed) "Cleartext HTTP: Allowed" else "Cleartext HTTP: Blocked (HTTPS only)",
                            style = RudyTypography.bodySmall,
                            color = if (info.cleartextAllowed) palette.danger else palette.positive
                        )
                    }
                }
            }
        },
        containerColor = palette.toolbar,
        shape = RudyCorners.modal
    )
}
