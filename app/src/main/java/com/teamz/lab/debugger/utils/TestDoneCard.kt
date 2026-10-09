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

    /**
     * Returns true when this call opened the card, so a caller about to show an interstitial can
     * skip it instead of covering the card. fs_done_card_shown is logged by TestDoneSheet when the
     * dialog is actually on screen, not here.
     */
    fun onTestCompleted(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!shouldShow(FirstScreenExperiment.isB(context), p.getBoolean(KEY_SHOWN, false))) return false
        p.edit().putBoolean(KEY_SHOWN, true).apply()
        shownLogPending = true
        _visible.value = true
        return true
    }

    @Volatile private var shownLogPending = false

    /** True once per showing: the sheet's LaunchedEffect re-runs on rotation (re-review minor 5). */
    fun claimShownLog(): Boolean {
        if (!shownLogPending) return false
        shownLogPending = false
        return true
    }

    fun dismiss() { _visible.value = false }
}
