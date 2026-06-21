package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v3.1.11 W2 — Referral share-text quality + analytics contract.
 *
 * Per growth research the default share message determines click-through more
 * than placement. The old text was hashtag-heavy ("#PhoneHealth #DeviceChecker
 * #TechTools") which performs poorly in private DMs (WhatsApp/Telegram/SMS —
 * the main share channels for solo BD/IN users).
 *
 * Rewrite shipped 2026-06-22:
 *   - Link on line 1 (one-tap-copy from WhatsApp preview)
 *   - Lead with friend's benefit ("what's draining your battery"), not sender's
 *   - Concrete deliverable named ("0–100 Device Score in 10 seconds")
 *   - Reward visible to RECEIVER ("we BOTH get ad-free time")
 *   - No hashtags (they don't help in private DMs)
 *
 * This regression guard prevents the text from regressing to the old hashtag-
 * heavy version. Also asserts the analytics event still fires.
 */
class ReferralShareTextContractTest {

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) {
            dir = dir.parentFile
        }
        return dir
    }

    private val src: String by lazy {
        File(projectRoot(),
            "app/src/main/java/com/teamz/lab/debugger/utils/referral_manager.kt").readText()
    }

    @Test
    fun shareText_leadsWithLinkOnLine1() {
        // The default share text body must place the link FIRST in the body so
        // WhatsApp/Telegram render the preview card immediately. Old version
        // buried the link under 5 lines of generic text.
        val shareFnStart = src.indexOf("fun shareReferralLink(")
        assertTrue("shareReferralLink function must exist", shareFnStart > 0)
        val shareBody = src.substring(shareFnStart, minOf(shareFnStart + 1500, src.length))
        // Trim leading whitespace inside the multiline string starts with the
        // referralLink variable interpolation immediately after triple-quote.
        assertTrue(
            "Share text default must place \$referralLink as the FIRST non-blank line of " +
                "the body so messaging-app link-preview cards render immediately.",
            shareBody.contains("\"\"\"\n            \$referralLink\n")
        )
    }

    @Test
    fun shareText_namesConcreteDeliverable() {
        // Specific > vague. "0-100 Device Score in 10 seconds" beats "amazing app".
        assertTrue(
            "Share text must mention a concrete deliverable ('Device Score') so the " +
                "receiver knows exactly what they get.",
            src.contains("0–100 Device Score") || src.contains("Device Score")
        )
        assertTrue(
            "Share text must mention the 10-second promise so the receiver knows the " +
                "time cost up front.",
            src.contains("10 seconds")
        )
    }

    @Test
    fun shareText_makesRewardVisibleToReceiver() {
        // The whole point of a referral is mutual reward. Old text only mentioned the
        // referral code without explaining what the receiver gets.
        assertTrue(
            "Share text must make the reward visible to both sides (e.g. 'we BOTH get' " +
                "or similar mutual-benefit phrasing).",
            src.contains("BOTH get") || src.contains("we both")
        )
    }

    @Test
    fun shareText_dropsHashtagsThatDontWorkInDms() {
        // Hashtags are dead weight in WhatsApp/Telegram/SMS — they take character
        // space without driving any traffic. The old text had #PhoneHealth /
        // #DeviceChecker / #TechTools.
        // Only inspect the actual multiline string literal — comments may legitimately
        // reference the old hashtags as historical context (e.g. "old text had #X").
        val defaultTextStart = src.indexOf("val defaultText = \"\"\"")
        val defaultTextEnd = src.indexOf("\"\"\".trimIndent()", defaultTextStart)
        assertTrue("defaultText multiline string must exist", defaultTextStart > 0 && defaultTextEnd > 0)
        val literal = src.substring(defaultTextStart, defaultTextEnd)
        val removedHashtags = listOf("#PhoneHealth", "#DeviceChecker", "#TechTools")
        removedHashtags.forEach { tag ->
            assertTrue(
                "Old hashtag '$tag' must NOT appear inside the defaultText literal — adds " +
                    "noise without driving any clicks in private DMs (the dominant share channel).",
                !literal.contains(tag)
            )
        }
    }

    @Test
    fun shareReferralLink_fires_ReferralShared_analyticsEvent() {
        // Without this event, the funnel (shared → clicked → installed) breaks at
        // step 1 — we wouldn't know if anyone is even attempting to share.
        val shareFnIdx = src.indexOf("fun shareReferralLink(")
        val shareBody = src.substring(shareFnIdx, minOf(shareFnIdx + 1500, src.length))
        assertTrue(
            "shareReferralLink must fire AnalyticsEvent.ReferralShared so the funnel " +
                "step 1 is measurable.",
            shareBody.contains("AnalyticsEvent.ReferralShared")
        )
        assertTrue(
            "shareReferralLink must use ACTION_SEND so the OS share chooser opens " +
                "(not a custom in-app dialog that loses cross-app share fidelity).",
            shareBody.contains("Intent.ACTION_SEND")
        )
    }
}
