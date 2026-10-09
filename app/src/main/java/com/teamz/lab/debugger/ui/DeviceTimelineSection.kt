package com.teamz.lab.debugger.ui

import android.content.Context
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
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.string
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamz.lab.debugger.db.DeviceEvent
import com.teamz.lab.debugger.db.DeviceEventsRepository
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.LocaleManager
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
                text = context.string(R.string.timeline_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isPremium) context.string(R.string.timeline_subtitle_premium)
                else context.string(R.string.timeline_subtitle_free),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(12.dp))

            val dayFmt = remember { SimpleDateFormat("EEE, d MMM", timelineDateLocale(context)) }
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
                            text = "${typeIcon(event.type)} ${timelineLabel(context, event)}",
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
                        text = context.string(
                            if (totalCount - events.size == 1) R.string.timeline_older_one
                            else R.string.timeline_older_many,
                            totalCount - events.size,
                        ),
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

/**
 * Day names and month names in the app language. The digits stay Latin (`nu-latn`), like every
 * other number in the app.
 */
internal fun timelineDateLocale(context: Context): Locale =
    if (context.resources.configuration.locales[0].language == LocaleManager.LANGUAGE_BANGLA) {
        Locale.forLanguageTag("bn-u-nu-latn")
    } else {
        Locale.getDefault()
    }

/**
 * The text of one timeline row in the app language.
 *
 * `DeviceEvent.label` is written once, in English, when the event happens, and old rows stay in
 * the database for 90 days. So the stored label is never shown as it is: the row is built again
 * from the event's own fields (score, charge payload), and only an event this code does not
 * recognise falls back to the stored label.
 */
internal fun timelineLabel(context: Context, event: DeviceEvent): String {
    val stored = event.label
    return when (event.type) {
        DeviceEvent.TYPE_SCORE_SCAN -> event.score
            ?.let { context.string(R.string.mx_timeline_score_scan, it.toString()) }
        DeviceEvent.TYPE_BASELINE_SNAPSHOT -> event.score
            ?.let { context.string(R.string.mx_timeline_daily_snapshot, (it / 10).toString()) }
        DeviceEvent.TYPE_CHARGE_SESSION -> chargeLabel(context, event.payload)
        DeviceEvent.TYPE_APP_INSTALLED -> stored
            ?.takeIf { it.startsWith(APP_INSTALLED_PREFIX) }
            ?.let { context.string(R.string.mx_timeline_app_installed, it.removePrefix(APP_INSTALLED_PREFIX)) }
        else -> null
    } ?: stored ?: timelineTypeName(context, event.type)
}

private const val APP_INSTALLED_PREFIX = "New app installed: "

/** Rebuilds "Charged 62% → 100% in 1h 40m" from the JSON the charge tracker stores beside it. */
private fun chargeLabel(context: Context, payload: String?): String? {
    if (payload.isNullOrBlank()) return null
    return try {
        val json = org.json.JSONObject(payload)
        val start = json.getInt("start").toString()
        val end = json.getInt("end").toString()
        val durationMs = json.getLong("duration_ms")
        val hours = durationMs / (60L * 60L * 1000L)
        val mins = (durationMs / (60L * 1000L)) % 60L
        if (hours > 0) {
            context.string(R.string.mx_timeline_charged_hours, start, end, hours.toString(), mins.toString())
        } else {
            context.string(R.string.mx_timeline_charged_minutes, start, end, mins.toString())
        }
    } catch (_: Exception) {
        null
    }
}

/** Shown only when an event has neither a label nor fields to build one from. */
private fun timelineTypeName(context: Context, type: String): String = when (type) {
    DeviceEvent.TYPE_SCORE_SCAN -> context.string(R.string.mx_timeline_type_score_scan)
    DeviceEvent.TYPE_CHARGE_SESSION -> context.string(R.string.mx_timeline_type_charge)
    DeviceEvent.TYPE_APP_INSTALLED -> context.string(R.string.mx_timeline_type_app)
    DeviceEvent.TYPE_BASELINE_SNAPSHOT -> context.string(R.string.mx_timeline_type_snapshot)
    else -> type
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
                text = context.string(R.string.timeline_last_charge),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (s.label == null) {
                    context.string(R.string.timeline_charge_recorded)
                } else {
                    timelineLabel(context, s)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
