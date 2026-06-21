package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.CohortLabeler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v3.1.11 W1 user-behavior insight — A/B cohort labeler tests.
 *
 * The labeler stamps a GA4 user property exactly once per install. These tests
 * verify:
 *   - assignCohort is deterministic (same input -> same bucket)
 *   - labelOnce is idempotent (second call re-stamps the same value, never reassigns)
 *   - 50/50 split is reasonable across a large input range
 *   - debugReset restores fresh-install behavior
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CohortLabelerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CohortLabeler.debugReset(context)
    }

    @Test
    fun `assignCohort is deterministic for the same input`() {
        val ts = 1_750_000_000_000L
        val first = CohortLabeler.assignCohort(ts)
        val second = CohortLabeler.assignCohort(ts)
        assertEquals(
            "Same input timestamp must always assign the same cohort — without this property " +
                "the labeler could drift mid-flight if storage is lost.",
            first, second
        )
    }

    @Test
    fun `assignCohort outputs exactly one of control or treatment`() {
        val allowed = setOf("control", "treatment")
        for (ts in listOf(0L, 1L, 49L, 50L, 99L, 1_750_000_000_000L, Long.MAX_VALUE / 2)) {
            val cohort = CohortLabeler.assignCohort(ts)
            assertTrue(
                "assignCohort($ts) must return 'control' or 'treatment', got '$cohort'",
                cohort in allowed
            )
        }
    }

    @Test
    fun `assignCohort produces roughly 50_50 split across many inputs`() {
        var control = 0
        var treatment = 0
        for (ts in 1L..10_000L) {
            when (CohortLabeler.assignCohort(ts)) {
                "control" -> control++
                "treatment" -> treatment++
            }
        }
        // 50/50 split with mod-100 should be exact
        assertEquals(
            "Mod-100 with 50/50 cutoff over 10000 sequential inputs should split evenly.",
            5000, control
        )
        assertEquals(5000, treatment)
    }

    @Test
    fun `labelOnce is idempotent across repeated calls`() {
        CohortLabeler.labelOnce(context)
        val prefs = context.getSharedPreferences("cohort_labeler", Context.MODE_PRIVATE)
        val firstAssignment = prefs.getString("ab_cohort_v3111_value", null)
        assertTrue(
            "First labelOnce call must persist a cohort value.",
            firstAssignment in setOf("control", "treatment")
        )
        // Call again many times — assignment must never change
        repeat(10) { CohortLabeler.labelOnce(context) }
        val laterAssignment = prefs.getString("ab_cohort_v3111_value", null)
        assertEquals(
            "Subsequent labelOnce calls must NOT reassign the cohort (would invalidate any " +
                "running A/B test by silently moving the user to the other bucket).",
            firstAssignment, laterAssignment
        )
    }

    @Test
    fun `debugReset clears the labeled flag so a new assignment happens`() {
        CohortLabeler.labelOnce(context)
        val prefs = context.getSharedPreferences("cohort_labeler", Context.MODE_PRIVATE)
        assertTrue(
            "After first labelOnce, ab_cohort_v3111_labeled must be true.",
            prefs.getBoolean("ab_cohort_v3111_labeled", false)
        )
        CohortLabeler.debugReset(context)
        assertEquals(
            "After debugReset, ab_cohort_v3111_labeled must be cleared.",
            false, prefs.getBoolean("ab_cohort_v3111_labeled", false)
        )
    }
}
