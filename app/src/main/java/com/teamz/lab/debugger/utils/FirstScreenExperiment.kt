package com.teamz.lab.debugger.utils

import android.content.Context

/**
 * A/B test: Camera / Mic / Screen chooser on the score screen (arm B) vs today's flow (arm A).
 * Spec: docs/superpowers/specs/2026-10-09-first-screen-test-chooser-design.md
 *
 * Only the score screen assigns an arm ([arm]), and only from an RC value that came from the
 * server. Before the first fetch lands, RC answers with the bundled `false`; storing that put every
 * slow or offline first launch in A (review 2026-10-09, C1). Such users are stored as "X": they see
 * today's flow and stay out of both arms for good, so a later fetch cannot move someone who has
 * already seen the A screen into B. fs_arm=X also measures how many are excluded. Everything else reads with [peekArm] / [isB], which
 * never assign, so vc51 upgraders and failed scans never enter the experiment (review I1).
 */
object FirstScreenExperiment {
    internal const val PREFS = "first_screen_experiment"
    internal const val KEY_ARM = "fs_arm"

    /** [rcFromServer] is null when RC has not delivered a server value yet -> excluded ("X"). */
    fun chooseArm(rcFromServer: Boolean?, stored: String?): String = when {
        stored != null -> stored
        rcFromServer == null -> "X"
        rcFromServer -> "B"
        else -> "A"
    }

    /** Score screen only. Assigns once: A/B from the server value, X when there is none yet. */
    fun arm(context: Context, rcFromServer: Boolean? = serverFlag()): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.getString(KEY_ARM, null)?.let { return it }
        val chosen = chooseArm(rcFromServer, null)
        p.edit().putString(KEY_ARM, chosen).apply()
        stamp(chosen)
        return chosen
    }

    fun peekArm(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ARM, null)

    fun isB(context: Context): Boolean = peekArm(context) == "B"

    /**
     * Re-sends the stored arm as the GA4 user property on every app start. A single stamp is lost
     * silently when analytics drops it (battery saver, doze, no context yet), and every per-arm
     * metric then misses that user (review I2).
     */
    fun restamp(context: Context) { peekArm(context)?.let(::stamp) }

    private fun stamp(arm: String) {
        try { AnalyticsUtils.setUserProperty("fs_arm", arm) } catch (_: Throwable) { }
    }

    // RC unreadable (Firebase not initialised) -> unknown, never a crash on the score screen.
    // A var so tests can stand in for a fetched server value.
    internal var serverFlagSource: () -> Boolean? =
        { try { RemoteConfigUtils.firstScreenTestChooserFromServer() } catch (_: Throwable) { null } }

    private fun serverFlag(): Boolean? = serverFlagSource()

    fun tabFor(choice: String): String = when (choice) {
        "camera" -> "camera"
        "mic", "screen" -> "screen_test"
        else -> "health"
    }
}
