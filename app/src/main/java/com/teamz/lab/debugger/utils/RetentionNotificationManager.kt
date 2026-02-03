package com.teamz.lab.debugger.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import java.util.Calendar

/**
 * RetentionNotificationManager - Handles automatic local notifications for user retention
 * OneSignal handles promotional notifications from backend
 */
object RetentionNotificationManager {
    // Notification channels for different types of retention notifications
    const val DAILY_HEALTH_CHANNEL = "daily_health_reminders"
    const val ENGAGEMENT_CHANNEL = "engagement_notifications"
    const val RETENTION_CHANNEL = "retention_notifications"
    
    // WorkManager tags for background scheduling
    private const val DAILY_HEALTH_WORK = "daily_health_work"
    private const val WEEKLY_REPORT_WORK = "weekly_report_work"
    private const val RETENTION_WORK = "retention_work"
    private const val STREAK_REMINDER_WORK = "streak_reminder_work"
    private const val ACHIEVEMENT_WORK = "achievement_work"
    private const val MILESTONE_WORK = "milestone_work"
    private const val PERSONALIZED_TIP_WORK = "personalized_tip_work"
    
    // Deduplication: Track last notification sent time to prevent duplicates
    private const val PREF_LAST_NOTIFICATION_TIME = "last_notification_time"
    private const val PREF_LAST_NOTIFICATION_TITLE = "last_notification_title"
    private const val MIN_NOTIFICATION_INTERVAL_MS = 60000L // 1 minute minimum between ANY notifications (prevents spam)
    
    // Smart retention: Track user engagement to send personalized notifications
    private const val PREF_NOTIFICATION_COUNT_TODAY = "notification_count_today"
    private const val PREF_LAST_NOTIFICATION_DATE = "last_notification_date"
    private const val MAX_NOTIFICATIONS_PER_DAY = 1 // Maximum 1 notification per day (user requested)
    
    // Context-aware notification tracking
    private const val PREF_LAST_TEMPERATURE_ALERT = "last_temperature_alert"
    private const val PREF_LAST_PRIVACY_ALERT = "last_privacy_alert"
    private const val PREF_LAST_TASK_REMINDER = "last_task_reminder"
    private const val PREF_LAST_HEALTH_SCORE_ALERT = "last_health_score_alert"
    private const val PREF_LAST_BATTERY_ALERT = "last_battery_alert"
    
    // User preference: Allow users to disable notifications
    private const val PREF_NOTIFICATIONS_ENABLED = "notifications_enabled"
    
    /**
     * Initialize automatic background notification scheduling for retention
     * OneSignal handles promotional notifications from backend
     */
    fun initializeRetentionNotifications(context: Context) {
        setupNotificationChannels(context)
        scheduleDailyHealthReminder(context)
        scheduleWeeklyReport(context)
        scheduleRetentionNotifications(context)
        scheduleStreakReminders(context) // Re-enabled with deduplication to prevent duplicates
        scheduleAchievementNotifications(context) // New: Achievement celebrations
        scheduleMilestoneNotifications(context) // New: Milestone celebrations
        schedulePersonalizedTips(context) // New: Personalized tips based on usage
    }
    
    /**
     * Schedule daily health reminder using WorkManager
     * This will trigger even if the app is terminated
     */
    private fun scheduleDailyHealthReminder(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .build()
        
        val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyHealthReminderWorker>(
            1, TimeUnit.DAYS // Repeat every 24 hours
        )
            .setConstraints(constraints)
            .addTag(DAILY_HEALTH_WORK)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_HEALTH_WORK,
            ExistingPeriodicWorkPolicy.REPLACE,
            dailyWorkRequest
        )
    }
    
    /**
     * Schedule weekly report using WorkManager
     */
    private fun scheduleWeeklyReport(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        
        val weeklyWorkRequest = PeriodicWorkRequestBuilder<WeeklyReportWorker>(
            7, TimeUnit.DAYS // Repeat every 7 days
        )
            .setConstraints(constraints)
            .addTag(WEEKLY_REPORT_WORK)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WEEKLY_REPORT_WORK,
            ExistingPeriodicWorkPolicy.REPLACE,
            weeklyWorkRequest
        )
    }
    
    /**
     * Schedule retention notifications for users who haven't opened the app
     * Much more conservative timing to avoid bothering users
     */
    private fun scheduleRetentionNotifications(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        
        // Much more conservative: only check every 3 days instead of daily
        val retentionWorkRequest = PeriodicWorkRequestBuilder<RetentionReminderWorker>(
            3, TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .addTag(RETENTION_WORK)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RETENTION_WORK,
            ExistingPeriodicWorkPolicy.REPLACE,
            retentionWorkRequest
        )
    }
    
    /**
     * Schedule streak reminders for active users
     * More conservative timing to avoid bothering users
     */
    private fun scheduleStreakReminders(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        
        // Check for streak opportunities every 12 hours (not 6 hours) to be less intrusive
        val streakWorkRequest = PeriodicWorkRequestBuilder<StreakReminderWorker>(
            12, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(STREAK_REMINDER_WORK)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            STREAK_REMINDER_WORK,
            ExistingPeriodicWorkPolicy.REPLACE,
            streakWorkRequest
        )
    }
    
    /**
     * Send immediate notification (for when app is open) - with smart logic and deduplication
     */
    fun sendDailyHealthReminder(context: Context) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastScanDate = HealthScoreUtils.getLastScanDate(context)
        
        // Only send if user hasn't scanned today
        if (lastScanDate != today) {
            // Use runBlocking since this is called from a non-suspend context
            val healthScore = kotlinx.coroutines.runBlocking {
                HealthScoreUtils.calculateDailyHealthScore(context)
            }
            val streak = HealthScoreUtils.getDailyStreak(context)
            
            // Only send if user has an active streak or low health score
            if (streak >= 1 || healthScore < 6) {
                val title = when {
                    streak >= 7 -> "🔥 Don't break your ${streak}-day streak!"
                    streak >= 3 -> "⚡ Keep your ${streak}-day streak alive!"
                    streak >= 1 -> "📱 Continue your ${streak}-day health check streak!"
                    else -> "🚀 Your device needs attention!"
                }
                
                val message = when {
                    healthScore >= 8 -> "Your device is doing great! Check today's score."
                    healthScore >= 6 -> "Your device needs a quick health check."
                    else -> "Your device might need attention. Scan now!"
                }
                
                // sendNotification has deduplication built-in, so just call it
                sendNotification(title, message, DAILY_HEALTH_CHANNEL, context)
            }
        }
    }
    
    /**
     * Track when user opens the app for retention logic
     */
    fun updateLastAppOpenTime(context: Context) {
        context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
            .edit {
                putLong("last_app_open_time", System.currentTimeMillis())
            }
    }
    
    /**
     * Check if notifications are enabled by user preference
     * Defaults to true (enabled) for new users
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_NOTIFICATIONS_ENABLED, true) // Default to enabled
    }
    
    /**
     * Set notification enabled/disabled preference
     */
    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
            .edit {
                putBoolean(PREF_NOTIFICATIONS_ENABLED, enabled)
            }
    }
    
    /**
     * Send notification with proper channel setup and deduplication
     * Includes smart daily limit to prevent notification spam
     * ENHANCED: Only allows 1 notification per day maximum (user requirement)
     */
    fun sendNotification(title: String, message: String, channel: String, context: Context) {
        try {
            // Check if user has disabled notifications
            if (!areNotificationsEnabled(context)) {
                return // User has disabled notifications, don't send
            }
            
            // ENHANCED: Check global notification cooldown (1 minute between ANY notifications)
            val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
            val lastNotificationTime = prefs.getLong(PREF_LAST_NOTIFICATION_TIME, 0L)
            val currentTime = System.currentTimeMillis()
            
            // Don't send if notification was sent less than 1 minute ago (prevents multiple notifications at same time)
            if ((currentTime - lastNotificationTime) < MIN_NOTIFICATION_INTERVAL_MS) {
                android.util.Log.d("RetentionNotificationManager", "Skipping notification - too soon after last one (${(currentTime - lastNotificationTime) / 1000}s ago)")
                return
            }
            
            // Check for duplicates before sending
            if (!shouldSendNotification(context, title, channel)) {
                return // Skip duplicate notification
            }
            
            // Smart daily limit: Don't send more than MAX_NOTIFICATIONS_PER_DAY per day (1 notification max)
            if (!canSendNotificationToday(context)) {
                android.util.Log.d("RetentionNotificationManager", "Skipping notification - daily limit reached")
                return // Skip to avoid notification spam
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Create notification channel for Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelObj = NotificationChannel(
                    channel,
                    when (channel) {
                        DAILY_HEALTH_CHANNEL -> "Daily Health Reminders"
                        ENGAGEMENT_CHANNEL -> "Engagement Notifications"
                        RETENTION_CHANNEL -> "Retention Notifications"
                        else -> "General Notifications"
                    },
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channelObj)
            }
            
            // Use consistent notification ID based on channel and title hash
            // This ensures same notification replaces previous one instead of stacking
            val notificationId = (channel + title).hashCode()
            
            val notification = NotificationCompat.Builder(context, channel)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify(notificationId, notification)
            
            // Record that we sent this notification (global timestamp for cooldown)
            // Use existing prefs variable from above
            prefs.edit {
                putLong(PREF_LAST_NOTIFICATION_TIME, System.currentTimeMillis())
            }
            
            // Record that we sent this notification (channel-specific)
            recordNotificationSent(context, title, channel)
            
            // Track daily notification count
            incrementDailyNotificationCount(context)
            
            android.util.Log.d("RetentionNotificationManager", "Notification sent: $title (daily count: ${prefs.getInt(PREF_NOTIFICATION_COUNT_TODAY, 0)})")
        } catch (e: Exception) {
            // Handle notification sending errors gracefully
            e.printStackTrace()
        }
    }
    
    /**
     * Check if notification should be sent (deduplication logic)
     * Prevents sending same notification within MIN_NOTIFICATION_INTERVAL_MS
     * Made internal so Worker classes can use it
     */
    internal fun shouldSendNotification(context: Context, title: String, channel: String): Boolean {
        val notificationPrefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        val lastTime = notificationPrefs.getLong("${PREF_LAST_NOTIFICATION_TIME}_${channel}", 0L)
        val lastTitle = notificationPrefs.getString("${PREF_LAST_NOTIFICATION_TITLE}_${channel}", "")
        val currentTime = System.currentTimeMillis()
        
        // Don't send if same title was sent recently
        if (title == lastTitle && (currentTime - lastTime) < MIN_NOTIFICATION_INTERVAL_MS) {
            return false
        }
        
        return true
    }
    
    /**
     * Record that a notification was sent (for deduplication)
     * Made internal so Worker classes can use it
     */
    internal fun recordNotificationSent(context: Context, title: String, channel: String) {
        context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
            .edit {
                putLong("${PREF_LAST_NOTIFICATION_TIME}_${channel}", System.currentTimeMillis())
                putString("${PREF_LAST_NOTIFICATION_TITLE}_${channel}", title)
            }
    }
    
    /**
     * Setup notification channels for Android 8.0+
     */
    private fun setupNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val dailyChannel = NotificationChannel(
                DAILY_HEALTH_CHANNEL,
                "Daily Health Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily reminders to check your device health"
            }
            
            val engagementChannel = NotificationChannel(
                ENGAGEMENT_CHANNEL,
                "Engagement Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications about streaks, improvements, and new features"
            }
            
            val retentionChannel = NotificationChannel(
                RETENTION_CHANNEL,
                "Retention Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to come back and check your device health"
            }
            
            notificationManager.createNotificationChannel(dailyChannel)
            notificationManager.createNotificationChannel(engagementChannel)
            notificationManager.createNotificationChannel(retentionChannel)
        }
    }
    
    /**
     * Get last app open time for retention logic
     */
    private fun getLastAppOpenTime(context: Context): Long {
        return context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
            .getLong("last_app_open_time", 0L)
    }
    
    /**
     * Check if we can send notification today (smart daily limit)
     * Prevents notification spam while allowing important notifications
     */
    private fun canSendNotificationToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastNotificationDate = prefs.getString(PREF_LAST_NOTIFICATION_DATE, "")
        val countToday = prefs.getInt(PREF_NOTIFICATION_COUNT_TODAY, 0)
        
        // Reset count if it's a new day
        if (lastNotificationDate != today) {
            prefs.edit {
                putString(PREF_LAST_NOTIFICATION_DATE, today)
                putInt(PREF_NOTIFICATION_COUNT_TODAY, 0)
            }
            return true // New day, can send
        }
        
        // Check if we've reached daily limit
        return countToday < MAX_NOTIFICATIONS_PER_DAY
    }
    
    /**
     * Increment daily notification count
     */
    private fun incrementDailyNotificationCount(context: Context) {
        val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        val countToday = prefs.getInt(PREF_NOTIFICATION_COUNT_TODAY, 0)
        prefs.edit {
            putInt(PREF_NOTIFICATION_COUNT_TODAY, countToday + 1)
        }
    }
    
    /**
     * Schedule achievement celebration notifications
     * Celebrates when users unlock achievements
     */
    private fun scheduleAchievementNotifications(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        
        // Check for new achievements every 6 hours
        val achievementWorkRequest = PeriodicWorkRequestBuilder<AchievementWorker>(
            6, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(ACHIEVEMENT_WORK)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ACHIEVEMENT_WORK,
            ExistingPeriodicWorkPolicy.REPLACE,
            achievementWorkRequest
        )
    }
    
    /**
     * Schedule milestone celebration notifications
     * Celebrates important milestones (streaks, scans, etc.)
     */
    private fun scheduleMilestoneNotifications(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        
        // Check for milestones every 12 hours
        val milestoneWorkRequest = PeriodicWorkRequestBuilder<MilestoneWorker>(
            12, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(MILESTONE_WORK)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MILESTONE_WORK,
            ExistingPeriodicWorkPolicy.REPLACE,
            milestoneWorkRequest
        )
    }
    
    /**
     * Schedule personalized tip notifications
     * Sends helpful tips based on user behavior and device state
     */
    private fun schedulePersonalizedTips(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        
        // Send personalized tips every 2 days (less frequent to avoid spam)
        val tipWorkRequest = PeriodicWorkRequestBuilder<PersonalizedTipWorker>(
            2, TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .addTag(PERSONALIZED_TIP_WORK)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERSONALIZED_TIP_WORK,
            ExistingPeriodicWorkPolicy.REPLACE,
            tipWorkRequest
        )
    }
    
    // ============================================================================
    // CONTEXT-AWARE NOTIFICATION TRIGGERS
    // ============================================================================
    
    /**
     * Send temperature alert notification (real-time, not scheduled)
     * Only sends if temperature is critical and not already alerted today
     */
    fun sendTemperatureAlert(context: Context, temperature: Float) {
        val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastAlertDate = prefs.getString(PREF_LAST_TEMPERATURE_ALERT, "")
        
        // Only send if critical temperature and not already alerted today
        if (temperature > 40f && lastAlertDate != today) {
            val title = when {
                temperature > 45f -> "🔥 Critical: Phone Overheating!"
                else -> "🌡️ Phone Getting Hot"
            }
            val message = when {
                temperature > 45f -> "Your phone is ${temperature.toInt()}°C! Stop using it and let it cool down immediately."
                else -> "Your phone is ${temperature.toInt()}°C. Close apps and let it cool down."
            }
            
            sendNotification(title, message, DAILY_HEALTH_CHANNEL, context)
            
            prefs.edit {
                putString(PREF_LAST_TEMPERATURE_ALERT, today)
            }
        }
    }
    
    /**
     * Send privacy alert notification when mic/camera used unexpectedly
     */
    fun sendPrivacyAlert(context: Context, threatType: String, details: String) {
        val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastAlertDate = prefs.getString(PREF_LAST_PRIVACY_ALERT, "")
        
        // Only send once per day
        if (lastAlertDate != today) {
            val title = when (threatType) {
                "mic" -> "🎤 Microphone Used Unexpectedly"
                "camera" -> "📷 Camera Used Unexpectedly"
                else -> "🔐 Privacy Alert"
            }
            val message = when (threatType) {
                "mic" -> "An app used your microphone. Review permissions in settings."
                "camera" -> "An app accessed your camera. Check which app and why."
                else -> details
            }
            
            sendNotification(title, message, DAILY_HEALTH_CHANNEL, context)
            
            prefs.edit {
                putString(PREF_LAST_PRIVACY_ALERT, today)
            }
        }
    }
    
    /**
     * Send task reminder notification if user hasn't completed tasks by 6 PM
     */
    fun sendTaskReminder(context: Context, completedCount: Int, totalTasks: Int) {
        val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastReminderDate = prefs.getString(PREF_LAST_TASK_REMINDER, "")
        
        // Only send once per day, after 6 PM
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        
        if (currentHour >= 18 && lastReminderDate != today && completedCount < totalTasks) {
            val remaining = totalTasks - completedCount
            val title = "📋 Complete Your Daily Tasks"
            val message = "You have $remaining task${if (remaining > 1) "s" else ""} remaining. Complete them to maintain your streak!"
            
            sendNotification(title, message, ENGAGEMENT_CHANNEL, context)
            
            prefs.edit {
                putString(PREF_LAST_TASK_REMINDER, today)
            }
        }
    }
    
    /**
     * Send health score drop alert if score dropped significantly
     */
    fun sendHealthScoreDropAlert(context: Context, currentScore: Int, previousScore: Int) {
        val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastAlertDate = prefs.getString(PREF_LAST_HEALTH_SCORE_ALERT, "")
        
        // Only send if score dropped by 2+ points and not already alerted today
        if ((previousScore - currentScore) >= 2 && lastAlertDate != today) {
            val title = "⚠️ Health Score Dropped"
            val message = "Your device health dropped from $previousScore/10 to $currentScore/10. Check what needs attention!"
            
            sendNotification(title, message, DAILY_HEALTH_CHANNEL, context)
            
            prefs.edit {
                putString(PREF_LAST_HEALTH_SCORE_ALERT, today)
            }
        }
    }
    
    /**
     * Send battery drain alert when power consumption spikes
     */
    fun sendBatteryDrainAlert(context: Context, powerWatts: Double) {
        val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastAlertDate = prefs.getString(PREF_LAST_BATTERY_ALERT, "")
        
        // Only send if critical power consumption and not already alerted today
        if (powerWatts > 10.0 && lastAlertDate != today) {
            val title = "⚡ High Battery Drain Detected"
            val message = "Your phone is using ${String.format("%.1f", powerWatts)}W. Close apps to save battery!"
            
            sendNotification(title, message, DAILY_HEALTH_CHANNEL, context)
            
            prefs.edit {
                putString(PREF_LAST_BATTERY_ALERT, today)
            }
        }
    }
}

// ============================================================================
// WORKER CLASSES FOR BACKGROUND NOTIFICATION SCHEDULING
// ============================================================================

/**
 * Daily health reminder worker - runs every 24 hours even when app is terminated
 */
class DailyHealthReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val lastScanDate = HealthScoreUtils.getLastScanDate(applicationContext)
            
            // Only send if user hasn't scanned today
            if (lastScanDate != today) {
                // Use runBlocking since Worker runs on background thread
                val healthScore = kotlinx.coroutines.runBlocking {
                    HealthScoreUtils.calculateDailyHealthScore(applicationContext)
                }
                val streak = HealthScoreUtils.getDailyStreak(applicationContext)
                
                val title = when {
                    streak >= 7 -> "🔥 Don't break your ${streak}-day streak!"
                    streak >= 3 -> "⚡ Keep your ${streak}-day streak alive!"
                    streak >= 1 -> "📱 Continue your ${streak}-day health check streak!"
                    else -> "🚀 Start your daily device health check!"
                }
                
                val message = when {
                    healthScore >= 8 -> "Your device is doing great! Check today's score."
                    healthScore >= 6 -> "Your device needs a quick health check."
                    else -> "Your device might need attention. Scan now!"
                }
                
                // Use sendNotification which has deduplication built-in
                RetentionNotificationManager.sendNotification(title, message, RetentionNotificationManager.DAILY_HEALTH_CHANNEL, applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}

/**
 * Weekly report worker - runs every 7 days
 */
class WeeklyReportWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val streak = HealthScoreUtils.getDailyStreak(applicationContext)
            val totalScans = HealthScoreUtils.getTotalScans(applicationContext)
            val bestScore = HealthScoreUtils.getBestScore(applicationContext)
            
            val title = when {
                streak >= 7 -> "🏆 Amazing week! You're on fire!"
                streak >= 3 -> "📈 Great progress this week!"
                streak >= 1 -> "👍 Good start! Keep it up!"
                else -> "📊 Your weekly device health report"
            }
            
            val message = when {
                streak >= 7 -> "You've scanned $totalScans times with a best score of ${bestScore}/10!"
                streak >= 3 -> "You've maintained a ${streak}-day streak. Impressive!"
                streak >= 1 -> "You've started your health journey. $totalScans scans so far!"
                else -> "Start your device health journey today!"
            }
            
            RetentionNotificationManager.sendNotification(title, message, RetentionNotificationManager.ENGAGEMENT_CHANNEL, applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * Retention reminder worker - checks if user hasn't opened app recently
 */
class RetentionReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val lastOpenTime = getLastAppOpenTime(applicationContext)
            val currentTime = System.currentTimeMillis()
            val hoursSinceLastOpen = (currentTime - lastOpenTime) / (1000 * 60 * 60)
            
            // Much more conservative: only send if haven't opened for 7+ days
            if (hoursSinceLastOpen >= 168) { // 7 days = 168 hours
                val streak = HealthScoreUtils.getDailyStreak(applicationContext)
                
                val title = when {
                    streak >= 5 -> "🔥 Your ${streak}-day streak is at risk!"
                    streak >= 1 -> "⚡ Don't lose your ${streak}-day streak!"
                    else -> "📱 Your device misses you!"
                }
                
                val message = when {
                    streak >= 5 -> "Quick scan to maintain your impressive streak!"
                    streak >= 1 -> "Just 30 seconds to keep your streak alive!"
                    else -> "Check your device health in just 30 seconds!"
                }
                
                RetentionNotificationManager.sendNotification(title, message, RetentionNotificationManager.RETENTION_CHANNEL, applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
    
    private fun getLastAppOpenTime(context: Context): Long {
        return context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
            .getLong("last_app_open_time", 0L)
    }
}

/**
 * Streak reminder worker - reminds active users to maintain streaks
 */
class StreakReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val lastScanDate = HealthScoreUtils.getLastScanDate(applicationContext)
            val streak = HealthScoreUtils.getDailyStreak(applicationContext)
            
            // Only send if user hasn't scanned today and has an active streak
            if (lastScanDate != today && streak >= 1) {
                val title = when {
                    streak >= 10 -> "🔥 Legendary ${streak}-day streak!"
                    streak >= 7 -> "🏆 Amazing ${streak}-day streak!"
                    streak >= 3 -> "⚡ Great ${streak}-day streak!"
                    else -> "📱 Keep your ${streak}-day streak!"
                }
                
                val message = when {
                    streak >= 10 -> "Don't break your legendary streak! Quick scan now!"
                    streak >= 7 -> "You're on fire! Maintain your amazing streak!"
                    streak >= 3 -> "You're doing great! Keep the streak alive!"
                    else -> "Quick scan to continue your streak!"
                }
                
                // Use sendNotification which has deduplication built-in
                RetentionNotificationManager.sendNotification(title, message, RetentionNotificationManager.ENGAGEMENT_CHANNEL, applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * Achievement worker - celebrates when users unlock achievements
 * Checks for newly unlocked achievements and sends celebration notifications
 */
class AchievementWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            // Check for newly unlocked achievements
            val unlockedAchievements = PowerAchievements.getUnlockedAchievements(applicationContext)
            val prefs = applicationContext.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
            val lastCelebratedAchievements = prefs.getStringSet("last_celebrated_achievements", emptySet()) ?: emptySet()
            
            // Find newly unlocked achievements
            val newAchievements = unlockedAchievements - lastCelebratedAchievements
            
            if (newAchievements.isNotEmpty()) {
                // Get achievement details
                val achievement = PowerAchievements.ALL_ACHIEVEMENTS.find { it.id in newAchievements }
                
                if (achievement != null) {
                    val title = "🎉 Achievement Unlocked: ${achievement.icon} ${achievement.title}!"
                    val message = achievement.description
                    
                    RetentionNotificationManager.sendNotification(
                        title,
                        message,
                        RetentionNotificationManager.ENGAGEMENT_CHANNEL,
                        applicationContext
                    )
                    
                    // Mark as celebrated
                    prefs.edit {
                        putStringSet("last_celebrated_achievements", unlockedAchievements)
                    }
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * Milestone worker - celebrates important milestones
 * Celebrates streaks, scan counts, and other milestones
 */
class MilestoneWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val streak = HealthScoreUtils.getDailyStreak(applicationContext)
            val totalScans = HealthScoreUtils.getTotalScans(applicationContext)
            val bestScore = HealthScoreUtils.getBestScore(applicationContext)
            val prefs = applicationContext.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
            val lastCelebratedMilestone = prefs.getString("last_celebrated_milestone", "")
            
            // Check for streak milestones
            val streakMilestones = listOf(5, 10, 15, 20, 30, 50, 100)
            val currentStreakMilestone = streakMilestones.find { streak >= it && streak < it + 1 }
            
            if (currentStreakMilestone != null && lastCelebratedMilestone != "streak_$currentStreakMilestone") {
                val title = when {
                    currentStreakMilestone >= 50 -> "🏆 LEGENDARY ${currentStreakMilestone}-Day Streak!"
                    currentStreakMilestone >= 30 -> "🔥 INCREDIBLE ${currentStreakMilestone}-Day Streak!"
                    currentStreakMilestone >= 20 -> "⭐ AMAZING ${currentStreakMilestone}-Day Streak!"
                    currentStreakMilestone >= 10 -> "⚡ IMPRESSIVE ${currentStreakMilestone}-Day Streak!"
                    else -> "🎯 Great ${currentStreakMilestone}-Day Streak!"
                }
                
                val message = when {
                    currentStreakMilestone >= 50 -> "You're a true champion! Keep it going!"
                    currentStreakMilestone >= 30 -> "Outstanding dedication! You're unstoppable!"
                    currentStreakMilestone >= 20 -> "Incredible consistency! Keep it up!"
                    currentStreakMilestone >= 10 -> "Double digits! You're on fire!"
                    else -> "Nice milestone! Keep building your streak!"
                }
                
                RetentionNotificationManager.sendNotification(
                    title,
                    message,
                    RetentionNotificationManager.ENGAGEMENT_CHANNEL,
                    applicationContext
                )
                
                prefs.edit {
                    putString("last_celebrated_milestone", "streak_$currentStreakMilestone")
                }
            }
            
            // Check for scan count milestones
            val scanMilestones = listOf(10, 25, 50, 100, 250, 500)
            val currentScanMilestone = scanMilestones.find { totalScans >= it && totalScans < it + 5 }
            
            if (currentScanMilestone != null && lastCelebratedMilestone != "scans_$currentScanMilestone") {
                val title = "📊 ${currentScanMilestone} Scans Milestone!"
                val message = "You've completed ${totalScans} health scans! Your dedication is impressive!"
                
                RetentionNotificationManager.sendNotification(
                    title,
                    message,
                    RetentionNotificationManager.ENGAGEMENT_CHANNEL,
                    applicationContext
                )
                
                prefs.edit {
                    putString("last_celebrated_milestone", "scans_$currentScanMilestone")
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * Personalized tip worker - sends helpful tips based on user behavior
 * Provides actionable insights without being intrusive
 */
class PersonalizedTipWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val streak = HealthScoreUtils.getDailyStreak(applicationContext)
            val totalScans = HealthScoreUtils.getTotalScans(applicationContext)
            // Use runBlocking since Worker runs on background thread
            val healthScore = kotlinx.coroutines.runBlocking {
                HealthScoreUtils.calculateDailyHealthScore(applicationContext)
            }
            val lastScanDate = HealthScoreUtils.getLastScanDate(applicationContext)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            
            // Only send tips if user hasn't scanned today (gentle reminder)
            if (lastScanDate != today) {
                val tips = mutableListOf<Pair<String, String>>()
                
                // Tip based on streak
                when {
                    streak == 0 && totalScans < 5 -> {
                        tips.add("💡 Start Your Journey" to "Begin a daily health check streak today! Just 30 seconds to get started.")
                    }
                    streak >= 1 && streak < 3 -> {
                        tips.add("⚡ Build Your Streak" to "You're on a ${streak}-day streak! Keep it going with a quick scan today.")
                    }
                    streak >= 3 && streak < 7 -> {
                        tips.add("🔥 Streak Building" to "Great ${streak}-day streak! One more day to hit a week!")
                    }
                }
                
                // Tip based on health score
                if (healthScore < 6) {
                    tips.add("🚨 Device Health Alert" to "Your device health score is ${healthScore}/10. Check what needs attention!")
                } else if (healthScore >= 9) {
                    tips.add("✨ Excellent Health" to "Your device is in great shape (${healthScore}/10)! Keep up the good maintenance.")
                }
                
                // Tip based on total scans
                when {
                    totalScans >= 50 && totalScans < 100 -> {
                        tips.add("📊 Halfway to 100" to "You've completed ${totalScans} scans! You're halfway to 100!")
                    }
                    totalScans >= 100 -> {
                        tips.add("🎯 Power User" to "Wow! ${totalScans} scans completed! You're a true power user!")
                    }
                }
                
                // Send a random tip if available
                if (tips.isNotEmpty()) {
                    val (title, message) = tips.random()
                    RetentionNotificationManager.sendNotification(
                        title,
                        message,
                        RetentionNotificationManager.ENGAGEMENT_CHANNEL,
                        applicationContext
                    )
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
} 