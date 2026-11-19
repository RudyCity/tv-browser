package io.rudycity.tvbrowser.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Rudy Design System v2: cool-slate browser chrome and native workspace tokens.
 * Antislop & fidelity invariants: WCAG AA verified contrast in both dark and light
 * themes, consistent 6/10/14/18/28dp corner hierarchy, >=48dp touch/focus targets,
 * visible keyboard focus rings, zero pure-black surfaces, and zero page CSS injection.
 */
internal object RudyColors {
    val background = Color(0xFF0C131E)
    val toolbar = Color(0xFF131B28)
    val surfaceElevated = Color(0xFF1B2536)
    val omnibox = Color(0xFF222E42)
    val omniboxFocused = Color(0xFF293850)
    val foreground = Color(0xFFF1F5FB)
    val muted = Color(0xFFB5C3D4)
    val accent = Color(0xFF9DC6FC)
    val onAccent = Color(0xFF071D38)
    val outline = Color(0xFF6E89AA)
    val outlineSubtle = Color(0xFF394B63)
    val positive = Color(0xFF7AD894)
    val danger = Color(0xFFF48B82)
}

internal object RudyLightColors {
    val background = Color(0xFFF2F5FA)
    val toolbar = Color(0xFFFFFFFF)
    val surfaceElevated = Color(0xFFF8FAFD)
    val omnibox = Color(0xFFE7EDF6)
    val omniboxFocused = Color(0xFFDCE5F2)
    val foreground = Color(0xFF142030)
    val muted = Color(0xFF445468)
    val accent = Color(0xFF1E54A8)
    val onAccent = Color(0xFFFFFFFF)
    val outline = Color(0xFF708297)
    val outlineSubtle = Color(0xFFCFD8E4)
    val positive = Color(0xFF1A6B35)
    val danger = Color(0xFFB3261E)
}

@Immutable
internal data class RudyPalette(
    val isDark: Boolean,
    val background: Color,
    val toolbar: Color,
    val surfaceElevated: Color,
    val omnibox: Color,
    val omniboxFocused: Color,
    val foreground: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val outline: Color,
    val outlineSubtle: Color,
    val positive: Color,
    val danger: Color
)

private val DarkRudyPalette = RudyPalette(
    isDark = true,
    background = RudyColors.background,
    toolbar = RudyColors.toolbar,
    surfaceElevated = RudyColors.surfaceElevated,
    omnibox = RudyColors.omnibox,
    omniboxFocused = RudyColors.omniboxFocused,
    foreground = RudyColors.foreground,
    muted = RudyColors.muted,
    accent = RudyColors.accent,
    onAccent = RudyColors.onAccent,
    outline = RudyColors.outline,
    outlineSubtle = RudyColors.outlineSubtle,
    positive = RudyColors.positive,
    danger = RudyColors.danger
)

private val LightRudyPalette = RudyPalette(
    isDark = false,
    background = RudyLightColors.background,
    toolbar = RudyLightColors.toolbar,
    surfaceElevated = RudyLightColors.surfaceElevated,
    omnibox = RudyLightColors.omnibox,
    omniboxFocused = RudyLightColors.omniboxFocused,
    foreground = RudyLightColors.foreground,
    muted = RudyLightColors.muted,
    accent = RudyLightColors.accent,
    onAccent = RudyLightColors.onAccent,
    outline = RudyLightColors.outline,
    outlineSubtle = RudyLightColors.outlineSubtle,
    positive = RudyLightColors.positive,
    danger = RudyLightColors.danger
)

internal val LocalRudyPalette = staticCompositionLocalOf { DarkRudyPalette }

internal object RudyTheme {
    val palette: RudyPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalRudyPalette.current
}

internal object RudySpacing {
    val compact = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

internal object RudyDimensions {
    val toolbarHeight = 58.dp
    val touchTarget = 48.dp
    val omniboxHeight = 48.dp
    val progressHeight = 2.dp
    val iconSize = 20.dp
    val leadingIconSize = 18.dp
    val hairline = 1.dp
    val focusStroke = 2.dp
}

internal object RudyCorners {
    val badge = RoundedCornerShape(6.dp)
    val control = RoundedCornerShape(10.dp)
    val tile = RoundedCornerShape(14.dp)
    val modal = RoundedCornerShape(18.dp)
    val omnibox = RoundedCornerShape(28.dp)
}

internal val RudyMonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.Medium
)

private val RudyDarkScheme = darkColorScheme(
    primary = RudyColors.accent,
    onPrimary = RudyColors.onAccent,
    surface = RudyColors.toolbar,
    onSurface = RudyColors.foreground,
    surfaceVariant = RudyColors.omnibox,
    onSurfaceVariant = RudyColors.muted,
    background = RudyColors.background,
    onBackground = RudyColors.foreground,
    outline = RudyColors.outline,
    outlineVariant = RudyColors.outlineSubtle,
    error = RudyColors.danger
)

private val RudyLightScheme = lightColorScheme(
    primary = RudyLightColors.accent,
    onPrimary = RudyLightColors.onAccent,
    surface = RudyLightColors.toolbar,
    onSurface = RudyLightColors.foreground,
    surfaceVariant = RudyLightColors.omnibox,
    onSurfaceVariant = RudyLightColors.muted,
    background = RudyLightColors.background,
    onBackground = RudyLightColors.foreground,
    outline = RudyLightColors.outline,
    outlineVariant = RudyLightColors.outlineSubtle,
    error = RudyLightColors.danger
)

internal val RudyTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 28.sp, lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontSize = 18.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    )
)

private val RudyShapes = Shapes(
    extraSmall = RudyCorners.badge,
    small = RudyCorners.control,
    medium = RudyCorners.tile,
    large = RudyCorners.modal,
    extraLarge = RudyCorners.omnibox
)

/**
 * Shared keyboard-focus ring and subtle spring press scale for interactive browser chrome.
 * Ensures keyboard and switch users always see a >=3:1 focus boundary in both themes.
 */
internal fun Modifier.rudyInteractiveFeedback(
    interactionSource: MutableInteractionSource,
    shape: Shape = RudyCorners.control,
    restingBorderColor: Color = Color.Transparent
): Modifier = composed {
    val palette = RudyTheme.palette
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "rudyPressScale"
    )
    val borderWidth = if (focused) RudyDimensions.focusStroke else RudyDimensions.hairline
    val borderColor = if (focused) palette.accent else restingBorderColor
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (borderColor != Color.Transparent) Modifier.border(borderWidth, borderColor, shape)
            else Modifier
        )
}

@Composable
internal fun RudyBrowserTheme(
    dark: Boolean? = null,
    themeMode: String? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when {
        dark != null -> dark
        themeMode == "dark" -> true
        themeMode == "light" -> false
        else -> systemDark
    }
    val palette = if (isDark) DarkRudyPalette else LightRudyPalette
    CompositionLocalProvider(LocalRudyPalette provides palette) {
        MaterialTheme(
            colorScheme = if (isDark) RudyDarkScheme else RudyLightScheme,
            typography = RudyTypography,
            shapes = RudyShapes,
            content = content
        )
    }
}
