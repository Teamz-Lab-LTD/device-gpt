package com.teamz.lab.debugger.utils

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit
import com.teamz.lab.debugger.R

object HealthScoreUtils {
    private const val PREFS_NAME = "health_score_prefs"
    private const val KEY_LAST_SCAN_DATE = "last_scan_date"
    private const val KEY_HEALTH_SCORE_HISTORY = "health_score_history"
    private const val KEY_DAILY_STREAK = "daily_streak"
    private const val KEY_BEST_SCORE = "best_score"
    private const val KEY_TOTAL_SCANS = "total_scans"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    suspend fun calculateDailyHealthScore(context: Context): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // Battery and storage used to be scored by matching words in display text, and the
        // words never matched ("Good ✅" is not "good"), so every phone lost 2 points it had
        // not earned. Read the structured values the display text was built from instead.
        val batteryHealth = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        } catch (_: Exception) { -1 }

        val storageFreePct = try {
            val stat = StatFs(Environment.getDataDirectory().path)
            if (stat.totalBytes > 0L) (stat.availableBytes * 100 / stat.totalBytes).toInt() else null
        } catch (_: Exception) { null }

        // Security used to deduct for any ❌/⚠️ in the Security tab text. Most of those lines
        // are not faults: "No admin set" is the normal state of a personal phone, SELinux is
        // unreadable by apps, /etc/hosts exists on every device. The one real exposure in that
        // block is unencrypted storage, read here directly. SecurityInfoCache is warmed by
        // Application start-up and the Health tab, not here: the full security scan (getenforce,
        // two package scans) used to sit in front of the first scan's score for nothing.
        val storageUnencrypted = try {
            (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager)
                ?.storageEncryptionStatus == DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE
        } catch (_: Exception) { false }

        scoreFromSignals(
            batteryHealth = batteryHealth,
            storageFreePct = storageFreePct,
            thermalStatus = getThermalZoneTemperatures(context),
            ramUsage = getRamUsage(context),
            storageUnencrypted = storageUnencrypted,
            rooted = isDeviceRooted().let { it.contains("Yes") || it.contains("Rooted") },
            usbDebugging = isUsbDebuggingEnabled(context).let { it.contains("enabled") || it.contains("Enabled") },
        )
    }

    /**
     * Pure scoring, so every deduction can be tested. A deduction needs a fault; an unreadable
     * value is an unknown and costs nothing — the old `else -> 1` turned "could not read" into
     * "something is wrong", which is how a healthy phone ended up "Fair".
     */
    internal fun scoreFromSignals(
        batteryHealth: Int,
        storageFreePct: Int?,
        thermalStatus: String,
        ramUsage: String,
        storageUnencrypted: Boolean,
        rooted: Boolean,
        usbDebugging: Boolean,
    ): Int {
        var score = 10

        // Battery Health (0-3 points)
        score -= when (batteryHealth) {
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> 3
            BatteryManager.BATTERY_HEALTH_DEAD,
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE,
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> 2
            BatteryManager.BATTERY_HEALTH_COLD -> 1
            else -> 0 // GOOD, UNKNOWN, unreadable
        }

        // Storage headroom (0-2 points) — same bands as FirstScanGate's storage sub-score
        score -= when {
            storageFreePct == null -> 0
            storageFreePct >= 15 -> 0
            storageFreePct >= 10 -> 1
            else -> 2
        }

        // Thermal Status (0-2 points)
        when {
            thermalStatus.contains("cool") || thermalStatus.contains("normal") -> score -= 0
            thermalStatus.contains("warm") -> score -= 1
            thermalStatus.contains("hot") || thermalStatus.contains("overheating") -> score -= 2
        }

        // RAM Usage (0-1 point)
        if (ramUsage.contains("high") || ramUsage.contains("critical")) {
            score -= 1
        }

        // Security (0-2 points)
        if (storageUnencrypted) score -= 2

        // Rooted device (major security risk)
        if (rooted) score -= 2

        // USB debugging enabled (security risk)
        if (usbDebugging) score -= 1

        return maxOf(1, score) // Minimum score of 1
    }

    fun saveHealthScore(context: Context, score: Int) {
        val prefs = getPrefs(context)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        // Get the previous scan date BEFORE updating it
        val previousScanDate = prefs.getString(KEY_LAST_SCAN_DATE, "") ?: ""
        
        // Save today's score
        prefs.edit {
            putInt("score_$today", score)
                .putString(KEY_LAST_SCAN_DATE, today)
        }

        // Update streak with the previous scan date
        updateDailyStreak(context, today, previousScanDate)
        
        // Update best score
        val bestScore = prefs.getInt(KEY_BEST_SCORE, 0)
        if (score > bestScore) {
            prefs.edit { putInt(KEY_BEST_SCORE, score) }
        }

        // Total scans are now calculated from actual history, no need to increment counter

        // The home-screen widget reads its own prefs; without this it showed 0/10 to everyone
        // who had not turned on the monitor service. See WidgetSnapshot.
        com.teamz.lab.debugger.widgets.WidgetSnapshot.writeAsync(context, score)
    }

    fun getLastScanDate(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_SCAN_DATE, "") ?: ""
    }

    fun getDailyStreak(context: Context): Int {
        // Recalculate streak based on actual history to ensure accuracy
        return calculateCurrentStreak(context)
    }

    fun getBestScore(context: Context): Int {
        // Recalculate best score from history to ensure accuracy
        return calculateBestScoreFromHistory(context)
    }

    fun getTotalScans(context: Context): Int {
        // Calculate total scans from actual history to ensure accuracy
        return calculateTotalScansFromHistory(context)
    }

    fun getHealthScoreHistory(context: Context, days: Int = 7): List<Pair<String, Int>> {
        val prefs = getPrefs(context)
        val history = mutableListOf<Pair<String, Int>>()
        val calendar = Calendar.getInstance()
        
        for (i in 0 until days) {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            val score = prefs.getInt("score_$date", -1)
            if (score != -1) {
                history.add(Pair(date, score))
            }
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        
        return history.reversed()
    }

    /**
     * Calculate total scans from actual scan history
     */
    private fun calculateTotalScansFromHistory(context: Context): Int {
        val prefs = getPrefs(context)
        val allKeys = prefs.all.keys
        val scanKeys = allKeys.filter { it.startsWith("score_") }
        return scanKeys.size
    }

    /**
     * Calculate current streak based on actual scan history
     */
    private fun calculateCurrentStreak(context: Context): Int {
        val history = getHealthScoreHistory(context, 30) // Check last 30 days
        if (history.isEmpty()) return 0
        
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val calendar = Calendar.getInstance()
        
        var streak = 0
        var currentDate = today
        
        // Check consecutive days starting from today going backwards
        // This ensures the streak increases day by day as long as user scans daily
        while (true) {
            val hasScannedToday = history.any { it.first == currentDate }
            if (hasScannedToday) {
                streak++
                // Move to previous day
                try {
                    val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(currentDate)
                    if (parsedDate != null) {
                        calendar.time = parsedDate
                        calendar.add(Calendar.DAY_OF_YEAR, -1)
                        currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                    } else {
                        break
                    }
                } catch (e: Exception) {
                    break
                }
            } else {
                // No scan found for this day, streak ends here
                break
            }
        }
        
        return streak
    }

    /**
     * Calculate best score from actual scan history
     */
    private fun calculateBestScoreFromHistory(context: Context): Int {
        val history = getHealthScoreHistory(context, 365) // Check last year
        if (history.isEmpty()) return 0
        
        return history.maxOfOrNull { it.second } ?: 0
    }

    private fun updateDailyStreak(context: Context, today: String, previousScanDate: String) {
        val prefs = getPrefs(context)
        
        if (previousScanDate == today) {
            // Already scanned today, don't update streak
            return
        }
        
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        
        if (previousScanDate == yesterday) {
            // Consecutive day
            prefs.edit { putInt(KEY_DAILY_STREAK, prefs.getInt(KEY_DAILY_STREAK, 0) + 1) }
        } else {
            // Break in streak
            prefs.edit { putInt(KEY_DAILY_STREAK, 1) }
        }
    }

    fun getHealthScoreMessage(score: Int): String {
        return when {
            score >= 9 -> "Excellent! Your device is in top condition!"
            score >= 7 -> "Good! Your device is performing well."
            score >= 5 -> "Fair. Some improvements needed - see recommendations below."
            else -> "Needs attention. Check recommendations below to improve your device."
        }
    }

    // Play policy 2026-07-10 + research insight #7: streak gamification removed.
    // Factual scan-history line instead — no FOMO, no manufactured habit pressure.
    fun getStreakMessage(streak: Int): String {
        return when {
            streak >= 1 -> "📱 Scanned $streak day${if (streak > 1) "s" else ""} in a row."
            else -> "🔍 Run a scan to see today's device state."
        }
    }

    // Get intelligent improvement suggestions based on actual device data
    fun getImprovementSuggestions(context: Context, score: Int): List<String> {
        val suggestions = mutableListOf<String>()
        
        // Analyze battery status - REAL device data analysis
        val batteryInfo = getBatteryChargingInfo(context)
        when {
            batteryInfo.contains("Overheat") || batteryInfo.contains("Overvoltage") -> {
                suggestions.add(context.string(R.string.mx_tip_hot_cool_down))
                suggestions.add(context.string(R.string.mx_tip_hot_no_charge))
                suggestions.add(context.string(R.string.mx_tip_hot_cool_place))
            }
            batteryInfo.contains("Failure") || batteryInfo.contains("Dead") -> {
                suggestions.add(context.string(R.string.mx_tip_dead_replace))
                suggestions.add(context.string(R.string.mx_tip_dead_saver))
                suggestions.add(context.string(R.string.mx_tip_dead_brightness))
            }
            batteryInfo.contains("Battery Full") -> {
                suggestions.add(context.string(R.string.mx_tip_full_unplug))
                suggestions.add(context.string(R.string.mx_tip_full_range))
            }
            batteryInfo.contains("Cold") -> {
                suggestions.add(context.string(R.string.mx_tip_cold_warm_up))
                suggestions.add(context.string(R.string.mx_tip_cold_car))
            }
        }
        
        // Analyze storage status - REAL device data analysis
        val storageInfo = getMemoryAndStorageInfo(context)
        when {
            storageInfo.contains("GB") -> {
                val storageMatch = Regex("(\\d+\\.?\\d*)\\s*GB").find(storageInfo)
                val availableGB = storageMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                when {
                    availableGB < 2f -> {
                        suggestions.add(context.string(R.string.mx_tip_storage_almost_full))
                        suggestions.add(context.string(R.string.mx_tip_storage_google_photos))
                        suggestions.add(context.string(R.string.mx_tip_storage_delete_apps))
                        suggestions.add(context.string(R.string.mx_tip_storage_clear_cache))
                    }
                    availableGB < 5f -> {
                        suggestions.add(context.string(R.string.mx_tip_storage_getting_full, "${availableGB}GB"))
                        suggestions.add(context.string(R.string.mx_tip_storage_cloud))
                        suggestions.add(context.string(R.string.mx_tip_storage_music))
                    }
                }
            }
            storageInfo.contains("MB") -> {
                val storageMatch = Regex("(\\d+)\\s*MB").find(storageInfo)
                val availableMB = storageMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                when {
                    availableMB < 500 -> {
                        suggestions.add(context.string(R.string.mx_tip_storage_emergency, "${availableMB}MB"))
                        suggestions.add(context.string(R.string.mx_tip_storage_delete_games))
                        suggestions.add(context.string(R.string.mx_tip_storage_move_all))
                        suggestions.add(context.string(R.string.mx_tip_storage_clear_all_cache))
                    }
                }
            }
        }
        
        // Analyze RAM usage - REAL device data analysis
        val ramUsage = getRamUsage(context)
        val ramMatch = Regex("\\((\\d+)%\\)").find(ramUsage)
        val ramUsagePercent = ramMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        when {
            ramUsagePercent > 85 -> {
                suggestions.add(context.string(R.string.mx_tip_ram_struggling))
                suggestions.add(context.string(R.string.mx_tip_ram_too_many))
                suggestions.add(context.string(R.string.mx_tip_ram_restart_daily))
            }
            ramUsagePercent > 70 -> {
                suggestions.add(context.string(R.string.mx_tip_ram_getting_slow, ramUsagePercent.toString()))
                suggestions.add(context.string(R.string.mx_tip_ram_swipe))
            }
        }
        
        // Analyze thermal status - REAL device data analysis
        val thermalStatus = getThermalZoneTemperatures(context)
        when {
            thermalStatus.contains("°C") -> {
                val tempMatch = Regex("(\\d+\\.?\\d*)°C").find(thermalStatus)
                val temperature = tempMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                when {
                    temperature > 45f -> {
                        suggestions.add(context.string(R.string.mx_tip_temp_burning, temperature.toString()))
                        suggestions.add(context.string(R.string.mx_tip_temp_fan))
                        suggestions.add(context.string(R.string.mx_tip_temp_sun))
                        suggestions.add(context.string(R.string.mx_tip_temp_cool_15))
                    }
                    temperature > 40f -> {
                        suggestions.add(context.string(R.string.mx_tip_temp_warm, temperature.toString()))
                        suggestions.add(context.string(R.string.mx_tip_temp_no_use_charging))
                    }
                }
            }
        }
        
        // Analyze security status. This runs from Compose — a LazyColumn item body and a
        // LaunchedEffect — so it must never block: getSecurityInfo() forks a `getenforce`
        // subprocess. Read the cache. When it is cold we add no security advice rather than
        // freezing the UI to fetch it; every branch below is additive, so omission is safe.
        val securityInfo = SecurityInfoCache.cachedOrEmpty()
        when {
            securityInfo.contains("❌ Security shield is off") -> {
                suggestions.add(context.string(R.string.mx_tip_sec_shield_off))
                suggestions.add(context.string(R.string.mx_tip_sec_shield_support))
                suggestions.add(context.string(R.string.mx_tip_sec_shield_careful))
            }
            securityInfo.contains("❌ Storage is not protected") -> {
                suggestions.add(context.string(R.string.mx_tip_sec_unencrypted))
                suggestions.add(context.string(R.string.mx_tip_sec_encrypt))
                suggestions.add(context.string(R.string.mx_tip_sec_encrypt_why))
            }
            // "❌ No admin set" branch removed 2026-10-04: no device admin is the normal state,
            // and the advice sent users to grant an app device-admin access.
            securityInfo.contains("⚠️ Clipboard can be read") -> {
                suggestions.add(context.string(R.string.mx_tip_sec_clipboard))
                suggestions.add(context.string(R.string.mx_tip_sec_clipboard_passwords))
                suggestions.add(context.string(R.string.mx_tip_sec_clipboard_newer))
            }
            securityInfo.contains("⚠️ Modified system files found") -> {
                suggestions.add(context.string(R.string.mx_tip_sec_modified))
                suggestions.add(context.string(R.string.mx_tip_sec_play_protect))
                suggestions.add(context.string(R.string.mx_tip_sec_modified_support))
            }
            // "🚨 Malware Signatures Detected" branch removed 2026-10-04: nothing produces that text
            // since the invented signature list was replaced, and the app does no malware detection.
            // The 👣 motion branch was removed 2026-10-04 with the placeholder check that fed
            // it — it told every user someone might be handling their phone.
            securityInfo.contains("📱") && securityInfo.contains("Permissions:") -> {
                suggestions.add(context.string(R.string.mx_tip_sec_permissions))
                suggestions.add(context.string(R.string.mx_tip_sec_permissions_review))
                suggestions.add(context.string(R.string.mx_tip_sec_permissions_off))
            }
            securityInfo.contains("⚠️") -> {
                suggestions.add(context.string(R.string.mx_tip_sec_update))
                suggestions.add(context.string(R.string.mx_tip_sec_lock))
                suggestions.add(context.string(R.string.mx_tip_sec_lock_type))
            }
        }
        
        // Score-based personalized suggestions
        when {
            score <= 3 -> {
                suggestions.add(context.string(R.string.mx_tip_score_serious))
                suggestions.add(context.string(R.string.mx_tip_score_reset))
                suggestions.add(context.string(R.string.mx_tip_score_upgrade))
            }
            score <= 5 -> {
                suggestions.add(context.string(R.string.mx_tip_score_several))
                suggestions.add(context.string(R.string.mx_tip_score_restart))
                suggestions.add(context.string(R.string.mx_tip_score_few_apps))
            }
            score <= 7 -> {
                suggestions.add(context.string(R.string.mx_tip_score_okay))
                suggestions.add(context.string(R.string.mx_tip_score_overnight))
                suggestions.add(context.string(R.string.mx_tip_score_update_apps))
            }
            score <= 9 -> {
                suggestions.add(context.string(R.string.mx_tip_score_great))
                suggestions.add(context.string(R.string.mx_tip_score_keep_going))
                suggestions.add(context.string(R.string.mx_tip_score_check_daily))
            }
            else -> {
                suggestions.add(context.string(R.string.mx_tip_score_perfect))
                suggestions.add(context.string(R.string.mx_tip_score_expert))
                suggestions.add(context.string(R.string.mx_tip_score_share))
            }
        }
        
        // Add fun, easy-to-understand maintenance tips
        if (suggestions.size < 6) {
            suggestions.add(context.string(R.string.mx_tip_general_charge_often))
            suggestions.add(context.string(R.string.mx_tip_general_cache))
            suggestions.add(context.string(R.string.mx_tip_general_restart_weekly))
            suggestions.add(context.string(R.string.mx_tip_general_update))
            suggestions.add(context.string(R.string.mx_tip_general_no_zero))
            suggestions.add(context.string(R.string.mx_tip_general_cool))
            suggestions.add(context.string(R.string.mx_tip_general_backup))
            suggestions.add(context.string(R.string.mx_tip_general_no_games_charging))
        }
        
        return suggestions.take(6) // Return top 6 most helpful suggestions
    }

    // Device Analysis function removed - redundant with Device Info tab
    
    /**
     * Daily Task System - Creates actionable tasks for users to complete daily
     */
    data class DailyTask(
        val id: String,
        val title: String,
        val description: String,
        val icon: String,
        val priority: TaskPriority,
        val actionType: TaskActionType
    )
    
    enum class TaskPriority {
        HIGH, MEDIUM, LOW
    }
    
    enum class TaskActionType {
        CLOSE_APPS,
        REVOKE_PERMISSION,
        CHECK_TEMPERATURE,
        CLEAR_CACHE,
        RESTART_PHONE,
        CHARGE_BATTERY,
        UPDATE_APPS,
        REVIEW_PRIVACY
    }
    
    private const val KEY_TASKS_TODAY = "tasks_today"
    private const val KEY_TASKS_COMPLETED = "tasks_completed_today"
    private const val KEY_TASKS_DATE = "tasks_date"

    /**
     * Language the saved tasks were written in. Task titles are stored as ready-made text, so
     * tasks saved in one language are thrown away and written again after a language switch.
     */
    private const val KEY_TASKS_LANGUAGE = "tasks_language"

    private fun textLanguage(context: Context): String = context.resources.configuration.locales[0].language

    private fun savedTasksAreCurrent(context: Context, today: String): Boolean {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_TASKS_DATE, "") == today &&
            prefs.getString(KEY_TASKS_LANGUAGE, null) == textLanguage(context)
    }
    
    // Temperature history tracking
    private const val KEY_TEMPERATURE_HISTORY = "temperature_history"
    
    /**
     * Generate daily actionable tasks based on current device state
     */
    fun generateDailyTasks(context: Context, healthScore: Int): List<DailyTask> {
        val tasks = mutableListOf<DailyTask>()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        // Check if tasks were already generated today
        if (savedTasksAreCurrent(context, today)) {
            // Return saved tasks for today
            return getSavedTasks(context)
        }
        
        // Generate new tasks based on device state
        val batteryInfo = getBatteryChargingInfo(context)
        val storageInfo = getMemoryAndStorageInfo(context)
        val ramUsage = getRamUsage(context)
        val thermalStatus = getThermalZoneTemperatures(context)
        // Non-blocking read; see getImprovementSuggestions above.
        val securityInfo = SecurityInfoCache.cachedOrEmpty()
        
        // Task 1: RAM/App Management
        val ramMatch = Regex("\\((\\d+)%\\)").find(ramUsage)
        val ramUsagePercent = ramMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (ramUsagePercent > 70) {
            val appCount = if (ramUsagePercent > 85) 5 else 3
            tasks.add(
                DailyTask(
                    id = "close_apps_$today",
                    title = context.string(R.string.mx_task_close_apps_title, appCount.toString()),
                    description = context.string(R.string.mx_task_close_apps_body, ramUsagePercent.toString()),
                    icon = "📱",
                    priority = if (ramUsagePercent > 85) TaskPriority.HIGH else TaskPriority.MEDIUM,
                    actionType = TaskActionType.CLOSE_APPS
                )
            )
        }
        
        // Task 2: Temperature Check
        val tempMatch = Regex("(\\d+\\.?\\d*)°C").find(thermalStatus)
        val temperature = tempMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        if (temperature > 35f) {
            tasks.add(
                DailyTask(
                    id = "check_temp_$today",
                    title = context.string(R.string.mx_task_temp_title, temperature.toInt().toString()),
                    description = if (temperature > 40f)
                        context.string(R.string.mx_task_temp_body_warm)
                    else
                        context.string(R.string.mx_task_temp_body_watch),
                    icon = "🌡️",
                    priority = if (temperature > 40f) TaskPriority.HIGH else TaskPriority.MEDIUM,
                    actionType = TaskActionType.CHECK_TEMPERATURE
                )
            )
        }
        
        // Task 3: Privacy/Permissions
        if (securityInfo.contains("Permissions:") || securityInfo.contains("⚠️")) {
            tasks.add(
                DailyTask(
                    id = "review_privacy_$today",
                    title = context.string(R.string.mx_task_privacy_title),
                    description = context.string(R.string.mx_task_privacy_body),
                    icon = "🔐",
                    priority = TaskPriority.MEDIUM,
                    actionType = TaskActionType.REVIEW_PRIVACY
                )
            )
        }
        
        // Task 4: Storage Management
        val storageMatch = Regex("(\\d+\\.?\\d*)\\s*(GB|MB)").find(storageInfo)
        if (storageMatch != null) {
            val available = storageMatch.groupValues[1].toFloatOrNull() ?: 0f
            val unit = storageMatch.groupValues[2]
            val availableGB = if (unit == "MB") available / 1024f else available
            if (availableGB < 5f) {
                tasks.add(
                    DailyTask(
                        id = "clear_storage_$today",
                        title = context.string(R.string.mx_task_storage_title),
                        description = context.string(R.string.mx_task_storage_body, "${String.format("%.1f", availableGB)}GB"),
                        icon = "💾",
                        priority = if (availableGB < 2f) TaskPriority.HIGH else TaskPriority.MEDIUM,
                        actionType = TaskActionType.CLEAR_CACHE
                    )
                )
            }
        }
        
        // Task 5: Battery Health
        if (batteryInfo.contains("Battery Full") || batteryInfo.contains("Overheat")) {
            tasks.add(
                DailyTask(
                    id = "battery_care_$today",
                    title = context.string(R.string.mx_task_battery_title),
                    description = if (batteryInfo.contains("Battery Full"))
                        context.string(R.string.mx_task_battery_body_full)
                    else
                        context.string(R.string.mx_task_battery_body_hot),
                    icon = "🔋",
                    priority = TaskPriority.MEDIUM,
                    actionType = TaskActionType.CHARGE_BATTERY
                )
            )
        }
        
        // If no critical tasks, add general maintenance tasks
        if (tasks.isEmpty() || tasks.size < 3) {
            tasks.add(
                DailyTask(
                    id = "restart_phone_$today",
                    title = context.string(R.string.mx_task_restart_title),
                    description = context.string(R.string.mx_task_restart_body),
                    icon = "🔄",
                    priority = TaskPriority.LOW,
                    actionType = TaskActionType.RESTART_PHONE
                )
            )
        }
        
        // Limit to 5 tasks max
        val finalTasks = tasks.take(5)
        
        // Save tasks for today
        saveTasks(context, finalTasks)
        
        return finalTasks
    }
    
    /**
     * Get tasks for today (either generated or saved)
     */
    fun getDailyTasks(context: Context, healthScore: Int): List<DailyTask> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return if (savedTasksAreCurrent(context, today)) {
            getSavedTasks(context)
        } else {
            generateDailyTasks(context, healthScore)
        }
    }
    
    /**
     * Mark a task as completed
     */
    fun completeTask(context: Context, taskId: String) {
        val prefs = getPrefs(context)
        val completedTasks = prefs.getStringSet(KEY_TASKS_COMPLETED, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        completedTasks.add(taskId)
        prefs.edit {
            putStringSet(KEY_TASKS_COMPLETED, completedTasks)
        }
        
        // Track task completion for streak bonus
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val tasksCompletedToday = completedTasks.count { it.contains(today) }
        if (tasksCompletedToday >= 3) {
            // Bonus: Completing 3+ tasks gives a small streak boost
            val streak = getDailyStreak(context)
            if (streak > 0) {
                // Small bonus - already counted in daily scan
            }
        }
    }
    
    /**
     * Check if task is completed
     */
    fun isTaskCompleted(context: Context, taskId: String): Boolean {
        val prefs = getPrefs(context)
        val completedTasks = prefs.getStringSet(KEY_TASKS_COMPLETED, mutableSetOf()) ?: mutableSetOf()
        return completedTasks.contains(taskId)
    }
    
    /**
     * Get number of completed tasks today
     */
    fun getCompletedTasksCount(context: Context): Int {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val prefs = getPrefs(context)
        val completedTasks = prefs.getStringSet(KEY_TASKS_COMPLETED, mutableSetOf()) ?: mutableSetOf()
        return completedTasks.count { it.contains(today) }
    }
    
    /**
     * Save tasks for today
     */
    private fun saveTasks(context: Context, tasks: List<DailyTask>) {
        val prefs = getPrefs(context)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val tasksJson = tasks.joinToString("|||") { "${it.id}::${it.title}::${it.description}::${it.icon}::${it.priority}::${it.actionType}" }
        prefs.edit {
            putString(KEY_TASKS_TODAY, tasksJson)
            putString(KEY_TASKS_DATE, today)
            putString(KEY_TASKS_LANGUAGE, textLanguage(context))
        }
    }
    
    /**
     * Get saved tasks for today
     */
    private fun getSavedTasks(context: Context): List<DailyTask> {
        val prefs = getPrefs(context)
        val tasksJson = prefs.getString(KEY_TASKS_TODAY, "") ?: return emptyList()
        if (tasksJson.isEmpty()) return emptyList()
        
        return tasksJson.split("|||").mapNotNull { taskStr ->
            val parts = taskStr.split("::")
            if (parts.size >= 6) {
                try {
                    DailyTask(
                        id = parts[0],
                        title = parts[1],
                        description = parts[2],
                        icon = parts[3],
                        priority = TaskPriority.valueOf(parts[4]),
                        actionType = TaskActionType.valueOf(parts[5])
                    )
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }
    
    // ============================================================================
    // TEMPERATURE HISTORY TRACKING
    // ============================================================================
    
    data class TemperatureDataPoint(
        val date: String,
        val maxTemp: Float,
        val minTemp: Float,
        val avgTemp: Float
    )
    
    /**
     * Save today's temperature data
     */
    fun saveTemperatureData(context: Context, temperature: Float) {
        val prefs = getPrefs(context)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        // Get existing history
        val historyJson = prefs.getString(KEY_TEMPERATURE_HISTORY, "") ?: ""
        val history = parseTemperatureHistory(historyJson)
        
        // Find or create today's entry
        val todayIndex = history.indexOfFirst { it.date == today }
        if (todayIndex >= 0) {
            // Update existing entry
            val existing = history[todayIndex]
            history[todayIndex] = TemperatureDataPoint(
                date = today,
                maxTemp = maxOf(existing.maxTemp, temperature),
                minTemp = minOf(existing.minTemp, temperature),
                avgTemp = (existing.avgTemp + temperature) / 2f // Simple average
            )
        } else {
            // Add new entry
            history.add(
                TemperatureDataPoint(
                    date = today,
                    maxTemp = temperature,
                    minTemp = temperature,
                    avgTemp = temperature
                )
            )
        }
        
        // Keep only last 7 days
        val recentHistory = history.takeLast(7)
        
        // Save back
        val updatedJson = serializeTemperatureHistory(recentHistory)
        prefs.edit {
            putString(KEY_TEMPERATURE_HISTORY, updatedJson)
        }
    }
    
    /**
     * Get 7-day temperature history
     */
    fun getTemperatureHistory(context: Context, days: Int = 7): List<TemperatureDataPoint> {
        val prefs = getPrefs(context)
        val historyJson = prefs.getString(KEY_TEMPERATURE_HISTORY, "") ?: ""
        val history = parseTemperatureHistory(historyJson)
        return history.takeLast(days)
    }
    
    /**
     * Get peak temperature from history
     */
    fun getPeakTemperature(context: Context): Float? {
        val history = getTemperatureHistory(context, 7)
        return history.maxOfOrNull { it.maxTemp }
    }
    
    /**
     * Parse temperature history from JSON string
     */
    private fun parseTemperatureHistory(json: String): MutableList<TemperatureDataPoint> {
        if (json.isEmpty()) return mutableListOf()
        
        return try {
            json.split("|||").mapNotNull { entry ->
                val parts = entry.split("::")
                if (parts.size >= 4) {
                    TemperatureDataPoint(
                        date = parts[0],
                        maxTemp = parts[1].toFloatOrNull() ?: 0f,
                        minTemp = parts[2].toFloatOrNull() ?: 0f,
                        avgTemp = parts[3].toFloatOrNull() ?: 0f
                    )
                } else {
                    null
                }
            }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }
    
    /**
     * Serialize temperature history to JSON string
     */
    private fun serializeTemperatureHistory(history: List<TemperatureDataPoint>): String {
        return history.joinToString("|||") { 
            "${it.date}::${it.maxTemp}::${it.minTemp}::${it.avgTemp}"
        }
    }
    
    /**
     * Get temperature trend (increasing, decreasing, stable)
     */
    /**
     * Returns English on purpose: the Health tab compares the result with "Not enough data" and
     * hands it to the AI report. `HealthDisplayText.temperatureTrend` puts it into the app
     * language where it is shown.
     */
    fun getTemperatureTrend(context: Context): String {
        val history = getTemperatureHistory(context, 7)
        if (history.size < 2) return "Not enough data"
        
        val recent = history.takeLast(3).map { it.avgTemp }
        val older = history.dropLast(3).takeLast(3).map { it.avgTemp }
        
        if (older.isEmpty()) return "Not enough data"
        
        val recentAvg = recent.average()
        val olderAvg = older.average()
        
        return when {
            recentAvg > olderAvg + 2 -> "📈 Increasing"
            recentAvg < olderAvg - 2 -> "📉 Decreasing"
            else -> "📊 Stable"
        }
    }
} 