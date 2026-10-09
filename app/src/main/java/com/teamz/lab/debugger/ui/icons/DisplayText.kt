package com.teamz.lab.debugger.ui.icons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.teamz.lab.debugger.ui.icons.IconTone.Accent
import com.teamz.lab.debugger.ui.icons.IconTone.Neutral
import com.teamz.lab.debugger.ui.icons.IconTone.Warn

/**
 * One line of text as it is drawn: the icon for its slot (or none) and the words with every emoji taken out.
 */
data class DisplayLine(val icon: EmojiIcon?, val text: String)

/**
 * The emoji that say "pass", "careful" or "fail". When one sits anywhere in a line it decides the line's icon,
 * because it is the judgement the reader is looking for; a topic emoji at the start only names the row.
 */
private val STATUS_MARKS = setOf("✅", "✓", "❌", "⚠", "🚨", "🚫", "⛔", "❗", "🟢", "🟡", "🔴")

private const val FIRE = "🔥"
private val HEAT_WORDS = listOf(
    "°C", "°F", "temp", "hot", "heat", "warm", "cool", "গরম", "তাপ", "ঠান্ডা",
)
private val STREAK_WORDS = listOf("streak", "day", "দিন")
private val BULLETS = listOf("• ", "- ", "· ")

/**
 * Splits one line of display text into the icon for its leading slot and the text without any emoji.
 *
 * The line may come from a builder in `utils` (English or, after the localizers, Bangla): both keep the same
 * emoji, so both give the same icon.
 *
 * - An emoji at the start becomes the icon ([splitLeadingEmoji]).
 * - A status mark (pass, warning, fail) anywhere in the line wins over a topic emoji and becomes the icon.
 *   The words that said so stay in the text, so the icon is never the only signal.
 * - Every other emoji is removed ([stripEmoji]); arrows inside a sentence stay.
 * - The flame is read by its line: a thermometer for heat, the streak flame for streaks, a bolt otherwise.
 * - A bullet in front of a line that got an icon is dropped: the icon is the bullet.
 */
fun splitDisplayLine(line: String): DisplayLine {
    if (line.isEmpty()) return DisplayLine(null, line)
    val clusters = EmojiText.findAll(line)
    if (clusters.none { !EmojiText.isArrowCluster(it) }) return DisplayLine(null, line)

    val (leading, rest) = splitLeadingEmoji(line)
    val leadingCluster = clusters.first()
    val startsWithEmoji = rest.length != line.length
    val leadingKey = if (startsWithEmoji) EmojiText.normalize(leadingCluster) else ""
    var icon: EmojiIcon? = if (leadingKey == FIRE) fireIcon(line) else leading

    val leadingIsStatus = leadingKey in STATUS_MARKS
    if (!leadingIsStatus) {
        val status = EmojiText.findAll(rest).firstOrNull { EmojiText.normalize(it) in STATUS_MARKS }
        if (status != null) icon = EmojiIcons.lookup(status)
    }

    var text = stripEmoji(rest)
    if (icon != null) {
        val indent = text.takeWhile { it == ' ' || it == '\t' }
        val body = text.substring(indent.length)
        BULLETS.firstOrNull { body.startsWith(it) }?.let { text = body.substring(it.length) }
    }
    return DisplayLine(icon, text)
}

private val TEMPERATURE_READING = Regex(""":\s*-?[\d.]+\s*°""")

/**
 * The flame's meaning in [line]: heat, a streak, or plain energy. A plain reading ("Battery Temp: 31.5°C")
 * gets a neutral thermometer; only a line that says the phone is hot gets the warning colour.
 */
private fun fireIcon(line: String): EmojiIcon = when {
    TEMPERATURE_READING.containsMatchIn(line) -> EmojiIcon(DgIcons.Temperature, Neutral)
    HEAT_WORDS.any { line.contains(it, ignoreCase = true) } -> EmojiIcon(DgIcons.Temperature, Warn)
    STREAK_WORDS.any { line.contains(it, ignoreCase = true) } -> EmojiIcon(DgIcons.Streak, Warn)
    else -> EmojiIcon(DgIcons.Bolt, Accent)
}

/** [splitDisplayLine] for every line of [text]. */
fun splitDisplayLines(text: String): List<DisplayLine> = text.split('\n').map { splitDisplayLine(it) }

/**
 * [text] as plain words: every emoji gone, line by line, with the same clean-up as the drawn rows.
 * For places that cannot draw an icon beside the text (a toast, a text field, a one-line label).
 */
fun displayText(text: String): String =
    if (EmojiText.findAll(text).none { !EmojiText.isArrowCluster(it) }) {
        text
    } else {
        text.split('\n').joinToString("\n") { splitDisplayLine(it).text }
    }

/**
 * Draws a block of text one row per line, each with its own icon in a fixed [DgIconSlot], so all lines align.
 *
 * This is the display end of the builder text: `utils` builders and the localizers keep their emoji as data,
 * and no emoji reaches the screen from here. The icon slot is kept for every line only when at least one line
 * has an icon; a block of plain lines is drawn flush left as before.
 *
 * @param collapsed draw only the first line that has words, on one line with an ellipsis.
 */
@Composable
fun DgDisplayLines(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    collapsed: Boolean = false,
    iconSize: Dp = 18.dp,
    fontWeight: FontWeight? = null,
) {
    val lines = remember(text) { splitDisplayLines(text) }
    val anyIcon = remember(lines) { lines.any { it.icon != null } }
    Column(modifier = modifier) {
        if (collapsed) {
            val first = lines.firstOrNull { it.text.isNotBlank() } ?: DisplayLine(null, "")
            DgDisplayRow(first, style, color, anyIcon && first.icon != null, iconSize, fontWeight, maxLines = 1)
        } else {
            lines.forEach { line ->
                if (line.text.isBlank() && line.icon == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    DgDisplayRow(line, style, color, anyIcon, iconSize, fontWeight)
                }
            }
        }
    }
}

/** One already split line: the icon slot (when [reserveIconSlot]) and the text. */
@Composable
fun DgDisplayRow(
    line: DisplayLine,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    reserveIconSlot: Boolean = true,
    iconSize: Dp = 18.dp,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    val lineHeight = with(LocalDensity.current) {
        if (style.lineHeight.isSpecified && style.lineHeight.isSp) style.lineHeight.toDp() else DgIconSlot
    }
    val slotHeight = if (lineHeight > iconSize) lineHeight else iconSize
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        if (reserveIconSlot) {
            Box(modifier = Modifier.width(DgIconSlot).height(slotHeight), contentAlignment = Alignment.Center) {
                line.icon?.let { icon ->
                    DgToneIcon(
                        icon = icon,
                        size = iconSize,
                        tint = if (icon.tone == Neutral) color else toneColor(icon.tone),
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = line.text,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A heading, label or chip with a real icon in front of it: what replaces "emoji + words" in a string resource.
 * It takes the arguments of `Text`, so a `Text(...)` becomes a `DgIconText(icon = ..., ...)` and nothing else moves.
 *
 * The icon is sized from the font and sits on the first line of the text. It is decoration: the words beside it
 * say the same thing, so it has no content description.
 *
 * @param tone colours the icon as a status (good, warn, bad, accent); without it the icon takes the text colour.
 * @param tint overrides both, for an icon on a coloured background.
 */
@Composable
fun DgIconText(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    tone: IconTone? = null,
    tint: Color = Color.Unspecified,
    iconSize: Dp = Dp.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    val textColor = when {
        color.isSpecified -> color
        style.color.isSpecified -> style.color
        else -> LocalContentColor.current
    }
    val font = when {
        fontSize.isSpecified && fontSize.isSp -> fontSize
        style.fontSize.isSpecified && style.fontSize.isSp -> style.fontSize
        else -> 14.sp
    }
    val line = when {
        lineHeight.isSpecified && lineHeight.isSp -> lineHeight
        style.lineHeight.isSpecified && style.lineHeight.isSp -> style.lineHeight
        else -> font * 1.4f
    }
    val (size, slotHeight) = with(LocalDensity.current) {
        val resolved = if (iconSize.isSpecified) iconSize else (font * 1.15f).toDp().coerceIn(14.dp, 40.dp)
        resolved to maxOf(line.toDp(), resolved)
    }
    val iconColor = when {
        tint.isSpecified -> tint
        tone != null && tone != Neutral -> toneColor(tone)
        else -> textColor
    }
    val arrangement = when (textAlign) {
        TextAlign.Center -> Arrangement.Center
        TextAlign.End, TextAlign.Right -> Arrangement.End
        else -> Arrangement.Start
    }
    Row(modifier = modifier, horizontalArrangement = arrangement, verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.width(size).height(slotHeight), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(size))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            maxLines = maxLines,
            style = style,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** [DgIconText] for an [EmojiIcon] from the table: the icon keeps the tone the table gives it. */
@Composable
fun DgIconText(
    icon: EmojiIcon,
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    DgIconText(
        icon = icon.icon,
        text = text,
        modifier = modifier,
        color = color,
        tone = icon.tone,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        style = style,
    )
}

/**
 * An icon standing where a lone emoji used to be drawn as text: an empty-state picture, a card's badge.
 * Its size comes from the font size the emoji had, so the layout around it does not move.
 *
 * @param tone colours the icon as a status; without it the icon takes [color] or the content colour.
 * @param contentDescription only when no words beside the icon say the same thing.
 */
@Composable
fun DgGlyph(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    color: Color = Color.Unspecified,
    tone: IconTone? = null,
    style: TextStyle = LocalTextStyle.current,
    contentDescription: String? = null,
) {
    val font = when {
        fontSize.isSpecified && fontSize.isSp -> fontSize
        style.fontSize.isSpecified && style.fontSize.isSp -> style.fontSize
        else -> 14.sp
    }
    val size = with(LocalDensity.current) { (font * 1.15f).toDp() }
    val tint = when {
        tone != null && tone != Neutral -> toneColor(tone)
        color.isSpecified -> color
        else -> LocalContentColor.current
    }
    Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
}

/**
 * A place in a ranking. The top three get the medal icon, and every place shows its number as text beside it,
 * because one medal shape stands for gold, silver and bronze.
 */
@Composable
fun DgRankBadge(rank: Int, topColor: Color, otherColor: Color, modifier: Modifier = Modifier) {
    val isTopThree = rank in 1..3
    val color = if (isTopThree) topColor else otherColor
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (isTopThree) {
            Icon(imageVector = DgIcons.Medal, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(2.dp))
        }
        Text(
            text = rank.toString(),
            color = color,
            fontSize = if (isTopThree) 14.sp else 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
