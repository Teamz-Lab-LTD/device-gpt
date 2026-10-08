package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.FirstScreenExperiment
import org.junit.Assert.assertEquals
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

    @Test fun `arm follows the flag the first time`() {
        assertEquals("B", FirstScreenExperiment.chooseArm(rcFlag = true, stored = null))
        assertEquals("A", FirstScreenExperiment.chooseArm(rcFlag = false, stored = null))
    }

    @Test fun `a stored arm never changes`() {
        assertEquals("A", FirstScreenExperiment.chooseArm(rcFlag = true, stored = "A"))
        assertEquals("B", FirstScreenExperiment.chooseArm(rcFlag = false, stored = "B"))
    }

    @Test fun `arm is persisted on first read`() {
        val first = FirstScreenExperiment.arm(context)          // RC default false -> A
        assertEquals("A", first)
        context.getSharedPreferences(FirstScreenExperiment.PREFS, Context.MODE_PRIVATE)
            .edit().putString(FirstScreenExperiment.KEY_ARM, "B").commit()
        assertEquals("B", FirstScreenExperiment.arm(context))   // stored wins
    }

    @Test fun `chooser routes to the existing tabs`() {
        assertEquals("camera", FirstScreenExperiment.tabFor("camera"))
        assertEquals("screen_test", FirstScreenExperiment.tabFor("mic"))
        assertEquals("screen_test", FirstScreenExperiment.tabFor("screen"))
        assertEquals("health", FirstScreenExperiment.tabFor("report"))
        assertEquals("health", FirstScreenExperiment.tabFor("anything else"))
    }
}
