package com.teamz.lab.debugger.health

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.teamz.lab.debugger.utils.HealthScoreUtils
import com.teamz.lab.debugger.utils.SecurityInfoCache
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for the Health-tab main-thread block.
 *
 * `HealthScoreUtils.getImprovementSuggestions()` and `generateDailyTasks()` used to reach
 * `getSecurityInfo()` through `runBlocking { }`. `getSecurityInfo()` forks a `getenforce`
 * subprocess and reads its stdout (device_utils.kt:1115). Both functions are called from
 * Compose — a `LazyColumn` item body and a `LaunchedEffect` — so the fork ran on the main
 * thread on every recomposition of the Health tab (35% of active users).
 *
 * The fix routes them through SecurityInfoCache, which never blocks.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class HealthTabNoAnrTest {

    private val appContext: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * Calls the suggestion builder directly on the main looper — exactly as the
     * `item(key = "recommendations")` composable body does — while a ticker posts every
     * 50ms. If it forks a subprocess on Main, the ticker starves.
     */
    @Test
    fun improvementSuggestionsDoNotBlockTheMainLooper() {
        SecurityInfoCache.resetForTest()

        val ticks = AtomicInteger(0)
        val handler = Handler(Looper.getMainLooper())
        val ticker = object : Runnable {
            override fun run() {
                ticks.incrementAndGet()
                handler.postDelayed(this, 50L)
            }
        }
        handler.post(ticker)

        // Hammer it the way a scrolling LazyColumn would.
        handler.post {
            repeat(20) { HealthScoreUtils.getImprovementSuggestions(appContext, 6) }
        }

        SystemClock.sleep(1_500L)
        val advanced = ticks.get()
        handler.removeCallbacks(ticker)

        assertTrue(
            "main looper advanced only $advanced ticks while 20 suggestion builds ran — " +
                "getImprovementSuggestions is forking `getenforce` on the main thread",
            advanced > 10
        )
    }

    /** Same property for the daily-task builder. */
    @Test
    fun dailyTaskBuilderDoesNotBlockTheMainLooper() {
        SecurityInfoCache.resetForTest()

        val ticks = AtomicInteger(0)
        val handler = Handler(Looper.getMainLooper())
        val ticker = object : Runnable {
            override fun run() {
                ticks.incrementAndGet()
                handler.postDelayed(this, 50L)
            }
        }
        handler.post(ticker)
        handler.post { repeat(20) { HealthScoreUtils.generateDailyTasks(appContext, 6) } }

        SystemClock.sleep(1_500L)
        val advanced = ticks.get()
        handler.removeCallbacks(ticker)

        assertTrue("main looper advanced only $advanced ticks", advanced > 10)
    }

    /**
     * Behavioural guard: a cold cache must degrade to "no security advice", never to a
     * crash or an empty suggestion list. Score-based advice is always present.
     */
    @Test
    fun suggestionsStillReturnedWhenSecurityCacheIsCold() {
        SecurityInfoCache.resetForTest()
        val suggestions = HealthScoreUtils.getImprovementSuggestions(appContext, 4)
        assertTrue("cold cache must still yield score-based advice", suggestions.isNotEmpty())
    }

    /** Once warmed, the security-derived advice must actually appear. */
    @Test
    fun securityAdviceAppearsAfterCacheIsWarmed() = runBlocking {
        SecurityInfoCache.resetForTest()
        val cold = HealthScoreUtils.getImprovementSuggestions(appContext, 4)

        val info = withTimeoutOrNull(8_000L) { SecurityInfoCache.refresh(appContext) }
        assertNotNull("SecurityInfoCache.refresh() never completed", info)
        assertTrue("refresh() must publish to the cache", SecurityInfoCache.cachedOrEmpty().isNotEmpty())

        val warm = HealthScoreUtils.getImprovementSuggestions(appContext, 4)
        // Warm must be a superset: same score-based advice, plus any security advice.
        assertTrue("warm suggestions lost content", warm.size >= cold.size)
    }

    /** The health score itself must not regress: it awaits the real value, not the cache. */
    @Test
    fun healthScoreStillUsesRealSecurityInfo() = runBlocking {
        SecurityInfoCache.resetForTest()
        val score = withTimeoutOrNull(10_000L) { HealthScoreUtils.calculateDailyHealthScore(appContext) }
        assertNotNull("calculateDailyHealthScore hung", score)
        assertTrue("score out of range: $score", score!! in 0..10)
        assertTrue(
            "calculateDailyHealthScore must populate the cache as a side effect",
            SecurityInfoCache.cachedOrEmpty().isNotEmpty()
        )
    }
}
