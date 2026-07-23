package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.CameraHealthUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for [CameraHealthUtils.CameraHealthResult.allLensesResponded] — deliberately
 * binary and provable (every lens opened AND delivered a frame), the shape of claim the
 * 2026-07-24 research verdict says IS defensible, unlike a graded quality score.
 */
class CameraHealthResultLogicTest {

    private fun lens(id: String) = CameraHealthUtils.LensReport(
        cameraId = id, facing = "Back", hardwareLevel = "FULL", focalLengthsMm = listOf(4.2f),
        maxDigitalZoom = 4f, hasOpticalStabilization = true, hasFlash = true,
        supportsAutofocus = true, rawAvailableToThisApp = false, physicalLensCount = 1,
    )

    private fun liveness(id: String, opened: Boolean, frameReceived: Boolean) =
        CameraHealthUtils.CameraLivenessResult(
            cameraId = id, facing = "Back", opened = opened, frameReceived = frameReceived,
            autofocusConverged = null, openToFrameMs = 100L, errorReason = null,
        )

    @Test
    fun `all lenses responding is true when every camera opened and delivered a frame`() {
        val result = CameraHealthUtils.CameraHealthResult(
            factSheet = CameraHealthUtils.CameraFactSheet(listOf(lens("0"), lens("1"))),
            liveness = listOf(
                liveness("0", opened = true, frameReceived = true),
                liveness("1", opened = true, frameReceived = true),
            ),
        )
        assertTrue(result.allLensesResponded)
    }

    @Test
    fun `one dead camera makes all lenses responded false, not a partial score`() {
        val result = CameraHealthUtils.CameraHealthResult(
            factSheet = CameraHealthUtils.CameraFactSheet(listOf(lens("0"), lens("1"))),
            liveness = listOf(
                liveness("0", opened = true, frameReceived = true),
                liveness("1", opened = false, frameReceived = false),
            ),
        )
        assertFalse(
            "a single dead camera must not be averaged away into a passing result",
            result.allLensesResponded,
        )
    }

    @Test
    fun `a camera that opened but never delivered a frame is not responded`() {
        val result = CameraHealthUtils.CameraHealthResult(
            factSheet = CameraHealthUtils.CameraFactSheet(listOf(lens("0"))),
            liveness = listOf(liveness("0", opened = true, frameReceived = false)),
        )
        assertFalse(result.allLensesResponded)
    }

    @Test
    fun `empty liveness list is never reported as all-responded`() {
        // No camera on the device at all must read as "nothing to report", not as a pass.
        val result = CameraHealthUtils.CameraHealthResult(
            factSheet = CameraHealthUtils.CameraFactSheet(emptyList()),
            liveness = emptyList(),
        )
        assertFalse(result.allLensesResponded)
    }
}
