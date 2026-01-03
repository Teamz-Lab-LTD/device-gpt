package com.teamz.lab.debugger

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.onGloballyPositioned
import com.teamz.lab.debugger.ui.DeviceInfoSection
import com.teamz.lab.debugger.ui.NetworkInfoSection
import com.teamz.lab.debugger.ui.PowerConsumptionSection
import com.teamz.lab.debugger.ui.LeaderboardSection
import com.teamz.lab.debugger.utils.openSettings
import com.teamz.lab.debugger.ui.NativeAdManager
import kotlinx.coroutines.launch
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Android
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManagerFactory
import com.teamz.lab.debugger.services.isDoNotAskMeAgain
import com.teamz.lab.debugger.services.isSystemMonitorRunning
import com.teamz.lab.debugger.services.isUserEnableMonitoringService
import com.teamz.lab.debugger.services.isUserFirstTime
import com.teamz.lab.debugger.services.setAlreadyReviewed
import com.teamz.lab.debugger.services.setDoNotAskMeAgain
import com.teamz.lab.debugger.services.setUserFirstTime
import com.teamz.lab.debugger.services.startSystemMonitorService
import com.teamz.lab.debugger.services.userHasAlreadyReviewed
import com.teamz.lab.debugger.ui.DrawerContent
import com.teamz.lab.debugger.ui.NotificationPermissionDialog
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.AppOpenAdManager
import com.teamz.lab.debugger.utils.handleError
import com.teamz.lab.debugger.utils.ErrorHandler
import com.teamz.lab.debugger.utils.ReferralManager
import com.teamz.lab.debugger.utils.DeviceSleepTracker
import com.teamz.lab.debugger.utils.ReviewPromptManager
import java.io.File
import androidx.core.net.toUri
import com.teamz.lab.debugger.utils.AIIcon
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import com.teamz.lab.debugger.ui.AIAssistantDialog
import com.teamz.lab.debugger.ui.ViralShareDialog
import com.teamz.lab.debugger.utils.InterstitialAdManager
import com.teamz.lab.debugger.ui.HealthSection
import com.teamz.lab.debugger.ui.theme.QuickThemeSwitcher
import com.teamz.lab.debugger.ui.theme.ThemeManager
import com.teamz.lab.debugger.ui.theme.LocalThemeManager
import com.teamz.lab.debugger.ui.theme.ThemeAwareContent
import androidx.compose.runtime.CompositionLocalProvider
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.utils.PowerConsumptionAggregator
import androidx.compose.runtime.collectAsState
import com.teamz.lab.debugger.utils.string
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.LocaleManager
import com.teamz.lab.debugger.utils.RemoteConfigUtils

class MainActivity : ComponentActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        // Apply locale before activity is created
        super.attachBaseContext(LocaleManager.createContextWithLocale(newBase))
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("PowerStateDebug", "MainActivity onCreate - savedInstanceState: ${if (savedInstanceState != null) "EXISTS" else "null"} (hashCode: ${hashCode()})")
        
        // Check if this is a cold start (no saved state) or recreation
        isFirstLaunch = savedInstanceState == null
        android.util.Log.d("MainActivity", "onCreate() - isFirstLaunch: $isFirstLaunch")
        
        try {
            // Ensure locale is set
            LocaleManager.setLocale(this)
            
            // Enable edge-to-edge display (modern API)
            // This must be called before setContent() for proper window insets handling
            WindowCompat.setDecorFitsSystemWindows(window, false)
            enableEdgeToEdge()
            
            // Analytics initialization - critical for business decisions
            try {
                AnalyticsUtils.init(this)
            } catch (e: Exception) {
                // Analytics failure is not fatal - app can continue
                ErrorHandler.handleError(e, context = "MainActivity.onCreate-Analytics")
            }
            
            // Check for referral deep links
            ReferralManager.checkReferral(this, intent)
            
            // Initialize device sleep tracker state (in case app was closed)
            // SystemMonitorService handles periodic tracking when app is running
            // This catches missed events when app opens after being closed
            DeviceSleepTracker.initializeState(this)
            
            checkForAppUpdate(this)
            
            // Initialize theme manager before setContent
            try {
                ThemeManager.initialize(this)
            } catch (e: Exception) {
                // Theme initialization failure is critical - app cannot display properly
                ErrorHandler.handleFatalError(
                    Exception("Failed to initialize ThemeManager: ${e.message}", e),
                    context = "MainActivity.onCreate-ThemeManager"
                )
            }
            
            setContent {
                // Provide theme manager to the composition
                CompositionLocalProvider(LocalThemeManager provides ThemeManager) {
                    ThemeAwareContent {
                        DebuggerApp(this@MainActivity)
                    }
                }
            }
        } catch (e: Exception) {
            // Critical activity initialization failure
            ErrorHandler.handleFatalError(
                Exception("Critical MainActivity initialization failed: ${e.message}", e),
                context = "MainActivity.onCreate"
            )
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Check for referral when app is already running
        ReferralManager.checkReferral(this, intent)
    }

    override fun onResume() {
        super.onResume()
        val appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                Toast.makeText(this, getString(R.string.update_downloaded_restarting), Toast.LENGTH_SHORT).show()
                appUpdateManager.completeUpdate()
            }
        }
    }

    private var wasAdShowing = false
    private var isFirstLaunch = true // Track if this is first launch (cold start)

    override fun onStart() {
        super.onStart()
        android.util.Log.d("MainActivity", "onStart() - MainActivity onStart called, wasAdShowing: $wasAdShowing, isFirstLaunch: $isFirstLaunch")
        
        // Only show app open ad if:
        // 1. Not resuming from an interstitial ad
        // 2. This is a cold start (first launch) OR app was in background for significant time
        // AppOpenAdManager will handle frequency capping and background time checks internally
        if (!wasAdShowing) {
            if (isFirstLaunch) {
                android.util.Log.d("MainActivity", "onStart() - ✅ Cold start detected, showing app open ad")
                AppOpenAdManager.showAdIfAvailable(this, isColdStart = true)
            } else {
                android.util.Log.d("MainActivity", "onStart() - Activity recreated, checking background time and cooldown...")
                // AppOpenAdManager will check background time and cooldown internally
                AppOpenAdManager.showAdIfAvailable(this, isColdStart = false)
            }
        } else {
            android.util.Log.d("MainActivity", "onStart() - ⚠️ Skipping app open ad (resuming from interstitial)")
        }
        wasAdShowing = false
        
        // Track app open and show review prompt if conditions are met
        // This uses Google Play In-App Review API (official, no third-party needed)
        // Shows after 3+ app opens, with 30-day cooldown, only if user hasn't reviewed
        val wasColdStart = isFirstLaunch
        isFirstLaunch = false // Reset after first check
        ReviewPromptManager.trackAppOpenAndMaybeShowReview(this, isColdStart = wasColdStart)
    }

    override fun onPause() {
        super.onPause()
        // Track if we're pausing due to an ad (interstitial ads pause the activity)
        // This helps prevent app open ad from showing when we resume
        wasAdShowing = InterstitialAdManager.isAdLoaded() || InterstitialAdManager.isLoading()
        android.util.Log.d("PowerStateDebug", "MainActivity onPause - wasAdShowing set to: $wasAdShowing (hashCode: ${hashCode()})")
    }

    override fun onDestroy() {
        android.util.Log.d("PowerStateDebug", "MainActivity onDestroy - Activity destroyed (hashCode: ${hashCode()})")
        super.onDestroy()
        NativeAdManager.clear()
    }

    private val UPDATE_REQUEST_CODE = 1234

    private fun checkForAppUpdate(activity: Activity) {
        val appUpdateManager = AppUpdateManagerFactory.create(activity)

        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && appUpdateInfo.isUpdateTypeAllowed(
                    AppUpdateType.FLEXIBLE
                )
            ) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo, AppUpdateType.FLEXIBLE, activity, UPDATE_REQUEST_CODE
                    )
                } catch (e: IntentSender.SendIntentException) {
                    ErrorHandler.handleError(e, context = "MainActivity.startAppUpdate")
                }
            }
        }
    }


}

// loadingText is now accessed via context.string(R.string.loading)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebuggerApp(activity: ComponentActivity) {
    // Set up drawer state and coroutine scope for opening/closing the drawer.
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var selectedTab by remember { mutableIntStateOf(0) } // Current tab index
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
            val isLeaderboardEnabled = remember { RemoteConfigUtils.isLeaderboardEnabled() }
            val leaderboardTabPosition = remember { RemoteConfigUtils.getLeaderboardTabPosition() }
            val totalTabs = if (isLeaderboardEnabled) 5 else 4
            val leaderboardTabIndex = if (leaderboardTabPosition == 0) {
                0 // First tab
            } else {
                totalTabs - 1 // Last tab
            }
            val isLeaderboardTabSelected = selectedTab == leaderboardTabIndex && isLeaderboardEnabled
            
            if (!isLeaderboardTabSelected) {
                val certTooltipState = rememberTooltipState()
                val aiTooltipState = rememberTooltipState()
                
                Row(modifier = Modifier.padding(16.dp)) {
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
                        modifier = Modifier.padding(end = 12.dp),
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
                                source = "fab_ai"
                            ) {
                                showAIDialog = true
                            }
                        },
                        modifier = Modifier.padding(end = 12.dp),
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
                    onClick = {
                        if (!shareText.contains(context.string(R.string.loading)) && shareText.isNotEmpty()) {
                            AnalyticsUtils.logEvent(AnalyticsEvent.FabShareClicked, mapOf(
                                "tab" to selectedTab,
                                "tab_name" to when(selectedTab) {
                                    0 -> "device_info"
                                    1 -> "network_info"
                                    2 -> "health"
                                    3 -> "power"
                                    else -> "unknown"
                                }
                            ))
                            InterstitialAdManager.showAdIfAvailable(activity) {
                                val brandedFooter = """
    
—
🛡️ Scanned and generated by **$appName**

📱 Download now on Google Play:
https://play.google.com/store/apps/details?id=${context.packageName}

💡 Protect, diagnose, and improve your Android with $appName.

""".trimIndent()
                                val fileName = when (selectedTab) {
                                    0 -> "my_device_info.txt"
                                    1 -> "my_network_info.txt"
                                    2 -> "my_health_report.txt"
                                    3 -> "my_power_report.txt"
                                    else -> "my_device_info.txt"
                                }
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Leaderboard configuration
                    val isLeaderboardEnabled = remember { RemoteConfigUtils.isLeaderboardEnabled() }
                    val leaderboardTabPosition = remember { RemoteConfigUtils.getLeaderboardTabPosition() }
                    val totalTabs = if (isLeaderboardEnabled) 5 else 4
                    val leaderboardTabIndex = if (leaderboardTabPosition == 0) {
                        0 // First tab
                    } else {
                        totalTabs - 1 // Last tab
                    }
                    
                    // Tab Row - Scrollable for long text support
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.fillMaxWidth(),
                        edgePadding = 0.dp
                    ) {
                        // Add leaderboard tab at configured position
                        if (isLeaderboardEnabled && leaderboardTabPosition == 0) {
                            Tab(
                                selected = selectedTab == leaderboardTabIndex,
                                onClick = {
                                    AnalyticsUtils.logEvent(AnalyticsEvent.TabLeaderboardViewed)
                                    selectedTab = leaderboardTabIndex
                                    shareText = context.string(R.string.loading)
                                },
                                text = {
                                    Text(
                                        "Leaderboard",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            )
                        }
                        
                        Tab(
                            selected = selectedTab == 0,
                            onClick = {
                                AnalyticsUtils.logEvent(AnalyticsEvent.TabDeviceInfoViewed)
                                selectedTab = 0
                                shareText = context.string(R.string.loading)
                            },
                            text = {
                                Text(
                                    context.string(R.string.device_info),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { 
                                AnalyticsUtils.logEvent(AnalyticsEvent.TabNetworkInfoViewed)
                                selectedTab = 1
                                shareText = context.string(R.string.loading)
                            },
                            text = {
                                Text(
                                    context.string(R.string.network_info),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { 
                                AnalyticsUtils.logEvent(AnalyticsEvent.TabHealthViewed)
                                selectedTab = 2
                                shareText = context.string(R.string.loading)
                            },
                            text = {
                                Text(
                                    "Health",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { 
                                AnalyticsUtils.logEvent(AnalyticsEvent.TabPowerViewed)
                                selectedTab = 3
                                shareText = context.string(R.string.loading)
                            },
                            text = {
                                Text(
                                    "Power",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        )
                        
                        // Add leaderboard tab at end if configured
                        if (isLeaderboardEnabled && leaderboardTabPosition == -1) {
                            Tab(
                                selected = selectedTab == leaderboardTabIndex,
                                onClick = {
                                    AnalyticsUtils.logEvent(AnalyticsEvent.TabLeaderboardViewed)
                                    selectedTab = leaderboardTabIndex
                                    shareText = context.string(R.string.loading)
                                },
                                text = {
                                    Text(
                                        "Leaderboard",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            )
                        }
                    }

                    // Content based on selected tab
                    // Content based on selected tab
                    Crossfade(targetState = selectedTab, label = "tab") { tab ->
                        when (tab) {
                            leaderboardTabIndex -> if (isLeaderboardEnabled) {
                                LeaderboardSection(activity = activity)
                            } else null
                            
                            0 -> DeviceInfoSection(
                                activity = activity,
                                onShareClick = { info -> shareText = info },
                                onAIClick = {
                                    com.teamz.lab.debugger.utils.AIClickHandler.handleAIClick(
                                        activity = activity,
                                        source = "device_info_section"
                                    ) {
                                        showAIDialog = true
                                    }
                                },
                                onItemAIClick = { title, content ->
                                    com.teamz.lab.debugger.utils.AIClickHandler.handleAIClick(
                                        activity = activity,
                                        source = "device_info_item",
                                        itemTitle = title
                                    ) {
                                        selectedItemForAI = Pair(title, content)
                                        showItemAIDialog = true
                                    }
                                }
                            )

                            1 -> NetworkInfoSection(
                                activity = activity, 
                                onShareClick = { info -> shareText = info },
                                onItemAIClick = { title, content ->
                                    com.teamz.lab.debugger.utils.AIClickHandler.handleAIClick(
                                        activity = activity,
                                        source = "network_info_item",
                                        itemTitle = title
                                    ) {
                                        selectedItemForAI = Pair(title, content)
                                        showItemAIDialog = true
                                    }
                                }
                            )

                            2 -> HealthSection(
                                onShareClick = { info -> shareText = info },
                                onAIClick = {
                                    com.teamz.lab.debugger.utils.AIClickHandler.handleAIClick(
                                        activity = activity,
                                        source = "health_section"
                                    ) {
                                        showAIDialog = true
                                    }
                                },
                                onItemAIClick = { title, content ->
                                    com.teamz.lab.debugger.utils.AIClickHandler.handleAIClick(
                                        activity = activity,
                                        source = "health_item",
                                        itemTitle = title
                                    ) {
                                        selectedItemForAI = Pair(title, content)
                                        showItemAIDialog = true
                                    }
                                }
                            )
                            
                            3 -> PowerConsumptionSection(
                                onShareClick = { info -> shareText = info },
                                onAIClick = {
                                    com.teamz.lab.debugger.utils.AIClickHandler.handleAIClick(
                                        activity = activity,
                                        source = "power_section"
                                    ) {
                                        showAIDialog = true
                                    }
                                },
                                onItemAIClick = { title, content ->
                                    com.teamz.lab.debugger.utils.AIClickHandler.handleAIClick(
                                        activity = activity,
                                        source = "power_item",
                                        itemTitle = title
                                    ) {
                                        selectedItemForAI = Pair(title, content)
                                        showItemAIDialog = true
                                    }
                                }
                            )
                            
                            else -> null
                        }
                    }

                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showAIDialog) {
        AIAssistantDialog(
            onDismiss = { 
                showAIDialog = false
                // Show ad after user dismisses AI dialog
                InterstitialAdManager.showAdIfAvailable(activity) {
                    // Ad shown and dismissed
                }
            },
            onShareWithApp = { app, promptMode ->
                val fileName = when (selectedTab) {
                    0 -> "my_device_info.txt"
                    1 -> "my_network_info.txt"
                    2 -> "my_health_report.txt"
                    3 -> "my_power_report.txt"
                    else -> "my_device_info.txt"
                }
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
                        }
                        is com.teamz.lab.debugger.utils.ShareResult.PartialSuccess -> {
                            AnalyticsUtils.logEvent(AnalyticsEvent.ShareWithAI, emptyMap())
                            android.util.Log.w("DeviceGPT_AI", "Partial success: ${result.message}")
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
                        }
                        is com.teamz.lab.debugger.utils.ShareResult.PartialSuccess -> {
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.ShareWithAI,
                                mapOf<String, Any?>("item_title" to itemTitle)
                            )
                            android.util.Log.w("DeviceGPT_AI", "Partial success for $itemTitle: ${result.message}")
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

                val fileName = when (selectedTab) {
                    0 -> "my_device_info.txt"
                    1 -> "my_network_info.txt"
                    2 -> "my_health_report.txt"
                    3 -> "my_power_report.txt"
                    else -> "my_device_info.txt"
                }
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
                        }
                        is com.teamz.lab.debugger.utils.ShareResult.PartialSuccess -> {
                            AnalyticsUtils.logEvent(AnalyticsEvent.ShareWithAI, emptyMap())
                            android.util.Log.w("DeviceGPT_AI", "Partial success: ${result.message}")
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
            powerData = if (selectedTab == 3) currentPowerData else null,
            aggregatedStats = if (selectedTab == 3) aggregatedPowerStats else null
        )
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


fun Activity.showInAppReview() {
    if (userHasAlreadyReviewed()) {
        val intent = Intent(
            Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri()
        )
        startActivity(intent)
    } else {
        val manager = ReviewManagerFactory.create(this)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                manager.launchReviewFlow(this, reviewInfo).addOnCompleteListener {
                    // User completed review flow - save to local and Firebase
                    // This syncs across devices so user won't see prompt on other devices
                    // Note: We save even if user dismissed, because Google Play API handles frequency limits
                    try {
                        setAlreadyReviewed(true)
                        
                        // Save to Firebase for cross-device sync (non-blocking)
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                com.teamz.lab.debugger.utils.ReviewPromptManager.saveReviewStatusToFirebase(
                                    this@showInAppReview,
                                    true
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Error saving review to Firebase", e)
                                // Don't throw - Firebase sync failure shouldn't block
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error setting review status", e)
                        // Don't throw - review status failure shouldn't crash app
                    }
                }
            } else {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$packageName".toUri()
                )
                startActivity(intent)
            }
        }
    }
}






