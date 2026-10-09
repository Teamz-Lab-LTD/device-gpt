package com.teamz.lab.debugger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.teamz.lab.debugger.utils.LocaleManager
import com.teamz.lab.debugger.ui.icons.DgText
import com.teamz.lab.debugger.ui.icons.displayText
import com.teamz.lab.debugger.ui.theme.DgMotion
import com.teamz.lab.debugger.ui.theme.motionTween

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
        )
    ) {
        DisableDialogEnterAnimation()
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
                            contentDescription = stringResource(R.string.close),
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
                                    contentDescription = stringResource(R.string.share),
                                    modifier = Modifier.size(32.dp),
                                    tint = headerIconTint
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            stringResource(R.string.lb_vs_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.lb_vs_subtitle),
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
                        stringResource(R.string.lb_vs_share_via),
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
                        text = stringResource(R.string.lb_vs_whatsapp),
                        containerColor = Color(0xFF25D366),
                        contentColor = Color.White,
                        onClick = {
                            com.teamz.lab.debugger.utils.EngagementTracker.trackSignificantAction(
                                context,
                                com.teamz.lab.debugger.utils.SignificantAction.REFERRAL_INVITE_SENT,
                                mapOf("channel" to "whatsapp")
                            )
                            shareToWhatsApp(context, finalShareText, referralLink, referralCode)
                            onDismiss()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Telegram
                    ShareButton(
                        icon = Icons.Default.Send,
                        text = stringResource(R.string.lb_vs_telegram),
                        containerColor = Color(0xFF0088CC),
                        contentColor = Color.White,
                        onClick = {
                            com.teamz.lab.debugger.utils.EngagementTracker.trackSignificantAction(
                                context,
                                com.teamz.lab.debugger.utils.SignificantAction.REFERRAL_INVITE_SENT,
                                mapOf("channel" to "telegram")
                            )
                            shareToTelegram(context, finalShareText, referralLink, referralCode)
                            onDismiss()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // SMS
                    ShareButton(
                        icon = Icons.Default.Message,
                        text = stringResource(R.string.lb_vs_sms),
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        onClick = {
                            com.teamz.lab.debugger.utils.EngagementTracker.trackSignificantAction(
                                context,
                                com.teamz.lab.debugger.utils.SignificantAction.REFERRAL_INVITE_SENT,
                                mapOf("channel" to "sms")
                            )
                            shareToSMS(context, finalShareText, referralLink, referralCode)
                            onDismiss()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Email
                    ShareButton(
                        icon = Icons.Default.Email,
                        text = stringResource(R.string.lb_vs_email),
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        onClick = {
                            com.teamz.lab.debugger.utils.EngagementTracker.trackSignificantAction(
                                context,
                                com.teamz.lab.debugger.utils.SignificantAction.REFERRAL_INVITE_SENT,
                                mapOf("channel" to "email")
                            )
                            shareToEmail(context, finalShareText, referralLink, referralCode)
                            onDismiss()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Generic share
                    ShareButton(
                        icon = Icons.Default.Share,
                        text = stringResource(R.string.lb_vs_more),
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
                    DgText(
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
                            DgText(
                                currentTier.badge,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        DgText(
                            stringResource(currentTier.titleRes()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    DgText(
                        stringResource(
                            if (referralCount != 1) R.string.lb_vs_friends_many else R.string.lb_vs_friends_one,
                            referralCount.toString()
                        ),
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
                val adFreeText = when {
                    hours >= 48 -> stringResource(R.string.lb_vs_adfree_days, (hours / 24).toString())
                    hours >= 1 -> stringResource(R.string.lb_vs_adfree_hours, hours.toString())
                    else -> stringResource(
                        R.string.lb_vs_adfree_minutes,
                        (adFreeRemainingMs / (60 * 1000)).toString()
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    DgText(
                        adFreeText,
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

                DgText(
                    stringResource(R.string.lb_vs_next, nextTier.badge, stringResource(nextTier.titleRes()), stringResource(nextTier.descriptionRes())),
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
                    animationSpec = motionTween(DgMotion.slow),
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

                DgText(
                    stringResource(
                        if (referralsToNext != 1) {
                            R.string.lb_vs_more_invites_many
                        } else {
                            R.string.lb_vs_more_invites_one
                        },
                        referralsToNext.toString()
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
            } else {
                // Max tier reached
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.lb_vs_max_tier),
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
            stringResource(R.string.lb_vs_milestones),
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
                DgText(
                    tier.badge,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    DgText(
                        stringResource(
                            if (tier.requiredReferrals != 1) {
                                R.string.lb_vs_tier_row_many
                            } else {
                                R.string.lb_vs_tier_row_one
                            },
                            tier.requiredReferrals.toString(),
                            stringResource(tier.titleRes())
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (unlocked) FontWeight.Bold else FontWeight.Normal,
                        color = if (unlocked) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DgText(
                        when {
                            tier.adFreeHours >= 48 -> stringResource(R.string.lb_vs_days_adfree, (tier.adFreeHours / 24).toString())
                            else -> stringResource(R.string.lb_vs_hours_adfree, tier.adFreeHours.toString())
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unlocked) accentColor.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                if (unlocked) {
                    Text(
                        stringResource(R.string.lb_vs_unlocked),
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
    val copiedText = stringResource(R.string.lb_code_copied)

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
                    stringResource(R.string.lb_vs_code_title),
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
                        DgText(
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
                            Toast.makeText(context, displayText(copiedText), Toast.LENGTH_SHORT).show()
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
                        Text(stringResource(R.string.ai_bridge_copy), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                val motivationText = if (nextTier != null) {
                    stringResource(
                        if (referralsToNext != 1) {
                            R.string.lb_vs_motivation_many
                        } else {
                            R.string.lb_vs_motivation_one
                        },
                        referralsToNext.toString(),
                        nextTier.badge,
                        stringResource(nextTier.titleRes()),
                        stringResource(nextTier.descriptionRes())
                    )
                } else {
                    stringResource(R.string.lb_vs_legend)
                }
                DgText(
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
            DgText(
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
    val defaultText = LocaleManager.localizedContext(context)
        .getString(R.string.lb_vs_msg_whatsapp, referralCode, referralLink)

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
    val defaultText = LocaleManager.localizedContext(context)
        .getString(R.string.lb_vs_msg_telegram, referralCode, referralLink)

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
    val defaultText = LocaleManager.localizedContext(context)
        .getString(R.string.lb_vs_msg_sms, referralCode, referralLink)

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
    val defaultText = LocaleManager.localizedContext(context)
        .getString(R.string.lb_vs_msg_email, referralCode, referralLink)

    val finalText = if (shareText.isNotEmpty()) "$shareText\n\n$defaultText" else defaultText

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_SUBJECT,
            LocaleManager.localizedContext(context).getString(R.string.lb_vs_email_subject)
        )
        putExtra(Intent.EXTRA_TEXT, finalText)
    }

    try {
        context.startActivity(
            Intent.createChooser(
                intent,
                LocaleManager.localizedContext(context).getString(R.string.lb_vs_email_chooser)
            )
        )
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

    val res = LocaleManager.localizedContext(context)
    // The trend word comes from PowerConsumptionAggregator's enum name and stays English for now.
    val trend = aggregatedStats.powerTrend.name.lowercase().replaceFirstChar { it.uppercase() }

    return buildString {
        appendLine(res.getString(R.string.lb_vs_power_title))
        appendLine()
        appendLine(res.getString(R.string.lb_vs_power_total, String.format("%.1f", powerData.totalPower / 1000)))
        appendLine(res.getString(R.string.lb_vs_power_trend, trend))
        appendLine(res.getString(R.string.lb_vs_power_avg, String.format("%.1f", aggregatedStats.averagePower / 1000)))
        appendLine(res.getString(R.string.lb_vs_power_peak, String.format("%.1f", aggregatedStats.peakPower / 1000)))
        appendLine()

        if (topConsumers.isNotEmpty()) {
            appendLine(res.getString(R.string.lb_vs_power_top))
            topConsumers.forEachIndexed { index, component ->
                appendLine("${index + 1}. ${component.component}: ${String.format("%.1f", component.powerConsumption / 1000)}W")
            }
            appendLine()
        }

        appendLine(res.getString(R.string.lb_vs_power_generated, appName))
        appendLine(
            res.getString(
                R.string.lb_vs_power_download,
                "https://play.google.com/store/apps/details?id=${context.packageName}"
            )
        )
    }
}

// ── Reward step wording ─────────────────────────────────────────────
// ReferralManager.RewardTier keeps its English title and description (the tier name is also
// what gets saved); the dialog shows these resources so the text follows the chosen language.

@StringRes
private fun ReferralManager.RewardTier.titleRes(): Int = when (this) {
    ReferralManager.RewardTier.NONE -> R.string.lb_tier_none_title
    ReferralManager.RewardTier.BRONZE -> R.string.lb_tier_bronze_title
    ReferralManager.RewardTier.SILVER -> R.string.lb_tier_silver_title
    ReferralManager.RewardTier.GOLD -> R.string.lb_tier_gold_title
    ReferralManager.RewardTier.LEGEND -> R.string.lb_tier_legend_title
}

@StringRes
private fun ReferralManager.RewardTier.descriptionRes(): Int = when (this) {
    ReferralManager.RewardTier.NONE -> R.string.lb_tier_none_desc
    ReferralManager.RewardTier.BRONZE -> R.string.lb_tier_bronze_desc
    ReferralManager.RewardTier.SILVER -> R.string.lb_tier_silver_desc
    ReferralManager.RewardTier.GOLD -> R.string.lb_tier_gold_desc
    ReferralManager.RewardTier.LEGEND -> R.string.lb_tier_legend_desc
}
