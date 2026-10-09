package com.teamz.lab.debugger.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamz.lab.debugger.R
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import com.teamz.lab.debugger.ui.components.ScoreRing
import com.teamz.lab.debugger.ui.components.ScoreRingNumber
import com.teamz.lab.debugger.ui.components.ScoreRingSession
import com.teamz.lab.debugger.ui.components.scoreTone
import com.teamz.lab.debugger.ui.components.scoreToneColor
import com.teamz.lab.debugger.ui.theme.DgSemanticColorsDark
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.ui.icons.DgText
import com.teamz.lab.debugger.ui.icons.DgIconText
import com.teamz.lab.debugger.ui.icons.DgIcons
import com.teamz.lab.debugger.ui.icons.IconTone

/**
 * v3.1.12 — closes the "one-shot FirstScanGate" gap.
 *
 * Previously: FirstScanGate fired ONCE on first launch (behind 50% RC gate).
 * User saw the 10s scan + 0-100 Device Score once, then it was gone forever.
 * The score + timestamp were saved in SharedPreferences but not surfaced
 * anywhere in the UI. Users who valued the score had no way to re-run it —
 * frustrating for them, wasted data for us.
 *
 * This card renders on the Health tab (top of hero area) when a user has
 * completed at least one first-scan. Shows:
 *   - Their last score with color-coded verdict
 *   - How long ago they scanned
 *   - "Run again" button → clears gate state → Activity.recreate() → gate re-fires
 *   - "Share" button → same viral share as first gate
 *
 * If [FirstScanGate.hasCompletedScan] is false this composable renders NOTHING —
 * the caller wraps it in a conditional check so the LazyColumn item is empty.
 * That preserves the pre-v3.1.12 Health tab layout for the RC control cohort
 * (users who never saw the gate).
 */
@Composable
fun LastScoreCard(
    onShareClick: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val score = remember { FirstScanGate.getLastScanScore(context) }
    val timestamp = remember { FirstScanGate.getLastScanTimestamp(context) }

    if (score < 0 || timestamp <= 0L) return

    // v3.2.0 R6: one data-backed insight per open (RC insight_per_open_enabled,
    // default OFF). Session-cached inside InsightEngine — no reroll farming.
    var insightLine by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.DeviceScoreCardViewed,
                mapOf("score" to score)
            )
        } catch (_: Throwable) { /* analytics not critical */ }
        try {
            insightLine = com.teamz.lab.debugger.utils.InsightEngine
                .insightForThisOpen(context)?.line
        } catch (_: Throwable) { /* insight optional */ }
    }

    val verdict = stringResource(verdictRes(score))
    val verdictColor = scoreToneColor(scoreTone(score))
    // The reveal plays once per session; after that the card shows its score at rest.
    val playReveal = remember { ScoreRingSession.firstTime("last_score") }
    val title = stringResource(R.string.first_scan_title)
    val (agoRes, agoCount) = timeAgoParts(timestamp)
    val agoLabel = stringResource(agoRes, agoCount)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    ScoreRing(
                        score = score,
                        size = 88.dp,
                        strokeWidth = 8.dp,
                        color = verdictColor,
                        animate = playReveal,
                        contentDescription = "$title: $score, $verdict",
                    ) { shown ->
                        ScoreRingNumber(
                            value = shown,
                            finalValue = score,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    DgText(
                        text = verdict,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = verdictColor
                    )
                    DgText(
                        text = agoLabel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            // v3.2.0 R6 insight line — renders only when real data supports it.
            insightLine?.let { line ->
                Spacer(Modifier.height(10.dp))
                DgIconText(
                    icon = DgIcons.Tip,
                    tone = IconTone.Accent,
                    text = line,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        FirstScanGate.clearForReplay(context)
                        (context as? Activity)?.recreate()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DgSemanticColorsDark.accent,
                        contentColor = DgSemanticColorsDark.onAccent
                    ),
                    shape = RoundedCornerShape(24.dp),
                    // Less side padding so the Bangla label stays on one line on a 320dp phone.
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.last_score_run_again),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                OutlinedButton(
                    onClick = {
                        try {
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.DeviceScoreCardShared,
                                mapOf("score" to score)
                            )
                        } catch (_: Throwable) { /* analytics not critical */ }
                        onShareClick(score)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.share), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** The verdict word as a string resource, so the card shows it in the app language. */
@StringRes
internal fun verdictRes(score: Int): Int = when {
    score >= 90 -> R.string.last_score_excellent
    score >= 75 -> R.string.last_score_good
    score >= 60 -> R.string.last_score_fair
    score >= 40 -> R.string.last_score_poor
    else -> R.string.last_score_critical
}

/** The verdict word in English, whatever the app language. Thresholds live in [verdictRes]. */
internal fun verdictFor(score: Int): String = when (verdictRes(score)) {
    R.string.last_score_excellent -> "Excellent"
    R.string.last_score_good -> "Good"
    R.string.last_score_fair -> "Fair"
    R.string.last_score_poor -> "Poor"
    else -> "Critical"
}

/**
 * How long ago [timestampMs] was, as a string resource and the number that goes into it
 * (0 for "just now", whose string takes no number).
 */
internal fun timeAgoParts(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): Pair<Int, Int> {
    val diffMs = (nowMs - timestampMs).coerceAtLeast(0L)
    val mins = diffMs / 60_000L
    val hours = mins / 60L
    val days = hours / 24L
    return when {
        mins < 1L -> R.string.time_ago_just_now to 0
        mins < 60L -> R.string.time_ago_minutes to mins.toInt()
        hours < 24L -> R.string.time_ago_hours to hours.toInt()
        days < 7L -> R.string.time_ago_days to days.toInt()
        days < 30L -> R.string.time_ago_weeks to (days / 7L).toInt()
        else -> R.string.time_ago_months to (days / 30L).toInt()
    }
}

/** The same label in English, whatever the app language. Thresholds live in [timeAgoParts]. */
internal fun timeAgoLabel(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val (res, count) = timeAgoParts(timestampMs, nowMs)
    return when (res) {
        R.string.time_ago_just_now -> "just now"
        R.string.time_ago_minutes -> "${count}m ago"
        R.string.time_ago_hours -> "${count}h ago"
        R.string.time_ago_days -> "${count}d ago"
        R.string.time_ago_weeks -> "${count}w ago"
        else -> "${count}mo ago"
    }
}
