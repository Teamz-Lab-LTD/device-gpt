package com.teamz.lab.debugger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.teamz.lab.debugger.utils.ChargeCycleTracker

/**
 * Manifest-registered receiver for ACTION_POWER_CONNECTED and ACTION_POWER_DISCONNECTED.
 * Both broadcasts are on Android's whitelist of implicit broadcasts NOT subject to the
 * Android 8+ background-restriction list, so this fires even when the app is killed.
 */
class ChargeEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> ChargeCycleTracker.onPowerConnected(context)
            Intent.ACTION_POWER_DISCONNECTED -> ChargeCycleTracker.onPowerDisconnected(context)
        }
    }
}
