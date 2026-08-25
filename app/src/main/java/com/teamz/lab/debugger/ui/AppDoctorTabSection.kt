package com.teamz.lab.debugger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamz.lab.debugger.R

/**
 * App Doctor tab — "can this phone reach a website, and if not, why".
 *
 * Release 1 hosts the website check. The per-app diagnosis (WebView version,
 * storage, permissions, battery restrictions) lands here in release 2.
 *
 * Why a tab rather than a card on Network Info: the reachability test buried there
 * measured 93% completion but only 9% discovery over 28 days — people who find it
 * finish it, and almost nobody finds it. GA4 28d to 2026-08-18 puts Network Info at
 * 18.0% reach, seventh of eight tabs.
 */
@Composable
fun AppDoctorTabSection(
    onItemAIClick: ((String, String) -> Unit)? = null,
    onShareClick: (String) -> Unit = {},
) {
    // The nav host's Share / AI / Cert FABs stay in FabLoading() — three blank squares —
    // until shareText moves off the "Loading…" placeholder. Same trap ScreenTestSection
    // documents. Publish a usable placeholder on entry; the probe replaces it with the
    // real report once one has run.
    LaunchedEffect(Unit) {
        onShareClick(
            "App Doctor — no website checked yet. Type an address and tap Check to " +
                "test whether this phone can reach it."
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(top = 12.dp)
            // Bottom inset clears the floating action buttons. At 24dp the last detail
            // row sat behind them and looked truncated on a real screen.
            .padding(bottom = 120.dp)
    ) {
        Text(
            stringResource(R.string.app_doctor_intro_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Text(
            stringResource(R.string.app_doctor_intro_body),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        CustomDomainProbeCard(
            onItemAIClick = onItemAIClick,
            onReportChanged = onShareClick,
        )
    }
}
