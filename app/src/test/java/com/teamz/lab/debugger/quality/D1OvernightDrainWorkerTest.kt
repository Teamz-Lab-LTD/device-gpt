package com.teamz.lab.debugger.quality

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression guard for v3.1.11 D1OvernightDrainWorker.
 *
 * Background: prior RetentionNotificationManager waited 3-7 days before its
 * first push, missing the D1 cliff entirely (utility apps lose 70%+ between
 * D0 and D1). v3.1.11 ships a one-shot 20-hour OneTimeWorkRequest that fires
 * a personalized overnight-drain push using the user's real baseline battery %.
 *
 * These tests are source-text checks (no Robolectric runtime needed) and
 * enforce the structural invariants that, if broken, would silently break the
 * D1 retention lever before anyone notices.
 */
class D1OvernightDrainWorkerTest {

    private fun locate(rel: String): File {
        val root = System.getProperty("user.dir") ?: "."
        val candidates = listOf(
            File(root, rel),
            File(root, "app/$rel"),
            File(File(root).parentFile, "app/$rel"),
            File("../app/$rel"),
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
            ?: error("Could not locate $rel — tried: ${candidates.joinToString { it.absolutePath }}")
    }

    private val workerSrc by lazy {
        locate("src/main/java/com/teamz/lab/debugger/utils/D1OvernightDrainWorker.kt").readText()
    }
    private val engagementSrc by lazy {
        locate("src/main/java/com/teamz/lab/debugger/utils/EngagementTracker.kt").readText()
    }
    private val rcSrc by lazy {
        locate("src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt").readText()
    }

    @Test
    fun `D1OvernightDrainWorker singleton exists with scheduleOnFirstInstall + cancel hooks`() {
        assertTrue(
            "D1OvernightDrainWorker must declare an `object` singleton — the worker is invoked " +
                "from EngagementTracker.init() and needs a stable entry point.",
            workerSrc.contains("object D1OvernightDrainWorker")
        )
        assertTrue(
            "scheduleOnFirstInstall(context) must exist — that's the call site from " +
                "EngagementTracker.init() when first install is detected.",
            workerSrc.contains("fun scheduleOnFirstInstall(context: Context)")
        )
        assertTrue(
            "cancelIfPendingOrganicReturn(context) must exist — that's the cancel hook from " +
                "EngagementTracker.init() when a returning user opens the app organically.",
            workerSrc.contains("fun cancelIfPendingOrganicReturn(context: Context)")
        )
    }

    @Test
    fun `D1 worker uses 20-hour OneTimeWorkRequest (not PeriodicWorkRequest)`() {
        assertTrue(
            "Worker must use OneTimeWorkRequestBuilder — the D1 push is fire-and-forget, " +
                "not periodic. Switching to PeriodicWorkRequest would recreate the v3.1.10 " +
                "RetentionNotificationManager bug (push every N days, miss D1 entirely).",
            workerSrc.contains("OneTimeWorkRequestBuilder<")
        )
        assertTrue(
            "Initial delay must be set in HOURS, not MINUTES/DAYS. 20 hours sits in the " +
                "post-overnight, pre-D1-cliff sweet spot for utility-app retention.",
            workerSrc.contains("INITIAL_DELAY_HOURS") &&
                workerSrc.contains("setInitialDelay(INITIAL_DELAY_HOURS, TimeUnit.HOURS)")
        )
        assertFalse(
            "Worker must NOT use PeriodicWorkRequest — that's the deprecated 3-day worker " +
                "RetentionNotificationManager already does (and which misses D1).",
            workerSrc.contains("PeriodicWorkRequest")
        )
    }

    @Test
    fun `D1 worker is gated by Remote Config flag and reads it at both schedule and fire time`() {
        // Reading the RC flag at fire time too (not just at schedule) lets us A/B mid-cohort
        // by flipping the flag without re-installing every user.
        val checks = Regex("RemoteConfigUtils\\.isD1OvernightDrainEnabled\\(\\)").findAll(workerSrc).count()
        assertTrue(
            "Worker must read RemoteConfigUtils.isD1OvernightDrainEnabled() at BOTH schedule " +
                "time and fire time (found $checks). Single-check at schedule means a stale " +
                "RC value gets pinned for the 20-hour delay; double-check lets owner kill the " +
                "push mid-flight by flipping the flag.",
            checks >= 2
        )
        assertTrue(
            "RemoteConfigUtils must expose isD1OvernightDrainEnabled() — required by the worker gate.",
            rcSrc.contains("fun isD1OvernightDrainEnabled(): Boolean") &&
                rcSrc.contains("\"d1_overnight_drain_enabled\"")
        )
    }

    @Test
    fun `D1 worker captures a real BatteryManager baseline at schedule time`() {
        assertTrue(
            "Worker must capture battery % at schedule time (the D1 push body needs a real " +
                "baseline to compute delta against — generic feature-talk pushes don't move D1).",
            workerSrc.contains("BatteryManager.BATTERY_PROPERTY_CAPACITY")
        )
        assertTrue(
            "Baseline must persist to SharedPreferences across the 20-hour delay (the process " +
                "may die in between schedule and fire).",
            workerSrc.contains("KEY_BASELINE_BATTERY_PCT") &&
                workerSrc.contains("putInt(KEY_BASELINE_BATTERY_PCT")
        )
    }

    @Test
    fun `D1 worker is idempotent via ExistingWorkPolicy_KEEP and KEY_WORK_SCHEDULED guard`() {
        assertTrue(
            "Worker enqueue must use ExistingWorkPolicy.KEEP — a second EngagementTracker.init() " +
                "call on a hot restart must NOT replace the already-scheduled worker.",
            workerSrc.contains("ExistingWorkPolicy.KEEP")
        )
        assertTrue(
            "scheduleOnFirstInstall must short-circuit on the KEY_WORK_SCHEDULED pref so it " +
                "never double-enqueues even if the caller is buggy.",
            workerSrc.contains("KEY_WORK_SCHEDULED") &&
                workerSrc.contains("getBoolean(KEY_WORK_SCHEDULED, false)")
        )
    }

    @Test
    fun `EngagementTracker_init wires D1 scheduling on first install AND cancel on returning open`() {
        // The init() function is the single integration point — every other surface that
        // wants to "trigger D1 retention work" must go through here.
        val initStart = engagementSrc.indexOf("fun init(context: Context)")
        if (initStart < 0) error("EngagementTracker.init() not found — refactor broke the integration point")
        // Find the end of the init function body — next top-level fun declaration is the boundary.
        val initEnd = engagementSrc.indexOf("\n    fun ", initStart + 1).let {
            if (it < 0) engagementSrc.length else it
        }
        val body = engagementSrc.substring(initStart, initEnd)
        assertTrue(
            "EngagementTracker.init() must call D1OvernightDrainWorker.scheduleOnFirstInstall " +
                "on the first-install branch (gated by isFirstInstall).",
            body.contains("D1OvernightDrainWorker.scheduleOnFirstInstall(context)")
        )
        assertTrue(
            "EngagementTracker.init() must call D1OvernightDrainWorker.cancelIfPendingOrganicReturn " +
                "on the returning-user branch — a user who came back on their own should NOT " +
                "be re-prompted by the D1 push.",
            body.contains("D1OvernightDrainWorker.cancelIfPendingOrganicReturn(context)")
        )
    }

    @Test
    fun `D1 analytics events exist with correct event names`() {
        // AnalyticsEvent enum is the contract between the worker and GA4 funnels.
        val analyticsSrc = locate("src/main/java/com/teamz/lab/debugger/utils/analytics_utils.kt").readText()
        assertTrue(
            "AnalyticsEvent.D1OvernightDrainPushed must exist with event name 'd1_overnight_drain_pushed'.",
            analyticsSrc.contains("D1OvernightDrainPushed(\"d1_overnight_drain_pushed\")")
        )
    }
}
