package com.teamz.lab.debugger.utils

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.teamz.lab.debugger.widgets.LockScreenMonitorWidget

/**
 * v3.2.0 — widget pin prompt (2026-07-10 growth synthesis, UI/UX #5).
 *
 * Shown ONE BEAT AFTER the score reveal (after "See details" lands on the
 * Health tab or after the share sheet closes) — never as a third parallel
 * button competing with the score CTAs (applied verifier modification).
 *
 * Guards, all mandatory:
 *   - RC `widget_pin_prompt_enabled` (default OFF)
 *   - OS support: requestPinAppWidget requires API 26+ AND launcher support
 *   - No existing widget instance (never nag users who already added it)
 *   - Once per install, ever
 */
object WidgetPinPrompt {

    private const val PREFS = "widget_pin_prompt"
    private const val KEY_PROMPTED = "prompted_once"

    /** Returns true when the OS pin sheet was actually requested. */
    fun maybePrompt(context: Context): Boolean {
        try {
            if (!RemoteConfigUtils.isWidgetPinPromptEnabled()) return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false

            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (p.getBoolean(KEY_PROMPTED, false)) return false

            val awm = context.getSystemService<AppWidgetManager>() ?: return false
            val component = ComponentName(context, LockScreenMonitorWidget::class.java)

            if (awm.getAppWidgetIds(component).isNotEmpty()) {
                log("already_added")
                p.edit { putBoolean(KEY_PROMPTED, true) }
                return false
            }
            if (!awm.isRequestPinAppWidgetSupported) {
                log("unsupported")
                p.edit { putBoolean(KEY_PROMPTED, true) }
                return false
            }

            p.edit { putBoolean(KEY_PROMPTED, true) } // one shot, regardless of outcome
            val requested = awm.requestPinAppWidget(component, null, null)
            log(if (requested) "shown" else "request_rejected")
            return requested
        } catch (t: Throwable) {
            android.util.Log.w("WidgetPinPrompt", "maybePrompt failed: ${t.message}")
            return false
        }
    }

    private fun log(result: String) {
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.WidgetPinPromptResult,
                mapOf("result" to result)
            )
        } catch (_: Throwable) { }
    }
}
