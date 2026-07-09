package com.teamz.lab.debugger.ui.adaptive

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.services.isDoNotAskMeAgain
import com.teamz.lab.debugger.services.isSystemMonitorRunning
import com.teamz.lab.debugger.services.isUserEnableMonitoringService
import com.teamz.lab.debugger.services.isUserFirstTime
import com.teamz.lab.debugger.services.setDoNotAskMeAgain
import com.teamz.lab.debugger.services.setUserFirstTime
import com.teamz.lab.debugger.services.startSystemMonitorService
import com.teamz.lab.debugger.ui.AIAssistantDialog
import com.teamz.lab.debugger.ui.PaywallWithReferralFallback
import com.teamz.lab.debugger.ui.ai.rememberPrivateAiFlow
import com.teamz.lab.debugger.ui.DrawerContent
import com.teamz.lab.debugger.ui.DeviceInfoSection
import com.teamz.lab.debugger.ui.GenerateReportDialog
import com.teamz.lab.debugger.ui.HealthSection
import com.teamz.lab.debugger.ui.LeaderboardSection
import com.teamz.lab.debugger.ui.NativeAdManager
import com.teamz.lab.debugger.ui.NetworkInfoSection
import com.teamz.lab.debugger.ui.NotificationPermissionDialog
import com.teamz.lab.debugger.ui.PowerConsumptionSection
import com.teamz.lab.debugger.ui.ReportReadyDialog
import com.teamz.lab.debugger.ui.RevenueCatPaywall
import com.teamz.lab.debugger.ui.VerifyReportDialog
import com.teamz.lab.debugger.ui.ViralShareDialog
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.ui.theme.QuickThemeSwitcher
import com.teamz.lab.debugger.utils.AIIcon
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.AppOpenAdManager
import com.teamz.lab.debugger.utils.EngagementTracker
import com.teamz.lab.debugger.utils.ErrorHandler
import com.teamz.lab.debugger.utils.InterstitialAdManager
import com.teamz.lab.debugger.utils.PaywallPolicy
import com.teamz.lab.debugger.utils.PowerConsumptionAggregator
import com.teamz.lab.debugger.utils.PowerConsumptionUtils
import com.teamz.lab.debugger.utils.RemoteConfigUtils
import com.teamz.lab.debugger.utils.RevenueCatManager
import com.teamz.lab.debugger.utils.ReviewPromptManager
import com.teamz.lab.debugger.utils.TabOrderManager
import com.teamz.lab.debugger.utils.TabType
import com.teamz.lab.debugger.utils.VerifiedReport
import com.teamz.lab.debugger.utils.handleError
import com.teamz.lab.debugger.utils.openSettings
import com.teamz.lab.debugger.utils.string
import com.revenuecat.purchases.ui.revenuecatui.ExperimentalPreviewRevenueCatUIPurchasesAPI
import java.io.File
import kotlinx.coroutines.launch

private fun logMainTabSelectionAnalytics(tabType: TabType) {
    when (tabType) {
        TabType.LEADERBOARD -> AnalyticsUtils.logEvent(AnalyticsEvent.TabLeaderboardViewed)
        TabType.HEALTH -> AnalyticsUtils.logEvent(AnalyticsEvent.TabHealthViewed)
        TabType.POWER -> AnalyticsUtils.logEvent(AnalyticsEvent.TabPowerViewed)
        TabType.DEVICE_INFO -> AnalyticsUtils.logEvent(AnalyticsEvent.TabDeviceInfoViewed)
        TabType.NETWORK_INFO -> AnalyticsUtils.logEvent(AnalyticsEvent.TabNetworkInfoViewed)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPreviewRevenueCatUIPurchasesAPI::class)
@Composable
fun DeviceGptNavExperience(
    activity: ComponentActivity,
    chromeMode: DeviceGptChromeMode
) {
    // Set up drawer state and coroutine scope for opening/closing the drawer.
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }
    // Check intent for navigation from widget (lock screen widget tap)
    // Support both old index-based and new name-based navigation for backward compatibility
    val initialTab = remember(activity) {
        val intent = activity.intent
        val navigateToSection = intent?.getIntExtra("navigate_to_section", -1) ?: -1
        val navigateToTab = intent?.getStringExtra("navigate_to_tab") ?: ""
        val source = intent?.getStringExtra("source") ?: "unknown"
        val trackWidgetTap = intent?.getBooleanExtra("track_widget_tap", false) ?: false
        val widgetAction = intent?.getStringExtra("widget_action") ?: ""
        
        // Use TabOrderManager to get tab indices dynamically
        val targetTab = when {
            // New name-based navigation (preferred)
            navigateToTab.equals("health", ignoreCase = true) -> TabOrderManager.getTabIndex(TabType.HEALTH)
            navigateToTab.equals("power", ignoreCase = true) -> TabOrderManager.getTabIndex(TabType.POWER)
            navigateToTab.equals("device_info", ignoreCase = true) -> TabOrderManager.getTabIndex(TabType.DEVICE_INFO)
            navigateToTab.equals("network_info", ignoreCase = true) -> TabOrderManager.getTabIndex(TabType.NETWORK_INFO)
            navigateToTab.equals("leaderboard", ignoreCase = true) -> TabOrderManager.getLeaderboardIndex() ?: -1
            // Old index-based navigation (backward compatibility)
            navigateToSection >= 0 -> navigateToSection
            else -> -1
        }
        
        if (targetTab >= 0) {
            // Log analytics for widget tap
            if (trackWidgetTap || source == "lock_screen_widget") {
                AnalyticsUtils.logEvent(AnalyticsEvent.WidgetTapped, mapOf(
                    "source" to source,
                    "target_section" to targetTab,
                    "navigation_method" to if (navigateToTab.isNotEmpty()) "name_based" else "index_based"
                ))
            }
            // Also log tab view for consistency
            val tabName = TabOrderManager.getTabNameForAnalytics(targetTab)
            AnalyticsUtils.logEvent(AnalyticsEvent.TabHealthViewed, mapOf(
                "source" to source,
                "tab_name" to tabName
            ))
            targetTab
        } else {
            // Default to first tab (usually Leaderboard if enabled)
            0
        }
    }
    var selectedTab by remember { mutableIntStateOf(initialTab) } // Current tab index
    
    // Handle widget action (e.g., clear RAM)
    val widgetAction = remember(activity) {
        activity.intent?.getStringExtra("widget_action") ?: ""
    }
    
    LaunchedEffect(widgetAction) {
        if (widgetAction == "clear_ram") {
            // Play policy 2026-07-10: interstitial removed from the widget-tap path.
            // A fullscreen ad between tapping the widget and seeing the memory report
            // is the disruptive-ads pattern — and this is the #1 surface (59 displays/user).
            val (success, message) = com.teamz.lab.debugger.utils.clearRam(context)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            AnalyticsUtils.logEvent(
                AnalyticsEvent.WidgetTapped,
                mapOf(
                    "source" to "lock_screen_widget",
                    "action" to "clear_ram",
                    "success" to success
                )
            )
            // Clear the action to prevent re-execution
            activity.intent?.removeExtra("widget_action")
        }
    }
    
    val appName = remember {
        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        context.packageManager.getApplicationLabel(applicationInfo).toString()
    }
    var shareText by remember { mutableStateOf(context.string(R.string.loading)) }
    var showAIDialog by remember { mutableStateOf(false) }
    var showAICertificateDialog by remember { mutableStateOf(false) }
    var showViralShareDialog by remember { mutableStateOf(false) }
    var selectedItemForAI by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showItemAIDialog by remember { mutableStateOf(false) }
    var showRevenueCatPaywall by remember { mutableStateOf(false) }
    var paywallAnalyticsSource by remember { mutableStateOf("revenuecat_paywall") }
    var showGenerateReportDialog by remember { mutableStateOf(false) }
    // v3.2.0 rewarded v1: "watch an ad -> generate 1 report" offer dialog.
    var showRewardedReportOffer by remember { mutableStateOf(false) }
    var showReportReadyDialog by remember { mutableStateOf(false) }
    var showVerifyReportDialog by remember { mutableStateOf(false) }
    var generatedReport by remember { mutableStateOf<VerifiedReport?>(null) }


    // Simple state management without any derivedStateOf
    var isAIReady by remember { mutableStateOf(false) }
    var isCertificateLoading by remember { mutableStateOf(true) }

    // Use derivedStateOf for FAB states to avoid continuous evaluation
    val fabStates by remember { 
        derivedStateOf {
            // All tabs now support sharing - enable FABs when shareText is ready
            val isCompletelyReady = !shareText.contains(context.string(R.string.loading)) && shareText.isNotEmpty()
            Pair(isCompletelyReady, !isCompletelyReady)
        }
    }
    
    // Update FAB states from derived state
    LaunchedEffect(fabStates) {
        isAIReady = fabStates.first
        isCertificateLoading = fabStates.second
    }

    var showPriceInputDialog by remember { mutableStateOf(false) }
    var inputPrice by remember { mutableStateOf("") }
    var inputCurrency by remember { mutableStateOf("USD") }

    HandleSystemMonitorAutoStart()

    // === "Review First, Paywall After" strategy ===
    // Flow: App Open → Review prompt (3rd session) → Paywall (chained after review)
    // Users subconsciously rate 5 stars before seeing pricing.
    // Ref: https://x.com/WasimShips/status/2034291252561104922

    // Track whether paywall was already triggered this session (avoid double-trigger)
    var paywallTriggeredThisSession by remember { mutableStateOf(false) }

    // Chain 1: Paywall shows AFTER review flow completes
    LaunchedEffect(Unit) {
        ReviewPromptManager.reviewFlowCompleted.collect {
            AnalyticsUtils.logEvent(AnalyticsEvent.PaywallChainReviewCompleted, mapOf(
                "is_premium" to RevenueCatManager.isPremium(),
                "already_triggered" to paywallTriggeredThisSession,
                "strategy_enabled" to RemoteConfigUtils.isReviewFirstStrategyEnabled()
            ))
            if (!RevenueCatManager.isPremium() && !paywallTriggeredThisSession &&
                RemoteConfigUtils.isReviewFirstStrategyEnabled()) {
                // v3.2.0: cold-open paywall gated on session>=min + first scan done.
                // 89% dismiss rate was measured on ungated cold-open triggers.
                if (!PaywallPolicy.coldTriggerAllowed(context)) {
                    AnalyticsUtils.logEvent(AnalyticsEvent.PaywallColdGateBlocked, mapOf(
                        "chain" to "after_review_chain",
                        "session_count" to EngagementTracker.getSessionCount(context)
                    ))
                    return@collect
                }
                val delayMs = RemoteConfigUtils.getPaywallDelayAfterReviewMs()
                kotlinx.coroutines.delay(delayMs)
                val prefs = context.getSharedPreferences("paywall_trigger_prefs", Context.MODE_PRIVATE)
                prefs.edit().putLong("last_paywall_shown_time", System.currentTimeMillis()).apply()
                paywallTriggeredThisSession = true
                paywallAnalyticsSource = "after_review_chain"
                AnalyticsUtils.logEvent(AnalyticsEvent.PaywallChainTriggered, mapOf(
                    "delay_ms" to delayMs,
                    "source" to "after_review_chain"
                ))
                showRevenueCatPaywall = true
            } else {
                AnalyticsUtils.logEvent(AnalyticsEvent.PaywallChainSkipped, mapOf(
                    "reason" to when {
                        RevenueCatManager.isPremium() -> "user_is_premium"
                        paywallTriggeredThisSession -> "already_triggered_this_session"
                        else -> "strategy_disabled"
                    }
                ))
            }
        }
    }

    // Chain 2: Fallback paywall for sessions where review doesn't show
    // (e.g., user already reviewed, 30-day cooldown, Google quota reached)
    // Uses longer delay to give the review chain priority
    LaunchedEffect(Unit) {
        if (RevenueCatManager.isPremium()) return@LaunchedEffect

        val paywallPrefs = context.getSharedPreferences("paywall_trigger_prefs", Context.MODE_PRIVATE)
        val sessionCount = paywallPrefs.getInt("paywall_session_count", 0) + 1
        paywallPrefs.edit().putInt("paywall_session_count", sessionCount).apply()

        val lastPaywallTime = paywallPrefs.getLong("last_paywall_shown_time", 0L)
        val daysSinceLastPaywall = if (lastPaywallTime > 0L)
            (System.currentTimeMillis() - lastPaywallTime) / (24 * 60 * 60 * 1000)
        else Long.MAX_VALUE

        val repeatDays = RemoteConfigUtils.getPaywallRepeatIntervalDays()
        // v3.2.0: cold-open fallback gated on session>=min + first scan done.
        if (!PaywallPolicy.coldTriggerAllowed(context)) {
            AnalyticsUtils.logEvent(AnalyticsEvent.PaywallColdGateBlocked, mapOf(
                "chain" to "smart_session_trigger",
                "session_count" to EngagementTracker.getSessionCount(context)
            ))
            return@LaunchedEffect
        }
        val shouldShow = lastPaywallTime == 0L || daysSinceLastPaywall >= repeatDays
        if (shouldShow) {
            val fallbackDelay = RemoteConfigUtils.getPaywallFallbackDelayMs()
            kotlinx.coroutines.delay(fallbackDelay)
            if (!RevenueCatManager.isPremium() && !paywallTriggeredThisSession) {
                paywallPrefs.edit().putLong("last_paywall_shown_time", System.currentTimeMillis()).apply()
                paywallTriggeredThisSession = true
                paywallAnalyticsSource = "smart_session_trigger"
                AnalyticsUtils.logEvent(AnalyticsEvent.PaywallFallbackTriggered, mapOf(
                    "session_count" to sessionCount,
                    "days_since_last" to daysSinceLastPaywall,
                    "fallback_delay_ms" to fallbackDelay
                ))
                showRevenueCatPaywall = true
            } else {
                AnalyticsUtils.logEvent(AnalyticsEvent.PaywallFallbackSkipped, mapOf(
                    "reason" to if (RevenueCatManager.isPremium()) "user_is_premium" else "already_triggered",
                    "session_count" to sessionCount
                ))
            }
        } else {
            AnalyticsUtils.logEvent(AnalyticsEvent.PaywallFallbackSkipped, mapOf(
                "reason" to "cooldown_not_expired",
                "days_since_last" to daysSinceLastPaywall,
                "repeat_days" to repeatDays
            ))
        }
    }

    // Wrap the Scaffold in a ModalNavigationDrawer
    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        DrawerContent(
            activity = activity,
            drawerState = drawerState,
            onPermissionChanged = { _, _ ->
                refreshTrigger++
            },
            onShareClick = {
                showViralShareDialog = true
            },
            onGenerateVerifiedReport = {
                if (!RevenueCatManager.isPremium()) {
                    // v3.2.0 rewarded v1 (RC rewarded_report_enabled, default OFF):
                    // offer renders ONLY when a rewarded ad is actually loaded —
                    // a no-fill unlock button is a broken promise (36.9% match rate).
                    if (RemoteConfigUtils.isRewardedReportEnabled() &&
                        com.teamz.lab.debugger.utils.RewardedAdManager.isAdLoaded()
                    ) {
                        AnalyticsUtils.logEvent(AnalyticsEvent.RewardedUnlockOffered, mapOf("feature" to "verified_report"))
                        showRewardedReportOffer = true
                    } else {
                        paywallAnalyticsSource = "verified_report_gate"
                        AnalyticsUtils.logEvent(AnalyticsEvent.PaywallVerifiedReportGated)
                        showRevenueCatPaywall = true
                    }
                } else {
                    InterstitialAdManager.showAdBeforeAction(
                        activity = activity,
                        actionName = "generate_verified_report"
                    ) {
                        showGenerateReportDialog = true
                    }
                }
            },
            onVerifyReport = {
                InterstitialAdManager.showAdBeforeAction(
                    activity = activity,
                    actionName = "verify_report"
                ) {
                    showVerifyReportDialog = true
                }
            }
        )
    }) {
        Scaffold(topBar = {
            val menuTooltipState = rememberTooltipState()
            val refreshTooltipState = rememberTooltipState()
            val settingsTooltipState = rememberTooltipState()
            val devTooltipState = rememberTooltipState()
            
            TopAppBar(title = { Text(appName) }, navigationIcon = {
                TooltipBox(
                    state = menuTooltipState,
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { 
                        Text(if (context.isSystemMonitorRunning()) context.string(R.string.open_menu) else context.string(R.string.open_menu_monitor_available))
                    } }
                ) {
                    Box {
                        IconButton(onClick = { 
                            AnalyticsUtils.logEvent(AnalyticsEvent.TopBarMenuOpened)
                            scope.launch { drawerState.open() } 
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = context.string(R.string.menu))
                        }
                        // Subtle indicator dot when monitor is off
                        if (!context.isSystemMonitorRunning()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 8.dp, end = 8.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }, actions = {
                TooltipBox(
                    state = refreshTooltipState,
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(context.string(R.string.refresh_all_data)) } }
                ) {
                    IconButton(onClick = {
                        AnalyticsUtils.logEvent(AnalyticsEvent.TopBarRefreshClicked)
                        refreshTrigger++
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = context.string(R.string.refresh),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                QuickThemeSwitcher()
                TooltipBox(
                    state = settingsTooltipState,
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(context.string(R.string.open_device_settings)) } }
                ) {
                    IconButton(onClick = {
                        AnalyticsUtils.logEvent(AnalyticsEvent.TopBarSettingsClicked)
                        openSettings(context, Settings.ACTION_SETTINGS)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = context.string(R.string.settings),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                TooltipBox(
                    state = devTooltipState,
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(context.string(R.string.open_developer_options)) } }
                ) {
                    IconButton(onClick = {
                        AnalyticsUtils.logEvent(AnalyticsEvent.TopBarDevOptionsClicked)
                        openSettings(context, ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = context.string(R.string.developer_options),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            })
        }, floatingActionButton = {
            // Hide FABs when leaderboard tab is selected
            val leaderboardTabIndex = remember { TabOrderManager.getLeaderboardIndex() }
            val isLeaderboardTabSelected = leaderboardTabIndex != null && selectedTab == leaderboardTabIndex
            
            if (!isLeaderboardTabSelected) {
                val certTooltipState = rememberTooltipState()
                val aiTooltipState = rememberTooltipState()
                val premiumTooltipState = rememberTooltipState()
                
                // Reactive premium status check - automatically updates when premium is purchased
                val premiumStatus by RevenueCatManager.premiumStatusFlow.collectAsState()
                val currentPremiumStatus = premiumStatus
                val isPremium = currentPremiumStatus is RevenueCatManager.PremiumStatus.Premium && 
                    (currentPremiumStatus as? RevenueCatManager.PremiumStatus.Premium)?.isActive == true
                
                var productPrice by remember { mutableStateOf<String?>(null) }
                
                // Fetch price dynamically from RevenueCat
                LaunchedEffect(isPremium) {
                    if (!isPremium) {
                        RevenueCatManager.getLifetimeProductPrice(
                            onSuccess = { price ->
                                productPrice = price
                            },
                            onError = { error ->
                                // No hardcoded fallback — a wrong displayed price is a
                                // misleading-price claim. FAB renders "PRO" when null.
                                android.util.Log.w("MainActivity", "Failed to fetch price: $error, hiding price")
                            }
                        )
                    }
                }
                
                // Premium FAB - More noticeable animation for better focus
                // Create infinite transition with more pronounced pulse
                val infiniteTransition = rememberInfiniteTransition(label = "premium_fab_animation")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.12f, // More noticeable scale for better focus
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = FastOutSlowInEasing), // Faster, more noticeable
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_scale"
                )
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.85f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "glow_alpha"
                )
                
                // Star rotation animation - rotate, pause, then repeat
                // Starts after 3 seconds delay, then rotates with pauses
                var startRotation by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(3000) // Start rotation after 3 seconds
                    startRotation = true
                }
                
                // Separate infinite transition for star rotation with pause
                val starRotationTransition = rememberInfiniteTransition(
                    label = "star_rotation_transition"
                )
                val starRotation by starRotationTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 5500 // Total: 1.5s rotation + 4s pause
                            0f at 0
                            360f at 1500 // Rotate in 1.5 seconds
                            360f at 5500 // Stay at 360 for 4 seconds (pause)
                        },
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "star_rotation"
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, // Even spacing between all FABs
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // Premium FAB - Only show if user is not premium
                if (!isPremium) {
                    TooltipBox(
                        state = premiumTooltipState,
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { 
                            PlainTooltip { 
                                Text(
                                    if (productPrice != null) {
                                        "⭐ Remove Ads - $productPrice Lifetime"
                                    } else {
                                        "⭐ Remove Ads - Lifetime"
                                    }
                                ) 
                            } 
                        }
                    ) {
                        // Main FAB matching other FABs style with continuous subtle animation
                        FloatingActionButton(
                            onClick = {
                                AnalyticsUtils.logEvent(AnalyticsEvent.PremiumFabClicked, mapOf(
                                    "price" to (productPrice ?: "2.99"),
                                    "type" to "lifetime"
                                ))
                                // Show RevenueCat paywall designed in console
                                paywallAnalyticsSource = "premium_fab"
                                showRevenueCatPaywall = true
                            },
                            modifier = Modifier
                                .scale(pulseScale) // Continuous subtle pulse
                                .semantics {
                                    // Make it properly focusable and accessible
                                    contentDescription = if (productPrice != null) {
                                        "Remove Ads - Premium $productPrice Lifetime"
                                    } else {
                                        "Remove Ads - Premium Lifetime"
                                    }
                                    role = Role.Button
                                },
                            containerColor = DesignSystemColors.NeonGreen, // Match other FABs
                            contentColor = DesignSystemColors.Dark, // Dark text on neon green
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 8.dp, // Slightly higher for premium emphasis
                                pressedElevation = 12.dp,
                                hoveredElevation = 10.dp,
                                focusedElevation = 10.dp
                            )
                        ) {
                            // Premium icon with price - matching other FABs style
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = DesignSystemColors.Dark.copy(alpha = glowAlpha), // Subtle glow effect
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(if (startRotation) starRotation else 0f) // Rotate star after delay with pause
                                )
                                Text(
                                    text = productPrice ?: "PRO",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp),
                                    fontSize = 10.sp,
                                    color = DesignSystemColors.Dark
                                )
                            }
                        }
                    }
                }
                // Certificate FAB
                TooltipBox(
                    state = certTooltipState,
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(if (isAIReady) context.string(R.string.generate_device_certificate) else context.string(R.string.loading_certificate_data)) } }
                ) {
                    FloatingActionButton(
                        onClick = {
                            AnalyticsUtils.logEvent(AnalyticsEvent.FabCertificateClicked)
                            InterstitialAdManager.showAdIfAvailable(activity) {
                                showPriceInputDialog = true
                            }
                        },
                        modifier = Modifier, // Remove padding - spacing handled by Row
                        containerColor = if (isAIReady) DesignSystemColors.NeonGreen else
                            DesignSystemColors.White.copy(
                            ),
                        contentColor = if (isAIReady) DesignSystemColors.White else DesignSystemColors.NeonGreen.copy(
                            alpha = 0.12f
                        ),
                    ) {
                        if (isCertificateLoading) {
                            FabLoading()
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = context.string(R.string.certificate),
                                    tint = DesignSystemColors.Dark
                                )
                                Text(
                                    text = context.string(R.string.cert),
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    color = DesignSystemColors.Dark,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
                // AI FAB (existing)
                TooltipBox(
                    state = aiTooltipState,
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(if (isAIReady) context.string(R.string.open_ai_assistant) else context.string(R.string.loading_ai_assistant)) } }
                ) {
                    FloatingActionButton(
                        onClick = {
                            com.teamz.lab.debugger.utils.AIClickHandler.handleAIClick(
                                activity = activity,
                                source = "fab_ai",
                                onPaywallRequest = {
                                    paywallAnalyticsSource = "ai_soft_gate"
                                    showRevenueCatPaywall = true
                                }
                            ) {
                                showAIDialog = true
                            }
                        },
                        modifier = Modifier, // Remove padding - spacing handled by Row
                        containerColor = if (isAIReady) DesignSystemColors.NeonGreen else
                            DesignSystemColors.White.copy(
                            ),
                        contentColor = if (isAIReady) DesignSystemColors.White else DesignSystemColors.NeonGreen.copy(
                            alpha = 0.12f
                        ),
                    ) {
                        if (isAIReady) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = AIIcon.icon,
                                    contentDescription = context.string(R.string.ai_assistant),
                                    tint = DesignSystemColors.Dark
                                )
                                Text(
                                    text = context.string(R.string.ai),
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 2.dp),
                                    color = DesignSystemColors.Dark
                                )
                            }
                        } else FabLoading()

                    }
                }
                // Share FAB (existing)
                FloatingActionButton(
                    modifier = Modifier, // No padding - spacing handled by Row
                    onClick = {
                        if (!shareText.contains(context.string(R.string.loading)) && shareText.isNotEmpty()) {
                            AnalyticsUtils.logEvent(AnalyticsEvent.FabShareClicked, mapOf(
                                "tab" to selectedTab,
                                "tab_name" to TabOrderManager.getTabNameForAnalytics(selectedTab)
                            ))
                            InterstitialAdManager.showAdIfAvailable(activity) {
                                val brandedFooter = """
    
—
🛡️ Scanned and generated by **$appName**

📱 Download now on Google Play:
https://play.google.com/store/apps/details?id=${context.packageName}

💡 Protect, diagnose, and improve your Android with $appName.

""".trimIndent()
                                val fileName = TabOrderManager.getShareFileName(selectedTab)
                                val file = File(context.cacheDir, fileName)
                                try {
                                    file.writeText(shareText + "\n" + brandedFooter)
                                    val authority = "${context.packageName}.fileprovider"
                                    val uri =
                                        FileProvider.getUriForFile(context, authority, file)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent, "Share Device Info"
                                        )
                                    )
                                    AnalyticsUtils.logEvent(AnalyticsEvent.ShareDeviceInfo)
                                } catch (e: Exception) {
                                    ErrorHandler.handleError(e, context = "MainActivity.shareDeviceInfo")
                                    Toast.makeText(
                                        context,
                                        "Unable to share file: ${e.localizedMessage}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    handleError(e)
                                }
                            }
                        }
                    },
                    containerColor = if (!shareText.contains(context.string(R.string.loading)) && shareText.isNotEmpty()) DesignSystemColors.NeonGreen else
                        DesignSystemColors.White.copy(
                        ),
                    contentColor = if (!shareText.contains(context.string(R.string.loading)) && shareText.isNotEmpty()) DesignSystemColors.White else DesignSystemColors.NeonGreen.copy(
                        alpha = 0.12f
                    ),
                ) {
                    if (!shareText.contains(context.string(R.string.loading)) && shareText.isNotEmpty()) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = context.string(R.string.cd_send_info),
                            tint = DesignSystemColors.Dark
                        )
                        Text(
                            text = context.string(R.string.send),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = DesignSystemColors.Dark,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    else FabLoading()

                }
                }
            }
        }) { paddingValues ->
            key(refreshTrigger) {
                val tabOrder = remember(refreshTrigger) { TabOrderManager.getTabOrder() }

                val searchFieldFocusRequester = remember { FocusRequester() }
                var scanFilterQuery by remember { mutableStateOf("") }

                val expandedKeyboardModifier =
                    if (chromeMode == DeviceGptChromeMode.ExpandedThreePane) {
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            fun goTo(category: ScanCategory): Boolean {
                                selectedTab = category.tabIndex()
                                shareText = context.string(R.string.loading)
                                return true
                            }
                            when {
                                event.isCtrlPressed && event.key == Key.F -> {
                                    searchFieldFocusRequester.requestFocus()
                                    true
                                }
                                event.key == Key.R -> {
                                    refreshTrigger++
                                    true
                                }
                                event.key == Key.E -> {
                                    exportScanSummaryCsv(context, activity, shareText)
                                    true
                                }
                                event.key == Key.One -> goTo(ScanCategory.BATTERY)
                                event.key == Key.Two -> goTo(ScanCategory.NETWORK)
                                event.key == Key.Three -> goTo(ScanCategory.HARDWARE)
                                event.key == Key.Four -> goTo(ScanCategory.PRIVACY)
                                event.key == Key.Five -> goTo(ScanCategory.AI)
                                else -> false
                            }
                        }
                    } else Modifier

                @Composable
                fun TabStrip() {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.fillMaxWidth(),
                        edgePadding = 0.dp,
                    ) {
                        tabOrder.forEachIndexed { index, tabType ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = {
                                    logMainTabSelectionAnalytics(tabType)
                                    selectedTab = index
                                    shareText = context.string(R.string.loading)
                                },
                                text = {
                                    Text(
                                        when (tabType) {
                                            TabType.LEADERBOARD -> "Leaderboard"
                                            TabType.HEALTH -> "Health"
                                            TabType.POWER -> "Power"
                                            TabType.DEVICE_INFO -> context.string(R.string.device_info)
                                            TabType.NETWORK_INFO -> context.string(R.string.network_info)
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                },
                            )
                        }
                    }
                }

                @Composable
                fun ScanCategoryNav(
                    modifier: Modifier = Modifier,
                    width: androidx.compose.ui.unit.Dp,
                ) {
                    Surface(
                        modifier = modifier
                            .width(width)
                            .fillMaxHeight(),
                        tonalElevation = 2.dp,
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(ScanCategory.entries.toList()) { category ->
                                val selected = selectedTab == category.tabIndex()
                                NavigationDrawerItem(
                                    selected = selected,
                                    label = { Text(category.displayLabel) },
                                    icon = {
                                        Icon(
                                            imageVector = when (category) {
                                                ScanCategory.BATTERY -> Icons.Filled.BatteryChargingFull
                                                ScanCategory.NETWORK -> Icons.Filled.Wifi
                                                ScanCategory.HARDWARE -> Icons.Filled.Smartphone
                                                ScanCategory.PRIVACY -> Icons.Filled.MonitorHeart
                                                ScanCategory.AI -> Icons.Filled.Psychology
                                            },
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        selectedTab = category.tabIndex()
                                        shareText = context.string(R.string.loading)
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }
                        }
                    }
                }

                @Composable
                fun ExpandedAiPane(modifier: Modifier = Modifier) {
                    val filteredPreview = remember(shareText, scanFilterQuery) {
                        if (scanFilterQuery.isBlank()) {
                            shareText
                        } else {
                            shareText.lines()
                                .filter { it.contains(scanFilterQuery, ignoreCase = true) }
                                .joinToString("\n")
                        }
                    }
                    Surface(
                        modifier = modifier
                            .width(320.dp)
                            .fillMaxHeight(),
                        tonalElevation = 3.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                "AI & actions",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = scanFilterQuery,
                                onValueChange = { scanFilterQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFieldFocusRequester),
                                label = { Text("Search in scan result (Ctrl+F)") },
                                singleLine = true,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = filteredPreview.take(4000).let { t ->
                                    if (t.length == 4000) t + "…" else t
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = {
                                    com.teamz.lab.debugger.utils.AIClickHandler.handleAIClick(
                                        activity = activity,
                                        source = "expanded_ai_pane",
                                        onPaywallRequest = {
                                            paywallAnalyticsSource = "ai_soft_gate"
                                            showRevenueCatPaywall = true
                                        },
                                    ) { showAIDialog = true }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(AIIcon.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Open AI assistant")
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { exportScanSummaryCsv(context, activity, shareText) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Export summary CSV (E)") }
                        }
                    }
                }

                @Composable
                fun MainScanPane(modifier: Modifier = Modifier) {
                    Box(
                        modifier = modifier,
                    ) {
                        Crossfade(targetState = selectedTab, label = "tab") { tab ->
                        val tabType = TabOrderManager.getTabTypeAt(tab)
                        when (tabType) {
                            TabType.LEADERBOARD -> {
                                LeaderboardSection(activity = activity)
                            }
                            
                            TabType.HEALTH -> {
                                HealthSection(
                                    onShareClick = { info -> shareText = info },
                                    onAIClick = {
                                        com.teamz.lab.debugger.utils.AIClickHandler.handleAIClick(
                                            activity = activity,
                                            source = "health_section",
                                            onPaywallRequest = {
                                                paywallAnalyticsSource = "ai_soft_gate"
                                                showRevenueCatPaywall = true
                                            }
                                        ) {
                                            showAIDialog = true
                                        }
                                    },
                                    onItemAIClick = { title, content ->
                                        com.teamz.lab.debugger.utils.AIClickHandler.handleAIClick(
                                            activity = activity,
                                            source = "health_item",
                                            itemTitle = title,
                                            onPaywallRequest = {
                                                paywallAnalyticsSource = "ai_soft_gate"
                                                showRevenueCatPaywall = true
                                            }
                                        ) {
                                            selectedItemForAI = Pair(title, content)
                                            showItemAIDialog = true
                                        }
                                    },
                                    onScanComplete = {
                                        if (!RevenueCatManager.isPremium()) {
                                            val prefs = context.getSharedPreferences("paywall_trigger_prefs", Context.MODE_PRIVATE)
                                            val lastShown = prefs.getLong("last_paywall_shown_time", 0L)
                                            val daysSince = (System.currentTimeMillis() - lastShown) / (24 * 60 * 60 * 1000)
                                            if (lastShown == 0L || daysSince >= 3) {
                                                prefs.edit().putLong("last_paywall_shown_time", System.currentTimeMillis()).apply()
                                                paywallAnalyticsSource = "health_scan_complete"
                                                AnalyticsUtils.logEvent(AnalyticsEvent.PaywallHealthScanTriggered, mapOf(
                                                    "days_since_last" to daysSince
                                                ))
                                                showRevenueCatPaywall = true
                                            } else {
                                                AnalyticsUtils.logEvent(AnalyticsEvent.PaywallHealthScanSkipped, mapOf(
                                                    "reason" to "cooldown_not_expired",
                                                    "days_since_last" to daysSince
                                                ))
                                            }
                                        }
                                    },
                                    onGenerateReportClick = {
                                        if (!RevenueCatManager.isPremium()) {
                                            paywallAnalyticsSource = "health_tab_report_teaser"
                                            AnalyticsUtils.logEvent(AnalyticsEvent.PaywallVerifiedReportGated, mapOf(
                                                "source" to "health_tab"
                                            ))
                                            showRevenueCatPaywall = true
                                        } else {
                                            InterstitialAdManager.showAdBeforeAction(
                                                activity = activity,
                                                actionName = "generate_verified_report"
                                            ) {
                                                showGenerateReportDialog = true
                                            }
                                        }
                                    },
                                    onVerifyReportClick = {
                                        InterstitialAdManager.showAdBeforeAction(
                                            activity = activity,
                                            actionName = "verify_report"
                                        ) {
                                            showVerifyReportDialog = true
                                        }
                                    }
                                )
                            }

                            TabType.POWER -> {
                                PowerConsumptionSection(
                                    onShareClick = { info -> shareText = info },
                                    onAIClick = {
                                        com.teamz.lab.debugger.utils.AIClickHandler.handleAIClick(
                                            activity = activity,
                                            source = "power_section",
                                            onPaywallRequest = {
                                                paywallAnalyticsSource = "ai_soft_gate"
                                                showRevenueCatPaywall = true
                                            }
                                        ) {
                                            showAIDialog = true
                                        }
                                    },
                                    onItemAIClick = { title, content ->
                                        com.teamz.lab.debugger.utils.AIClickHandler.handleAIClick(
                                            activity = activity,
                                            source = "power_item",
                                            itemTitle = title,
                                            onPaywallRequest = {
                                                paywallAnalyticsSource = "ai_soft_gate"
                                                showRevenueCatPaywall = true
                                            }
                                        ) {
                                            selectedItemForAI = Pair(title, content)
                                            showItemAIDialog = true
                                        }
                                    }
                                )
                            }
                            
                            TabType.DEVICE_INFO -> {
                                DeviceInfoSection(
                                    activity = activity,
                                    onShareClick = { info -> shareText = info },
                                    onAIClick = {
                                        com.teamz.lab.debugger.utils.AIClickHandler.handleAIClick(
                                            activity = activity,
                                            source = "device_info_section",
                                            onPaywallRequest = {
                                                paywallAnalyticsSource = "ai_soft_gate"
                                                showRevenueCatPaywall = true
                                            }
                                        ) {
                                            showAIDialog = true
                                        }
                                    },
                                    onItemAIClick = { title, content ->
                                        com.teamz.lab.debugger.utils.AIClickHandler.handleAIClick(
                                            activity = activity,
                                            source = "device_info_item",
                                            itemTitle = title,
                                            onPaywallRequest = {
                                                paywallAnalyticsSource = "ai_soft_gate"
                                                showRevenueCatPaywall = true
                                            }
                                        ) {
                                            selectedItemForAI = Pair(title, content)
                                            showItemAIDialog = true
                                        }
                                    },
                                    onPremiumGateClick = { sectionName ->
                                        paywallAnalyticsSource = "device_info_premium_section"
                                        AnalyticsUtils.logEvent(AnalyticsEvent.PremiumGateClicked, mapOf(
                                            "source" to "device_info_section",
                                            "section" to sectionName
                                        ))
                                        showRevenueCatPaywall = true
                                    }
                                )
                            }

                            TabType.NETWORK_INFO -> {
                                NetworkInfoSection(
                                    activity = activity, 
                                    onShareClick = { info -> shareText = info },
                                    onItemAIClick = { title, content ->
                                        com.teamz.lab.debugger.utils.AIClickHandler.handleAIClick(
                                            activity = activity,
                                            source = "network_info_item",
                                            itemTitle = title,
                                            onPaywallRequest = {
                                                paywallAnalyticsSource = "ai_soft_gate"
                                                showRevenueCatPaywall = true
                                            }
                                        ) {
                                            selectedItemForAI = Pair(title, content)
                                            showItemAIDialog = true
                                        }
                                    }
                                )
                            }
                            
                            null -> null
                        }
                        }
                    }
                }

                when (chromeMode) {
                    DeviceGptChromeMode.CompactPhone -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            TabStrip()
                            MainScanPane(
                                Modifier
                                    .weight(1f, fill = true)
                                    .fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }

                    DeviceGptChromeMode.MediumListDetail -> {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                        ) {
                            ScanCategoryNav(width = 264.dp)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                MainScanPane(
                                    Modifier
                                        .weight(1f, fill = true)
                                        .fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }

                    DeviceGptChromeMode.ExpandedThreePane -> {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .then(expandedKeyboardModifier),
                        ) {
                            ScanCategoryNav(width = 240.dp)
                            MainScanPane(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                            ExpandedAiPane()
                        }
                    }
                }
            }
        }
    }

    val privateAiMain = rememberPrivateAiFlow(subject = "Device scan") { mode ->
        com.teamz.lab.debugger.utils.AIPromptGenerator.generateMainPrompt(
            tabIndex = selectedTab,
            promptMode = mode,
            appName = appName,
        ) + "\n\n" + shareText
    }
    privateAiMain.Render()

    if (showAIDialog) {
        AIAssistantDialog(
            onDismiss = {
                showAIDialog = false
                // Show ad after user dismisses AI dialog
                InterstitialAdManager.showAdIfAvailable(activity) {
                    // Ad shown and dismissed
                }
            },
            onPrivateAiSelected = { mode ->
                showAIDialog = false
                privateAiMain.onSelect(mode)
            },
            onShareWithApp = { app, promptMode ->
                val fileName = TabOrderManager.getShareFileName(selectedTab)
                try {
                    // Generate AI prompt using centralized prompt generator
                    val prompt = com.teamz.lab.debugger.utils.AIPromptGenerator.generateMainPrompt(
                        tabIndex = selectedTab,
                        promptMode = promptMode,
                        appName = appName
                    )



                    android.util.Log.d("DeviceGPT_AI", "Generating AI prompt for ${app.name}, mode: $promptMode, tab: $selectedTab")
                    android.util.Log.d("DeviceGPT_AI", "Prompt length: ${prompt.length} chars, Share text length: ${shareText.length} chars")
                    
                    // Use robust sharing function (sends text directly + optional file)
                    val fileContent = prompt + "\n\n" + "=".repeat(50) + "\n\n" + shareText
                    val result = com.teamz.lab.debugger.utils.shareWithAIAppRobust(
                        context = context,
                        content = fileContent,
                        fileName = fileName,
                        aiAppPackageName = app.packageName,
                        aiAppName = app.name,
                        config = com.teamz.lab.debugger.utils.ShareConfig(
                            maxTextLength = 10000,
                            enableFileAttachment = true,
                            enableClipboardFallback = true,
                            enableChunking = false, // Disabled - use file for large content
                            logDiagnostics = true
                        )
                    )
                    
                    // Log result
                    when (result) {
                        is com.teamz.lab.debugger.utils.ShareResult.Success -> {
                            AnalyticsUtils.logEvent(AnalyticsEvent.ShareWithAI, emptyMap())
                            android.util.Log.d("DeviceGPT_AI", "Successfully shared with ${app.name}")
                            // Track meaningful interaction for review prompt (after positive AI experience)
                            ReviewPromptManager.trackMeaningfulInteraction(activity, "ai_share_success")
                        }
                        is com.teamz.lab.debugger.utils.ShareResult.PartialSuccess -> {
                            AnalyticsUtils.logEvent(AnalyticsEvent.ShareWithAI, emptyMap())
                            android.util.Log.w("DeviceGPT_AI", "Partial success: ${result.message}")
                            // Track meaningful interaction for review prompt (after positive AI experience)
                            ReviewPromptManager.trackMeaningfulInteraction(activity, "ai_share_partial")
                        }
                        is com.teamz.lab.debugger.utils.ShareResult.Failure -> {
                            android.util.Log.e("DeviceGPT_AI", "Share failed: ${result.error}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DeviceGPT_AI", "Error in AI share flow", e)
                    handleError(e)
                }
                showAIDialog = false
            },
            context = context
        )
    }

    val privateAiItem = rememberPrivateAiFlow(
        subject = selectedItemForAI?.first ?: "Item",
    ) { mode ->
        val (itemTitle, itemContent) = selectedItemForAI ?: return@rememberPrivateAiFlow ""
        val prompt = com.teamz.lab.debugger.ui.generateItemPrompt(
            itemTitle, itemContent, appName, mode,
        )
        prompt + "\n\n" + "**$itemTitle**\n\n" + itemContent
    }
    privateAiItem.Render()

    // Per-item AI dialog for device info items
    if (showItemAIDialog
        && selectedItemForAI != null) {
        val (itemTitle, itemContent) = selectedItemForAI!!
        AIAssistantDialog(
            onDismiss = {
                showItemAIDialog = false
                selectedItemForAI = null
                // Show ad after user dismisses item AI dialog
                InterstitialAdManager.showAdIfAvailable(activity) {
                    // Ad shown and dismissed
                }
            },
            onPrivateAiSelected = { mode ->
                showItemAIDialog = false
                privateAiItem.onSelect(mode)
            },
            onShareWithApp = { app, promptMode ->
                // Clean filename - remove special characters that might cause issues
                val cleanTitle = itemTitle.replace(Regex("[^a-zA-Z0-9\\s]"), "").replace(" ", "_").lowercase()
                // Detect category to use appropriate prefix
                val category = com.teamz.lab.debugger.utils.AIPromptGenerator.detectItemCategory(itemTitle)
                val prefix = when (category) {
                    "network" -> "network_info"
                    "battery" -> "battery_info"
                    "storage" -> "storage_info"
                    "security" -> "security_info"
                    "privacy" -> "privacy_info"
                    "privacy_report" -> "privacy_report"
                    "reachability" -> "reachability_report"
                    "zero_trust" -> "zero_trust_report"
                    else -> "device_info"
                }
                val fileName = "${prefix}_$cleanTitle.txt"
                try {
                    // Generate context-specific prompt for this item
                    val prompt = com.teamz.lab.debugger.ui.generateItemPrompt(
                        itemTitle,
                        itemContent,
                        appName,
                        promptMode
                    )
                    
                    android.util.Log.d("DeviceGPT_AI", "Generating item prompt for: $itemTitle, mode: $promptMode")
                    android.util.Log.d("DeviceGPT_AI", "Prompt length: ${prompt.length} chars, Content length: ${itemContent.length} chars")
                    
                    // Write prompt + separator + actual data content so AI can analyze it
                    // This ensures ChatGPT has both the prompt instructions AND the actual data
                    val fullContent = prompt + "\n\n" + "=".repeat(50) + "\n\n" + 
                        "**$itemTitle**\n\n" + itemContent
                    
                    // Use robust sharing function (sends text directly + optional file)
                    val result = com.teamz.lab.debugger.utils.shareWithAIAppRobust(
                        context = context,
                        content = fullContent,
                        fileName = fileName,
                        aiAppPackageName = app.packageName,
                        aiAppName = app.name,
                        config = com.teamz.lab.debugger.utils.ShareConfig(
                            maxTextLength = 10000,
                            enableFileAttachment = true,
                            enableClipboardFallback = true,
                            enableChunking = false, // Disabled - use file for large content
                            logDiagnostics = true
                        )
                    )
                    
                    // Log result and analytics
                    when (result) {
                        is com.teamz.lab.debugger.utils.ShareResult.Success -> {
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.ShareWithAI,
                                mapOf<String, Any?>("item_title" to itemTitle)
                            )
                            android.util.Log.d("DeviceGPT_AI", "Successfully shared item: $itemTitle")
                            // Track meaningful interaction for review prompt (after positive AI experience)
                            ReviewPromptManager.trackMeaningfulInteraction(activity, "ai_share_item_success")
                        }
                        is com.teamz.lab.debugger.utils.ShareResult.PartialSuccess -> {
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.ShareWithAI,
                                mapOf<String, Any?>("item_title" to itemTitle)
                            )
                            android.util.Log.w("DeviceGPT_AI", "Partial success for $itemTitle: ${result.message}")
                            // Track meaningful interaction for review prompt (after positive AI experience)
                            ReviewPromptManager.trackMeaningfulInteraction(activity, "ai_share_item_partial")
                        }
                        is com.teamz.lab.debugger.utils.ShareResult.Failure -> {
                            android.util.Log.e("DeviceGPT_AI", "Share failed for $itemTitle: ${result.error}")
                        }
                    }
                    } catch (e: Exception) {
                    android.util.Log.e("DeviceGPT_AI", "Error in item AI share flow", e)
                    handleError(e)
                }
                showItemAIDialog = false
                selectedItemForAI = null
            },
            context = context,
            title = "AI Insights: $itemTitle",
            subtitle = "Get detailed explanations about this device information.",
            showExplanationModeToggle = true
        )
    }


    // Price and currency input dialog
    if (showPriceInputDialog) {
        AlertDialog(onDismissRequest = {
            showPriceInputDialog = false
        }, title = {
            Text(context.string(R.string.can_device_get_certified), style = MaterialTheme.typography.titleLarge)
        }, text = {
            Column {
                Text(
                    context.string(R.string.certificate_description),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = inputPrice,
                    onValueChange = { inputPrice = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(context.string(R.string.original_price)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = inputCurrency, onValueChange = {
                        inputCurrency = it.uppercase().take(3)
                    }, // e.g., USD, BDT, EUR
                    label = { Text(context.string(R.string.currency_hint)) }, singleLine = true
                )
            }
        }, confirmButton = {
            Button(
                onClick = {
                    showPriceInputDialog = false
                    showAICertificateDialog = true
                    // Pass aiPrompt to AIAssistantDialog or handle as needed
                }) {
                Text(context.string(R.string.continue_text))
            }
        }, dismissButton = {
            TextButton(onClick = {
                showPriceInputDialog = false
            }) {
                Text(context.string(R.string.cancel))
            }
        })
    }

    val privateAiCert = rememberPrivateAiFlow(subject = "Resale Report") { mode ->
        val now =
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
        val prompt = com.teamz.lab.debugger.utils.AIPromptGenerator.generateCertificatePrompt(
            appName = appName,
            inputPrice = inputPrice,
            inputCurrency = inputCurrency,
            scanDate = now,
        )
        prompt + "\n\n" + shareText
    }
    privateAiCert.Render()

    // AI Assistant dialog with viral, user-friendly title and subtitle
    if (showAICertificateDialog) {
        AIAssistantDialog(
            onDismiss = {
                showAIDialog = false
                showAICertificateDialog = false
                // Show ad after user dismisses certificate dialog
                InterstitialAdManager.showAdIfAvailable(activity) {
                    // Ad shown and dismissed
                }
            },
            onPrivateAiSelected = { mode ->
                showAICertificateDialog = false
                privateAiCert.onSelect(mode)
            },
            onShareWithApp = { app, mode ->
                val now =
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date())

                // Generate certificate prompt using centralized prompt generator
                val aiPrompt = com.teamz.lab.debugger.utils.AIPromptGenerator.generateCertificatePrompt(
                    appName = appName,
                    inputPrice = inputPrice,
                    inputCurrency = inputCurrency,
                    scanDate = now
                )

                val fileName = TabOrderManager.getShareFileName(selectedTab)
                try {
                    // Use robust sharing function (sends text directly + optional file)
                    val fileContent = aiPrompt + "\n\n" + shareText
                    val result = com.teamz.lab.debugger.utils.shareWithAIAppRobust(
                        context = context,
                        content = fileContent,
                        fileName = fileName,
                        aiAppPackageName = app.packageName,
                        aiAppName = app.name,
                        config = com.teamz.lab.debugger.utils.ShareConfig(
                            maxTextLength = 10000,
                            enableFileAttachment = true,
                            enableClipboardFallback = true,
                            enableChunking = false, // Disabled - use file for large content
                            logDiagnostics = true
                        )
                    )
                    
                    // Log result
                    when (result) {
                        is com.teamz.lab.debugger.utils.ShareResult.Success -> {
                            AnalyticsUtils.logEvent(AnalyticsEvent.ShareWithAI, emptyMap())
                            android.util.Log.d("DeviceGPT_AI", "Successfully shared certificate with ${app.name}")
                            // Track meaningful interaction for review prompt (after positive AI experience)
                            ReviewPromptManager.trackMeaningfulInteraction(activity, "ai_share_certificate_success")
                        }
                        is com.teamz.lab.debugger.utils.ShareResult.PartialSuccess -> {
                            AnalyticsUtils.logEvent(AnalyticsEvent.ShareWithAI, emptyMap())
                            android.util.Log.w("DeviceGPT_AI", "Partial success: ${result.message}")
                            // Track meaningful interaction for review prompt (after positive AI experience)
                            ReviewPromptManager.trackMeaningfulInteraction(activity, "ai_share_certificate_partial")
                        }
                        is com.teamz.lab.debugger.utils.ShareResult.Failure -> {
                            android.util.Log.e("DeviceGPT_AI", "Share failed: ${result.error}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DeviceGPT_AI", "Error in certificate AI share flow", e)
                    ErrorHandler.handleError(e, context = "MainActivity.shareCertificateWithAI-fallback")
                }
            },
            context = context,
            title = "Generate My Device Certificate",
            subtitle = "Get your official phone certificate and resale value in seconds—perfect for sharing or selling!",
            showExplanationModeToggle = false
        )
    }
    
    // Get power data from aggregator flows (if on power tab)
    val currentPowerData by PowerConsumptionAggregator.currentPowerFlow.collectAsState()
    val aggregatedPowerStats by PowerConsumptionAggregator.aggregatedStatsFlow.collectAsState()
    
    // Viral Share Dialog
    if (showViralShareDialog) {
        ViralShareDialog(
            onDismiss = { 
                showViralShareDialog = false
                // Show ad after user dismisses share dialog
                InterstitialAdManager.showAdIfAvailable(activity) {
                    // Ad shown and dismissed
                }
            },
            context = context,
            shareText = shareText,
            showReferralCode = true,
            powerData = if (selectedTab == TabOrderManager.getTabIndex(TabType.POWER)) currentPowerData else null,
            aggregatedStats = if (selectedTab == TabOrderManager.getTabIndex(TabType.POWER)) aggregatedPowerStats else null
        )
    }

    // v3.2.0 rewarded v1 — exact-grant offer. Copy states the grant verbatim:
    // one report per ad. Grant is delivered even if the ad fails AFTER starting;
    // an early user dismiss (no reward callback) grants nothing.
    if (showRewardedReportOffer) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRewardedReportOffer = false },
            title = { Text("Generate 1 Verified Report") },
            text = { Text("Watch a short ad to generate 1 report — or go Premium for unlimited reports.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showRewardedReportOffer = false
                    com.teamz.lab.debugger.utils.RewardedAdManager.showAd(
                        activity = activity,
                        onRewardEarned = {
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.RewardedUnlockUsed,
                                mapOf("feature" to "verified_report")
                            )
                            showGenerateReportDialog = true
                        },
                        onAdFailed = {
                            // Ad started but failed to render — user held up their
                            // side; deliver the grant anyway.
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.RewardedUnlockUsed,
                                mapOf("feature" to "verified_report", "via" to "ad_failed_grant")
                            )
                            showGenerateReportDialog = true
                        }
                    )
                }) { Text("Watch ad") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showRewardedReportOffer = false
                    paywallAnalyticsSource = "verified_report_gate"
                    showRevenueCatPaywall = true
                }) { Text("Go Premium") }
            }
        )
    }

    // Generate Verified Report dialog
    if (showGenerateReportDialog) {
        GenerateReportDialog(
            onDismiss = { showGenerateReportDialog = false },
            onReportReady = { report ->
                showGenerateReportDialog = false
                generatedReport = report
                showReportReadyDialog = true
            }
        )
    }

    // Report Ready dialog (after generation) — show ad on dismiss for revenue
    if (showReportReadyDialog && generatedReport != null) {
        ReportReadyDialog(
            report = generatedReport!!,
            onDismiss = {
                showReportReadyDialog = false
                generatedReport = null
                InterstitialAdManager.showAdIfAvailable(activity) {
                    // Ad shown and dismissed
                }
            }
        )
    }

    // Verify a Report dialog
    if (showVerifyReportDialog) {
        VerifyReportDialog(
            onDismiss = { showVerifyReportDialog = false },
            initialCode = ""
        )
    }

    // RevenueCat Paywall - shows the "device-gpt" paywall designed in RevenueCat console
    // Full screen composable approach (not dialog)
    // Using reusable composable component
    PaywallWithReferralFallback(
        showPaywall = showRevenueCatPaywall,
        onDismiss = {
            showRevenueCatPaywall = false
            paywallAnalyticsSource = "revenuecat_paywall" // Reset source
        },
        analyticsSource = paywallAnalyticsSource
    )
    
}

private fun exportScanSummaryCsv(context: Context, activity: ComponentActivity?, body: String) {
    val loading = context.getString(R.string.loading)
    if (body.isBlank() || body.contains(loading)) {
        Toast.makeText(context, "Refresh a tab first to export scan text.", Toast.LENGTH_SHORT).show()
        return
    }
    val rows = body.lines().map { listOf(it.replace(",", ";")) }
    val uri = PowerConsumptionUtils.exportExperimentCSV(
        context,
        "scan_summary",
        listOf("line"),
        rows,
    )
    if (uri == null) {
        Toast.makeText(context, "Could not create CSV.", Toast.LENGTH_SHORT).show()
        return
    }
    if (activity != null) {
        InterstitialAdManager.showAdBeforeAction(activity, "csv_export_scan_summary") {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(share, "Export scan CSV"))
        }
    } else {
        Toast.makeText(context, "Cannot share without activity context.", Toast.LENGTH_SHORT).show()
    }
}


@Composable
private fun FabLoading() {
    CircularProgressIndicator(
        modifier = Modifier.size(20.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.tertiary
    )
}

@Composable
fun HandleSystemMonitorAutoStart() {
    val context = LocalContext.current
    val postNotificationPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) android.Manifest.permission.POST_NOTIFICATIONS
        else null
    var showDialog by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startService(context)
            } else {
                Toast.makeText(context, context.string(R.string.notification_permission_denied), Toast.LENGTH_SHORT).show()
            }
            showDialog = false
        }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Only start monitoring if:
                // 1. Permission is granted
                // 2. User has enabled monitoring
                // 3. Service is NOT already running (prevent duplicate starts)
                if ((postNotificationPermission == null || ActivityCompat.checkSelfPermission(
                        context, postNotificationPermission
                    ) == PackageManager.PERMISSION_GRANTED)
                    && context.isUserEnableMonitoringService()
                    && !context.isSystemMonitorRunning() // Prevent duplicate starts
                ) {
                    context.startSystemMonitorService()
                    // Distinguish auto-starts (user opens app, service already enabled in prefs)
                    // from explicit toggle-on events tracked at drawer.kt. Lets us measure
                    // how many sessions are "ritual" (came back, monitor auto-resumed) vs
                    // "active opt-in" (user toggled in this session).
                    try {
                        com.teamz.lab.debugger.utils.AnalyticsUtils.logEvent(
                            com.teamz.lab.debugger.utils.AnalyticsEvent.RealtimeMonitorAutoStarted
                        )
                    } catch (_: Exception) { /* analytics never blocks core flow */ }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        val isAlreadyRunning = context.isSystemMonitorRunning()
        if (!isAlreadyRunning && !permissionRequested) {
            permissionRequested = true
            if (postNotificationPermission != null && ActivityCompat.checkSelfPermission(
                    context, postNotificationPermission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                showDialog = true
            } else {
                startService(context)
            }
        }
    }

    NotificationPermissionDialog(
        showDialog = showDialog && !context.isDoNotAskMeAgain(),
        onDismiss = { showDialog = false },
        onDoNotAskMeAgain = {
            context.setDoNotAskMeAgain(it)
        },
        onRequestPermission = {
            postNotificationPermission?.let {
                launcher.launch(it)
            }
        })
}


private fun startService(context: Context) {
    if (context.isUserFirstTime() || context.isUserEnableMonitoringService()) {
        context.startSystemMonitorService()
        if (context.isUserFirstTime()) {
            context.setUserFirstTime(false)
        }
    }
}
