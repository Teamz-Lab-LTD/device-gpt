package com.teamz.lab.debugger.quality

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `InfoTextLocalizer` shows the English rows of the phone-info and network-info tabs in Bangla
 * by matching them against the English values of the `info_l_*` and `info_t_*` keys. Two things
 * can quietly turn a Bangla row back into English: a key that is not listed in
 * `arrays_info.xml`, and an English line reworded in the Kotlin without its key. Both are
 * checked here by reading the files, with no Android runtime.
 */
class InfoTextPatternsGuardTest {

    private fun locate(rel: String): File {
        val root = System.getProperty("user.dir") ?: "."
        return listOf(File(root, rel), File(root, "app/$rel"), File("../app/$rel"))
            .firstOrNull { it.exists() } ?: File(root, rel)
    }

    private fun read(rel: String): String = locate(rel).also { assertTrue("$rel missing", it.exists()) }.readText()

    private val strings: Map<String, String> by lazy {
        Regex("<string\\s+name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(read("src/main/res/values/strings_info.xml"))
            .associate { it.groupValues[1] to unescape(it.groupValues[2]) }
    }

    /** What aapt turns the XML text into. */
    private fun unescape(xml: String): String =
        xml.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\")

    private fun listed(arrayName: String): Set<String> {
        val arrays = read("src/main/res/values/arrays_info.xml")
        val body = Regex("<array\\s+name=\"$arrayName\">(.*?)</array>", RegexOption.DOT_MATCHES_ALL)
            .find(arrays)?.groupValues?.get(1) ?: ""
        return Regex("@string/([A-Za-z0-9_]+)").findAll(body).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun `every label and line pattern is listed for the localizer, and nothing else is`() {
        val labels = strings.keys.filter { it.startsWith("info_l_") }.toSet()
        val lines = strings.keys.filter { it.startsWith("info_t_") }.toSet()
        assertTrue("no label patterns found — file or regex drifted", labels.size > 100)
        assertTrue("no line patterns found — file or regex drifted", lines.size > 200)

        val listedLabels = listed("info_label_keys")
        val listedLines = listed("info_template_keys")
        assertTrue("not in info_label_keys: ${labels - listedLabels}", (labels - listedLabels).isEmpty())
        assertTrue("not in info_template_keys: ${lines - listedLines}", (lines - listedLines).isEmpty())
        assertTrue("info_label_keys lists non-label keys: ${listedLabels - labels}", (listedLabels - labels).isEmpty())
        assertTrue("info_template_keys lists non-line keys: ${listedLines - lines}", (listedLines - lines).isEmpty())
    }

    @Test
    fun `two patterns never share the same English text`() {
        for (prefix in listOf("info_l_", "info_t_")) {
            val twice = strings.filterKeys { it.startsWith(prefix) }.entries
                .groupBy({ it.value }, { it.key }).filterValues { it.size > 1 }
            assertTrue("same English under two $prefix keys: $twice", twice.isEmpty())
        }
    }

    @Test
    fun `every pattern still matches text the Kotlin produces`() {
        val files = listOf(
            "utils/device_utils.kt", "utils/network_utils.kt", "utils/NetworkPrivacyScorer.kt",
            "utils/ZeroTrustScorer.kt", "utils/NetworkReachabilityTester.kt", "utils/PrivacyExposureModel.kt",
            "utils/PrivacyExposureScanner.kt", "utils/WebViewStackProbe.kt", "ui/device_info_ui.kt",
            "ui/network_ui.kt", "ui/DeviceInfoViewModel.kt",
        )
        val source = files.joinToString("\n") { read("src/main/java/com/teamz/lab/debugger/$it") }
            .replace(Regex("\"\\s*\\+\\s*\""), "")          // "a" + "b", also across lines
            .replace("\\\"", "\"").replace("\\$", "$")
            .replace(Regex("\\\\u([0-9A-Fa-f]{4})")) { it.groupValues[1].toInt(16).toChar().toString() }

        // Built at run time from two pieces ("Charging at " + "Not Available"), so not in the source as one.
        val joinedAtRunTime = setOf("info_t_bat_charging_unknown", "info_t_bat_discharging_unknown")

        val gone = mutableListOf<String>()
        for ((key, english) in strings) {
            if (!(key.startsWith("info_l_") || key.startsWith("info_t_")) || key in joinedAtRunTime) continue
            // The longest stretch of fixed text between placeholders is enough to pin the line.
            val fixed = english.split(Regex("%\\d+\\\$s")).map { it.replace("%%", "%").trim() }.maxByOrNull { it.length }
            if (!fixed.isNullOrEmpty() && !source.contains(fixed)) gone += "$key: \"$fixed\""
        }
        assertTrue(
            "English text reworded in Kotlin without its info_* key (Bangla users now see English):\n" +
                gone.joinToString("\n"),
            gone.isEmpty(),
        )
    }
}
