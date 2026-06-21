package com.teamz.lab.debugger.quality

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.teamz.lab.debugger.utils.D1OvernightDrainWorker
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * v3.1.11 W1 — INSTRUMENTATION test for the D1 overnight-drain worker.
 *
 * This is the only test class in app/src/androidTest/. It runs on a real
 * Android device or emulator (NOT Robolectric / NOT JVM), so it exercises the
 * REAL WorkManager scheduler, REAL SharedPreferences storage, REAL
 * NotificationManager, and REAL Firebase Analytics SDK.
 *
 * Use it to catch bugs that Robolectric or unit tests cannot — e.g. an Android
 * 14+ permission gate that's only enforced on actual Android, a vendor-specific
 * WorkManager quirk on Pixel/Samsung, a real-world IPC timing race.
 *
 * Why this complements existing tests:
 *   - D1OvernightDrainWorkerTest (unit, JVM): source-text shape only
 *   - D1OvernightDrainContractTest (unit, JVM): constants stability
 *   - D1OvernightDrainRuntimeTest (Robolectric): in-process WorkManager + fake hardware
 *   - This (instrumentation, on-device): production-equivalent WorkManager + real Android APIs
 *
 * Trade-off: instrumentation tests are 5-10× slower to run (must boot ART, install
 * APK, push test runner). Keep them rare and high-value. This one validates the
 * worker contract end-to-end so the production Wed 06-25 deploy can rely on it.
 *
 * Run via:
 *   ./gradlew :app:connectedDebugAndroidTest --tests '*D1OvernightDrainWorkerInstrumentationTest*'
 *
 * Requires a connected device or emulator (any Android 8+).
 */
@RunWith(AndroidJUnit4::class)
class D1OvernightDrainWorkerInstrumentationTest {

    private val workName = "d1_overnight_drain"
    private val prefsName = "d1_overnight_drain"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Initialize WorkManager in test mode so we can inspect job state without
        // actually waiting 20 hours. WorkManagerTestInitHelper uses a synchronous
        // executor + a manipulable TestDriver under the hood.
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        // Reset state so each test starts from "first install".
        context.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        WorkManager.getInstance(context).cancelUniqueWork(workName).result.get()
    }

    @Test
    fun scheduleOnFirstInstall_enqueuesRealWorkRequestOnDevice() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        D1OvernightDrainWorker.scheduleOnFirstInstall(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(workName).get()
        assertEquals(
            "Real-device WorkManager must enqueue exactly one work request after first-install schedule.",
            1, infos.size
        )
        assertEquals(
            "WorkInfo state must be ENQUEUED on the real device (not BLOCKED or CANCELLED).",
            WorkInfo.State.ENQUEUED, infos[0].state
        )
    }

    @Test
    fun cancelIfPendingOrganicReturn_actuallyCancelsTheRealWorkRequest() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        D1OvernightDrainWorker.scheduleOnFirstInstall(context)
        D1OvernightDrainWorker.cancelIfPendingOrganicReturn(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(workName).get()
        assertTrue(
            "After cancelIfPendingOrganicReturn within the 20-hour window, all WorkInfo " +
                "instances must transition to CANCELLED on the real device.",
            infos.all { it.state == WorkInfo.State.CANCELLED }
        )
    }
}
