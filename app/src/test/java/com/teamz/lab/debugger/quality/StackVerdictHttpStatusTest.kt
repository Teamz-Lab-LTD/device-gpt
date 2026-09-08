package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.AppDoctorContext
import com.teamz.lab.debugger.utils.AppDoctorContext.StackVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A real behavioural test, not a source-text grep — `compareStacks` is pure, so the
 * decision can be exercised directly with no device and no network.
 *
 * The bug it pins: the two stacks disagreed on what "reached" means. The Java probe
 * sets SUCCESS as soon as `conn.responseCode` returns at all, 403 and 405 included,
 * because for pure reachability that is correct — the packets arrived. Chromium
 * reports any main-frame status >= 400 through `onReceivedHttpError`, which
 * `WebViewStackProbe` records as HTTP_ERROR and therefore not a success.
 *
 * A WAF or a HEAD-refusing endpoint answering 403 thus produced java 4/4, webview
 * 0/4, and the comparison announced WEBVIEW_ONLY_FAILS — the feature's headline
 * diagnosis, "Chromium is broken while every other tool says the site is up" — on a
 * site where both stacks had been refused identically by the server.
 *
 * That matters beyond one banner: App Doctor's WebView-vs-Java probe is described in
 * the Play listing in eleven languages.
 */
class StackVerdictHttpStatusTest {

    @Test
    fun `a 403 to both stacks is not a WebView-only failure`() {
        assertEquals(
            "Java got 4/4 but the server answered 403, so the Java side did not retrieve " +
                "the page either. Both stacks were refused the same way — calling that " +
                "WEBVIEW_ONLY_FAILS blames Chromium for the site's own refusal.",
            StackVerdict.BOTH_FAIL,
            AppDoctorContext.compareStacks(
                javaSuccessCount = 4,
                webViewSuccessCount = 0,
                javaHttpCode = 403,
            ),
        )
    }

    @Test
    fun `a 405 to a bare request is not a WebView-only failure`() {
        assertEquals(
            StackVerdict.BOTH_FAIL,
            AppDoctorContext.compareStacks(4, 0, javaHttpCode = 405),
        )
    }

    @Test
    fun `the genuine case still reports WEBVIEW_ONLY_FAILS`() {
        assertEquals(
            "Java retrieved the page (200) and Chromium could not. This is the real " +
                "diagnosis and it must survive the fix — otherwise the feature is gutted " +
                "rather than corrected.",
            StackVerdict.WEBVIEW_ONLY_FAILS,
            AppDoctorContext.compareStacks(4, 0, javaHttpCode = 200),
        )
    }

    @Test
    fun `a 3xx counts as retrieved`() {
        assertEquals(
            StackVerdict.WEBVIEW_ONLY_FAILS,
            AppDoctorContext.compareStacks(4, 0, javaHttpCode = 301),
        )
    }

    @Test
    fun `an unknown java status code keeps the old behaviour`() {
        assertEquals(
            "A null code means the caller could not read one. Assuming failure there would " +
                "silently disable the diagnosis; the verdict must stay as it was.",
            StackVerdict.WEBVIEW_ONLY_FAILS,
            AppDoctorContext.compareStacks(4, 0, javaHttpCode = null),
        )
    }

    @Test
    fun `both stacks fine is still BOTH_OK, and both failing is still BOTH_FAIL`() {
        assertEquals(StackVerdict.BOTH_OK, AppDoctorContext.compareStacks(4, 4, 200))
        assertEquals(StackVerdict.BOTH_FAIL, AppDoctorContext.compareStacks(0, 0, null))
        assertEquals(StackVerdict.JAVA_ONLY_FAILS, AppDoctorContext.compareStacks(0, 4, null))
    }

    @Test
    fun `a missing measurement is still INCOMPLETE`() {
        assertEquals(StackVerdict.INCOMPLETE, AppDoctorContext.compareStacks(null, 4, 200))
        assertEquals(StackVerdict.INCOMPLETE, AppDoctorContext.compareStacks(4, null, 200))
    }
}
