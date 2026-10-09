package com.teamz.lab.debugger.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.teamz.lab.debugger.R
import java.util.Locale

/**
 * Shows the English text built by `device_utils.kt`, `network_utils.kt` and the privacy / trust
 * scorers in the app language, at the moment it is drawn.
 *
 * Why the builders are not translated themselves: their output is data. The health score, the
 * verified report, the leaderboard upload, the AI prompts, the share reports, the AI bridge and
 * about thirty unit tests read that English text (`contains("Battery Full")`, `== "Yes"`,
 * `contains("✅")`). A Bangla row there would break a score or send Bangla to an AI. So the
 * builders keep returning English, and only the screen passes the text through here.
 *
 * How it matches: `values/strings_info.xml` holds one key per row label (`info_l_*`, the text
 * before the colon) and one per whole line or value (`info_t_*`, with `%n$s` where the phone's
 * own value goes). The English value of a key is the pattern, the app-language value is what is
 * shown. A line that matches nothing is shown as the phone reported it.
 */
object InfoTextLocalizer {

    private const val MAX_DEPTH = 4
    private const val CACHE_SIZE = 128

    @Volatile
    private var current: Pair<String, Engine>? = null

    private val cache = object : LinkedHashMap<String, String>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > CACHE_SIZE
    }

    /**
     * Returns [english] in the app language. Returns it unchanged when the app is in English.
     *
     * @param exactOnly translate only when a whole line is a known sentence. For text the phone
     *   wrote itself, such as system log lines, where translating single words would garble it.
     */
    fun localize(context: Context, english: String, exactOnly: Boolean = false): String {
        if (english.isBlank()) return english
        val code = LocaleManager.currentLanguageCode(context)
        if (code == LocaleManager.LANGUAGE_ENGLISH) return english
        return try {
            val engine = engineFor(context, code)
            val key = if (exactOnly) "\u0001$english" else english
            synchronized(cache) { cache[key] }?.let { return it }
            engine.localize(english, exactOnly).also { synchronized(cache) { cache[key] = it } }
        } catch (e: Exception) {
            handleError(e)
            english
        }
    }

    private fun engineFor(context: Context, code: String): Engine {
        current?.let { (cachedCode, engine) -> if (cachedCode == code) return engine }
        return synchronized(this) {
            current?.let { (cachedCode, engine) -> if (cachedCode == code) return@synchronized engine }
            val app = context.applicationContext ?: context
            val englishConfig = Configuration(app.resources.configuration).apply { setLocale(Locale.ENGLISH) }
            val engine = Engine(
                english = app.createConfigurationContext(englishConfig).resources,
                target = LocaleManager.localizedContext(context).resources,
            )
            synchronized(cache) { cache.clear() }
            current = code to engine
            engine
        }
    }

    /** Drops the loaded patterns. For tests only. */
    internal fun resetForTest() {
        synchronized(this) { current = null }
        synchronized(cache) { cache.clear() }
    }

    private val PLACEHOLDER = Regex("%(\\d+)\\\$s")
    private val PLACEHOLDER_OR_PERCENT = Regex("%(\\d+)\\\$s|%%")

    /** Separators the builders join short phrases with. Tried only after no whole pattern matched. */
    private val SEPARATORS = listOf("; ", " — ", " · ", ", ")

    /** One `info_t_*` key that has placeholders, compiled to a pattern over the English line. */
    private class Template(val id: Int, raw: String) {
        private val literals: List<String> = PLACEHOLDER.split(raw).map { it.replace("%%", "%") }
        val numbers: List<Int> = PLACEHOLDER.findAll(raw).map { it.groupValues[1].toInt() }.toList()
        val prefix: String = literals.first()
        val suffix: String = literals.last()
        val literalLength: Int = literals.sumOf { it.length }
        val regex: Regex = Regex(literals.joinToString("(.*?)") { Regex.escape(it) })
    }

    /** The matcher. Takes plain [Resources] so a unit test can build it without the app's language state. */
    internal class Engine(english: Resources, private val target: Resources) {

        private val labels = HashMap<String, Int>()
        private val exact = HashMap<String, Int>()
        private val templates: List<Template>

        init {
            for (id in ids(english, R.array.info_label_keys)) labels[plain(english.getString(id))] = id
            val withPlaceholders = ArrayList<Template>()
            for (id in ids(english, R.array.info_template_keys)) {
                val raw = english.getString(id)
                if (PLACEHOLDER.containsMatchIn(raw)) withPlaceholders += Template(id, raw) else exact[plain(raw)] = id
            }
            // The pattern with the most fixed text wins, so "Charging at %1$s" is tried before "%1$s FPS".
            templates = withPlaceholders.sortedByDescending { it.literalLength }
        }

        fun localize(text: String, exactOnly: Boolean): String =
            text.split('\n').joinToString("\n") { line ->
                val core = line.trim()
                when {
                    core.isEmpty() -> line
                    exactOnly -> exact[core]?.let { indentOf(line) + render(it, emptyMap()) } ?: line
                    else -> indentOf(line) + fragment(core, 0)
                }
            }

        private fun fragment(s: String, depth: Int): String {
            if (s.isEmpty() || depth > MAX_DEPTH) return s
            exact[s]?.let { return render(it, emptyMap()) }

            // "label: value" with a label we know. Before the line patterns, or an open-ended one
            // such as "%1$s min left" would swallow the label along with the value.
            val colon = s.indexOf(": ")
            val left = if (colon > 0) s.substring(0, colon).trimEnd() else ""
            val right = if (colon > 0) s.substring(colon + 2).trim() else ""
            if (colon > 0) {
                labels[left]?.let { return render(it, emptyMap()) + ": " + fragment(right, depth + 1) }
            } else if (s.endsWith(":")) {
                labels[s.dropLast(1).trimEnd()]?.let { return render(it, emptyMap()) + ":" }
            }

            matchTemplate(s, depth)?.let { return it }
            if (s.startsWith("• ")) return "• " + fragment(s.substring(2).trim(), depth + 1)

            // A label we do not know may still be a sentence we do ("N apps …: a; b"), or carry a known value.
            if (colon > 0) {
                val shownLeft = fragment(left, depth + 1)
                val shownRight = fragment(right, depth + 1)
                if (shownLeft != left || shownRight != right) return "$shownLeft: $shownRight"
            }

            // "✅ Yes", "🟢 Low": a leading mark, then a value we know.
            val space = s.indexOf(' ')
            if (space in 1..8 && s.substring(0, space).none { it.code < 0x250 && it.isLetterOrDigit() }) {
                val rest = s.substring(space + 1).trim()
                val shown = fragment(rest, depth + 1)
                if (shown != rest) return s.substring(0, space + 1) + shown
            }

            for (separator in SEPARATORS) {
                if (!s.contains(separator)) continue
                val parts = s.split(separator).map { it.trim() }
                val shown = parts.map { fragment(it, depth + 1) }
                if (shown != parts) return shown.joinToString(separator)
            }
            return s
        }

        private fun matchTemplate(s: String, depth: Int): String? {
            for (template in templates) {
                if (s.length < template.literalLength) continue
                if (!s.startsWith(template.prefix) || !s.endsWith(template.suffix)) continue
                val match = template.regex.matchEntire(s) ?: continue
                val args = HashMap<Int, String>()
                template.numbers.forEachIndexed { index, number ->
                    args[number] = fragment(match.groupValues[index + 1].trim(), depth + 1)
                }
                return render(template.id, args)
            }
            return null
        }

        /** The app-language text of [id], with the phone's own values put back in. */
        private fun render(id: Int, args: Map<Int, String>): String =
            PLACEHOLDER_OR_PERCENT.replace(target.getString(id)) { match ->
                if (match.value == "%%") "%" else args[match.groupValues[1].toInt()].orEmpty()
            }

        private fun indentOf(line: String): String = line.takeWhile { it == ' ' || it == '\t' }

        private fun plain(raw: String): String = raw.replace("%%", "%")

        private fun ids(resources: Resources, arrayId: Int): List<Int> {
            val typed = resources.obtainTypedArray(arrayId)
            try {
                return (0 until typed.length()).map { typed.getResourceId(it, 0) }.filter { it != 0 }
            } finally {
                typed.recycle()
            }
        }
    }
}
