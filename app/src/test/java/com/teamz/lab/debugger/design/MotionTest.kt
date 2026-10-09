package com.teamz.lab.debugger.design

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.ui.components.shownScore
import com.teamz.lab.debugger.ui.components.sweepDegrees
import com.teamz.lab.debugger.ui.theme.DgMotion
import com.teamz.lab.debugger.ui.theme.LocalReduceMotion
import com.teamz.lab.debugger.ui.theme.ProvideReduceMotion
import com.teamz.lab.debugger.ui.theme.isReduceMotionEnabled
import com.teamz.lab.debugger.ui.theme.motionSpring
import com.teamz.lab.debugger.ui.theme.motionSpringSpec
import com.teamz.lab.debugger.ui.theme.motionTween
import com.teamz.lab.debugger.ui.theme.motionTweenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Motion tokens, the reduce-motion switch, and the arithmetic behind the score ring. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MotionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun setAnimatorScale(scale: Float) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @Test fun `tokens hold the agreed values`() {
        assertEquals(150, DgMotion.quick)
        assertEquals(250, DgMotion.standard)
        assertEquals(400, DgMotion.slow)
        assertEquals(900, DgMotion.reveal)
        assertEquals(40, DgMotion.stagger)
        // Each easing starts at 0 and ends at 1, and Enter is ahead of Exit half-way through.
        for (easing in listOf(DgMotion.Standard, DgMotion.Enter, DgMotion.Exit)) {
            assertEquals(0f, easing.transform(0f), 0.001f)
            assertEquals(1f, easing.transform(1f), 0.001f)
        }
        assertTrue(DgMotion.Enter.transform(0.5f) > DgMotion.Exit.transform(0.5f))
    }

    @Test fun `tween spec is a snap under reduce motion and a token tween otherwise`() {
        val reduced: FiniteAnimationSpec<Float> = motionTweenSpec(reduceMotion = true)
        assertTrue("got $reduced", reduced is SnapSpec)
        assertEquals(0, (reduced as SnapSpec).delay)

        val normal: FiniteAnimationSpec<Float> =
            motionTweenSpec(reduceMotion = false, durationMillis = DgMotion.slow, delayMillis = DgMotion.stagger)
        normal as TweenSpec
        assertEquals(400, normal.durationMillis)
        assertEquals(40, normal.delay)
        assertSame(DgMotion.Standard, normal.easing)
        assertEquals(DgMotion.standard, (motionTweenSpec<Float>(reduceMotion = false) as TweenSpec).durationMillis)
    }

    @Test fun `spring spec is a snap under reduce motion and the one spring otherwise`() {
        assertTrue(motionSpringSpec<Float>(reduceMotion = true) is SnapSpec)
        val spring = motionSpringSpec<Float>(reduceMotion = false) as SpringSpec
        assertEquals(DgMotion.springDamping, spring.dampingRatio, 0f)
        assertEquals(DgMotion.springStiffness, spring.stiffness, 0f)
    }

    @Test fun `reduce motion follows the animator duration scale`() {
        assertFalse("unset means animations on", isReduceMotionEnabled(context.contentResolver))
        setAnimatorScale(0f)
        assertTrue(isReduceMotionEnabled(context.contentResolver))
        setAnimatorScale(0.5f)
        assertFalse(isReduceMotionEnabled(context.contentResolver))
        setAnimatorScale(1f)
        assertFalse(isReduceMotionEnabled(context.contentResolver))
    }

    @Test fun `composable helpers return a snap when the local says reduce motion`() {
        var reducedTween: FiniteAnimationSpec<Float>? = null
        var reducedSpring: FiniteAnimationSpec<Float>? = null
        var normalTween: FiniteAnimationSpec<Float>? = null
        var defaultLocal: Boolean? = null
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            defaultLocal = LocalReduceMotion.current
            normalTween = motionTween(DgMotion.quick)
            CompositionLocalProvider(LocalReduceMotion provides true) {
                reducedTween = motionTween()
                reducedSpring = motionSpring()
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, defaultLocal)
        assertEquals(DgMotion.quick, (normalTween as TweenSpec).durationMillis)
        assertTrue("got $reducedTween", reducedTween is SnapSpec)
        assertTrue("got $reducedSpring", reducedSpring is SnapSpec)
    }

    @Test fun `the provider reads the system setting and follows a change`() {
        setAnimatorScale(0f)
        var seen: Boolean? = null
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            ProvideReduceMotion { seen = LocalReduceMotion.current }
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(true, seen)

        setAnimatorScale(1f)
        context.contentResolver.notifyChange(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            null,
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, seen)
    }

    @Test fun `score ring arithmetic clamps and ends exactly on the score`() {
        assertEquals(0, shownScore(86, 100, 0f))
        assertEquals(43, shownScore(86, 100, 0.5f))
        assertEquals(86, shownScore(86, 100, 1f))
        assertEquals(100, shownScore(140, 100, 1f))
        assertEquals(0, shownScore(-5, 100, 1f))
        assertEquals(86, shownScore(86, 100, 3f))

        assertEquals(0f, sweepDegrees(86, 100, 0f), 0f)
        assertEquals(309.6f, sweepDegrees(86, 100, 1f), 0.01f)
        assertEquals(360f, sweepDegrees(140, 100, 1f), 0f)
        assertEquals(252f, sweepDegrees(7, 10, 1f), 0.01f)
        assertEquals(0f, sweepDegrees(5, 0, 1f), 0f)
    }
}
