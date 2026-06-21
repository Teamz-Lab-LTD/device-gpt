package com.teamz.lab.debugger.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v3.1.11 W1 ad-pipeline fix — source-text regression guard.
 *
 * 2026-06-21 audit found that AppOpenAdManager.kt + Application.kt had FIVE separate
 * call sites that invoked AppOpenAdManager.loadAd(). Combined with no request-side
 * throttle, this produced 5148 AdMob requests/week for 18 displays (0.43% show rate).
 *
 * Converged fix DELETES three of those call sites. The legitimate trigger paths are:
 *   1. Application.onCreate cold-start bootstrap
 *   2. AppOpenAdManager.kt post-dismissal self-reload (onAdDismissedFullScreenContent)
 *
 * Everything else (cooldown-branch preload, background-time-branch preload,
 * null-check-during-show preload, show-failure preload) WAS deleted and must
 * stay deleted. Without this regression guard, a future PR could re-introduce
 * a load-call to "fix" some edge case and silently re-break the fan-out.
 */
class AppOpenLoadAdCallSiteCountTest {

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) {
            dir = dir.parentFile
        }
        return dir
    }

    private fun read(path: String): String {
        val f = File(projectRoot(), path)
        if (!f.exists()) error("file not found: ${f.absolutePath}")
        return f.readText()
    }

    private fun countOccurrences(src: String, needle: Regex): Int =
        needle.findAll(src).count()

    @Test
    fun `AppOpenAdManager loadAd has exactly one external call site (Application onCreate)`() {
        // External call sites use the qualified `AppOpenAdManager.loadAd(...)` form.
        // The internal self-call inside the object body uses bare `loadAd(...)` — that
        // one is tested separately below.
        val externalCallRegex = Regex("""\bAppOpenAdManager\.loadAd\s*\(""")
        val appKt = read("app/src/main/java/com/teamz/lab/debugger/Application.kt")
        val externalCount = countOccurrences(appKt, externalCallRegex)
        assertEquals(
            "Exactly 1 external AppOpenAdManager.loadAd(...) call site allowed (Application.onCreate). " +
                "If you added another, you are about to re-break the 5148/wk request fan-out bug from 2026-06-21. " +
                "Read app/src/main/java/com/teamz/lab/debugger/utils/app_open_manager.kt header comment before adding.",
            1, externalCount
        )

        // Make sure NO other file in the production source tree calls AppOpenAdManager.loadAd().
        // Grep the full main source tree minus Application.kt + the manager file itself.
        val mainSrcRoot = File(projectRoot(), "app/src/main/java")
        val violators = mutableListOf<String>()
        mainSrcRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .filter { !it.absolutePath.endsWith("Application.kt") }
            .filter { !it.absolutePath.endsWith("app_open_manager.kt") }
            .forEach { f ->
                if (externalCallRegex.containsMatchIn(f.readText())) {
                    violators.add(f.absolutePath.substringAfter("app/src/main/java/"))
                }
            }
        assertTrue(
            "No production file outside Application.kt may call AppOpenAdManager.loadAd(). Found violators: $violators",
            violators.isEmpty()
        )
    }

    @Test
    fun `Internal loadAd self-call sites in app_open_manager exactly equal one (onAdDismissed only)`() {
        // Internal self-calls inside the AppOpenAdManager object use bare `loadAd(...)`.
        // Pre-fix had FOUR internal self-call sites:
        //   1. cooldown-branch preload      [DELETED]
        //   2. background-time-branch preload [DELETED]
        //   3. null-check-during-show preload [DELETED]
        //   4. show-failure preload          [DELETED]
        //   5. post-dismissal self-reload    [KEPT — legitimate]
        // Plus the function declaration itself. Filter out the `fun loadAd(...)` decl
        // and count bare `loadAd(activity` invocations.
        val src = read("app/src/main/java/com/teamz/lab/debugger/utils/app_open_manager.kt")
        // Match `loadAd(activity` (with or without second arg) — the post-dismissal call.
        val selfCallRegex = Regex("""(?<!fun )\bloadAd\s*\(activity""")
        val n = countOccurrences(src, selfCallRegex)
        assertEquals(
            "Exactly 1 internal self-call to loadAd(activity ...) must remain (onAdDismissedFullScreenContent). " +
                "Found $n. If >1, you re-introduced one of the 4 deleted preload sites — check the " +
                "DELETED-marker comments in app_open_manager.kt to see which one came back.",
            1, n
        )
    }

    @Test
    fun `internal loadAd self-calls in app_open_manager removed (cooldown + bg + null-check + show-fail)`() {
        val src = read("app/src/main/java/com/teamz/lab/debugger/utils/app_open_manager.kt")
        // The four deleted preload sites — each used to contain `loadAd(activity)` inside
        // a guard branch. The fix replaced them with explanatory comments. Assert each
        // deletion landmark is present so we can detect a regression by source-text alone.
        val deletionMarkers = listOf(
            "DELETED redundant preload here",          // cooldown branch
            "DELETED redundant preload here for the",  // background-time branch
            "DELETED the \"no ad available -> preload",// null-check-during-show
            "DELETED preload-after-show-failure",      // show-failure callback
        )
        deletionMarkers.forEach { marker ->
            assertTrue(
                "Deletion-landmark comment missing: '$marker'. If you removed the comment, also verify the corresponding loadAd() call has NOT been re-added — the comment is the only thing telling future devs why the line is gone.",
                src.contains(marker)
            )
        }
    }

    @Test
    fun `loadAd self-call inside post-dismissal callback is preserved`() {
        // We did NOT delete the legitimate self-reload — that one fires AFTER an ad was
        // shown, so the next session has a cached ad ready. Guard it.
        val src = read("app/src/main/java/com/teamz/lab/debugger/utils/app_open_manager.kt")
        val dismissedIdx = src.indexOf("override fun onAdDismissedFullScreenContent()")
        assertTrue(
            "onAdDismissedFullScreenContent must exist — it's where the post-dismissal preload lives.",
            dismissedIdx > 0
        )
        // Walk to the closing brace of this override; find the next `override fun` or 6-space `}`
        val nextOverride = src.indexOf("override fun", dismissedIdx + 1)
        val body = src.substring(dismissedIdx, if (nextOverride > 0) nextOverride else dismissedIdx + 1000)
        assertTrue(
            "Post-dismissal callback must still call loadAd(activity) — this is the legitimate " +
                "trigger path that preloads the next ad after a successful show.",
            body.contains("loadAd(activity)")
        )
    }

    @Test
    fun `Application onCreate calls resetSessionCounters before MobileAds initialize`() {
        // resetSessionCounters() makes the per-session load cap honest. The Application
        // object is a process singleton so counters would otherwise leak across user
        // sessions. Order matters: reset must run BEFORE loadAd().
        val src = read("app/src/main/java/com/teamz/lab/debugger/Application.kt")
        val resetIdx = src.indexOf("AppOpenAdManager.resetSessionCounters()")
        val loadIdx = src.indexOf("AppOpenAdManager.loadAd(applicationContext)")
        assertTrue(
            "Application.onCreate must call AppOpenAdManager.resetSessionCounters().",
            resetIdx > 0
        )
        assertTrue(
            "Application.onCreate must still preload at cold-start.",
            loadIdx > 0
        )
        assertTrue(
            "resetSessionCounters() must run BEFORE loadAd() to ensure the new session " +
                "starts with attempts=0 and lastLoadAttemptMs=0.",
            resetIdx < loadIdx
        )
    }
}
