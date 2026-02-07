package com.teamz.lab.debugger.utils

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Device integrity checks that are missing from the existing codebase:
 * - Overlay (draw-over-other-apps) permission abuse
 * - Suspicious notification listener services
 * - Unknown sources (sideloading) enabled
 *
 * All checks are read-only and informational.
 */
object DeviceIntegrityChecker {

    /**
     * Check if any non-system app has overlay (draw over other apps) permission.
     * Returns true if overlay is enabled globally (Android < 6.0 always true).
     */
    fun isOverlayPermissionEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            // Pre-Marshmallow: overlay was granted by default
            true
        }
    }

    /**
     * Get the list of enabled notification listener services.
     * Returns a list of package/component names, or empty if none.
     */
    fun getEnabledNotificationListeners(context: Context): List<String> {
        return try {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            if (flat.isNullOrBlank()) {
                emptyList()
            } else {
                flat.split(":").filter { it.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Check if unknown sources (sideloading) is enabled.
     * On Android 8.0+ this is per-app, so we check the global setting
     * which may not be available -- in that case we return false.
     */
    fun isUnknownSourcesEnabled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // On Android 8+, per-app installs from unknown sources.
                // We can only check if our own app has it, or use the legacy setting.
                // The legacy setting may still be present on some devices.
                @Suppress("DEPRECATION")
                val legacy = Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.INSTALL_NON_MARKET_APPS,
                    0
                )
                legacy == 1
            } else {
                @Suppress("DEPRECATION")
                val value = Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.INSTALL_NON_MARKET_APPS,
                    0
                )
                value == 1
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Get a human-readable summary of notification listeners.
     */
    fun getNotificationListenersSummary(context: Context): String {
        val listeners = getEnabledNotificationListeners(context)
        return if (listeners.isEmpty()) {
            "No notification listener services enabled"
        } else {
            "${listeners.size} service(s) enabled:\n" +
                listeners.joinToString("\n") { "  - ${it.substringBefore("/").substringAfterLast(".")}" }
        }
    }
}
