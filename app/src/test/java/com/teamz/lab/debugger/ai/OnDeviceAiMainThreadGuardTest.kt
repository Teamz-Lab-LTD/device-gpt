package com.teamz.lab.debugger.ai

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-text guards for the 2026-07-10 "AI chooser freezes, Cancel does nothing" ANR.
 *
 * Root cause: OnDeviceAiAvailability.refreshStatus() was a plain `suspend fun` with no
 * dispatcher of its own, so it inherited the caller's. Two UI call sites reach it from
 * Dispatchers.Main (LaunchedEffect in ai_assistant_dialog.kt, rememberCoroutineScope in
 * PrivateAiFlow.kt). It transitively calls Summarizer.checkFeatureStatus().get() — an
 * unbounded block on AICore IPC. Main thread wedged => taps, including Cancel, never
 * dispatched => the whole app looks frozen.
 *
 * These are text assertions because the real fix lives in dispatcher/timeout choices that
 * a JVM unit test cannot exercise without a device and a live AICore.
 */
class OnDeviceAiMainThreadGuardTest {

    private fun src(path: String): String {
        val f = File("src/main/java/com/teamz/lab/debugger/$path")
        assertTrue("missing source file: ${f.path}", f.exists())
        // Strip comments so our own explanatory prose cannot satisfy an assertion.
        return f.readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .joinToString("\n")
    }

    @Test
    fun `refreshStatus pins itself to IO so Main-thread callers cannot block`() {
        val s = src("ai/ondevice/OnDeviceAiAvailability.kt")
        val decl = Regex("suspend fun refreshStatus\\([^)]*\\)[^{=]*=\\s*withContext\\(Dispatchers\\.IO\\)")
        assertTrue(
            "refreshStatus must be wrapped in withContext(Dispatchers.IO) — it blocks on AICore IPC",
            decl.containsMatchIn(s)
        )
    }

    @Test
    fun `AICore status probe uses a bounded get`() {
        val s = src("ai/ondevice/PrivateAiExplainer.kt")
        assertFalse(
            "checkFeatureStatus().get() with no timeout can wedge a thread forever",
            Regex("checkFeatureStatus\\(\\)\\.get\\(\\s*\\)").containsMatchIn(s)
        )
        assertTrue(
            "checkFeatureStatus().get(...) must take a timeout",
            Regex("checkFeatureStatus\\(\\)\\.get\\(\\s*PROBE_TIMEOUT_SECONDS").containsMatchIn(s)
        )
        assertTrue(
            "a TimeoutException from the probe must be caught, not propagated",
            s.contains("catch (timeout: TimeoutException)")
        )
    }

    @Test
    fun `no unbounded get() survives anywhere in the on-device AI path`() {
        val s = src("ai/ondevice/PrivateAiExplainer.kt")
        val unbounded = Regex("\\.get\\(\\s*\\)").findAll(s).count()
        assertTrue(
            "found $unbounded unbounded .get() call(s); every AICore future must take a timeout",
            unbounded == 0
        )
        assertTrue("runInference must be bounded", s.contains("INFERENCE_TIMEOUT_SECONDS"))
        assertTrue("downloadFeature must be bounded", s.contains("DOWNLOAD_TIMEOUT_MINUTES"))
    }

    @Test
    fun `AI chooser resolves installed packages off the main thread`() {
        val s = src("ui/ai_assistant_dialog.kt")
        // getPackageInfo must not sit inside a remember{} block (composition == main thread).
        val rememberBlock = Regex(
            "val installedApps\\s*=\\s*remember\\([^)]*\\)\\s*\\{",
            RegexOption.DOT_MATCHES_ALL
        )
        assertFalse(
            "installedApps must not be computed inside remember{} — 9 binder calls on Main",
            rememberBlock.containsMatchIn(s)
        )
        assertTrue(
            "installedApps must be resolved in withContext(Dispatchers.IO)",
            s.contains("withContext(Dispatchers.IO)") && s.contains("getPackageInfo")
        )
    }

    @Test
    fun `Application warm-up probe stays off the main thread`() {
        val s = src("Application.kt")
        assertTrue(
            "the startup AI probe must launch on Dispatchers.IO",
            Regex("CoroutineScope\\(Dispatchers\\.IO\\)\\.launch").containsMatchIn(s)
        )
    }
}
