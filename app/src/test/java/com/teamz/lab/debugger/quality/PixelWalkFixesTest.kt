package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.HealthScoreUtils
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
        val p = context.getSharedPreferences(WidgetSnapshot.PREFS, Context.MODE_PRIVATE)
        assertEquals(9, p.getInt(WidgetSnapshot.KEY_HEALTH_SCORE, -1))
        assertTrue(p.getLong("last_update", 0) > 0)
        assertTrue(p.getString("ram", "")!!.contains("%)"))
    }

    @Test
    fun `refresh with no new score reuses the last saved one`() {
        HealthScoreUtils.saveHealthScore(context, 8)
        context.getSharedPreferences(WidgetSnapshot.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        WidgetSnapshot.write(context)
        assertEquals(8, context.getSharedPreferences(WidgetSnapshot.PREFS, Context.MODE_PRIVATE).getInt("health_score", -1))
    }

    @Test
    fun `no score yet renders as unknown, not zero`() {
        assertEquals("Health: --/10", LockScreenMonitorWidget.healthScoreLabel(false, 0, ""))
        assertEquals("Health: 9/10 ↑", LockScreenMonitorWidget.healthScoreLabel(true, 9, " ↑"))
        val w = src("app/src/main/java/com/teamz/lab/debugger/widgets/LockScreenMonitorWidget.kt")
        val lowScore = w.indexOf("healthScore < 5 -> \"⚠️ Low Score\"")
        val guard = w.lastIndexOf("!hasScore ->", lowScore)
        assertTrue("unknown score must be handled before the Low Score branch", guard in 0 until lowScore)
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
}
