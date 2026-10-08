package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2026-10-09. After vc51 native ads loaded properly (61 requests/day, 100% filled) but AdMob
 * counted 0 impressions: the first Health-tab slot was the 7th card, two full screens down
 * (measured on the emulator: the badge appeared only after 2 swipes). Users do not scroll there.
 * The first slot now sits right after the Device Timeline, on the first screen, and away from
 * the score card's Run again / Share buttons (accidental-click distance).
 */
class NativeAdPlacementTest {

    private val src: String by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        File(dir, "app/src/main/java/com/teamz/lab/debugger/ui/health_section.kt").readText()
    }

    @Test
    fun `first native ad is on the first screen, after the timeline and before the health score card`() {
        val timeline = src.indexOf("item(key = \"device_timeline\")")
        val ad = src.indexOf("item(key = \"native_ad_top\")")
        val healthScore = src.indexOf("item(key = \"health_score\")")
        assertTrue("all three items must exist", timeline >= 0 && ad >= 0 && healthScore >= 0)
        assertTrue("native_ad_top must follow device_timeline", ad > timeline)
        assertTrue("native_ad_top must come before health_score (was the 7th card)", ad < healthScore)
    }

    @Test
    fun `first native ad is not directly under the score card buttons`() {
        val score = src.indexOf("item(key = \"last_device_score\")")
        val ad = src.indexOf("item(key = \"native_ad_top\")")
        val between = src.substring(score, ad)
        assertTrue("at least one card must separate the ad from Run again / Share",
            between.contains("item(key = \"device_timeline\")"))
    }
}
