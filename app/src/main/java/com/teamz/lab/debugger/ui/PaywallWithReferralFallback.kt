package com.teamz.lab.debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.teamz.lab.debugger.ui.theme.AppTheme
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.ui.theme.useThemeManager
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.ReferralManager
import com.teamz.lab.debugger.utils.RevenueCatManager

@Composable
fun PaywallWithReferralFallback(
    showPaywall: Boolean,
    onDismiss: () -> Unit,
    analyticsSource: String = "revenuecat_paywall"
) {
    val context = LocalContext.current
    val premiumStatus by RevenueCatManager.premiumStatusFlow.collectAsState()
    val isPremium = (premiumStatus as? RevenueCatManager.PremiumStatus.Premium)?.isActive == true

    var purchaseCompleted by remember { mutableStateOf(false) }
    var showReferralFallback by remember { mutableStateOf(false) }
    var showFullShareDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isPremium) {
        if (isPremium) purchaseCompleted = true
    }

    LaunchedEffect(showPaywall) {
        if (!showPaywall) {
            purchaseCompleted = false
            showReferralFallback = false
            showFullShareDialog = false
        }
    }

    val handleRcDismiss: () -> Unit = {
        if (purchaseCompleted || isPremium) {
            onDismiss()
        } else {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.PremiumPaywallDismissed,
                mapOf(
                    "source" to analyticsSource,
                    "reason" to "referral_fallback_offered"
                )
            )
            showReferralFallback = true
        }
    }

    RevenueCatPaywall(
        showPaywall = showPaywall && !showReferralFallback && !showFullShareDialog,
        onDismiss = handleRcDismiss,
        analyticsSource = analyticsSource
    )

    if (showReferralFallback && !showFullShareDialog) {
        ReferralFallbackScreen(
            analyticsSource = analyticsSource,
            onShareClick = {
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.PremiumPaywallShown,
                    mapOf(
                        "source" to "referral_fallback_${analyticsSource}",
                        "action" to "share_tapped"
                    )
                )
                showFullShareDialog = true
            },
            onDismiss = {
                showReferralFallback = false
                onDismiss()
            }
        )
    }

    if (showFullShareDialog) {
        ViralShareDialog(
            onDismiss = {
                showFullShareDialog = false
                showReferralFallback = false
                onDismiss()
            },
            context = context
        )
    }
}

@Composable
private fun ReferralFallbackScreen(
    analyticsSource: String,
    onShareClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = useThemeManager().getEffectiveTheme() == AppTheme.DESIGN_SYSTEM_DARK
    val referralCount = remember { ReferralManager.getReferralCount(context) }
    val nextTier = remember { ReferralManager.getNextTier(context) }
    val referralsToNext = remember { ReferralManager.getReferralsToNextTier(context) }

    LaunchedEffect(Unit) {
        AnalyticsUtils.logEvent(
            AnalyticsEvent.PremiumPaywallShown,
            mapOf(
                "source" to "referral_fallback_${analyticsSource}",
                "action" to "modal_shown",
                "current_referrals" to referralCount,
                "next_tier_required" to (nextTier?.requiredReferrals ?: 0),
                "next_tier_reward_hours" to (nextTier?.adFreeHours ?: 0L)
            )
        )
    }

    val rewardLabel: String = nextTier?.let {
        when {
            it.adFreeHours >= 720 -> "30 days ad-free"
            it.adFreeHours >= 168 -> "7 days ad-free"
            it.adFreeHours >= 72 -> "3 days ad-free"
            else -> "24 hours ad-free"
        }
    } ?: "max tier unlocked"

    val headlineCopy: String = when {
        nextTier == null || referralsToNext <= 0 ->
            "You've unlocked every reward — share again to keep it rolling."
        referralsToNext == 1 -> "1 more friend → unlock $rewardLabel ${nextTier.badge}"
        else -> "$referralsToNext friends → unlock $rewardLabel ${nextTier.badge}"
    }

    val progress: Float = if (nextTier != null && nextTier.requiredReferrals > 0) {
        (referralCount.toFloat() / nextTier.requiredReferrals.toFloat()).coerceIn(0f, 1f)
    } else 1f

    val animatedProgress = progress

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val accentBg = if (isDark) {
                    DesignSystemColors.NeonGreen.copy(alpha = 0.10f)
                } else {
                    DesignSystemColors.NeonGreen.copy(alpha = 0.18f)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(accentBg, MaterialTheme.colorScheme.background)
                            )
                        )
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(56.dp))

                    Surface(
                        modifier = Modifier.size(88.dp),
                        shape = CircleShape,
                        color = DesignSystemColors.NeonGreen,
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.PeopleAlt,
                                contentDescription = null,
                                tint = DesignSystemColors.Dark,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    Text(
                        text = "Not ready to pay?",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Invite friends and unlock premium for free.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(40.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = headlineCopy,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Start
                            )

                            Spacer(Modifier.height(14.dp))

                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = DesignSystemColors.NeonGreen,
                                trackColor = MaterialTheme.colorScheme.outlineVariant,
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                                gapSize = 0.dp,
                                drawStopIndicator = {}
                            )

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$referralCount referred",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = nextTier?.let { "Goal: ${it.requiredReferrals}" } ?: "Maxed out",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = "Send your link via WhatsApp, Telegram, SMS or email. Rewards unlock automatically when friends install.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(Modifier.weight(1f))

                    Button(
                        onClick = onShareClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DesignSystemColors.NeonGreen,
                            contentColor = DesignSystemColors.Dark
                        )
                    ) {
                        Text(
                            text = "Share with friends",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Maybe later",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
