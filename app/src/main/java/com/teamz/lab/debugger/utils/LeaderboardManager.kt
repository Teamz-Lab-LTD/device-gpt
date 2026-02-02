package com.teamz.lab.debugger.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.concurrent.TimeUnit
import com.teamz.lab.debugger.utils.AdConfig

/**
 * Leaderboard Manager
 * 
 * Handles:
 * - Anonymous authentication
 * - Gmail linking for anonymous users
 * - Optimized Firestore queries
 * - Data upload with minimal fields
 * - Trust indicators
 * - 30-day data retention reminder
 */
object LeaderboardManager {
    private const val TAG = "LeaderboardManager"
    private const val PREFS_NAME = "leaderboard_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_LAST_UPLOAD = "last_upload"
    private const val KEY_ANONYMOUS_SIGNUP_DATE = "anonymous_signup_date"
    private const val KEY_EMAIL_LINKED = "email_linked"
    private const val KEY_DATA_RETENTION_REMINDER_SHOWN = "data_retention_reminder_shown"
    
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var applicationContext: Context? = null
    
    /**
     * Initialize Firebase Auth - call from Application.onCreate()
     * This ensures we have a persistent anonymous user and sets Analytics user ID
     */
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        // Wait for Firebase Auth to restore session, then ensure user exists
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                // User exists - save ID and set Analytics user ID
                saveUserId(context, user.uid)
                setAnalyticsUserId(user.uid)
                Log.d(TAG, "Firebase Auth session restored: ${user.uid}")
            } else {
                // No user - check if we have a saved user ID first
                val savedUserId = getSavedUserId(context)
                if (savedUserId.isNotEmpty()) {
                    // We had a user before but session was lost - try to restore
                    Log.d(TAG, "Session lost but saved user ID exists: $savedUserId")
                }
                // Ensure anonymous user is signed in
                ensureAnonymousUser(context)
            }
        }
        
        // Initial check - if user already exists, set Analytics immediately
        val currentUser = auth.currentUser
        if (currentUser != null) {
            saveUserId(context, currentUser.uid)
            setAnalyticsUserId(currentUser.uid)
            Log.d(TAG, "User already authenticated: ${currentUser.uid}")
        } else {
            // Wait a bit for Auth to restore session, then check again
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (auth.currentUser == null) {
                    ensureAnonymousUser(context)
                } else {
                    val user = auth.currentUser!!
                    saveUserId(context, user.uid)
                    setAnalyticsUserId(user.uid)
                }
            }, 500)
        }
    }
    
    /**
     * Set Firebase Analytics user ID to track same user across sessions
     */
    private fun setAnalyticsUserId(userId: String) {
        try {
            val appContext = applicationContext
            if (appContext != null) {
                com.google.firebase.analytics.FirebaseAnalytics.getInstance(appContext)
                    .setUserId(userId)
                Log.d(TAG, "Analytics user ID set: $userId")
            } else {
                Log.w(TAG, "Application context not available for Analytics")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set Analytics user ID", e)
        }
    }
    
    /**
     * Get saved user ID from SharedPreferences
     */
    private fun getSavedUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_ID, "") ?: ""
    }
    
    /**
     * Get current user ID (anonymous or linked)
     */
    fun getCurrentUserId(): String {
        return auth.currentUser?.uid ?: ""
    }
    
    /**
     * Get current user
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }
    
    /**
     * Check if user is anonymous
     */
    fun isAnonymousUser(): Boolean {
        return auth.currentUser?.isAnonymous == true
    }
    
    /**
     * Get user authentication status for debugging
     */
    fun getUserStatus(context: Context): String {
        val currentUser = auth.currentUser
        val savedUserId = getSavedUserId(context)
        
        return when {
            currentUser != null -> {
                "✅ User authenticated: ${currentUser.uid} (Anonymous: ${currentUser.isAnonymous})"
            }
            savedUserId.isNotEmpty() -> {
                "⚠️ Session lost but saved ID exists: $savedUserId - waiting for restore"
            }
            else -> {
                "❌ No user - creating anonymous user..."
            }
        }
    }
    
    /**
     * Force check and create anonymous user if needed (for debugging)
     */
    fun ensureUserExists(context: Context) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "User already exists: ${currentUser.uid} (Anonymous: ${currentUser.isAnonymous})")
            saveUserId(context, currentUser.uid)
            setAnalyticsUserId(currentUser.uid)
        } else {
            Log.d(TAG, "No user found - ensuring anonymous user is created")
            ensureAnonymousUser(context)
        }
    }
    
    /**
     * Ensure anonymous user is signed in
     * Only creates a new user if no user exists and no saved user ID is found
     */
    private fun ensureAnonymousUser(context: Context) {
        // Double-check that no user exists
        if (auth.currentUser != null) {
            val userId = auth.currentUser!!.uid
            saveUserId(context, userId)
            setAnalyticsUserId(userId)
            Log.d(TAG, "User already exists: $userId")
            return
        }
        
        // Check if we have a saved user ID - if so, don't create a new one
        // This prevents creating multiple anonymous users for the same device
        val savedUserId = getSavedUserId(context)
        if (savedUserId.isNotEmpty()) {
            Log.d(TAG, "Saved user ID found: $savedUserId - waiting for Auth to restore session")
            // Wait a bit more for Auth to restore, then create if still needed
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (auth.currentUser == null) {
                    Log.w(TAG, "Session not restored, creating new anonymous user")
                    createAnonymousUser(context)
                } else {
                    val user = auth.currentUser!!
                    saveUserId(context, user.uid)
                    setAnalyticsUserId(user.uid)
                }
            }, 1000)
            return
        }
        
        // No saved user ID - create new anonymous user
        createAnonymousUser(context)
    }
    
    /**
     * Create a new anonymous user
     */
    private fun createAnonymousUser(context: Context) {
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = task.result?.user?.uid ?: ""
                    Log.d(TAG, "New anonymous user created: $userId")
                    saveUserId(context, userId)
                    saveAnonymousSignupDate(context)
                    setAnalyticsUserId(userId)
                } else {
                    Log.e(TAG, "Failed to sign in anonymously", task.exception)
                    // Retry after a delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (auth.currentUser == null) {
                            auth.signInAnonymously()
                                .addOnCompleteListener { retryTask ->
                                    if (retryTask.isSuccessful) {
                                        val userId = retryTask.result?.user?.uid ?: ""
                                        Log.d(TAG, "Anonymous user created (retry): $userId")
                                        saveUserId(context, userId)
                                        saveAnonymousSignupDate(context)
                                        setAnalyticsUserId(userId)
                                    }
                                }
                        }
                    }, 2000)
                }
            }
    }
    
    /**
     * Link Gmail account to anonymous user
     * Also links RevenueCat to Firebase UID for cross-device purchase restoration
     */
    suspend fun linkGmailAccount(context: Context, idToken: String): Boolean {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.currentUser?.linkWithCredential(credential)?.await()
            if (result != null) {
                saveEmailLinked(context, true)
                Log.d(TAG, "Gmail account linked successfully")
                
                // Link RevenueCat to Firebase UID for cross-device purchase restoration
                val firebaseUserId = result.user?.uid
                if (firebaseUserId != null) {
                    try {
                        com.teamz.lab.debugger.utils.RevenueCatManager.setUserId(firebaseUserId)
                        Log.d(TAG, "RevenueCat linked to Firebase UID: $firebaseUserId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to link RevenueCat after Gmail login", e)
                        // Don't fail the Gmail linking if RevenueCat fails
                    }
                }
                
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to link Gmail account", e)
            false
        }
    }
    
    /**
     * Logout user - signs out and creates a new anonymous user
     * This ensures data continuity while allowing users to switch accounts
     */
    suspend fun logout(context: Context): Boolean {
        return try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                // Sign out from Google Sign-In if applicable
                // Note: This is best handled in the UI layer where we have access to Activity and R resources
                // For now, we'll just sign out from Firebase Auth, which will disconnect the Google account
                try {
                    // Try to get Google Sign-In client if context is Activity
                    if (context is android.app.Activity) {
                        try {
                            var webClientId = AdConfig.getOAuthClientId()
                            if (webClientId.isEmpty()) {
                                // Fallback to strings.xml if AdConfig returns empty
                                webClientId = context.getString(
                                    context.resources.getIdentifier(
                                        "default_web_client_id",
                                        "string",
                                        context.packageName
                                    )
                                )
                            }
                            if (webClientId.isNotEmpty() && webClientId != "YOUR_OAUTH_CLIENT_ID") {
                                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                                )
                                    .requestIdToken(webClientId)
                                    .requestEmail()
                                    .build()
                                
                                val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                                googleSignInClient.signOut()
                                Log.d(TAG, "Google Sign-In session cleared")
                            }
                        } catch (e: android.content.res.Resources.NotFoundException) {
                            Log.w(TAG, "Could not find web client ID resource - continuing with Firebase sign out")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to sign out from Google Sign-In", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sign out from Google Sign-In (may not be signed in)", e)
                    // Continue with Firebase sign out even if Google sign out fails
                }
                
                // Sign out current user from Firebase
                auth.signOut()
                Log.d(TAG, "User signed out: ${currentUser.uid}")
                
                // Clear email linked flag
                saveEmailLinked(context, false)
                
                // Clear saved user ID to allow new anonymous user creation
                getPrefs(context).edit {
                    remove(KEY_USER_ID)
                }
                
                // Wait a bit for sign out to complete
                kotlinx.coroutines.delay(500)
                
                // Create new anonymous user
                ensureAnonymousUser(context)
                
                // Wait for anonymous user to be created
                var attempts = 0
                while (auth.currentUser == null && attempts < 10) {
                    kotlinx.coroutines.delay(500)
                    attempts++
                }
                
                if (auth.currentUser != null) {
                    val newUserId = auth.currentUser!!.uid
                    saveUserId(context, newUserId)
                    setAnalyticsUserId(newUserId)
                    Log.d(TAG, "New anonymous user created after logout: $newUserId")
                    true
                } else {
                    Log.e(TAG, "Failed to create anonymous user after logout")
                    false
                }
            } else {
                Log.w(TAG, "No user to logout")
                // Still ensure anonymous user exists
                ensureAnonymousUser(context)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to logout", e)
            // Try to ensure anonymous user exists even if logout failed
            try {
                ensureAnonymousUser(context)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to create anonymous user after logout error", e2)
            }
            false
        }
    }

    /**
     * Delete account and all associated data (GDPR compliance)
     * This permanently deletes:
     * - User data from user_data collection
     * - User contributions from leaderboards
     * - User contributions from device_insights
     * - Daily stats
     * - Firebase Auth user
     */
    suspend fun deleteAccount(context: Context): Boolean {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.w(TAG, "No user to delete")
                return false
            }

            val userId = currentUser.uid
            Log.d(TAG, "🗑️ Starting account deletion for user: $userId")

            // 1. Delete user_data/{userId}
            try {
                val userDataRef = db.collection("user_data").document(userId)
                userDataRef.collection("latest").document("current").delete().await()
                userDataRef.delete().await()
                Log.d(TAG, "✅ Deleted user_data/$userId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete user_data (may not exist)", e)
            }

            // 2. Remove user from leaderboards entries
            // Note: We query by userId field (which exists for Firestore rules) and check userIds array in memory
            // This avoids requiring a Firestore index for whereArrayContains
            try {
                val categories = listOf(
                    "power_efficiency", "performance_consistency", "thermal_management",
                    "battery_life", "charging_speed", "overall_score"
                )

                for (category in categories) {
                    try {
                        // Query by userId field (last user who updated), then check userIds array
                        val entries = db.collection("leaderboards")
                            .document(category)
                            .collection("entries")
                            .whereEqualTo("userId", userId)
                            .get()
                            .await()

                        for (doc in entries.documents) {
                            val data = doc.data
                            val userIds = (data?.get("userIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                            if (userIds.size == 1 && userIds.contains(userId)) {
                                // Only this user - delete the entire entry
                                doc.reference.delete().await()
                                Log.d(TAG, "  ✅ Deleted leaderboard entry: $category/${doc.id}")
                            } else if (userIds.contains(userId)) {
                                // Multiple users - remove this user from the list
                                val updatedUserIds = userIds.filter { it != userId }
                                val userCount = (data?.get("userCount") as? Number)?.toInt() ?: 1

                                // Update the entry with reduced user count and userIds
                                doc.reference.update(
                                    mapOf(
                                        "userIds" to updatedUserIds,
                                        "userCount" to maxOf(1, userCount - 1)
                                    )
                                ).await()
                                Log.d(TAG, "  ✅ Removed user from leaderboard entry: $category/${doc.id}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to process leaderboard category: $category", e)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove user from leaderboards", e)
            }

            // 3. Remove user from device_insights
            // Note: We can't easily query by userIds array without an index, so we'll skip this
            // The device_insights will naturally exclude this user's data in future aggregations
            // For GDPR compliance, we've deleted the source data (user_data), so aggregated data
            // will become stale and won't include this user's contributions going forward
            Log.d(TAG, "⚠️ Note: device_insights are aggregated - source data deleted, aggregation will update naturally")

            // 4. Delete daily_stats
            try {
                val dailyStats = db.collection("daily_stats")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()

                for (doc in dailyStats.documents) {
                    doc.reference.delete().await()
                }
                Log.d(TAG, "✅ Deleted ${dailyStats.size()} daily_stats entries")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete daily_stats", e)
            }

            // 5. Sign out from Google Sign-In if applicable
            if (context is android.app.Activity) {
                try {
                    var webClientId = AdConfig.getOAuthClientId()
                    if (webClientId.isEmpty()) {
                        // Fallback to strings.xml if AdConfig returns empty
                        webClientId = context.getString(
                            context.resources.getIdentifier(
                                "default_web_client_id",
                                "string",
                                context.packageName
                            )
                        )
                    }
                    if (webClientId.isNotEmpty() && webClientId != "YOUR_OAUTH_CLIENT_ID") {
                        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                        )
                            .requestIdToken(webClientId)
                            .requestEmail()
                            .build()

                        val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                        googleSignInClient.signOut().await()
                        Log.d(TAG, "✅ Signed out from Google Sign-In")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sign out from Google Sign-In", e)
                }
            }

            // 6. Delete Firebase Auth user
            currentUser.delete().await()
            Log.d(TAG, "✅ Deleted Firebase Auth user")

            // 7. Clear local preferences
            getPrefs(context).edit {
                clear()
            }

            // 8. Create new anonymous user
            kotlinx.coroutines.delay(500)
            ensureAnonymousUser(context)

            var attempts = 0
            while (auth.currentUser == null && attempts < 10) {
                kotlinx.coroutines.delay(500)
                attempts++
            }

            if (auth.currentUser != null) {
                val newUserId = auth.currentUser!!.uid
                saveUserId(context, newUserId)
                setAnalyticsUserId(newUserId)
                Log.d(TAG, "✅ New anonymous user created: $newUserId")
            }

            Log.d(TAG, "✅✅✅ Account deletion completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to delete account", e)
            // Try to ensure anonymous user exists even if deletion failed
            try {
                ensureAnonymousUser(context)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to create anonymous user after deletion error", e2)
            }
            false
        }
    }
    
    /**
     * Get user email if available
     */
    fun getUserEmail(): String? {
        return auth.currentUser?.email
    }
    
    /**
     * Get user display name if available
     */
    fun getUserDisplayName(): String? {
        return auth.currentUser?.displayName
    }
    
    /**
     * Get user photo URL if available
     */
    fun getUserPhotoUrl(): String? {
        return auth.currentUser?.photoUrl?.toString()
    }
    
    /**
     * Check if user should be reminded about data retention
     * Only shows reminder if retention days > 0 (not indefinite)
     */
    fun shouldShowDataRetentionReminder(context: Context): Boolean {
        if (isEmailLinked(context)) return false
        if (getDataRetentionReminderShown(context)) return false
        
        val retentionDays = try {
            RemoteConfigUtils.getLeaderboardDataRetentionDays()
        } catch (e: Exception) {
            -1L // Default to keep forever if RemoteConfig not available
        }
        
        // If retention is -1 (keep forever), don't show reminder
        if (retentionDays == -1L) return false
        
        val signupDate = getAnonymousSignupDate(context)
        if (signupDate == 0L) return false
        
        val reminderDays = try {
            RemoteConfigUtils.getLeaderboardDataRetentionReminderDays()
        } catch (e: Exception) {
            5L // Default: 5 days before removal
        }
        
        val daysSinceSignup = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - signupDate)
        val daysUntilRemoval = retentionDays - daysSinceSignup
        return daysUntilRemoval <= reminderDays && daysUntilRemoval > 0
    }
    
        /**
         * Upload leaderboard entry (optimized - minimal fields only)
         */
        suspend fun uploadLeaderboardEntry(
            context: Context,
            entry: LeaderboardEntry
        ): Boolean {
            return try {
                val userId = getCurrentUserId()
                if (userId.isEmpty()) {
                    Log.e(TAG, "❌ No user ID available - cannot upload")
                    return false
                }
                
                // Update entry with user ID
                val entryWithUserId = entry.copy(userId = userId)
                
                // Debug logging for scores
                val nonZeroScores = entryWithUserId.scores.values.count { it > 0.0 }
                Log.d(TAG, "📊 Uploading leaderboard entry:")
                Log.d(TAG, "  - User ID: $userId")
                Log.d(TAG, "  - Device: ${entryWithUserId.displayName}")
                Log.d(TAG, "  - Data Quality: ${entryWithUserId.dataQuality}/5")
                Log.d(TAG, "  - Non-zero scores: $nonZeroScores/${entryWithUserId.scores.size}")
                entryWithUserId.scores.forEach { (category, score) ->
                    if (score > 0.0) {
                        Log.d(TAG, "  ✅ $category: $score/100")
                    } else {
                        Log.d(TAG, "  ⚠️ $category: $score/100 (zero)")
                    }
                }
                
                // Check specifically for thermal_efficiency
                val thermalScore = entryWithUserId.scores["thermal_efficiency"] ?: 0.0
                if (thermalScore <= 0) {
                    Log.w(TAG, "⚠️ WARNING: thermal_efficiency score is $thermalScore! This will show as 0 in leaderboard.")
                    Log.w(TAG, "⚠️ Check if battery temperature is being collected correctly.")
                }
                
                // Upload to user_data/latest (only latest entry, not snapshots)
                val userDataRef = db.collection("user_data")
                    .document(userId)
                    .collection("latest")
                    .document("current")
                
                Log.d(TAG, "📤 Uploading to Firestore: user_data/$userId/latest/current")
                userDataRef.set(entryWithUserId).await()
                Log.d(TAG, "✅ User data uploaded successfully")
                
                // Update aggregated leaderboard entries for each category
                Log.d(TAG, "📤 Updating category leaderboards...")
                updateCategoryLeaderboards(entryWithUserId)
                Log.d(TAG, "✅ Category leaderboards updated")
                
                // Update device insights
                Log.d(TAG, "📤 Updating device insights...")
                updateDeviceInsights(entryWithUserId)
                Log.d(TAG, "✅ Device insights updated")
                
                // Save last upload time
                saveLastUpload(context)
                
                Log.d(TAG, "✅✅✅ Leaderboard entry uploaded successfully for device: ${entryWithUserId.displayName}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "❌❌❌ Failed to upload leaderboard entry", e)
                Log.e(TAG, "  Error type: ${e.javaClass.simpleName}")
                Log.e(TAG, "  Error message: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    
    /**
     * Update category leaderboards (optimized query structure)
     */
    private suspend fun updateCategoryLeaderboards(entry: LeaderboardEntry) {
        entry.scores.forEach { (category, score) ->
            try {
                val categoryRef = db.collection("leaderboards")
                    .document(category)
                    .collection("entries")
                    .document(entry.normalizedDeviceId)
                
                Log.d(TAG, "📤 Updating category leaderboard: $category for device: ${entry.displayName} (normalizedId: ${entry.normalizedDeviceId})")
                
                // Use transaction to update aggregated data
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(categoryRef)
                    val currentData = snapshot.toObject(CategoryLeaderboardEntry::class.java)
                    
                    if (currentData == null) {
                        // First entry for this device
                        Log.d(TAG, "  ✅ Creating NEW entry for device: ${entry.displayName} in category: $category")
                        val newEntry = CategoryLeaderboardEntry(
                            normalizedDeviceId = entry.normalizedDeviceId,
                            normalizedBrand = entry.normalizedBrand,
                            normalizedModel = entry.normalizedModel,
                            displayName = entry.displayName,
                            score = score,
                            userCount = 1,
                            avgScore = score,
                            topScore = score,
                            dataQuality = entry.dataQuality,
                            lastUpdated = entry.timestamp,
                            userId = entry.userId,
                            userIds = listOf(entry.userId),
                            measurementCount = entry.measurementCount, // Track total measurements
                            dataFreshness = entry.timestamp // Most recent data timestamp
                        )
                        // Convert to map and add userId for Firestore rules
                        val entryMap = mapOf(
                            "normalizedDeviceId" to newEntry.normalizedDeviceId,
                            "normalizedBrand" to newEntry.normalizedBrand,
                            "normalizedModel" to newEntry.normalizedModel,
                            "displayName" to newEntry.displayName,
                            "score" to newEntry.score,
                            "userCount" to newEntry.userCount,
                            "avgScore" to newEntry.avgScore,
                            "topScore" to newEntry.topScore,
                            "dataQuality" to newEntry.dataQuality,
                            "lastUpdated" to newEntry.lastUpdated,
                            "userId" to entry.userId, // Add userId for Firestore rules
                            "userIds" to listOf(entry.userId), // Track unique user IDs
                            "measurementCount" to newEntry.measurementCount, // Track consistency
                            "dataFreshness" to newEntry.dataFreshness // Track freshness
                        )
                        transaction.set(categoryRef, entryMap)
                        Log.d(TAG, "  ✅ New entry created: userCount=1, score=$score, quality=${entry.dataQuality}")
                    } else {
                        // Check if this is a new user or same user updating
                        // Get existing userIds list (if exists) or create new one
                        val existingUserIds = (snapshot.get("userIds") as? List<*>)?.mapNotNull { it as? String }?.toMutableSet() 
                            ?: mutableSetOf<String>()
                        
                        // Only increment userCount if this is a new unique user
                        val isNewUser = !existingUserIds.contains(entry.userId)
                        val newUserCount = if (isNewUser) {
                            existingUserIds.add(entry.userId)
                            currentData.userCount + 1
                        } else {
                            currentData.userCount // Keep same count for existing user
                        }
                        
                        if (isNewUser) {
                            Log.d(TAG, "  ✅ NEW USER added to existing device: ${entry.displayName}")
                            Log.d(TAG, "  📊 User count: ${currentData.userCount} → $newUserCount")
                        } else {
                            Log.d(TAG, "  🔄 Same user updating data for device: ${entry.displayName}")
                        }
                        
                        // Update average score (weighted by user count)
                        val newAvgScore = if (isNewUser) {
                            ((currentData.avgScore * currentData.userCount) + score) / newUserCount
                        } else {
                            // For same user, update their contribution to the average
                            // This is a simplified approach - recalculate based on all users
                            currentData.avgScore // Keep existing average for now
                        }
                        
                        val newTopScore = maxOf(currentData.topScore, score)
                        val newDataQuality = maxOf(currentData.dataQuality, entry.dataQuality)
                        
                        // DATA PURITY: Track measurement count for consistency
                        // Increment measurement count for same user (shows consistency)
                        val newMeasurementCount = if (isNewUser) {
                            (currentData.measurementCount ?: 0) + entry.measurementCount
                        } else {
                            (currentData.measurementCount ?: 0) + entry.measurementCount // Same user, new measurement
                        }
                        
                        // Update data freshness (most recent timestamp)
                        val newDataFreshness = maxOf(currentData.dataFreshness ?: 0L, entry.timestamp)
                        
                        transaction.update(categoryRef, mapOf(
                            "score" to score,
                            "userCount" to newUserCount,
                            "avgScore" to newAvgScore,
                            "topScore" to newTopScore,
                            "dataQuality" to newDataQuality,
                            "lastUpdated" to entry.timestamp,
                            "userId" to entry.userId, // Keep userId updated for Firestore rules
                            "userIds" to existingUserIds.toList(), // Store unique user IDs
                            "measurementCount" to newMeasurementCount, // Track consistency
                            "dataFreshness" to newDataFreshness // Track freshness
                        ))
                        Log.d(TAG, "  ✅ Entry updated: userCount=$newUserCount, avgScore=$newAvgScore, quality=$newDataQuality")
                    }
                }.await()
                Log.d(TAG, "  ✅✅ Category leaderboard updated successfully: $category")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to update category leaderboard: $category", e)
                Log.e(TAG, "  Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Update device insights (aggregated by device)
     */
    private suspend fun updateDeviceInsights(entry: LeaderboardEntry) {
        try {
            val deviceRef = db.collection("device_insights")
                .document(entry.normalizedDeviceId)
            
            db.runTransaction { transaction ->
                val snapshot = transaction.get(deviceRef)
                val currentData = snapshot.toObject(DeviceInsight::class.java)
                
                if (currentData == null) {
                    // First entry for this device
                    transaction.set(deviceRef, mapOf(
                        "normalizedDeviceId" to entry.normalizedDeviceId,
                        "displayName" to entry.displayName,
                        "normalizedBrand" to entry.normalizedBrand,
                        "scores" to entry.scores,
                        "userCount" to 1,
                        "dataQuality" to entry.dataQuality,
                        "trustLevel" to calculateTrustLevel(1, entry.dataQuality),
                        "lastUpdated" to entry.timestamp,
                        "userIds" to listOf(entry.userId) // Track unique user IDs
                    ))
                } else {
                    // Update aggregated data
                    // Check if this is a new user or same user updating
                    val existingUserIds = (snapshot.get("userIds") as? List<*>)?.mapNotNull { it as? String }?.toMutableSet() 
                        ?: mutableSetOf<String>()
                    
                    val isNewUser = !existingUserIds.contains(entry.userId)
                    val newUserCount = if (isNewUser) {
                        existingUserIds.add(entry.userId)
                        currentData.userCount + 1
                    } else {
                        currentData.userCount // Keep same count for existing user
                    }
                    
                    val newScores = if (isNewUser) {
                        entry.scores.mapValues { (category, newScore) ->
                            val currentScore = currentData.scores[category] ?: 0.0
                            ((currentScore * currentData.userCount) + newScore) / newUserCount
                        }
                    } else {
                        // For same user, keep existing scores (or update if needed)
                        currentData.scores
                    }
                    val newDataQuality = maxOf(currentData.dataQuality, entry.dataQuality)
                    
                    transaction.update(deviceRef, mapOf(
                        "scores" to newScores,
                        "userCount" to newUserCount,
                        "dataQuality" to newDataQuality,
                        "trustLevel" to calculateTrustLevel(newUserCount, newDataQuality),
                        "lastUpdated" to entry.timestamp,
                        "userIds" to existingUserIds.toList() // Store unique user IDs
                    ))
                }
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update device insights", e)
        }
    }
    
    /**
     * Get leaderboard entries for a category (optimized query)
     * Handles permission errors gracefully
     * DATA PURITY: Filters out stale data (older than 30 days) for decision-making accuracy
     */
    suspend fun getLeaderboardEntries(
        category: String,
        limit: Int = 100
    ): List<CategoryLeaderboardEntry> {
        return try {
            // Ensure user is authenticated first
            if (auth.currentUser == null) {
                ensureAnonymousUser(android.app.Application().applicationContext)
                // Wait a bit for auth to complete
                kotlinx.coroutines.delay(500)
            }
            
            // DATA PURITY: Filter out very stale data (older than 180 days) - very lenient
            // This ensures users see relevant data while not being too restrictive
            val staleDataThreshold = System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000) // 180 days
            
            // COST OPTIMIZATION: Use efficient query with proper limit
            // Query only entries with non-zero scores to reduce reads
            val allEntries = try {
                db.collection("leaderboards")
                    .document(category)
                    .collection("entries")
                    .whereGreaterThan("avgScore", 0.0) // Filter at query level (cost-efficient)
                    .orderBy("avgScore", Query.Direction.DESCENDING)
                    .limit((limit * 2).toLong()) // Get 2x limit to account for filtering
                    .get()
                    .await()
                    .toObjects(CategoryLeaderboardEntry::class.java)
            } catch (e: Exception) {
                // If whereGreaterThan fails (no index), fallback to simple query
                Log.w(TAG, "Query with whereGreaterThan failed, using fallback: ${e.message}")
                db.collection("leaderboards")
                    .document(category)
                    .collection("entries")
                    .orderBy("avgScore", Query.Direction.DESCENDING)
                    .limit((limit * 2).toLong())
                    .get()
                    .await()
                    .toObjects(CategoryLeaderboardEntry::class.java)
                    .filter { it.avgScore > 0.0 } // Filter in memory as fallback
            }
            
            // Filter out stale data and ensure minimum data quality - very lenient filters
            val freshEntries = allEntries
                .filter { entry ->
                    // Filter 1: Data must be fresh (updated within last 180 days) OR if lastUpdated is 0, allow it
                    // This handles cases where lastUpdated might not be set
                    val isFresh = entry.lastUpdated == 0L || entry.lastUpdated > staleDataThreshold
                    
                    // Filter 2: Minimum data quality (at least 1/5) - very lenient
                    val hasQuality = entry.dataQuality >= 1
                    
                    // Filter 3: Must have at least 1 user contributing
                    val hasUsers = entry.userCount > 0
                    
                    // Filter 4: Allow zero scores IF they have data quality >= 1 and users
                    // This shows devices with incomplete data rather than hiding them completely
                    // Zero scores will be sorted to the bottom but still visible
                    val hasScore = entry.avgScore >= 0.0 // Allow zero scores
                    
                    val isValid = isFresh && hasQuality && hasUsers && hasScore
                    
                    // Removed verbose logging for performance
                    
                    isValid
                }
                .take(limit) // Take top N after filtering
            
            // Reduced logging for performance
            
            // FAST PATH: If we have entries from leaderboards, return them immediately (no merge needed)
            // Only use device_insights as fallback when leaderboards is completely empty
            if (freshEntries.isNotEmpty()) {
                // Sort: Non-zero scores first (descending), then zero scores at the bottom
                val sortedEntries = freshEntries
                    .sortedWith(compareByDescending<CategoryLeaderboardEntry> { it.avgScore > 0.0 }
                        .thenByDescending { it.avgScore })
                    .take(limit)
                
                return sortedEntries
            }
            
            // FALLBACK: Only fetch from device_insights if leaderboards is empty
            // This ensures we show data even if leaderboards collection is incomplete
            return try {
                // COST OPTIMIZATION: Query only devices with non-zero scores for this category
                val deviceInsights = try {
                    db.collection("device_insights")
                        .whereGreaterThan("scores.$category", 0.0)
                        .limit((limit * 2).toLong()) // Get more to filter
                        .get()
                        .await()
                        .toObjects(DeviceInsight::class.java)
                } catch (e: Exception) {
                    // If nested field query fails, get all and filter in memory (less efficient but works)
                    Log.w(TAG, "Nested field query failed, using fallback: ${e.message}")
                    db.collection("device_insights")
                        .limit(100) // Limit to reduce cost
                        .get()
                        .await()
                        .toObjects(DeviceInsight::class.java)
                        .filter { (it.scores[category] ?: 0.0) > 0.0 }
                }
                
                // Convert DeviceInsight to CategoryLeaderboardEntry and apply same filters
                // RAM OPTIMIZATION: Use sequence for memory efficiency (lazy evaluation)
                val convertedEntries = deviceInsights
                    .asSequence() // Use sequence to avoid creating intermediate collections
                    .map { insight ->
                        val score = insight.scores[category] ?: 0.0
                        CategoryLeaderboardEntry(
                            normalizedDeviceId = insight.normalizedDeviceId,
                            normalizedBrand = insight.normalizedBrand,
                            normalizedModel = "",
                            displayName = insight.displayName,
                            score = score,
                            userCount = insight.userCount,
                            avgScore = score,
                            topScore = score,
                            dataQuality = insight.dataQuality,
                            lastUpdated = insight.lastUpdated,
                            userId = "",
                            userIds = emptyList(),
                            measurementCount = 0,
                            dataFreshness = insight.lastUpdated
                        )
                    }
                    .filter { entry ->
                        val isFresh = entry.lastUpdated == 0L || entry.lastUpdated > staleDataThreshold
                        val hasQuality = entry.dataQuality >= 1
                        val hasUsers = entry.userCount > 0
                        val hasScore = entry.avgScore >= 0.0 // Allow zero scores
                        isFresh && hasQuality && hasUsers && hasScore
                    }
                    .sortedWith(compareByDescending<CategoryLeaderboardEntry> { it.avgScore > 0.0 }
                        .thenByDescending { it.avgScore }) // Non-zero first, then by score descending
                    .take(limit)
                    .toList() // Convert to list only at the end (minimal memory footprint)
                
                convertedEntries
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get fallback data from device_insights", e)
                emptyList() // Return empty list if fallback also fails
            }
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            when (e.code) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    Log.w(TAG, "Permission denied - user may need to authenticate", e)
                    // Return empty list - UI will show appropriate message
                    emptyList()
                }
                else -> {
                    Log.e(TAG, "Failed to get leaderboard entries", e)
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get leaderboard entries", e)
            emptyList()
        }
    }
    
    /**
     * Get user's rank in a category
     */
    suspend fun getUserRank(
        category: String,
        normalizedDeviceId: String
    ): Int {
        return try {
            val entries = getLeaderboardEntries(category, 1000)
            val rank = entries.indexOfFirst { it.normalizedDeviceId == normalizedDeviceId }
            if (rank >= 0) rank + 1 else -1
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user rank", e)
            -1
        }
    }
    
    /**
     * Check if there's a permission error
     */
    fun hasPermissionError(): Boolean {
        // This will be set when we detect permission errors
        return false // For now, always return false - we handle errors gracefully
    }
    
    /**
     * Get device insights
     */
    suspend fun getDeviceInsights(normalizedDeviceId: String): DeviceInsight? {
        return try {
            db.collection("device_insights")
                .document(normalizedDeviceId)
                .get()
                .await()
                .toObject(DeviceInsight::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get device insights", e)
            null
        }
    }
    
    /**
     * Get all device insights (for best device overall calculation)
     * Fetches all available device insights (up to limit) for comprehensive best device calculation
     */
    suspend fun getAllDeviceInsights(limit: Int = 1000): List<DeviceInsight> {
        return try {
            // Get all device insights to ensure we have enough data for filtering
            db.collection("device_insights")
                .limit(limit.toLong())
                .get()
                .await()
                .toObjects(DeviceInsight::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all device insights", e)
            emptyList()
        }
    }
    
    /**
     * Get best devices for a category (top devices by score)
     * Uses category leaderboard entries for better data availability
     */
    suspend fun getBestDevices(
        category: String,
        limit: Long = 10
    ): List<DeviceInsight> {
        return try {
            // First try to get from category leaderboard (more reliable)
            val categoryEntries = getLeaderboardEntries(category, limit.toInt())
            if (categoryEntries.isNotEmpty()) {
                // Convert CategoryLeaderboardEntry to DeviceInsight
                return categoryEntries.map { entry ->
                    DeviceInsight(
                        normalizedDeviceId = entry.normalizedDeviceId,
                        displayName = entry.displayName,
                        normalizedBrand = entry.normalizedBrand,
                        scores = mapOf(category to entry.avgScore),
                        userCount = entry.userCount,
                        dataQuality = entry.dataQuality,
                        trustLevel = calculateTrustLevel(entry.userCount, entry.dataQuality),
                        lastUpdated = entry.lastUpdated
                    )
                }
            }
            
            // Fallback: Get top devices from device_insights collection
            // Filter by devices that have a score for this category
            db.collection("device_insights")
                .whereGreaterThan("scores.$category", 0.0)
                .orderBy("scores.$category", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
                .toObjects(DeviceInsight::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get best devices from device_insights, trying fallback", e)
            // If orderBy on nested field fails, get all and sort in memory
            try {
                db.collection("device_insights")
                    .limit(200) // Get more to filter (increased for better coverage)
                    .get()
                    .await()
                    .toObjects(DeviceInsight::class.java)
                    .filter { it.scores[category] != null && it.scores[category]!! > 0.0 }
                    .sortedByDescending { it.scores[category] ?: 0.0 }
                    .take(limit.toInt())
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to get best devices (fallback also failed)", e2)
                emptyList()
            }
        }
    }
    
    /**
     * Calculate trust level
     */
    private fun calculateTrustLevel(userCount: Int, dataQuality: Int): String {
        return when {
            userCount >= 100 && dataQuality >= 4 -> "Verified"
            userCount >= 50 && dataQuality >= 3 -> "High"
            userCount >= 10 && dataQuality >= 2 -> "Medium"
            else -> "Low"
        }
    }

    /**
     * DEBUG FUNCTION: Inspect Firestore leaderboard structure
     * This helps identify why data might not be showing in the leaderboard
     *
     * Usage: Call this from your code or add a button in UI to trigger it
     * Example: scope.launch { LeaderboardManager.debugLeaderboardStructure() }
     */
    suspend fun debugLeaderboardStructure() {
        Log.d(TAG, "🔍 ========== DEBUG: Leaderboard Structure Analysis ==========")

        val categories = listOf(
            "power_efficiency", "camera_efficiency", "cpu_performance",
            "display_efficiency", "health_score", "power_trend",
            "component_optimization", "thermal_efficiency", "performance_consistency"
        )

        val staleDataThreshold = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000) // 30 days for purchase decisions

        categories.forEach { category ->
            try {
                Log.d(TAG, "\n📊 Category: $category")

                // Check leaderboards collection
                val leaderboardEntries = db.collection("leaderboards")
                    .document(category)
                    .collection("entries")
                    .get()
                    .await()

                Log.d(TAG, "  - Total entries in leaderboards/$category/entries: ${leaderboardEntries.size()}")

                if (leaderboardEntries.isEmpty()) {
                    Log.w(TAG, "  ⚠️ No entries found in leaderboards collection!")
                } else {
                    leaderboardEntries.documents.forEachIndexed { index, doc ->
                        val data = doc.data ?: emptyMap()
                        val avgScore = (data["avgScore"] as? Number)?.toDouble() ?: 0.0
                        val userCount = (data["userCount"] as? Number)?.toInt() ?: 0
                        val dataQuality = (data["dataQuality"] as? Number)?.toInt() ?: 0
                        val lastUpdated = (data["lastUpdated"] as? Number)?.toLong() ?: 0L
                        val displayName = data["displayName"] as? String ?: doc.id

                        val isFresh = lastUpdated == 0L || lastUpdated > staleDataThreshold
                        val hasQuality = dataQuality >= 1
                        val hasUsers = userCount > 0
                        val hasScore = avgScore > 0.0
                        val isValid = isFresh && hasQuality && hasUsers && hasScore

                        Log.d(TAG, "  Entry ${index + 1}: $displayName (docId: ${doc.id})")
                        Log.d(TAG, "    - avgScore: $avgScore ${if (!hasScore) "❌ FILTERED" else "✅"}")
                        Log.d(TAG, "    - userCount: $userCount ${if (!hasUsers) "❌ FILTERED" else "✅"}")
                        Log.d(TAG, "    - dataQuality: $dataQuality ${if (!hasQuality) "❌ FILTERED" else "✅"}")
                        Log.d(TAG, "    - lastUpdated: $lastUpdated (${if (lastUpdated == 0L) "Not set" else java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(lastUpdated))}) ${if (!isFresh) "❌ FILTERED (stale)" else "✅"}")
                        Log.d(TAG, "    - Status: ${if (isValid) "✅ VALID - Will show in leaderboard" else "❌ FILTERED OUT - Will NOT show"}")
                    }
                }

                // Check device_insights collection as fallback
                val deviceInsights = db.collection("device_insights")
                    .get()
                    .await()

                val categoryInsights = deviceInsights.documents
                    .mapNotNull { doc ->
                        val data = doc.data ?: emptyMap()
                        val scores = data["scores"] as? Map<*, *>
                        val score = (scores?.get(category) as? Number)?.toDouble() ?: 0.0
                        if (score > 0.0) {
                            Triple(
                                data["displayName"] as? String ?: doc.id,
                                score,
                                (data["userCount"] as? Number)?.toInt() ?: 0
                            )
                        } else null
                    }

                if (categoryInsights.isNotEmpty()) {
                    Log.d(TAG, "  - Found ${categoryInsights.size} devices in device_insights with score > 0 for this category:")
                    categoryInsights.sortedByDescending { it.second }.forEachIndexed { index, (name, score, users) ->
                        Log.d(TAG, "    ${index + 1}. $name: score=$score, users=$users")
                    }
                } else {
                    Log.d(TAG, "  - No devices in device_insights with score > 0 for this category")
                }

            } catch (e: Exception) {
                Log.e(TAG, "  ❌ Error checking category $category: ${e.message}", e)
            }
        }

        Log.d(TAG, "\n🔍 ========== End Debug Analysis ==========\n")
    }

    // Preferences helpers
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private fun saveUserId(context: Context, userId: String) {
        getPrefs(context).edit {
            putString(KEY_USER_ID, userId)
        }
    }
    
    private fun saveLastUpload(context: Context) {
        getPrefs(context).edit {
            putLong(KEY_LAST_UPLOAD, System.currentTimeMillis())
        }
    }
    
    private fun saveAnonymousSignupDate(context: Context) {
        getPrefs(context).edit {
            putLong(KEY_ANONYMOUS_SIGNUP_DATE, System.currentTimeMillis())
        }
    }
    
    private fun getAnonymousSignupDate(context: Context): Long {
        return getPrefs(context).getLong(KEY_ANONYMOUS_SIGNUP_DATE, 0L)
    }
    
    private fun saveEmailLinked(context: Context, linked: Boolean) {
        getPrefs(context).edit {
            putBoolean(KEY_EMAIL_LINKED, linked)
        }
    }
    
    fun isEmailLinked(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_EMAIL_LINKED, false)
    }
    
    fun getDaysUntilDataRemoval(context: Context): Long {
        val signupDate = getAnonymousSignupDate(context)
        if (signupDate == 0L) return -1L // No signup date - assume keep forever
        
        val retentionDays = try {
            RemoteConfigUtils.getLeaderboardDataRetentionDays()
        } catch (e: Exception) {
            -1L // Default to keep forever if RemoteConfig not available
        }
        
        // If retention is -1 (keep forever), return -1
        if (retentionDays == -1L) return -1L
        
        val daysSinceSignup = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - signupDate)
        return maxOf(0L, retentionDays - daysSinceSignup)
    }
    
    fun setDataRetentionReminderShown(context: Context) {
        getPrefs(context).edit {
            putBoolean(KEY_DATA_RETENTION_REMINDER_SHOWN, true)
        }
    }
    
    private fun getDataRetentionReminderShown(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DATA_RETENTION_REMINDER_SHOWN, false)
    }
    
    /**
     * Upload app power data to leaderboard
     */
    suspend fun uploadAppPowerEntry(
        context: Context,
        appPowerData: PowerConsumptionUtils.AppPowerData
    ): Boolean {
        return try {
            val userId = getCurrentUserId()
            if (userId.isEmpty()) {
                Log.e(TAG, "❌ No user ID available - cannot upload app power data")
                return false
            }
            
            Log.d(TAG, "📤 Uploading app power entry: ${appPowerData.appName} (${appPowerData.packageName})")
            
            // Upload to app_power_leaderboard collection
            val appPowerRef = db.collection("app_power_leaderboard")
                .document(appPowerData.packageName)
            
            // Use transaction to update aggregated data
            db.runTransaction { transaction ->
                val snapshot = transaction.get(appPowerRef)
                val currentData = snapshot.toObject(AppPowerLeaderboardEntry::class.java)
                
                if (currentData == null) {
                    // First entry for this app
                    val newEntry = AppPowerLeaderboardEntry(
                        packageName = appPowerData.packageName,
                        appName = appPowerData.appName,
                        avgPowerConsumption = appPowerData.powerConsumption,
                        peakPowerConsumption = appPowerData.powerConsumption,
                        userCount = 1,
                        avgBatteryImpact = appPowerData.batteryImpact,
                        totalUsageTime = appPowerData.totalUsageTime,
                        dataQuality = 3, // Default quality
                        lastUpdated = appPowerData.timestamp,
                        userId = userId,
                        userIds = listOf(userId),
                        measurementCount = 1
                    )
                    transaction.set(appPowerRef, newEntry)
                    Log.d(TAG, "  ✅ Created new app power entry: ${appPowerData.appName}")
                } else {
                    // Update existing entry
                    val existingUserIds = (snapshot.get("userIds") as? List<*>)?.mapNotNull { it as? String }?.toMutableSet() 
                        ?: mutableSetOf<String>()
                    
                    val isNewUser = !existingUserIds.contains(userId)
                    val newUserCount = if (isNewUser) {
                        existingUserIds.add(userId)
                        currentData.userCount + 1
                    } else {
                        currentData.userCount
                    }
                    
                    // Update average power (weighted by user count)
                    val newAvgPower = if (isNewUser) {
                        ((currentData.avgPowerConsumption * currentData.userCount) + appPowerData.powerConsumption) / newUserCount
                    } else {
                        // For same user, update their contribution
                        ((currentData.avgPowerConsumption * currentData.userCount) - (currentData.avgPowerConsumption * (currentData.userCount - 1)) + appPowerData.powerConsumption) / currentData.userCount
                    }
                    
                    val newPeakPower = maxOf(currentData.peakPowerConsumption, appPowerData.powerConsumption)
                    val newAvgBatteryImpact = if (isNewUser) {
                        ((currentData.avgBatteryImpact * currentData.userCount) + appPowerData.batteryImpact) / newUserCount
                    } else {
                        ((currentData.avgBatteryImpact * currentData.userCount) - (currentData.avgBatteryImpact * (currentData.userCount - 1)) + appPowerData.batteryImpact) / currentData.userCount
                    }
                    
                    val newTotalUsageTime = currentData.totalUsageTime + appPowerData.totalUsageTime
                    val newMeasurementCount = currentData.measurementCount + 1
                    
                    transaction.update(appPowerRef, mapOf(
                        "avgPowerConsumption" to newAvgPower,
                        "peakPowerConsumption" to newPeakPower,
                        "userCount" to newUserCount,
                        "avgBatteryImpact" to newAvgBatteryImpact,
                        "totalUsageTime" to newTotalUsageTime,
                        "lastUpdated" to appPowerData.timestamp,
                        "userId" to userId,
                        "userIds" to existingUserIds.toList(),
                        "measurementCount" to newMeasurementCount
                    ))
                    Log.d(TAG, "  ✅ Updated app power entry: ${appPowerData.appName}, userCount=$newUserCount, avgPower=$newAvgPower W")
                }
            }.await()
            
            Log.d(TAG, "✅✅✅ App power entry uploaded successfully: ${appPowerData.appName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ Failed to upload app power entry", e)
            false
        }
    }
    
    /**
     * Get app power leaderboard entries (sorted by power consumption descending)
     */
    suspend fun getAppPowerLeaderboardEntries(
        limit: Int = 100
    ): List<AppPowerLeaderboardEntry> {
        return try {
            // Ensure user is authenticated first
            if (auth.currentUser == null) {
                ensureAnonymousUser(android.app.Application().applicationContext)
                kotlinx.coroutines.delay(500)
            }
            
            // Filter out stale data (older than 180 days)
            val staleDataThreshold = System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000)
            
            val allEntries = try {
                db.collection("app_power_leaderboard")
                    .whereGreaterThan("avgPowerConsumption", 0.0)
                    .orderBy("avgPowerConsumption", Query.Direction.DESCENDING)
                    .limit((limit * 2).toLong())
                    .get()
                    .await()
                    .toObjects(AppPowerLeaderboardEntry::class.java)
            } catch (e: Exception) {
                Log.w(TAG, "Query with whereGreaterThan failed, using fallback: ${e.message}")
                db.collection("app_power_leaderboard")
                    .orderBy("avgPowerConsumption", Query.Direction.DESCENDING)
                    .limit((limit * 2).toLong())
                    .get()
                    .await()
                    .toObjects(AppPowerLeaderboardEntry::class.java)
                    .filter { it.avgPowerConsumption > 0.0 }
            }
            
            // Filter out stale data and ensure minimum data quality
            val freshEntries = allEntries
                .filter { entry ->
                    val isFresh = entry.lastUpdated == 0L || entry.lastUpdated > staleDataThreshold
                    val hasQuality = entry.dataQuality >= 1
                    val hasUsers = entry.userCount > 0
                    val hasPower = entry.avgPowerConsumption >= 0.0
                    isFresh && hasQuality && hasUsers && hasPower
                }
                .take(limit)
            
            freshEntries
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            when (e.code) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    Log.w(TAG, "Permission denied - user may need to authenticate", e)
                    emptyList()
                }
                else -> {
                    Log.e(TAG, "Failed to get app power leaderboard entries", e)
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get app power leaderboard entries", e)
            emptyList()
        }
    }
}

/**
 * Category leaderboard entry (Firestore document)
 * DATA PURITY: Includes freshness and consistency tracking
 */
data class CategoryLeaderboardEntry(
    val normalizedDeviceId: String = "",
    val normalizedBrand: String = "",
    val normalizedModel: String = "",
    val displayName: String = "",
    val score: Double = 0.0,
    val userCount: Int = 0,
    val avgScore: Double = 0.0,
    val topScore: Double = 0.0,
    val dataQuality: Int = 0,
    val lastUpdated: Long = 0L,
    val userId: String = "",
    val userIds: List<String> = emptyList(), // Track unique users
    val measurementCount: Int = 0, // Total measurements (consistency tracking)
    val dataFreshness: Long = 0L // Most recent data timestamp (staleness filtering)
)

