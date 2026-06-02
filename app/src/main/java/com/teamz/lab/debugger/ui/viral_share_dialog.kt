package com.teamz.lab.debugger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.ui.theme.AppTheme
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.ui.theme.useThemeManager
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.ReferralManager
import com.teamz.lab.debugger.utils.string

/**
 * Viral Share Dialog - Makes sharing easy, shows reward progress, and tracks viral growth
 */
@Composable
fun ViralShareDialog(
    onDismiss: () -> Unit,
    context: Context,
    shareText: String = "",
    showReferralCode: Boolean = true,
    powerData: com.teamz.lab.debugger.utils.PowerConsumptionUtils.PowerConsumptionSummary? = null,
    aggregatedStats: com.teamz.lab.debugger.utils.PowerConsumptionAggregator.PowerStats? = null
) {
    val referralCode = remember { ReferralManager.getOrCreateReferralCode(context) }
    val referralLink = remember { ReferralManager.getShortReferralLink(context) }
    val referralCount = remember { ReferralManager.getReferralCount(context) }
    val currentTier = remember { ReferralManager.getCurrentTier(context) }
    val nextTier = remember { ReferralManager.getNextTier(context) }
    val referralsToNext = remember { ReferralManager.getReferralsToNextTier(context) }
    val isAdFree = remember { ReferralManager.isAdFreeFromReferrals(context) }
    val adFreeRemainingMs = remember { ReferralManager.getAdFreeRemainingMs(context) }
    val isDark = useThemeManager().getEffectiveTheme() == AppTheme.DESIGN_SYSTEM_DARK

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top-right close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                // Header
                val headerBg = if (isDark) DesignSystemColors.NeonGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(headerBg, MaterialTheme.colorScheme.surface)
                            )
                        )
                        .padding(top = 28.dp, bottom = 20.dp, start = 20.dp, end = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val headerIconBg = if (isDark) DesignSystemColors.NeonGreen else MaterialTheme.colorScheme.primaryContainer
                        val headerIconTint = if (isDark) DesignSystemColors.Dark else MaterialTheme.colorScheme.onPrimaryContainer
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = headerIconBg,
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Share",
                                    modifier = Modifier.size(32.dp),
                                    tint = headerIconTint
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "Share & Earn Rewards!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Every friend you invite earns you ad-free time",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = TextUnit(20f, TextUnitType.Sp)
                        )
                    }
                }

                // ── Reward Progress Card ────────────────────────────────
                Spacer(modifier = Modifier.height(4.dp))
                RewardProgressCard(
                    referralCount = referralCount,
                    currentTier = currentTier,
                    nextTier = nextTier,
                    referralsToNext = referralsToNext,
                    isAdFree = isAdFree,
                    adFreeRemainingMs = adFreeRemainingMs,
                    isDark = isDark
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Social share buttons section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        "Share via",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    val powerInsightsText = remember(powerData, aggregatedStats) {
                        if (powerData != null && aggregatedStats != null) {
                            generatePowerShareText(powerData, aggregatedStats, context)
                        } else ""
                    }

                    val finalShareText = if (powerInsightsText.isNotEmpty()) {
                        if (shareText.isNotEmpty()) "$shareText\n\n$powerInsightsText" else powerInsightsText
                    } else shareText

                    // WhatsApp
                    ShareButton(
                        icon = Icons.Default.Chat,
                        text = "WhatsApp",
                        containerColor = Color(0xFF25D366),
                        contentColor = Color.White,
                        onClick = {
                            shareToWhatsApp(context, finalShareText, referralLink, referralCode)
                            onDismiss()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Telegram
                    ShareButton(
                        icon = Icons.Default.Send,
                        text = "Telegram",
                        containerColor = Color(0xFF0088CC),
                        contentColor = Color.White,
                        onClick = {
                            shareToTelegram(context, finalShareText, referralLink, referralCode)
                            onDismiss()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // SMS
                    ShareButton(
                        icon = Icons.Default.Message,
                        text = "SMS",
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        onClick = {
                            shareToSMS(context, finalShareText, referralLink, referralCode)
                            onDismiss()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Email
                    ShareButton(
                        icon = Icons.Default.Email,
                        text = "Email",
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        onClick = {
                            shareToEmail(context, finalShareText, referralLink, referralCode)
                            onDismiss()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Generic share
                    ShareButton(
                        icon = Icons.Default.Share,
                        text = "More Options",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            ReferralManager.shareReferralLink(context, finalShareText)
                            onDismiss()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Referral code + copy
                if (showReferralCode) {
                    ReferralCodeSection(
                        referralCode = referralCode,
                        context = context,
                        isDark = isDark,
                        nextTier = nextTier,
                        referralsToNext = referralsToNext
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Close button
                val closeBorder = if (isDark) BorderStroke(1.5.dp, DesignSystemColors.NeonGreen.copy(alpha = 0.6f)) else null
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = closeBorder,
                    colors = if (isDark) ButtonDefaults.outlinedButtonColors(contentColor = DesignSystemColors.NeonGreen) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Text(
                        LocalContext.current.string(R.string.close),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ── Reward Progress Card ────────────────────────────────────────────

@Composable
private fun RewardProgressCard(
    referralCount: Int,
    currentTier: ReferralManager.RewardTier,
    nextTier: ReferralManager.RewardTier?,
    referralsToNext: Int,
    isAdFree: Boolean,
    adFreeRemainingMs: Long,
    isDark: Boolean
) {
    val accentColor = if (isDark) DesignSystemColors.NeonGreen else MaterialTheme.colorScheme.primary
    val accentOnColor = if (isDark) DesignSystemColors.Dark else MaterialTheme.colorScheme.onPrimary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) DesignSystemColors.NeonGreen.copy(alpha = 0.10f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Current status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentTier.badge.isNotEmpty()) {
                            Text(
                                currentTier.badge,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            currentTier.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "$referralCount friend${if (referralCount != 1) "s" else ""} referred",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = accentColor
                        )
                    }
                }
            }

            // Ad-free status
            if (isAdFree && adFreeRemainingMs > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                val hours = adFreeRemainingMs / (3600 * 1000)
                val timeText = when {
                    hours >= 48 -> "${hours / 24} days"
                    hours >= 1 -> "${hours}h"
                    else -> "${adFreeRemainingMs / (60 * 1000)}m"
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        "Ad-free: $timeText remaining",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Progress to next tier
            if (nextTier != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Next: ${nextTier.badge} ${nextTier.title} — ${nextTier.description}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Progress bar
                val progress = if (nextTier.requiredReferrals > 0) {
                    referralCount.toFloat() / nextTier.requiredReferrals.toFloat()
                } else 0f
                val animatedProgress by animateFloatAsState(
                    targetValue = progress.coerceIn(0f, 1f),
                    animationSpec = tween(800),
                    label = "progress"
                )

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accentColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    "$referralsToNext more invite${if (referralsToNext != 1) "s" else ""} to unlock!",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
            } else {
                // Max tier reached
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "You've reached the highest tier! Thank you for spreading the word!",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = accentColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Reward tiers summary
            Spacer(modifier = Modifier.height(16.dp))
            RewardTiersList(referralCount = referralCount, isDark = isDark)
        }
    }
}

@Composable
private fun RewardTiersList(referralCount: Int, isDark: Boolean) {
    val tiers = ReferralManager.RewardTier.entries.filter { it != ReferralManager.RewardTier.NONE }
    val accentColor = if (isDark) DesignSystemColors.NeonGreen else MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Reward Milestones",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        tiers.forEach { tier ->
            val unlocked = referralCount >= tier.requiredReferrals
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tier.badge,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${tier.requiredReferrals} invite${if (tier.requiredReferrals != 1) "s" else ""} — ${tier.title}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (unlocked) FontWeight.Bold else FontWeight.Normal,
                        color = if (unlocked) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        when {
                            tier.adFreeHours >= 48 -> "${tier.adFreeHours / 24} days ad-free"
                            else -> "${tier.adFreeHours}h ad-free"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unlocked) accentColor.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                if (unlocked) {
                    Text(
                        "Unlocked",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }
        }
    }
}

// ── Referral Code Section ───────────────────────────────────────────

@Composable
private fun ReferralCodeSection(
    referralCode: String,
    context: Context,
    isDark: Boolean,
    nextTier: ReferralManager.RewardTier?,
    referralsToNext: Int
) {
    val codeBoxBg = if (isDark) DesignSystemColors.NeonGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer
    val codeBoxText = if (isDark) DesignSystemColors.White else MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Your Referral Code",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = codeBoxBg
                    ) {
                        Text(
                            referralCode,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = codeBoxText,
                            letterSpacing = TextUnit(1.5f, TextUnitType.Sp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Referral Code", referralCode))
                            Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                            AnalyticsUtils.logEvent(AnalyticsEvent.ReferralShared, mapOf("method" to "copy"))
                        },
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) DesignSystemColors.NeonGreen else MaterialTheme.colorScheme.primary,
                            contentColor = if (isDark) DesignSystemColors.Dark else MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                val motivationText = if (nextTier != null) {
                    "Share with $referralsToNext more friend${if (referralsToNext != 1) "s" else ""} to unlock ${nextTier.badge} ${nextTier.title} — ${nextTier.description}"
                } else {
                    "You're a Legend! Keep sharing to help friends discover their device health."
                }
                Text(
                    motivationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── Share Button ────────────────────────────────────────────────────

@Composable
private fun ShareButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = text,
                modifier = Modifier.size(24.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

// ── Platform Share Functions ────────────────────────────────────────

private fun shareToWhatsApp(context: Context, shareText: String, referralLink: String, referralCode: String) {
    val defaultText = """
        🔍 Check out this amazing device health checker app!

        📱 Get detailed insights about your phone's performance, battery, storage, and security.

        Use my referral code: $referralCode

        Download now: $referralLink

        #PhoneHealth #DeviceChecker
    """.trimIndent()

    val finalText = if (shareText.isNotEmpty()) "$shareText\n\n$defaultText" else defaultText

    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_TEXT, finalText)
        }
        context.startActivity(intent)
        AnalyticsUtils.logEvent(AnalyticsEvent.ShareToWhatsApp, mapOf("referral_code" to referralCode))
    } catch (e: Exception) {
        ReferralManager.shareReferralLink(context, finalText)
    }
}

private fun shareToTelegram(context: Context, shareText: String, referralLink: String, referralCode: String) {
    val defaultText = """
        🔍 Check out this amazing device health checker app!

        📱 Get detailed insights about your phone's performance, battery, storage, and security.

        Use my referral code: $referralCode

        Download now: $referralLink
    """.trimIndent()

    val finalText = if (shareText.isNotEmpty()) "$shareText\n\n$defaultText" else defaultText

    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("org.telegram.messenger")
            putExtra(Intent.EXTRA_TEXT, finalText)
        }
        context.startActivity(intent)
        AnalyticsUtils.logEvent(AnalyticsEvent.ShareToTelegram, mapOf("referral_code" to referralCode))
    } catch (e: Exception) {
        ReferralManager.shareReferralLink(context, finalText)
    }
}

private fun shareToSMS(context: Context, shareText: String, referralLink: String, referralCode: String) {
    val defaultText = """
        Check out this device health app! Use my code: $referralCode
        $referralLink
    """.trimIndent()

    val finalText = if (shareText.isNotEmpty()) "$shareText\n\n$defaultText" else defaultText

    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:")
        putExtra("sms_body", finalText)
    }

    try {
        context.startActivity(intent)
        AnalyticsUtils.logEvent(AnalyticsEvent.ShareToSMS, mapOf("referral_code" to referralCode))
    } catch (e: Exception) {
        ReferralManager.shareReferralLink(context, finalText)
    }
}

private fun shareToEmail(context: Context, shareText: String, referralLink: String, referralCode: String) {
    val defaultText = """
        Check out this amazing device health checker app!

        Get detailed insights about your phone's performance, battery, storage, and security.

        Use my referral code: $referralCode

        Download now: $referralLink
    """.trimIndent()

    val finalText = if (shareText.isNotEmpty()) "$shareText\n\n$defaultText" else defaultText

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Check out this amazing device health app!")
        putExtra(Intent.EXTRA_TEXT, finalText)
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Share via Email"))
        AnalyticsUtils.logEvent(AnalyticsEvent.ShareToEmail, mapOf("referral_code" to referralCode))
    } catch (e: Exception) {
        ReferralManager.shareReferralLink(context, finalText)
    }
}

private fun generatePowerShareText(
    powerData: com.teamz.lab.debugger.utils.PowerConsumptionUtils.PowerConsumptionSummary,
    aggregatedStats: com.teamz.lab.debugger.utils.PowerConsumptionAggregator.PowerStats,
    context: Context
): String {
    val appName = try {
        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        context.packageManager.getApplicationLabel(applicationInfo).toString()
    } catch (e: Exception) {
        "DeviceGPT"
    }

    val topConsumers = powerData.components
        .sortedByDescending { it.powerConsumption }
        .take(3)

    return buildString {
        appendLine("⚡ Power Consumption Insights")
        appendLine()
        appendLine("📊 Total Power: ${String.format("%.1f", powerData.totalPower / 1000)}W")
        appendLine("📈 Trend: ${aggregatedStats.powerTrend.name.lowercase().replaceFirstChar { it.uppercase() }}")
        appendLine("📉 Average: ${String.format("%.1f", aggregatedStats.averagePower / 1000)}W")
        appendLine("🔝 Peak: ${String.format("%.1f", aggregatedStats.peakPower / 1000)}W")
        appendLine()

        if (topConsumers.isNotEmpty()) {
            appendLine("🔝 Top Power Consumers:")
            topConsumers.forEachIndexed { index, component ->
                appendLine("${index + 1}. ${component.component}: ${String.format("%.1f", component.powerConsumption / 1000)}W")
            }
            appendLine()
        }

        appendLine("📱 Generated by $appName")
        appendLine("🔗 Download: https://play.google.com/store/apps/details?id=${context.packageName}")
    }
}
