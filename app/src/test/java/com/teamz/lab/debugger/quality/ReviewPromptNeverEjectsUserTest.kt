package com.teamz.lab.debugger.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ReviewPromptManager used to answer a failed requestReviewFlow() by launching
 * `market://details?id=<pkg>` — throwing the user onto the Play Store listing about
 * 15 seconds into their first session, right after the score reveal, without asking.
 *
 * requestReviewFlow() fails for ordinary reasons (review quota already spent, no Play
 * Services, unsupported device), so this was a common path rather than an edge case.
 *
 * These guards read the source rather than the behaviour because the ejection lived in a
 * Play-services callback that a JVM test cannot drive. Comments are stripped first: the
 * explanation of the deleted code necessarily contains the words it forbids.
 */
class ReviewPromptNeverEjectsUserTest {

    private fun sourceWithoutComments(path: String): String {
        val raw = File(path).readText()
        val noBlock = raw.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        return noBlock.lines().joinToString("\n") { it.substringBefore("//") }
    }

    private val reviewManager =
        sourceWithoutComments("src/main/java/com/teamz/lab/debugger/utils/ReviewPromptManager.kt")

    @Test
    fun `positive control - the file was actually read`() {
        assertTrue(
            "ReviewPromptManager.kt did not load; every assertion below would pass vacuously",
            reviewManager.contains("object ReviewPromptManager")
        )
        assertTrue(
            "comment stripping removed real code",
            reviewManager.contains("requestReviewFlow()")
        )
    }

    @Test
    fun `negative control - comment stripping works`() {
        assertEquals(
            "a phrase that exists ONLY inside a comment leaked through the stripper",
            0,
            Regex("Google's In-App Review guidance").findAll(reviewManager).count()
        )
    }

    @Test
    fun `review prompt never deep-links to the store listing`() {
        assertEquals(
            "market:// deep link is back in ReviewPromptManager — a failed review flow must " +
                "not eject the user out of the app onto the Play Store",
            0,
            Regex("market://").findAll(reviewManager).count()
        )
        assertEquals(
            "ReviewPromptManager calls startActivity — it has no reason to navigate anywhere",
            0,
            Regex("""startActivity\s*\(""").findAll(reviewManager).count()
        )
    }

    @Test
    fun `a failed review flow is still measured`() {
        assertTrue(
            "the unavailable branch emits no analytics, so the failure rate is invisible",
            reviewManager.contains("AnalyticsEvent.ReviewFlowUnavailable")
        )
    }

    @Test
    fun `the old deep-link event name has no emitters left`() {
        val analytics =
            sourceWithoutComments("src/main/java/com/teamz/lab/debugger/utils/analytics_utils.kt")
        assertEquals(
            "review_opened_play_store is still defined; it described a behaviour that no " +
                "longer exists and will read as a live funnel step",
            0,
            Regex("review_opened_play_store").findAll(analytics).count()
        )
    }
}
