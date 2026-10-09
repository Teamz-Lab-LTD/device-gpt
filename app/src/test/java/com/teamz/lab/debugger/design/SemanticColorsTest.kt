package com.teamz.lab.debugger.design

import androidx.compose.ui.graphics.Color
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.ui.theme.DesignSystemDarkColorScheme
import com.teamz.lab.debugger.ui.theme.DesignSystemLightColorScheme
import com.teamz.lab.debugger.ui.theme.DgSemanticColors
import com.teamz.lab.debugger.ui.theme.DgSemanticColorsDark
import com.teamz.lab.debugger.ui.theme.DgSemanticColorsLight
import com.teamz.lab.debugger.ui.theme.contrastOn
import com.teamz.lab.debugger.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every semantic colour reads at 4.5:1 or better on its theme's surfaces, and under its own `on` colour. */
class SemanticColorsTest {

    private fun DgSemanticColors.pairs() = listOf(
        "good" to (good to onGood),
        "warn" to (warn to onWarn),
        "accent" to (accent to onAccent),
        "info" to (info to onInfo),
    )

    private fun check(theme: String, colors: DgSemanticColors, surfaces: List<Color>, error: Color) {
        for ((name, pair) in colors.pairs()) {
            val (color, on) = pair
            for (surface in surfaces) {
                val ratio = contrastRatio(color, surface)
                assertTrue("$theme $name on $surface is only $ratio:1", ratio >= 4.5f)
            }
            val onRatio = contrastRatio(on, color)
            assertTrue("$theme on-$name over $name is only $onRatio:1", onRatio >= 4.5f)
        }
        for (surface in surfaces) {
            assertTrue("$theme error on $surface", contrastRatio(error, surface) >= 4.5f)
        }
    }

    @Test fun `dark theme colours pass on background and surface`() {
        check(
            "dark",
            DgSemanticColorsDark,
            listOf(DesignSystemColors.Dark, DesignSystemColors.DarkII),
            DesignSystemDarkColorScheme.error,
        )
    }

    @Test fun `light theme colours pass on background and surface variant`() {
        check(
            "light",
            DgSemanticColorsLight,
            listOf(DesignSystemColors.White, DesignSystemColors.Light),
            DesignSystemLightColorScheme.error,
        )
    }

    @Test fun `contrast helpers agree with the known values`() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(Color.White, Color.White), 0.001f)
        assertEquals(DesignSystemColors.Dark, contrastOn(DesignSystemColors.NeonGreen))
        assertEquals(DesignSystemColors.White, contrastOn(DesignSystemColors.DarkII))
    }
}
