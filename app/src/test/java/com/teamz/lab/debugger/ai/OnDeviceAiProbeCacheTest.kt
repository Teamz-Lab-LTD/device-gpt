package com.teamz.lab.debugger.ai

import com.teamz.lab.debugger.ai.ondevice.OnDeviceAiAvailability
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Guards the 2026-07-22 Crashlytics flood: 1,193 non-fatals from 9 users in one week, all
 * from `PrivateAiExplainer.checkStatusBlocking`.
 *
 * Root cause: `refreshStatus()` wrote [OnDeviceAiAvailability.lastKnownStatus] but never read
 * it, while its KDoc claimed "Caches the result". `ai_assistant_dialog.kt` calls it from a
 * `LaunchedEffect(Unit)` on every open of the AI chooser, so every open re-ran the AICore IPC
 * probe. On a device without AICore that probe throws, and the catch reported the throw — one
 * non-fatal per dialog open, per user, forever. The noise buried the app's real crashes.
 *
 * These are behavioural, not source-text, assertions: they drive the real object through
 * `probeOverride` and count probes. Both fail against the pre-fix implementation
 * (`probes = 3` and `probes = 2` respectively), which is the only reason to keep them.
 */
class OnDeviceAiProbeCacheTest {

    @Before fun setUp() = OnDeviceAiAvailability.resetForTest()

    @After fun tearDown() = OnDeviceAiAvailability.resetForTest()

    @Test
    fun `a terminal status is probed once and then served from cache`() = runBlocking {
        val probes = AtomicInteger(0)
        OnDeviceAiAvailability.probeOverride = {
            probes.incrementAndGet()
            OnDeviceAiAvailability.Status.UNSUPPORTED
        }

        // Three opens of the AI chooser on a device that cannot run Gemini Nano.
        repeat(3) { OnDeviceAiAvailability.refreshStatus(FAKE_CONTEXT) }

        assertEquals(
            "UNSUPPORTED describes the hardware and cannot change inside one process — " +
                "re-probing it is what produced 1,193 non-fatals from 9 users",
            1,
            probes.get(),
        )
        assertEquals(
            OnDeviceAiAvailability.Status.UNSUPPORTED,
            OnDeviceAiAvailability.lastKnownStatus(),
        )
    }

    @Test
    fun `LIBRARY_MISSING is also terminal`() = runBlocking {
        val probes = AtomicInteger(0)
        OnDeviceAiAvailability.probeOverride = {
            probes.incrementAndGet()
            OnDeviceAiAvailability.Status.LIBRARY_MISSING
        }

        repeat(3) { OnDeviceAiAvailability.refreshStatus(FAKE_CONTEXT) }

        assertEquals("a missing ML Kit GenAI library cannot appear at runtime", 1, probes.get())
    }

    @Test
    fun `DOWNLOADABLE keeps re-probing so the model download can be observed`() = runBlocking {
        val probes = AtomicInteger(0)
        OnDeviceAiAvailability.probeOverride = {
            // System-managed download completes between the 2nd and 3rd chooser open.
            if (probes.incrementAndGet() < 3) OnDeviceAiAvailability.Status.DOWNLOADABLE
            else OnDeviceAiAvailability.Status.READY
        }

        repeat(3) { OnDeviceAiAvailability.refreshStatus(FAKE_CONTEXT) }

        assertEquals(
            "DOWNLOADABLE must NOT be cached — Gemini Nano finishes downloading while the " +
                "app runs and the chooser has to see READY",
            3,
            probes.get(),
        )
        assertEquals(
            OnDeviceAiAvailability.Status.READY,
            OnDeviceAiAvailability.lastKnownStatus(),
        )
    }

    @Test
    fun `force bypasses the cache for an explicit user retry`() = runBlocking {
        val probes = AtomicInteger(0)
        OnDeviceAiAvailability.probeOverride = {
            probes.incrementAndGet()
            OnDeviceAiAvailability.Status.UNSUPPORTED
        }

        OnDeviceAiAvailability.refreshStatus(FAKE_CONTEXT)
        OnDeviceAiAvailability.refreshStatus(FAKE_CONTEXT, force = true)

        assertEquals("force = true must re-probe even a terminal status", 2, probes.get())
    }

    private companion object {
        /**
         * `probeOverride` short-circuits [OnDeviceAiAvailability] before the Context is ever
         * dereferenced, so this only has to satisfy the signature. A Mockito stub is used
         * rather than `null as Context`, which throws at class-init under Kotlin's null checks.
         */
        val FAKE_CONTEXT: android.content.Context =
            org.mockito.Mockito.mock(android.content.Context::class.java)
    }
}
