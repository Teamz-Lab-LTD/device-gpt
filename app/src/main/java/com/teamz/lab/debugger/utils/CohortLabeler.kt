package com.teamz.lab.debugger.utils

import android.content.Context
import android.util.Log

/**
 * v3.1.11 W1 user-behavior insight — A/B cohort labeler.
 *
 * Stamps a GA4 user property `ab_cohort_v3111` exactly ONCE per install. Without
 * this, post-v3.1.11 retention dashboards cannot slice control vs treatment
 * cohorts (the workflow synthesis 2026-06-21 flagged this gap explicitly).
 *
 * Cohort assignment is deterministic: hash of install timestamp mod 100. Buckets
 * 0..49 = control, 50..99 = treatment. This split is stable per-install (cannot
 * drift if the user uninstalls + reinstalls — they get a fresh cohort, which is
 * the expected behavior for a new analytic identity).
 *
 * USAGE: call [labelOnce] from Application.onCreate AFTER AnalyticsUtils.init.
 * Internal SharedPreferences gate makes the call idempotent — second call no-ops.
 */
object CohortLabeler {

    private const val TAG = "CohortLabeler"
    private const val PREFS = "cohort_labeler"
    private const val KEY_COHORT_LABELED = "ab_cohort_v3111_labeled"
    private const val KEY_COHORT_VALUE = "ab_cohort_v3111_value"

    private const val USER_PROPERTY = "ab_cohort_v3111"
    private const val BUCKET_CONTROL = "control"
    private const val BUCKET_TREATMENT = "treatment"

    /**
     * Idempotent — first call assigns + stamps the user property, subsequent
     * calls re-stamp the previously assigned value (recovers from analytics
     * SDK state loss without ever changing a user's cohort).
     */
    fun labelOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val alreadyLabeled = prefs.getBoolean(KEY_COHORT_LABELED, false)
        val cohort = if (alreadyLabeled) {
            prefs.getString(KEY_COHORT_VALUE, BUCKET_CONTROL) ?: BUCKET_CONTROL
        } else {
            val assigned = assignCohort(System.currentTimeMillis())
            prefs.edit()
                .putBoolean(KEY_COHORT_LABELED, true)
                .putString(KEY_COHORT_VALUE, assigned)
                .apply()
            assigned
        }
        try {
            AnalyticsUtils.setUserProperty(USER_PROPERTY, cohort)
            Log.i(TAG, "ab_cohort_v3111 set to '$cohort' (alreadyLabeled=$alreadyLabeled)")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set user property: ${t.message}")
        }
    }

    /**
     * Deterministic 50/50 split based on install-timestamp hash. Visible for
     * tests — same input always returns the same bucket.
     */
    internal fun assignCohort(installTimestampMs: Long): String {
        val bucket = (installTimestampMs % 100).toInt()
        return if (bucket < 50) BUCKET_CONTROL else BUCKET_TREATMENT
    }

    /** Test/debug helper — clears the labeler state so a fresh assignment happens. */
    fun debugReset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        Log.d(TAG, "Cohort labeler state cleared (debug)")
    }
}
