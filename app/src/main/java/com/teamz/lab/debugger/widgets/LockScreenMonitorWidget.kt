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
        
        // Read data from SharedPreferences (stored by SystemMonitorService)
        val prefs = context.getSharedPreferences("lock_screen_widget_data", Context.MODE_PRIVATE)
        
        android.util.Log.d("DeviceGPT_Widget", "Reading widget data from SharedPreferences")
        
        val battery = prefs.getString("battery", "🔋 Battery: --") ?: "🔋 Battery: --"
        val ram = prefs.getString("ram", "🧠 RAM: --") ?: "🧠 RAM: --"
        val cpu = prefs.getString("cpu", "") ?: ""
        val download = prefs.getString("download", "📶 ↓ --") ?: "📶 ↓ --"
        val upload = prefs.getString("upload", "↑ --") ?: "↑ --"
        val latency = prefs.getString("latency", "") ?: ""
        val power = prefs.getString("power", "⚡ Power: --") ?: "⚡ Power: --"
        val thermal = prefs.getString("thermal", "🌡️ --") ?: "🌡️ --"
        val healthScore = prefs.getInt("health_score", 0)
        val streak = prefs.getInt("streak", 0)
        val lastUpdate = prefs.getLong("last_update", 0)
        
        // Get battery percentage and charging status (stored separately for accuracy)
        val batteryPercent = prefs.getInt("battery_percent", -1)
        val isCharging = prefs.getBoolean("battery_charging", false)
        val isFull = prefs.getBoolean("battery_full", false)
        val chargingType = prefs.getString("charging_type", "") ?: ""
        
        // Factual status messages. Play policy 2026-07-10: stale prefs may still hold
        // pre-v3.2 "optimize" strings — sanitize on read so no banned word ever renders.
        val alertMessage = sanitizeLegacyVocab(prefs.getString("alert_message", "") ?: "")
        val ctaMessage = sanitizeLegacyVocab(
            prefs.getString("cta_message", "Open health check →") ?: "Open health check →"
        )
        
        // Extract temperature - use stored value first, fallback to parsing thermal string
        val tempValue = prefs.getString("temperature_value", null) ?: try {
            // Fallback: try to extract from thermal string
            val pattern1 = Regex("🌡️[^:]*:\\s*([\\d.]+)°C")
            pattern1.find(thermal)?.groupValues?.get(1)
                ?: Regex("🔋[^:]*:\\s*([\\d.]+)°C").find(thermal)?.groupValues?.get(1)
                ?: Regex("([\\d.]+)°C").find(thermal)?.groupValues?.get(1)
                ?: "--"
        } catch (e: Exception) { "--" }
        
        val ramPercent = try {
            ram.substringAfter("(").substringBefore("%)").trim().takeIf { it.isNotEmpty() } ?: "--"
        } catch (e: Exception) { "--" }
        
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
        var v2DeltaLine: String? = null
        var trendArrow = ""
        if (widgetV2) {
            try {
                // Write today's snapshot if due (dedup inside), then read prefs-cached delta.
                com.teamz.lab.debugger.db.DeviceEventsRepository.recordDailySnapshotIfDue(context, healthScore)
                val (prev, last) = com.teamz.lab.debugger.db.DeviceEventsRepository.snapshotDeltaFromPrefs(context)
                if (prev in 0..10 && last in 0..10) {
                    val diff = last - prev
                    trendArrow = when {
                        diff > 0 -> " ↑"
                        diff < 0 -> " ↓"
                        else -> ""
                    }
                    v2DeltaLine = when {
                        diff > 0 -> "Health +$diff vs yesterday"
                        diff < 0 -> "Health $diff vs yesterday — see what changed"
                        else -> "Nothing changed — all steady"
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("DeviceGPT_Widget", "widget v2 delta failed: ${e.message}")
            }
        }
        views.setTextViewText(R.id.widget_health_score, "Health: $healthScore/10$trendArrow")
        val healthColor = when {
            healthScore >= 7 -> 0xFFD9FE06.toInt()   // lime — original "good" color preserved
            healthScore >= 4 -> 0xFFFFC107.toInt()   // amber — caution
            healthScore >= 1 -> 0xFFFF6B6B.toInt()   // red-coral — needs attention
            else -> 0xFFAAAAAA.toInt()                // grey — no data yet
        }
        views.setInt(R.id.widget_health_score, "setTextColor", healthColor)
        
        // Play policy 2026-07-10: streak row removed — FOMO mechanic on a utility
        // widget is manufactured urgency. View kept in XML for layout stability,
        // always rendered empty.
        views.setTextViewText(R.id.widget_streak, "")
        
        // Alert line. v2: the delta line takes priority — "what changed" is the
        // hook, not urgency. v1: factual alert from the monitor service.
        val alertLine = if (widgetV2 && v2DeltaLine != null) v2DeltaLine else alertMessage
        if (alertLine.isNotEmpty()) {
            views.setViewVisibility(R.id.widget_alert, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_alert, alertLine)
        } else {
            views.setViewVisibility(R.id.widget_alert, android.view.View.GONE)
        }
        
        // Get additional info
        val storageUsedTotal = prefs.getString("storage_used_total", "---") ?: "---"
        val downloadSpeed = prefs.getString("download_speed", "---") ?: "---"
        val uploadSpeed = prefs.getString("upload_speed", "---") ?: "---"
        val hasInternet = prefs.getBoolean("has_internet", false) // Get actual connectivity status
        val powerValue = try {
            power.substringAfter(":").substringBefore("W").trim().takeIf { it.isNotEmpty() } ?: "---"
        } catch (e: Exception) { "---" }
        
        // Key Metrics Row 1 (What Users Care About Most) - Clear labels for users
        val batteryDisplay = when {
            batteryPercent >= 0 -> {
                when {
                    isFull -> "Battery: 100%"
                    isCharging && chargingType.isNotEmpty() -> "Battery: $batteryPercent% (Charging via $chargingType)"
                    isCharging -> "Battery: $batteryPercent% (Charging)"
                    else -> "Battery: $batteryPercent%"
                }
            }
            else -> "Battery: ---"
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
        
        // Temperature with clear label - show "---" on errors
        val tempDisplay = if (tempValue != "--" && tempValue.isNotEmpty()) {
            val temp = tempValue.toFloatOrNull() ?: 0f
            when {
                temp > 0f -> {
                    when {
                        temp > 45f -> "Temp: ${temp.toInt()}°C (Hot!)"
                        temp > 40f -> "Temp: ${temp.toInt()}°C (Warm)"
                        else -> "Temp: ${temp.toInt()}°C"
                    }
                }
                else -> "Temp: ---"
            }
        } else {
            "Temp: ---"
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

        // RAM with clear label - show "---" on errors
        views.setTextViewText(R.id.widget_ram, "RAM: ${if (ramPercent != "--" && ramPercent.isNotEmpty()) "${ramPercent}%" else "---"}")
        
        // Key Metrics Row 2 (Additional Info) - Show storage in used/total format
        views.setTextViewText(R.id.widget_storage, "Disk: $storageUsedTotal")
        
        // Beautify network - show speed like system monitor (download ↓ • upload ↑)
        val networkDisplay = when {
            !hasInternet -> 
                "Internet: ---"
            downloadSpeed == "---" && uploadSpeed == "---" -> 
                "Internet: ---"
            downloadSpeed != "---" && uploadSpeed != "---" -> 
                "Internet: $downloadSpeed ↓ • $uploadSpeed ↑"
            downloadSpeed != "---" -> 
                "Internet: $downloadSpeed ↓"
            uploadSpeed != "---" -> 
                "Internet: $uploadSpeed ↑"
            else -> 
                "Internet: ---"
        }
        views.setTextViewText(R.id.widget_network, networkDisplay)
        
        views.setTextViewText(R.id.widget_power, "Drain: ${if (powerValue != "---") "${powerValue}W" else "---"}")
        
        // Key Metrics Row 3 (CPU & FPS) - show "---" on errors
        // Extract CPU percentage from cpu string (format: "CPU: X.XX / Y.YY GHz (Z%) • N active cores")
        val cpuPercent = try {
            val cpuPercentMatch = Regex("\\((\\d+)%\\)").find(cpu)
            cpuPercentMatch?.groupValues?.get(1) ?: "---"
        } catch (e: Exception) { "---" }
        views.setTextViewText(R.id.widget_cpu, "CPU: ${if (cpuPercent != "---" && cpuPercent.isNotEmpty()) "${cpuPercent}%" else "---"}")
        
        // Extract FPS from fps_data (format: "FPS: 59 • Drop Rate: 1.0%")
        val fpsData = prefs.getString("fps_data", "") ?: ""
        val fpsValue = try {
            val fpsMatch = Regex("FPS:\\s*(\\d+)").find(fpsData)
            fpsMatch?.groupValues?.get(1) ?: "---"
        } catch (e: Exception) { "---" }
        views.setTextViewText(R.id.widget_fps, "FPS: ${if (fpsValue != "---" && fpsValue.isNotEmpty()) fpsValue else "---"}")
        
        // Compelling CTA (Click Trigger)
        views.setTextViewText(R.id.widget_cta, ctaMessage)
        
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
                            storagePercent > 90 -> "💾 Low Space"
                            storagePercent > 80 -> "💾 Storage Full"
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
                        drain > 10f -> "⚡ High Drain"
                        drain > 7f -> "⚡ High Power"
                        else -> null
                    }
                } catch (e: Exception) { /* Continue to next check */ }
            }
            
            // Priority 3: Check network quality
            if (secondaryStatus == null) {
                secondaryStatus = when {
                    !hasInternet -> "📶 No Net"
                    downloadSpeed != "---" -> {
                        try {
                            val speed = downloadSpeed.toFloatOrNull() ?: 0f
                            if (speed < 0.5f) "📶 Slow Net" else null
                        } catch (e: Exception) { null }
                    }
                    else -> null
                }
            }
            
            // Fallback: Always show something meaningful - positive status or monitoring status
            // This ensures the field is never empty
            secondaryStatus ?: when {
                healthScore >= 8 -> "✅ Good"
                healthScore >= 7 -> "📊 OK"
                else -> "📊 Monitoring"
            }
        } else {
            // No alert shown, so show primary status here
            when {
                // Critical issues (highest priority)
                tempValue != "--" && tempValue.toFloatOrNull() ?: 0f > 45f -> "🌡️ Hot"
                ramPercent != "--" && ramPercent.toIntOrNull() ?: 0 > 85 -> "📊 High Memory"
                healthScore < 5 -> "⚠️ Low Score"
                // Warnings (medium priority)
                tempValue != "--" && tempValue.toFloatOrNull() ?: 0f > 40f -> "🌡️ Warm"
                ramPercent != "--" && ramPercent.toIntOrNull() ?: 0 > 70 -> "📊 High Usage"
                healthScore < 7 -> "📉 Below Normal"
                // Positive status (when everything is good)
                healthScore >= 8 -> "✅ Healthy"
                else -> "📊 Good"
            }
        }
        views.setTextViewText(R.id.widget_status, statusText)
        
        // Update time in bottom right (user-friendly format)
        val timeAgo = if (lastUpdate > 0) {
            val secondsAgo = (System.currentTimeMillis() - lastUpdate) / 1000
            when {
                secondsAgo < 5 -> "Just now"
                secondsAgo < 60 -> "${secondsAgo}s ago"
                secondsAgo < 120 -> "1 min ago"
                secondsAgo < 3600 -> "${secondsAgo / 60} mins ago"
                secondsAgo < 7200 -> "1 hr ago"
                else -> "${secondsAgo / 3600} hrs ago"
            }
        } else {
            "Initializing"
        }
        views.setTextViewText(R.id.widget_last_update, timeAgo)
        
        // Get widget action (what to do when tapped)
        val widgetAction = prefs.getString("widget_action", "") ?: ""
        
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

        /**
         * Play policy 2026-07-10: prefs written by a pre-v3.2 APK can still contain
         * banned "optimize" vocabulary after upgrade. Sanitize on read — the new
         * writer never produces these, so this only fires once per upgrade window.
         */
        fun sanitizeLegacyVocab(text: String): String {
            if (text.isEmpty()) return text
            val banned = listOf("optimize", "optimized", "boost", "clean up", "speed up", "junk", "free up ram")
            val lower = text.lowercase()
            return if (banned.any { lower.contains(it) }) "Open health check →" else text
        }

        /**
         * Trigger widget update from SystemMonitorService
         */
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

