package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.FirstScreenExperiment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstScreenExperimentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clear() {
        context.getSharedPreferences(FirstScreenExperiment.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun stored(): String? =
        context.getSharedPreferences(FirstScreenExperiment.PREFS, Context.MODE_PRIVATE)
            .getString(FirstScreenExperiment.KEY_ARM, null)

    @Test fun `arm follows the server value the first time`() {
        assertEquals("B", FirstScreenExperiment.chooseArm(rcFromServer = true, stored = null))
        assertEquals("A", FirstScreenExperiment.chooseArm(rcFromServer = false, stored = null))
    }

    @Test fun `a stored arm never changes`() {
        assertEquals("A", FirstScreenExperiment.chooseArm(rcFromServer = true, stored = "A"))
        assertEquals("B", FirstScreenExperiment.chooseArm(rcFromServer = false, stored = "B"))
        assertEquals("B", FirstScreenExperiment.chooseArm(rcFromServer = null, stored = "B"))
    }

    // Review 2026-10-09 C1: before the RC fetch lands, getBoolean returns the bundled false. Storing
    // that put every slow/offline first launch in A, so B lacked exactly those users.
    @Test fun `no arm is assigned while the RC value is not from the server`() {
        assertNull(FirstScreenExperiment.chooseArm(rcFromServer = null, stored = null))
        assertNull(FirstScreenExperiment.arm(context, rcFromServer = null))
        assertNull("nothing persisted", stored())
        assertFalse(FirstScreenExperiment.isB(context))
    }

    @Test fun `arm is persisted once the server value is known`() {
        assertEquals("B", FirstScreenExperiment.arm(context, rcFromServer = true))
        assertEquals("B", stored())
        assertEquals("B", FirstScreenExperiment.arm(context, rcFromServer = false))  // stored wins
    }

    // Review 2026-10-09 I1: only the score screen assigns. Reading the arm anywhere else must not.
    @Test fun `isB and peekArm never assign an arm`() {
        assertNull(FirstScreenExperiment.peekArm(context))
        assertFalse(FirstScreenExperiment.isB(context))
        assertNull("peek must not persist", stored())
    }

    @Test fun `chooser routes to the existing tabs`() {
        assertEquals("camera", FirstScreenExperiment.tabFor("camera"))
        assertEquals("screen_test", FirstScreenExperiment.tabFor("mic"))
        assertEquals("screen_test", FirstScreenExperiment.tabFor("screen"))
        assertEquals("health", FirstScreenExperiment.tabFor("report"))
        assertEquals("health", FirstScreenExperiment.tabFor("anything else"))
    }
}
