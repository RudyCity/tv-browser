package io.rudycity.tvbrowser.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Rudy Design System v1 — minimal browser chrome, not a webpage theme.
 * Antislop rules: restrained layers, clear focus, consistent touch targets,
 * no decorative gradients, and no styling injected into user websites.
 */
internal object RudyColors {
    val toolbar = Color(0xFF151C27)
    val omnibox = Color(0xFF283444)
    val foreground = Color(0xFFF0F4FA)
    val muted = Color(0xFFB3C0CF)
    val accent = Color(0xFF9DC6FC)
    val onAccent = Color(0xFF061B34)
}

internal object RudySpacing {
    val compact = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
}

internal object RudyDimensions {
    val toolbarHeight = 68.dp
    val touchTarget = 48.dp
    val omniboxHeight = 56.dp
    val progressHeight = 2.dp
    val iconSize = 24.dp
    val leadingIconSize = 18.dp
}

internal object RudyCorners {
    val omnibox = RoundedCornerShape(28.dp)
}

private val RudyDarkScheme = darkColorScheme(
    primary = RudyColors.accent,
    onPrimary = RudyColors.onAccent,
    surface = RudyColors.toolbar,
    onSurface = RudyColors.foreground,
    surfaceVariant = RudyColors.omnibox,
    onSurfaceVariant = RudyColors.muted
)

private val RudyTypography = Typography(
    bodyMedium = TextStyle(
        fontSize = 14.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.Medium, color = RudyColors.foreground
    ),
    labelMedium = TextStyle(
        fontSize = 13.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.Medium, color = RudyColors.muted
    ),
    titleMedium = TextStyle(
        fontSize = 18.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold, color = RudyColors.foreground
    )
)

private val RudyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
internal fun RudyBrowserTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RudyDarkScheme,
        typography = RudyTypography,
        shapes = RudyShapes,
        content = content
    )
}
