package io.rudycity.tvbrowser.compose

import org.junit.Assert.assertTrue
import org.junit.Test

class RudyDesignSystemTest {
    @Test fun touchTargetAndSpacingFollowDesignMinimums() {
        assertTrue(RudyDimensions.touchTarget.value >= 48f)
        assertTrue(RudyDimensions.omniboxHeight.value >= RudyDimensions.touchTarget.value)
        assertTrue(RudyDimensions.toolbarHeight.value >= RudyDimensions.omniboxHeight.value)
        assertTrue(RudySpacing.compact.value < RudySpacing.md.value)
    }

    @Test fun lightAndDarkPalettesAreBothOpaque() {
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
}
