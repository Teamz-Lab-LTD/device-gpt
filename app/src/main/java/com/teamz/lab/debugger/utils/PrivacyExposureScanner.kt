package com.teamz.lab.debugger.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager

/**
 * Collects the facts that PrivacyExposureModel reasons over.
 *
 * Everything here uses an API that answers honestly on a current Android release:
 *
 *  - InputMethodManager.enabledInputMethodList and
 *    AccessibilityManager.getEnabledAccessibilityServiceList are system-service queries. They
 *    are NOT filtered by Android 11 package visibility, so a monitoring app that hid its
 *    launcher icon still appears here the moment it enables the capability it needs in order
 *    to do anything. That is what makes this approach work where a package-name list cannot.
 *  - getPackagesHoldingPermissions() is one binder call, and it reports permissions actually
 *    held rather than merely declared.
 *
 * Package-visibility caveat, stated rather than hidden: the permission-holder queries see the
 * apps this app can see, which on Android 11+ means launcher-visible apps (the app declares a
 * LAUNCHER <intent> in <queries> and deliberately does NOT hold QUERY_ALL_PACKAGES). So the
 * ad-id and sensitive-permission lists cover apps with an icon. The keyboard and accessibility
 * checks — the two that matter for monitoring — have no such limit.
 */
object PrivacyExposureScanner {

    /** Permission -> the words a person would use for it. */
    private val SENSITIVE_PERMISSIONS = linkedMapOf(
        "android.permission.RECORD_AUDIO" to "microphone",
        "android.permission.CAMERA" to "camera",
        "android.permission.ACCESS_FINE_LOCATION" to "precise location",
        "android.permission.ACCESS_BACKGROUND_LOCATION" to "location in background",
        "android.permission.READ_SMS" to "text messages",
        "android.permission.READ_CALL_LOG" to "call history",
        "android.permission.READ_CONTACTS" to "contacts",
    )

    private const val PERMISSION_AD_ID = "com.google.android.gms.permission.AD_ID"
    private const val PERMISSION_MEDIA_PROJECTION =
        "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"

    fun collectKeyboards(context: Context): List<KeyboardFact> = runCatching {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val pm = context.packageManager
        imm.enabledInputMethodList.map { imi ->
            val appInfo = imi.serviceInfo.applicationInfo
            KeyboardFact(
                packageName = imi.packageName,
                label = runCatching { imi.loadLabel(pm).toString() }.getOrDefault(""),
                isSystem = appInfo != null && isSystemApp(appInfo),
            )
        }
    }.getOrDefault(emptyList())

    fun collectAccessibilityServices(context: Context): List<AccessibilityFact> = runCatching {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val pm = context.packageManager
        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .map { info ->
                val caps = info.capabilities
                val serviceInfo = info.resolveInfo?.serviceInfo
                AccessibilityFact(
                    packageName = serviceInfo?.packageName ?: info.id.substringBefore('/'),
                    label = runCatching { info.resolveInfo.loadLabel(pm).toString() }
                        .getOrDefault(""),
                    canFilterKeyEvents = caps and
                        AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS != 0,
                    canRetrieveWindowContent = caps and
                        AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0,
                    canTakeScreenshot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        caps and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT != 0,
                    isSystem = serviceInfo?.applicationInfo?.let { isSystemApp(it) } ?: false,
                )
            }
    }.getOrDefault(emptyList())

    /**
     * One binder call per permission set rather than one per app — the version this replaces
     * walked up to 50 apps individually and still got the answer wrong.
     */
    fun collectAppPermissions(context: Context): List<AppPermissionFact> = runCatching {
        val pm = context.packageManager
        val wanted = (SENSITIVE_PERMISSIONS.keys +
            PERMISSION_AD_ID + PERMISSION_MEDIA_PROJECTION).toTypedArray()

        @Suppress("DEPRECATION")
        val holders = pm.getPackagesHoldingPermissions(wanted, PackageManager.GET_PERMISSIONS)

        holders.mapNotNull { info ->
            if (info.packageName == context.packageName) return@mapNotNull null
            val appInfo = info.applicationInfo ?: return@mapNotNull null
            if (isSystemApp(appInfo)) return@mapNotNull null

            val granted = grantedPermissions(info)
            val sensitive = SENSITIVE_PERMISSIONS.entries
                .filter { it.key in granted }
                .map { it.value }
            val fact = AppPermissionFact(
                packageName = info.packageName,
                label = runCatching { appInfo.loadLabel(pm).toString() }.getOrDefault(""),
                grantedSensitive = sensitive,
                holdsAdId = PERMISSION_AD_ID in granted,
                holdsMediaProjection = PERMISSION_MEDIA_PROJECTION in granted,
            )
            if (sensitive.isEmpty() && !fact.holdsAdId && !fact.holdsMediaProjection) null else fact
        }
    }.getOrDefault(emptyList())

    /**
     * Normal permissions (AD_ID, the foreground-service ones) are granted at install and carry
     * the GRANTED flag; runtime permissions carry it only once the user has said yes. Reading
     * the flag rather than the requested list is what makes "holds" mean holds.
     */
    private fun grantedPermissions(info: PackageInfo): Set<String> {
        val names = info.requestedPermissions ?: return emptySet()
        @Suppress("DEPRECATION")
        val flags = info.requestedPermissionsFlags
        return names.filterIndexed { i, _ ->
            flags == null || i >= flags.size ||
                (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
        }.toSet()
    }

    private fun isSystemApp(info: ApplicationInfo): Boolean =
        (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    // --- The four findings, each one fact-collection plus a pure decision ---

    fun keystrokeExposure(context: Context): ExposureFinding =
        assessKeystrokeExposure(collectKeyboards(context), collectAccessibilityServices(context))

    fun screenCaptureExposure(context: Context): ExposureFinding =
        assessScreenCaptureExposure(collectAccessibilityServices(context), collectAppPermissions(context))

    fun adTrackingExposure(context: Context): ExposureFinding =
        assessAdTrackingExposure(collectAppPermissions(context))

    fun sensitivePermissionExposure(context: Context): ExposureFinding =
        assessSensitivePermissionExposure(collectAppPermissions(context))

    /**
     * Replaces the "offline malware signature scan", which compared installed packages against
     * three invented ids (com.spy.fakeapp, com.sneaky.keylogger, com.hidden.sniffer) and so
     * reported a clean device unconditionally. DeviceGPT has no malware corpus and cannot
     * honestly claim signature detection.
     *
     * What it can measure is where an app came from. Sideloading is not malicious in itself —
     * F-Droid and enterprise deployment are ordinary — but it is the route malware takes, and
     * it is a real, checkable fact rather than a fictional list.
     */
    fun installSourceExposure(context: Context): ExposureFinding {
        val pm = context.packageManager
        val outside = getInstalledApps(context).mapNotNull { pkg ->
            if (pkg == context.packageName) return@mapNotNull null
            val installer = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pm.getInstallSourceInfo(pkg).installingPackageName
                } else {
                    @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
                }
            }.getOrNull()
            if (installer in KNOWN_STORES) return@mapNotNull null
            val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                ?: return@mapNotNull null
            if (isSystemApp(appInfo)) return@mapNotNull null
            val label = runCatching { appInfo.loadLabel(pm).toString() }.getOrDefault(pkg)
            "$label ($pkg)" + (installer?.let { " — installed by $it" } ?: " — no install source recorded")
        }

        if (outside.isEmpty()) {
            return ExposureFinding(
                level = ExposureLevel.NONE,
                headline = "Every app you installed came from an app store, so Play Protect " +
                    "scanned it.",
            )
        }
        return ExposureFinding(
            level = ExposureLevel.INFORMATIONAL,
            headline = "${outside.size} app(s) were installed from outside an app store",
            items = outside.take(20),
            recommendation = "Sideloading is not malicious by itself, but these skipped store " +
                "review. Play Store → profile → Play Protect → Scan will check them.",
        )
    }

    private val KNOWN_STORES = setOf(
        "com.android.vending",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.amazon.venezia",
        "com.sec.android.app.samsungapps",
        "com.huawei.appmarket",
        "com.xiaomi.market",
        "com.oppo.market",
        "com.heytap.market",
        "com.vivo.appstore",
        "com.farsitel.bazaar",
        "org.fdroid.fdroid",
    )
}
