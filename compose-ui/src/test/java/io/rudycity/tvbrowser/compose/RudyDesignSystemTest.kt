package io.rudycity.tvbrowser.compose

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RudyDesignSystemTest {
    private fun linearize(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double {
        return 0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)
    }

    private fun contrast(fg: Color, bg: Color): Double {
        val l1 = luminance(fg)
        val l2 = luminance(bg)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    @Test fun touchTargetAndSpacingFollowDesignMinimums() {
        assertTrue(RudyDimensions.touchTarget.value >= 48f)
        assertTrue(RudyDimensions.omniboxHeight.value >= RudyDimensions.touchTarget.value)
        assertTrue(RudyDimensions.toolbarHeight.value >= RudyDimensions.omniboxHeight.value)
        assertTrue(RudySpacing.compact.value < RudySpacing.md.value)
        assertTrue(RudySpacing.md.value < RudySpacing.xl.value)
    }

    @Test fun lightAndDarkPalettesAreBothOpaqueAndNeverPureBlack() {
        assertNotEquals(Color.Black, RudyColors.background)
        assertTrue(RudyLightColors.foreground.alpha >= 1f)
        assertTrue(RudyLightColors.toolbar.alpha >= 1f)
        assertTrue(RudyLightColors.background.alpha >= 1f)
        assertTrue(RudyLightColors.accent != RudyColors.accent)
    }

    @Test fun browserChromePaletteUsesOpaqueForeground() {
        assertTrue(RudyColors.foreground.alpha >= 1f)
        assertTrue(RudyColors.toolbar.alpha >= 1f)
        assertTrue(RudyColors.accent.alpha >= 1f)
    }

    @Test fun darkPaletteMeetsWcagAaContrastThresholds() {
        assertTrue(contrast(RudyColors.foreground, RudyColors.background) >= 4.5)
        assertTrue(contrast(RudyColors.foreground, RudyColors.toolbar) >= 4.5)
        assertTrue(contrast(RudyColors.foreground, RudyColors.surfaceElevated) >= 4.5)
        assertTrue(contrast(RudyColors.foreground, RudyColors.omnibox) >= 4.5)
        assertTrue(contrast(RudyColors.muted, RudyColors.toolbar) >= 4.5)
        assertTrue(contrast(RudyColors.muted, RudyColors.surfaceElevated) >= 4.5)
        assertTrue(contrast(RudyColors.muted, RudyColors.omnibox) >= 4.5)
        assertTrue(contrast(RudyColors.accent, RudyColors.toolbar) >= 4.5)
        assertTrue(contrast(RudyColors.onAccent, RudyColors.accent) >= 4.5)
        assertTrue(contrast(RudyColors.positive, RudyColors.omnibox) >= 4.5)
        assertTrue(contrast(RudyColors.danger, RudyColors.surfaceElevated) >= 4.5)
        assertTrue(contrast(RudyColors.outline, RudyColors.surfaceElevated) >= 3.0)
        assertTrue(contrast(RudyColors.outline, RudyColors.omnibox) >= 3.0)
    }

    @Test fun lightPaletteMeetsWcagAaContrastThresholds() {
        assertTrue(contrast(RudyLightColors.foreground, RudyLightColors.background) >= 4.5)
        assertTrue(contrast(RudyLightColors.foreground, RudyLightColors.toolbar) >= 4.5)
        assertTrue(contrast(RudyLightColors.foreground, RudyLightColors.omnibox) >= 4.5)
        assertTrue(contrast(RudyLightColors.muted, RudyLightColors.toolbar) >= 4.5)
        assertTrue(contrast(RudyLightColors.muted, RudyLightColors.omnibox) >= 4.5)
        assertTrue(contrast(RudyLightColors.accent, RudyLightColors.toolbar) >= 4.5)
        assertTrue(contrast(RudyLightColors.onAccent, RudyLightColors.accent) >= 4.5)
        assertTrue(contrast(RudyLightColors.positive, RudyLightColors.omnibox) >= 4.5)
        assertTrue(contrast(RudyLightColors.danger, RudyLightColors.toolbar) >= 4.5)
        assertTrue(contrast(RudyLightColors.outline, RudyLightColors.toolbar) >= 3.0)
        assertTrue(contrast(RudyLightColors.outline, RudyLightColors.omnibox) >= 3.0)
    }
}
