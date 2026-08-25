package com.teamz.lab.debugger.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager

/**
 * Device-side context for App Doctor, plus the pure logic that turns two probe
 * results into a verdict.
 *
 * Everything here degrades to "unknown" rather than throwing or omitting a row: a
 * silently missing line reads as "fine" to someone scanning a report, which is the
 * opposite of what a diagnostic should do.
 */
object AppDoctorContext {

    data class Transport(
        val wifi: Boolean,
        val cellular: Boolean,
        val vpn: Boolean,
        val carrier: String?
    ) {
        /** e.g. "Mobile data (Grameenphone)", "Wi-Fi", "Wi-Fi + VPN", "unknown". */
        val label: String
            get() {
                val parts = buildList {
                    if (wifi) add("Wi-Fi")
                    if (cellular) add(
                        if (carrier.isNullOrBlank()) "Mobile data"
                        else "Mobile data ($carrier)"
                    )
                    if (vpn) add("VPN")
                }
                return if (parts.isEmpty()) "unknown" else parts.joinToString(" + ")
            }
    }

    /**
     * Reads the ACTIVE network's transports. Users reported the 2026-08-25 failure on
     * "mobile data and wifi both" and nobody could confirm which, because only
     * TRANSPORT_VPN was ever read.
     */
    fun readTransport(context: Context): Transport {
        var wifi = false
        var cellular = false
        var vpn = false
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            if (caps != null) {
                wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
        } catch (_: Throwable) { /* leave all false -> label reads "unknown" */ }
        return Transport(wifi, cellular, vpn, readCarrier(context))
    }

    /**
     * Carrier name. READ_PHONE_STATE is declared, but the permission may still be
     * denied at runtime, and on Wi-Fi-only tablets there is no telephony service at
     * all — both must return null, never crash and never fabricate a name.
     */
    fun readCarrier(context: Context): String? = try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        tm?.networkOperatorName?.takeIf { it.isNotBlank() }
    } catch (_: SecurityException) {
        null
    } catch (_: Throwable) {
        null
    }

    // ---- Pure verdict logic (unit-tested without a device) ---------------------

    enum class StackVerdict {
        /** Both stacks reach it. Nothing to see. */
        BOTH_OK,
        /** Neither reaches it — a genuine reachability problem for this phone. */
        BOTH_FAIL,
        /**
         * Java says fine, Chromium does not. THE diagnostic case: a WebView-shell app
         * fails while every other tool on the phone says the site is up.
         */
        WEBVIEW_ONLY_FAILS,
        /** Chromium succeeds where the Java stack fails — usually proxy/PAC or SNI. */
        JAVA_ONLY_FAILS,
        /** One side was not measured. */
        INCOMPLETE
    }

    /**
     * Compare the two stacks. Pure so the decision is testable without a network.
     *
     * "Reached" means at least one attempt succeeded — an intermittent pass still
     * proves the path exists, and intermittency is reported separately by each
     * aggregate's own summary line.
     */
    fun compareStacks(
        javaSuccessCount: Int?,
        webViewSuccessCount: Int?
    ): StackVerdict {
        if (javaSuccessCount == null || webViewSuccessCount == null) return StackVerdict.INCOMPLETE
        val javaOk = javaSuccessCount > 0
        val webOk = webViewSuccessCount > 0
        return when {
            javaOk && webOk -> StackVerdict.BOTH_OK
            javaOk && !webOk -> StackVerdict.WEBVIEW_ONLY_FAILS
            !javaOk && webOk -> StackVerdict.JAVA_ONLY_FAILS
            else -> StackVerdict.BOTH_FAIL
        }
    }
}
