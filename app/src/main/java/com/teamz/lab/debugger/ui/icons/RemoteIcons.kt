package com.teamz.lab.debugger.ui.icons

import androidx.annotation.DrawableRes
import com.teamz.lab.debugger.R

/**
 * Icons for the surfaces Compose cannot reach: notifications and the home-screen widget (`RemoteViews`).
 *
 * Those surfaces cannot draw an `ImageVector`, and they must not draw an emoji either. The text they show is
 * built far away and still starts with its emoji, which stays as data. Here that leading emoji picks one of the
 * `res/drawable/ic_dg_*.xml` vectors (the same shapes as [DgIcons], as XML), and [stripEmoji] cleans the text.
 * No Android UI types are used, so this runs in a plain unit test.
 */
object RemoteIcons {

    private val HEAT_WORDS = listOf("°C", "°F", "temp", "hot", "heat", "warm", "গরম", "তাপ")

    private val table: Map<String, Int> = mapOf(
        "🔋" to R.drawable.ic_dg_battery,
        "🔌" to R.drawable.ic_dg_bolt,
        "⚡" to R.drawable.ic_dg_bolt,
        "🌡" to R.drawable.ic_dg_temperature,
        "📊" to R.drawable.ic_dg_bar_chart,
        "📈" to R.drawable.ic_dg_chart,
        "📉" to R.drawable.ic_dg_chart_down,
        "🧠" to R.drawable.ic_dg_memory,
        "💾" to R.drawable.ic_dg_storage,
        "📶" to R.drawable.ic_dg_signal,
        "✅" to R.drawable.ic_dg_check,
        "⚠" to R.drawable.ic_dg_warning,
        "🚨" to R.drawable.ic_dg_warning,
        "📱" to R.drawable.ic_dg_phone,
        "🎤" to R.drawable.ic_dg_mic,
        "📷" to R.drawable.ic_dg_camera,
        "🔐" to R.drawable.ic_dg_lock,
        "🔒" to R.drawable.ic_dg_lock,
        "📋" to R.drawable.ic_dg_report,
        "🏆" to R.drawable.ic_dg_trophy,
        "🎉" to R.drawable.ic_dg_trophy,
        "⭐" to R.drawable.ic_dg_star,
        "✨" to R.drawable.ic_dg_star,
        "🎯" to R.drawable.ic_dg_target,
        "📦" to R.drawable.ic_dg_app,
        "⚙" to R.drawable.ic_dg_cpu,
        "💡" to R.drawable.ic_dg_tip,
        "🛡" to R.drawable.ic_dg_shield,
    )

    /**
     * The drawable for the emoji at the start of [text], or [fallback] when there is none or it has no drawable.
     * The flame is a thermometer on a line about heat and the streak flame otherwise.
     */
    @DrawableRes
    fun forText(text: String, @DrawableRes fallback: Int): Int {
        var index = 0
        while (index < text.length && text[index].isWhitespace()) index++
        val length = EmojiText.clusterLengthAt(text, index)
        if (length == 0) return fallback
        val key = EmojiText.normalize(text.substring(index, index + length))
        if (key == "🔥") {
            val heat = HEAT_WORDS.any { text.contains(it, ignoreCase = true) }
            return if (heat) R.drawable.ic_dg_temperature else R.drawable.ic_dg_streak
        }
        return table[key] ?: fallback
    }

    /**
     * [text] for a notification or a widget row: no emoji, and no trailing "go" arrow (the surface draws its own
     * arrow or needs none). Arrows inside a sentence, such as "62% → 100%", stay.
     */
    fun plain(text: String): String = stripEmoji(text).trimEnd().removeSuffix("→").trimEnd()
}
