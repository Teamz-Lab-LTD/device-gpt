package com.teamz.lab.debugger.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Probes a domain through the CHROMIUM stack instead of the Java one.
 *
 * Why this exists, and why the Java probe alone is not enough: Chromium's WebView
 * does not use `InetAddress.getByName()` and does not use `HttpsURLConnection`. It
 * has its own DNS resolver, its own socket pool, its own TLS stack and its own
 * proxy/cache handling. So [NetworkReachabilityTester] can legitimately report
 * "4/4 OK" for a domain that a WebView-shell app still cannot load — which is
 * exactly the 2026-08-25 InterviewBoss shape, where Chrome worked while the app
 * failed on the same phone in the same second.
 *
 * A disagreement between the two stacks IS the diagnosis. Presenting only the Java
 * row would let a green result hide the bug this feature was built to find.
 */
object WebViewStackProbe {

    /** Per-attempt ceiling. A hung load must never wedge the UI. */
    const val DEFAULT_TIMEOUT_MS = 12_000L

    /** Mirrors the Java probe so the two rows are comparable at a glance. */
    const val DEFAULT_ATTEMPTS = NetworkReachabilityTester.DEFAULT_PROBE_ATTEMPTS

    enum class Outcome { OK, ERROR, HTTP_ERROR, SSL_ERROR, TIMEOUT, UNSUPPORTED }

    data class Attempt(
        val outcome: Outcome,
        val errorCode: Int?,
        val description: String?,
        val elapsedMs: Long
    ) {
        val ok: Boolean get() = outcome == Outcome.OK
    }

    data class Aggregate(
        val domain: String,
        val attempts: Int,
        val successCount: Int,
        val avgLatencyMs: Long,
        val firstFailure: Attempt?,
        val perAttempt: List<Attempt>
    ) {
        val isIntermittent: Boolean get() = successCount in 1 until attempts
        val allFailed: Boolean get() = attempts > 0 && successCount == 0

        val summaryLine: String
            get() = when {
                attempts == 0 -> "Not tested"
                successCount == 0 -> "0/$attempts OK" +
                    (firstFailure?.description?.let { " · $it" } ?: "")
                else -> "$successCount/$attempts OK, avg ${avgLatencyMs}ms"
            }
    }

    /**
     * The System WebView implementation this device actually runs. For a WebView-shell
     * app this is the single most diagnostic field on the screen: a stale or swapped
     * provider explains failures that look like server problems.
     *
     * `getCurrentWebViewPackage()` is API 26+. On 24-25 this returns null and callers
     * must render "unknown" rather than omit the row.
     */
    @SuppressLint("ObsoleteSdkInt")
    fun currentWebViewPackage(): Pair<String, String>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return try {
            webViewPackageApi26()
        } catch (_: Throwable) {
            // Some OEM builds and stripped emulator images throw here rather than
            // returning null. An unknown provider must not take the tab down.
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun webViewPackageApi26(): Pair<String, String>? {
        val pkg = WebView.getCurrentWebViewPackage() ?: return null
        return pkg.packageName to (pkg.versionName ?: "unknown")
    }

    /**
     * Load [domain] in an offscreen WebView [attempts] times and aggregate.
     *
     * Runs on the MAIN thread — WebView construction and teardown are main-thread-only
     * and crash otherwise. Every attempt is bounded by [timeoutMs] and the instance is
     * destroyed in a `finally`, so a hung load leaks neither the WebView nor its
     * renderer process.
     *
     * A fresh WebView per attempt, with its cache cleared, keeps attempts independent;
     * reusing one instance would serve attempts 2..n from the Chromium cache and mask
     * exactly the intermittency being hunted.
     */
    suspend fun probeRepeated(
        context: Context,
        domain: String,
        attempts: Int = DEFAULT_ATTEMPTS,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Aggregate = withContext(Dispatchers.Main) {
        val safeAttempts = attempts.coerceIn(1, 10)
        val results = ArrayList<Attempt>(safeAttempts)
        repeat(safeAttempts) {
            results.add(probeOnce(context, domain, timeoutMs))
        }
        aggregate(domain, results)
    }

    /** Pure aggregation, split out so the maths is testable without a WebView. */
    fun aggregate(domain: String, results: List<Attempt>): Aggregate {
        val ok = results.filter { it.ok }
        val lat = ok.map { it.elapsedMs }
        return Aggregate(
            domain = domain,
            attempts = results.size,
            successCount = ok.size,
            avgLatencyMs = if (lat.isEmpty()) 0L else lat.sum() / lat.size,
            firstFailure = results.firstOrNull { !it.ok },
            perAttempt = results
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun probeOnce(
        context: Context,
        domain: String,
        timeoutMs: Long
    ): Attempt = withContext(Dispatchers.Main) {
        val start = SystemClock.elapsedRealtime()
        var web: WebView? = null
        try {
            val outcome = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val w = try {
                        WebView(context)
                    } catch (_: Throwable) {
                        // Devices with the WebView provider disabled or mid-update.
                        if (cont.isActive) {
                            cont.resume(
                                Attempt(Outcome.UNSUPPORTED, null,
                                    "System WebView unavailable on this device", 0L)
                            )
                        }
                        return@suspendCancellableCoroutine
                    }
                    web = w
                    w.settings.apply {
                        javaScriptEnabled = false      // reachability only; no page logic
                        loadsImagesAutomatically = false
                        blockNetworkImage = true
                        cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                    }
                    w.clearCache(true)
                    w.webViewClient = object : WebViewClient() {
                        private var settled = false
                        private fun settle(a: Attempt) {
                            if (settled) return
                            settled = true
                            if (cont.isActive) cont.resume(a)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            settle(Attempt(Outcome.OK, null, null,
                                SystemClock.elapsedRealtime() - start))
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            // Subresource failures are noise — a blocked tracker is not
                            // the site being unreachable. Only the main frame counts.
                            if (request?.isForMainFrame != true) return
                            settle(
                                Attempt(
                                    Outcome.ERROR,
                                    error?.errorCode,
                                    error?.description?.toString() ?: "load failed",
                                    SystemClock.elapsedRealtime() - start
                                )
                            )
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            if (request?.isForMainFrame != true) return
                            settle(
                                Attempt(
                                    Outcome.HTTP_ERROR,
                                    errorResponse?.statusCode,
                                    "HTTP ${errorResponse?.statusCode ?: "error"}",
                                    SystemClock.elapsedRealtime() - start
                                )
                            )
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: android.net.http.SslError?
                        ) {
                            // NEVER proceed() — that would make the probe report a site
                            // as reachable while silently accepting a bad certificate,
                            // and would teach a diagnostic tool to lie about TLS.
                            handler?.cancel()
                            settle(
                                Attempt(
                                    Outcome.SSL_ERROR,
                                    error?.primaryError,
                                    "Certificate rejected (code ${error?.primaryError})",
                                    SystemClock.elapsedRealtime() - start
                                )
                            )
                        }
                    }
                    cont.invokeOnCancellation { /* teardown happens in finally */ }
                    w.loadUrl("https://$domain/")
                }
            }
            outcome ?: Attempt(
                Outcome.TIMEOUT, null,
                "No response within ${timeoutMs / 1000}s",
                SystemClock.elapsedRealtime() - start
            )
        } finally {
            // Main thread already; stop first so a in-flight load cannot fire a callback
            // into a destroyed instance.
            web?.let {
                try {
                    it.stopLoading()
                    it.webViewClient = WebViewClient()
                    it.destroy()
                } catch (_: Throwable) { /* teardown must never surface to the UI */ }
            }
        }
    }
}
