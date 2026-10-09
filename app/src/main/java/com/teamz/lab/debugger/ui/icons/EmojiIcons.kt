package com.teamz.lab.debugger.ui.icons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Factory
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Recycling
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.teamz.lab.debugger.ui.icons.IconTone.Accent
import com.teamz.lab.debugger.ui.icons.IconTone.Bad
import com.teamz.lab.debugger.ui.icons.IconTone.Good
import com.teamz.lab.debugger.ui.icons.IconTone.Neutral
import com.teamz.lab.debugger.ui.icons.IconTone.Warn
import com.teamz.lab.debugger.ui.theme.dgSemanticColors

/** What an icon says about its row, which decides its colour. The icon's shape always differs too. */
enum class IconTone {
    /** No judgement: the same colour as the text beside it. */
    Neutral,

    /** A pass, a healthy value. */
    Good,

    /** Needs attention. */
    Warn,

    /** A failure, a problem. */
    Bad,

    /** A highlight in the brand accent: rewards, tips, energy. */
    Accent,
}

/** The vector that replaces an emoji, with the tone it carries. */
data class EmojiIcon(val icon: ImageVector, val tone: IconTone)

/**
 * The one table from every emoji the app has used as an icon to the vector that replaces it.
 *
 * An emoji mapped to `null` is decorative and is simply dropped. Keys are stored without the U+FE0F
 * variation selector and without skin tones, so each emoji needs one entry (see [EmojiText.normalize]).
 * `EmojiIconsTest` scans the source tree and fails when an emoji appears that has no entry here.
 */
object EmojiIcons {

    /** Status mark for a pass. */
    val StatusGood: EmojiIcon by lazy { EmojiIcon(Icons.Rounded.CheckCircle, Good) }

    /** Status mark for something that needs attention. */
    val StatusWarn: EmojiIcon by lazy { EmojiIcon(Icons.Rounded.Warning, Warn) }

    /** Status mark for a failure. */
    val StatusBad: EmojiIcon by lazy { EmojiIcon(Icons.Rounded.Cancel, Bad) }

    private val table: Map<String, EmojiIcon?> by lazy { buildTable() }

    /** True when [emoji] (with or without a variation selector) has an entry, including "drop" entries. */
    fun isKnown(emoji: String): Boolean = table.containsKey(EmojiText.normalize(emoji))

    /** The icon for [emoji], or `null` when it is decorative or unknown. */
    fun lookup(emoji: String): EmojiIcon? = table[EmojiText.normalize(emoji)]

    /** True when [emoji] is known and deliberately mapped to nothing. */
    fun isDropped(emoji: String): Boolean {
        val key = EmojiText.normalize(emoji)
        return table.containsKey(key) && table[key] == null
    }

    /** Every table key (normalized emoji). */
    val knownEmoji: Set<String> get() = table.keys

    @Suppress("LongMethod")
    private fun buildTable(): Map<String, EmojiIcon?> {
        val map = LinkedHashMap<String, EmojiIcon?>()
        fun put(icon: ImageVector, tone: IconTone, vararg emoji: String) {
            val entry = EmojiIcon(icon, tone)
            emoji.forEach { map[EmojiText.normalize(it)] = entry }
        }
        fun drop(vararg emoji: String) = emoji.forEach { map[EmojiText.normalize(it)] = null }

        // Status marks. Shape and colour both change, so the meaning never rests on colour alone.
        put(Icons.Rounded.CheckCircle, Good, "✅")
        put(Icons.Rounded.Check, Good, "✓")
        put(Icons.Rounded.Cancel, Bad, "❌")
        put(Icons.Rounded.Warning, Warn, "⚠️")
        put(Icons.Rounded.Warning, Bad, "🚨")
        put(Icons.Rounded.PriorityHigh, Warn, "❗")
        put(Icons.Rounded.Block, Bad, "🚫", "⛔")
        put(Icons.Rounded.Dangerous, Bad, "☠️", "💀")
        put(Icons.AutoMirrored.Rounded.HelpOutline, Neutral, "❓")
        put(Icons.Rounded.ThumbUp, Good, "👍")
        put(Icons.Rounded.Circle, Good, "🟢")
        put(Icons.Rounded.Circle, Warn, "🟡")
        put(Icons.Rounded.Circle, Bad, "🔴")
        put(Icons.Rounded.Circle, Neutral, "⚪")
        // The only blue circle in the app is the Bluetooth row of the power tab.
        put(Icons.Rounded.Bluetooth, Neutral, "🔵")
        put(Icons.Rounded.Favorite, Good, "💚")
        put(Icons.Rounded.Favorite, Warn, "💛", "🧡")
        put(Icons.Rounded.Favorite, Bad, "❤️")

        // The app's own ideas.
        put(DgIcons.Phone, Neutral, "📱")
        put(DgIcons.Camera, Neutral, "📷", "📸")
        put(DgIcons.Mic, Neutral, "🎤", "🎙️")
        put(DgIcons.Display, Neutral, "📺", "🖥️")
        put(DgIcons.Battery, Neutral, "🔋")
        put(DgIcons.ChargeFlow, Neutral, "🔌")
        put(DgIcons.Bolt, Accent, "⚡")
        put(DgIcons.Signal, Neutral, "📶")
        put(DgIcons.Sensor, Neutral, "📡")
        put(DgIcons.Connection, Neutral, "🧩")
        put(DgIcons.BarChart, Neutral, "📊")
        put(DgIcons.Chart, Neutral, "📈")
        put(DgIcons.ChartDown, Neutral, "📉")
        put(DgIcons.Cpu, Neutral, "🧠", "📟")
        put(DgIcons.Storage, Neutral, "💾", "📀", "💿")
        put(DgIcons.Temperature, Neutral, "🌡️")
        put(DgIcons.Streak, Warn, "🔥")
        put(DgIcons.PrivacyShield, Neutral, "🛡️", "🔰")
        put(DgIcons.Lock, Neutral, "🔒", "🔐")
        put(DgIcons.Health, Neutral, "🏥")
        put(DgIcons.AiHelp, Neutral, "🤖")
        put(DgIcons.Tip, Accent, "💡")
        put(DgIcons.Info, Neutral, "ℹ️")
        put(DgIcons.Trophy, Accent, "🏆")
        put(DgIcons.Medal, Accent, "🥇", "🥈", "🥉", "🏅")
        put(DgIcons.Star, Accent, "⭐", "🌟")
        put(DgIcons.Target, Neutral, "🎯")
        put(DgIcons.Calendar, Neutral, "📅", "📆", "🗓️")
        put(DgIcons.Clock, Neutral, "🕒", "🕰️")
        put(DgIcons.Globe, Neutral, "🌐", "🌍", "🌎")
        put(DgIcons.Download, Neutral, "📥")
        put(DgIcons.Upload, Neutral, "📤")
        put(DgIcons.Report, Neutral, "📄")
        put(DgIcons.Speed, Neutral, "⏱️")
        put(DgIcons.BuySell, Accent, "💰")

        // Generic meanings: stock Material icons.
        put(Icons.AutoMirrored.Rounded.ArrowForward, Neutral, "→")
        put(Icons.AutoMirrored.Rounded.ArrowBack, Neutral, "←")
        put(Icons.Rounded.ArrowUpward, Neutral, "↑", "🔝")
        put(Icons.Rounded.ArrowDownward, Neutral, "↓")
        put(Icons.Rounded.KeyboardArrowUp, Neutral, "🔼")
        put(Icons.Rounded.KeyboardArrowDown, Neutral, "🔽")
        put(Icons.Rounded.Refresh, Neutral, "🔄")
        put(Icons.Rounded.Repeat, Neutral, "🔁")
        put(Icons.Rounded.Search, Neutral, "🔍", "🔬")
        put(Icons.Rounded.Link, Neutral, "🔗")
        put(Icons.Rounded.Pause, Neutral, "⏸️")
        put(Icons.Rounded.SkipNext, Neutral, "⏭️")
        put(Icons.Rounded.Wifi, Neutral, "🛜")
        put(Icons.Rounded.Settings, Neutral, "⚙️")
        put(Icons.Rounded.Build, Neutral, "🔧", "🛠️", "🔩")
        put(Icons.Rounded.HourglassEmpty, Neutral, "⏳")
        put(Icons.Rounded.Alarm, Neutral, "⏰")
        put(Icons.Rounded.Timer, Neutral, "⌛")
        put(Icons.Rounded.LocationOn, Neutral, "📍")
        put(Icons.Rounded.PushPin, Neutral, "📌")
        put(Icons.Rounded.Map, Neutral, "🗺️")
        put(Icons.Rounded.Route, Neutral, "🛣️")
        put(Icons.Rounded.Explore, Neutral, "🧭")
        put(Icons.Rounded.LocationCity, Neutral, "🏙️")
        put(Icons.Rounded.SatelliteAlt, Neutral, "🛰️")
        put(Icons.Rounded.Assignment, Neutral, "📋")
        put(Icons.Rounded.Description, Neutral, "📑")
        put(Icons.Rounded.Folder, Neutral, "📂")
        put(Icons.Rounded.Inventory2, Neutral, "📦")
        put(Icons.Rounded.AttachFile, Neutral, "📎")
        put(Icons.Rounded.Bookmark, Neutral, "🔖")
        put(Icons.AutoMirrored.Rounded.Label, Neutral, "🏷️")
        put(Icons.AutoMirrored.Rounded.MenuBook, Neutral, "📚")
        put(Icons.Rounded.School, Neutral, "🎓")
        put(Icons.Rounded.Science, Neutral, "🧪")
        put(Icons.Rounded.Straighten, Neutral, "📏")
        put(Icons.Rounded.Pin, Neutral, "🔢")
        put(Icons.Rounded.TextFields, Neutral, "🔠", "🔤")
        put(Icons.Rounded.Badge, Neutral, "🆔")
        put(Icons.Rounded.NewReleases, Accent, "🆕")
        put(Icons.Rounded.Keyboard, Neutral, "⌨️")
        put(Icons.Rounded.SportsEsports, Neutral, "🎮")
        put(Icons.Rounded.Videocam, Neutral, "🎬", "🎥", "🎞️")
        put(Icons.Rounded.CameraFront, Neutral, "🤳")
        put(Icons.Rounded.Image, Neutral, "🖼️")
        put(Icons.Rounded.Landscape, Neutral, "🌄")
        put(Icons.AutoMirrored.Rounded.VolumeUp, Neutral, "🔊")
        put(Icons.AutoMirrored.Rounded.VolumeOff, Neutral, "🔇")
        put(Icons.Rounded.MusicNote, Neutral, "🎵", "🎶")
        put(Icons.Rounded.Headphones, Neutral, "🎧")
        put(Icons.Rounded.Call, Neutral, "📞")
        put(Icons.Rounded.InstallMobile, Neutral, "📲")
        put(Icons.Rounded.Notifications, Neutral, "🔔")
        put(Icons.Rounded.Campaign, Neutral, "📣")
        put(Icons.Rounded.LightMode, Neutral, "🔆", "☀️")
        put(Icons.Rounded.DarkMode, Neutral, "🌙")
        put(Icons.Rounded.Contrast, Neutral, "🌗")
        put(Icons.Rounded.Bedtime, Neutral, "💤", "😴")
        put(Icons.Rounded.FlashlightOn, Neutral, "🔦")
        put(Icons.Rounded.AcUnit, Neutral, "❄️")
        put(Icons.Rounded.Air, Neutral, "🌬️")
        put(Icons.Rounded.Recycling, Neutral, "♻️")
        put(Icons.Rounded.CleaningServices, Neutral, "🧹")
        put(Icons.Rounded.Delete, Neutral, "🗑️")
        put(Icons.Rounded.Factory, Neutral, "🏭")
        put(Icons.Rounded.Business, Neutral, "🏢")
        put(Icons.Rounded.Balance, Neutral, "⚖️")
        put(Icons.Rounded.LocalPolice, Neutral, "👮")
        put(Icons.Rounded.VisibilityOff, Neutral, "🕵️")
        put(Icons.Rounded.LockOpen, Warn, "🔓")
        put(Icons.Rounded.Person, Neutral, "👤")
        put(Icons.Rounded.Group, Neutral, "👥")
        put(Icons.Rounded.TouchApp, Neutral, "👆")
        put(Icons.Rounded.PanTool, Neutral, "🖐️")
        put(Icons.Rounded.Layers, Neutral, "🧱", "🗂️")
        put(Icons.Rounded.CropSquare, Neutral, "⬜")
        put(Icons.Rounded.StarBorder, Neutral, "☆")
        put(Icons.Rounded.Diamond, Accent, "💎")
        put(Icons.Rounded.WorkspacePremium, Accent, "👑")
        put(Icons.Rounded.CardGiftcard, Accent, "🎁")

        // Decoration with no meaning of its own: dropped.
        drop("✨", "🎉", "🚀", "💪", "😊", "🔹", "🌀", "👣")
        return map
    }
}

private fun isBlank(codePoint: Int): Boolean =
    codePoint == ' '.code || codePoint == '\t'.code || codePoint == 0x00A0 || codePoint == 0x200B

/**
 * Splits an emoji off the very start of [text].
 *
 * Leading whitespace is allowed before the emoji. Several emoji in a row are all removed, together with the
 * spaces that follow, and the icon is the first of them that has one. A decorative or unknown emoji is
 * removed and gives `null`. Text that does not start with an emoji comes back unchanged with `null`.
 *
 * ```
 * splitLeadingEmoji("⚠️ Battery is hot")   // (Warning icon in Warn tone, "Battery is hot")
 * splitLeadingEmoji("Battery is fine")     // (null, "Battery is fine")
 * ```
 */
fun splitLeadingEmoji(text: String): Pair<EmojiIcon?, String> {
    var index = 0
    while (index < text.length && text[index].isWhitespace()) index++
    if (EmojiText.clusterLengthAt(text, index) == 0) return null to text

    var icon: EmojiIcon? = null
    while (index < text.length) {
        val length = EmojiText.clusterLengthAt(text, index)
        if (length == 0) break
        if (icon == null) icon = EmojiIcons.lookup(text.substring(index, index + length))
        index += length
        while (index < text.length && isBlank(text[index].code)) index++
    }
    return icon to text.substring(index)
}

/**
 * Removes every emoji from [text] and closes the gap each one leaves, for surfaces that cannot draw vectors
 * inside text (notifications, widget rows, toasts).
 *
 * Only the spaces next to a removed emoji are touched: no doubled space, no space left at the start or end
 * of a line or before punctuation. Everything else, line breaks included, stays as written, so text without
 * emoji comes back unchanged. Text arrows such as "→" are kept: every surface can draw them.
 */
fun stripEmoji(text: String): String {
    if (text.isEmpty()) return text
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val length = EmojiText.clusterLengthAt(text, i)
        if (length == 0 || EmojiText.isArrowCluster(text.subSequence(i, i + length))) {
            val step = if (length > 0) length else Character.charCount(Character.codePointAt(text, i))
            out.append(text, i, i + step)
            i += step
            continue
        }
        i += length
        val atLineStart = out.isEmpty() || out.last() == '\n'
        val afterBlank = out.isNotEmpty() && isBlank(out.last().code)
        if (atLineStart || afterBlank) {
            while (i < text.length && isBlank(text[i].code)) i++
        }
        val atLineEnd = i >= text.length || text[i] == '\n'
        val beforeClosingMark = i < text.length && text[i] in CLOSING_MARKS
        if (atLineEnd || beforeClosingMark) {
            while (out.isNotEmpty() && isBlank(out.last().code)) out.setLength(out.length - 1)
        }
    }
    return out.toString()
}

private const val CLOSING_MARKS = ",.;:!?)।"

/** The colour for [tone] on the current theme. Each one keeps at least 4.5:1 on the app's surfaces. */
@Composable
@ReadOnlyComposable
fun toneColor(tone: IconTone): Color = when (tone) {
    Neutral -> MaterialTheme.colorScheme.onSurface
    Good -> dgSemanticColors().good
    Warn -> dgSemanticColors().warn
    Bad -> MaterialTheme.colorScheme.error
    Accent -> dgSemanticColors().accent
}

/** Width of the fixed icon slot in [DgLabelRow]. */
val DgIconSlot: Dp = 24.dp

/**
 * Draws an [EmojiIcon] in its tone colour.
 *
 * @param contentDescription pass text only when the icon is the sole carrier of the meaning; leave `null`
 * when a label sits beside it.
 */
@Composable
fun DgToneIcon(
    icon: EmojiIcon,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = toneColor(icon.tone),
    contentDescription: String? = null,
) {
    Icon(
        imageVector = icon.icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/**
 * One line of "icon, then text" where the icon comes from the emoji at the start of [text].
 *
 * The icon sits in a fixed [DgIconSlot] wide slot, centred on the first line of text, so a column of these
 * rows lines up whether or not every row has an icon.
 *
 * @param text raw text, possibly starting with an emoji. The emoji is never drawn.
 * @param style text style; its line height sets the slot height.
 * @param color text colour. Neutral icons use it too, so text and icon match.
 * @param iconTint overrides the tone colour, for rows drawn on a coloured background.
 * @param reserveIconSlot keep the empty slot when the row has no icon, so rows stay aligned.
 * @param trailing optional content at the end of the row, such as a value or a chevron.
 */
@Composable
fun DgLabelRow(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    iconTint: Color? = null,
    iconSize: Dp = 20.dp,
    reserveIconSlot: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val (icon, label) = remember(text) { splitDisplayLine(text) }
    val lineHeight = with(LocalDensity.current) {
        if (style.lineHeight.isSpecified && style.lineHeight.isSp) style.lineHeight.toDp() else DgIconSlot
    }
    val slotHeight = if (lineHeight > iconSize) lineHeight else iconSize
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        if (icon != null || reserveIconSlot) {
            Box(modifier = Modifier.width(DgIconSlot).height(slotHeight), contentAlignment = Alignment.Center) {
                if (icon != null) {
                    val tint = iconTint ?: if (icon.tone == Neutral) color else toneColor(icon.tone)
                    DgToneIcon(icon = icon, size = iconSize, tint = tint)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

/**
 * Draws multi-line text as one [DgLabelRow] per line, for the value blocks built in `utils` where each line
 * starts with its own emoji. Blank lines become a small gap.
 */
@Composable
fun DgLabelLines(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    reserveIconSlot: Boolean = true,
) {
    val lines = remember(text) { text.lines() }
    Column(modifier = modifier) {
        lines.forEach { line ->
            if (line.isBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                DgLabelRow(text = line, style = style, color = color, reserveIconSlot = reserveIconSlot)
            }
        }
    }
}
