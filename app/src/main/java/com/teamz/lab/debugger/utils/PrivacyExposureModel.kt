package com.teamz.lab.debugger.utils

/**
 * The pure half of the App Privacy audit: facts in, verdict out, no Android types.
 *
 * WHY THIS FILE EXISTS
 * Five of the six checks in ZeroTrustScorer's App Privacy section could not produce a true
 * finding, and the store listing advertised four of them by name. Each was a hardcoded list
 * of invented package names — "com.spy.keylogger", "com.hidden.screenrecorder",
 * "com.sneaky.keylogger" — matched against installed packages. Real monitoring software does
 * not ship under those ids, and on Android 11+ the set being searched (launcher-visible apps)
 * structurally excludes anything that hid its icon, which is the whole definition of the
 * thing being looked for. So the answer was "clean" on every device regardless of what was
 * installed. Same shape as the per-app battery claim that drew a Play Deceptive Behavior
 * rejection in a44b84b.
 *
 * The replacement detects MECHANISMS instead of names. An app cannot read your keystrokes
 * without being an enabled input method or an accessibility service that filters key events;
 * it cannot scrape your screen without accessibility window-content access or a media
 * projection; it cannot track you across apps without the advertising id. Those are all
 * enumerable, they do not depend on knowing a package name in advance, and — importantly —
 * the system services that list them are not subject to Android 11 package-visibility
 * filtering, so the answer is the same on a modern phone as an old one.
 *
 * Verdicts stay deliberately mild. A third-party keyboard is a normal thing to install and
 * calling SwiftKey a keylogger would just be a different wrong answer, in the same family as
 * the one being fixed. ELEVATED is reserved for capabilities that are genuinely rare outside
 * monitoring software. Everything else is INFORMATIONAL: here is what this can see, look at
 * it yourself.
 */

/** An enabled keyboard. Whatever is typed anywhere on the device passes through it. */
data class KeyboardFact(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

/** An enabled accessibility service, described by what it is actually allowed to do. */
data class AccessibilityFact(
    val packageName: String,
    val label: String,
    val canFilterKeyEvents: Boolean,
    val canRetrieveWindowContent: Boolean,
    val canTakeScreenshot: Boolean,
    val isSystem: Boolean,
)

/** One installed app, with the permissions it actually HOLDS (not merely requests). */
data class AppPermissionFact(
    val packageName: String,
    val label: String,
    val grantedSensitive: List<String>,
    val holdsAdId: Boolean,
    val holdsMediaProjection: Boolean,
)

enum class ExposureLevel { NONE, INFORMATIONAL, ELEVATED }

/**
 * @param items one line per thing found, named so the user can go and look at it. Empty on NONE.
 */
data class ExposureFinding(
    val level: ExposureLevel,
    val headline: String,
    val items: List<String> = emptyList(),
    val recommendation: String? = null,
) {
    val isClean: Boolean get() = level == ExposureLevel.NONE

    /** Rendered for the text surfaces (Device Info rows, AppFunctions, AI prompt). */
    fun render(): String {
        val icon = when (level) {
            ExposureLevel.NONE -> "✅"
            ExposureLevel.INFORMATIONAL -> "ℹ️"
            ExposureLevel.ELEVATED -> "⚠️"
        }
        val body = if (items.isEmpty()) "" else "\n" + items.joinToString("\n") { "• $it" }
        val advice = recommendation?.let { "\n$it" } ?: ""
        return "$icon $headline$body$advice"
    }
}

private fun describe(pkg: String, label: String): String =
    if (label.isBlank() || label == pkg) pkg else "$label ($pkg)"

/**
 * Who can see your keystrokes.
 *
 * Two real mechanisms, and only two. A keyboard receives every character by design. An
 * accessibility service that requested key-event filtering receives them before the focused
 * app does — that is the API stalkerware uses, and almost nothing legitimate needs it.
 *
 * A non-system keyboard is reported, never accused: it is INFORMATIONAL, because installing
 * Gboard on a Samsung or SwiftKey anywhere produces exactly this state.
 */
fun assessKeystrokeExposure(
    keyboards: List<KeyboardFact>,
    accessibility: List<AccessibilityFact>,
): ExposureFinding {
    val keyFilterers = accessibility.filter { it.canFilterKeyEvents && !it.isSystem }
    val thirdPartyKeyboards = keyboards.filter { !it.isSystem }

    if (keyFilterers.isNotEmpty()) {
        return ExposureFinding(
            level = ExposureLevel.ELEVATED,
            headline = "${keyFilterers.size} accessibility service(s) can intercept key presses " +
                "before the app you are typing into receives them",
            items = keyFilterers.map { describe(it.packageName, it.label) } +
                thirdPartyKeyboards.map { describe(it.packageName, it.label) + " — keyboard" },
            recommendation = "Very little legitimate software needs this. Review it in " +
                "Settings → Accessibility → Installed services and turn off anything you did " +
                "not set up yourself.",
        )
    }
    if (thirdPartyKeyboards.isNotEmpty()) {
        return ExposureFinding(
            level = ExposureLevel.INFORMATIONAL,
            headline = "${thirdPartyKeyboards.size} third-party keyboard(s) enabled — a keyboard " +
                "sees everything you type, including passwords",
            items = thirdPartyKeyboards.map { describe(it.packageName, it.label) },
            recommendation = "Normal if you installed these on purpose. Settings → System → " +
                "Languages & input → On-screen keyboard lists every one.",
        )
    }
    return ExposureFinding(
        level = ExposureLevel.NONE,
        headline = "Only the keyboard that shipped with your phone is enabled, and no " +
            "accessibility service can read key presses.",
    )
}

/**
 * Who can see your screen.
 *
 * Accessibility window-content access reads the text of everything on screen — the mechanism
 * behind screen-scraping monitoring tools and banking trojans alike. Screenshot capability
 * (Android 11+) is stronger still. Holding the media-projection permission means an app is
 * able to ask to record the screen; that is what every legitimate screen recorder does, so it
 * is reported as information, not as a threat.
 */
fun assessScreenCaptureExposure(
    accessibility: List<AccessibilityFact>,
    apps: List<AppPermissionFact>,
): ExposureFinding {
    val readers = accessibility.filter {
        !it.isSystem && (it.canRetrieveWindowContent || it.canTakeScreenshot)
    }
    val recorders = apps.filter { it.holdsMediaProjection }

    if (readers.isNotEmpty()) {
        return ExposureFinding(
            level = ExposureLevel.ELEVATED,
            headline = "${readers.size} accessibility service(s) can read the contents of your " +
                "screen in any app",
            items = readers.map {
                val what = if (it.canTakeScreenshot) "screen content + screenshots" else "screen content"
                describe(it.packageName, it.label) + " — $what"
            } + recorders.map { describe(it.packageName, it.label) + " — can request screen recording" },
            recommendation = "Settings → Accessibility → Installed services. Anything here you " +
                "did not enable yourself should be turned off.",
        )
    }
    if (recorders.isNotEmpty()) {
        return ExposureFinding(
            level = ExposureLevel.INFORMATIONAL,
            headline = "${recorders.size} app(s) can ask to record your screen",
            items = recorders.map { describe(it.packageName, it.label) },
            recommendation = "Android always shows a confirmation dialog before recording " +
                "starts, so this cannot happen without you tapping Allow.",
        )
    }
    return ExposureFinding(
        level = ExposureLevel.NONE,
        headline = "No app can read your screen content or record your screen.",
    )
}

/**
 * Cross-app ad tracking, measured rather than guessed.
 *
 * The old version searched installed package NAMES for SDK ids like "com.google.ads". A
 * package name never contains the id of an SDK bundled inside it, so the check found nothing,
 * on any device, ever — while the result sat behind a premium teaser promising "the full list
 * of SDKs tracking you".
 *
 * What is actually observable is the permission: an app that reads the cross-app advertising
 * identifier must hold com.google.android.gms.permission.AD_ID. That is the tracking, and it
 * is enumerable.
 */
fun assessAdTrackingExposure(apps: List<AppPermissionFact>): ExposureFinding {
    val trackers = apps.filter { it.holdsAdId }.sortedBy { it.label.lowercase() }
    if (trackers.isEmpty()) {
        return ExposureFinding(
            level = ExposureLevel.NONE,
            headline = "No app on this device holds permission to read your advertising ID.",
        )
    }
    return ExposureFinding(
        level = ExposureLevel.INFORMATIONAL,
        headline = "${trackers.size} app(s) can read your advertising ID — the identifier that " +
            "lets ad networks link what you do in one app to another",
        items = trackers.map { describe(it.packageName, it.label) },
        recommendation = "You can switch it off for all of them at once: Settings → Privacy → " +
            "Ads → Delete advertising ID.",
    )
}

/**
 * Apps holding the permissions people care about, per app.
 *
 * The version this replaces called checkSelfPermission(context, permission) inside a loop over
 * installed apps. That call takes no package argument — it answers for the CALLING app. Since
 * DeviceGPT itself holds RECORD_AUDIO, CAMERA and location, the loop returned every installed
 * app as a permission abuser on every device. The same mistake existed twice, in
 * detectDangerousPermissions and getPermissionHeatmap.
 */
fun assessSensitivePermissionExposure(apps: List<AppPermissionFact>): ExposureFinding {
    val holders = apps.filter { it.grantedSensitive.isNotEmpty() }
        .sortedByDescending { it.grantedSensitive.size }
    if (holders.isEmpty()) {
        return ExposureFinding(
            level = ExposureLevel.NONE,
            headline = "No app currently holds microphone, camera, location, SMS, call-log or " +
                "contacts access.",
        )
    }
    return ExposureFinding(
        level = ExposureLevel.INFORMATIONAL,
        headline = "${holders.size} app(s) currently hold sensitive permissions you granted",
        items = holders.take(25).map {
            describe(it.packageName, it.label) + " — " + it.grantedSensitive.joinToString(", ")
        } + if (holders.size > 25) listOf("…and ${holders.size - 25} more") else emptyList(),
        recommendation = "Settings → Privacy → Permission manager groups these by permission " +
            "so you can revoke one across every app at once.",
    )
}
