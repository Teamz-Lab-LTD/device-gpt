package com.teamz.lab.debugger.quality

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the 2026-07-25 "plan cleanup" pass: three items the original camera-test
 * plan flagged as "worth fixing regardless of whether the camera feature ships" — a fabricated
 * AI-prompt summary, a hand-rolled AI-share path that bypassed the app's one hardened share
 * helper, and a keyless `remember{}` — plus a guard against the same class of "AI-share bypass"
 * bug reappearing anywhere else in the app.
 */
class DeadCodeAndPromptFixesTest {

    // ─────────────────────── fabricated device-prompt summary ───────────────────────

    // generateMainPrompt(tabIndex, ...) dispatches through TabOrderManager, which touches
    // FirebaseRemoteConfig — not runnable in a plain JVM unit test without a real Android
    // runtime (Process.myPid unmocked). Source-text guard instead, same technique as the
    // camera-capture tests: proves the fabricated phrases are gone and cannot silently return.
    @Test fun `Simple-mode device prompt no longer asserts invented battery, storage or security values`() {
        // Pre-fix regression this guards: generateDevicePrompt's Simple-mode branch hardcoded
        // specific claims ("Battery: Health is decent, but phone feels a bit warm", "Storage:
        // Almost full", "Motion: Phone moved while locked", "Security: No major risks found")
        // that were NOT derived from any real scan value — the AI would repeat these as if they
        // were this user's actual device state.
        val src = readAiPromptGeneratorSource()
        val fnStart = src.indexOf("private fun generateDevicePrompt(")
        assertTrue("generateDevicePrompt not found — test is stale", fnStart >= 0)
        val fnEnd = src.indexOf("\n    /**", fnStart + 1).let { if (it > fnStart) it else src.length }
        val fnBody = src.substring(fnStart, fnEnd)

        assertFalse(
            "prompt still asserts a fabricated 'Health is decent' battery claim",
            fnBody.contains("Health is decent"),
        )
        assertFalse(
            "prompt still asserts a fabricated 'Almost full' storage claim",
            fnBody.contains("Almost full"),
        )
        assertFalse(
            "prompt still asserts a fabricated 'moved while locked' motion claim",
            fnBody.contains("Phone moved while locked"),
        )
        assertFalse(
            "prompt still asserts a fabricated 'No major risks found' security claim",
            fnBody.contains("No major risks found"),
        )
        assertTrue(
            "Simple-mode device prompt must point the AI at the real numbers rather than " +
                "asserting a guessed summary",
            fnBody.contains("real numbers", ignoreCase = true),
        )
    }

    private fun readAiPromptGeneratorSource(): String =
        File("src/main/java/com/teamz/lab/debugger/utils/ai_prompt_generator.kt").readText()

    // ─────────────────────── dead hand-rolled AI-share path removed ───────────────────────

    private fun readPowerConsumptionCardSource(): String =
        File("src/main/java/com/teamz/lab/debugger/ui/power_consumption_card.kt").readText()

    @Test fun `the dead shareWithAIApp hand-rolled share function is gone`() {
        // Pre-fix regression this guards: shareWithAIApp hand-rolled ACTION_SEND with
        // EXTRA_STREAM only (no EXTRA_TEXT, no clipboard fallback), used cacheDir instead of
        // filesDir, and bypassed shareWithAIAppRobust entirely — with zero live callers.
        val src = readPowerConsumptionCardSource()
        assertFalse(
            "shareWithAIApp must not be reintroduced — it bypassed shareWithAIAppRobust and " +
                "had zero callers when removed",
            src.contains("private fun shareWithAIApp("),
        )
        assertFalse(
            "generateAppOptimizationPrompt must not be reintroduced — its only caller was the " +
                "removed shareWithAIApp",
            src.contains("private fun generateAppOptimizationPrompt("),
        )
    }

    @Test fun `no ACTION_SEND block in this file targets a specific package outside the robust share helper`() {
        // General guard against the same class of bug reappearing elsewhere in this (very
        // large) file: every OTHER ACTION_SEND block here is a generic "share to any app"
        // export (CSV, report, etc. via Intent.createChooser) with no setPackage(...) — that's
        // fine, standard Android share-sheet UX. A NEW block that pins ACTION_SEND to a specific
        // AI app's package would be the same bypass this test was written to catch.
        val src = readPowerConsumptionCardSource()
        val setPackageAiAppCount = Regex("""setPackage\(\s*aiApp\.packageName\s*\)""").findAll(src).count()
        assertTrue(
            "found $setPackageAiAppCount ACTION_SEND block(s) pinned to aiApp.packageName in " +
                "power_consumption_card.kt — AI-targeted shares must go through " +
                "shareWithAIAppRobust, not a hand-rolled Intent",
            setPackageAiAppCount == 0,
        )
    }

    // ─────────────────────── keyless remember{} permission bug (pre-existing fix) ───────────

    @Test fun `camera permission state updates directly from the launcher result, not a keyless remember`() {
        // Guards the 2026-07-24 fix already in place: hasCameraPermission was `remember { ... }`
        // with no key, so it never recomputed after the user granted permission — the preview
        // stayed hidden until the whole composition was torn down. The fix updates the state
        // var directly inside the permission launcher's callback.
        val src = readPowerConsumptionCardSource()
        val launcherIdx = src.indexOf("val cameraPermissionLauncher = rememberLauncherForActivityResult(")
        assertTrue("camera permission launcher not found — test is stale", launcherIdx >= 0)
        val launcherBlockEnd = src.indexOf("\n    }", launcherIdx).let { if (it > launcherIdx) it else src.length }
        val launcherBlock = src.substring(launcherIdx, launcherBlockEnd)
        assertTrue(
            "camera permission launcher callback must assign hasCameraPermission directly from " +
                "the result, not rely on a keyless remember{} to recompute it",
            launcherBlock.contains("hasCameraPermission = isGranted"),
        )
    }
}
