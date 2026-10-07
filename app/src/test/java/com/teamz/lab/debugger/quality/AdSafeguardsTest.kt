package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.AdConfig
import com.teamz.lab.debugger.utils.AdDailyCap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 2026-10-08. One phone (Redmi Note 11, 3.1.26) sent ~1,279 native requests in two days — two
 * thirds of the month — and saw 2 ads. The per-session budget resets on every process start,
 * so it cannot stop a loop that restarts the process. The cause is unproven; this cap makes it
 * harmless whatever it was. And the rewarded unit in local_config was a placeholder
 * ("…/1234567890") that AdMob does not have.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdSafeguardsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        context.getSharedPreferences(AdDailyCap.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        AdDailyCap.init(context)
    }

    @Test
    fun `daily cap stops requests at the limit`() {
        repeat(AdDailyCap.NATIVE_PER_DAY) {
            assertTrue("request ${it + 1} allowed", AdDailyCap.allow("native", day = "2026-10-08"))
            AdDailyCap.record("native", day = "2026-10-08")
        }
        assertFalse(AdDailyCap.allow("native", day = "2026-10-08"))
    }

    @Test
    fun `cap survives a process restart`() {
        repeat(AdDailyCap.NATIVE_PER_DAY) { AdDailyCap.record("native", day = "2026-10-08") }
        AdDailyCap.init(context) // a new process re-initialises from disk
        assertFalse(AdDailyCap.allow("native", day = "2026-10-08"))
    }

    @Test
    fun `a new day starts a new count`() {
        repeat(AdDailyCap.NATIVE_PER_DAY) { AdDailyCap.record("native", day = "2026-10-08") }
        assertTrue(AdDailyCap.allow("native", day = "2026-10-09"))
        assertEquals(0, AdDailyCap.count("native", day = "2026-10-09"))
    }

    @Test
    fun `placeholder and test unit ids are not real units`() {
        assertTrue(AdConfig.isRealUnitId("ca-app-pub-7088022825081956/2139601263"))
        assertFalse(AdConfig.isRealUnitId("ca-app-pub-7088022825081956/1234567890"))
        assertFalse(AdConfig.isRealUnitId("ca-app-pub-3940256099942544/5224354917"))
        assertFalse(AdConfig.isRealUnitId(""))
    }
}
