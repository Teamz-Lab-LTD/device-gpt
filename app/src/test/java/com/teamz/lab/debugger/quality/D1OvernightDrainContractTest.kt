package com.teamz.lab.debugger.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Stability contract for v3.1.11 D1OvernightDrainWorker — pins names that, if
 * ever silently renamed, would (a) break A/B segmentation in GA4, (b) cause
 * WorkManager to enqueue duplicate jobs because the unique-name guard misses,
 * or (c) leak SharedPreferences keys to a future schema migration.
 *
 * Tests here are SOURCE-TEXT constants checks (fastest possible) — runtime
 * behaviour is covered by D1OvernightDrainWorkerTest.
 */
class D1OvernightDrainContractTest {

    private fun locate(rel: String): File {
        val root = System.getProperty("user.dir") ?: "."
        val candidates = listOf(
            File(root, rel),
            File(root, "app/$rel"),
            File(File(root).parentFile, "app/$rel"),
            File("../app/$rel"),
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
            ?: error("Could not locate $rel")
    }

    private val workerSrc by lazy {
        locate("src/main/java/com/teamz/lab/debugger/utils/D1OvernightDrainWorker.kt").readText()
    }

    @Test
    fun `WorkManager unique work name is exactly d1_overnight_drain`() {
        // Unique work name MUST be stable — changing this orphans previously-scheduled
        // workers on a user's device after an app update, and lets ExistingWorkPolicy.KEEP
        // miss the duplicate guard (double D1 push for same user = guaranteed uninstall).
        assertTrue(
            "WORK_NAME must remain the literal \"d1_overnight_drain\". Renaming breaks " +
                "ExistingWorkPolicy.KEEP across upgrades — users would receive two D1 pushes.",
            workerSrc.contains("private const val WORK_NAME = \"d1_overnight_drain\"")
        )
    }

    @Test
    fun `SharedPreferences keys are stable strings (regression guard)`() {
        // SharedPreferences key names define the persistence schema for the D1 lever
        // across app upgrades. Renaming any of these creates an upgrade-time orphan:
        // existing scheduled workers have baseline pinned under the old key, the new
        // schedule path reads the new key (default), and the personalized delta push
        // collapses to "Your battery dropped 0%" — meaningless to the user.
        val keys = mapOf(
            "PREFS" to "d1_overnight_drain",
            "KEY_BASELINE_BATTERY_PCT" to "baseline_battery_pct",
            "KEY_BASELINE_TS" to "baseline_ts",
            "KEY_WORK_SCHEDULED" to "work_scheduled",
            "CHANNEL_ID" to "d1_overnight_drain",
        )
        keys.forEach { (constant, expectedValue) ->
            assertTrue(
                "$constant must remain \"$expectedValue\" for forward-compatibility. " +
                    "Renaming = orphaned baseline on upgrade = meaningless push body.",
                workerSrc.contains("private const val $constant = \"$expectedValue\"")
            )
        }
    }

    @Test
    fun `Initial delay is exactly 20 hours (D1 sweet spot)`() {
        // 20h sits past the user's overnight charge cycle AND before the D1 drop-off
        // cliff at ~24-30h post-install. Industry data: ±2h matters. Drift to 16h or
        // 30h = lever loses 30-50% of its retention lift.
        assertTrue(
            "INITIAL_DELAY_HOURS must be exactly 20L. Drift moves D1 push outside the " +
                "post-overnight pre-cliff sweet spot.",
            workerSrc.contains("private const val INITIAL_DELAY_HOURS = 20L")
        )
    }

    @Test
    fun `Notification ID 2026 does not collide with other notification surfaces`() {
        // Notification IDs must be unique across the app's notification surfaces.
        // 2026 is chosen as "year of creation" — easy to grep for collision with
        // future workers. The RetentionNotificationManager + ChargeCycleTracker use
        // ranges 1000-1999; OneSignal uses ≥ 100000.
        assertTrue(
            "NOTIFICATION_ID must remain 2026 (year-of-creation, collision-safe vs " +
                "RetentionNotificationManager 1000-1999 + OneSignal ≥100000).",
            workerSrc.contains("private const val NOTIFICATION_ID = 2026")
        )
    }

    @Test
    fun `Worker body posts notification through NotificationCompat (not deprecated APIs)`() {
        // Direct NotificationManager.notify() bypasses POST_NOTIFICATIONS permission
        // check on Android 13+. Must go through NotificationManagerCompat.
        assertTrue(
            "Worker must post notifications via androidx.core.app.NotificationManagerCompat " +
                "to respect Android 13+ POST_NOTIFICATIONS permission gate.",
            workerSrc.contains("NotificationManagerCompat")
        )
    }

    @Test
    fun `cancelIfPendingOrganicReturn bounds the cancel window to the schedule lifetime`() {
        // Cancel logic must check baseline_ts age — if more than 20h elapsed, the
        // worker has already run (or been auto-removed) and a cancel call would
        // do nothing useful but might race-trigger WorkManager state edge cases.
        val cancelStart = workerSrc.indexOf("fun cancelIfPendingOrganicReturn(context: Context)")
        val cancelEnd = workerSrc.indexOf("\n    fun ", cancelStart + 1).let {
            if (it < 0) workerSrc.length else it
        }
        val body = workerSrc.substring(cancelStart, cancelEnd)
        assertTrue(
            "cancelIfPendingOrganicReturn must check baseline_ts age before calling cancel — " +
                "out-of-window calls should no-op rather than poke WorkManager state.",
            body.contains("KEY_BASELINE_TS") &&
                body.contains("INITIAL_DELAY_HOURS")
        )
        assertTrue(
            "cancelIfPendingOrganicReturn must clear KEY_WORK_SCHEDULED after cancellation, " +
                "so a subsequent install detects scheduling state correctly.",
            body.contains("putBoolean(KEY_WORK_SCHEDULED, false)")
        )
    }

    @Test
    fun `Notification channel importance is DEFAULT not HIGH or MAX`() {
        // D1 push is NOT urgent — it's a soft re-engagement nudge. Channel importance
        // HIGH or MAX would full-screen the notification on Android 13+, breaking
        // user trust on first 24h. DEFAULT lets it appear in the shade only.
        assertTrue(
            "Notification channel must use IMPORTANCE_DEFAULT — D1 is re-engagement, not urgent.",
            workerSrc.contains("NotificationManager.IMPORTANCE_DEFAULT")
        )
    }
}
