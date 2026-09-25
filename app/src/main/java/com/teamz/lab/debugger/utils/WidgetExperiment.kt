package com.teamz.lab.debugger.utils

import android.content.Context

/**
 * Labels each widget user with the widget layout they were actually served, so a Remote Config
 * split on `widget_v2_enabled` can be READ.
 *
 * Without this, flipping widget_v2_enabled to a 50% condition is a random change rather than an
 * experiment: GA4 has no way to tell which users saw the "what changed vs yesterday" layout and
 * which saw the original, so neither widget taps nor retention can be compared between them.
 *
 * Deliberately unlike [CohortLabeler], which assigns its own split from an install-timestamp hash.
 * Here Remote Config decides the arm and stays the kill switch; this object only records what the
 * device RESOLVED. That makes the label ground truth — including the empty-activation case, where
 * RC falls back to the bundled `false` and the user genuinely sees the old layout.
 *
 * Stamped from the widget render path only, so the property covers exactly the population the flag
 * can affect. A user with no widget pinned sees no difference and is correctly left unlabelled.
 */
object WidgetExperiment {

    const val USER_PROPERTY = "widget_v2_arm"
    const val ARM_ON = "on"
    const val ARM_OFF = "off"

    private const val PREFS = "widget_experiment"
    private const val KEY_LAST_STAMPED = "last_stamped_arm"

    fun armLabel(v2Enabled: Boolean): String = if (v2Enabled) ARM_ON else ARM_OFF

    /**
     * The widget renders hundreds of times a day (11,862 widget_displayed events over 30 days), so
     * re-stamping on every render would be pure overhead. Stamp only when the resolved arm differs
     * from the last one written. A never-stamped install (null) always stamps.
     */
    fun shouldStamp(lastStamped: String?, current: String): Boolean = lastStamped != current

    fun stampIfChanged(context: Context, v2Enabled: Boolean) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val current = armLabel(v2Enabled)
            if (!shouldStamp(prefs.getString(KEY_LAST_STAMPED, null), current)) return
            AnalyticsUtils.setUserProperty(USER_PROPERTY, current)
            prefs.edit().putString(KEY_LAST_STAMPED, current).apply()
        } catch (_: Throwable) {
            // Labelling must never break the widget render it rides on.
        }
    }
}
