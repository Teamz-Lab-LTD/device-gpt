package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.ui.adaptive.FabScrollPolicy
import com.teamz.lab.debugger.utils.HealthScoreUtils
import com.teamz.lab.debugger.utils.SpeedTestPolicy
import com.teamz.lab.debugger.utils.aiReadinessLine
import com.teamz.lab.debugger.utils.faceUnlockLine
import com.teamz.lab.debugger.utils.hackabilityLine
import com.teamz.lab.debugger.utils.hasUltraWideCamera
import com.teamz.lab.debugger.utils.megabitsPerSecond
import com.teamz.lab.debugger.widgets.LockScreenMonitorWidget
import com.teamz.lab.debugger.widgets.WidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 2026-10-05, vc50 on the owner's Pixel 8a and a fresh emulator, as a new user:
 *  - the widget pin prompt (≈30% accept) produced "Health: 0/10 · ⚠️ Low Score" with every metric
 *    "--", right after the app showed an Excellent score — only the opt-in monitor service wrote
 *    the widget's data;
 *  - "Internet Health Score 60/100 ⚠️ Average" on healthy Wi-Fi: speeds were megaBYTES per
 *    second labelled Mbps (1/8 of real);
 *  - "Can be fooled by a photo" on a Class 3 face unlock; "No Ultra-Wide Camera" on a phone with
 *    one; "Not fully AI-ready" on a Tensor G3 from a feature flag Android does not define.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PixelWalkFixesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun src(rel: String): String {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return File(dir, rel).readText()
    }

    @Before
    fun clear() {
        context.getSharedPreferences(WidgetSnapshot.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("health_score_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    // ---- widget ---------------------------------------------------------------------------

    @Test
    fun `saving the daily score fills the widget without the monitor service`() {
        HealthScoreUtils.saveHealthScore(context, 9)
        WidgetSnapshot.pending!!.get()
        val p = context.getSharedPreferences(WidgetSnapshot.PREFS, Context.MODE_PRIVATE)
        assertEquals(9, p.getInt(WidgetSnapshot.KEY_HEALTH_SCORE, -1))
        assertEquals(WidgetSnapshot.today(), p.getString(WidgetSnapshot.KEY_HEALTH_SCORE_DAY, null))
        assertTrue(p.getLong("last_update", 0) > 0)
        assertTrue(p.getString("ram", "")!!.contains("%)"))
    }

    @Test
    fun `refresh with no new score reuses today's saved one`() {
        HealthScoreUtils.saveHealthScore(context, 8)
        WidgetSnapshot.pending!!.get()
        context.getSharedPreferences(WidgetSnapshot.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        WidgetSnapshot.write(context)
        assertEquals(8, context.getSharedPreferences(WidgetSnapshot.PREFS, Context.MODE_PRIVATE).getInt("health_score", -1))
    }

    @Test
    fun `no score yet renders as unknown, not zero`() {
        assertEquals("Health: --/10", LockScreenMonitorWidget.healthScoreLabel(context, false, 0, ""))
        assertEquals("Health: 9/10 ↑", LockScreenMonitorWidget.healthScoreLabel(context, true, 9, " ↑"))
        assertEquals("📊 Open app to scan", LockScreenMonitorWidget.primaryStatus(context, false, "--", "--", 0))
        assertEquals("⚠️ Low Score", LockScreenMonitorWidget.primaryStatus(context, true, "--", "--", 3))
        assertEquals("✅ Healthy", LockScreenMonitorWidget.primaryStatus(context, true, "30.0", "40", 9))
    }

    @Test
    fun `a score from an earlier day is not shown as current`() {
        assertTrue(LockScreenMonitorWidget.scoreIsCurrent(true, "2026-10-05", "2026-10-05", false))
        assertFalse(LockScreenMonitorWidget.scoreIsCurrent(true, "2026-09-12", "2026-10-05", false))
        assertFalse(LockScreenMonitorWidget.scoreIsCurrent(true, null, "2026-10-05", false))
        assertTrue("live service score is current", LockScreenMonitorWidget.scoreIsCurrent(true, null, "2026-10-05", true))
        assertFalse(LockScreenMonitorWidget.scoreIsCurrent(false, "2026-10-05", "2026-10-05", true))
        val w = src("app/src/main/java/com/teamz/lab/debugger/widgets/LockScreenMonitorWidget.kt")
        assertTrue("timeline snapshot only for a current score",
            w.contains("if (hasScore) com.teamz.lab.debugger.db.DeviceEventsRepository.recordDailySnapshotIfDue"))
    }

    @Test
    fun `stale monitor-service readings are not shown`() {
        val now = 1_000_000_000L
        assertTrue(LockScreenMonitorWidget.isServiceFresh(now - 30_000, now))
        assertFalse(LockScreenMonitorWidget.isServiceFresh(now - 6 * 60_000, now))
        assertFalse(LockScreenMonitorWidget.isServiceFresh(0L, now))
        val w = src("app/src/main/java/com/teamz/lab/debugger/widgets/LockScreenMonitorWidget.kt")
        for (k in listOf("download_speed", "upload_speed", "cpu", "fps_data", "power", "alert_message", "cta_message", "widget_action"))
            assertFalse("$k must go through the freshness gate", w.contains("prefs.getString(\"$k\""))
        assertTrue(src("app/src/main/java/com/teamz/lab/debugger/services/system_monitor_service.kt")
            .contains("LockScreenMonitorWidget.KEY_SERVICE_LAST_UPDATE"))
    }

    @Test
    fun `monitor service does not run a 12 MB speed test every 30 seconds`() {
        val t0 = 10_000_000L
        assertTrue(SpeedTestPolicy.shouldRun(t0, 0L, unmetered = true))
        assertFalse("never on metered data", SpeedTestPolicy.shouldRun(t0, 0L, unmetered = false))
        assertFalse(SpeedTestPolicy.shouldRun(t0 + 60_000, t0, unmetered = true))
        assertTrue(SpeedTestPolicy.shouldRun(t0 + SpeedTestPolicy.MIN_INTERVAL_MS, t0, unmetered = true))
        val svc = src("app/src/main/java/com/teamz/lab/debugger/services/system_monitor_service.kt")
        assertTrue(svc.contains("if (runSpeedTest) getNetworkDownloadSpeed() else reusedDownload"))
    }

    @Test
    fun `a cached speed belongs to the network it was measured on, and expires`() {
        val t0 = 50_000_000L
        assertEquals("200.00 Mbps", SpeedTestPolicy.reuse("200.00 Mbps", t0, "net:101", "net:101", t0 + 60_000))
        assertEquals("wifi -> mobile", SpeedTestPolicy.NOT_MEASURED, SpeedTestPolicy.reuse("200.00 Mbps", t0, "net:101", "net:102", t0 + 60_000))
        assertEquals(SpeedTestPolicy.NOT_MEASURED, SpeedTestPolicy.reuse("200.00 Mbps", t0, "net:101", null, t0 + 60_000))
        assertEquals("expired", SpeedTestPolicy.NOT_MEASURED, SpeedTestPolicy.reuse("200.00 Mbps", t0, "net:101", "net:101", t0 + SpeedTestPolicy.MIN_INTERVAL_MS))
        assertEquals(SpeedTestPolicy.NOT_MEASURED, SpeedTestPolicy.reuse("200.00 Mbps", t0, null, null, t0 + 60_000))
    }

    @Test
    fun `RAM percent parse does not echo the whole label`() {
        assertEquals("72", LockScreenMonitorWidget.ramPercentFrom("🧠 RAM: 5468 MB / 7572 MB (72%)"))
        assertEquals("--", LockScreenMonitorWidget.ramPercentFrom("🧠 RAM: --"))
    }

    @Test
    fun `storage string is used first, not available first`() {
        assertEquals("27/128 GB", WidgetSnapshot.storageUsedTotal("27 GB / 128 GB"))
        assertNull(WidgetSnapshot.storageUsedTotal("Unavailable"))
        val svc = src("app/src/main/java/com/teamz/lab/debugger/services/system_monitor_service.kt")
        assertFalse(svc.contains("val usedGB = totalGB - availableGB"))
    }

    @Test
    fun `widget placeholder does not tell a healthy phone to fix issues`() {
        assertFalse(src("app/src/main/res/layout/widget_lock_screen_monitor.xml").contains("Tap to fix issues"))
    }

    // ---- network speed --------------------------------------------------------------------

    @Test
    fun `speed is reported in megabits`() {
        // 10 MB in 1.6 s on a 50 Mbps line
        assertEquals(50.0, megabitsPerSecond(10_000_000L, 1.6), 0.01)
        assertEquals(0.0, megabitsPerSecond(10_000_000L, 0.0), 0.0)
        val n = src("app/src/main/java/com/teamz/lab/debugger/utils/network_utils.kt")
        assertFalse(n.contains("val speed = 10 / duration"))
        assertFalse(n.contains("val speed = dataSizeMB / duration"))
        assertFalse("upload must not use the slow single-region echo server", n.contains("URL(\"https://httpbin.org/post\")"))
        assertTrue(n.contains("connection.readTimeout"))
        assertTrue("download must be capped in time", n.contains("SPEED_TEST_MAX_NANOS"))
    }

    // ---- device info ----------------------------------------------------------------------

    @Test
    fun `face unlock never claims a photo can fool it`() {
        for (s in listOf(faceUnlockLine(true), faceUnlockLine(false))) {
            assertFalse(s, s.contains("fooled by a photo") && !s.contains("cannot fool"))
            assertFalse(s, s.contains("2D"))
        }
    }

    @Test
    fun `ultra-wide is found through zoom-out or a wider physical lens`() {
        assertTrue(hasUltraWideCamera(0.6f, listOf(4.7f)))              // Pixel-style logical camera
        assertTrue(hasUltraWideCamera(1.0f, listOf(6.9f, 2.2f)))         // separate wide lens
        assertFalse(hasUltraWideCamera(1.0f, listOf(4.7f)))              // single back camera
        assertFalse(hasUltraWideCamera(Float.MAX_VALUE, emptyList()))
    }

    @Test
    fun `AI readiness does not use an undefined system feature`() {
        val d = src("app/src/main/java/com/teamz/lab/debugger/utils/device_utils.kt")
        assertFalse(d.contains("hasSystemFeature(\"android.hardware.neuralnetworks\")"))
        assertTrue(aiReadinessLine(true, 8.0).startsWith("✅"))
        assertTrue(aiReadinessLine(false, 8.0).startsWith("ℹ️"))
    }

    @Test
    fun `hackability counts only what it measures`() {
        assertTrue(hackabilityLine(false, false).contains("No issues found"))
        assertTrue(hackabilityLine(false, true).contains("1 thing to check"))
        assertFalse(hackabilityLine(true, true).contains("/3"))
        val d = src("app/src/main/java/com/teamz/lab/debugger/utils/device_utils.kt")
        val fn = d.substring(d.indexOf("suspend fun getPhoneHackabilityScore"), d.indexOf("internal fun hackabilityLine"))
        assertFalse("must not word-match the whole security text", fn.contains("getSecurityInfo"))
    }

    @Test
    fun `one-line results are not sold as a full analysis`() {
        val ui = src("app/src/main/java/com/teamz/lab/debugger/ui/device_info_ui.kt")
        assertFalse(ui.contains("full face unlock security analysis"))
        assertFalse(ui.contains("full hackability report & security fixes"))
    }

    // ---- floating buttons ------------------------------------------------------------------

    @Test
    fun `floating buttons step aside while reading and return on scroll up`() {
        assertFalse("finger moving up = reading down", FabScrollPolicy.next(true, -40f))
        assertTrue(FabScrollPolicy.next(false, 40f))
        assertTrue("tap jitter keeps state", FabScrollPolicy.next(true, -3f))
        assertFalse(FabScrollPolicy.next(false, 3f))
        val nav = src("app/src/main/java/com/teamz/lab/debugger/ui/adaptive/DeviceGptNavExperience.kt")
        assertTrue(nav.contains("visible = fabsVisible"))
        assertEquals("all three layouts observe scrolling", 3, Regex("\\.nestedScroll\\(fabScroll\\)").findAll(nav).count())
        assertTrue("buttons come back on tab change", nav.contains("LaunchedEffect(selectedTab) { fabsVisible = true }"))
        val fab = nav.indexOf("AnimatedVisibility(\n                    visible = fabsVisible")
        assertTrue("price fetch must stay outside the animated block, or every scroll refetches it",
            nav.indexOf("RevenueCatManager.getLifetimeProductPrice") in 0 until fab)
    }
}
