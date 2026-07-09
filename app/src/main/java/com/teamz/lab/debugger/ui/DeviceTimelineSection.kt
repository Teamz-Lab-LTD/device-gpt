package com.teamz.lab.debugger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamz.lab.debugger.db.DeviceEvent
import com.teamz.lab.debugger.db.DeviceEventsRepository
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.RemoteConfigUtils
import com.teamz.lab.debugger.utils.RevenueCatManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v3.2.0 R5 — Device Timeline (2026-07-10 growth synthesis).
 *
 * Day-grouped event history under the LastScoreCard on the Health tab.
 * Free tier: last 7 days. Premium: full window (90-day retention).
 * History is COLLECTED regardless of tier — the paywall unlocks the view,
 * never the collection, and the copy says so plainly.
 *
 * RC-gated: `timeline_enabled` (default OFF — dark-shipped).
 * Standing policy condition: NO countdown timers, NO fake urgency on the
 * locked row or its paywall.
 */
@Composable
fun DeviceTimelineSection(
    onHistoryPaywall: () -> Unit,
) {
    if (!RemoteConfigUtils.isTimelineEnabled()) return
    val context = LocalContext.current
    val isPremium = RevenueCatManager.isPremium()

    var events by remember { mutableStateOf<List<DeviceEvent>>(emptyList()) }
    var totalCount by remember { mutableStateOf(0) }

    LaunchedEffect(isPremium) {
        try {
            events = DeviceEventsRepository.timelineWindow(context, if (isPremium) 90L else 7L)
            totalCount = DeviceEventsRepository.eventCount(context)
            AnalyticsUtils.logEvent(
                AnalyticsEvent.TimelineOpened,
                mapOf(
                    "events_in_window" to events.size,
                    "events_total" to totalCount,
                    "is_premium" to isPremium,
                )
            )
        } catch (_: Throwable) { /* DB read failure: render nothing extra */ }
    }

    if (events.isEmpty() && totalCount == 0) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Device Timeline",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isPremium) "Last 90 days of this device's history"
                else "Last 7 days. Your full history is saved on your phone — Premium unlocks the full view.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(12.dp))

            val dayFmt = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()) }
            val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            val grouped = remember(events) {
                events.groupBy { dayFmt.format(Date(it.timestamp)) }
            }
            grouped.forEach { (day, dayEvents) ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                dayEvents.forEach { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${typeIcon(event.type)} ${event.label ?: event.type}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = timeFmt.format(Date(event.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            // Locked older-history row (free tier only, only when older events exist).
            // Factual copy — no urgency, no countdown.
            if (!isPremium && totalCount > events.size) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.TimelineHistoryPaywallShown,
                                mapOf("hidden_events" to (totalCount - events.size))
                            )
                            onHistoryPaywall()
                        }
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        text = "🔒 ${totalCount - events.size} older event${if (totalCount - events.size == 1) "" else "s"} — Premium shows 90 days",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

private fun typeIcon(type: String): String = when (type) {
    DeviceEvent.TYPE_SCORE_SCAN -> "📊"
    DeviceEvent.TYPE_CHARGE_SESSION -> "🔌"
    DeviceEvent.TYPE_APP_INSTALLED -> "📦"
    DeviceEvent.TYPE_BASELINE_SNAPSHOT -> "📈"
    else -> "•"
}

/**
 * v3.2.0 R2 slice 1 — in-app charge summary card. Zero permissions needed:
 * renders the most recent charge session (<24h old) from device_events.
 * RC-gated `charge_summary_enabled` (default OFF).
 */
@Composable
fun ChargeSummaryCard() {
    if (!RemoteConfigUtils.isChargeSummaryEnabled()) return
    val context = LocalContext.current
    var session by remember { mutableStateOf<DeviceEvent?>(null) }

    LaunchedEffect(Unit) {
        try {
            val latest = DeviceEventsRepository.latestChargeSessions(context, 1).firstOrNull()
            if (latest != null &&
                System.currentTimeMillis() - latest.timestamp < 24L * 60 * 60 * 1000
            ) {
                session = latest
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.ChargeSummarySurfaced,
                    mapOf("age_min" to ((System.currentTimeMillis() - latest.timestamp) / 60_000L).toInt())
                )
            }
        } catch (_: Throwable) { }
    }

    val s = session ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🔌 Last charge",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = s.label ?: "Charge session recorded",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
