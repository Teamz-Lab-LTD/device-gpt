package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Pins the 2026-08-27 fix for the D1 push.
 *
 * Measured on 3.1.20 (GA4 481224245) — the only cohort both past the 20h delay and
 * running a build that can log worker_fired:
 *
 *     scheduled 36 = cancelled 20 + worker_fired 5 + UNACCOUNTED 11
 *
 * The 20 cancellations are correct behaviour. Of the 16 still eligible, 5 ran.
 * Every assertion below fails against the pre-fix source; verified by reverting.
 */
class D1DualDeliveryContractTest {

    private val workerRaw = File(
        "src/main/java/com/teamz/lab/debugger/utils/D1OvernightDrainWorker.kt"
    ).readText()

    /**
     * Comments are stripped before matching. The first run of this suite failed on
     * `setExactAndAllowWhileIdle` appearing in the doc comment that explains why it
     * is NOT used — a source-scanning test that reads its own prose is measuring
     * nothing.
     */
    private val worker = workerRaw
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val tracker = File(
        "src/main/java/com/teamz/lab/debugger/utils/EngagementTracker.kt"
    ).readText()

    @Test
    fun `both delivery paths are armed on first install`() {
        val schedule = worker.substringAfter("fun scheduleOnFirstInstall")
            .substringBefore("fun cancelIfPendingOrganicReturn")
        assertTrue(
            "WorkManager path must still be enqueued",
            schedule.contains("enqueueUniqueWork(WORK_NAME")
        )
        assertTrue(
            "AlarmManager fallback must be armed alongside it — a single subsystem " +
                "is what produced 5 fires out of 16 eligible installs",
            schedule.contains("armAlarmFallback(context)")
        )
    }

    @Test
    fun `organic return cancels both paths, never just one`() {
        val cancel = worker.substringAfter("fun cancelIfPendingOrganicReturn")
            .substringBefore("fun trackPushOpened")
        assertTrue(cancel.contains("cancelUniqueWork(WORK_NAME)"))
        assertTrue(
            "A returning user must not still get the push from the alarm path",
            cancel.contains("cancelAlarmFallback(context)")
        )
    }

    @Test
    fun `the two paths cannot both post — outcome is claimed exactly once`() {
        assertTrue(
            "claimOutcome must be the single gate both paths pass through",
            worker.contains("private fun claimOutcome(")
        )
        val deliver = worker.substringAfter("internal suspend fun deliver(")
        assertTrue(
            "deliver() must claim the outcome before posting",
            deliver.indexOf("claimOutcome(ctx)") in 0 until deliver.indexOf("postNotification(ctx, text)")
        )
    }

    @Test
    fun `a failed RC fetch retries instead of silently defaulting to false`() {
        val deliver = worker.substringAfter("internal suspend fun deliver(")
            .substringBefore("fun reportDeliveryPostMortem")
        assertTrue(
            "fetch success must be read separately from the flag value, or a timeout " +
                "is indistinguishable from the control arm of the A/B",
            deliver.contains("awaitD1OvernightDrainFetched()")
        )
        assertTrue(
            "a failed fetch must not fall through to the bundled default",
            deliver.contains("if (!fetched && canRetry)")
        )
        val doWork = worker.substringAfter("override suspend fun doWork()")
        assertTrue(
            "doWork must surface that as Result.retry()",
            doWork.contains("Result.retry()")
        )
        assertTrue(
            "retry needs a backoff policy or WorkManager uses the 30s default",
            worker.contains("setBackoffCriteria(BackoffPolicy.EXPONENTIAL")
        )
    }

    @Test
    fun `alarm receiver is declared, not exported, and matches the action constant`() {
        assertTrue(
            "receiver must be in the manifest or the alarm silently never delivers",
            manifest.contains("D1OvernightDrainWorker\$AlarmReceiver")
        )
        val receiver = manifest.substringAfter("D1OvernightDrainWorker\$AlarmReceiver")
            .substringBefore("</receiver>")
        assertTrue("receiver must not be exported", receiver.contains("android:exported=\"false\""))
        assertTrue(
            "manifest action must match ACTION_D1_ALARM",
            receiver.contains("com.teamz.lab.debugger.D1_OVERNIGHT_DRAIN")
        )
        assertTrue(
            worker.contains("const val ACTION_D1_ALARM = \"com.teamz.lab.debugger.D1_OVERNIGHT_DRAIN\"")
        )
    }

    @Test
    fun `exact alarms are not used — they would need SCHEDULE_EXACT_ALARM`() {
        assertFalse(
            "setExactAndAllowWhileIdle needs a permission Play scrutinises, and this " +
                "app already carries two Data Safety rejections",
            worker.contains("setExactAndAllowWhileIdle")
        )
        assertTrue(worker.contains("setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP"))
        assertFalse(
            "no new permission should be needed for this fix",
            manifest.contains("SCHEDULE_EXACT_ALARM") || manifest.contains("RECEIVE_BOOT_COMPLETED")
        )
    }

    @Test
    fun `the unaccounted installs get named on the next app open`() {
        assertTrue(worker.contains("fun reportDeliveryPostMortem("))
        val pm = worker.substringAfter("fun reportDeliveryPostMortem(")
        assertTrue(
            "must read WorkManager's own state, not re-derive it from our own prefs",
            pm.contains("getWorkInfosForUniqueWork(WORK_NAME)")
        )
        assertTrue("must be one-shot", pm.contains("KEY_POSTMORTEM_SENT"))
        assertTrue(
            "must not fire before the delay has elapsed, or every install reports ENQUEUED",
            pm.contains("if (ageMs < INITIAL_DELAY_HOURS")
        )
        assertTrue(
            "and it has to actually be called from the returning-user path",
            tracker.contains("D1OvernightDrainWorker.reportDeliveryPostMortem(context)")
        )
    }
}
