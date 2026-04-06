package com.teamz.lab.debugger.ui
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.services.isDoNotAskMeAgain
import com.teamz.lab.debugger.services.isSystemMonitorRunning
import com.teamz.lab.debugger.services.isUserEnableMonitoringService
import com.teamz.lab.debugger.services.setDoNotAskMeAgain
import com.teamz.lab.debugger.services.setUserEnableMonitoringService
import com.teamz.lab.debugger.services.startSystemMonitorService
import com.teamz.lab.debugger.services.stopSystemMonitorService
import com.teamz.lab.debugger.showInAppReview
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.utils.AdConfig
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.InterstitialAdManager
import com.teamz.lab.debugger.utils.LeaderboardManager
import com.teamz.lab.debugger.utils.LocaleManager
import com.teamz.lab.debugger.utils.PasskeyAuthManager
import com.teamz.lab.debugger.utils.PermissionManager
import com.teamz.lab.debugger.utils.RetentionNotificationManager
import com.teamz.lab.debugger.utils.RevenueCatManager
import com.teamz.lab.debugger.utils.string
import com.teamz.lab.debugger.ui.RevenueCatPaywall
import com.revenuecat.purchases.ui.revenuecatui.Paywall
import com.revenuecat.purchases.ui.revenuecatui.PaywallOptions
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.teamz.lab.debugger.widgets.LockScreenMonitorWidget

@Composable
fun DrawerContent(
    activity: Activity,
    drawerState: DrawerState,
    onPermissionChanged: ((permission: String, granted: Boolean) -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
    onGenerateVerifiedReport: (() -> Unit)? = null,
    onVerifyReport: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION to "📍 Location Access",
        Manifest.permission.READ_PHONE_STATE to "📱 Phone Info Access",
        Manifest.permission.PACKAGE_USAGE_STATS to "📊 Network Usage Stats"
    )

    var grantedPermission by remember { mutableStateOf<String?>(null) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var redirectToSettings by remember { mutableStateOf(false) }
    var showUsageStatsDialog by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val isMonitoringRunning = remember {
        mutableStateOf(context.isUserEnableMonitoringService())
    }
    val coroutineScope = rememberCoroutineScope()
    var drawerOpenTrigger by remember { mutableIntStateOf(0) }

    // ✅ Check & store POST_NOTIFICATIONS safely
    val postNotificationPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS
        else null
    if (postNotificationPermission != null) {
        permissions.plus(postNotificationPermission to "🔔 Notification Access")
    }

    val permissionStates = remember(drawerOpenTrigger) {
        mutableStateMapOf<String, Boolean>().apply {
            permissions.forEach { (perm, _) ->
                if (perm == Manifest.permission.PACKAGE_USAGE_STATS) {
                    this[perm] = PermissionManager.hasUsageStatsPermission(context)
                } else {
                    this[perm] = PermissionManager.hasPermission(context, perm)
                }
            }
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            drawerOpenTrigger++
            AnalyticsUtils.logEvent(
                AnalyticsEvent.DrawerOpened, mapOf(
                    "timestamp" to System.currentTimeMillis()
                )
            )
        }
    }

    fun startStatService() {
        if (postNotificationPermission != null && !PermissionManager.hasNotificationPermission(
                context
            )
        ) {
            if (drawerState.isOpen && !showNotificationDialog && !context.isUserEnableMonitoringService() &&
                !context.isSystemMonitorRunning()
            ) showNotificationDialog =
                true
        } else {
            if (context.isUserEnableMonitoringService()) {
                context.startSystemMonitorService()
                isMonitoringRunning.value = true
                Toast.makeText(
                    context,
                    context.string(R.string.toast_monitoring_started),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            isMonitoringRunning.value = context.isSystemMonitorRunning()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                startStatService()
                // ✅ Re-check usage stats permission
                val currentGranted = PermissionManager.hasUsageStatsPermission(context)
                permissionStates[Manifest.permission.PACKAGE_USAGE_STATS] = currentGranted
                onPermissionChanged?.invoke(Manifest.permission.PACKAGE_USAGE_STATS, currentGranted)
                coroutineScope.launch {
                    drawerState.close()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        grantedPermission?.let { perm ->
            permissionStates[perm] = granted
            AnalyticsUtils.logEvent(
                if (granted) AnalyticsEvent.PermissionGranted else AnalyticsEvent.PermissionDenied,
                mapOf("permission" to perm)
            )
            AnalyticsUtils.logEvent(
                AnalyticsEvent.DrawerPermissionToggled, mapOf(
                    "permission" to perm,
                    "granted" to granted
                )
            )
            grantedPermission = null
            onPermissionChanged?.invoke(perm, granted)
            if (!granted) {
                Toast.makeText(
                    context,
                    context.string(R.string.toast_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun handlePermissionRequest(permission: String) {
        // Log permission request analytics
        when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION -> {
                AnalyticsUtils.logEvent(AnalyticsEvent.PermissionLocationRequested)
            }

            Manifest.permission.READ_PHONE_STATE -> {
                AnalyticsUtils.logEvent(AnalyticsEvent.PermissionPhoneStateRequested)
            }

            Manifest.permission.PACKAGE_USAGE_STATS -> {
                AnalyticsUtils.logEvent(AnalyticsEvent.PermissionUsageStatsRequested)
                showUsageStatsDialog = true
                AnalyticsUtils.logEvent(AnalyticsEvent.DrawerUsageStatsDialogShown)
                return
            }

            Manifest.permission.POST_NOTIFICATIONS -> {
                AnalyticsUtils.logEvent(AnalyticsEvent.PermissionNotificationRequested)
            }
        }

        when (permission) {
            Manifest.permission.PACKAGE_USAGE_STATS -> {
                // Already handled above
            }

            else -> {
                if (PermissionManager.shouldShowRationale(activity, permission)) {
                    grantedPermission = permission
                    launcher.launch(permission)
                } else {
                    redirectToSettings = true
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.DrawerSettingsOpened, mapOf(
                            "reason" to "permission_required",
                            "permission" to permission
                        )
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 80.dp) // Extra bottom padding for system nav
    ) {
        // Premium / Remove Ads - Compact, organized design
        val premiumStatus by RevenueCatManager.premiumStatusFlow.collectAsState()
        val isPremium = RevenueCatManager.isPremium()
        var productPrice by remember { mutableStateOf<String?>(null) }
        
        // Fetch price dynamically from RevenueCat
        LaunchedEffect(Unit) {
            if (!isPremium) {
                RevenueCatManager.getLifetimeProductPrice(
                    onSuccess = { price ->
                        productPrice = price
                    },
                    onError = { error ->
                        // Fallback to default if price fetch fails
                        productPrice = "$2.99"
                        android.util.Log.w("Drawer", "Failed to fetch price: $error, using fallback")
                    }
                )
            }
        }
        
        if (!isPremium) {
            // Enhanced animated premium card with multiple effects
            val infiniteTransition = rememberInfiniteTransition(label = "premium_widget_animation")
            
            // Enhanced glow animation - more pronounced
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "widget_glow"
            )
            
            // Enhanced scale pulse animation - more noticeable
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.04f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "widget_pulse"
            )
            
            // Star icon rotation - continuous with pause
            val starRotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 3000
                        0f at 0
                        360f at 1500 // Rotate in 1.5 seconds
                        360f at 3000 // Stay at 360 for 1.5 seconds (pause)
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "star_rotation"
            )
            
            // Additional shimmer/glow effect for the entire card

            // Enhanced animated card with multiple effects
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .scale(pulseScale) // Apply scale animation
                    .clickable {
                        // Show RevenueCat paywall designed in console
                        AnalyticsUtils.logEvent(AnalyticsEvent.PremiumDrawerCardClicked, mapOf(
                            "source" to "drawer",
                            "price" to (productPrice ?: "2.99"),
                            "type" to "lifetime"
                        ))
                        coroutineScope.launch {
                            drawerState.close()
                        }
                        // Show RevenueCat paywall - will be handled by PaywallDialog in drawer
                        showPremiumDialog = true
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = BorderStroke(
                    width = (2.dp * glowAlpha).coerceAtLeast(1.dp),
                    color = DesignSystemColors.NeonGreen.copy(alpha = glowAlpha)
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = (6.dp * glowAlpha).coerceAtLeast(2.dp)
                )
            ) {
                // Main content with enhanced animations
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header row: Icon + Title + Price
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(starRotation) // Rotate star with pause
                                    .scale(1f + (glowAlpha - 0.5f) * 0.2f) // Subtle scale with glow
                            )
                            Column {
                                Text(
                                    text = "DeviceGPT Premium",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (productPrice != null) {
                                        "$productPrice • Lifetime Access"
                                    } else {
                                        "Lifetime Access"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    
                    // Benefits row - DeviceGPT focused
                    Text(
                        text = "✓ No ads • Faster DeviceGPT • Support development",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                    
                    // CTA Button - Full width, compact with animation
                    Button(
                        onClick = {
                            // Paywall is already triggered by card click, but keep for button click too
                            AnalyticsUtils.logEvent(AnalyticsEvent.DrawerItemClicked, mapOf("item" to "remove_ads_widget_button"))
                            coroutineScope.launch {
                                drawerState.close()
                            }
                            RevenueCatManager.showPaywall(
                                activity = activity,
                                onSuccess = {
                                    Toast.makeText(context, "Premium activated! Ads removed.", Toast.LENGTH_SHORT).show()
                                },
                                onError = { error ->
                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                },
                                onDismiss = {}
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Get DeviceGPT Premium",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            // Show premium status badge if user has premium - clickable to show premium info
            var showPremiumInfoDialog by remember { mutableStateOf(false) }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable {
                        AnalyticsUtils.logEvent(AnalyticsEvent.PremiumBadgeClicked, mapOf(
                            "source" to "drawer"
                        ))
                        showPremiumInfoDialog = true
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DesignSystemColors.NeonGreen.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = BorderStroke(
                    width = 1.5.dp,
                    color = DesignSystemColors.NeonGreen.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = DesignSystemColors.NeonGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Premium Active",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            // Premium Info Dialog - shows when user clicks premium badge
            if (showPremiumInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showPremiumInfoDialog = false },
                    title = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✅",
                                fontSize = 48.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "DeviceGPT Premium",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "You're enjoying an ad-free experience!",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "✓ All ads removed\n✓ Lifetime access\n✓ Full app features\n✓ Support development",
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 24.sp
                            )
                            Text(
                                text = "Thank you for supporting DeviceGPT!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showPremiumInfoDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DesignSystemColors.NeonGreen,
                                contentColor = DesignSystemColors.Dark
                            )
                        ) {
                            Text("Got it")
                        }
                    }
                )
            }
        }
        
        // Add space before promotional section
        Spacer(modifier = Modifier.height(8.dp))
        
        // Third-party service promotion - clearly labeled as Ad
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            AdBadge()
            Spacer(modifier = Modifier.height(4.dp))
            AnimatedPromotionalButton(
                containerColor = DesignSystemColors.NeonGreen,
                contentColor = DesignSystemColors.Dark,
                colorText = DesignSystemColors.Dark,
                label = "Want to launch your own app or web?"
            ) {
                AnalyticsUtils.logEvent(AnalyticsEvent.DrawerUpworkClicked)
                val urlIntent = Intent(
                    Intent.ACTION_VIEW,
                    "https://www.upwork.com/agencies/1904602719490921565/".toUri()
                )
                context.startActivity(urlIntent)
            }
        }
        // 🔄 Real-time toggle - Card with proper padding for full text visibility
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp) // Increased padding for better text visibility
            ) {
                RealtimeMonitorToggle(isRunning = isMonitoringRunning.value, onToggle = { enabled ->
                    context.setUserEnableMonitoringService(enabled)
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.RealtimeMonitorToggled, mapOf("enabled" to enabled)
                    )
                    if (enabled) {
                        startStatService()
                    } else {
                        context.stopSystemMonitorService()
                        isMonitoringRunning.value = false
                        Toast.makeText(
                            context,
                            context.string(R.string.toast_monitoring_stopped),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = MaterialTheme.colorScheme.outline
        )

        Text(
            text = "App Permissions",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )

        permissions.forEach { (permission, label) ->
            PermissionToggleRow(
                label = label,
                permission = permission,
                isGranted = permissionStates[permission] == true,
                onRequest = {
                    handlePermissionRequest(permission)
                }
            )
        }

        val anyDenied = permissionStates.any { (perm, granted) ->
            isDangerousPermission(perm) && !granted
        }

        if (anyDenied) {
            Spacer(modifier = Modifier.height(6.dp))
            IconTextButton(
                icon = Icons.Default.Warning,
                label = "Grant Permissions"
            ) {
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.DrawerSettingsOpened, mapOf(
                        "reason" to "grant_permissions"
                    )
                )
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                })
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = MaterialTheme.colorScheme.outline
        )

        // Notification Toggle
        Text(
            text = "Notifications",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )

        var notificationsEnabled by remember {
            mutableStateOf(RetentionNotificationManager.areNotificationsEnabled(context))
        }

        LaunchedEffect(drawerState.isOpen) {
            if (drawerState.isOpen) {
                notificationsEnabled = RetentionNotificationManager.areNotificationsEnabled(context)
            }
        }

        // Notification toggle - Compact card with reduced padding
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier.padding(10.dp)
            ) {
                NotificationToggle(
                    isEnabled = notificationsEnabled,
                    onToggle = { enabled ->
                        notificationsEnabled = enabled
                        RetentionNotificationManager.setNotificationsEnabled(context, enabled)
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.DrawerNotificationToggled,
                            mapOf("enabled" to enabled)
                        )
                        if (enabled) {
                            Toast.makeText(
                                context,
                                "Notifications enabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Notifications disabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }

        // Widget Setup Section
        Text(
            text = "Widget",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
        
        var showWidgetInstructions by remember { mutableStateOf(false) }
        var widgetInstructionType by remember { mutableStateOf("") } // "home" or "lock"
        
        // Add to Home Screen Button (Programmatic - Android 8.0+)
        IconTextButton(
            icon = Icons.Default.Star,
            label = "Add to Home Screen"
        ) {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.WidgetAddToHomeScreenClicked, mapOf(
                    "android_version" to Build.VERSION.SDK_INT
                )
            )
            // Try to request widget pinning (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, LockScreenMonitorWidget::class.java)
                
                try {
                    val success = appWidgetManager.requestPinAppWidget(componentName, null, null)
                    if (success) {
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.WidgetAddToHomeScreenSuccess, mapOf(
                                "method" to "programmatic"
                            )
                        )
                    } else {
                        // If pinning not supported, show instructions
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.WidgetAddToHomeScreenFailed, mapOf(
                                "reason" to "pinning_not_supported"
                            )
                        )
                        widgetInstructionType = "home"
                        showWidgetInstructions = true
                    }
                } catch (e: Exception) {
                    // Fallback to instructions dialog
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.WidgetAddToHomeScreenFailed, mapOf(
                            "reason" to "exception",
                            "error" to (e.message ?: "unknown")
                        )
                    )
                    widgetInstructionType = "home"
                    showWidgetInstructions = true
                }
            } else {
                // For older Android, show instructions
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.WidgetAddToHomeScreenFailed, mapOf(
                        "reason" to "android_version_too_old"
                    )
                )
                widgetInstructionType = "home"
                showWidgetInstructions = true
            }
        }
        
        // Add to Lock Screen Button (Manual instructions only - Android 14+)
        if (Build.VERSION.SDK_INT >= 34) { // Android 14+ (API 34+)
            IconTextButton(
                icon = Icons.Default.Verified,
                label = "Add to Lock Screen"
            ) {
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.WidgetAddToLockScreenClicked, mapOf(
                        "android_version" to Build.VERSION.SDK_INT
                    )
                )
                // Lock screen widgets cannot be added programmatically
                // Show instructions dialog
                widgetInstructionType = "lock"
                showWidgetInstructions = true
            }
        }
        
        // Widget Instructions Dialog
        if (showWidgetInstructions) {
            // Track when instructions dialog is shown
            LaunchedEffect(showWidgetInstructions) {
                if (showWidgetInstructions) {
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.WidgetInstructionsShown, mapOf(
                            "type" to widgetInstructionType,
                            "android_version" to Build.VERSION.SDK_INT
                        )
                    )
                }
            }
            
            AlertDialog(
                onDismissRequest = { showWidgetInstructions = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                },
                title = {
                    Text(
                        text = "Add Device Monitor Widget",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (widgetInstructionType == "home") {
                            Text(
                                text = "Add Widget to Home Screen",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "📱 Manual Steps:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "1. Long-press on home screen\n2. Tap 'Widgets'\n3. Find 'DeviceGPT'\n4. Drag to home screen",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "📊 What the Widget Shows:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "• Health Score & Daily Streak (most prominent)\n• Battery % with charging status (AC/USB/Wireless)\n• Temperature (°C)\n• Power consumption (Watts)\n• RAM usage (%)\n• Network speed (Mbps)\n• Update time indicator",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "🔄 Data Updates:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "• Updates every 15 seconds automatically\n• Requires 'Real-time Monitor' to be enabled\n• Shows 'Live' when data is fresh (< 5s)\n• All data comes from real system APIs (not estimates)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "💡 Tip: Tap the widget to open Health section directly!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        } else if (widgetInstructionType == "lock") {
                            Text(
                                text = "Add Widget to Lock Screen",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "🔒 Lock Screen Widgets (Android 14+):",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Lock screen widgets must be added manually:\n\n1. Lock your phone\n2. Long-press on lock screen\n3. Tap 'Customize' or 'Edit'\n4. Tap 'Add Widget' or 'Widgets'\n5. Find 'DeviceGPT'\n6. Add to lock screen\n\n⚠️ Note: Lock screen widgets may not be available on all devices or Android versions.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            // Fallback - show both
                            Text(
                                text = "See your health score & streak on your home screen or lock screen!",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "📱 For Home Screen:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "1. Long-press on home screen\n2. Tap 'Widgets'\n3. Find 'DeviceGPT'\n4. Drag to home screen",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (Build.VERSION.SDK_INT >= 34) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "🔒 For Lock Screen (Android 14+):",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "1. Long-press on lock screen\n2. Tap 'Customize' or 'Edit'\n3. Tap 'Add Widget' or 'Widgets'\n4. Find 'DeviceGPT'\n5. Add to lock screen\n\nNote: Lock screen widgets may not be available on all devices.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Text(
                            text = "💡 Tip: Widget shows health score, streak, battery, temperature & more!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showWidgetInstructions = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DesignSystemColors.NeonGreen,
                            contentColor = DesignSystemColors.Dark
                        )
                    ) {
                        Text("Got it")
                    }
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.outline
        )

        // Language Selection - COMMENTED OUT (Default to English)
        /*
        Text(
            text = context.string(R.string.language),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp)
        )
        
        LanguageSelector(
            currentLanguage = LocaleManager.getSelectedLanguage(context),
            onLanguageSelected = { language ->
                LocaleManager.setLanguage(context, language)
                // Restart activity to apply language change
                activity.recreate()
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.outline
        )
        */

        // Premium section removed - consolidated into top widget for better UX
        
        // Leaderboard Account Status - Better organized
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            Text(
                text = "Leaderboard",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            // Leaderboard account status - no border, compact, well-organized
            LeaderboardAccountStatus()
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = MaterialTheme.colorScheme.outline
        )

        // Link to our other apps - clearly labeled as Ad
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            AdBadge()
            Spacer(modifier = Modifier.height(4.dp))
            IconTextButton(
                icon = Icons.Default.AutoAwesome,
                label = "More Apps by Teamz Lab",
            ) {
                AnalyticsUtils.logEvent(AnalyticsEvent.DrawerMoreAppsClicked)
                val urlIntent = Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/dev?id=7194763656319643086".toUri()
                )
                context.startActivity(urlIntent)
            }
        }

        IconTextButton(
            icon = Icons.Filled.RateReview,
            label = "Help us grow — leave feedback",
        ) {
            AnalyticsUtils.logEvent(AnalyticsEvent.DrawerReviewClicked)
            activity.showInAppReview()
        }

        IconTextButton(
            icon = Icons.Default.Verified,
            label = "Generate Verified Report"
        ) {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.DrawerItemClicked,
                mapOf("item" to "generate_verified_report")
            )
            coroutineScope.launch { drawerState.close() }
            onGenerateVerifiedReport?.invoke()
        }

        IconTextButton(
            icon = Icons.Default.Info,
            label = "Verify a Report"
        ) {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.DrawerItemClicked,
                mapOf("item" to "verify_report")
            )
            coroutineScope.launch { drawerState.close() }
            onVerifyReport?.invoke()
        }

        IconTextButton(
            icon = Icons.Default.Share,
            label = "Share with Friends"
        ) {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.DrawerItemClicked, mapOf(
                    "item" to "share_with_friends"
                )
            )
            coroutineScope.launch {
                drawerState.close()
            }
            // Show ad before opening share dialog
            InterstitialAdManager.showAdBeforeAction(
                activity = activity,
                actionName = "share_with_friends"
            ) {
                onShareClick?.invoke()
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = MaterialTheme.colorScheme.outline
        )

        // Open Source / GitHub Link - Show ad before visiting
        Column {
            // Small "Open Source" label
            Text(
                text = "Open Source",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                fontSize = 10.sp
            )
            IconTextButton(
                icon = Icons.Default.Info,
                label = "View Source Code on GitHub"
            ) {
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.DrawerItemClicked, mapOf(
                        "item" to "view_source_code"
                    )
                )
                coroutineScope.launch {
                    drawerState.close()
                }
                // Show interstitial ad before opening GitHub
                InterstitialAdManager.showAdBeforeAction(
                    activity = activity,
                    actionName = "view_github_source"
                ) {
                    // Open GitHub repository after ad is dismissed
                    val urlIntent = Intent(
                        Intent.ACTION_VIEW,
                        "https://github.com/Teamz-Lab-LTD/device-gpt".toUri()
                    )
                    context.startActivity(urlIntent)
                }
            }
        }
    }

    // RevenueCat Paywall - shows the "device-gpt" paywall designed in RevenueCat console
    // Full screen composable approach (not dialog)
    // Using reusable composable component
    RevenueCatPaywall(
        showPaywall = showPremiumDialog,
        onDismiss = { showPremiumDialog = false },
        analyticsSource = "revenuecat_paywall_drawer"
    )
    
    NotificationPermissionDialog(
        showDialog = showNotificationDialog && !context.isDoNotAskMeAgain(),
        onDismiss = { showNotificationDialog = false },
        onDoNotAskMeAgain = {
            context.setDoNotAskMeAgain(it)
        },
        onRequestPermission = {
            AnalyticsUtils.logEvent(AnalyticsEvent.DrawerNotificationDialogShown)
            grantedPermission = postNotificationPermission
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, postNotificationPermission!!
                )
            ) {
                launcher.launch(postNotificationPermission)
            } else {
                redirectToSettings = true
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.DrawerSettingsOpened, mapOf(
                        "reason" to "notification_permission"
                    )
                )
            }
        })

    if (showUsageStatsDialog) {
        AlertDialog(
            onDismissRequest = { showUsageStatsDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.primary,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = {
                Text(
                    "Network Usage Access",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text(
                    "To show your network usage statistics, we need access to usage data. " +
                            "This helps us display accurate network usage information in the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        AnalyticsUtils.logEvent(AnalyticsEvent.SettingsUsageAccessOpened)
                        showUsageStatsDialog = false
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                ) {
                    Text(
                        context.string(R.string.open_settings),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsageStatsDialog = false }) {
                    Text(
                        context.string(R.string.cancel),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
    }

    if (redirectToSettings) {
        LaunchedEffect(Unit) {
            redirectToSettings = false
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
            })
            Toast.makeText(
                context, "Please enable notification permission manually", Toast.LENGTH_LONG
            ).show()
        }
    }
}

@Composable
fun RealtimeMonitorToggle(
    isRunning: Boolean, onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isRunning) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp) // More space for text before switch
        ) {
            // Title - no badge needed, switch position shows state
            Text(
                text = "Realtime Monitor",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            // Description - full text visible with proper wrapping
            Text(
                text = if (isRunning) 
                    "Live stats active: battery, speed, fps, network" 
                else 
                    "Tap to enable live monitoring of battery, speed, fps, network",
                style = MaterialTheme.typography.labelSmall,
                color = if (isRunning) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                maxLines = 3, // Allow up to 3 lines for long text
                lineHeight = 15.sp, // Better line spacing
                modifier = Modifier.fillMaxWidth()
            )
        }
        Switch(
            checked = isRunning,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DesignSystemColors.DarkII,
                uncheckedThumbColor = DesignSystemColors.DarkII,
                checkedTrackColor = DesignSystemColors.Border,
                uncheckedTrackColor = DesignSystemColors.White
            )
        )
    }
}

@Composable
fun PermissionToggleRow(
    label: String, isGranted: Boolean, onRequest: () -> Unit, permission: String
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp
        )

        Switch(
            checked = isGranted,
            onCheckedChange = { newValue ->
                if (newValue) {
                    onRequest()
                } else if (permission == Manifest.permission.PACKAGE_USAGE_STATS) {
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.SettingsUsageAccessOpened, mapOf(
                            "action" to "disable_permission"
                        )
                    )
                    Toast.makeText(
                        context,
                        "To disable usage stats, go to Usage Access settings.",
                        Toast.LENGTH_SHORT
                    ).show()
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } else {
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.SettingsAppDetailsOpened, mapOf(
                            "action" to "disable_permission",
                            "permission" to permission
                        )
                    )
                    Toast.makeText(
                        context,
                        "To disable this permission, go to App Settings.",
                        Toast.LENGTH_SHORT
                    ).show()
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                    })
                }
            },
            modifier = Modifier.scale(0.8f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = DesignSystemColors.DarkII,
                uncheckedThumbColor = DesignSystemColors.DarkII,
                checkedTrackColor = DesignSystemColors.Border,
                uncheckedTrackColor = DesignSystemColors.White
            )
        )
    }
}

fun isDangerousPermission(permission: String): Boolean {
    return permission == Manifest.permission.ACCESS_FINE_LOCATION || permission == Manifest.permission.READ_PHONE_STATE || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && permission == Manifest.permission.POST_NOTIFICATIONS)
}

@Composable
fun IconTextButton(
    icon: ImageVector,
    colorText: Color = MaterialTheme.colorScheme.onPrimary,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    label: String, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(16.dp),
                tint = colorText
            )
            Text(
                label,
                color = colorText,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 12.sp
            )

        }
    }
}

@Composable
fun AnimatedPromotionalButton(
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    colorText: Color = MaterialTheme.colorScheme.onPrimary,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Create pulsing animation for the bulb icon
    val infiniteTransition = rememberInfiniteTransition(label = "promotional_bulb_animation")
    
    // Pulsing scale animation - makes the bulb "glow" and pulse
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bulb_pulse"
    )
    
    // Glow alpha animation - makes the bulb appear to glow
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bulb_glow"
    )
    
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = Icons.Default.TipsAndUpdates,
                contentDescription = label,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(16.dp)
                    .scale(pulseScale)
                    .alpha(glowAlpha),
                tint = colorText
            )
            Text(
                label,
                color = colorText,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun NotificationPermissionDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onDoNotAskMeAgain: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
) {
    if (!showDialog) return
    var isCheck by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.primary,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Text(
                text = "Allow Realtime Monitor",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Text(
                text = "To show live system data in the notification bar, the app needs permission to post notifications.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onRequestPermission()
            }) {
                Text(
                    LocalContext.current.string(R.string.allow),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onDismiss) {
                    Text(
                        LocalContext.current.string(R.string.cancel),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Checkbox(
                        checked = isCheck,
                        onCheckedChange = {
                            isCheck = it
                            onDoNotAskMeAgain(it)
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.outline,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    Text(
                        "Don't ask me again",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    )
}

@Composable
fun NotificationToggle(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isEnabled) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                contentDescription = if (isEnabled) "Notifications enabled" else "Notifications disabled",
                tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "App Notifications",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isEnabled)
                        "Receive daily health reminders and engagement notifications"
                    else
                        "Notifications are disabled. Enable to receive daily reminders",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    maxLines = 2,
                    lineHeight = 14.sp
                )
            }
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DesignSystemColors.DarkII,
                uncheckedThumbColor = DesignSystemColors.DarkII,
                checkedTrackColor = DesignSystemColors.Border,
                uncheckedTrackColor = DesignSystemColors.White
            )
        )
    }
}

@Composable
fun LanguageSelector(
    currentLanguage: LocaleManager.AppLanguage,
    onLanguageSelected: (LocaleManager.AppLanguage) -> Unit
) {
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }

    // Language selection row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showLanguageDialog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = context.string(R.string.language),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = context.string(R.string.language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = currentLanguage.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }

    // Language selection dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = context.string(R.string.select_language),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LocaleManager.AppLanguage.values().forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLanguageSelected(language)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentLanguage == language,
                                onClick = {
                                    onLanguageSelected(language)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = language.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (currentLanguage == language)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(
                        text = context.string(R.string.cancel),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }
}

@Composable
fun LeaderboardAccountStatus() {
    val context = LocalContext.current
    val activity = context as? Activity
    var isAnonymous by remember { mutableStateOf(LeaderboardManager.isAnonymousUser()) }
    var isEmailLinked by remember { mutableStateOf(LeaderboardManager.isEmailLinked(context)) }
    var userId by remember { mutableStateOf(LeaderboardManager.getCurrentUserId()) }
    var isLinking by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var userEmail by remember { mutableStateOf<String?>(null) }
    var userDisplayName by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showReauthDialog by remember { mutableStateOf(false) }
    var pendingDeleteAfterReauth by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Google Sign-In launcher for re-authentication (account deletion)
    val reauthSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken

                if (idToken != null) {
                    // Re-authenticate with the credential
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    val (success, needsReauth) = LeaderboardManager.deleteAccount(context, credential)
                    
                    showReauthDialog = false
                    isDeleting = false
                    pendingDeleteAfterReauth = false
                    
                    if (success) {
                        // Reset state
                        userId = LeaderboardManager.getCurrentUserId()
                        isAnonymous = LeaderboardManager.isAnonymousUser()
                        isEmailLinked = LeaderboardManager.isEmailLinked(context)
                        userEmail = null
                        userDisplayName = null
                        android.widget.Toast.makeText(
                            context,
                            "Account deleted successfully",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else if (needsReauth) {
                        // Still needs re-authentication (shouldn't happen, but handle it)
                        android.widget.Toast.makeText(
                            context,
                            "Re-authentication required. Please try again.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "Failed to delete account. Please try again.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    isDeleting = false
                    pendingDeleteAfterReauth = false
                    android.widget.Toast.makeText(
                        context,
                        "Re-authentication failed. Please try again.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                isDeleting = false
                pendingDeleteAfterReauth = false
                android.widget.Toast.makeText(
                    context,
                    "Re-authentication failed. Please try again.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Google Sign-In launcher for linking account
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken

                if (idToken != null) {
                    val success = LeaderboardManager.linkGmailAccount(context, idToken)
                    if (success) {
                        isEmailLinked = true
                        userEmail = LeaderboardManager.getUserEmail()
                        userDisplayName = LeaderboardManager.getUserDisplayName()
                        android.widget.Toast.makeText(
                            context,
                            "Account linked successfully!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "Failed to link account. Please try again.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Sign-in failed. Please try again.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    context,
                    "Sign-in failed. Please try again.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } finally {
                isLinking = false
            }
        }
    }

    // Refresh status and ensure user exists
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        userId = LeaderboardManager.getCurrentUserId()
        isAnonymous = LeaderboardManager.isAnonymousUser()
        isEmailLinked = LeaderboardManager.isEmailLinked(context)
        userEmail = LeaderboardManager.getUserEmail()
        userDisplayName = LeaderboardManager.getUserDisplayName()

        // If no user exists, ensure one is created
        if (userId.isEmpty()) {
            LeaderboardManager.ensureUserExists(context)
            kotlinx.coroutines.delay(2000)
            userId = LeaderboardManager.getCurrentUserId()
            isAnonymous = LeaderboardManager.isAnonymousUser()
            isEmailLinked = LeaderboardManager.isEmailLinked(context)
            userEmail = LeaderboardManager.getUserEmail()
            userDisplayName = LeaderboardManager.getUserDisplayName()
        }
    }

    // Refresh when linking completes or auth state changes
    LaunchedEffect(isEmailLinked) {
        if (isEmailLinked) {
            userId = LeaderboardManager.getCurrentUserId()
            isAnonymous = LeaderboardManager.isAnonymousUser()
            userEmail = LeaderboardManager.getUserEmail()
            userDisplayName = LeaderboardManager.getUserDisplayName()
        }
    }

    // Handle logout
    fun handleLogout() {
        scope.launch {
            isLoggingOut = true
            try {
                // Sign out from Google Sign-In first if we have an activity
                if (activity != null) {
                    try {
                        var webClientId = AdConfig.getOAuthClientId()
                        if (webClientId.isEmpty()) {
                            // Fallback to strings.xml if AdConfig returns empty
                            webClientId = activity.getString(R.string.default_web_client_id)
                        }
                        if (webClientId.isNotEmpty() && webClientId != "YOUR_OAUTH_CLIENT_ID") {
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(webClientId)
                                .requestEmail()
                                .build()

                            val googleSignInClient = GoogleSignIn.getClient(activity, gso)
                            googleSignInClient.signOut().await()
                        }
                    } catch (e: Exception) {
                        Log.w(
                            "LeaderboardAccountStatus",
                            "Failed to sign out from Google Sign-In",
                            e
                        )
                        // Continue with Firebase logout even if Google sign out fails
                    }
                }

                // Then logout from Firebase and create anonymous user
                val success = LeaderboardManager.logout(context)
                if (success) {
                    // Reset all state
                    userId = LeaderboardManager.getCurrentUserId()
                    isAnonymous = LeaderboardManager.isAnonymousUser()
                    isEmailLinked = LeaderboardManager.isEmailLinked(context)
                    userEmail = null
                    userDisplayName = null
                    android.widget.Toast.makeText(
                        context,
                        "Logged out successfully",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Failed to logout. Please try again.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("LeaderboardAccountStatus", "Error during logout", e)
                android.widget.Toast.makeText(
                    context,
                    "An error occurred during logout",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } finally {
                isLoggingOut = false
            }
        }
    }

    // Better organized leaderboard card - no border, reduced padding
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isEmailLinked)
            DesignSystemColors.NeonGreen.copy(alpha = 0.1f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Header: Status + Checkmark
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Status Icon
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isEmailLinked)
                        DesignSystemColors.NeonGreen.copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isEmailLinked) Icons.Default.AccountCircle else Icons.Default.Info,
                            contentDescription = "Account status",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Status Text + Checkmark
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = when {
                            isEmailLinked -> "Account Linked"
                            userId.isNotEmpty() -> "Anonymous Account"
                            else -> "Setting up..."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )

                    // Checkmark badge
                    if (isEmailLinked) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) {
                                DesignSystemColors.NeonGreen
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(14.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✓",
                                    color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) {
                                        DesignSystemColors.Dark
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                }
            }

            // Email Display (if linked)
            if (isEmailLinked && userEmail != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = userEmail ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Display Name (if different from email)
            if (isEmailLinked && userDisplayName != null && userDisplayName != userEmail) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = userDisplayName ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }

            // Data Safety Message
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        isEmailLinked -> "Your data is safe and will be kept forever"
                        userId.isNotEmpty() -> {
                            val daysUntilRemoval =
                                LeaderboardManager.getDaysUntilDataRemoval(context)
                            if (daysUntilRemoval == -1L) {
                                "Your data is anonymous and will be kept as long as possible"
                            } else if (daysUntilRemoval > 0) {
                                "Link Gmail to keep data safe forever (${daysUntilRemoval} days remaining)"
                            } else {
                                "Link Gmail to keep your leaderboard data safe forever"
                            }
                        }

                        else -> "Setting up your anonymous account..."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isEmailLinked)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 2
                )
            }

            // Logout and Delete Account Buttons (if linked)
            if (isEmailLinked) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Logout Button
                    TextButton(
                        onClick = { handleLogout() },
                        enabled = !isLoggingOut,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        if (isLoggingOut) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                strokeWidth = 1.5.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Logging out...",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                fontSize = 9.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Logout",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                fontSize = 9.sp
                            )
                        }
                    }
                    
                    // Delete Account Button
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !isLoggingOut,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Delete",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            fontSize = 9.sp
                        )
                    }
                    
                    // Delete Account Confirmation Dialog
                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
                            title = {
                                Text(
                                    text = "Delete Account",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 14.sp
                                )
                            },
                            text = {
                                Text(
                                    text = "This will permanently delete your account and all associated data. This action cannot be undone.\n\nAre you sure you want to delete your account?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 11.sp
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        isDeleting = true
                                        scope.launch {
                                            val (success, needsReauth) = LeaderboardManager.deleteAccount(context)
                                            
                                            if (needsReauth) {
                                                // Re-authentication required - show re-auth dialog
                                                showDeleteDialog = false
                                                showReauthDialog = true
                                                pendingDeleteAfterReauth = true
                                            } else if (success) {
                                                // Account deleted successfully
                                                showDeleteDialog = false
                                                isDeleting = false
                                                // Reset state
                                                userId = LeaderboardManager.getCurrentUserId()
                                                isAnonymous = LeaderboardManager.isAnonymousUser()
                                                isEmailLinked = LeaderboardManager.isEmailLinked(context)
                                                userEmail = null
                                                userDisplayName = null
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Account deleted successfully",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                // Deletion failed
                                                showDeleteDialog = false
                                                isDeleting = false
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Failed to delete account. Please try again.",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    enabled = !isDeleting,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    if (isDeleting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            color = MaterialTheme.colorScheme.error,
                                            strokeWidth = 1.5.dp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        "Delete",
                                        fontSize = 11.sp
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showDeleteDialog = false },
                                    enabled = !isDeleting
                                ) {
                                    Text(
                                        "Cancel",
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        )
                    }
                    
                    // Re-authentication Dialog (for account deletion)
                    if (showReauthDialog) {
                        AlertDialog(
                            onDismissRequest = { 
                                if (!isDeleting) {
                                    showReauthDialog = false
                                    pendingDeleteAfterReauth = false
                                }
                            },
                            title = {
                                Text(
                                    text = "Re-authentication Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 14.sp
                                )
                            },
                            text = {
                                Text(
                                    text = "For security reasons, you need to sign in again to delete your account. This helps protect your account from unauthorized deletion.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 11.sp
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        if (activity != null) {
                                            isDeleting = true
                                            startGoogleSignInForReauth(activity, reauthSignInLauncher)
                                        } else {
                                            showReauthDialog = false
                                            pendingDeleteAfterReauth = false
                                            android.widget.Toast.makeText(
                                                context,
                                                "Unable to start re-authentication. Please try again.",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    enabled = !isDeleting,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    if (isDeleting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 1.5.dp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        "Sign In",
                                        fontSize = 11.sp
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { 
                                        showReauthDialog = false
                                        pendingDeleteAfterReauth = false
                                        isDeleting = false
                                    },
                                    enabled = !isDeleting
                                ) {
                                    Text(
                                        "Cancel",
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        )
                    }
                }
            } else if (userId.isNotEmpty() && activity != null) {
                Spacer(modifier = Modifier.height(10.dp))
                // Compact Link Gmail button (Credential Manager / Passkeys)
                Button(
                    onClick = {
                        scope.launch {
                            isLinking = true
                            val outcome = PasskeyAuthManager.signInWithGoogle(context)
                            when (outcome) {
                                is PasskeyAuthManager.AuthOutcome.Success -> {
                                    isEmailLinked = LeaderboardManager.isEmailLinked(context)
                                    userEmail = LeaderboardManager.getUserEmail()
                                    userDisplayName = LeaderboardManager.getUserDisplayName()
                                    android.widget.Toast.makeText(
                                        context,
                                        "Account linked successfully!",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                is PasskeyAuthManager.AuthOutcome.Cancelled -> {
                                    // User cancelled; no toast needed
                                }
                                is PasskeyAuthManager.AuthOutcome.Error -> {
                                    android.widget.Toast.makeText(
                                        context,
                                        outcome.message,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            isLinking = false
                        }
                    },
                    enabled = !isLinking,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignSystemColors.NeonGreen,
                        contentColor = DesignSystemColors.Dark
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (isLinking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = DesignSystemColors.Dark,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Linking...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Link Gmail Account",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Link Gmail account from drawer
 */
private fun linkGmailAccountFromDrawer(
    activity: Activity,
    launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
) {
    try {
        var webClientId = AdConfig.getOAuthClientId()
        if (webClientId.isEmpty()) {
            // Fallback to strings.xml if AdConfig returns empty
            webClientId = activity.getString(R.string.default_web_client_id)
        }
        if (webClientId.isNotEmpty() && webClientId != "YOUR_OAUTH_CLIENT_ID") {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()

            val googleSignInClient = GoogleSignIn.getClient(activity, gso)
            val signInIntent = googleSignInClient.signInIntent
            launcher.launch(signInIntent)
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            activity,
            "Failed to start sign-in. Please try again.",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

private fun startGoogleSignInForReauth(
    activity: Activity,
    launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
) {
    try {
        var webClientId = AdConfig.getOAuthClientId()
        if (webClientId.isEmpty()) {
            // Fallback to strings.xml if AdConfig returns empty
            webClientId = activity.getString(R.string.default_web_client_id)
        }
        if (webClientId.isNotEmpty() && webClientId != "YOUR_OAUTH_CLIENT_ID") {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()

            val googleSignInClient = GoogleSignIn.getClient(activity, gso)
            val signInIntent = googleSignInClient.signInIntent
            launcher.launch(signInIntent)
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            activity,
            "Failed to start re-authentication. Please try again.",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Prominent "Ad" badge for drawer promotional items.
 * Required by Google Play Deceptive Ads policy — ads must be clearly labeled.
 */
@Composable
private fun AdBadge() {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.error,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "Ad",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onError,
        )
    }
}
