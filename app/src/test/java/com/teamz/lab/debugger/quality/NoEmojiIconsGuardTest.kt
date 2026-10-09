package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.ui.icons.EmojiText
import com.teamz.lab.debugger.ui.icons.IconTone
import com.teamz.lab.debugger.ui.icons.RemoteIcons
import com.teamz.lab.debugger.ui.icons.displayText
import com.teamz.lab.debugger.ui.icons.hasDrawnEmoji
import com.teamz.lab.debugger.ui.icons.splitDisplayLine
import com.teamz.lab.debugger.ui.icons.splitDisplayLines
import com.teamz.lab.debugger.utils.InfoTextLocalizer
import com.teamz.lab.debugger.utils.LocaleManager
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * No emoji is drawn as an icon anywhere in the app.
 *
 * Emoji still live in the code, on purpose, as data: the builders in `utils` return English lines that start
 * with an emoji, and scores, the verified report, AI prompts, share text and many tests read those lines. So
 * this test does not ban emoji. It guards the three places where one could reach a screen:
 *
 * 1. a string literal handed straight to a Compose text parameter under `ui/`;
 * 2. a string resource that is only ever displayed;
 * 3. the display path itself: builder text, through the localizer, through the row splitter.
 *
 * An emoji is counted exactly as `EmojiIconsTest` counts it ([EmojiText.findAll]). Text arrows inside a
 * sentence ("94 → 97") are text and may stay; an arrow that ends a label is a "go" button and may not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NoEmojiIconsGuardTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
        LocaleManager.resetForTest()
        InfoTextLocalizer.resetForTest()
    }

    @After
    fun tearDown() {
        prefs().edit().clear().commit()
        LocaleManager.resetForTest()
        InfoTextLocalizer.resetForTest()
    }

    private fun prefs() = context.getSharedPreferences("locale_preferences", Context.MODE_PRIVATE)

    private fun locate(rel: String): File {
        val root = System.getProperty("user.dir") ?: "."
        return listOf(File(root, rel), File(root, "app/$rel"), File("../app/$rel"))
            .firstOrNull { it.exists() } ?: File(root, rel)
    }

    /** The emoji in [text] that would be drawn: every cluster except the text arrows. */
    private fun drawnEmoji(text: String): List<String> =
        EmojiText.findAll(EmojiText.decodeEscapes(text)).filterNot { EmojiText.isArrow(it.codePointAt(0)) }

    // ---- 1. Kotlin literals under ui/ ----

    /**
     * Parameters whose value is drawn as written. `icon` is here because several cards used to take their icon
     * as an emoji string.
     */
    private val textParameters = listOf(
        "text", "title", "label", "contentDescription", "icon", "subtitle", "description", "message", "placeholder",
    )

    @Test fun `no emoji in a string literal passed to a text parameter under ui`() {
        val ui = locate("src/main/java/com/teamz/lab/debugger/ui")
        assertTrue("ui/ not found from ${System.getProperty("user.dir")}", ui.isDirectory)
        // ui/icons holds the emoji table itself: its keys are emoji on purpose.
        val ownPackage = "ui${File.separator}icons${File.separator}"
        val literal = """"((?:[^"\\$]|\\.|\$(?!\{))*)""""
        val named = Regex("""\b(${textParameters.joinToString("|")})\s*=\s*$literal""")
        val positional = Regex("""(?<![A-Za-z0-9_.])(?:Dg)?Text\(\s*$literal""")
        val offenders = ArrayList<String>()
        var files = 0
        ui.walkTopDown().filter { it.isFile && it.extension == "kt" && ownPackage !in it.path }.forEach { file ->
            files++
            file.readLines().forEachIndexed { index, line ->
                val found = named.findAll(line).map { it.groupValues[2] } +
                    positional.findAll(line).map { it.groupValues[1] }
                found.filter { drawnEmoji(it).isNotEmpty() }.forEach {
                    offenders += "${file.name}:${index + 1}: \"$it\""
                }
            }
        }
        assertTrue("only $files files scanned", files > 30)
        assertTrue(
            "Emoji drawn as text. Use an Icon (DgIcons / DgStock), DgIconText or DgGlyph instead:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    // ---- 2. String resources ----

    /**
     * Keys that keep their emoji, because their text is data before it is display. Each is drawn only through
     * `DgText` / `DgDisplayLines` (Compose) or `RemoteIcons` (notifications, widget), which turn the leading
     * emoji into a vector icon and remove every other emoji. Test 3 below checks that for every one of them.
     */
    private val keptAsData: List<Pair<String, String>> = listOf(
        "info_l_" to "InfoTextLocalizer pattern: the English value is matched against builder output",
        "info_t_" to "InfoTextLocalizer pattern: the English value is matched against builder output",
        "mx_battery_" to "HealthDisplayText: the Bangla form of builder rows that keep their emoji in English",
        "mx_trend_" to "HealthDisplayText: the Bangla form of the builder's trend words",
        "mx_tip_" to "health tips built in health_score_utils as a list; the emoji picks each tip's icon",
        "mx_monitor_" to "always-on notification rows; MonitorDisplayText matches the English originals",
        "mx_notif_" to "notification titles: the duplicate key and the icon selector; stripped when built",
        "mx_widget_alert_" to "stored in prefs for the widget; the emoji picks the alert row's icon",
        "mx_widget_status_" to "widget status words; the emoji picks the status cell's icon",
        "pw_agg_" to "power recommendations built in power_consumption_aggregator as a list",
        "pw_alert_" to "power alerts built in power_alerts",
        "pw_cam_res_" to "camera test result block built in power_consumption_utils",
        "pw_compact_summary" to "one-line power summary built in power_consumption_utils",
        "pw_edu_" to "education text built in power_education; lines are split at display",
        "pw_info_" to "lines of the component info block; each line gets its icon at display",
        "pw_power_range" to "a line of a result block; gets its icon at display",
        "pw_disp_result_note" to "a line of a result block; gets its icon at display",
        "pw_cpu_impact" to "a line of a result block; gets its icon at display",
        "pw_net_strength" to "a line of a result block; gets its icon at display",
        "pw_share_" to "share text sent to other apps: not this app's UI",
        "lb_vs_msg_" to "share text sent to other apps: not this app's UI",
        "lb_vs_power_" to "share text sent to other apps: not this app's UI",
    )

    private fun isKeptAsData(key: String): Boolean = keptAsData.any { (prefix, _) -> key.startsWith(prefix) }

    private data class Res(val folder: String, val key: String, val value: String)

    private fun resources(): List<Res> {
        val res = locate("src/main/res")
        assertTrue("res/ not found", res.isDirectory)
        val pattern = Regex("<string\\s+name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        val out = ArrayList<Res>()
        res.listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!.sortedBy { it.name }.forEach { folder ->
            folder.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }!!.forEach { file ->
                pattern.findAll(file.readText()).forEach {
                    out += Res(folder.name, it.groupValues[1], it.groupValues[2])
                }
            }
        }
        assertTrue("only ${out.size} strings read", out.size > 2000)
        return out
    }

    @Test fun `no emoji in a display-only string resource in any language`() {
        val offenders = resources()
            .filter { !isKeptAsData(it.key) && drawnEmoji(it.value).isNotEmpty() }
            .map { "${it.folder}/${it.key}: ${it.value.take(60)}" }
        assertTrue(
            "Emoji in a string that is only displayed. Remove it and give the composable an Icon; or, if code " +
                "reads this text as data, add the key to keptAsData with the reason:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test fun `no label ends with a typed go arrow`() {
        val offenders = resources()
            .filter { !isKeptAsData(it.key) && it.value.trimEnd().endsWith("→") }
            .map { "${it.folder}/${it.key}: ${it.value.take(60)}" }
        assertTrue(
            "A trailing arrow is a button's icon, not text:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test fun `the allow-list holds no dead entry`() {
        val keys = resources().filter { it.folder == "values" }
        keptAsData.forEach { (prefix, _) ->
            assertTrue(
                "keptAsData entry \"$prefix\" matches no string with an emoji: remove it",
                keys.any { it.key.startsWith(prefix) && drawnEmoji(it.value).isNotEmpty() },
            )
        }
    }

    // ---- 3. The display path ----

    /** Android's own unescaping of a resource value, near enough for this check. */
    private fun unescape(value: String): String =
        EmojiText.decodeEscapes(value).replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace(Regex("%(\\d+\\$)?[sd]"), "7").replace("%%", "%")

    @Test fun `every string kept as data comes out of the row splitter with no emoji`() {
        var checked = 0
        val kept = resources().filter { isKeptAsData(it.key) && (it.folder == "values" || it.folder == "values-bn") }
        kept.forEach { res ->
            splitDisplayLines(unescape(res.value)).forEach { line ->
                checked++
                assertTrue(
                    "${res.folder}/${res.key} still shows an emoji after the split: \"${line.text}\"",
                    drawnEmoji(line.text).isEmpty(),
                )
            }
            val remote = RemoteIcons.plain(unescape(res.value))
            assertTrue(
                "${res.folder}/${res.key} still shows an emoji in a notification: \"$remote\"",
                !hasDrawnEmoji(remote),
            )
        }
        assertTrue("only $checked lines checked", checked > 500)
    }

    /** Real lines from `device_utils`, `network_utils`, the scorers and the monitor service. */
    private val builderLines = """
        🔋 Battery Status: Charging at 5.20 W
        🔌 Charge Type: USB ⚡
        🔥 Battery Temp: 31.5°C
        📊 Battery Health: Good ✅
        🔬 Battery Tech: Li-ion
        ⏳ Estimated Full Charge: 12 min left
        🔋 Estimated Cycles (Approximation): ~92 cycles
        ❌ Security shield is off
        ❌ Storage is not protected
        ⚠️ Clipboard can be read
        ⚠️ Modified system files found
        ✅ GPS Enabled: Yes
        📶 Internet: ✅ Connected • Latency: 42ms
        📶 Internet: ⚠️ No connection
        Charging at 5.2W • ⏳ 12 min left • 🔥 31.0°C
        ⚡Saver On
        😴Dozing
        • ✅ Screen lock is set
        🚀 Ready to start your health journey?
    """.trimIndent()

    @Test fun `builder lines go through the localizer and the row splitter with no emoji left`() {
        for (language in listOf(LocaleManager.AppLanguage.ENGLISH, LocaleManager.AppLanguage.BENGALI)) {
            LocaleManager.setLanguage(context, language)
            InfoTextLocalizer.resetForTest()
            val shown = InfoTextLocalizer.localize(context, builderLines)
            val lines = splitDisplayLines(shown)
            assertEquals(builderLines.lines().size, lines.size)
            lines.forEach { line ->
                assertTrue("$language: \"${line.text}\" still shows an emoji", drawnEmoji(line.text).isEmpty())
                assertTrue("$language: a line lost its words", line.text.isNotBlank())
            }
            assertTrue("$language: plain text still shows an emoji", !hasDrawnEmoji(displayText(shown)))
        }
    }

    @Test fun `a status mark anywhere in the line becomes the icon and the words stay`() {
        val health = splitDisplayLine("📊 Battery Health: Good ✅")
        assertEquals("Battery Health: Good", health.text)
        assertEquals(IconTone.Good, health.icon!!.tone)

        val offline = splitDisplayLine("📶 Internet: ⚠️ No connection")
        assertEquals("Internet: No connection", offline.text)
        assertEquals(IconTone.Warn, offline.icon!!.tone)

        val bullet = splitDisplayLine("• ❌ Security shield is off")
        assertEquals("Security shield is off", bullet.text)
        assertEquals(IconTone.Bad, bullet.icon!!.tone)
    }

    @Test fun `the flame is a thermometer for heat and the streak flame for streaks`() {
        val heat = splitDisplayLine("🔥 Battery Temp: 31.5°C").icon
        val streak = splitDisplayLine("🔥 INCREDIBLE 7-Day Streak!").icon
        assertNotNull(heat)
        assertNotNull(streak)
        assertTrue("heat and streak must not share an icon", heat!!.icon !== streak!!.icon)
        assertEquals(R.drawable.ic_dg_temperature, RemoteIcons.forText("🔥 Critical: Phone Overheating!", 0))
        assertEquals(R.drawable.ic_dg_streak, RemoteIcons.forText("🔥 INCREDIBLE 7-Day Streak!", 0))
    }

    @Test fun `text with no emoji is left exactly as it is`() {
        val plain = "  Model: Pixel 8a (94 → 97)"
        assertNull(splitDisplayLine(plain).icon)
        assertEquals(plain, splitDisplayLine(plain).text)
        assertEquals(plain, displayText(plain))
    }

    @Test fun `notification and widget text loses its emoji and its go arrow, and keeps arrows in sentences`() {
        assertEquals("New app on this device", RemoteIcons.plain("📦 New app on this device"))
        assertEquals("Open health check", RemoteIcons.plain("Open health check →"))
        val sentence = "Charge complete: 62% → 100% in 40m."
        assertEquals(sentence, RemoteIcons.plain(sentence))
        assertEquals(R.drawable.ic_dg_check, RemoteIcons.forText("✅ All normal — health 9/10", 0))
        assertEquals(R.drawable.ic_dg_warning, RemoteIcons.forText("Health 7/10", R.drawable.ic_dg_warning))
    }
}
