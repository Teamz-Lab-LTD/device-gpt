package com.teamz.lab.debugger.quality

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.db.DeviceEvent
import com.teamz.lab.debugger.ui.timelineLabel
import com.teamz.lab.debugger.utils.HealthDisplayText
import com.teamz.lab.debugger.utils.MonitorDisplayText
import com.teamz.lab.debugger.widgets.LockScreenMonitorWidget
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Some text is compared, stored or sent to an AI in English, and is put into the app language
 * only where it is shown ([HealthDisplayText], [MonitorDisplayText], [timelineLabel]). These
 * tests pin the three promises that makes:
 *  - an English user sees exactly the text they saw before;
 *  - a Bangla user sees no English sentence for the lines these mappers know;
 *  - a line the mapper does not know comes back unchanged, never empty and never a crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DisplayTextLocalizationTest {

    private val app: Context = ApplicationProvider.getApplicationContext()
    private val english: Context = localized("en")
    private val bangla: Context = localized("bn")

    private fun localized(language: String): Context =
        app.createConfigurationContext(Configuration().apply { setLocale(Locale(language)) })

    private val latinWord = Regex("[A-Za-z]{3,}")

    private val batteryInfo = """
        🔋 Battery Status: Battery Full 🎉
        🔌 Charge Type: Fast Charging 🔋
        🔥 Battery Temp: 31.5°C
        📊 Battery Health: Good ✅
        🔬 Battery Tech: Unknown
        ⏳ Estimated Full Charge: N/A
        🔋 Estimated Cycles (Approximation): ~120 cycles
        📅 Predicted Battery Life Remaining: ~1+ year (Estimated based on capacity)
    """.trimIndent()

    @Test
    fun `English text is returned exactly as it came in`() {
        assertEquals(batteryInfo, HealthDisplayText.batteryInfo(english, batteryInfo))
        assertEquals(
            "USB debugging enabled (security risk)",
            HealthDisplayText.privacyThreat(english, "USB debugging enabled (security risk)"),
        )
        val memory = "Current memory: 1790 MB used of 3920 MB (45%). Android manages RAM automatically."
        assertEquals(memory, HealthDisplayText.actionResult(english, memory))
        assertEquals("📈 Increasing", HealthDisplayText.temperatureTrend(english, "📈 Increasing"))
        assertEquals("Camera Health Test", HealthDisplayText.aiItemTitle(english, "Camera Health Test"))
        assertEquals("Error: boom", HealthDisplayText.errorLine(english, "boom"))
        assertEquals("FPS: 59 • Drop Rate: 1.0%", MonitorDisplayText.fps(english, "FPS: 59 • Drop Rate: 1.0%"))
        assertEquals(
            "Charging at 5.2W • ⏳ 12 min left • 🔥 31.0°C",
            MonitorDisplayText.battery(english, "Charging at 5.2W • ⏳ 12 min left • 🔥 31.0°C"),
        )
    }

    @Test
    fun `the battery card has no English left in Bangla and keeps its numbers`() {
        val shown = HealthDisplayText.batteryInfo(bangla, batteryInfo)
        assertEquals("one row in, one row out", batteryInfo.lines().size, shown.lines().size)
        assertFalse("English left in: $shown", latinWord.containsMatchIn(shown))
        assertTrue(shown.contains("31.5°C"))
        assertTrue(shown.contains("120"))
    }

    @Test
    fun `code that matches on English still sees English`() {
        // health_score_utils and device_utils match these words in the raw text. The display
        // mapper must never be the thing they read.
        assertTrue(batteryInfo.contains("Battery Full"))
        assertFalse(HealthDisplayText.batteryInfo(bangla, batteryInfo).contains("Battery Full"))
    }

    @Test
    fun `privacy risks, result lines and trends are Bangla for a Bangla user`() {
        listOf(
            "An installed app can capture or read your screen",
            "USB debugging enabled (security risk)",
            "Device is rooted (security risk)",
            "SSL certificate issues detected",
            "A GPS spoofing app is installed",
        ).forEach { risk ->
            val shown = HealthDisplayText.privacyThreat(bangla, risk)
            assertFalse("English left in: $shown", latinWord.containsMatchIn(shown))
        }
        val results = listOf(
            "Open storage settings to clear app caches manually.",
            "No battery tips right now. Android manages battery automatically.",
            "Found 2 tips. Opening battery settings…",
            "Found 1 tip. Opening battery settings…",
            "Unable to read battery info: boom. Opening settings…",
        )
        results.forEach { line ->
            val shown = HealthDisplayText.actionResult(bangla, line)
            assertFalse("English left in: $shown", latinWord.containsMatchIn(shown))
        }
        val memory = HealthDisplayText.actionResult(
            bangla, "Memory pressure detected: 3800 MB used of 3920 MB (96%). Android will auto-close background apps as needed.",
        )
        assertTrue("numbers must survive: $memory", memory.contains("3800 MB") && memory.contains("3920 MB") && memory.contains("96%"))
        val freed = HealthDisplayText.actionResult(
            bangla, "Freed 12 MB storage. Cleared cache from 3 apps. Open storage settings to clear more.",
        )
        assertTrue("numbers must survive: $freed", freed.contains("12 MB") && freed.contains("3"))
        assertEquals(bangla.getString(R.string.mx_trend_stable), HealthDisplayText.temperatureTrend(bangla, "📊 Stable"))
        assertFalse(latinWord.containsMatchIn(HealthDisplayText.errorLine(bangla, "java.io.IOException: boom")))
    }

    @Test
    fun `text the mapper does not know passes through unchanged`() {
        assertEquals("ব্যাটারি ভালো আছে", HealthDisplayText.batteryInfo(bangla, "ব্যাটারি ভালো আছে"))
        assertEquals("Some new risk", HealthDisplayText.privacyThreat(bangla, "Some new risk"))
        assertEquals("Some new result", HealthDisplayText.actionResult(bangla, "Some new result"))
        assertEquals("Not enough data", HealthDisplayText.temperatureTrend(bangla, "Not enough data"))
        assertEquals("Wi-Fi Details", HealthDisplayText.aiItemTitle(bangla, "Wi-Fi Details"))
        assertEquals("⚙️ ", MonitorDisplayText.cpu(bangla, "").take(3))
    }

    @Test
    fun `the AI dialog title is Bangla while the identifier that names the file is untouched`() {
        val identifier = "Camera Health Test"
        val shown = HealthDisplayText.aiItemTitle(bangla, identifier)
        assertFalse("English left in: $shown", latinWord.containsMatchIn(shown))
        // The nav shell builds the file name from the identifier, exactly as before.
        val cleanTitle = identifier.replace(Regex("[^a-zA-Z0-9\\s]"), "").replace(" ", "_").lowercase()
        assertEquals("camera_health_test", cleanTitle)
    }

    @Test
    fun `timeline rows are rebuilt from the event, not read from the stored English label`() {
        val scan = DeviceEvent(type = DeviceEvent.TYPE_SCORE_SCAN, timestamp = 1L, score = 82, label = "Device Score 82")
        val snapshot = DeviceEvent(
            type = DeviceEvent.TYPE_BASELINE_SNAPSHOT, timestamp = 1L, score = 90, label = "Daily snapshot 9/10",
        )
        val charge = DeviceEvent(
            type = DeviceEvent.TYPE_CHARGE_SESSION,
            timestamp = 1L,
            label = "Charged 62% → 100% in 1h 40m",
            payload = """{"start":62,"end":100,"duration_ms":6000000}""",
        )
        val install = DeviceEvent(
            type = DeviceEvent.TYPE_APP_INSTALLED, timestamp = 1L, label = "New app installed: bKash", payload = "com.bkash",
        )

        // English: word for word what the writers stored.
        assertEquals("Device Score 82", timelineLabel(english, scan))
        assertEquals("Daily snapshot 9/10", timelineLabel(english, snapshot))
        assertEquals("Charged 62% → 100% in 1h 40m", timelineLabel(english, charge))
        assertEquals("New app installed: bKash", timelineLabel(english, install))

        // Bangla: no English sentence, numbers and the app's own name kept.
        assertTrue(timelineLabel(bangla, scan).contains("82"))
        assertFalse(latinWord.containsMatchIn(timelineLabel(bangla, scan)))
        assertTrue(timelineLabel(bangla, snapshot).contains("9/10"))
        assertFalse(latinWord.containsMatchIn(timelineLabel(bangla, charge)))
        assertTrue(timelineLabel(bangla, charge).contains("62%") && timelineLabel(bangla, charge).contains("100%"))
        assertTrue(timelineLabel(bangla, install).endsWith("bKash"))

        // An old row with no payload still shows something.
        val oldCharge = charge.copy(payload = null)
        assertEquals("Charged 62% → 100% in 1h 40m", timelineLabel(bangla, oldCharge))
    }

    @Test
    fun `widget lines follow the context they are given and keep Latin digits`() {
        assertEquals("Health: 9/10 ↑", LockScreenMonitorWidget.healthScoreLabel(english, true, 9, " ↑"))
        val shown = LockScreenMonitorWidget.healthScoreLabel(bangla, true, 9, " ↑")
        assertTrue("digits must stay Latin: $shown", shown.contains("9/10"))
        assertFalse("English left in: $shown", latinWord.containsMatchIn(shown))
        assertFalse(
            latinWord.containsMatchIn(LockScreenMonitorWidget.primaryStatus(bangla, true, "30.0", "40", 9)),
        )
        // "AC" is stored data; the word shown for it is not.
        assertFalse(latinWord.containsMatchIn(LockScreenMonitorWidget.chargerName(bangla, "Wireless")))
        assertEquals("AC", LockScreenMonitorWidget.chargerName(english, "AC"))
    }
}
