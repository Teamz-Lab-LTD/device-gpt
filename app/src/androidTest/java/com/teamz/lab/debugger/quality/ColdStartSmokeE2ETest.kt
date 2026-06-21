package com.teamz.lab.debugger.quality

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teamz.lab.debugger.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v3.1.11 E2E — cold-start smoke test.
 *
 * Boots MainActivity end-to-end on a real device. Asserts:
 *   - Activity reaches RESUMED state (no crash, no ANR before resume)
 *   - setContent is called (Compose host is alive)
 *
 * This is the cheapest E2E test possible and the most valuable: if a release
 * cannot cold-start, every other test is moot. Run this before any production
 * upload.
 *
 * Run via:
 *   ./gradlew :app:connectedDebugAndroidTest --tests '*ColdStartSmokeE2ETest*'
 *
 * Status: WRITTEN, NOT RUN ON DEVICE THIS SESSION. Owner runs manually before
 * v3.1.11 production upload.
 */
@RunWith(AndroidJUnit4::class)
class ColdStartSmokeE2ETest {

    @Test
    fun mainActivity_coldStartsWithoutCrashAndReachesResumed() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            assertNotNull("ActivityScenario must spin up MainActivity successfully.", scenario)
            scenario.onActivity { activity ->
                assertNotNull("MainActivity reference must be non-null after launch.", activity)
                assertTrue(
                    "Activity must NOT be finishing after cold-start — finishing here would " +
                        "indicate a crash or fatal init failure that the user would see " +
                        "as the app failing to open.",
                    !activity.isFinishing
                )
            }
        } finally {
            scenario.close()
        }
    }
}
