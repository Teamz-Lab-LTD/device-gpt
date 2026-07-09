package com.teamz.lab.debugger.quality

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v3.2.0 growth program (2026-07-10 synthesis) — source-text regression guards.
 *
 * The Deceptive Behavior strike cleared 2026-07-10. These tests pin every
 * policy-critical deletion + honesty fix so a future PR cannot silently
 * re-introduce the strike class:
 *   1. Widget/monitor "optimize" vocabulary purged (R0)
 *   2. Streak-FOMO push machine disabled (insight #7)
 *   3. First scan is real — no fabricated fallback, no fixed theater loop (R1)
 *   4. Ad grace window enforced before interstitials
 *   5. RevenueCat offering resolution prefers offerings.current (Experiments fix)
 *   6. No hardcoded displayed prices (misleading-price guard)
 *   7. Watchdog uses launcher <queries>, never QUERY_ALL_PACKAGES (R4)
 *   8. Drawer share/GitHub interstitials stay deleted
 *   9. v3.2.0 RC flags stay in bundled defaults (dark-ship contract)
 */
class GrowthProgramV320ContractTest {

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) {
            dir = dir.parentFile
        }
        return dir
    }

    private fun read(path: String): String = File(projectRoot(), path).readText()

    // ---- 1. Vocab purge (R0) ---------------------------------------------------

    @Test
    fun `widget contains no optimize vocabulary or streak FOMO`() {
        val src = read("app/src/main/java/com/teamz/lab/debugger/widgets/LockScreenMonitorWidget.kt")
        assertFalse("Widget must not render 'Tap to optimize'", src.contains("\"Tap to optimize"))
        assertFalse("Widget must not render '✅ Optimized'", src.contains("✅ Optimized"))
        assertFalse("Widget must not render streak-days FOMO text", src.contains("\"🔥 1 day\""))
        assertTrue(
            "Widget must sanitize stale pref vocabulary on read (upgrade window guard)",
            src.contains("sanitizeLegacyVocab")
        )
    }

    @Test
    fun `monitor service CTA generator is optimize-free`() {
        val src = read("app/src/main/java/com/teamz/lab/debugger/services/system_monitor_service.kt")
        assertFalse(
            "generateCompellingCTA must not emit 'Tap to optimize'",
            src.contains("\"Tap to optimize")
        )
        assertFalse(
            "Alert generator must not emit streak-FOMO lines",
            src.contains("Don't break your")
        )
    }

    // ---- 2. Streak push machine disabled ----------------------------------------

    @Test
    fun `retention manager cancels time-driven FOMO workers on init`() {
        val src = read("app/src/main/java/com/teamz/lab/debugger/utils/RetentionNotificationManager.kt")
        assertTrue(
            "init must cancel DAILY_HEALTH_WORK for upgrading users",
            src.contains("cancelUniqueWork(DAILY_HEALTH_WORK)")
        )
        assertTrue(
            "init must cancel RETENTION_WORK ('we miss you' pushes)",
            src.contains("cancelUniqueWork(RETENTION_WORK)")
        )
        assertTrue(
            "init must cancel STREAK_REMINDER_WORK",
            src.contains("cancelUniqueWork(STREAK_REMINDER_WORK)")
        )
        assertFalse(
            "No streak-FOMO notification strings may remain",
            src.contains("streak alive") || src.contains("streak is at risk") || src.contains("Don't break your")
        )
        assertFalse(
            "init must NOT re-schedule the daily nag / retention / streak workers",
            src.contains("scheduleDailyHealthReminder(context)\n        scheduleWeeklyReport")
        )
    }

    // ---- 3. Honest first scan (R1) -----------------------------------------------

    @Test
    fun `first scan has no fabricated fallback and reads four real subsystems`() {
        val src = read("app/src/main/java/com/teamz/lab/debugger/ui/FirstScanGate.kt")
        assertFalse(
            "DEFAULT_FALLBACK_SCORE (the fabricated 72) must stay deleted — inventing " +
                "a score is the exact Deceptive Behavior class the strike was about.",
            src.contains("DEFAULT_FALLBACK_SCORE")
        )
        assertTrue("runQuickScan (real 4-check) must exist", src.contains("suspend fun runQuickScan"))
        assertTrue("battery CONDITION reader must exist", src.contains("readBatteryConditionScore"))
        assertTrue("memory reader must exist", src.contains("readMemoryScore"))
        assertTrue("storage reader must exist", src.contains("readStorageScore"))
        assertTrue("network reader must exist", src.contains("readNetworkScore"))
        assertTrue(
            "Disclosed weights must remain 40/25/20/15",
            src.contains("WEIGHT_BATTERY = 40") && src.contains("WEIGHT_MEMORY = 25") &&
                src.contains("WEIGHT_STORAGE = 20") && src.contains("WEIGHT_NETWORK = 15")
        )
        assertTrue(
            "markScanFailed must exist — unreadable device shows an error state, never a number",
            src.contains("fun markScanFailed")
        )
    }

    @Test
    fun `scan screen has no fixed-duration theater loop and renders a FAILED state`() {
        val src = read("app/src/main/java/com/teamz/lab/debugger/ui/FirstScanGateScreen.kt")
        assertFalse(
            "The fixed 100x100ms fake progress loop must stay deleted — progress " +
                "binds to actual check completion.",
            src.contains("for (i in 1..100)")
        )
        assertTrue("FAILED phase must exist", src.contains("Phase.FAILED"))
        assertTrue(
            "Result card must disclose the scoring weights",
            src.contains("WEIGHT_BATTERY") && src.contains("WEIGHT_NETWORK")
        )
    }

    // ---- 4. Ad grace window -------------------------------------------------------

    @Test
    fun `interstitial manager enforces the ads-grace session window`() {
        val src = read("app/src/main/java/com/teamz/lab/debugger/utils/interstitial_ad_manager.kt")
        assertTrue(
            "showAdBeforeAction must skip interstitials during RC ads_grace_sessions",
            src.contains("getAdsGraceSessions()")
        )
    }

    // ---- 5. Offering resolution priority ------------------------------------------

    @Test
    fun `offering resolution prefers offerings-current over the pinned ID`() {
        val mgr = read("app/src/main/java/com/teamz/lab/debugger/utils/RevenueCatManager.kt")
        val paywall = read("app/src/main/java/com/teamz/lab/debugger/ui/RevenueCatPaywall.kt")
        assertFalse(
            "RevenueCatManager must NOT resolve pinned ID first — that renders " +
                "RevenueCat Experiments + country-targeted offerings permanently dead.",
            mgr.contains("offerings.getOffering(OFFERING_ID) ?: offerings.current")
        )
        assertTrue(
            "RevenueCatManager must prefer offerings.current",
            mgr.contains("offerings.current ?: offerings.getOffering(OFFERING_ID)")
        )
        assertTrue(
            "RevenueCatPaywall must prefer offerings.current",
            paywall.contains("offerings.current")
        )
    }

    // ---- 6. No hardcoded displayed prices ------------------------------------------

    @Test
    fun `no UI file assigns a hardcoded dollar price as displayed fallback`() {
        val uiDir = File(projectRoot(), "app/src/main/java/com/teamz/lab/debugger/ui")
        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line ->
                    val code = line.substringBefore("//")
                    if (code.contains("mutableStateOf(\"\$2.99\")") ||
                        code.contains("productPrice = \"\$2.99\"") ||
                        code.contains("?: \"\$2.99\"")
                    ) "${f.name}:${i + 1}: $line" else null
                }.asIterable()
            }.toList()
        assertTrue(
            "Hardcoded '\$2.99' price fallbacks found — a wrong displayed price is a " +
                "misleading-price claim under localized/tiered pricing:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    // ---- 7. Watchdog permissions ----------------------------------------------------

    @Test
    fun `manifest uses launcher queries and never QUERY_ALL_PACKAGES`() {
        // Strip XML comments — the guard targets real permission declarations,
        // not the comment explaining why the permission is absent.
        val manifest = read("app/src/main/AndroidManifest.xml")
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        assertFalse(
            "QUERY_ALL_PACKAGES requires a Play declaration that will almost certainly " +
                "fail weeks after a strike — launcher <queries> covers the use case.",
            manifest.contains("QUERY_ALL_PACKAGES")
        )
        assertTrue(
            "Launcher-intent <queries> entry must exist for the new-app watchdog",
            manifest.contains("android.intent.action.MAIN") &&
                manifest.contains("android.intent.category.LAUNCHER")
        )
    }

    @Test
    fun `watchdog baselines silently on first run and caps notifications daily`() {
        val src = read("app/src/main/java/com/teamz/lab/debugger/utils/NewAppWatchdog.kt")
        assertTrue(
            "First run must ONLY save the baseline — never alert on pre-existing apps",
            src.contains("if (known == null)")
        )
        assertTrue("Daily notification cap must exist", src.contains("KEY_LAST_NOTIF_DAY"))
        // Verdict-vocabulary guard on CODE only (comments stripped) — the KDoc
        // legitimately documents which words are banned.
        val codeOnly = src.lines()
            .map { it.substringBefore("//") }
            .filterNot { it.trim().startsWith("*") || it.trim().startsWith("/*") }
            .joinToString("\n")
        listOf("spyware", "threat", "danger", "malware", "virus").forEach { verdict ->
            assertFalse(
                "Watchdog code must never emit the verdict word '$verdict' — " +
                    "permission-review facts only.",
                codeOnly.contains(verdict, ignoreCase = true)
            )
        }
    }

    // ---- 8. Drawer interstitials stay deleted -----------------------------------------

    @Test
    fun `drawer share and github actions carry no interstitial tax`() {
        val src = read("app/src/main/java/com/teamz/lab/debugger/ui/drawer.kt")
        assertFalse(
            "Drawer must not call showAdBeforeAction — the share-action interstitial " +
                "taxed the app's one free growth loop; the GitHub one punished trust.",
            src.contains("showAdBeforeAction")
        )
    }

    // ---- 9. Dark-ship RC contract --------------------------------------------------------

    @Test
    fun `all v320 features are RC-gated with bundled defaults`() {
        val rc = read("app/src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt")
        listOf(
            "paywall_delay_enabled", "paywall_min_sessions", "paywall_reason_routing_enabled",
            "ads_grace_sessions", "post_delight_ad_quiet_ms", "widget_v2_enabled",
            "charge_summary_enabled", "new_app_watchdog_enabled", "insight_per_open_enabled",
            "rewarded_report_enabled", "share_card_v2_enabled", "timeline_enabled",
            "widget_pin_prompt_enabled",
        ).forEach { key ->
            assertTrue(
                "RC key '$key' missing from bundled defaults — first-install cold start " +
                    "would couple behaviour to SDK internals.",
                rc.contains("\"$key\"")
            )
        }
    }

    @Test
    fun `rewarded offer only renders when an ad is actually loaded`() {
        val src = read("app/src/main/java/com/teamz/lab/debugger/ui/adaptive/DeviceGptNavExperience.kt")
        val offerIdx = src.indexOf("isRewardedReportEnabled()")
        assertTrue("Rewarded gate must exist in the Verified Report path", offerIdx > 0)
        val window = src.substring(offerIdx, minOf(src.length, offerIdx + 200))
        assertTrue(
            "Rewarded offer must check RewardedAdManager.isAdLoaded() — a no-fill " +
                "unlock button is a broken promise at 36.9% match rate.",
            window.contains("isAdLoaded()")
        )
    }

    @Test
    fun `charge delivery no longer relies on the dead manifest receiver alone`() {
        val tracker = read("app/src/main/java/com/teamz/lab/debugger/utils/ChargeCycleTracker.kt")
        assertTrue(
            "Runtime receiver registration must exist (manifest POWER receivers are " +
                "dead on API 26+)",
            tracker.contains("fun registerRuntimeReceiver")
        )
        assertTrue(
            "Dangling-session reconcile on app open must exist",
            tracker.contains("fun reconcileOnAppOpen")
        )
        val worker = read("app/src/main/java/com/teamz/lab/debugger/utils/ChargeStartWorker.kt")
        assertTrue(
            "Charge-start worker must use setRequiresCharging(true) — the only " +
                "OS-sanctioned app-dead wake on charge; no FGS.",
            worker.contains("setRequiresCharging(true)")
        )
        assertFalse(
            "Charge path must not start a foreground service",
            worker.contains("startForegroundService")
        )
    }

    @Test
    fun `insight engine uses own-baseline templates only`() {
        val src = read("app/src/main/java/com/teamz/lab/debugger/utils/InsightEngine.kt")
        assertFalse(
            "Insights must never claim cross-user percentiles — no data source exists",
            src.contains("percentile", ignoreCase = true) && src.contains("top ", ignoreCase = true)
        )
        assertTrue(
            "Session cache must exist (no reroll farming)",
            src.contains("KEY_SESSION_ID")
        )
        assertTrue(
            "Engine must return null when no data supports a template — never invent",
            src.contains("return null // no data supports any template")
        )
    }
}
