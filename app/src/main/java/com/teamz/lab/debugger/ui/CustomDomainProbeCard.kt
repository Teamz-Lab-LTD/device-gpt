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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.ui.theme.AppTheme
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.ui.theme.useThemeManager
import com.teamz.lab.debugger.utils.AIIcon
import com.teamz.lab.debugger.utils.AppDoctorContext
import com.teamz.lab.debugger.utils.AppDoctorReportHolder
import com.teamz.lab.debugger.utils.WebViewStackProbe
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
fun CustomDomainProbeCard(
    activity: android.app.Activity? = null,
    onItemAIClick: ((String, String) -> Unit)? = null,
    onReportChanged: (String) -> Unit = {},
) {
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
    // Device-wide network context, captured at probe time. These are the settings that
    // actually explain a failure ("DNS blocked" means little without knowing Private DNS
    // is on), and they were the exact unknowns during the 2026-08-25 incident.
    var dnsServers by remember { mutableStateOf<String?>(null) }
    var privateDns by remember { mutableStateOf<Boolean?>(null) }
    var vpnOn by remember { mutableStateOf<Boolean?>(null) }
    // Captive portal: cafe/hotel/office wifi that wants a sign-in first. A very common
    // cause of "the site is down" that has nothing to do with the site.
    var captivePortal by remember { mutableStateOf<Boolean?>(null) }
    // Chromium-stack result. Kept separate from `result` on purpose: the two rows must
    // be shown side by side, because their DISAGREEMENT is the diagnosis.
    var webResult by remember { mutableStateOf<WebViewStackProbe.Aggregate?>(null) }
    var webPackage by remember { mutableStateOf<Pair<String, String>?>(null) }
    var transportLabel by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf(0) }   // 0 idle, 1 java, 2 webview

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
            phase = 1
            result = null
            webResult = null
            AnalyticsUtils.logEvent(AnalyticsEvent.CustomDomainProbeStarted)
            try {
                // Sequential on purpose — see probeDomainRepeated's KDoc. Progress comes from
                // its onAttempt callback: driving it from an outer repeat() loop of
                // attempts = 1 calls, as this did until 2026-09-09, skipped ATTEMPT_GAP_MS
                // entirely and left attempts 2..4 measuring the connection pool.
                val agg = NetworkReachabilityTester.probeDomainRepeated(
                    domain = domain,
                    category = "Custom",
                    attempts = attempts,
                    onAttempt = { n -> attemptProgress = n },
                )
                result = agg
                dnsServers = try {
                    com.teamz.lab.debugger.utils.getDnsServers(context)
                } catch (_: Exception) { null }
                privateDns = try {
                    NetworkReachabilityTester.isPrivateDnsEnabled(context)
                } catch (_: Exception) { null }
                vpnOn = try {
                    NetworkReachabilityTester.isVpnActive(context)
                } catch (_: Exception) { null }
                captivePortal = try {
                    NetworkReachabilityTester.checkCaptivePortalPublic()
                } catch (_: Exception) { null }
                transportLabel = try {
                    AppDoctorContext.readTransport(context).label
                } catch (_: Exception) { null }
                webPackage = WebViewStackProbe.currentWebViewPackage()

                // Second stack. Chromium has its own resolver, socket pool and TLS —
                // a Java-stack pass does NOT mean a WebView-shell app can load the site.
                phase = 2
                val web = try {
                    WebViewStackProbe.probeRepeated(context, domain)
                } catch (_: Exception) { null }
                webResult = web

                val report = NetworkReachabilityTester.buildProbeReport(
                    agg, dnsServers, privateDns, vpnOn,
                    webView = web,
                    webViewPackage = webPackage,
                    transportLabel = transportLabel,
                    captivePortal = captivePortal
                )
                // Hand the report up. The nav host's Share / AI / Cert FABs sit in
                // FabLoading() until shareText moves off the "Loading…" placeholder — the
                // same trap documented in ScreenTestSection. Without this the FABs render
                // as three blank squares forever. Also feeds the global AI export.
                AppDoctorReportHolder.latest = report
                onReportChanged(report)
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
                phase = 0
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
                Column(Modifier.weight(1f)) {
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
                // Per-item AI hand-off, same shape as ScreenTestSection. Only offered once
                // there is a result — an "Ask AI" with nothing to ask about is a dead button.
                result?.let { r ->
                    if (onItemAIClick != null) {
                        IconButton(
                            modifier = Modifier.size(40.dp),
                            onClick = {
                                onItemAIClick(
                                    "Website Check",
                                    NetworkReachabilityTester.buildProbeReport(
                                        r, dnsServers, privateDns, vpnOn,
                                        webView = webResult,
                                        webViewPackage = webPackage,
                                        transportLabel = transportLabel,
                                        captivePortal = captivePortal
                                    )
                                )
                            }
                        ) {
                            Icon(
                                AIIcon.icon,
                                contentDescription = "Ask AI",
                                tint = AIIcon.color()
                            )
                        }
                    }
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
                onClick = {
                    val act = activity
                    if (act != null) {
                        com.teamz.lab.debugger.utils.InterstitialAdManager.showAdBeforeAction(
                            act, "app_doctor_probe"
                        ) { run() }
                    } else {
                        run()
                    }
                },
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
                        if (phase == 2) stringResource(R.string.probe_checking_webview)
                        else stringResource(R.string.probe_checking, attemptProgress, attempts),
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
                StackComparisonRows(r, webResult)

                Spacer(Modifier.height(8.dp))
                ProbeNetworkContext(
                    dnsServers, privateDns, vpnOn, captivePortal,
                    transportLabel, webPackage
                )

                Spacer(Modifier.height(4.dp))
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

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val report = NetworkReachabilityTester.buildProbeReport(
                            r, dnsServers, privateDns, vpnOn,
                            webView = webResult,
                            webViewPackage = webPackage,
                            transportLabel = transportLabel,
                            captivePortal = captivePortal
                        )
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as? android.content.ClipboardManager
                        cm?.setPrimaryClip(
                            android.content.ClipData.newPlainText("DeviceGPT website check", report)
                        )
                        // Android 13+ shows its own copy confirmation; a second toast there
                        // would be a duplicate the user has to dismiss.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.probe_copied),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.probe_copy_report))
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

/**
 * The two stacks, side by side.
 *
 * This is the point of the whole feature. The Java row and the Chromium row can and do
 * disagree, and the disagreement is the finding — a WebView-shell app failing while
 * every other check on the phone reports the site as healthy. Showing only the Java row
 * would present a green result and hide the exact bug this was built to catch.
 */
@Composable
private fun StackComparisonRows(
    java: RepeatedProbeResult,
    web: WebViewStackProbe.Aggregate?
) {
    val verdict = AppDoctorContext.compareStacks(
        java.successCount,
        web?.successCount,
        java.perAttempt.firstNotNullOfOrNull { it.httpsResponseCode },
    )
    Column(Modifier.fillMaxWidth()) {
        StackRow(stringResource(R.string.probe_stack_java), java.summaryLine, java.successCount > 0)
        StackRow(
            stringResource(R.string.probe_stack_webview),
            web?.summaryLine ?: "—",
            web != null && web.successCount > 0
        )
        if (verdict == AppDoctorContext.StackVerdict.WEBVIEW_ONLY_FAILS) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.probe_stack_disagree),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StackRow(label: String, value: String, ok: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon + text, never colour alone — the accessibility rule this codebase follows.
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.6f)
        )
    }
}

/**
 * Device-wide network context. Shown with every result, not only failures: "4/4 OK"
 * still leaves "…but through which DNS, and is a VPN in the path?" unanswered, and
 * those were the exact unknowns nobody could resolve during the 2026-08-25 incident.
 *
 * Values are device-wide by definition — Android exposes no per-app DNS.
 */
@Composable
private fun ProbeNetworkContext(
    dnsServers: String?,
    privateDns: Boolean?,
    vpnOn: Boolean?,
    captivePortal: Boolean?,
    transportLabel: String?,
    webPackage: Pair<String, String>?
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        ContextRow(
            stringResource(R.string.probe_context_transport),
            transportLabel ?: "unknown"
        )
        ContextRow(stringResource(R.string.probe_detail_servers), dnsServers ?: "unknown")
        // The System WebView build is THE field for a web-view-shell app: a stale or
        // swapped provider explains failures that otherwise look like server problems.
        ContextRow(
            stringResource(R.string.probe_context_webview),
            webPackage?.let { "${it.second}" } ?: "unknown"
        )
        privateDns?.let {
            ContextRow(
                stringResource(R.string.probe_context_private_dns),
                if (it) "on" else "off"
            )
        }
        vpnOn?.let {
            ContextRow(stringResource(R.string.probe_context_vpn), if (it) "on" else "off")
        }
        // Only surfaced when true: "captive portal: no" on every normal network is noise
        // that trains the reader to skip the whole block.
        if (captivePortal == true) {
            ContextRow(stringResource(R.string.probe_context_captive), "yes")
        }
    }
}

@Composable
private fun ContextRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f)
        )
    }
}

/**
 * Advanced panel — raw values, monospace, exact numbers and units.
 *
 * One compact line per attempt. An earlier version used a fixed-width label column plus
 * a long value string, which wrapped mid-value on a real screen and left "HTTP 200"
 * orphaned on its own line, misaligned with its own label. Abbreviated and single-line
 * is the fix; the full unabbreviated values live in "Copy full report".
 */
@Composable
private fun ProbeDetails(r: RepeatedProbeResult) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        r.perAttempt.forEachIndexed { i, a ->
            val ok = a.overallStatus == ReachabilityStatus.REACHABLE
            val line = buildString {
                append(i + 1).append("  ")
                append(if (ok) "OK  " else "FAIL")
                append("  dns ").append(a.dnsLatencyMs).append("ms")
                if (ok) {
                    append("  tls ").append(a.httpsLatencyMs).append("ms")
                    a.httpsResponseCode?.let { append("  ").append(it) }
                }
            }
            Text(
                line,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                color = if (ok) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 3.dp)
            )
            // The failure reason gets its own wrapping line — it is the one value here
            // worth reading in full, so it must never be truncated to fit a column.
            if (!ok) {
                a.errorDetail?.let {
                    Text(
                        "     $it",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
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
