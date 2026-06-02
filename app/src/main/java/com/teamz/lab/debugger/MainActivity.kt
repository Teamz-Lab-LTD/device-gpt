package com.teamz.lab.debugger

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManagerFactory
import com.teamz.lab.debugger.services.setAlreadyReviewed
import com.teamz.lab.debugger.services.userHasAlreadyReviewed
import com.teamz.lab.debugger.ui.NativeAdManager
import com.teamz.lab.debugger.ui.adaptive.AdaptiveDeviceGptLayout
import com.teamz.lab.debugger.ui.theme.LocalThemeManager
import com.teamz.lab.debugger.ui.theme.ThemeAwareContent
import com.teamz.lab.debugger.ui.theme.ThemeManager
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.AppLog
import com.teamz.lab.debugger.utils.AppOpenAdManager
import com.teamz.lab.debugger.utils.DeviceSleepTracker
import com.teamz.lab.debugger.utils.EngagementTracker
import com.teamz.lab.debugger.utils.RemoteConfigUtils
import com.teamz.lab.debugger.utils.ErrorHandler
import com.teamz.lab.debugger.utils.InterstitialAdManager
import com.teamz.lab.debugger.utils.ReferralManager
import com.teamz.lab.debugger.utils.RevenueCatManager
import com.teamz.lab.debugger.utils.ReviewPromptManager
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.restore.RestoreCredentialManager
import com.teamz.lab.debugger.utils.LocaleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.createContextWithLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.d("PowerStateDebug", "MainActivity onCreate - savedInstanceState: ${if (savedInstanceState != null) "EXISTS" else "null"} (hashCode: ${hashCode()})")

        isFirstLaunch = savedInstanceState == null
        AppLog.d("MainActivity", "onCreate() - isFirstLaunch: $isFirstLaunch")

        try {
            LocaleManager.setLocale(this)

            try {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                enableEdgeToEdge()
            } catch (e: Exception) {
                AppLog.w("MainActivity", "Window operation failed (non-fatal): ${e.message}", e)
            }

            try {
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
            } catch (e: Exception) {
                AppLog.w("MainActivity", "Failed to set soft input mode (non-fatal): ${e.message}", e)
            }

            try {
                ThemeManager.initialize(this)
            } catch (e: Exception) {
                ErrorHandler.handleFatalError(
                    Exception("Failed to initialize ThemeManager: ${e.message}", e),
                    context = "MainActivity.onCreate-ThemeManager",
                )
            }

            setContent {
                CompositionLocalProvider(LocalThemeManager provides ThemeManager) {
                    ThemeAwareContent {
                        AdaptiveDeviceGptLayout(this@MainActivity)
                    }
                }
            }

            lifecycleScope.launch(Dispatchers.IO) {
                RestoreCredentialManager.runPostLaunchRestore(this@MainActivity)
            }

            window.decorView.post {
                try {
                    AnalyticsUtils.init(this@MainActivity)
                } catch (e: Exception) {
                    ErrorHandler.handleError(e, context = "MainActivity.onCreate-Analytics")
                }

                ReferralManager.checkReferral(this@MainActivity, intent)
                ReferralManager.checkInstallReferrer(this@MainActivity)
                ReferralManager.isAdFreeFromReferrals(this@MainActivity)
                ReferralManager.onReferredUserAppOpen(this@MainActivity)
                EngagementTracker.init(this@MainActivity)
                RemoteConfigUtils.captureCountryCode(this@MainActivity)
                handleChargeSummaryDeepLink(intent)

                DeviceSleepTracker.initializeState(this@MainActivity)

                checkForAppUpdate(this@MainActivity)
            }
        } catch (e: Exception) {
            ErrorHandler.handleFatalError(
                Exception("Critical MainActivity initialization failed: ${e.message}", e),
                context = "MainActivity.onCreate",
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ReferralManager.checkReferral(this, intent)
        handleChargeSummaryDeepLink(intent)
    }

    /**
     * Single dispatch for any notification deep-link source. Each "from" value comes
     * from a specific notification surface (charge cycle ritual hook, persistent system
     * monitor notification, etc.). Logs both the AnalyticsEvent (for GA4 aggregation)
     * and the SignificantAction (for EngagementTracker habit-streak math).
     */
    private fun handleChargeSummaryDeepLink(intent: Intent?) {
        val from = intent?.getStringExtra("from") ?: return
        when (from) {
            "charge_summary" -> {
                com.teamz.lab.debugger.utils.AnalyticsUtils.logEvent(
                    com.teamz.lab.debugger.utils.AnalyticsEvent.ChargeSummaryOpened
                )
                com.teamz.lab.debugger.utils.EngagementTracker.trackSignificantAction(
                    this,
                    com.teamz.lab.debugger.utils.SignificantAction.CHARGE_SUMMARY_OPENED
                )
            }
            "monitor_notification" -> {
                com.teamz.lab.debugger.utils.AnalyticsUtils.logEvent(
                    com.teamz.lab.debugger.utils.AnalyticsEvent.MonitorNotificationOpened
                )
                com.teamz.lab.debugger.utils.EngagementTracker.trackSignificantAction(
                    this,
                    "monitor_notification_opened"
                )
            }
        }
        // Clear the extra so a configuration change doesn't double-log.
        intent.removeExtra("from")
    }

    override fun onResume() {
        super.onResume()

        try {
            val currentFocus = currentFocus
            if (currentFocus != null) {
                currentFocus.clearFocus()
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to clear focus in onResume (non-fatal): ${e.message}", e)
        }

        val appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                Toast.makeText(this, getString(R.string.update_downloaded_restarting), Toast.LENGTH_SHORT).show()
                appUpdateManager.completeUpdate()
            }
        }
    }

    private var wasAdShowing = false
    private var isFirstLaunch = true

    override fun onStart() {
        super.onStart()
        android.util.Log.d("MainActivity", "onStart() - MainActivity onStart called, wasAdShowing: $wasAdShowing, isFirstLaunch: $isFirstLaunch")

        val isPremium = RevenueCatManager.isPremium()
        val isReferralAdFree = ReferralManager.isAdFreeFromReferrals(this)
        if (isPremium || isReferralAdFree) {
            android.util.Log.d("MainActivity", "onStart() - ⚠️ User has premium or referral ad-free, skipping app open ad")
            AppOpenAdManager.clearAd()
        } else {
            if (!wasAdShowing) {
                if (isFirstLaunch) {
                    android.util.Log.d("MainActivity", "onStart() - ✅ Cold start detected, showing app open ad")
                    AppOpenAdManager.showAdIfAvailable(this, isColdStart = true)
                } else {
                    android.util.Log.d("MainActivity", "onStart() - Activity recreated, checking background time and cooldown...")
                    AppOpenAdManager.showAdIfAvailable(this, isColdStart = false)
                }
            } else {
                android.util.Log.d("MainActivity", "onStart() - ⚠️ Skipping app open ad (resuming from interstitial)")
            }
        }
        wasAdShowing = false

        val wasColdStart = isFirstLaunch
        isFirstLaunch = false
        ReviewPromptManager.trackAppOpenAndMaybeShowReview(this, isColdStart = wasColdStart)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            try {
                val currentFocus = currentFocus
                if (currentFocus != null) {
                    currentFocus.clearFocus()
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Failed to clear focus (non-fatal): ${e.message}", e)
            }
        }

        super.onWindowFocusChanged(hasFocus)
    }

    override fun onPause() {
        super.onPause()
        wasAdShowing = InterstitialAdManager.isAdLoaded() || InterstitialAdManager.isLoading()
        android.util.Log.d("PowerStateDebug", "MainActivity onPause - wasAdShowing set to: $wasAdShowing (hashCode: ${hashCode()})")
    }

    override fun onDestroy() {
        android.util.Log.d("PowerStateDebug", "MainActivity onDestroy - Activity destroyed (hashCode: ${hashCode()})")
        super.onDestroy()
        NativeAdManager.clear()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window.decorView.post { window.decorView.invalidate() }
        AppLog.d(
            "MainActivity",
            "onConfigurationChanged smallestWidthDp=${newConfig.smallestScreenWidthDp} " +
                "screenWidthDp=${newConfig.screenWidthDp} densityDpi=${newConfig.densityDpi}",
        )
    }

    private val UPDATE_REQUEST_CODE = 1234

    private fun checkForAppUpdate(activity: Activity) {
        val appUpdateManager = AppUpdateManagerFactory.create(activity)

        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && appUpdateInfo.isUpdateTypeAllowed(
                    AppUpdateType.FLEXIBLE,
                )
            ) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo, AppUpdateType.FLEXIBLE, activity, UPDATE_REQUEST_CODE,
                    )
                } catch (e: IntentSender.SendIntentException) {
                    ErrorHandler.handleError(e, context = "MainActivity.startAppUpdate")
                }
            }
        }
    }
}

fun Activity.showInAppReview() {
    if (userHasAlreadyReviewed()) {
        val intent = Intent(
            Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri(),
        )
        startActivity(intent)
    } else {
        val manager = ReviewManagerFactory.create(this)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                manager.launchReviewFlow(this, reviewInfo).addOnCompleteListener {
                    try {
                        setAlreadyReviewed(true)

                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                com.teamz.lab.debugger.utils.ReviewPromptManager.saveReviewStatusToFirebase(
                                    this@showInAppReview,
                                    true,
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Error saving review to Firebase", e)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error setting review status", e)
                    }
                }
            } else {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$packageName".toUri(),
                )
                startActivity(intent)
            }
        }
    }
}
