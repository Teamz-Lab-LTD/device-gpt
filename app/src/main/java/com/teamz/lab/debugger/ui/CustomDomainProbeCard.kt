package com.teamz.lab.debugger.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.ui.theme.AppTheme
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.ui.theme.useThemeManager
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.NetworkReachabilityTester
import com.teamz.lab.debugger.utils.ReachabilityStatus
import com.teamz.lab.debugger.utils.RepeatedProbeResult
import kotlinx.coroutines.launch

/**
 * "Check a website" — probes a user-supplied domain [DEFAULT_PROBE_ATTEMPTS] times
 * and reports the pass count, never a bare green tick.
 *
 * Built after the 2026-08-25 InterviewBoss incident, where the app failed for ~20%
 * of users while Chrome worked on the same phone in the same second, and nothing on
 * the device could answer "is this reachable from HERE?". A single probe would have
 * shown green on most attempts, which is precisely why this runs four.
 *
 * PRIVACY: the typed domain is used for a DNS lookup and a HEAD request and nothing
 * else. It is never logged, never attached to an analytics event, and never
 * persisted. The analytics events below deliberately carry only the outcome shape
 * (pass count, status) — never the domain itself.
 */
@Composable
fun CustomDomainProbeCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val isDark = useThemeManager().getEffectiveTheme() == AppTheme.DESIGN_SYSTEM_DARK

    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<RepeatedProbeResult?>(null) }
    var attemptProgress by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    val attempts = NetworkReachabilityTester.DEFAULT_PROBE_ATTEMPTS

    fun run() {
        if (isRunning) return
        val domain = NetworkReachabilityTester.normaliseUserDomain(input)
        if (domain == null) {
            invalid = true
            result = null
            return
        }
        invalid = false
        keyboard?.hide()
        scope.launch {
            isRunning = true
            attemptProgress = 0
            result = null
            AnalyticsUtils.logEvent(AnalyticsEvent.CustomDomainProbeStarted)
            try {
                // Sequential on purpose — see probeDomainRepeated's KDoc. Progress is
                // driven per attempt so a 4x-slower test still feels alive.
                val perAttempt = mutableListOf<com.teamz.lab.debugger.utils.DomainProbeResult>()
                repeat(attempts) { i ->
                    attemptProgress = i + 1
                    perAttempt.add(
                        NetworkReachabilityTester.probeDomainRepeated(
                            domain = domain, category = "Custom", attempts = 1
                        ).perAttempt.first()
                    )
                }
                val agg = NetworkReachabilityTester.aggregateAttempts(domain, "Custom", perAttempt)
                result = agg
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.CustomDomainProbeCompleted,
                    mapOf(
                        // Outcome shape only — never the domain.
                        "success_count" to agg.successCount,
                        "attempts" to agg.attempts,
                        "intermittent" to agg.isIntermittent,
                        "status" to agg.overallStatus.name
                    )
                )
            } catch (_: Exception) {
                result = null
            } finally {
                isRunning = false
                attemptProgress = 0
            }
        }
    }

    AppCard(bottomPadding = 12) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.TravelExplore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.probe_custom_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.probe_custom_subtitle),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it; invalid = false },
                singleLine = true,
                enabled = !isRunning,
                isError = invalid,
                placeholder = { Text(stringResource(R.string.probe_custom_hint)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { run() }),
                modifier = Modifier.fillMaxWidth()
            )

            if (invalid) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.probe_invalid_domain),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { run() },
                enabled = !isRunning && input.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)   // touch target floor
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.probe_checking, attemptProgress, attempts),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        stringResource(R.string.probe_custom_button),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            result?.let { r ->
                Spacer(Modifier.height(16.dp))
                ProbeVerdict(r, isDark)

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(
                        stringResource(
                            if (showDetails) R.string.probe_hide_details
                            else R.string.probe_show_details
                        )
                    )
                }

                AnimatedVisibility(visible = showDetails) {
                    ProbeDetails(r)
                }

                ProbeFixActions(context, r)
            }
        }
    }
}

/**
 * Plain-language verdict. Colour is never the only signal — every state pairs an
 * icon and a sentence with the colour, per the accessibility rules.
 */
@Composable
private fun ProbeVerdict(r: RepeatedProbeResult, isDark: Boolean) {
    val (icon, tint, headline) = when {
        r.isIntermittent -> Triple(
            Icons.Filled.WarningAmber,
            // Amber literals match the sibling NetworkReachabilityCard's convention
            // rather than inventing a token. Light mode uses a darkened amber:
            // #FF9800 on white is ~2.1:1, under the 3:1 non-text contrast floor.
            if (isDark) Color(0xFFFF9800) else Color(0xFFB26A00),
            stringResource(R.string.probe_verdict_intermittent)
        )
        r.successCount == r.attempts && r.attempts > 0 -> Triple(
            Icons.Filled.CheckCircle,
            if (isDark) DesignSystemColors.NeonGreen else Color(0xFF1B7A3D),
            stringResource(R.string.probe_verdict_all_ok)
        )
        r.overallStatus == ReachabilityStatus.DNS_BLOCKED -> Triple(
            Icons.Filled.ErrorOutline,
            MaterialTheme.colorScheme.error,
            stringResource(R.string.probe_verdict_dns_blocked)
        )
        r.overallStatus == ReachabilityStatus.TLS_BLOCKED -> Triple(
            Icons.Filled.ErrorOutline,
            MaterialTheme.colorScheme.error,
            stringResource(R.string.probe_verdict_tls_blocked)
        )
        r.overallStatus == ReachabilityStatus.TCP_BLOCKED -> Triple(
            Icons.Filled.ErrorOutline,
            MaterialTheme.colorScheme.error,
            stringResource(R.string.probe_verdict_tcp_blocked)
        )
        else -> Triple(
            Icons.Filled.ErrorOutline,
            MaterialTheme.colorScheme.error,
            stringResource(R.string.probe_verdict_unreachable)
        )
    }

    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                headline,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            // The honest headline number — "3/4 OK, avg 240ms", never a bare tick.
            Text(
                r.summaryLine,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            val why = when {
                r.isIntermittent -> stringResource(
                    R.string.probe_verdict_intermittent_why,
                    r.attempts - r.successCount, r.attempts
                )
                r.successCount == r.attempts && r.attempts > 0 -> stringResource(
                    R.string.probe_verdict_ok_detail, r.attempts, r.avgLatencyMs.toInt()
                )
                r.overallStatus == ReachabilityStatus.DNS_BLOCKED ->
                    stringResource(R.string.probe_verdict_dns_why)
                else -> r.errorDetail.orEmpty()
            }
            if (why.isNotBlank()) {
                Text(
                    why,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Advanced panel — raw values, monospace, exact numbers and units. */
@Composable
private fun ProbeDetails(r: RepeatedProbeResult) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        r.perAttempt.forEachIndexed { i, a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.probe_attempt_label, i + 1),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp)
                )
                Text(
                    buildString {
                        append(a.overallStatus.name)
                        append("  ")
                        append(stringResource(R.string.probe_detail_dns))
                        append(' ').append(a.dnsLatencyMs).append("ms")
                        append("  ")
                        append(stringResource(R.string.probe_detail_https))
                        append(' ').append(a.httpsLatencyMs).append("ms")
                        a.httpsResponseCode?.let { append("  HTTP ").append(it) }
                        a.errorDetail?.let { append("  ").append(it) }
                    },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * One-tap deep links. Only shown when the corresponding condition is actually
 * present — a settings shortcut offered for a setting that is already fine is
 * noise that trains the user to ignore the section.
 */
@Composable
private fun ProbeFixActions(context: Context, r: RepeatedProbeResult) {
    if (r.successCount == r.attempts) return

    Column(Modifier.fillMaxWidth()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            FixButton(stringResource(R.string.probe_fix_private_dns)) {
                safeStartSettings(context, "android.settings.PRIVATE_DNS_SETTINGS")
            }
        }
        FixButton(stringResource(R.string.probe_fix_vpn)) {
            safeStartSettings(context, Settings.ACTION_VPN_SETTINGS)
        }
        FixButton(stringResource(R.string.probe_fix_wifi)) {
            safeStartSettings(context, Settings.ACTION_WIFI_SETTINGS)
        }
    }
}

@Composable
private fun FixButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(top = 6.dp)
    ) {
        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/**
 * Some OEM builds ship without a given settings activity. Launching blind throws
 * ActivityNotFoundException and crashes the tab; a dead button is bad, a crash is
 * worse. Falls back to the app's own settings page, which always resolves.
 */
private fun safeStartSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // Nothing further we can safely do; the verdict text still stands alone.
        }
    }
}
