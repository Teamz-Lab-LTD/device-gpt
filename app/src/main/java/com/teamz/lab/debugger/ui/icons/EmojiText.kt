package com.teamz.lab.debugger.ui.icons

/**
 * Plain-text emoji scanning shared by [splitLeadingEmoji], [stripEmoji] and the unit test that checks every
 * emoji in the source tree has an entry in [EmojiIcons]. No Android or Compose types, so it runs anywhere.
 */
object EmojiText {

    private const val VARIATION_SELECTOR = 0xFE0F
    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val KEYCAP = 0x20E3

    /** True for the text arrows (U+2190 to U+21FF). They have table entries but [stripEmoji] keeps them. */
    fun isArrow(codePoint: Int): Boolean = codePoint in 0x2190..0x21FF

    /** True when [codePoint] can start an emoji cluster (pictographs, dingbats, symbols and text arrows). */
    fun isEmojiBase(codePoint: Int): Boolean =
        codePoint in 0x1F000..0x1FAFF ||
            codePoint in 0x2600..0x27BF ||
            codePoint in 0x2B00..0x2BFF ||
            codePoint in 0x2300..0x23FF ||
            isArrow(codePoint) ||
            codePoint in EXTRA_BASES

    private val EXTRA_BASES = setOf(
        0x2139, 0x203C, 0x2049, 0x2934, 0x2935, 0x25AA, 0x25AB, 0x25B6, 0x25C0, 0x25FB, 0x25FC, 0x25FD, 0x25FE,
    )

    private fun isSkinTone(codePoint: Int): Boolean = codePoint in 0x1F3FB..0x1F3FF

    private fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in 0x1F1E6..0x1F1FF

    private fun isKeycapBase(codePoint: Int): Boolean =
        codePoint in '0'.code..'9'.code || codePoint == '#'.code || codePoint == '*'.code

    /**
     * Length in UTF-16 units of the emoji cluster that starts at [index], or 0 when none starts there.
     *
     * A cluster is a base code point followed by any variation selectors and skin tones, joined to further
     * bases by zero-width joiners; or a pair of regional indicators (a flag); or a keycap sequence.
     */
    fun clusterLengthAt(text: CharSequence, index: Int): Int {
        if (index < 0 || index >= text.length) return 0
        val first = Character.codePointAt(text, index)
        var end = index + Character.charCount(first)
        if (isKeycapBase(first)) {
            if (end < text.length && text[end].code == VARIATION_SELECTOR) end++
            return if (end < text.length && text[end].code == KEYCAP) end + 1 - index else 0
        }
        if (!isEmojiBase(first)) return 0
        if (isRegionalIndicator(first)) {
            if (end < text.length) {
                val second = Character.codePointAt(text, end)
                if (isRegionalIndicator(second)) end += Character.charCount(second)
            }
            return end - index
        }
        while (end < text.length) {
            val next = Character.codePointAt(text, end)
            if (next == VARIATION_SELECTOR || isSkinTone(next)) {
                end += Character.charCount(next)
                continue
            }
            if (next == ZERO_WIDTH_JOINER && end + 1 < text.length) {
                val joined = Character.codePointAt(text, end + 1)
                if (isEmojiBase(joined)) {
                    end += 1 + Character.charCount(joined)
                    continue
                }
            }
            break
        }
        return end - index
    }

    /** Every emoji cluster in [text], in order, exactly as written (variation selectors included). */
    fun findAll(text: CharSequence): List<String> {
        val found = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            val length = clusterLengthAt(text, i)
            if (length > 0) {
                found.add(text.subSequence(i, i + length).toString())
                i += length
            } else {
                i += Character.charCount(Character.codePointAt(text, i))
            }
        }
        return found
    }

    /**
     * The table key for an emoji cluster: variation selectors and skin tones removed, so that "⚠" and "⚠️"
     * (and a thumbs-up of any skin tone) share one entry.
     */
    fun normalize(cluster: String): String {
        val out = StringBuilder(cluster.length)
        var i = 0
        while (i < cluster.length) {
            val cp = Character.codePointAt(cluster, i)
            if (cp != VARIATION_SELECTOR && !isSkinTone(cp)) out.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    /** True when the cluster is a text arrow that [stripEmoji] leaves in place. */
    internal fun isArrowCluster(cluster: CharSequence): Boolean =
        cluster.isNotEmpty() && isArrow(Character.codePointAt(cluster, 0))

    /**
     * Turns Kotlin and XML escapes (`🔗`, `&#x1F517;`, `&#128279;`) into the characters they stand
     * for, so a source scan sees an escaped emoji the same as a typed one.
     */
    fun decodeEscapes(source: String): String {
        val unicode = Regex("""\\u([0-9a-fA-F]{4})""").replace(source) { m ->
            m.groupValues[1].toInt(16).toChar().toString()
        }
        return Regex("""&#(x[0-9a-fA-F]+|[0-9]+);""").replace(unicode) { m ->
            val raw = m.groupValues[1]
            val cp = if (raw.startsWith("x")) raw.substring(1).toIntOrNull(16) else raw.toIntOrNull()
            if (cp != null && Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else m.value
        }
    }
}
