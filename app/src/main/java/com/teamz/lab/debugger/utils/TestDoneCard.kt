package com.teamz.lab.debugger.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One-time "Done ✓" card after the first completed test, arm B only. See spec 2026-10-09. */
object TestDoneCard {
    internal const val PREFS = "test_done_card"
    private const val KEY_SHOWN = "shown"

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible

    fun shouldShow(isB: Boolean, alreadyShown: Boolean): Boolean = isB && !alreadyShown

    fun onTestCompleted(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!shouldShow(FirstScreenExperiment.isB(context), p.getBoolean(KEY_SHOWN, false))) return
        p.edit().putBoolean(KEY_SHOWN, true).apply()
        _visible.value = true
        try { AnalyticsUtils.logEvent(AnalyticsEvent.FsDoneCardShown) } catch (_: Throwable) { }
    }

    fun dismiss() { _visible.value = false }
}
