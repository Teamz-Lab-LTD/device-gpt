package com.teamz.lab.debugger.utils

/**
 * When the Real-time Monitor may run an active speed test.
 *
 * Until 2026-10-05 SystemMonitorService downloaded 10 MB and uploaded 2 MB on every 30-second
 * pass — about 1.4 GB an hour, on mobile data as much as on Wi-Fi. Now: at most every
 * 30 minutes, and only on an unmetered network. Between tests the last result is reused.
 */
object SpeedTestPolicy {
    const val MIN_INTERVAL_MS = 30 * 60_000L
    const val NOT_MEASURED = "Not measured"

    fun shouldRun(nowMs: Long, lastRunMs: Long, unmetered: Boolean): Boolean =
        unmetered && (lastRunMs <= 0L || nowMs - lastRunMs >= MIN_INTERVAL_MS)

    /**
     * A cached result describes the network it was measured on, for a while. Reused forever, a
     * 200 Mbps Wi-Fi result kept showing on mobile data under a fresh timestamp.
     */
    fun reuse(cached: String, measuredAtMs: Long, measuredOn: String?, currentNetwork: String?, nowMs: Long): String =
        if (measuredOn != null && measuredOn == currentNetwork && nowMs - measuredAtMs in 0 until MIN_INTERVAL_MS) cached
        else NOT_MEASURED
}
