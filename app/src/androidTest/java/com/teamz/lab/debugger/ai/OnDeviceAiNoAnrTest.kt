package com.teamz.lab.debugger.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.teamz.lab.debugger.ai.ondevice.OnDeviceAiAvailability
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the 2026-07-10 "AI chooser freezes, Cancel does nothing" ANR.
 *
 * `OnDeviceAiAvailability.refreshStatus()` transitively calls
 * `Summarizer.checkFeatureStatus().get()`. Before the fix it declared no dispatcher, so
 * calling it from a Compose `LaunchedEffect` ran the blocking IPC on `Dispatchers.Main`.
 * The main looper stopped draining, so no tap — including Cancel — was ever dispatched.
 *
 * A healthy AICore answers in milliseconds, which is why simply calling the real probe on a
 * Pixel 8a passes even against the broken code. The only way to test the property we care
 * about is to make the probe slow on purpose. `probeOverride` does that.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class OnDeviceAiNoAnrTest {

    private val appContext: Context get() = ApplicationProvider.getApplicationContext()

    /** How long the fake AICore stalls. Comfortably longer than the observation window. */
    private val slowProbeMillis = 3_000L

    @After
    fun clearSeam() {
        OnDeviceAiAvailability.probeOverride = null
    }

    /**
     * THE regression test. A probe that stalls 3s is launched from `Dispatchers.Main`,
     * exactly as `ai_assistant_dialog.kt`'s `LaunchedEffect` does. Meanwhile a ticker posts
     * to the main looper every 50ms. If `refreshStatus` occupies Main, the ticker starves.
     *
     * Against the pre-fix code this fails with ~0 ticks. Against the fix it sees ~30.
     */
    @Test
    fun mainLooperKeepsDrainingWhileASlowAiProbeRuns() {
        OnDeviceAiAvailability.probeOverride = {
            SystemClock.sleep(slowProbeMillis)
            OnDeviceAiAvailability.Status.UNSUPPORTED
        }

        val ticks = AtomicInteger(0)
        val handler = Handler(Looper.getMainLooper())
        val ticker = object : Runnable {
            override fun run() {
                ticks.incrementAndGet()
                handler.postDelayed(this, 50L)
            }
        }
        handler.post(ticker)
        MainScope().launch { OnDeviceAiAvailability.refreshStatus(appContext) }

        SystemClock.sleep(1_500L)
        val advanced = ticks.get()
        handler.removeCallbacks(ticker)

        // 1500ms / 50ms == ~30 expected. Blocking Main yields ~1.
        assertTrue(
            "main looper advanced only $advanced ticks while a 3s AI probe ran — " +
                "refreshStatus is executing on Dispatchers.Main (this is the ANR)",
            advanced > 10
        )
    }

    /** A slow probe must not stall the caller past its own timeout budget either. */
    @Test
    fun slowProbeStillReturnsToItsCaller() = runBlocking {
        OnDeviceAiAvailability.probeOverride = {
            SystemClock.sleep(slowProbeMillis)
            OnDeviceAiAvailability.Status.UNSUPPORTED
        }
        val status = withTimeoutOrNull(8_000L) { OnDeviceAiAvailability.refreshStatus(appContext) }
        assertNotNull("refreshStatus never returned", status)
    }

    /** The real probe, on whatever hardware this runs on, must terminate. */
    @Test
    fun realProbeCompletesWithinItsTimeoutBudget() = runBlocking {
        val status = withTimeoutOrNull(8_000L) { OnDeviceAiAvailability.refreshStatus(appContext) }
        assertNotNull(
            "real refreshStatus did not return within 8s — a .get() is still unbounded",
            status
        )
    }

    /** Repeated chooser opens must not deadlock on the @Synchronized getOrCreate. */
    @Test
    fun repeatedProbesDoNotDeadlock() = runBlocking {
        repeat(3) { i ->
            val status = withTimeoutOrNull(8_000L) { OnDeviceAiAvailability.refreshStatus(appContext) }
            assertNotNull("probe #$i hung", status)
        }
    }
}
