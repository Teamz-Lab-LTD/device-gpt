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
    fun `D1 worker is gated by Remote Config flag at FIRE TIME ONLY (race guard)`() {
        // Schedule-time RC gate is a BUG: bundled APK defaults (flag=false) are read on
        // fresh install BEFORE the network fetch completes (~5 min). A schedule-time
        // gate would lose the D1 lever for every user installing before RC fetches.
        // Worker re-checks the flag at fire time (20h later) — by then RC has fetched
        // the latest server value, and the owner can also flip mid-flight to kill push.
        val checks = Regex("RemoteConfigUtils\\.isD1OvernightDrainEnabled\\(\\)").findAll(workerSrc).count()
        assertTrue(
            "Worker must read isD1OvernightDrainEnabled() at LEAST ONCE (at fire time). " +
                "Found $checks. Zero means no RC gate at all — push fires even when owner " +
                "has disabled it.",
            checks >= 1
        )
        // Race-condition regression guard: schedule path must NOT gate on RC.
        val scheduleStart = workerSrc.indexOf("fun scheduleOnFirstInstall(context: Context)")
        val scheduleEnd = workerSrc.indexOf("\n    fun ", scheduleStart + 1).let {
            if (it < 0) workerSrc.length else it
        }
        val scheduleBody = workerSrc.substring(scheduleStart, scheduleEnd)
        assertFalse(
            "scheduleOnFirstInstall MUST NOT call isD1OvernightDrainEnabled() — that creates " +
                "a race where on a fresh install the bundled APK default=false is read before " +
                "the RC network fetch completes (~5 min), and the D1 lever is silently lost " +
                "for every user installing before RC fetches. Gate at fire time only.",
            scheduleBody.contains("isD1OvernightDrainEnabled")
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
    fun `D1 analytics events exist with correct event names (full funnel)`() {
        // AnalyticsEvent enum is the contract between the worker and GA4 funnels.
        // Full funnel = Scheduled -> Pushed -> (Cancelled | Opened). Missing any
        // makes the D1 retention dashboard unreadable.
        val analyticsSrc = locate("src/main/java/com/teamz/lab/debugger/utils/analytics_utils.kt").readText()
        val required = listOf(
            "D1OvernightDrainScheduled(\"d1_overnight_drain_scheduled\")",
            "D1OvernightDrainPushed(\"d1_overnight_drain_pushed\")",
            "D1OvernightDrainCancelled(\"d1_overnight_drain_cancelled\")",
            "D1OvernightDrainOpened(\"d1_overnight_drain_opened\")",
        )
        required.forEach { decl ->
            assertTrue(
                "AnalyticsEvent enum must contain $decl — full funnel measurability " +
                    "requires all four lifecycle events.",
                analyticsSrc.contains(decl)
            )
        }
    }

    @Test
    fun `scheduleOnFirstInstall EMITS D1OvernightDrainScheduled analytics event`() {
        // Without this event, owner can't measure "how many users entered the
        // experiment cohort" vs "how many actually got the push." Required for
        // funnel math: scheduled count is the denominator of pushed/scheduled.
        val scheduleStart = workerSrc.indexOf("fun scheduleOnFirstInstall(context: Context)")
        val scheduleEnd = workerSrc.indexOf("\n    fun ", scheduleStart + 1).let {
            if (it < 0) workerSrc.length else it
        }
        val body = workerSrc.substring(scheduleStart, scheduleEnd)
        assertTrue(
            "scheduleOnFirstInstall must emit AnalyticsEvent.D1OvernightDrainScheduled — " +
                "denominator of the D1 funnel.",
            body.contains("AnalyticsEvent.D1OvernightDrainScheduled")
        )
        assertTrue(
            "D1OvernightDrainScheduled event must include baseline_pct param for cohort " +
                "segmentation (e.g. low-battery cohort might respond differently).",
            body.contains("\"baseline_pct\"")
        )
    }

    @Test
    fun `cancelIfPendingOrganicReturn EMITS D1OvernightDrainCancelled event`() {
        // Cancel rate measures "how many users came back organically before push fired".
        // High cancel rate vs pushed rate = good — means UI is sticky. Required signal
        // to know whether D1 push is needed at all or if onboarding is doing the work.
        val cancelStart = workerSrc.indexOf("fun cancelIfPendingOrganicReturn(context: Context)")
        val cancelEnd = workerSrc.indexOf("\n    fun ", cancelStart + 1).let {
            if (it < 0) workerSrc.length else it
        }
        val body = workerSrc.substring(cancelStart, cancelEnd)
        assertTrue(
            "cancelIfPendingOrganicReturn must emit AnalyticsEvent.D1OvernightDrainCancelled " +
                "— organic-return rate is the diagnostic for whether D1 push is needed.",
            body.contains("AnalyticsEvent.D1OvernightDrainCancelled")
        )
        assertTrue(
            "D1OvernightDrainCancelled event must include cancel_at_min param so we can " +
                "histogram time-to-organic-return (e.g. most cancel at +2h vs +18h).",
            body.contains("\"cancel_at_min\"")
        )
    }

    @Test
    fun `trackPushOpened exists and emits D1OvernightDrainOpened event`() {
        // CLOSES THE FUNNEL: scheduled -> pushed -> OPENED. Without this we know
        // pushes fired but not whether they brought users back — can't measure
        // D1 retention lift at all.
        assertTrue(
            "D1OvernightDrainWorker must expose trackPushOpened(context: Context) for " +
                "MainActivity to call when launched via the D1 notification deep-link.",
            workerSrc.contains("fun trackPushOpened(context: Context)")
        )
        val openStart = workerSrc.indexOf("fun trackPushOpened(context: Context)")
        val openEnd = workerSrc.indexOf("\n    fun ", openStart + 1).let {
            if (it < 0) workerSrc.length else it
        }
        val body = workerSrc.substring(openStart, openEnd)
        assertTrue(
            "trackPushOpened must emit AnalyticsEvent.D1OvernightDrainOpened — closes the " +
                "scheduled->pushed->opened funnel that proves D1 lift.",
            body.contains("AnalyticsEvent.D1OvernightDrainOpened")
        )
        assertTrue(
            "trackPushOpened must include time_from_install_min so we can correlate push " +
                "timing with open rate (e.g. is 20h truly the sweet spot vs 16h or 24h).",
            body.contains("\"time_from_install_min\"")
        )
    }

    @Test
    fun `MainActivity wires d1_overnight_drain deep-link case to trackPushOpened`() {
        // If MainActivity's deep-link switch doesn't handle "d1_overnight_drain",
        // the user tapping the notification still launches the app but no Opened
        // event fires — funnel breaks invisibly.
        val mainSrc = locate("src/main/java/com/teamz/lab/debugger/MainActivity.kt").readText()
        val handlerStart = mainSrc.indexOf("private fun handleChargeSummaryDeepLink(intent: Intent?)")
        if (handlerStart < 0) error("MainActivity.handleChargeSummaryDeepLink not found")
        val handlerEnd = mainSrc.indexOf("\n    override fun ", handlerStart + 1).let {
            if (it < 0) mainSrc.length else it
        }
        val body = mainSrc.substring(handlerStart, handlerEnd)
        assertTrue(
            "MainActivity.handleChargeSummaryDeepLink must include the \"d1_overnight_drain\" " +
                "case so notification-tap launches close the funnel.",
            body.contains("\"d1_overnight_drain\" ->")
        )
        assertTrue(
            "The d1_overnight_drain case must call D1OvernightDrainWorker.trackPushOpened(this) " +
                "— that's the funnel-close event emitter.",
            body.contains("D1OvernightDrainWorker.trackPushOpened(this)")
        )
        assertTrue(
            "The d1_overnight_drain case should also record an EngagementTracker significant " +
                "action so habit-streak math counts the return.",
            body.contains("\"d1_overnight_drain_opened\"")
        )
    }
}
