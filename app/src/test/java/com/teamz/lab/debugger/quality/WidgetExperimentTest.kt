package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.WidgetExperiment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * widget_v2_enabled can only be tested as an experiment if GA4 knows which layout each widget
 * user was served. These tests pin the label and the conditions under which it is written.
 */
class WidgetExperimentTest {

    @Test
    fun `the arm label follows the resolved flag`() {
        assertEquals("on", WidgetExperiment.armLabel(true))
        assertEquals("off", WidgetExperiment.armLabel(false))
    }

    @Test
    fun `a never-stamped install is always stamped`() {
        // First render after install — nothing recorded yet, so GA4 must learn the arm.
        assertTrue(WidgetExperiment.shouldStamp(null, "off"))
        assertTrue(WidgetExperiment.shouldStamp(null, "on"))
    }

    @Test
    fun `an unchanged arm is not re-stamped on every render`() {
        // The widget rendered 11,862 times in 30 days. Re-stamping each time is pure overhead.
        assertFalse(WidgetExperiment.shouldStamp("on", "on"))
        assertFalse(WidgetExperiment.shouldStamp("off", "off"))
    }

    @Test
    fun `a flip in Remote Config is recorded`() {
        // If the flag is flipped (or killed), the property must follow what the user now sees,
        // otherwise post-flip behaviour would be attributed to the wrong arm.
        assertTrue(WidgetExperiment.shouldStamp("off", "on"))
        assertTrue(WidgetExperiment.shouldStamp("on", "off"))
    }

    @Test
    fun `the widget stamps from the resolved value, right where it is read`() {
        val widget = File("src/main/java/com/teamz/lab/debugger/widgets/LockScreenMonitorWidget.kt")
            .readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }
        assertTrue("positive control: widget source loaded", widget.contains("isWidgetV2Enabled()"))
        val readAt = widget.indexOf("isWidgetV2Enabled()")
        val stampAt = widget.indexOf("WidgetExperiment.stampIfChanged(context, widgetV2)")
        assertTrue(
            "the widget no longer records which arm it served — a widget_v2_enabled split " +
                "would be unreadable in GA4",
            stampAt > 0,
        )
        assertTrue(
            "the stamp must use the value the widget actually resolved, so it has to come " +
                "after isWidgetV2Enabled() is read",
            stampAt > readAt,
        )
    }
}
