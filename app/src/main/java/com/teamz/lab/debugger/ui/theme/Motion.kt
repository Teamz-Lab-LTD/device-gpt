package com.teamz.lab.debugger.ui.theme

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The app's motion tokens. Every duration and easing in the UI comes from here, through [motionTween] and
 * [motionSpring], so that one switch ([LocalReduceMotion]) can turn all of it off.
 */
@Suppress("ConstPropertyName")
object DgMotion {
    /** Small state changes: a press, a tint, a chip. Milliseconds. */
    const val quick = 150

    /** The default: expanding a row, swapping content. Milliseconds. */
    const val standard = 250

    /** Large surfaces entering: sheets, the done card. Milliseconds. */
    const val slow = 400

    /** The score reveal, the one long moment in the app. Milliseconds. */
    const val reveal = 900

    /** Delay between neighbours that enter one after another. Milliseconds. */
    const val stagger = 40

    /** One way of a decorative loop (a pulse, a glow, a shimmer pass). Loops stop under reduce-motion. */
    const val pulse = 1200

    /** One turn of a loading spinner. Milliseconds. */
    const val spin = 1000

    /** One short "something is wrong" shake, start to rest. Milliseconds. */
    const val shake = 300

    /** For things that move on screen from one place to another. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** For things arriving: fast start, long settle. */
    val Enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** For things leaving: they speed up and go. */
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** Damping of the app's one spring: a small, single overshoot. */
    const val springDamping = 0.7f

    /** Stiffness of the app's one spring. */
    const val springStiffness = 400f
}

/**
 * True when the user has turned animations off. Provide it once at the app root with
 * [ProvideReduceMotion]; it is `false` where nothing provides it.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * True when the system animator duration scale is 0, which is what both "Remove animations" in
 * Accessibility and "Animator duration scale: off" in Developer options set. Any failure to read the
 * setting counts as "animations on".
 */
fun isReduceMotionEnabled(resolver: ContentResolver): Boolean =
    try {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (_: Exception) {
        false
    }

/**
 * Whether the user has turned animations off, kept up to date while the app is open: changing the setting
 * recomposes the callers.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.applicationContext.contentResolver
    var reduce by remember(resolver) { mutableStateOf(isReduceMotionEnabled(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = isReduceMotionEnabled(resolver)
            }
        }
        val registered = try {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
            true
        } catch (_: Exception) {
            false
        }
        // The setting may have changed between the first read and the registration.
        reduce = isReduceMotionEnabled(resolver)
        onDispose {
            if (registered) resolver.unregisterContentObserver(observer)
        }
    }
    return reduce
}

/** Reads the system setting and provides it as [LocalReduceMotion] to [content]. Call once, at the root. */
@Composable
fun ProvideReduceMotion(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReduceMotion provides rememberReduceMotion(), content = content)
}

/**
 * The tween behind [motionTween], usable outside composition (and in tests).
 *
 * @param reduceMotion when true the result is a snap: the value jumps to its target.
 */
fun <T> motionTweenSpec(
    reduceMotion: Boolean,
    durationMillis: Int = DgMotion.standard,
    delayMillis: Int = 0,
    easing: Easing = DgMotion.Standard,
): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = easing)

/**
 * The spring behind [motionSpring], usable outside composition (and in tests).
 *
 * @param reduceMotion when true the result is a snap: the value jumps to its target.
 */
fun <T> motionSpringSpec(
    reduceMotion: Boolean,
    dampingRatio: Float = DgMotion.springDamping,
    stiffness: Float = DgMotion.springStiffness,
    visibilityThreshold: T? = null,
): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else spring(dampingRatio, stiffness, visibilityThreshold)

/**
 * A tween from the motion tokens, or a snap when the user has turned animations off.
 *
 * ```
 * val alpha by animateFloatAsState(if (visible) 1f else 0f, motionTween(DgMotion.quick))
 * ```
 */
@Composable
@ReadOnlyComposable
fun <T> motionTween(
    durationMillis: Int = DgMotion.standard,
    delayMillis: Int = 0,
    easing: Easing = DgMotion.Standard,
): FiniteAnimationSpec<T> = motionTweenSpec(LocalReduceMotion.current, durationMillis, delayMillis, easing)

/** The app's one spring, or a snap when the user has turned animations off. */
@Composable
@ReadOnlyComposable
fun <T> motionSpring(
    dampingRatio: Float = DgMotion.springDamping,
    stiffness: Float = DgMotion.springStiffness,
    visibilityThreshold: T? = null,
): FiniteAnimationSpec<T> =
    motionSpringSpec(LocalReduceMotion.current, dampingRatio, stiffness, visibilityThreshold)

/**
 * A value that loops for ever between [initialValue] and [targetValue]: the one way to write a decorative
 * loop (pulse, glow, shimmer, spinner). When the user has turned animations off the loop does not run and
 * the value stays at [restingValue].
 *
 * @param durationMillis one way of the loop, from the motion tokens.
 * @param repeatMode [RepeatMode.Reverse] goes there and back; [RepeatMode.Restart] jumps back to the start.
 * @param holdMillis time to wait at [targetValue] before the next round (only with [RepeatMode.Restart]).
 * @param restingValue the value shown when nothing may move.
 */
@Composable
fun rememberMotionLoop(
    initialValue: Float,
    targetValue: Float,
    durationMillis: Int = DgMotion.pulse,
    easing: Easing = DgMotion.Standard,
    repeatMode: RepeatMode = RepeatMode.Reverse,
    holdMillis: Int = 0,
    restingValue: Float = initialValue,
    label: String = "motion-loop",
): State<Float> {
    if (LocalReduceMotion.current) {
        return remember(restingValue) { mutableFloatStateOf(restingValue) }
    }
    val transition = rememberInfiniteTransition(label = label)
    val animation = if (holdMillis > 0) {
        keyframes {
            this.durationMillis = durationMillis + holdMillis
            initialValue at 0 using easing
            targetValue at durationMillis
            targetValue at durationMillis + holdMillis
        }
    } else {
        tween(durationMillis = durationMillis, easing = easing)
    }
    return transition.animateFloat(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(animation = animation, repeatMode = repeatMode),
        label = label,
    )
}
