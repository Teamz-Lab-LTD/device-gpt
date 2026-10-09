package com.teamz.lab.debugger.ui.icons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.teamz.lab.debugger.ui.icons.IconTone.Neutral

private const val ICON_TO_FONT = 1.15f
private const val LINE_TO_FONT = 1.4f
private val MIN_ICON = 14.dp
private val MAX_ICON = 64.dp
private val DEFAULT_FONT = 14.sp

/**
 * `Text` for words that were built somewhere else: a builder in `utils`, a localizer, a view model, a
 * resource with arguments. It takes the same arguments as Material's `Text`.
 *
 * Text without emoji is handed to `Text` untouched, so swapping `Text` for `DgText` changes nothing there.
 * Text with emoji is never drawn as written: each line is split by [splitDisplayLine], its icon is drawn as a
 * vector in the tone colour in front of the line, and the words follow with every emoji removed. A string
 * that is only an emoji becomes just its icon, sized from the font size.
 *
 * This is the one place that keeps emoji kept as data (scores, share text and tests read them) off the screen.
 */
@Composable
fun DgText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    val lines = remember(text) { if (hasDrawnEmoji(text)) splitDisplayLines(text) else null }

    @Composable
    fun words(value: String, wordsModifier: Modifier, lineLimit: Int, least: Int) {
        Text(
            text = value,
            modifier = wordsModifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = lineLimit,
            minLines = least,
            onTextLayout = onTextLayout ?: {},
            style = style,
        )
    }

    if (lines == null) {
        words(text, modifier, maxLines, minLines)
        return
    }

    val textColor = when {
        color.isSpecified -> color
        style.color.isSpecified -> style.color
        else -> LocalContentColor.current
    }
    val font = when {
        fontSize.isSpecified && fontSize.isSp -> fontSize
        style.fontSize.isSpecified && style.fontSize.isSp -> style.fontSize
        else -> DEFAULT_FONT
    }
    val line = when {
        lineHeight.isSpecified && lineHeight.isSp -> lineHeight
        style.lineHeight.isSpecified && style.lineHeight.isSp -> style.lineHeight
        else -> font * LINE_TO_FONT
    }
    val (iconSize, slotHeight) = with(LocalDensity.current) {
        val size = (font * ICON_TO_FONT).toDp().coerceIn(MIN_ICON, MAX_ICON)
        size to maxOf(line.toDp(), size)
    }
    val arrangement = when (textAlign) {
        TextAlign.Center -> Arrangement.Center
        TextAlign.End, TextAlign.Right -> Arrangement.End
        else -> Arrangement.Start
    }

    @Composable
    fun row(item: DisplayLine, rowModifier: Modifier, reserveSlot: Boolean, lineLimit: Int) {
        Row(modifier = rowModifier, horizontalArrangement = arrangement, verticalAlignment = Alignment.Top) {
            if (item.icon != null || reserveSlot) {
                Box(modifier = Modifier.width(iconSize).height(slotHeight), contentAlignment = Alignment.Center) {
                    item.icon?.let { icon ->
                        DgToneIcon(
                            icon = icon,
                            size = iconSize,
                            tint = if (icon.tone == Neutral) textColor else toneColor(icon.tone),
                        )
                    }
                }
            }
            if (item.text.isNotBlank()) {
                if (item.icon != null || reserveSlot) Spacer(modifier = Modifier.width(iconGap(iconSize)))
                words(item.text, Modifier.weight(1f, fill = false), lineLimit, 1)
            }
        }
    }

    if (lines.size == 1) {
        row(lines[0], modifier, reserveSlot = false, lineLimit = maxLines)
        return
    }
    val anyIcon = lines.any { it.icon != null }
    val shown = if (maxLines == Int.MAX_VALUE) lines else lines.filter { it.text.isNotBlank() }.take(maxLines)
    val horizontal = when (textAlign) {
        TextAlign.Center -> Alignment.CenterHorizontally
        TextAlign.End, TextAlign.Right -> Alignment.End
        else -> Alignment.Start
    }
    Column(modifier = modifier, horizontalAlignment = horizontal) {
        shown.forEach { item ->
            when {
                item.text.isBlank() && item.icon == null -> Spacer(modifier = Modifier.height(slotHeight / 2))
                !anyIcon -> words(item.text, Modifier, if (maxLines == Int.MAX_VALUE) maxLines else 1, 1)
                else -> row(
                    item,
                    Modifier,
                    reserveSlot = textAlign != TextAlign.Center,
                    lineLimit = if (maxLines == Int.MAX_VALUE) maxLines else 1,
                )
            }
        }
    }
}

/** Annotated text is laid out by its author and never carries builder emoji: it goes straight to `Text`. */
@Composable
fun DgText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout ?: {},
        style = style,
    )
}

/** True when [text] holds an emoji that must not be drawn. Arrows inside a sentence are text and stay. */
fun hasDrawnEmoji(text: CharSequence): Boolean {
    var i = 0
    while (i < text.length) {
        val length = EmojiText.clusterLengthAt(text, i)
        if (length > 0 && !EmojiText.isArrowCluster(text.subSequence(i, i + length))) return true
        i += if (length > 0) length else Character.charCount(Character.codePointAt(text, i))
    }
    return false
}

private fun iconGap(iconSize: Dp): Dp = if (iconSize > 28.dp) 12.dp else 8.dp
