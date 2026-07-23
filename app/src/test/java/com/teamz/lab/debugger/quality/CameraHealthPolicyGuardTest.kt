package com.teamz.lab.debugger.quality

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the 2026-07-24 deep-research verdict (workflow wf_0f296bf8-afa, 13 confirmed / 12
 * refuted claims): every pixel-based camera-quality judgement (sharpness, colour accuracy,
 * sensor health) measures the vendor's ISP pipeline, not the sensor, and cannot be honestly
 * substantiated on Android — the same "impossible functionality" shape that got this app's
 * per-app battery attribution rejected under Play's Deceptive Behavior policy (commit
 * `a44b84b`). Policy liability also attaches independently to the store listing (title, icon,
 * screenshots, description) — this file scans BOTH the code and the listing.
 *
 * These are source-text guards: no unit test can exercise real Camera2 hardware, so the
 * defense here is that a banned phrase can never enter the shipped strings in the first place.
 */
class CameraHealthPolicyGuardTest {

    private val bannedPatterns = listOf(
        Regex("colou?r accuracy", RegexOption.IGNORE_CASE),
        Regex("sharpness (score|rating|grade)", RegexOption.IGNORE_CASE),
        Regex("sensor health", RegexOption.IGNORE_CASE),
        Regex("lens quality", RegexOption.IGNORE_CASE),
        Regex("camera (health|quality) score", RegexOption.IGNORE_CASE),
        Regex("detects? lens damage", RegexOption.IGNORE_CASE),
        Regex("hot.?pixel", RegexOption.IGNORE_CASE),      // dropped entirely per research — must not reappear
        Regex("stuck.?pixel", RegexOption.IGNORE_CASE),    // screen test only; never for the camera sensor
        Regex("dark.?frame", RegexOption.IGNORE_CASE),
        Regex("\\bhealth\\s*[:=]?\\s*\\d{1,3}\\s*%", RegexOption.IGNORE_CASE),
    )

    /**
     * Strips comments before scanning. Our own explanatory prose about WHY a phrase is banned
     * (this file's KDoc, camera_health_utils.kt's header) necessarily contains the banned words —
     * only user-visible strings and code matter here. Same technique as
     * [com.teamz.lab.debugger.ai.OnDeviceAiMainThreadGuardTest].
     */
    private fun readFileStrippingComments(path: String): String {
        val f = File(path)
        assertTrue("missing file: ${f.path}", f.exists())
        return f.readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .joinToString("\n")
    }

    private fun readFile(path: String): String = readFileStrippingComments(path)

    private fun assertNoneOf(text: String, label: String) {
        for (pattern in bannedPatterns) {
            val match = pattern.find(text)
            assertTrue(
                "$label contains a banned policy phrase (\"${match?.value}\") — see " +
                    "camera_health_utils.kt header for why this class of claim is banned",
                match == null,
            )
        }
    }

    @Test
    fun `camera_health_utils source contains no banned quality-score language`() {
        assertNoneOf(
            readFile("src/main/java/com/teamz/lab/debugger/utils/camera_health_utils.kt"),
            "camera_health_utils.kt",
        )
    }

    @Test
    fun `camera_health_card source contains no banned quality-score language`() {
        assertNoneOf(
            readFile("src/main/java/com/teamz/lab/debugger/ui/camera_health_card.kt"),
            "camera_health_card.kt",
        )
    }

    @Test
    fun `the camera_health AI prompt block contains no banned quality-score language`() {
        val full = readFile("src/main/java/com/teamz/lab/debugger/utils/ai_prompt_generator.kt")
        // Anchor on the FUNCTION DEFINITION, not the dispatch call site that appears earlier in
        // the file (`"camera_health" -> generateSimpleCameraHealthPrompt(...)`). Indexing from
        // the call site instead of the definition once swallowed ~2,500 unrelated lines,
        // including the pre-existing display prompt's "colour accuracy" line — this comment
        // documents that failure so the anchor is never loosened back to a plain indexOf().
        val start = full.indexOf("private fun generateSimpleCameraHealthPrompt(")
        assertTrue("could not locate the camera_health Simple function definition", start >= 0)
        val end = full.indexOf("private fun generateAdvancedCameraHealthPrompt(", start)
        assertTrue("could not locate the camera_health Advanced function definition", end > start)
        val nextFn = full.indexOf("private fun generateSimpleCameraPowerTestPrompt(", end)
        assertTrue("could not locate the end boundary after the Advanced function", nextFn > end)
        assertNoneOf(full.substring(start, nextFn), "ai_prompt_generator.kt camera_health functions")
    }

    @Test
    fun `every locale strings xml is free of banned camera-quality claims`() {
        val resDir = File("src/main/res")
        assertTrue("res dir missing", resDir.exists())
        val stringsFiles = resDir.listFiles { d -> d.isDirectory && d.name.startsWith("values") }
            .orEmpty()
            .mapNotNull { dir -> File(dir, "strings.xml").takeIf { it.exists() } }
        assertTrue("expected to find at least one strings.xml", stringsFiles.isNotEmpty())
        for (f in stringsFiles) {
            assertNoneOf(f.readText(), f.path)
        }
    }

    @Test
    fun `Camera Health Test routes to its own AI category, not the privacy log branch`() {
        // detectItemCategory must resolve "Camera Health Test" to "camera_health" BEFORE the
        // generic "camera" case (which is a privacy/permission-log prompt, wrong content for
        // hardware results). Checked as source text because detectItemCategory is a private
        // function inside a large object; this proves the routing exists at all, in the right
        // relative order.
        val src = readFile("src/main/java/com/teamz/lab/debugger/utils/ai_prompt_generator.kt")
        val healthCheckIdx = src.indexOf("\"Camera Health\", ignoreCase = true) -> \"camera_health\"")
        val genericCameraIdx = src.indexOf(
            "itemTitle.contains(\"Camera\", ignoreCase = true) || itemTitle.contains(\"Mic\"",
        )
        assertTrue("camera_health detection branch not found", healthCheckIdx >= 0)
        assertTrue("generic camera branch not found (test is stale)", genericCameraIdx >= 0)
        assertTrue(
            "camera_health must be detected BEFORE the generic camera branch in the when{} — " +
                "Kotlin when{} takes the first match",
            healthCheckIdx < genericCameraIdx,
        )
    }
}
