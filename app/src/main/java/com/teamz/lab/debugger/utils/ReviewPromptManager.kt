package com.teamz.lab.debugger.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.teamz.lab.debugger.BuildConfig
import com.teamz.lab.debugger.services.userHasAlreadyReviewed
import com.teamz.lab.debugger.services.setAlreadyReviewed
import com.teamz.lab.debugger.utils.LeaderboardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Review Prompt Manager
 * 
 * Manages automatic review prompts using Google Play In-App Review API.
 * Follows Google's best practices:
 * - Shows after positive user experience (not on first launch)
 * - Respects frequency limits (Google allows max 3 prompts per year)
 * - Shows at appropriate times (after app opens, with delay)
 * - Only shows if user hasn't reviewed yet
 */
object ReviewPromptManager {
    private const val TAG = "ReviewPromptManager"
    private const val PREFS_NAME = "review_prompt_prefs"
    
    // Firebase collection
    private const val FIRESTORE_COLLECTION = "user_reviews"
    private const val FIRESTORE_FIELD_HAS_REVIEWED = "has_reviewed"
    private const val FIRESTORE_FIELD_REVIEWED_DATE = "reviewed_date"
    
    // Configuration - "Review First, Paywall After" strategy
    // Show review on FIRST session, then paywall chains after it
    // Users subconsciously rate 5 stars before seeing pricing
    private const val MIN_APP_OPENS_BEFORE_PROMPT = 1 // Show from first session
    private const val MIN_MEANINGFUL_INTERACTIONS = 1 // OR 1 meaningful interaction
    private const val MIN_DAYS_BETWEEN_PROMPTS = 30 // Don't show more than once per month
    private const val DELAY_BEFORE_SHOWING_MS = 3000L // Fallback: 3s after app opens (overridden by RemoteConfig)
    private const val DELAY_AFTER_INTERACTION_MS = 3000L // Wait 3 seconds after positive interaction
    private const val DELAY_FIRST_LAUNCH_MS = 15000L // Fallback: 15s on first launch (overridden by RemoteConfig)
    private const val ENABLE_FIRST_LAUNCH_REVIEW = true // Show review on first session

    // Signal for chaining paywall after review flow completes
    // Strategy: Review first → Paywall after (users rate positively before seeing pricing)
    private val _reviewFlowCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reviewFlowCompleted: SharedFlow<Unit> = _reviewFlowCompleted
    
    // Preference keys
    private const val KEY_APP_OPEN_COUNT = "app_open_count"
    private const val KEY_MEANINGFUL_INTERACTIONS = "meaningful_interactions"
    private const val KEY_LAST_PROMPT_TIME = "last_prompt_time"
    private const val KEY_FIRST_LAUNCH_DATE = "first_launch_date"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    /**
     * Track app open and show review prompt if conditions are met
     * 
     * @param activity The activity context
     * @param isColdStart Whether this is a cold start (first launch after app was closed)
     */
    fun trackAppOpenAndMaybeShowReview(activity: Activity, isColdStart: Boolean) {
        val context = activity.applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Check if user already reviewed (local check first - fast)
        if (context.userHasAlreadyReviewed()) {
            Log.d(TAG, "trackAppOpenAndMaybeShowReview() - User already reviewed (local), skipping")
            // Still sync in background to keep Firebase updated
            CoroutineScope(Dispatchers.IO).launch {
                syncReviewStatusFromFirebase(context)
            }
            return
        }
        
        // Sync review status from Firebase (if user is logged in) - non-blocking
        // This ensures review status is synced across devices
        // Note: We check local first (fast), then sync in background
        CoroutineScope(Dispatchers.IO).launch {
            syncReviewStatusFromFirebase(context)
        }
        
        // Track first launch date
        val firstLaunchDate = prefs.getLong(KEY_FIRST_LAUNCH_DATE, 0L)
        val isFirstLaunch = firstLaunchDate == 0L
        
        if (isFirstLaunch) {
            prefs.edit {
                putLong(KEY_FIRST_LAUNCH_DATE, System.currentTimeMillis())
            }
            Log.d(TAG, "trackAppOpenAndMaybeShowReview() - First launch detected")
            
            // Show review on first launch if enabled (with configurable delay to let user see the app)
            if (ENABLE_FIRST_LAUNCH_REVIEW && !context.userHasAlreadyReviewed()) {
                val firstLaunchDelay = RemoteConfigUtils.getReviewDelayFirstLaunchMs()
                Log.d(TAG, "trackAppOpenAndMaybeShowReview() - Will show review on first launch after ${firstLaunchDelay}ms")
                CoroutineScope(Dispatchers.Main).launch {
                    delay(firstLaunchDelay) // Configurable via RemoteConfig: review_delay_first_launch_ms
                    
                    // Double-check user hasn't reviewed in the meantime
                    try {
                        withTimeout(500) {
                            syncReviewStatusFromFirebase(context)
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "trackAppOpenAndMaybeShowReview() - Sync timeout/error on first launch, proceeding")
                    }
                    
                    // Final check before showing
                    if (!context.userHasAlreadyReviewed()) {
                        Log.d(TAG, "trackAppOpenAndMaybeShowReview() - ✅ Showing review prompt on first launch")
                        showReviewPrompt(activity)
                    } else {
                        Log.d(TAG, "trackAppOpenAndMaybeShowReview() - User already reviewed, skipping first launch prompt")
                    }
                }
            } else {
                Log.d(TAG, "trackAppOpenAndMaybeShowReview() - First launch review disabled or user already reviewed")
            }
            
            // Still increment app open count for future prompts
            prefs.edit {
                putInt(KEY_APP_OPEN_COUNT, 1)
            }
            return
        }
        
        // Increment app open count
        val appOpenCount = prefs.getInt(KEY_APP_OPEN_COUNT, 0) + 1
        prefs.edit {
            putInt(KEY_APP_OPEN_COUNT, appOpenCount)
        }
        Log.d(TAG, "trackAppOpenAndMaybeShowReview() - App open count: $appOpenCount")
        
        // Check if we should show review prompt
        if (shouldShowReviewPrompt(context)) {
            // Show review after a configurable delay (so it doesn't interrupt app opening)
            val returningDelay = RemoteConfigUtils.getReviewDelayReturningMs()
            CoroutineScope(Dispatchers.Main).launch {
                delay(returningDelay) // Configurable via RemoteConfig: review_delay_returning_ms
                
                // Double-check conditions before showing (user might have reviewed in the meantime)
                // Quick sync before showing (with timeout to avoid blocking too long)
                try {
                    // Try to sync quickly (500ms timeout) before showing
                    withTimeout(500) {
                        syncReviewStatusFromFirebase(context)
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.d(TAG, "trackAppOpenAndMaybeShowReview() - Sync timeout, proceeding with local check")
                } catch (e: Exception) {
                    Log.w(TAG, "trackAppOpenAndMaybeShowReview() - Sync error, proceeding with local check", e)
                }
                
                // Final check before showing
                if (!context.userHasAlreadyReviewed() && shouldShowReviewPrompt(context)) {
                    Log.d(TAG, "trackAppOpenAndMaybeShowReview() - ✅ Showing review prompt")
                    showReviewPrompt(activity)
                } else {
                    Log.d(TAG, "trackAppOpenAndMaybeShowReview() - ⚠️ Conditions changed, not showing review")
                }
            }
        } else {
            Log.d(TAG, "trackAppOpenAndMaybeShowReview() - ⚠️ Not showing review (conditions not met)")
        }
    }
    
    /**
     * Track a meaningful user interaction (e.g., completed scan, used AI, completed experiment)
     * This is better than just tracking app opens - shows review after positive experiences
     * 
     * @param activity The activity context
     * @param interactionType Type of interaction (for logging/debugging)
     */
    fun trackMeaningfulInteraction(activity: Activity, interactionType: String = "unknown") {
        val context = activity.applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Check if user already reviewed (local check first - fast)
        if (context.userHasAlreadyReviewed()) {
            Log.d(TAG, "trackMeaningfulInteraction() - User already reviewed (local), skipping")
            return
        }
        
        // Increment meaningful interaction count
        val interactionCount = prefs.getInt(KEY_MEANINGFUL_INTERACTIONS, 0) + 1
        prefs.edit {
            putInt(KEY_MEANINGFUL_INTERACTIONS, interactionCount)
        }
        Log.d(TAG, "trackMeaningfulInteraction() - Interaction '$interactionType' tracked. Total: $interactionCount")
        
        // Check if we should show review prompt after this positive interaction
        if (shouldShowReviewPrompt(context)) {
            // Show review after a delay (so it doesn't interrupt the user's flow)
            CoroutineScope(Dispatchers.Main).launch {
                delay(DELAY_AFTER_INTERACTION_MS)
                
                // Double-check conditions before showing
                try {
                    withTimeout(500) {
                        syncReviewStatusFromFirebase(context)
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.d(TAG, "trackMeaningfulInteraction() - Sync timeout, proceeding with local check")
                } catch (e: Exception) {
                    Log.w(TAG, "trackMeaningfulInteraction() - Sync error, proceeding with local check", e)
                }
                
                // Final check before showing
                if (!context.userHasAlreadyReviewed() && shouldShowReviewPrompt(context)) {
                    Log.d(TAG, "trackMeaningfulInteraction() - ✅ Showing review prompt after positive interaction: $interactionType")
                    showReviewPrompt(activity)
                } else {
                    Log.d(TAG, "trackMeaningfulInteraction() - ⚠️ Conditions changed, not showing review")
                }
            }
        } else {
            Log.d(TAG, "trackMeaningfulInteraction() - ⚠️ Not showing review yet (conditions not met)")
        }
    }
    
    /**
     * Check if we should show review prompt based on:
     * - App open count (minimum 3 opens) OR meaningful interactions (minimum 2)
     * - Time since last prompt (minimum 30 days)
     * - User hasn't reviewed yet
     */
    private fun shouldShowReviewPrompt(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Check if user already reviewed (local check - fast)
        if (context.userHasAlreadyReviewed()) {
            Log.d(TAG, "shouldShowReviewPrompt() - User already reviewed")
            return false
        }
        
        // Check time since last prompt
        val lastPromptTime = prefs.getLong(KEY_LAST_PROMPT_TIME, 0L)
        if (lastPromptTime > 0) {
            val timeSinceLastPrompt = System.currentTimeMillis() - lastPromptTime
            val daysSinceLastPrompt = timeSinceLastPrompt / (24 * 60 * 60 * 1000)
            
            if (daysSinceLastPrompt < MIN_DAYS_BETWEEN_PROMPTS) {
                Log.d(TAG, "shouldShowReviewPrompt() - Only $daysSinceLastPrompt days since last prompt (need $MIN_DAYS_BETWEEN_PROMPTS)")
                return false
            }
        }
        
        // Check app open count OR meaningful interactions
        val appOpenCount = prefs.getInt(KEY_APP_OPEN_COUNT, 0)
        val interactionCount = prefs.getInt(KEY_MEANINGFUL_INTERACTIONS, 0)

        val hasEnoughAppOpens = appOpenCount >= MIN_APP_OPENS_BEFORE_PROMPT
        val hasEnoughInteractions = interactionCount >= MIN_MEANINGFUL_INTERACTIONS

        if (!hasEnoughAppOpens && !hasEnoughInteractions) {
            Log.d(TAG, "shouldShowReviewPrompt() - App opens ($appOpenCount) < $MIN_APP_OPENS_BEFORE_PROMPT AND interactions ($interactionCount) < $MIN_MEANINGFUL_INTERACTIONS")
            return false
        }

        Log.d(TAG, "shouldShowReviewPrompt() - ✅ All conditions met, should show review (opens: $appOpenCount, interactions: $interactionCount)")
        return true
    }
    
    /**
     * Show Google Play In-App Review prompt
     * Uses the official Google Play In-App Review API
     * 
     * Note: In DEBUG builds, the review prompt may not show due to Google Play API limitations.
     * This is expected behavior - test on release builds or internal testing track.
     */
    private fun showReviewPrompt(activity: Activity) {
        try {
            val context = activity.applicationContext
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Check if activity is still valid
            if (activity.isFinishing || activity.isDestroyed) {
                Log.w(TAG, "showReviewPrompt() - ⚠️ Activity is finishing/destroyed, skipping review prompt")
                return
            }
            
            // Update last prompt time
            prefs.edit {
                putLong(KEY_LAST_PROMPT_TIME, System.currentTimeMillis())
            }
            
            // Track analytics
            AnalyticsUtils.logEvent(AnalyticsEvent.ReviewRequested)
            
            // IMPORTANT: Google Play In-App Review API limitations
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "showReviewPrompt() - ⚠️ DEBUG BUILD DETECTED")
                Log.w(TAG, "showReviewPrompt() - Google Play In-App Review API may not show in debug builds")
                Log.w(TAG, "showReviewPrompt() - This is expected! Test on release builds or internal testing track")
                Log.w(TAG, "showReviewPrompt() - Attempting to show anyway (may fail silently)...")
            }
            
            Log.d(TAG, "showReviewPrompt() - 🚀 Requesting review flow from Google Play...")
            
            // Use Google Play In-App Review API
            val manager = ReviewManagerFactory.create(activity)
            val request = manager.requestReviewFlow()
            
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Review flow is available, show it
                    val reviewInfo = task.result
                    Log.d(TAG, "showReviewPrompt() - ✅ Review flow available, launching...")
                    
                    // Double-check activity is still valid before showing
                    if (activity.isFinishing || activity.isDestroyed) {
                        Log.w(TAG, "showReviewPrompt() - ⚠️ Activity finished before showing review, skipping")
                        return@addOnCompleteListener
                    }
                    
                    manager.launchReviewFlow(activity, reviewInfo).addOnCompleteListener {
                        Log.d(TAG, "showReviewPrompt() - ✅ Review flow completed (user may have rated or dismissed)")
                        // Note: We don't automatically set "already reviewed" here because:
                        // 1. User might dismiss without rating
                        // 2. Google Play API handles frequency limits automatically
                        // 3. We'll sync from Firebase if user reviewed on another device

                        // Signal paywall chain: review done → now show paywall
                        _reviewFlowCompleted.tryEmit(Unit)
                    }
                } else {
                    // Review flow not available
                    val error = task.exception
                    val errorMessage = error?.message ?: "Unknown error"
                    
                    Log.w(TAG, "showReviewPrompt() - ⚠️ Review flow not available: $errorMessage")
                    
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "showReviewPrompt() - ⚠️ DEBUG BUILD: This is expected!")
                        Log.w(TAG, "showReviewPrompt() - The review prompt typically doesn't show in debug builds")
                        Log.w(TAG, "showReviewPrompt() - To test:")
                        Log.w(TAG, "  1. Build a release APK: ./gradlew assembleRelease")
                        Log.w(TAG, "  2. Upload to Internal Testing track in Play Console")
                        Log.w(TAG, "  3. Install from Play Store (not directly)")
                        Log.w(TAG, "  4. The review prompt will show on release builds")
                    } else {
                        Log.w(TAG, "showReviewPrompt() - This is normal if:")
                        Log.w(TAG, "  - User has already reviewed recently")
                        Log.w(TAG, "  - Device doesn't support in-app review")
                        Log.w(TAG, "  - Too many review requests (Google limits to 3 per year)")
                    }
                    
                    // Fallback: Open Play Store page (but don't mark as reviewed)
                    // Only do this in release builds to avoid confusion
                    if (!BuildConfig.DEBUG) {
                        try {
                            val packageName = activity.packageName
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                            if (intent.resolveActivity(activity.packageManager) != null) {
                                activity.startActivity(intent)
                                AnalyticsUtils.logEvent(AnalyticsEvent.ReviewOpenedPlayStore)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to open Play Store", e)
                        }
                    }

                    // Signal paywall chain even on failure: review attempted → now show paywall
                    _reviewFlowCompleted.tryEmit(Unit)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "showReviewPrompt() - ❌ Error showing review prompt", e)
            ErrorHandler.handleError(e, context = "ReviewPromptManager.showReviewPrompt")
        }
    }
    
    /**
     * Check if user has reviewed (checks local only - fast)
     * Firebase sync happens in background, so we rely on local status
     * which gets updated when sync completes
     */
    private fun hasUserReviewed(context: Context): Boolean {
        // Check local only (fast, non-blocking)
        // Firebase sync happens in background and updates local status
        return context.userHasAlreadyReviewed()
    }
    
    /**
     * Sync review status from Firebase
     * This ensures if user reviewed on Device A, Device B will know about it
     */
    private suspend fun syncReviewStatusFromFirebase(context: Context) {
        // Explicitly check if user is authenticated
        var currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "syncReviewStatusFromFirebase() - No authenticated user, skipping")
            return
        }
        
        try {
            // Add small delay to ensure auth token is ready
            kotlinx.coroutines.delay(100)
            
            // Double-check authentication after delay
            currentUser = auth.currentUser
            if (currentUser == null) {
                Log.d(TAG, "syncReviewStatusFromFirebase() - User not authenticated after delay, skipping")
                return
            }
            
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastSyncTime = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
            val timeSinceSync = System.currentTimeMillis() - lastSyncTime
            val oneHour = 60 * 60 * 1000L
            
            // Only sync if it's been more than 1 hour (avoid too frequent syncs)
            if (timeSinceSync < oneHour) {
                Log.d(TAG, "syncReviewStatusFromFirebase() - Recently synced, skipping")
                return
            }
            
            Log.d(TAG, "syncReviewStatusFromFirebase() - Syncing review status for user: ${currentUser.uid}")
            
            // Use Source.SERVER to ensure fresh data and bypass cache issues
            val reviewDoc = db.collection(FIRESTORE_COLLECTION)
                .document(currentUser.uid)
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()
            
            if (reviewDoc.exists()) {
                val hasReviewed = reviewDoc.getBoolean(FIRESTORE_FIELD_HAS_REVIEWED) ?: false
                
                if (hasReviewed) {
                    // User reviewed on another device - update local status
                    Log.d(TAG, "syncReviewStatusFromFirebase() - ✅ User reviewed on another device, updating local status")
                    context.setAlreadyReviewed(true)
                }
                
                // Update last sync time
                prefs.edit {
                    putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                }
            } else {
                // No review data in Firebase yet - check if local says reviewed and upload it
                if (context.userHasAlreadyReviewed()) {
                    Log.d(TAG, "syncReviewStatusFromFirebase() - Local says reviewed, uploading to Firebase")
                    saveReviewStatusToFirebase(context, true)
                }
                
                // Update last sync time
                prefs.edit {
                    putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                }
            }
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            // Handle PERMISSION_DENIED specifically
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Log.w(TAG, "syncReviewStatusFromFirebase() - ⚠️ Permission denied (user may not be authenticated or rules may not allow read)")
            } else {
                Log.e(TAG, "syncReviewStatusFromFirebase() - ❌ Error syncing review status", e)
            }
            // Don't throw - this is a background sync, shouldn't block app
        } catch (e: Exception) {
            Log.e(TAG, "syncReviewStatusFromFirebase() - ❌ Error syncing review status", e)
            // Don't throw - this is a background sync, shouldn't block app
        }
    }
    
    /**
     * Save review status to Firebase
     * This ensures review status is synced across all user's devices
     * Can be called from external code (e.g., when user manually reviews)
     */
    suspend fun saveReviewStatusToFirebase(context: Context, hasReviewed: Boolean) {
        // Explicitly check if user is authenticated
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "saveReviewStatusToFirebase() - No authenticated user, skipping")
            return
        }
        
        try {
            Log.d(TAG, "saveReviewStatusToFirebase() - Saving review status: $hasReviewed for user: ${currentUser.uid}")
            
            val reviewData = hashMapOf(
                FIRESTORE_FIELD_HAS_REVIEWED to hasReviewed,
                FIRESTORE_FIELD_REVIEWED_DATE to if (hasReviewed) System.currentTimeMillis() else null,
                "userId" to currentUser.uid,
                "lastUpdated" to System.currentTimeMillis()
            )
            
            db.collection(FIRESTORE_COLLECTION)
                .document(currentUser.uid)
                .set(reviewData)
                .await()
            
            Log.d(TAG, "saveReviewStatusToFirebase() - ✅ Review status saved to Firebase")
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            // Handle PERMISSION_DENIED specifically
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Log.w(TAG, "saveReviewStatusToFirebase() - ⚠️ Permission denied (user may not be authenticated or rules may not allow write)")
            } else {
                Log.e(TAG, "saveReviewStatusToFirebase() - ❌ Error saving review status", e)
            }
            // Don't throw - this is a background operation
        } catch (e: Exception) {
            Log.e(TAG, "saveReviewStatusToFirebase() - ❌ Error saving review status", e)
            // Don't throw - this is a background operation
        }
    }
    
    /**
     * Reset review prompt data (for testing purposes)
     */
    @Suppress("unused")
    fun resetReviewPromptData(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            clear()
        }
        Log.d(TAG, "resetReviewPromptData() - Review prompt data reset")
    }
    
    /**
     * Get current review prompt state (for debugging)
     */
    @Suppress("unused")
    fun getReviewPromptState(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val appOpenCount = prefs.getInt(KEY_APP_OPEN_COUNT, 0)
        val lastPromptTime = prefs.getLong(KEY_LAST_PROMPT_TIME, 0L)
        val firstLaunchDate = prefs.getLong(KEY_FIRST_LAUNCH_DATE, 0L)
        val hasReviewed = context.userHasAlreadyReviewed()
        
        val daysSinceLastPrompt = if (lastPromptTime > 0) {
            (System.currentTimeMillis() - lastPromptTime) / (24 * 60 * 60 * 1000)
        } else {
            -1
        }
        
        val interactionCount = prefs.getInt(KEY_MEANINGFUL_INTERACTIONS, 0)
        
        return """
            ReviewPromptManager State:
            - App Open Count: $appOpenCount (need $MIN_APP_OPENS_BEFORE_PROMPT)
            - Meaningful Interactions: $interactionCount (need $MIN_MEANINGFUL_INTERACTIONS)
            - Has Reviewed: $hasReviewed
            - Last Prompt: ${if (lastPromptTime > 0) "$daysSinceLastPrompt days ago" else "Never"}
            - First Launch: ${if (firstLaunchDate > 0) "${(System.currentTimeMillis() - firstLaunchDate) / (24 * 60 * 60 * 1000)} days ago" else "Not tracked"}
            - Should Show: ${shouldShowReviewPrompt(context)}
        """.trimIndent()
    }
}

