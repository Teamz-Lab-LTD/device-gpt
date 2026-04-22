package com.teamz.lab.debugger

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.MobileAds
import com.teamz.lab.debugger.utils.AppOpenAdManager
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.teamz.lab.debugger.utils.InterstitialAdManager
import com.teamz.lab.debugger.utils.RemoteConfigUtils
import com.teamz.lab.debugger.utils.ReferralManager
import com.teamz.lab.debugger.utils.RetentionNotificationManager
import com.teamz.lab.debugger.utils.ErrorHandler
import com.teamz.lab.debugger.utils.RevenueCatManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.content.edit
import com.teamz.lab.debugger.BuildConfig
import com.teamz.lab.debugger.utils.AppLog

class MyApplication : Application(), Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {
    private var currentActivity: Activity? = null
    private var isAppInBackground = true // Track app state for lifecycle awareness
    
    // Store the default uncaught exception handler
    private val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun onCreate() {
        super<Application>.onCreate()
        AppLog.d("MyApplication", "onCreate() - Initializing app...")
        
        try {
            // Set up global uncaught exception handler - CRITICAL: Must be first
            setupGlobalExceptionHandler()
            
            registerActivityLifecycleCallbacks(this)
            
            // Initialize referral ad-free cache BEFORE ads load
            // This must happen early so RemoteConfigUtils.isUserAdFree() works
            ReferralManager.isAdFreeFromReferrals(this)

            // Initialize Mobile Ads SDK
            AppLog.d("MyApplication", "onCreate() - Initializing MobileAds SDK...")
            MobileAds.initialize(this) {
                AppLog.d("MyApplication", "onCreate() - ✅ MobileAds SDK initialized")
                // Preload ad after SDK is initialized (no activity yet, just preload)
                AppLog.d("MyApplication", "onCreate() - Preloading app open ad...")
                AppOpenAdManager.loadAd(applicationContext) // Just preload, no activity yet
            }
            
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            
            // Initialize RevenueCat for subscription management (ad removal)
                AppLog.d("MyApplication", "onCreate() - Initializing RevenueCat...")
            try {
                // RevenueCat API key should be in local_config.properties as REVENUECAT_API_KEY
                // If not found, RevenueCatManager will handle gracefully
                RevenueCatManager.initialize(this)
                // DEBUG_FORCE_FREE_USER is automatically ON in debug, OFF in release (via BuildConfig.DEBUG)
                if (RevenueCatManager.DEBUG_FORCE_FREE_USER) {
                    // Reset all paywall/review prefs for fresh testing each debug run
                    getSharedPreferences("paywall_trigger_prefs", 0).edit().clear().apply()
                    getSharedPreferences("review_prompt_prefs", 0).edit().clear().apply()
                    getSharedPreferences("ai_usage_prefs", 0).edit().clear().apply()
                    AppLog.w("MyApplication", "DEBUG mode: Forcing free user + reset prefs for testing")
                }
                AppLog.d("MyApplication", "onCreate() - RevenueCat initialized (forceFree=${RevenueCatManager.DEBUG_FORCE_FREE_USER})")
            } catch (e: Exception) {
                // RevenueCat initialization failure is not fatal - app can continue with ads
                AppLog.w("MyApplication", "RevenueCat initialization failed - ads will be shown", e)
                ErrorHandler.handleError(e, context = "MyApplication.onCreate-RevenueCat")
            }
            
            // Initialize Remote Config
            AppLog.d("MyApplication", "onCreate() - Initializing RemoteConfig...")
            RemoteConfigUtils.init()
            AppLog.d("MyApplication", "onCreate() - RemoteConfig initialized")
            
            // Initialize Leaderboard Manager (Firebase Auth)
            AppLog.d("MyApplication", "onCreate() - Initializing LeaderboardManager...")
            com.teamz.lab.debugger.utils.LeaderboardManager.initialize(this)
            AppLog.d("MyApplication", "onCreate() - LeaderboardManager initialized")
            
            // Link RevenueCat to Firebase Auth for cross-device purchase restoration
            AppLog.d("MyApplication", "onCreate() - Setting up Firebase Auth listener for RevenueCat...")
            val firebaseAuth = FirebaseAuth.getInstance()
            
            // Check immediately if user already exists (for faster linking on app start)
            val currentUser = firebaseAuth.currentUser
            if (currentUser != null) {
                AppLog.d("MyApplication", "Firebase Auth user already exists: ${currentUser.uid}, linking RevenueCat immediately...")
                // Small delay to ensure RevenueCat is fully initialized
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    RevenueCatManager.setUserId(currentUser.uid)
                }, 500)
            }
            
            // Set up listener for future auth state changes
            firebaseAuth.addAuthStateListener { auth ->
                val user = auth.currentUser
                if (user != null) {
                    // User is authenticated (anonymous or Gmail) - link RevenueCat to Firebase UID
                    AppLog.d("MyApplication", "Firebase Auth user detected: ${user.uid}, linking RevenueCat...")
                    RevenueCatManager.setUserId(user.uid)
                } else {
                    AppLog.d("MyApplication", "No Firebase Auth user - RevenueCat will use anonymous ID")
                }
            }
            
            // Schedule automatic leaderboard data upload on app start
            // This ensures data is uploaded even if user doesn't interact with the app
            CoroutineScope(Dispatchers.IO).launch {
                kotlinx.coroutines.delay(3000) // Wait for Firebase Auth to initialize
                com.teamz.lab.debugger.utils.LeaderboardDataUpload.uploadOnAppStart(this@MyApplication)
            }
            
            // Schedule daily background upload using WorkManager
            com.teamz.lab.debugger.utils.LeaderboardBackgroundUploadWorker.scheduleDailyUpload(this)
            
            // Verbose Logging only in debug builds for production performance
            if (BuildConfig.DEBUG) {
                OneSignal.Debug.logLevel = LogLevel.VERBOSE
            } else {
                OneSignal.Debug.logLevel = LogLevel.WARN
            }

            // OneSignal Initialization - CRITICAL: App cannot function without this
            try {
                val oneSignalAppId = com.teamz.lab.debugger.utils.AdConfig.getOneSignalAppId()
                if (oneSignalAppId.isNotEmpty()) {
                    OneSignal.initWithContext(this, oneSignalAppId)
                } else {
                    AppLog.w("MyApplication", "OneSignal App ID not configured - skipping initialization")
                }
            } catch (e: Exception) {
                // OneSignal initialization failure is critical - app should crash
                ErrorHandler.handleFatalError(
                    Exception("Failed to initialize OneSignal: ${e.message}", e),
                    context = "MyApplication.onCreate-OneSignal"
                )
            }

            // requestPermission will show the native Android notification permission prompt.
            // NOTE: It's recommended to use a OneSignal In-App Message to prompt instead.
            CoroutineScope(Dispatchers.IO).launch {
                OneSignal.Notifications.requestPermission(false)
            }
            
            // Initialize automatic background notification scheduling for retention
            // OneSignal handles promotional notifications from backend
            RetentionNotificationManager.initializeRetentionNotifications(this)

            // Warm up on-device AI availability so the first AI chooser open already
            // knows whether to surface the Private AI row. Non-blocking — any failure
            // is recorded and the chooser falls back silently to the cloud-AI flow.
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    com.teamz.lab.debugger.ai.ondevice.OnDeviceAiAvailability
                        .refreshStatus(this@MyApplication)
                }.onFailure {
                    AppLog.w("MyApplication", "On-device AI probe failed", it)
                }
            }
        } catch (e: Exception) {
            // Critical initialization failure - app cannot continue
            ErrorHandler.handleFatalError(
                Exception("Critical app initialization failed: ${e.message}", e),
                context = "MyApplication.onCreate"
            )
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        AppLog.d("MyApplication", "onStart() - App lifecycle onStart called, currentActivity: ${currentActivity?.javaClass?.simpleName ?: "null"}")
        isAppInBackground = false
        
        // REMOVED: Don't show app open ad here - let MainActivity handle it with proper lifecycle awareness
        // This prevents duplicate triggers when activity is recreated
        
        // Only preload interstitial if we don't have one already
        if (!InterstitialAdManager.isAdLoaded() && !InterstitialAdManager.isLoading()) {
            InterstitialAdManager.loadAd(applicationContext)
        }
        
        // Track app open time for retention notifications
        RetentionNotificationManager.updateLastAppOpenTime(applicationContext)
        
        // Send immediate notification if needed (for when app is open)
        CoroutineScope(Dispatchers.IO).launch {
            RetentionNotificationManager.sendDailyHealthReminder(applicationContext)
        }
    }
    
    override fun onStop(owner: LifecycleOwner) {
        AppLog.d("MyApplication", "onStop() - App lifecycle onStop called")
        isAppInBackground = true
        AppOpenAdManager.onAppWentToBackground() // Track background time for smart ad timing
    }

    override fun onActivityStarted(activity: Activity) {
        AppLog.d("MyApplication", "onActivityStarted() - Activity started: ${activity.javaClass.simpleName}")
        currentActivity = activity
        
        // REMOVED: Don't show app open ad here - let MainActivity handle it with proper lifecycle awareness
        // This prevents duplicate triggers when activity is recreated (screen rotation, returning from another app)
        // MainActivity.onStart() will handle showing the ad with proper cold start detection
        
        // Track app open time for retention notifications
        RetentionNotificationManager.updateLastAppOpenTime(applicationContext)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }
    
    /**
     * Set up global uncaught exception handler
     * This catches all unhandled exceptions and logs them to Crashlytics
     * 
     * CRITICAL: This must be called first in onCreate() before any other initialization
     * If this fails, the app cannot properly log crashes
     */
    private fun setupGlobalExceptionHandler() {
        try {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    // Log to Crashlytics
                    ErrorHandler.handleThrowable(
                        throwable,
                        context = "UncaughtException-Thread:${thread.name}"
                    )
                    
                    // Set additional context
                    ErrorHandler.setCustomKey("thread_name", thread.name)
                    ErrorHandler.setCustomKey("thread_id", thread.id.toString())
                    
                    // Log to console
                    AppLog.e("MyApplication", "Uncaught exception in thread: ${thread.name}", throwable)
                    
                } catch (e: Exception) {
                    // If error handling itself fails, use default handler
                    AppLog.e("MyApplication", "Error in exception handler: ${e.message}", e)
                } finally {
                    // Call the default handler to ensure app crashes properly
                    // This is important for Android to show crash dialog
                    defaultUncaughtExceptionHandler?.uncaughtException(thread, throwable)
                }
            }
            
            AppLog.d("MyApplication", "Global exception handler set up")
        } catch (e: Exception) {
            // If we can't set up exception handler, app is in critical state
            // Log to system log and rethrow - app must crash
            AppLog.e("MyApplication", "CRITICAL: Failed to set up exception handler", e)
            ErrorHandler.handleFatalError(
                Exception("Failed to set up global exception handler: ${e.message}", e),
                context = "MyApplication.setupGlobalExceptionHandler"
            )
        }
    }
}
