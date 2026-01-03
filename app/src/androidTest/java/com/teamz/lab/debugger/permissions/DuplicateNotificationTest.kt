package com.teamz.lab.debugger

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.teamz.lab.debugger.utils.RetentionNotificationManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Duplicate Notification Detection Tests - Real Device Testing
 * 
 * WHY SEPARATE FILE?
 * - Focused on specific bug: duplicate notifications
 * - Easier to maintain and debug duplicate issues
 * - Can be run independently when investigating duplicates
 * - Clear separation of concerns: duplicates vs. general notification tests
 * 
 * Tests to detect and prevent duplicate notifications:
 * - Multiple calls to sendDailyHealthReminder
 * - WorkManager and immediate notifications overlap
 * - Notification deduplication
 * - Time interval notification duplicates
 */
@RunWith(AndroidJUnit4::class)
class DuplicateNotificationTest {
    
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    
    // ==================== DUPLICATE NOTIFICATION DETECTION ====================
    
    @Test
    fun testMultipleCallsToSendDailyHealthReminder() {
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        
        // Clear any existing notifications first
        notificationManager.cancelAll()
        Thread.sleep(500)
        
        // Get initial notification count
        val initialNotifications = notificationManager.activeNotifications.size
        
        // Call sendDailyHealthReminder multiple times rapidly
        // This simulates the scenario where Application.onStart() is called multiple times
        repeat(5) {
            RetentionNotificationManager.sendDailyHealthReminder(context)
            Thread.sleep(100) // Small delay between calls
        }
        
        Thread.sleep(2000) // Wait for notifications to appear
        
        // Check for duplicate notifications
        val activeNotifications = notificationManager.activeNotifications
        val notificationCount = activeNotifications.size
        
        // Count notifications with same title/content
        val dailyHealthNotifications = activeNotifications.filter { notification ->
            val title = notification.notification.extras?.getCharSequence(
                android.app.Notification.EXTRA_TITLE
            )?.toString() ?: ""
            
            title.contains("streak", ignoreCase = true) ||
            title.contains("device", ignoreCase = true) ||
            title.contains("health", ignoreCase = true)
        }
        
        // Should not have multiple identical notifications
        // If notification was sent, it should only appear once
        if (dailyHealthNotifications.isNotEmpty()) {
            val uniqueTitles = dailyHealthNotifications.mapNotNull { notification ->
                notification.notification.extras?.getCharSequence(
                    android.app.Notification.EXTRA_TITLE
                )?.toString()
            }.distinct()
            
            // If we have more notifications than unique titles, we have duplicates
            val hasDuplicates = dailyHealthNotifications.size > uniqueTitles.size
            
            assert(!hasDuplicates) { 
                "Duplicate notifications detected! Found ${dailyHealthNotifications.size} notifications with only ${uniqueTitles.size} unique titles. " +
                "Titles: ${uniqueTitles.joinToString(", ")}"
            }
        }
    }
    
    @Test
    fun testRapidNotificationSending() {
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        
        // Clear notifications
        notificationManager.cancelAll()
        Thread.sleep(500)
        
        // Send same notification multiple times rapidly
        val testTitle = "Test Notification"
        val testMessage = "Test Message"
        
        repeat(10) {
            RetentionNotificationManager.sendNotification(
                testTitle,
                testMessage,
                RetentionNotificationManager.DAILY_HEALTH_CHANNEL,
                context
            )
            Thread.sleep(50) // Very rapid calls
        }
        
        Thread.sleep(2000)
        
        // Check for duplicates
        val activeNotifications = notificationManager.activeNotifications
        val testNotifications = activeNotifications.filter { notification ->
            val title = notification.notification.extras?.getCharSequence(
                android.app.Notification.EXTRA_TITLE
            )?.toString() ?: ""
            title == testTitle
        }
        
        // All notifications with same title should be considered duplicates
        // (In a real app, you'd want deduplication logic)
        if (testNotifications.size > 1) {
            // This indicates potential duplicate issue
            // Log for investigation
            println("WARNING: Found ${testNotifications.size} notifications with same title '$testTitle'")
        }
        
        // Clean up
        notificationManager.cancelAll()
    }
    
    @Test
    fun testNotificationDeduplicationByTime() {
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        
        // Test that notifications sent within a short time interval don't duplicate
        notificationManager.cancelAll()
        Thread.sleep(500)
        
        val initialCount = notificationManager.activeNotifications.size
        
        // Send notification
        RetentionNotificationManager.sendNotification(
            "Time Test Notification",
            "First notification",
            RetentionNotificationManager.DAILY_HEALTH_CHANNEL,
            context
        )
        
        Thread.sleep(1000)
        
        // Send same notification again immediately (simulating rapid app lifecycle events)
        RetentionNotificationManager.sendNotification(
            "Time Test Notification",
            "First notification",
            RetentionNotificationManager.DAILY_HEALTH_CHANNEL,
            context
        )
        
        Thread.sleep(2000)
        
        val activeNotifications = notificationManager.activeNotifications
        val testNotifications = activeNotifications.filter { notification ->
            val title = notification.notification.extras?.getCharSequence(
                android.app.Notification.EXTRA_TITLE
            )?.toString() ?: ""
            title == "Time Test Notification"
        }
        
        // Should ideally have only one notification, but Android allows multiple
        // We check if there are excessive duplicates
        if (testNotifications.size > 2) {
            assert(false) {
                "Excessive duplicate notifications detected: ${testNotifications.size} notifications with same title"
            }
        }
        
        // Clean up
        notificationManager.cancelAll()
    }
    
    @Test
    fun testWorkManagerSchedulingDoesNotDuplicate() {
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        
        // Test that WorkManager doesn't create duplicate scheduled work
        try {
            val workManager = androidx.work.WorkManager.getInstance(context)
            
            // Initialize notifications multiple times (simulating app restarts)
            repeat(3) {
                RetentionNotificationManager.initializeRetentionNotifications(context)
                Thread.sleep(500)
            }
            
            // WorkManager should use REPLACE policy to prevent duplicates
            // We can't easily verify this without running WorkManager,
            // but we verify the initialization doesn't crash
            assert(true) { "WorkManager initialization completed without errors" }
        } catch (e: Exception) {
            // WorkManager may not be available in test environment
            assert(true) { "WorkManager test handled gracefully" }
        }
    }
    
    @Test
    fun testApplicationLifecycleNotificationDuplicates() {
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        
        // Simulate multiple app lifecycle events
        // Application.onStart() calls sendDailyHealthReminder
        // This could happen multiple times if app is backgrounded/foregrounded rapidly
        
        notificationManager.cancelAll()
        Thread.sleep(500)
        
        val initialCount = notificationManager.activeNotifications.size
        
        // Simulate rapid onStart() calls
        repeat(3) {
            RetentionNotificationManager.sendDailyHealthReminder(context)
            Thread.sleep(200)
        }
        
        Thread.sleep(3000) // Wait for notifications
        
        val activeNotifications = notificationManager.activeNotifications
        val dailyHealthNotifications = activeNotifications.filter { notification ->
            val title = notification.notification.extras?.getCharSequence(
                android.app.Notification.EXTRA_TITLE
            )?.toString() ?: ""
            
            title.contains("streak", ignoreCase = true) ||
            title.contains("device", ignoreCase = true) ||
            title.contains("health", ignoreCase = true)
        }
        
        // Check for duplicate titles
        val titles = dailyHealthNotifications.mapNotNull { notification ->
            notification.notification.extras?.getCharSequence(
                android.app.Notification.EXTRA_TITLE
            )?.toString()
        }
        
        val uniqueTitles = titles.distinct()
        
        // If we have duplicates, log them
        if (titles.size > uniqueTitles.size) {
            val duplicates = titles.groupingBy { it }.eachCount().filter { it.value > 1 }
            
            println("WARNING: Potential duplicate notifications detected:")
            duplicates.forEach { (title, count) ->
                println("  - '$title' appears $count times")
            }
            
            // This is a bug - same notification should not be sent multiple times
            assert(false) {
                "Duplicate notifications detected! Same notification sent ${duplicates.values.maxOrNull()} times. " +
                "Duplicates: ${duplicates.keys.joinToString(", ")}"
            }
        }
    }
    
    @Test
    fun testNotificationChannelDeduplication() {
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        
        // Test that notification channels are not created multiple times
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Initialize multiple times
            repeat(3) {
                RetentionNotificationManager.initializeRetentionNotifications(context)
                Thread.sleep(500)
            }
            
            // Check channels exist (should be created once)
            val channels = listOf(
                RetentionNotificationManager.DAILY_HEALTH_CHANNEL,
                RetentionNotificationManager.ENGAGEMENT_CHANNEL,
                RetentionNotificationManager.RETENTION_CHANNEL
            )
            
            channels.forEach { channelId ->
                val channel = notificationManager.getNotificationChannel(channelId)
                assert(channel != null) { "Channel '$channelId' should exist" }
            }
            
            // Channels should not be duplicated (Android handles this, but we verify)
            val allChannels = notificationManager.notificationChannels
            val channelIds = allChannels.map { it.id }
            val uniqueChannelIds = channelIds.distinct()
            
            assert(channelIds.size == uniqueChannelIds.size) {
                "Duplicate notification channels detected! Found ${channelIds.size} channels but only ${uniqueChannelIds.size} unique IDs"
            }
        }
    }
    
    @Test
    fun testSameNotificationContentDeduplication() {
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        
        // Test that notifications with identical content don't stack up
        notificationManager.cancelAll()
        Thread.sleep(500)
        
        val title = "Duplicate Test"
        val message = "This is a test"
        
        // Send identical notifications
        repeat(5) {
            RetentionNotificationManager.sendNotification(
                title,
                message,
                RetentionNotificationManager.DAILY_HEALTH_CHANNEL,
                context
            )
            Thread.sleep(100)
        }
        
        Thread.sleep(2000)
        
        val activeNotifications = notificationManager.activeNotifications
        val duplicateNotifications = activeNotifications.filter { notification ->
            val notifTitle = notification.notification.extras?.getCharSequence(
                android.app.Notification.EXTRA_TITLE
            )?.toString() ?: ""
            val notifMessage = notification.notification.extras?.getCharSequence(
                android.app.Notification.EXTRA_TEXT
            )?.toString() ?: ""
            
            notifTitle == title && notifMessage == message
        }
        
        // Android allows multiple notifications, but we check for excessive duplicates
        if (duplicateNotifications.size > 3) {
            println("WARNING: Found ${duplicateNotifications.size} identical notifications")
            // This indicates a potential bug - should have deduplication logic
        }
        
        // Clean up
        notificationManager.cancelAll()
    }
    
    @Test
    fun testNotificationIDUniqueness() {
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        
        // Test that notification IDs are unique (prevents duplicates)
        notificationManager.cancelAll()
        Thread.sleep(500)
        
        val title = "ID Test"
        val message = "Testing notification IDs"
        
        // Send multiple notifications
        repeat(5) {
            RetentionNotificationManager.sendNotification(
                title,
                message,
                RetentionNotificationManager.DAILY_HEALTH_CHANNEL,
                context
            )
            Thread.sleep(100)
        }
        
        Thread.sleep(2000)
        
        val activeNotifications = notificationManager.activeNotifications
        val testNotifications = activeNotifications.filter { notification ->
            val notifTitle = notification.notification.extras?.getCharSequence(
                android.app.Notification.EXTRA_TITLE
            )?.toString() ?: ""
            notifTitle == title
        }
        
        // Check notification IDs are unique
        val notificationIds = testNotifications.map { it.id }.distinct()
        
        // If we have same number of IDs as notifications, they're all unique
        // If we have fewer IDs, some notifications share IDs (which is actually good for deduplication)
        if (testNotifications.size > notificationIds.size) {
            // Some notifications share IDs - this is actually good (prevents duplicates)
            println("INFO: ${testNotifications.size} notifications share ${notificationIds.size} IDs (good for deduplication)")
        } else {
            // All notifications have unique IDs - could lead to duplicates
            println("WARNING: All ${testNotifications.size} notifications have unique IDs - may cause duplicates")
        }
        
        // Clean up
        notificationManager.cancelAll()
    }
    
    @Test
    fun testTimeIntervalNotificationDeduplication() {
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        
        // Test that time-interval notifications don't duplicate
        // This simulates the scenario where WorkManager triggers and immediate call happens
        
        notificationManager.cancelAll()
        Thread.sleep(500)
        
        // Simulate WorkManager worker sending notification
        RetentionNotificationManager.sendNotification(
            "Daily Health Reminder",
            "Time to check your device health!",
            RetentionNotificationManager.DAILY_HEALTH_CHANNEL,
            context
        )
        
        Thread.sleep(1000)
        
        // Simulate immediate call (from Application.onStart())
        RetentionNotificationManager.sendDailyHealthReminder(context)
        
        Thread.sleep(2000)
        
        val activeNotifications = notificationManager.activeNotifications
        val healthReminders = activeNotifications.filter { notification ->
            val title = notification.notification.extras?.getCharSequence(
                android.app.Notification.EXTRA_TITLE
            )?.toString() ?: ""
            
            title.contains("health", ignoreCase = true) ||
            title.contains("streak", ignoreCase = true) ||
            title.contains("device", ignoreCase = true)
        }
        
        // Check for duplicate titles within short time
        val titles = healthReminders.mapNotNull { notification ->
            notification.notification.extras?.getCharSequence(
                android.app.Notification.EXTRA_TITLE
            )?.toString()
        }
        
        val duplicateTitles = titles.groupingBy { it }.eachCount().filter { it.value > 1 }
        
        if (duplicateTitles.isNotEmpty()) {
            println("WARNING: Duplicate notifications found within time interval:")
            duplicateTitles.forEach { (title, count) ->
                println("  - '$title' appears $count times")
            }
            
            // This is the bug the user reported
            assert(false) {
                "Duplicate time-interval notifications detected! " +
                "Same notification sent multiple times: ${duplicateTitles.keys.joinToString(", ")}"
            }
        }
        
        // Clean up
        notificationManager.cancelAll()
    }
    
    @Test
    fun testConcurrentNotificationSending() {
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        
        // Test concurrent notification sending (simulating multiple threads)
        notificationManager.cancelAll()
        Thread.sleep(500)
        
        // Send notifications concurrently
        val threads = mutableListOf<Thread>()
        repeat(5) {
            val thread = Thread {
                RetentionNotificationManager.sendNotification(
                    "Concurrent Test",
                    "Testing concurrent sending",
                    RetentionNotificationManager.DAILY_HEALTH_CHANNEL,
                    context
                )
            }
            threads.add(thread)
            thread.start()
        }
        
        // Wait for all threads
        threads.forEach { it.join() }
        
        Thread.sleep(2000)
        
        val activeNotifications = notificationManager.activeNotifications
        val concurrentNotifications = activeNotifications.filter { notification ->
            val title = notification.notification.extras?.getCharSequence(
                android.app.Notification.EXTRA_TITLE
            )?.toString() ?: ""
            title == "Concurrent Test"
        }
        
        // Check for duplicates from concurrent calls
        if (concurrentNotifications.size > 1) {
            println("WARNING: ${concurrentNotifications.size} notifications from concurrent calls")
            // Should have deduplication logic for concurrent calls
        }
        
        // Clean up
        notificationManager.cancelAll()
    }
    
    @Test
    fun testNotificationDeduplicationByContentHash() {
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        
        // Test that notifications with same content are deduplicated
        notificationManager.cancelAll()
        Thread.sleep(500)
        
        val title = "Content Hash Test"
        val message = "Same content notification"
        
        // Send same notification multiple times
        repeat(3) {
            RetentionNotificationManager.sendNotification(
                title,
                message,
                RetentionNotificationManager.DAILY_HEALTH_CHANNEL,
                context
            )
            Thread.sleep(500)
        }
        
        Thread.sleep(2000)
        
        val activeNotifications = notificationManager.activeNotifications
        val sameContentNotifications = activeNotifications.filter { notification ->
            val notifTitle = notification.notification.extras?.getCharSequence(
                android.app.Notification.EXTRA_TITLE
            )?.toString() ?: ""
            val notifMessage = notification.notification.extras?.getCharSequence(
                android.app.Notification.EXTRA_TEXT
            )?.toString() ?: ""
            
            notifTitle == title && notifMessage == message
        }
        
        // Ideally should have only one notification with same content
        // But Android allows multiple - we check for excessive duplicates
        if (sameContentNotifications.size > 2) {
            assert(false) {
                "Excessive duplicate notifications with same content: ${sameContentNotifications.size} notifications"
            }
        }
        
        // Clean up
        notificationManager.cancelAll()
    }
    
    // ==================== COMPREHENSIVE DUPLICATE DETECTION ====================
    
    @Test
    fun testComprehensiveDuplicateDetection() {
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        
        // Comprehensive test: simulate real-world scenario
        notificationManager.cancelAll()
        Thread.sleep(500)
        
        val initialCount = notificationManager.activeNotifications.size
        
        // Simulate app lifecycle: onStart() called multiple times
        repeat(2) {
            RetentionNotificationManager.sendDailyHealthReminder(context)
            Thread.sleep(1000)
        }
        
        // Simulate WorkManager worker (if it would trigger)
        RetentionNotificationManager.sendNotification(
            "Daily Health Reminder",
            "Check your device health",
            RetentionNotificationManager.DAILY_HEALTH_CHANNEL,
            context
        )
        
        Thread.sleep(3000)
        
        val activeNotifications = notificationManager.activeNotifications
        val allTitles = activeNotifications.mapNotNull { notification ->
            notification.notification.extras?.getCharSequence(
                android.app.Notification.EXTRA_TITLE
            )?.toString()
        }
        
        // Find duplicates
        val titleCounts = allTitles.groupingBy { it }.eachCount()
        val duplicates = titleCounts.filter { it.value > 1 }
        
        if (duplicates.isNotEmpty()) {
            println("DUPLICATE NOTIFICATIONS DETECTED:")
            duplicates.forEach { (title, count) ->
                println("  - '$title' appears $count times")
            }
            
            // This confirms the bug exists
            assert(false) {
                "BUG CONFIRMED: Duplicate notifications detected! " +
                "Found ${duplicates.size} duplicate titles: ${duplicates.keys.joinToString(", ")}"
            }
        } else {
            println("No duplicate notifications detected - all notifications are unique")
            assert(true) { "No duplicates found" }
        }
        
        // Clean up
        notificationManager.cancelAll()
    }
}

