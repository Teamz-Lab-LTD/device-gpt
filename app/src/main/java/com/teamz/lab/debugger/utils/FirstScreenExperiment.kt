package com.teamz.lab.debugger.utils

import android.content.Context

/**
 * A/B test: Camera / Mic / Screen chooser on the score screen (arm B) vs today's flow (arm A).
 * Spec: docs/superpowers/specs/2026-10-09-first-screen-test-chooser-design.md
 * The arm is read from RC once, at the first score reveal, then fixed for this install.
 */
object FirstScreenExperiment {
    internal const val PREFS = "first_screen_experiment"
    internal const val KEY_ARM = "fs_arm"

    fun chooseArm(rcFlag: Boolean, stored: String?): String =
        stored ?: if (rcFlag) "B" else "A"

    fun arm(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.getString(KEY_ARM, null)?.let { return it }
        // RC unreadable (Firebase not initialised yet) -> control arm, never a crash on the score screen.
        val flag = try { RemoteConfigUtils.isFirstScreenTestChooserEnabled() } catch (_: Throwable) { false }
        val chosen = chooseArm(flag, null)
        p.edit().putString(KEY_ARM, chosen).apply()
        try { AnalyticsUtils.setUserProperty("fs_arm", chosen) } catch (_: Throwable) { }
        return chosen
    }

    fun isB(context: Context): Boolean = arm(context) == "B"

    fun tabFor(choice: String): String = when (choice) {
        "camera" -> "camera"
        "mic", "screen" -> "screen_test"
        else -> "health"
    }
}
