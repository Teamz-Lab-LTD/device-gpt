package com.teamz.lab.debugger.ui.adaptive

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * The bottom floating buttons (PRO / Cert / AI / Send) sit on top of every tab's content. On a
 * Pixel 8a (2026-10-05) they covered the streak labels and the Memory Usage card on the Health tab
 * and could not be moved out of the way. They now slide away while the user scrolls down to read
 * and come back on the first scroll up — the usual pattern for floating actions over a list.
 */
object FabScrollPolicy {
    /** Pixels of finger travel before the buttons react, so a tap-jitter does not flicker them. */
    const val THRESHOLD_PX = 12f

    /** [dy] is the nested-scroll delta: negative when the finger moves up (reading further down). */
    fun next(visible: Boolean, dy: Float): Boolean = when {
        dy < -THRESHOLD_PX -> false
        dy > THRESHOLD_PX -> true
        else -> visible
    }
}

class FabScrollConnection(private val onVisible: (Boolean) -> Unit, private val current: () -> Boolean) :
    NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val next = FabScrollPolicy.next(current(), available.y)
        if (next != current()) onVisible(next)
        return Offset.Zero // observe only, never consume
    }
}
