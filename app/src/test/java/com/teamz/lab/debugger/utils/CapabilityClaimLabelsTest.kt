package com.teamz.lab.debugger.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * 2026-10-04: three section names claimed detections the app cannot perform, after the results
 * under them had been made honest.
 *   - "Device Spyware & Tracking Test", with a premium teaser selling "full spyware scan results"
 *   - "Deep Packet Inspection (ISP/Government Scanning Traffic)" / "Deep Data Scanning" over a
 *     single `ping -c 1 8.8.8.8`
 *   - Network Trust row "Deep Packet Inspection" ("No deep packet inspection signatures detected")
 * The same claim reached the AI prompts and the AppFunctions description that on-device agents read.
 */
class CapabilityClaimLabelsTest {

    private fun src(rel: String): String {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return File(dir, "app/src/main/java/com/teamz/lab/debugger/$rel").readText()
    }

    @Test
    fun `no surface names a spyware scan or deep packet inspection`() {
        val files = listOf(
            "ui/device_info_ui.kt", "utils/device_utils.kt", "utils/network_utils.kt",
            "utils/NetworkPrivacyScorer.kt", "utils/ai_prompt_generator.kt",
            "appfunctions/NetworkAppFunctions.kt",
        )
        val banned = listOf(
            "Spyware & Tracking Test", "spyware scan", "Deep Packet Inspection",
            "deep packet inspection signatures", "Deep Data Scanning", "Unable to Check DPI",
            "DPI hints", "DPI detection", "DPI methodology",
            "Screen Recording Apps Detected", "(Spying Apps)", "Spyware Running",
            "Eavesdropping Risk", "voice clone risk", "Safe from voice cloning",
            "Keylogger Detection", "SSL Hijack by ISP", "Normal Battery Consumption",
            "AI Voice Clone Risk Check", "voice clone vulnerability",
        )
        for (f in files) {
            // Code comments explaining the removal may name the old label. Everything else —
            // string literals, prompt templates — may not. AppFunctions KDoc is read by on-device
            // agents (isDescribedByKDoc = true), so there comments count too.
            val visible = src(f).lines().filter { line ->
                val t = line.trim()
                f.startsWith("appfunctions") || !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
            }.joinToString("\n")
            for (b in banned) assertFalse("$f still shows \"$b\"", visible.contains(b))
        }
    }

    @Test
    fun `internet threat level counts findings, not checks that could not run`() {
        assertEquals(0, countNetworkFindings(listOf("✅ ok", "❌ Could not check", "❌ Could not check")))
        assertEquals(1, countNetworkFindings(listOf("⚠️ Suspicious SSL Certificate Detected!", "❌ Could not check")))
    }
}
