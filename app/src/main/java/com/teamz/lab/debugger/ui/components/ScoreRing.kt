package com.teamz.lab.debugger.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.teamz.lab.debugger.ui.theme.AppTheme
import com.teamz.lab.debugger.ui.theme.DebuggerTheme
import com.teamz.lab.debugger.ui.theme.DgMotion
import com.teamz.lab.debugger.ui.theme.LocalReduceMotion
import com.teamz.lab.debugger.ui.theme.dgSemanticColors
import kotlin.math.roundToInt

/** The score a ring shows part-way through its reveal: [score] clamped to 0..[maxScore], times [progress]. */
internal fun shownScore(score: Int, maxScore: Int, progress: Float): Int =
    (score.coerceIn(0, maxScore.coerceAtLeast(0)) * progress.coerceIn(0f, 1f)).roundToInt()

/** Degrees of ring to draw for [score] out of [maxScore] at reveal [progress] (0 to 1). */
internal fun sweepDegrees(score: Int, maxScore: Int, progress: Float): Float {
    if (maxScore <= 0) return 0f
    return 360f * (score.coerceIn(0, maxScore).toFloat() / maxScore) * progress.coerceIn(0f, 1f)
}

/**
 * The app's signature moment: a ring that fills to the score while the number in the middle counts up
 * with it.
 *
 * The ring sweeps from the top, clockwise, over [DgMotion.reveal] with the [DgMotion.Enter] easing. When
 * the user has turned animations off (see [LocalReduceMotion]) or [animate] is false, the final value is
 * shown at once. Screen readers hear [contentDescription] once, never the counting.
 *
 * @param score the value to show; clamped to 0..[maxScore].
 * @param maxScore the value of a full ring.
 * @param size outer diameter.
 * @param strokeWidth ring thickness.
 * @param trackColor the unfilled part of the ring.
 * @param color the filled part. Pass the good / fair / poor colour for the score.
 * @param animate false shows the final value without the reveal, for example when the card is revisited.
 * @param contentDescription what a screen reader says. Pass a localized sentence; the default is the bare
 * "score / max".
 * @param onRevealFinished called once the ring has reached the score (immediately when nothing animates).
 * Start whatever follows the reveal from here.
 * @param content the centre of the ring. It receives the score to show right now; the default is
 * [ScoreRingNumber].
 */
@Composable
fun ScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    maxScore: Int = 100,
    size: Dp = 160.dp,
    strokeWidth: Dp = 12.dp,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    color: Color = dgSemanticColors().accent,
    animate: Boolean = true,
    contentDescription: String = "$score / $maxScore",
    onRevealFinished: (() -> Unit)? = null,
    content: @Composable BoxScope.(shownScore: Int) -> Unit = { shown ->
        ScoreRingNumber(value = shown, finalValue = score)
    },
) {
    val reduceMotion = LocalReduceMotion.current
    val moves = animate && !reduceMotion
    val progress = remember { Animatable(if (moves) 0f else 1f) }
    val finished by rememberUpdatedState(onRevealFinished)

    LaunchedEffect(score, maxScore, moves) {
        if (moves) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = DgMotion.reveal, easing = DgMotion.Enter))
        } else {
            progress.snapTo(1f)
        }
        finished?.invoke()
    }

    // Recompose the centre only when the whole number changes, not on every frame.
    val shown by remember(score, maxScore) { derivedStateOf { shownScore(score, maxScore, progress.value) } }
    val clamped = score.coerceIn(0, maxScore.coerceAtLeast(0))

    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = clamped.toFloat(),
                    range = 0f..maxScore.coerceAtLeast(1).toFloat(),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            val sweep = sweepDegrees(score, maxScore, progress.value)
            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Box(modifier = Modifier.padding(strokeWidth + 4.dp), contentAlignment = Alignment.Center) {
            content(shown)
        }
    }
}

/**
 * A number set in tabular figures: every digit takes the width of the widest digit of [style], so the
 * number does not shake from side to side while it counts.
 *
 * The app's brand font has proportional digits and no `tnum` feature, so asking the font for tabular
 * figures is not enough; the digits are placed in equal cells here. `tnum` is still requested for fonts
 * that have it.
 *
 * @param value the number to show now.
 * @param finalValue the number the count ends on; the width is reserved for it from the first frame.
 */
@Composable
fun ScoreRingNumber(
    value: Int,
    modifier: Modifier = Modifier,
    finalValue: Int = value,
    style: TextStyle = MaterialTheme.typography.displayLarge,
    color: Color = LocalContentColor.current,
) {
    val tabular = remember(style) { style.copy(fontFeatureSettings = "tnum", textAlign = TextAlign.Center) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val cell = remember(tabular, density) {
        val widest = (0..9).maxOf { measurer.measure(it.toString(), tabular, maxLines = 1).size.width }
        with(density) { widest.toDp() }
    }
    val digits = value.toString()
    val cells = maxOf(digits.length, finalValue.toString().length)
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center) {
        // Half cells on both sides keep the total width fixed while the number is a digit short.
        repeat(cells - digits.length) { Box(modifier = Modifier.width(cell / 2)) }
        digits.forEach { digit ->
            Text(
                text = digit.toString(),
                style = tabular,
                color = color,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(cell),
            )
        }
        repeat(cells - digits.length) { Box(modifier = Modifier.width(cell / 2)) }
    }
}

@Preview(name = "Score ring, dark", showBackground = true, backgroundColor = 0xFF12151A)
@Composable
private fun ScoreRingDarkPreview() {
    DebuggerTheme(AppTheme.DESIGN_SYSTEM_DARK) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(24.dp)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                ScoreRing(score = 86, animate = false)
            }
        }
    }
}

@Preview(name = "Score ring, light", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ScoreRingLightPreview() {
    DebuggerTheme(AppTheme.DESIGN_SYSTEM_LIGHT) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(24.dp)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                ScoreRing(score = 42, color = dgSemanticColors().warn, animate = false)
            }
        }
    }
}

@Preview(name = "Score ring, small and animated", showBackground = true, backgroundColor = 0xFF1D1F25)
@Composable
private fun ScoreRingSmallPreview() {
    DebuggerTheme(AppTheme.DESIGN_SYSTEM_DARK) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                ScoreRing(score = 7, maxScore = 10, size = 72.dp, strokeWidth = 6.dp) { shown ->
                    ScoreRingNumber(value = shown, finalValue = 7, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
