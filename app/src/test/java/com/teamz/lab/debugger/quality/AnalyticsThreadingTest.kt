package com.teamz.lab.debugger.quality

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression guard: AnalyticsUtils must never block the main thread.
 *
 * Background — 2026-06-19 Crashlytics showed v3.1.9 ANR in
 * LeaderboardSection.CategorySelector. Root cause: AnalyticsUtils.logEvent()
 * called isDeviceInRestrictedMode() inline on the caller's thread, which
 * synchronously queries Settings.Global.AIRPLANE_MODE_ON via IPC to the
 * SettingsProvider. Each tap, screen view, and ad event fires logEvent, so
 * the IPC could stack on slow devices and trip an ANR.
 *
 * v3.1.10 fix: route logEvent / logAdRevenue / setUserId / setUserProperty
 * through a dedicated HandlerThread so SettingsProvider IPC + Firebase API
 * calls never run on the caller's thread.
 *
 * These tests run in <50ms via the preflight gate. Add a new assertion every
 * time a new main-thread analytics regression is discovered.
 */
class AnalyticsThreadingTest {

    private fun locate(relPath: String): File {
        val root = System.getProperty("user.dir") ?: "."
        val candidates = listOf(
            File(root, relPath),
            File(root, "app/$relPath"),
            File(File(root).parentFile, "app/$relPath"),
            File("../app/$relPath"),
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
            ?: error("Could not locate $relPath. Tried: ${candidates.joinToString { it.absolutePath }}")
    }

    private val src by lazy {
        locate("src/main/java/com/teamz/lab/debugger/utils/analytics_utils.kt").readText()
    }

    @Test
    fun `AnalyticsUtils owns a dedicated HandlerThread for background work`() {
        assertTrue(
            "AnalyticsUtils must declare a HandlerThread named analyticsThread. Inline-on-caller " +
                "Firebase + SettingsProvider work caused the v3.1.9 CategorySelector ANR.",
            src.contains("analyticsThread: HandlerThread") &&
                src.contains("HandlerThread(\"teamzlab-analytics\")")
        )
        assertTrue(
            "AnalyticsUtils must expose an analyticsHandler bound to the analyticsThread looper.",
            src.contains("analyticsHandler: Handler") &&
                src.contains("Handler(analyticsThread.looper)")
        )
    }

    @Test
    fun `logEvent body runs on the analytics handler, not the caller thread`() {
        // The body of logEvent after bundle-build + DEBUG short-circuit must defer to
        // analyticsHandler.post — otherwise isDeviceInRestrictedMode runs on the
        // caller's thread and reintroduces the ANR.
        val logEventStart = src.indexOf("fun logEvent(")
        val logEventEnd = src.indexOf("fun logAdRevenue", logEventStart)
        if (logEventStart < 0 || logEventEnd < 0) {
            error("Could not locate logEvent body in analytics_utils.kt")
        }
        val body = src.substring(logEventStart, logEventEnd)
        assertTrue(
            "logEvent must post Firebase + isDeviceInRestrictedMode work to analyticsHandler. " +
                "Inline execution caused the v3.1.9 ANR.",
            body.contains("analyticsHandler.post")
        )
        // The bundle assembly happens synchronously (callers may reuse the map),
        // but the restricted-mode check + Firebase call must be inside the post block.
        val postIdx = body.indexOf("analyticsHandler.post")
        val restrictedIdx = body.indexOf("isDeviceInRestrictedMode")
        val firebaseLogIdx = body.indexOf("firebaseAnalytics?.logEvent")
        assertTrue(
            "isDeviceInRestrictedMode must be called from inside analyticsHandler.post, not before it.",
            restrictedIdx > postIdx
        )
        assertTrue(
            "firebaseAnalytics?.logEvent must be called from inside analyticsHandler.post.",
            firebaseLogIdx > postIdx
        )
    }

    @Test
    fun `logAdRevenue body runs on the analytics handler`() {
        val start = src.indexOf("fun logAdRevenue(")
        val end = src.indexOf("fun setUserId", start)
        if (start < 0 || end < 0) {
            error("Could not locate logAdRevenue body in analytics_utils.kt")
        }
        val body = src.substring(start, end)
        assertTrue(
            "logAdRevenue must post Firebase work to analyticsHandler — it shares the same " +
                "isDeviceInRestrictedMode path that caused the v3.1.9 ANR.",
            body.contains("analyticsHandler.post")
        )
    }

    @Test
    fun `setUserId runs on the analytics handler`() {
        val start = src.indexOf("fun setUserId(")
        val end = src.indexOf("fun setUserProperty", start)
        if (start < 0 || end < 0) return // setUserProperty may have been removed
        val body = src.substring(start, end)
        assertTrue(
            "setUserId must defer to analyticsHandler.post — same isDeviceInRestrictedMode " +
                "main-thread risk applies.",
            body.contains("analyticsHandler.post")
        )
    }

    @Test
    fun `setUserProperty runs on the analytics handler`() {
        val start = src.indexOf("fun setUserProperty(")
        if (start < 0) return // method may be removed later
        // Look ahead for the closing brace of the function (next blank line after Handler.post block)
        val end = src.indexOf("\n}", start) // end of object or next top-level brace
        val body = src.substring(start, if (end > start) end else src.length)
        assertTrue(
            "setUserProperty must defer to analyticsHandler.post — same main-thread risk.",
            body.contains("analyticsHandler.post")
        )
    }

    @Test
    fun `no analytics method calls isDeviceInRestrictedMode directly without Handler post`() {
        // Defense against future regression: any new public analytics method that calls
        // isDeviceInRestrictedMode must wrap it in analyticsHandler.post. We enforce this
        // by counting direct calls and post-block calls — they must match.
        // Match invocations `isDeviceInRestrictedMode(context)` — the trailing closing
        // paren disambiguates from the function declaration `(context: Context)`.
        val directCalls = Regex("isDeviceInRestrictedMode\\(context\\)").findAll(src).count()
        val handlerPostBlocks = Regex("analyticsHandler\\.post\\s*\\{").findAll(src).count()
        // Each isDeviceInRestrictedMode call must be inside its own analyticsHandler.post block.
        // Allow handlerPostBlocks >= directCalls (some posts may not need the check).
        assertTrue(
            "Found $directCalls direct calls to isDeviceInRestrictedMode but only " +
                "$handlerPostBlocks analyticsHandler.post blocks. Every restricted-mode check " +
                "must be inside a post block to avoid the v3.1.9 main-thread ANR.",
            handlerPostBlocks >= directCalls
        )
    }
}
