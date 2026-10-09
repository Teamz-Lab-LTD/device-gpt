package com.teamz.lab.debugger.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.teamz.lab.debugger.ui.theme.DgMotion
import com.teamz.lab.debugger.ui.theme.LocalReduceMotion
import com.teamz.lab.debugger.ui.theme.motionSpring
import com.teamz.lab.debugger.ui.theme.motionSpringSpec
import com.teamz.lab.debugger.ui.theme.motionTween
import com.teamz.lab.debugger.ui.theme.motionTweenSpec
import kotlin.math.PI
import kotlin.math.sin

/*
 * The app's small set of moves. Each one changes transform and opacity only (never layout size), never
 * blocks touches, and does nothing when the user has turned animations off.
 */

/** Scale a pressed control shrinks to. */
const val PRESSED_SCALE = 0.97f

/** How far a shake travels to each side. */
private val ShakeDistance = 6.dp

/** Full left-right swings in one shake. */
private const val SHAKE_SWINGS = 2.5f

/** Where [shakeOffsetFraction] is at [progress] (0 to 1): a swing that dies out and ends exactly at rest. */
internal fun shakeOffsetFraction(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return (sin(p * 2.0 * PI * SHAKE_SWINGS) * (1f - p)).toFloat()
}

/**
 * Rise-and-fade entrance: the content comes up [rise] and fades in when [visible] turns true. It keeps its
 * place in the layout the whole time, so nothing around it moves.
 */
@Composable
fun Modifier.riseIn(visible: Boolean, delayMillis: Int = 0, rise: Dp = 8.dp): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = motionTween(DgMotion.standard, delayMillis, DgMotion.Enter),
        label = "rise-in",
    )
    val risePx = with(LocalDensity.current) { rise.toPx() }
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * risePx
    }
}

/** [riseIn] that plays once, when the content is first composed. */
@Composable
fun Modifier.riseInOnAppear(delayMillis: Int = 0, rise: Dp = 8.dp): Modifier {
    val reduceMotion = LocalReduceMotion.current
    var visible by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) { visible = true }
    return riseIn(visible, delayMillis, rise)
}

/** Fade only, for controls that must not move while they appear. */
@Composable
fun Modifier.fadeInWhen(visible: Boolean, delayMillis: Int = 0): Modifier {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = motionTween(DgMotion.standard, delayMillis, DgMotion.Enter),
        label = "fade-in",
    )
    return graphicsLayer { this.alpha = alpha }
}

/** A small spring pop (scale 0.9 to 1, with a fade) when [visible] turns true. */
@Composable
fun Modifier.popIn(visible: Boolean): Modifier {
    val scale by animateFloatAsState(if (visible) 1f else 0.9f, motionSpring(), label = "pop-in-scale")
    val alpha by animateFloatAsState(if (visible) 1f else 0f, motionTween(DgMotion.quick), label = "pop-in-alpha")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

/** One scale pop, played once when the content is first composed with [active] true: "this went well". */
@Composable
fun Modifier.popOnce(active: Boolean = true): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val scale = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active && !reduceMotion) {
            scale.snapTo(0.6f)
            scale.animateTo(1f, motionSpringSpec(false))
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * One short sideways shake, played once when the content is first composed with [active] true: "look here,
 * something is wrong". It lasts [DgMotion.shake] and ends exactly where it started.
 */
@Composable
fun Modifier.shakeOnce(active: Boolean = true): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val progress = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active && !reduceMotion) {
            progress.snapTo(0f)
            progress.animateTo(1f, motionTweenSpec(false, DgMotion.shake, easing = LinearEasing))
        }
    }
    val distancePx = with(LocalDensity.current) { ShakeDistance.toPx() }
    return graphicsLayer { translationX = shakeOffsetFraction(progress.value) * distancePx }
}

/**
 * The pressed look of the big test buttons: the control shrinks to [PRESSED_SCALE] while a finger is on it.
 * Pass the same [interactionSource] to the button.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = motionTween(DgMotion.quick),
        label = "press-scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
