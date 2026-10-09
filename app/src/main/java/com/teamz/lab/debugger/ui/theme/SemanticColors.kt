package com.teamz.lab.debugger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Meaning colours the Material colour scheme does not carry: good, warn, accent and info.
 *
 * "Bad" is `MaterialTheme.colorScheme.error` and is not repeated here. Every colour is paired with the `on`
 * colour to use for text or icons drawn on top of it, and every colour keeps at least 4.5:1 against the
 * app's surfaces for its theme (checked by `SemanticColorsTest`).
 *
 * @property good a pass or a healthy value.
 * @property onGood content colour on a [good] background.
 * @property warn needs attention.
 * @property onWarn content colour on a [warn] background.
 * @property accent the brand highlight: lime on dark surfaces, a dark olive on light ones (lime on white is
 * unreadable).
 * @property onAccent content colour on an [accent] background.
 * @property info neutral information.
 * @property onInfo content colour on an [info] background.
 */
@Immutable
data class DgSemanticColors(
    val good: Color,
    val onGood: Color,
    val warn: Color,
    val onWarn: Color,
    val accent: Color,
    val onAccent: Color,
    val info: Color,
    val onInfo: Color,
)

/** Semantic colours for the dark theme, on `#12151A` and `#1D1F25`. */
val DgSemanticColorsDark = DgSemanticColors(
    good = Color(0xFF34D399),
    onGood = DesignSystemColors.Dark,
    warn = Color(0xFFFFA94D),
    onWarn = DesignSystemColors.Dark,
    accent = DesignSystemColors.NeonGreen,
    onAccent = DesignSystemColors.Dark,
    info = Color(0xFF7CB7FF),
    onInfo = DesignSystemColors.Dark,
)

/** Semantic colours for the light theme, on `#FFFFFF` and `#F4F5F5`. */
val DgSemanticColorsLight = DgSemanticColors(
    good = Color(0xFF0F7B3F),
    onGood = DesignSystemColors.White,
    warn = Color(0xFFA85100),
    onWarn = DesignSystemColors.White,
    accent = Color(0xFF4A6200),
    onAccent = DesignSystemColors.White,
    info = Color(0xFF1D5FD1),
    onInfo = DesignSystemColors.White,
)

/**
 * The semantic colours for the theme in use, chosen from how dark the current background is so it follows
 * [DebuggerTheme] without the theme having to provide anything.
 */
@Composable
@ReadOnlyComposable
fun dgSemanticColors(): DgSemanticColors =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DgSemanticColorsDark else DgSemanticColorsLight

/**
 * WCAG contrast ratio between two opaque colours, from 1 (none) to 21 (black on white). Use it to pick a
 * content colour for a background that is not a token.
 */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val lighter = if (la > lb) la else lb
    val darker = if (la > lb) lb else la
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** Black or white, whichever reads better on [background]. For backgrounds that have no `on` token. */
fun contrastOn(background: Color): Color =
    if (contrastRatio(background, DesignSystemColors.Dark) >= contrastRatio(background, DesignSystemColors.White)) {
        DesignSystemColors.Dark
    } else {
        DesignSystemColors.White
    }
