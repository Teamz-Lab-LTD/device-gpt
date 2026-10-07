package com.teamz.lab.debugger.utils

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hard ceiling on ad requests per install per day, kept on disk.
 *
 * The in-memory per-session budgets reset whenever the process starts, so they cannot stop a
 * loop that restarts the process. On 2026-09-19..20 one phone sent ~1,279 native requests in two
 * days (two thirds of the month) and saw 2 ads; the cause was never reproduced. Whatever it was,
 * this ceiling bounds it. A real user needs a handful of native ads a day; the cap is far above
 * that and far below a loop.
 */
object AdDailyCap {
    internal const val PREFS = "ad_daily_cap"
    const val NATIVE_PER_DAY = 30

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun limit(format: String): Int = when (format) {
        "native" -> NATIVE_PER_DAY
        else -> Int.MAX_VALUE
    }

    fun count(format: String, day: String = today()): Int {
        val p = prefs ?: return 0
        return if (p.getString("${format}_day", null) == day) p.getInt("${format}_count", 0) else 0
    }

    /** Not initialised = no disk = allow; the in-memory budgets still apply. */
    fun allow(format: String, day: String = today()): Boolean = count(format, day) < limit(format)

    fun record(format: String, day: String = today()) {
        val p = prefs ?: return
        val next = count(format, day) + 1
        p.edit().putString("${format}_day", day).putInt("${format}_count", next).apply()
        if (next == limit(format)) {
            AnalyticsUtils.logEvent(AnalyticsEvent.AdFailed, mapOf("ad_type" to format, "error_message" to "daily_cap_reached"))
        }
    }
}
