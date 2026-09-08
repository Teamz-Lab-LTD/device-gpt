package com.teamz.lab.debugger.privacy

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teamz.lab.debugger.utils.PrivacyExposureScanner
import com.teamz.lab.debugger.utils.detectAdTrackingApps
import com.teamz.lab.debugger.utils.detectHiddenApps
import com.teamz.lab.debugger.utils.detectKeylogger
import com.teamz.lab.debugger.utils.detectOfflineMalware
import com.teamz.lab.debugger.utils.detectScreenRecordingApps
import com.teamz.lab.debugger.utils.detectSuspiciousAccessibilityServices
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the privacy collectors on a real device and asserts the ones with a knowable ground
 * truth. The JVM tests cover the decision logic; this covers the part they cannot — whether the
 * Android APIs behind the facts actually answer.
 *
 * That gap is not hypothetical. Every collector wraps its query in
 * runCatching { ... }.getOrDefault(emptyList()), so a thrown SecurityException or a
 * package-visibility refusal returns an empty list, which the model then reports as "clean".
 * A check that reports a refusal as a pass is the exact failure being fixed — a hardcoded list
 * that could never match also reported "clean" on every device. So the structural invariants
 * below are the guard: there is no such thing as an Android device with no enabled keyboard, and
 * no real phone where nothing holds camera, microphone or location.
 *
 *   adb: ./gradlew :app:connectedDebugAndroidTest \
 *          -Pandroid.testInstrumentationRunnerArguments.class=\
 *          com.teamz.lab.debugger.privacy.PrivacyExposureDeviceProbeTest
 */
@RunWith(AndroidJUnit4::class)
class PrivacyExposureDeviceProbeTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val tag = "PrivacyProbe"

    @Test
    fun theKeyboardQueryActuallyAnswers() {
        val keyboards = PrivacyExposureScanner.collectKeyboards(context)
        keyboards.forEach { Log.i(tag, "keyboard: $it") }
        assertTrue(
            "collectKeyboards returned nothing. No Android device has zero enabled input " +
                "methods, so this is the runCatching swallowing a failure and the model will " +
                "report a clean device on a broken query.",
            keyboards.isNotEmpty(),
        )
    }

    @Test
    fun thePermissionHolderQueryActuallyAnswers() {
        val apps = PrivacyExposureScanner.collectAppPermissions(context)
        Log.i(tag, "permission-holding apps: ${apps.size}")
        apps.take(40).forEach { Log.i(tag, "  $it") }
        assertTrue(
            "getPackagesHoldingPermissions returned no app holding camera, mic, location, SMS, " +
                "contacts, call log, AD_ID or media projection. On a real phone that cannot be " +
                "true — the query is being refused and reported as a clean result.",
            apps.isNotEmpty(),
        )
    }

    @Test
    fun adTrackingFindsHoldersOfTheAdvertisingId() {
        val apps = PrivacyExposureScanner.collectAppPermissions(context)
        val adIdHolders = apps.filter { it.holdsAdId }
        Log.i(tag, "AD_ID holders: ${adIdHolders.size} -> ${adIdHolders.map { it.packageName }}")
        assertTrue(
            "No app holds com.google.android.gms.permission.AD_ID. The check this replaced " +
                "returned exactly this answer on every device because it matched SDK ids " +
                "against package names, and the empty result was sold behind a premium teaser. " +
                "If this now fails on a real phone with ad-supported apps installed, the " +
                "replacement is no better than what it replaced.",
            adIdHolders.isNotEmpty(),
        )
    }

    /** Not an assertion — a record of what each surface actually prints on this device. */
    @Test
    fun printEveryPrivacySurface() {
        listOf(
            "keystroke" to detectKeylogger(context),
            "screen" to detectScreenRecordingApps(context),
            "adTracking" to detectAdTrackingApps(context),
            "installSource" to detectOfflineMalware(context),
            "accessibility" to detectSuspiciousAccessibilityServices(context),
            "hiddenApps" to detectHiddenApps(context),
        ).forEach { (name, out) -> Log.i(tag, "=== $name ===\n$out") }
    }
}
