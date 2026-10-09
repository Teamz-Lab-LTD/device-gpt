package com.teamz.lab.debugger.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.teamz.lab.debugger.MainActivity
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.LocaleManager
import com.teamz.lab.debugger.ui.icons.RemoteIcons

/**
 * Device Monitor Widget
 * 
 * Shows comprehensive device monitoring data on home screen (and lock screen if supported)
 * Uses data from existing SystemMonitorService - no duplicate monitoring
 * 
 * Note: Lock screen widgets are only available on Android 16+ on some devices.
 * This widget works on home screen for all Android 13+ devices.
 * 
 * Displays:
 * - Power consumption (Watts)
 * - Battery percentage
 * - RAM usage
 * - CPU info
 * - Network speed
 * - Health score
 * - Daily streak
 */
class LockScreenMonitorWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        android.util.Log.d("DeviceGPT_Widget", "onUpdate called for ${appWidgetIds.size} widget(s)")
        // An explicit update broadcast reaches the provider even with no widget pinned; logging
        // WidgetDisplayed{widget_count=0} then would count every scan as a widget display.
        if (appWidgetIds.isEmpty()) return
        
        // Track widget display
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.WidgetDisplayed, mapOf(
                    "widget_count" to appWidgetIds.size,
                    "android_version" to Build.VERSION.SDK_INT
                )
            )
        } catch (e: Exception) {
            android.util.Log.w("DeviceGPT_Widget", "Failed to log analytics", e)
        }
        
        // Update all widget instances
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        // Update widget when data changes
        if (intent.action == ACTION_UPDATE_WIDGET || 
            intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, LockScreenMonitorWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        android.util.Log.d("DeviceGPT_Widget", "Updating widget $appWidgetId")
        val views = RemoteViews(context.packageName, R.layout.widget_lock_screen_monitor)
        // A launcher inflates this layout with the phone's own language, not the app's. So every
        // line is set from here with a context in the language the person chose in the app; the
        // text in the XML is only what shows before the first update.
        val text = LocaleManager.localizedContext(context)
        
        // Read data from SharedPreferences (stored by SystemMonitorService)
        val prefs = context.getSharedPreferences("lock_screen_widget_data", Context.MODE_PRIVATE)
        
        android.util.Log.d("DeviceGPT_Widget", "Reading widget data from SharedPreferences")
        
        // Speeds, CPU, FPS, power and the alert/CTA text are written ONLY by SystemMonitorService,
        // which never clears them when it stops. WidgetSnapshot refreshes last_update on its own,
        // so without this gate a widget showed weeks-old service readings next to "Just now".
        val serviceFresh = isServiceFresh(prefs.getLong(KEY_SERVICE_LAST_UPDATE, 0L), System.currentTimeMillis())
        fun svc(key: String, default: String): String =
            if (serviceFresh) prefs.getString(key, default) ?: default else default

        val battery = svc("battery", "🔋 Battery: --")
        val ram = prefs.getString("ram", "🧠 RAM: --") ?: "🧠 RAM: --"
        val cpu = svc("cpu", "")
        val download = svc("download", "📶 ↓ --")
        val upload = svc("upload", "↑ --")
        val latency = svc("latency", "")
        val power = svc("power", "⚡ Power: --")
        val thermal = svc("thermal", "🌡️ --")
        // No score yet is not a score of 0 ("Health: 0/10 · ⚠️ Low Score" right after the app
        // showed an Excellent result), and a score from an earlier day is not today's.
        val hasScore = scoreIsCurrent(
            hasStoredScore = prefs.contains("health_score"),
            scoreDay = prefs.getString(WidgetSnapshot.KEY_HEALTH_SCORE_DAY, null),
            today = WidgetSnapshot.today(),
            serviceFresh = serviceFresh
        )
        val healthScore = prefs.getInt("health_score", 0)
        val streak = if (serviceFresh) prefs.getInt("streak", 0) else 0
        val lastUpdate = prefs.getLong("last_update", 0)
        
        // Get battery percentage and charging status (stored separately for accuracy)
        val batteryPercent = prefs.getInt("battery_percent", -1)
        val isCharging = prefs.getBoolean("battery_charging", false)
        val isFull = prefs.getBoolean("battery_full", false)
        val chargingType = prefs.getString("charging_type", "") ?: ""
        
        // Factual status messages. Play policy 2026-07-10: stale prefs may still hold
        // pre-v3.2 "optimize" strings — sanitize on read so no banned word ever renders.
        val alertMessage = sanitizeLegacyVocab(svc("alert_message", ""), text.getString(R.string.mx_widget_cta_open))
        val openHealthCheck = text.getString(R.string.mx_widget_cta_open)
        val ctaMessage = sanitizeLegacyVocab(svc("cta_message", openHealthCheck), openHealthCheck)
        
        // Extract temperature - use stored value first, fallback to parsing thermal string
        val tempValue = prefs.getString("temperature_value", null) ?: try {
            // Fallback: try to extract from thermal string
            val pattern1 = Regex("🌡️[^:]*:\\s*([\\d.]+)°C")
            pattern1.find(thermal)?.groupValues?.get(1)
                ?: Regex("🔋[^:]*:\\s*([\\d.]+)°C").find(thermal)?.groupValues?.get(1)
                ?: Regex("([\\d.]+)°C").find(thermal)?.groupValues?.get(1)
                ?: "--"
        } catch (e: Exception) { "--" }
        
        val ramPercent = ramPercentFrom(ram)
        
        // MOST IMPORTANT: Health Score (Psychological Trigger #1)
        // v3.1.11 W2 user-behavior insight — color-code health score so the user's
        // eye lands on the most-important metric first. Same XML, just tinted at
        // runtime. RemoteViews.setTextColor via setInt + method name (Android's
        // RemoteViews API for cross-process View mutation).
        //
        // v3.2.0 R3 widget v2 (RC widget_v2_enabled, default OFF): delta-first
        // content in the SAME layout — score + trend arrow, alert line becomes
        // "what changed vs yesterday". Honest "no change" state is common and fine.
        val widgetV2 = try {
            com.teamz.lab.debugger.utils.RemoteConfigUtils.isWidgetV2Enabled()
        } catch (e: Exception) { false }
        // Record the arm actually served, so a Remote Config split on widget_v2_enabled can be
        // compared in GA4. Only writes when the resolved arm changes, not on every render.
        com.teamz.lab.debugger.utils.WidgetExperiment.stampIfChanged(context, widgetV2)
        var v2DeltaLine: String? = null
        var trendArrow = ""
        // Write today's snapshot UNCONDITIONALLY (dedup is inside the repository). This used
        // to sit inside the `if (widgetV2)` below, which made the only writer of
        // TYPE_BASELINE_SNAPSHOT rows conditional on a flag that is off — while
        // timeline_enabled is ON in production and DeviceTimelineSection renders exactly that
        // row type. The Timeline therefore had no daily health history to show, and flipping
        // widget_v2_enabled would have produced no visible delta for at least a day, because
        // the history it reads only starts accruing after the flip.
        // Only a score measured today (or live by the service): the repository keeps one row per
        // day, so recording yesterday's score at the first refresh after midnight dropped the
        // day's real scan, and a missing score was recorded as "Daily snapshot 0/10".
        try {
            if (hasScore) com.teamz.lab.debugger.db.DeviceEventsRepository.recordDailySnapshotIfDue(context, healthScore)
        } catch (e: Exception) {
            android.util.Log.w("DeviceGPT_Widget", "daily snapshot failed: ${e.message}")
        }
        if (widgetV2 && hasScore) {
            try {
                val (prev, last) = com.teamz.lab.debugger.db.DeviceEventsRepository.snapshotDeltaFromPrefs(context)
                if (prev in 0..10 && last in 0..10) {
                    val diff = last - prev
                    trendArrow = when {
                        diff > 0 -> " ↑"
                        diff < 0 -> " ↓"
                        else -> ""
                    }
                    v2DeltaLine = when {
                        diff > 0 -> text.getString(R.string.mx_widget_delta_up, diff.toString())
                        diff < 0 -> text.getString(R.string.mx_widget_delta_down, diff.toString())
                        else -> text.getString(R.string.mx_widget_delta_same)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("DeviceGPT_Widget", "widget v2 delta failed: ${e.message}")
            }
        }
        views.setTextViewText(R.id.widget_health_score, healthScoreLabel(text, hasScore, healthScore, trendArrow))
        val healthColor = when {
            !hasScore -> 0xFFAAAAAA.toInt()
            healthScore >= 7 -> 0xFFD9FE06.toInt()   // lime — original "good" color preserved
            healthScore >= 4 -> 0xFFFFC107.toInt()   // amber — caution
            healthScore >= 1 -> 0xFFFF6B6B.toInt()   // red-coral — needs attention
            else -> 0xFFAAAAAA.toInt()                // grey — no data yet
        }
        views.setInt(R.id.widget_health_score, "setTextColor", healthColor)
        views.setInt(R.id.widget_health_icon, "setColorFilter", healthColor)
        
        // Play policy 2026-07-10: streak row removed — FOMO mechanic on a utility
        // widget is manufactured urgency. View kept in XML for layout stability,
        // always rendered empty.
        views.setTextViewText(R.id.widget_streak, "")
        
        // Alert line. v2: the delta line takes priority — "what changed" is the
        // hook, not urgency. v1: factual alert from the monitor service.
        val alertLine = if (widgetV2 && v2DeltaLine != null) v2DeltaLine else alertMessage
        if (alertLine.isNotEmpty()) {
            // The stored line still starts with its emoji (data, and old installs have it saved): it picks
            // the row's icon and is never drawn. An "all normal" line is not an alarm, so it is not red.
            val alertIcon = RemoteIcons.forText(alertLine, R.drawable.ic_dg_warning)
            val alertColor = if (alertIcon == R.drawable.ic_dg_check) ALERT_CALM_COLOR else ALERT_COLOR
            views.setViewVisibility(R.id.widget_alert_row, android.view.View.VISIBLE)
            views.setImageViewResource(R.id.widget_alert_icon, alertIcon)
            views.setInt(R.id.widget_alert_icon, "setColorFilter", alertColor)
            views.setInt(R.id.widget_alert, "setTextColor", alertColor)
            views.setTextViewText(R.id.widget_alert, RemoteIcons.plain(alertLine))
        } else {
            views.setViewVisibility(R.id.widget_alert_row, android.view.View.GONE)
        }
        
        // Get additional info
        val storageUsedTotal = prefs.getString("storage_used_total", "---") ?: "---"
        val downloadSpeed = svc("download_speed", "---")
        val uploadSpeed = svc("upload_speed", "---")
        val hasInternet = serviceFresh && prefs.getBoolean("has_internet", false) // Get actual connectivity status
        val powerValue = try {
            power.substringAfter(":").substringBefore("W").trim().takeIf { it.isNotEmpty() } ?: "---"
        } catch (e: Exception) { "---" }
        
        // Key Metrics Row 1 (What Users Care About Most) - Clear labels for users
        val batteryDisplay = when {
            batteryPercent >= 0 -> {
                when {
                    isFull -> text.getString(R.string.mx_widget_battery, "100")
                    isCharging && chargingType.isNotEmpty() -> text.getString(
                        R.string.mx_widget_battery_charging_via, batteryPercent.toString(), chargerName(text, chargingType),
                    )
                    isCharging -> text.getString(R.string.mx_widget_battery_charging, batteryPercent.toString())
                    else -> text.getString(R.string.mx_widget_battery, batteryPercent.toString())
                }
            }
            else -> text.getString(R.string.mx_widget_battery_none)
        }
        views.setTextViewText(R.id.widget_battery, batteryDisplay)
        // v3.1.11 W2 — tint battery text red when ≤20% so user notices at a glance.
        // Same row in XML, no layout change.
        val batteryColor = when {
            batteryPercent in 1..20 -> 0xFFFF6B6B.toInt()   // red — critical
            batteryPercent in 21..35 -> 0xFFFFC107.toInt()  // amber — low
            else -> 0xFFFFFFFF.toInt()                       // white — normal (matches XML default)
        }
        views.setInt(R.id.widget_battery, "setTextColor", batteryColor)
        views.setInt(R.id.widget_battery_icon, "setColorFilter", batteryColor)
        
        // Temperature with clear label - show "---" on errors
        val tempDisplay = if (tempValue != "--" && tempValue.isNotEmpty()) {
            val temp = tempValue.toFloatOrNull() ?: 0f
            when {
                temp > 0f -> {
                    when {
                        temp > 45f -> text.getString(R.string.mx_widget_temp_hot, temp.toInt().toString())
                        temp > 40f -> text.getString(R.string.mx_widget_temp_warm, temp.toInt().toString())
                        else -> text.getString(R.string.mx_widget_temp, temp.toInt().toString())
                    }
                }
                else -> text.getString(R.string.mx_widget_temp_none)
            }
        } else {
            text.getString(R.string.mx_widget_temp_none)
        }
        views.setTextViewText(R.id.widget_thermal, tempDisplay)
        // v3.1.11 W2 — tint temperature red when overheating (>45°C) so the warning
        // catches the eye even without reading the "Hot!" suffix.
        val tempNum = tempValue.toFloatOrNull() ?: 0f
        val tempColor = when {
            tempNum > 45f -> 0xFFFF6B6B.toInt()   // red — overheating
            tempNum > 40f -> 0xFFFFC107.toInt()   // amber — warm
            else -> 0xFFFFFFFF.toInt()             // white — normal
        }
        views.setInt(R.id.widget_thermal, "setTextColor", tempColor)
        views.setInt(R.id.widget_thermal_icon, "setColorFilter", tempColor)

        // RAM with clear label - show "---" on errors
        views.setTextViewText(
            R.id.widget_ram,
            if (ramPercent != "--" && ramPercent.isNotEmpty()) {
                text.getString(R.string.mx_widget_ram, ramPercent)
            } else {
                text.getString(R.string.mx_widget_ram_none)
            },
        )
        
        // Key Metrics Row 2 (Additional Info) - Show storage in used/total format
        views.setTextViewText(R.id.widget_storage, text.getString(R.string.mx_widget_disk, storageUsedTotal))
        
        // Beautify network - show speed like system monitor (download ↓ • upload ↑)
        val networkDisplay = when {
            !hasInternet ->
                text.getString(R.string.mx_widget_net_none)
            downloadSpeed == "---" && uploadSpeed == "---" ->
                text.getString(R.string.mx_widget_net_none)
            downloadSpeed != "---" && uploadSpeed != "---" ->
                text.getString(R.string.mx_widget_net_both, downloadSpeed, uploadSpeed)
            downloadSpeed != "---" ->
                text.getString(R.string.mx_widget_net_down, downloadSpeed)
            uploadSpeed != "---" ->
                text.getString(R.string.mx_widget_net_up, uploadSpeed)
            else ->
                text.getString(R.string.mx_widget_net_none)
        }
        views.setTextViewText(R.id.widget_network, networkDisplay)
        
        views.setTextViewText(
            R.id.widget_power,
            if (powerValue != "---") {
                text.getString(R.string.mx_widget_drain, powerValue)
            } else {
                text.getString(R.string.mx_widget_drain_none)
            },
        )
        
        // Key Metrics Row 3 (CPU & FPS) - show "---" on errors
        // Extract CPU percentage from cpu string (format: "CPU: X.XX / Y.YY GHz (Z%) • N active cores")
        val cpuPercent = try {
            val cpuPercentMatch = Regex("\\((\\d+)%\\)").find(cpu)
            cpuPercentMatch?.groupValues?.get(1) ?: "---"
        } catch (e: Exception) { "---" }
        views.setTextViewText(
            R.id.widget_cpu,
            if (cpuPercent != "---" && cpuPercent.isNotEmpty()) {
                text.getString(R.string.mx_widget_cpu, cpuPercent)
            } else {
                text.getString(R.string.mx_widget_cpu_none)
            },
        )
        
        // Extract FPS from fps_data (format: "FPS: 59 • Drop Rate: 1.0%")
        val fpsData = svc("fps_data", "")
        val fpsValue = try {
            val fpsMatch = Regex("FPS:\\s*(\\d+)").find(fpsData)
            fpsMatch?.groupValues?.get(1) ?: "---"
        } catch (e: Exception) { "---" }
        views.setTextViewText(
            R.id.widget_fps,
            text.getString(R.string.mx_widget_fps, if (fpsValue != "---" && fpsValue.isNotEmpty()) fpsValue else "---"),
        )
        
        // Compelling CTA (Click Trigger)
        // The layout draws the "go" arrow as an icon, so the typed one (also in text saved earlier) goes.
        views.setTextViewText(R.id.widget_cta, RemoteIcons.plain(ctaMessage))
        
        // Status indicator in Row 3 - show most meaningful actionable info
        // When alert is showing primary issue, show secondary critical info to avoid redundancy
        val statusText = if (alertMessage.isNotEmpty()) {
            // Alert is showing primary issue, so show secondary/other critical info
            // Check in priority order and return first match, or fallback to general status
            var secondaryStatus: String? = null
            
            // Priority 1: Check storage space (important secondary metric)
            if (secondaryStatus == null && storageUsedTotal != "---") {
                try {
                    val storageMatch = Regex("(\\d+)/(\\d+)").find(storageUsedTotal)
                    if (storageMatch != null) {
                        val usedGB = storageMatch.groupValues[1].toIntOrNull() ?: 0
                        val totalGB = storageMatch.groupValues[2].toIntOrNull() ?: 1
                        val storagePercent = if (totalGB > 0) (usedGB * 100) / totalGB else 0
                        secondaryStatus = when {
                            storagePercent > 90 -> text.getString(R.string.mx_widget_status_low_space)
                            storagePercent > 80 -> text.getString(R.string.mx_widget_status_storage_full)
                            else -> null
                        }
                    }
                } catch (e: Exception) { /* Continue to next check */ }
            }
            
            // Priority 2: Check power drain (important secondary metric)
            if (secondaryStatus == null && powerValue != "---") {
                try {
                    val drain = powerValue.toFloatOrNull() ?: 0f
                    secondaryStatus = when {
                        drain > 10f -> text.getString(R.string.mx_widget_status_high_drain)
                        drain > 7f -> text.getString(R.string.mx_widget_status_high_power)
                        else -> null
                    }
                } catch (e: Exception) { /* Continue to next check */ }
            }
            
            // Priority 3: Check network quality
            if (secondaryStatus == null) {
                secondaryStatus = when {
                    !hasInternet -> text.getString(R.string.mx_widget_status_no_net)
                    downloadSpeed != "---" -> {
                        try {
                            val speed = downloadSpeed.toFloatOrNull() ?: 0f
                            if (speed < 0.5f) text.getString(R.string.mx_widget_status_slow_net) else null
                        } catch (e: Exception) { null }
                    }
                    else -> null
                }
            }
            
            // Fallback: Always show something meaningful - positive status or monitoring status
            // This ensures the field is never empty
            secondaryStatus ?: text.getString(
                when {
                    !hasScore -> R.string.mx_widget_status_open_app
                    healthScore >= 8 -> R.string.mx_widget_status_good_check
                    healthScore >= 7 -> R.string.mx_widget_status_ok
                    else -> R.string.mx_widget_status_watching
                }
            )
        } else {
            // No alert shown, so show primary status here
            primaryStatus(text, hasScore, tempValue, ramPercent, healthScore)
        }
        // The status words keep their leading emoji as data; it picks the cell's icon and is not drawn.
        views.setImageViewResource(
            R.id.widget_status_icon,
            RemoteIcons.forText(statusText, R.drawable.ic_dg_bar_chart),
        )
        views.setTextViewText(R.id.widget_status, RemoteIcons.plain(statusText))
        
        // Update time in bottom right (user-friendly format)
        val timeAgo = if (lastUpdate > 0) {
            val secondsAgo = (System.currentTimeMillis() - lastUpdate) / 1000
            when {
                secondsAgo < 5 -> text.getString(R.string.mx_widget_time_just_now)
                secondsAgo < 60 -> text.getString(R.string.mx_widget_time_seconds, secondsAgo.toString())
                secondsAgo < 120 -> text.getString(R.string.mx_widget_time_one_minute)
                secondsAgo < 3600 -> text.getString(R.string.mx_widget_time_minutes, (secondsAgo / 60).toString())
                secondsAgo < 7200 -> text.getString(R.string.mx_widget_time_one_hour)
                else -> text.getString(R.string.mx_widget_time_hours, (secondsAgo / 3600).toString())
            }
        } else {
            text.getString(R.string.mx_widget_time_starting)
        }
        views.setTextViewText(R.id.widget_last_update, timeAgo)
        
        // Get widget action (what to do when tapped)
        val widgetAction = svc("widget_action", "")
        
        // Set click intent to open app directly to Health section (quick action)
        // Use dynamic navigation by tab name instead of index to handle tab order changes
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Use tab name instead of index for dynamic navigation
            putExtra("navigate_to_tab", "health")
            putExtra("source", "lock_screen_widget")
            // Add analytics tracking flag
            putExtra("track_widget_tap", true)
            // Add widget action (e.g., clear_ram)
            if (widgetAction.isNotEmpty()) {
                putExtra("widget_action", widgetAction)
            }
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        // Make entire widget clickable - opens Health section directly
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        
        // Track widget update
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.WidgetUpdated, mapOf(
                    "health_score" to healthScore,
                    "streak" to streak,
                    "battery_percent" to batteryPercent,
                    "is_charging" to isCharging,
                    "data_age_seconds" to if (lastUpdate > 0) ((System.currentTimeMillis() - lastUpdate) / 1000).toInt() else -1
                )
            )
        } catch (e: Exception) {
            // Analytics failure shouldn't break widget
            android.util.Log.w("DeviceGPT_Widget", "Failed to log analytics", e)
        }
        
        // Update widget
        try {
            appWidgetManager.updateAppWidget(appWidgetId, views)
            android.util.Log.d("DeviceGPT_Widget", "Widget $appWidgetId updated successfully. Health: $healthScore/10, Streak: $streak days")
        } catch (e: Exception) {
            android.util.Log.e("DeviceGPT_Widget", "Error updating widget $appWidgetId", e)
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.teamz.lab.debugger.UPDATE_LOCK_SCREEN_WIDGET"

        /** Alert row: the same red-coral as the layout default, and the calm green for "all normal". */
        private const val ALERT_COLOR = 0xFFFF6B6B.toInt()
        private const val ALERT_CALM_COLOR = 0xFF34D399.toInt()

        /**
         * Play policy 2026-07-10: prefs written by a pre-v3.2 APK can still contain
         * banned "optimize" vocabulary after upgrade. Sanitize on read — the new
         * writer never produces these, so this only fires once per upgrade window.
         */
        fun sanitizeLegacyVocab(text: String, replacement: String = "Open health check →"): String {
            if (text.isEmpty()) return text
            val banned = listOf("optimize", "optimized", "boost", "clean up", "speed up", "junk", "free up ram")
            val lower = text.lowercase()
            return if (banned.any { lower.contains(it) }) replacement else text
        }

        /**
         * Trigger widget update from SystemMonitorService
         */
        internal const val KEY_SERVICE_LAST_UPDATE = "service_last_update"
        /** SystemMonitorService writes every 30 s; older than this, its readings are not current. */
        internal const val SERVICE_FRESH_MS = 5 * 60_000L

        internal fun isServiceFresh(serviceLastUpdate: Long, now: Long): Boolean =
            serviceLastUpdate > 0 && now - serviceLastUpdate in 0..SERVICE_FRESH_MS

        internal fun scoreIsCurrent(hasStoredScore: Boolean, scoreDay: String?, today: String, serviceFresh: Boolean): Boolean =
            hasStoredScore && (serviceFresh || scoreDay == today)

        /** [text] is a context in the app language; see `LocaleManager.localizedContext`. */
        internal fun primaryStatus(
            text: Context,
            hasScore: Boolean,
            tempValue: String,
            ramPercent: String,
            healthScore: Int,
        ): String = text.getString(
            when {
                !hasScore -> R.string.mx_widget_status_open_app
                // Critical issues (highest priority)
                tempValue != "--" && (tempValue.toFloatOrNull() ?: 0f) > 45f -> R.string.mx_widget_status_hot
                ramPercent != "--" && (ramPercent.toIntOrNull() ?: 0) > 85 -> R.string.mx_widget_status_high_memory
                healthScore < 5 -> R.string.mx_widget_status_low_score
                // Warnings (medium priority)
                tempValue != "--" && (tempValue.toFloatOrNull() ?: 0f) > 40f -> R.string.mx_widget_status_warm
                ramPercent != "--" && (ramPercent.toIntOrNull() ?: 0) > 70 -> R.string.mx_widget_status_high_usage
                healthScore < 7 -> R.string.mx_widget_status_below_normal
                // Positive status (when everything is good)
                healthScore >= 8 -> R.string.mx_widget_status_healthy
                else -> R.string.mx_widget_status_good
            }
        )

        /** The stored charger type ("AC", "USB", "Wireless") is data; this is the word shown for it. */
        internal fun chargerName(text: Context, chargingType: String): String = when (chargingType) {
            "AC" -> text.getString(R.string.mx_widget_charger_ac)
            "USB" -> text.getString(R.string.mx_widget_charger_usb)
            "Wireless" -> text.getString(R.string.mx_widget_charger_wireless)
            else -> chargingType
        }

        /** "🧠 RAM: 5468 MB / 7572 MB (72%)" -> "72"; anything without "(N%)" -> "--". */
        internal fun ramPercentFrom(ram: String): String =
            Regex("\\((\\d+)%\\)").find(ram)?.groupValues?.get(1) ?: "--"

        internal fun healthScoreLabel(text: Context, hasScore: Boolean, score: Int, trendArrow: String): String =
            if (hasScore) {
                text.getString(R.string.mx_widget_health, score.toString(), trendArrow)
            } else {
                text.getString(R.string.mx_widget_health_none)
            }

        fun updateWidget(context: Context) {
            android.util.Log.d("DeviceGPT_Widget", "Triggering widget update from SystemMonitorService")
            try {
                val intent = Intent(context, LockScreenMonitorWidget::class.java).apply {
                    action = ACTION_UPDATE_WIDGET
                }
                context.sendBroadcast(intent)
                android.util.Log.d("DeviceGPT_Widget", "Widget update broadcast sent successfully")
            } catch (e: Exception) {
                android.util.Log.e("DeviceGPT_Widget", "Error sending widget update broadcast", e)
            }
        }
    }
}

