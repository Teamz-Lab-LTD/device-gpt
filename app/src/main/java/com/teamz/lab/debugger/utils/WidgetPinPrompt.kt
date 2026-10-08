package com.teamz.lab.debugger.utils

import android.app.PendingIntent
import android.content.Intent
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

    const val ACTION_WIDGET_PIN_SUCCESS = "com.teamz.lab.debugger.WIDGET_PIN_SUCCESS"
    private const val PIN_SUCCESS_REQUEST_CODE = 2028


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
            // The third argument is the OS success callback. It was null until 2026-09-08,
            // which made widget_add_to_home_screen_success structurally impossible to fire —
            // "18 prompts, 0 successes" measured nothing at all. Supplying a PendingIntent is
            // the only way the launcher can tell us the widget was really pinned.
            val successIntent = PendingIntent.getBroadcast(
                context,
                PIN_SUCCESS_REQUEST_CODE,
                Intent(context, WidgetPinResultReceiver::class.java)
                    .setAction(ACTION_WIDGET_PIN_SUCCESS),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val requested = awm.requestPinAppWidget(component, null, successIntent)
            log(if (requested) "shown" else "request_rejected")
            return requested
        } catch (t: Throwable) {
            android.util.Log.w("WidgetPinPrompt", "maybePrompt failed: ${t.message}")
            return false
        }
    }

    /** The user tapped "Add widget": ask the launcher now. No RC flag, no once-per-install. */
    fun requestNow(context: Context): Boolean = try {
        val awm = context.getSystemService<AppWidgetManager>()
        if (awm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !awm.isRequestPinAppWidgetSupported) false
        else {
            val successIntent = PendingIntent.getBroadcast(
                context, PIN_SUCCESS_REQUEST_CODE,
                Intent(context, WidgetPinResultReceiver::class.java).setAction(ACTION_WIDGET_PIN_SUCCESS),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            awm.requestPinAppWidget(ComponentName(context, LockScreenMonitorWidget::class.java), null, successIntent)
        }
    } catch (t: Throwable) { false }

    private fun log(result: String) {
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.WidgetPinPromptResult,
                mapOf("result" to result)
            )
        } catch (_: Throwable) { }
    }
}

/**
 * Fires when the launcher confirms the widget was actually pinned. This is the other half of
 * the PendingIntent handed to requestPinAppWidget — without it the pin funnel ends at "asked".
 */
class WidgetPinResultReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetPinPrompt.ACTION_WIDGET_PIN_SUCCESS) return
        try {
            AnalyticsUtils.init(context.applicationContext)
            AnalyticsUtils.logEvent(
                AnalyticsEvent.WidgetAddToHomeScreenSuccess,
                mapOf("source" to "pin_prompt")
            )
        } catch (_: Throwable) { /* analytics not critical */ }
    }
}
