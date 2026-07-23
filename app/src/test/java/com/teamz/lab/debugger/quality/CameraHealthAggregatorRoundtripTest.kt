package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.CameraHealthAggregator
import com.teamz.lab.debugger.utils.CameraHealthUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Encode/decode roundtrip for [CameraHealthAggregator]'s manual delimited-string persistence —
 * the same encoding idiom [com.teamz.lab.debugger.utils.PowerConsumptionAggregator] uses
 * elsewhere in this app (no Gson/Room dependency exists here, so this is hand-rolled and worth
 * a direct proof it survives a save/load cycle, including the edge cases a hand-rolled encoder
 * is most likely to get wrong: nulls, commas inside free text, and an empty lens list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CameraHealthAggregatorRoundtripTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("camera_health_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `a camera health result with two lenses survives save and load unchanged`() {
        val original = CameraHealthUtils.CameraHealthResult(
            factSheet = CameraHealthUtils.CameraFactSheet(
                listOf(
                    CameraHealthUtils.LensReport(
                        cameraId = "0", facing = "Back", hardwareLevel = "FULL",
                        focalLengthsMm = listOf(4.2f, 1.8f), maxDigitalZoom = 8f,
                        hasOpticalStabilization = true, hasFlash = true, supportsAutofocus = true,
                        rawAvailableToThisApp = true, physicalLensCount = 3,
                    ),
                    CameraHealthUtils.LensReport(
                        cameraId = "1", facing = "Front", hardwareLevel = "LIMITED",
                        focalLengthsMm = emptyList(), maxDigitalZoom = null,
                        hasOpticalStabilization = false, hasFlash = false, supportsAutofocus = false,
                        rawAvailableToThisApp = false, physicalLensCount = 1,
                    ),
                ),
            ),
            liveness = listOf(
                CameraHealthUtils.CameraLivenessResult(
                    cameraId = "0", facing = "Back", opened = true, frameReceived = true,
                    autofocusConverged = true, openToFrameMs = 342L, errorReason = null,
                ),
                CameraHealthUtils.CameraLivenessResult(
                    cameraId = "1", facing = "Front", opened = false, frameReceived = false,
                    autofocusConverged = null, openToFrameMs = null,
                    // Commas are the field delimiter in this encoder — a real error message can
                    // contain them, and the encoder must not corrupt the row.
                    errorReason = "Camera did not respond in time, retry needed",
                ),
            ),
        )

        CameraHealthAggregator.saveCameraHealthResult(context, original)
        val loaded = CameraHealthAggregator.loadCameraHealthHistory(context)

        assertEquals(1, loaded.size)
        val result = loaded.single()
        assertEquals(original.timestamp, result.timestamp)
        assertEquals(2, result.factSheet.lenses.size)
        assertEquals(original.factSheet.lenses[0], result.factSheet.lenses[0])
        assertEquals(original.factSheet.lenses[1], result.factSheet.lenses[1])
        assertEquals(original.liveness[0], result.liveness[0])
        // The comma inside errorReason is replaced with a space by the encoder (see
        // encodeResult) — this asserts that replacement, not silent corruption or a crash.
        assertEquals(
            "Camera did not respond in time  retry needed",
            result.liveness[1].errorReason,
        )
        assertNull(result.liveness[1].autofocusConverged)
        assertTrue(result.factSheet.lenses[1].focalLengthsMm.isEmpty())
    }

    @Test
    fun `history is capped and trimmed to the most recent entries, oldest first dropped`() {
        repeat(15) { i ->
            val result = CameraHealthUtils.CameraHealthResult(
                factSheet = CameraHealthUtils.CameraFactSheet(emptyList()),
                liveness = emptyList(),
                timestamp = i.toLong(),
            )
            CameraHealthAggregator.saveCameraHealthResult(context, result)
        }
        val loaded = CameraHealthAggregator.loadCameraHealthHistory(context)
        assertTrue("history must be capped, got ${loaded.size}", loaded.size <= 10)
        assertEquals("the most recent entry must survive the trim", 14L, loaded.last().timestamp)
        assertTrue(
            "the oldest entries must be the ones dropped, not the newest",
            loaded.first().timestamp > 0L,
        )
    }

    @Test
    fun `screen pixel result with a reported issue survives save and load`() {
        val result = CameraHealthUtils.ScreenPixelResult(
            userReportedIssue = true,
            colorShownWhenReported = "green",
        )
        CameraHealthAggregator.saveScreenPixelResult(context, result)
        val loaded = CameraHealthAggregator.loadScreenPixelHistory(context)

        assertEquals(1, loaded.size)
        assertEquals(true, loaded.single().userReportedIssue)
        assertEquals("green", loaded.single().colorShownWhenReported)
    }

    @Test
    fun `a screen pixel result with no issue stores a null colour, not the string 'null'`() {
        val result = CameraHealthUtils.ScreenPixelResult(
            userReportedIssue = false,
            colorShownWhenReported = null,
        )
        CameraHealthAggregator.saveScreenPixelResult(context, result)
        val loaded = CameraHealthAggregator.loadScreenPixelHistory(context)

        assertNull(loaded.single().colorShownWhenReported)
    }
}
