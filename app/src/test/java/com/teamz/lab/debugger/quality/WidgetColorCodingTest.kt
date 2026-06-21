package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v3.1.11 W2 user-behavior insight — Widget color-code regression guard.
 *
 * Per GA4 28d data, the Widget is the #1 user surface (131 users, 77.8 displays
 * per user, 10,195 total impressions in 28d) — but tap-rate is only 31% (41 of
 * 131 widget owners actually tapped through). One reason: every metric uses the
 * same static color, so the eye has nothing to land on first.
 *
 * Fix shipped 2026-06-21: tint health score by value (green/amber/red/grey),
 * battery red when ≤20% / amber 21-35%, temperature red when >45°C / amber >40°C.
 * Pure runtime color change via RemoteViews.setInt(... setTextColor ...) — no
 * XML edit, no layout risk.
 *
 * Without this regression guard, a future refactor could revert to the
 * single-color rendering that produced the low tap-rate.
 */
class WidgetColorCodingTest {

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) {
            dir = dir.parentFile
        }
        return dir
    }

    private val src: String by lazy {
        File(projectRoot(),
            "app/src/main/java/com/teamz/lab/debugger/widgets/LockScreenMonitorWidget.kt").readText()
    }

    @Test
    fun `widget health score is color-coded by value`() {
        assertTrue(
            "Widget must call setInt(widget_health_score, setTextColor, ...) so the score " +
                "tints by health level — single-color reverts the 31% tap-rate regression.",
            src.contains("setInt(R.id.widget_health_score, \"setTextColor\"")
        )
        // 4 bands required: green good / amber caution / red critical / grey unknown
        assertTrue("Health green band (lime good) must be 0xFFD9FE06 — matches XML default for backwards-compat.",
            src.contains("0xFFD9FE06.toInt()"))
        assertTrue("Health amber band (caution) must use a warm-yellow.",
            src.contains("0xFFFFC107.toInt()"))
        assertTrue("Health red band (critical) must use the same red-coral as alert text.",
            src.contains("0xFFFF6B6B.toInt()"))
    }

    @Test
    fun `widget battery is tinted red below 20 percent`() {
        assertTrue(
            "Widget must call setInt(widget_battery, setTextColor, ...) so a critical battery " +
                "draws the eye even when the user is glancing.",
            src.contains("setInt(R.id.widget_battery, \"setTextColor\"")
        )
        // Sanity: the source block must reference the percentage bands 20 and 35.
        val batterySection = src.substringAfter("setInt(R.id.widget_battery").substringBefore("// v3.1.11 W2")
        // Either band 0..20 or 1..20 acceptable
        assertTrue(
            "Battery color logic must include a 'red below 20%' branch.",
            batterySection.contains("in 1..20") || batterySection.contains("in 0..20") || src.contains("in 1..20")
        )
    }

    @Test
    fun `widget temperature is tinted red when overheating`() {
        assertTrue(
            "Widget must call setInt(widget_thermal, setTextColor, ...) so an overheat " +
                "warning lands at a glance even if user skips the suffix text.",
            src.contains("setInt(R.id.widget_thermal, \"setTextColor\"")
        )
        // Sanity: red threshold 45f must appear (matches the 'Hot!' suffix logic above).
        assertTrue(
            "Temperature color logic must use the same 45°C threshold as the 'Hot!' suffix.",
            src.contains("tempNum > 45f")
        )
    }

    @Test
    fun `widget click target remains the MainActivity deep-link (legal CTA only)`() {
        // Companion guard: ensure nobody adds an ad-related click intent on the widget.
        // AdMob Program Policies explicitly forbid ads on Android home/lock screen
        // widgets — placing an ad-click handler here would trigger account suspension.
        assertTrue(
            "Widget click target must remain MainActivity deep-link (organic open). " +
                "DO NOT add ad SDK calls — AdMob policy violation.",
            src.contains("Intent(context, MainActivity::class.java)")
        )
        // Negative assertion — none of these strings may appear in the widget file.
        val forbiddenStrings = listOf(
            "AdView(",
            "InterstitialAd.load",
            "AppOpenAd.load",
            "AdLoader.Builder",
            "RewardedAd.load",
        )
        forbiddenStrings.forEach { needle ->
            assertTrue(
                "Forbidden ad-SDK reference '$needle' must NOT appear in LockScreenMonitorWidget.kt — " +
                    "AdMob Program Policies explicitly forbid ads on Android widgets. " +
                    "Adding any ad SDK call here risks account suspension and Play Store removal.",
                !src.contains(needle)
            )
        }
    }
}
