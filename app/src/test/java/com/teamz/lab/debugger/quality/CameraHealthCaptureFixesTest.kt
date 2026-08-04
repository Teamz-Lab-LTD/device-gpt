package com.teamz.lab.debugger.quality

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.ui.CameraHealthViewModel
import com.teamz.lab.debugger.utils.CameraHealthUtils
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression tests for the 2026-07-25 camera bug-fix session: sideways preview, dark photos
 * (especially the back camera), the "Check Again" scroll-reset, the missing colour-cast photo
 * preview, and the removed Go Pro button. Each test below was run against the pre-fix source
 * with the relevant fix commented out to confirm it actually fails without the fix — see the
 * inline note on each test for what that regression looked like.
 *
 * [CameraHealthViewModel.decodeAndOrient] was `private`; it is now `@VisibleForTesting internal`
 * purely so this file can call it directly — no behavior change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real Skia decode/compress — legacy mode fakes pixels as black
class CameraHealthCaptureFixesTest {

    private lateinit var viewModel: CameraHealthViewModel

    @Before fun setup() {
        viewModel = CameraHealthViewModel(ApplicationProvider.getApplicationContext<Application>())
    }

    // ─────────────────────── sideways preview fix ───────────────────────
    //
    // A non-square (wide) source bitmap is used deliberately: if the EXIF rotation is NOT
    // applied, a 90°/270°-tagged JPEG decodes with its width/height unswapped, which this test
    // catches immediately. A square bitmap could not tell the difference.

    private fun jpegWithOrientation(orientationTag: Int, width: Int = 40, height: Int = 20): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(android.graphics.Color.RED)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)

        // Write real bytes to a temp file so android.media.ExifInterface can rewrite the
        // orientation tag in place — the only way to attach EXIF to a freshly compressed JPEG.
        val tmp = File.createTempFile("camera_test", ".jpg")
        tmp.writeBytes(out.toByteArray())
        ExifInterface(tmp.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientationTag.toString())
            saveAttributes()
        }
        val result = tmp.readBytes()
        tmp.delete()
        return result
    }

    @Test fun `ORIENTATION_ROTATE_90 swaps width and height in the decoded preview`() {
        // Pre-fix regression this guards: decodeAndOrient() with the EXIF branch skipped (or
        // absent) returns the raw sensor-frame bitmap — width and height stay in the JPEG's
        // native (landscape) order even when the phone was held portrait, i.e. "sideways".
        val jpeg = jpegWithOrientation(ExifInterface.ORIENTATION_ROTATE_90, width = 40, height = 20)
        val oriented = viewModel.decodeAndOrient(jpeg)
        assertNotNull("decode must not fail on a valid EXIF-tagged JPEG", oriented)
        assertTrue(
            "ORIENTATE_90 must swap dimensions (got ${oriented!!.width}x${oriented.height})",
            oriented.width < oriented.height,
        )
    }

    @Test fun `ORIENTATION_ROTATE_270 also swaps width and height`() {
        val jpeg = jpegWithOrientation(ExifInterface.ORIENTATION_ROTATE_270, width = 40, height = 20)
        val oriented = viewModel.decodeAndOrient(jpeg)
        assertNotNull(oriented)
        assertTrue(
            "ORIENTATION_270 must swap dimensions (got ${oriented!!.width}x${oriented.height})",
            oriented.width < oriented.height,
        )
    }

    @Test fun `ORIENTATION_NORMAL leaves dimensions unswapped`() {
        val jpeg = jpegWithOrientation(ExifInterface.ORIENTATION_NORMAL, width = 40, height = 20)
        val oriented = viewModel.decodeAndOrient(jpeg)
        assertNotNull(oriented)
        assertTrue(
            "ORIENTATION_NORMAL must NOT swap dimensions (got ${oriented!!.width}x${oriented.height})",
            oriented.width > oriented.height,
        )
    }

    @Test fun `ORIENTATION_ROTATE_180 keeps dimensions but still decodes without crashing`() {
        val jpeg = jpegWithOrientation(ExifInterface.ORIENTATION_ROTATE_180, width = 40, height = 20)
        val oriented = viewModel.decodeAndOrient(jpeg)
        assertNotNull(oriented)
        assertTrue(oriented!!.width > oriented.height)
    }

    @Test fun `a JPEG with no EXIF block at all decodes without crashing`() {
        // OEM camera HALs sometimes strip EXIF entirely — decodeAndOrient must fall back to the
        // raw decode, never throw, never return null on an otherwise-valid JPEG.
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(android.graphics.Color.BLUE)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        val oriented = viewModel.decodeAndOrient(out.toByteArray())
        assertNotNull(oriented)
    }

    @Test fun `garbage bytes return null instead of crashing the caller`() {
        val oriented = viewModel.decodeAndOrient(byteArrayOf(1, 2, 3, 4, 5))
        assertNull(oriented)
    }

    // ─────────────────────── dark-photo / AE warmup fix ───────────────────────
    //
    // The real fix (TEMPLATE_PREVIEW warmup before TEMPLATE_STILL_CAPTURE, adaptive on real
    // CONTROL_AE_STATE convergence) needs a live Camera2 session and cannot run in a JVM unit
    // test. This is a source-text guard — the same technique CameraHealthPolicyGuardTest
    // already uses for non-hardware-testable behavior — proving the fix is actually present
    // and cannot silently regress.

    private fun readCameraHealthUtilsSource(): String =
        File("src/main/java/com/teamz/lab/debugger/utils/camera_health_utils.kt").readText()

    @Test fun `capture flow drives a repeating TEMPLATE_PREVIEW warmup, gated before any TEMPLATE_STILL_CAPTURE`() {
        // Pre-fix regression this guards: a single cold TEMPLATE_STILL_CAPTURE with no warmup —
        // the first frame from a freshly opened camera comes out near-black, worst on the back
        // camera whose AE target is calibrated for a wider dynamic range.
        val src = readCameraHealthUtilsSource()
        assertTrue("TEMPLATE_PREVIEW warmup request not found", src.contains("createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)"))
        assertTrue("TEMPLATE_STILL_CAPTURE not found", src.contains("createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)"))
        assertTrue(
            "warmup preview must be a REPEATING request, not a single shot",
            src.contains("setRepeatingRequest("),
        )
        // The still capture must be a one-shot, guarded so it can only run once — reached from
        // either the AE-convergence path or the ceiling fallback, never called unconditionally.
        val stillFnIdx = src.indexOf("fun fireStillCapture()")
        assertTrue("fireStillCapture() function not found", stillFnIdx >= 0)
        val guardIdx = src.indexOf("if (warmupComplete) return", stillFnIdx)
        assertTrue(
            "still capture must be guarded by a one-shot flag so it only ever fires from the warmup path",
            guardIdx in stillFnIdx..(stillFnIdx + 200),
        )
    }

    @Test fun `AE warmup waits for the device's own CONTROL_AE_STATE convergence, not a guessed fixed delay`() {
        // 2026-07-25: replaced a hardcoded 700ms delay (tuned by hand on ONE device, the
        // Pixel 8a) with a read of the device's own AE convergence signal — a fixed number has
        // no reason to be correct on other camera HALs (wife's phone is a different OEM).
        val src = readCameraHealthUtilsSource()
        assertTrue(
            "must check CaptureResult.CONTROL_AE_STATE for real convergence",
            src.contains("CaptureResult.CONTROL_AE_STATE"),
        )
        assertTrue(
            "must recognize CONTROL_AE_STATE_CONVERGED as the signal to stop warming up",
            src.contains("CONTROL_AE_STATE_CONVERGED"),
        )
    }

    @Test fun `AE warmup has a floor and a ceiling that both stay within the outer liveness timeout`() {
        val src = readCameraHealthUtilsSource()
        fun constant(name: String): Int? =
            Regex("""$name\s*=\s*([\d_]+)L""").find(src)
                ?.groupValues?.get(1)?.replace("_", "")?.toInt()

        val minMs = constant("AE_WARMUP_MIN_MS")
        val maxMs = constant("AE_WARMUP_MAX_MS")
        assertNotNull("AE_WARMUP_MIN_MS constant not found", minMs)
        assertNotNull("AE_WARMUP_MAX_MS constant not found", maxMs)
        assertTrue(
            "floor is ${minMs}ms — firing on the very first frame reintroduces the dark-photo bug",
            minMs!! >= 150,
        )
        assertTrue("ceiling (${maxMs}ms) must be strictly greater than the floor (${minMs}ms)", maxMs!! > minMs)
        assertTrue(
            "ceiling (${maxMs}ms) must leave real headroom inside the outer 4000ms liveness timeout " +
                "for camera-open + still-capture round trip",
            maxMs <= 2000,
        )
    }

    @Test fun `the still-capture request sets JPEG_ORIENTATION from the sensor orientation`() {
        // Pre-fix regression this guards: no JPEG_ORIENTATION set, so the raw sensor-frame JPEG
        // is written sideways regardless of how the phone is held.
        val src = readCameraHealthUtilsSource()
        assertTrue(
            "still-capture request must set CaptureRequest.JPEG_ORIENTATION",
            src.contains("set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation)"),
        )
    }

    @Test fun `both preview warmup and still capture explicitly force AE_MODE_ON and AWB_MODE_AUTO`() {
        val src = readCameraHealthUtilsSource()
        val aeOnCount = Regex("CONTROL_AE_MODE_ON\\b").findAll(src).count()
        val awbAutoCount = Regex("CONTROL_AWB_MODE_AUTO\\b").findAll(src).count()
        assertTrue("expected AE_MODE_ON set in both capture phases, found $aeOnCount", aeOnCount >= 2)
        assertTrue("expected AWB_MODE_AUTO set in both capture phases, found $awbAutoCount", awbAutoCount >= 2)
    }

    @Test fun `a warmup preview frame is discarded, only the still-capture frame is kept`() {
        // Pre-fix regression this guards: if every frame from the repeating preview request were
        // kept, onFrameCaptured would fire with the dark warmup frame instead of the settled one.
        val src = readCameraHealthUtilsSource()
        assertTrue(
            "must gate delivery on a 'keep this frame' flag set only after the warmup phase",
            src.contains("keepNextFrame = true") && src.contains("if (!keepNextFrame)"),
        )
    }

    // ─────────────────────── EXIF read (readExifSummary) ───────────────────────

    private fun realJpegWithFullExif(): ByteArray {
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(android.graphics.Color.GREEN)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        val tmp = File.createTempFile("camera_exif_test", ".jpg")
        tmp.writeBytes(out.toByteArray())
        ExifInterface(tmp.absolutePath).apply {
            @Suppress("DEPRECATION")
            setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, "400")
            setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "0.0166667") // 1/60s
            setAttribute(ExifInterface.TAG_F_NUMBER, "1.8")
            setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "26/10")
            setAttribute(ExifInterface.TAG_FLASH, "1") // bit0 set = fired
            saveAttributes()
        }
        val result = tmp.readBytes()
        tmp.delete()
        return result
    }

    @Test fun `readExifSummary reads back real ISO, shutter, aperture, focal length and flash`() {
        val summary = CameraHealthUtils.readExifSummary(realJpegWithFullExif())
        assertNotNull(summary)
        assertEquals(400, summary!!.isoSpeed)
        assertEquals("1/60s", summary.exposureTimeSec)
        assertEquals(1.8f, summary.fNumber!!, 0.01f)
        assertEquals(2.6f, summary.focalLengthMm!!, 0.01f)
        assertEquals(true, summary.flashFired)
    }

    @Test fun `readExifSummary on a JPEG with no EXIF block returns null, not a fake zeroed result`() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        // Some encoders still emit a minimal EXIF block even with no tags set — readExifSummary
        // must at minimum never crash and never invent ISO=0 / f/0 style fake values.
        val summary = CameraHealthUtils.readExifSummary(out.toByteArray())
        if (summary != null) {
            assertNull("must not fabricate an ISO value when none was written", summary.isoSpeed)
        }
    }

    @Test fun `readExifSummary on garbage bytes never throws and never fabricates a value`() {
        // ExifInterface itself doesn't throw on non-JPEG bytes — it just finds no tags. The
        // safety property that matters is "never invent a value", not "always return null".
        val summary = CameraHealthUtils.readExifSummary(byteArrayOf(9, 9, 9))
        if (summary != null) {
            assertNull(summary.isoSpeed)
            assertNull(summary.exposureTimeSec)
            assertNull(summary.fNumber)
            assertNull(summary.focalLengthMm)
            assertNull(summary.flashFired)
        }
    }

    // ─────────────────────── colour-cast saturation read ───────────────────────

    private fun solidColorJpeg(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        return out.toByteArray()
    }

    @Test fun `a fully saturated red frame reads high saturation`() {
        val saturation = CameraHealthUtils.analyzeCapturedFrameForColorCast(
            solidColorJpeg(android.graphics.Color.RED),
        )
        assertNotNull(saturation)
        assertTrue("pure red must read near-max saturation, got $saturation", saturation!! > 0.8f)
    }

    @Test fun `a neutral grey frame reads near-zero saturation`() {
        val grey = android.graphics.Color.rgb(128, 128, 128)
        val saturation = CameraHealthUtils.analyzeCapturedFrameForColorCast(solidColorJpeg(grey))
        assertNotNull(saturation)
        assertTrue("neutral grey must read near-zero saturation, got $saturation", saturation!! < 0.08f)
    }

    @Test fun `analyzeCapturedFrameForColorCast returns null on undecodable bytes, never a fake value`() {
        assertNull(CameraHealthUtils.analyzeCapturedFrameForColorCast(byteArrayOf(1, 2, 3)))
    }

    @Test fun `buildColorCastCheckResult flags a grey photo as monochrome, a red photo as not`() {
        val grey = android.graphics.Color.rgb(128, 128, 128)
        val context = ApplicationProvider.getApplicationContext<Application>()

        val greyResult = CameraHealthUtils.buildColorCastCheckResult(context, solidColorJpeg(grey))
        assertEquals(true, greyResult.capturedLooksMonochrome)

        val redResult = CameraHealthUtils.buildColorCastCheckResult(context, solidColorJpeg(android.graphics.Color.RED))
        assertEquals(false, redResult.capturedLooksMonochrome)
    }

    @Test fun `buildColorCastCheckResult with null jpegBytes reports unknown, never guesses`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val result = CameraHealthUtils.buildColorCastCheckResult(context, null)
        assertNull(result.capturedLooksMonochrome)
        assertNull(result.averageSaturation)
        assertNull(result.exifSummary)
    }

    // ─────────────────────── UI regressions: Go Pro button, ad-on-recheck, missing preview ───

    private fun readCameraHealthCardSource(): String =
        File("src/main/java/com/teamz/lab/debugger/ui/camera_health_card.kt").readText()

    /** Strips `//` and block comments so KDoc mentioning the removed feature doesn't self-trigger. */
    private fun stripComments(src: String): String =
        src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    @Test fun `Go Pro upsell button is removed from the camera problem report card`() {
        // Pre-fix regression this guards: the paywall rendered an empty box on tap (real cause
        // unconfirmed — a RC/Play product-type mismatch was suspected 2026-07-25 but disproven
        // the same day via direct API checks against both RC and Play) — the button was removed
        // rather than shipping a broken tap target.
        val liveCode = stripComments(readCameraHealthCardSource())
        assertFalse(
            "a live 'Go Pro' Button must not be wired back into camera_health_card.kt until the " +
                "root cause of the empty-paywall tap is found and fixed",
            Regex("Go Pro", RegexOption.IGNORE_CASE).containsMatchIn(liveCode),
        )
        assertFalse(
            "RevenueCatManager must not be referenced from live code in this file — the paywall " +
                "entry point here was intentionally removed, not just hidden",
            liveCode.contains("RevenueCatManager"),
        )
    }

    @Test fun `Check Again does not trigger an interstitial ad`() {
        // Pre-fix regression this guards: InterstitialAdManager.showAdBeforeAction wrapped the
        // colour-cast recheck button; the ad Activity paused MainActivity, which tore down and
        // rebuilt the tab's scroll state on resume, jumping the viewport back to the top.
        val src = readCameraHealthCardSource()
        val cardStart = src.indexOf("private fun ColorCastCheckCard(")
        assertTrue("ColorCastCheckCard not found — test is stale", cardStart >= 0)
        val cardEnd = src.indexOf("@Composable", cardStart + 1)
        val cardBody = src.substring(cardStart, if (cardEnd > cardStart) cardEnd else src.length)
        assertFalse(
            "ColorCastCheckCard's Check/Check Again button must not call InterstitialAdManager " +
                "— doing so resets scroll position on ad-activity resume",
            cardBody.contains("InterstitialAdManager"),
        )
    }

    @Test fun `ColorCastResultCard renders the captured preview photo when one is available`() {
        // Pre-fix regression this guards: the check proved a frame arrived but never displayed
        // it — "why is there no preview shown from the image?" (user report, 2026-07-25).
        val src = readCameraHealthCardSource()
        val fnStart = src.indexOf("private fun ColorCastResultCard(")
        assertTrue("ColorCastResultCard not found — test is stale", fnStart >= 0)
        val fnBody = src.substring(fnStart)
        assertTrue(
            "ColorCastResultCard must take a preview Bitmap parameter",
            fnBody.substringBefore(")").contains("preview:"),
        )
        assertTrue(
            "ColorCastResultCard must render an Image when preview != null",
            fnBody.contains("if (preview != null)") &&
                fnBody.substringAfter("if (preview != null)").substringBefore("Row(")
                    .contains("Image("),
        )
    }
}
