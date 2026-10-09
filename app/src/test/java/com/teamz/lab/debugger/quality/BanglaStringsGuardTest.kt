package com.teamz.lab.debugger.quality

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards `values-bn/strings.xml` against the three ways the Bangla has gone wrong before:
 * a key missing (a Bangla user then sees English in the middle of a Bangla screen),
 * a placeholder dropped or renumbered (a crash or a wrong number at runtime), and
 * English or book words creeping back in.
 *
 * The reader is someone in a phone market who reads Bangla slowly and reads no English.
 * The rules come from `docs/superpowers/specs/2026-10-09-easy-bangla-design.md`
 * ("Bangla style guide"). This is a plain file-reading test, no Android runtime.
 */
class BanglaStringsGuardTest {

    private fun locate(rel: String): File {
        val root = System.getProperty("user.dir") ?: "."
        return listOf(File(root, rel), File(root, "app/$rel"), File("../app/$rel"))
            .firstOrNull { it.exists() } ?: File(root, rel)
    }

    private data class Entry(val value: String, val translatable: Boolean)

    private fun parse(locale: String): Map<String, Entry> {
        val file = locate("src/main/res/$locale/strings.xml")
        assertTrue("$locale/strings.xml missing", file.exists())
        val out = LinkedHashMap<String, Entry>()
        Regex("<string\\s+name=\"([^\"]+)\"([^>]*)>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .forEach { m ->
                val name = m.groupValues[1]
                assertTrue("$locale/strings.xml declares $name twice", name !in out)
                out[name] = Entry(
                    value = m.groupValues[3],
                    translatable = !m.groupValues[2].contains("translatable=\"false\""),
                )
            }
        assertTrue("$locale/strings.xml parsed to nothing — regex or file drifted", out.size > 100)
        return out
    }

    private val en by lazy { parse("values") }
    private val bn by lazy { parse("values-bn") }

    // ── allow-lists ──────────────────────────────────────────────────────────

    /**
     * Keys whose Bangla value is allowed to equal the English one, and which are skipped by
     * the Latin-letter check. They are names, not sentences. They are NOT marked
     * `translatable="false"` because the 17 other `values-*` folders also define them, and
     * lint's ExtraTranslation (fatal) would then fail the release build.
     */
    private val sameAsEnglishKeys = setOf(
        "app_name",          // the product name, DeviceGPT
        "watts",             // the unit "W", printed next to a number
        "my_device_info",    // file name of a shared report
        "my_network_info",   // file name of a shared report
        "my_health_report",  // file name of a shared report
        "my_power_report",   // file name of a shared report
        "probe_custom_hint", // "example.com", the pattern of a website address to type
    )

    /** Latin words allowed inside a Bangla value. Keep this short; every entry needs a reason. */
    private val latinAllowed = setOf(
        "DeviceGPT", // product name (style guide rule 1)
        "Claude",    // a laptop app's name; the person has to find this exact word on the laptop
        "Desktop",   // part of the app names "Claude Desktop" / "ChatGPT Desktop"
        "ChatGPT",   // app name
        "Cursor",    // app name
        "Gemini",    // app name
        "MCP",       // the label of the settings page the person must open inside those apps
        "Developer", // same: the label of a settings page inside those apps
        "VPN",       // printed in the phone's own settings; always written beside ভিপিএন
        "DNS",       // printed in the phone's own settings; always written beside its meaning
        "Private",   // "Private DNS", the phone's own settings label
        "BDT",       // currency code the person has to type
        "USD",       // currency code the person has to type
        "example",   // example.com, shown as the pattern of a website address
        "com",       // example.com
    )

    /**
     * Keys where the Bangla may use fewer placeholders than the English.
     * `total_tests`: `%2$s` is the English plural "s" ("test" / "tests"). Bangla has no plural
     * suffix, and printing an "s" after a Bangla word would be wrong.
     */
    private val placeholderSubsetOk = setOf("total_tests")

    /** Book words banned by style-guide rule 3. Substring match, so মনিটর also catches মনিটরিং. */
    private val bannedWords = listOf(
        "স্বাস্থ্য স্কোর", "পরিমাপ", "অনুমতি অস্বীকার", "অক্ষম", "ত্রুটি", "বিজ্ঞপ্তি",
        "অবস্থান অ্যাক্সেস", "ব্যবহার", "প্রদান", "সম্পন্ন", "নির্বাচন", "কম্পোনেন্ট",
        "ডেটা রিফ্রেশ", "মনিটর", "স্ট্রিক",
    )

    // ── helpers ──────────────────────────────────────────────────────────────

    private val placeholder = Regex("%(\\d+\\$)?[sdf]")

    private fun placeholders(s: String): List<String> =
        placeholder.findAll(s.replace("%%", "")).map { it.value }.sorted().toList()

    private fun percentLiterals(s: String): Int = Regex("%%").findAll(s).count()

    /** The text a person reads: placeholders, XML entities and Android escapes removed. */
    private fun visible(s: String): String =
        s.replace("%%", " ")
            .replace(placeholder, " ")
            .replace(Regex("&[a-z]+;"), " ")
            .replace(Regex("\\\\[nt'\"]"), " ")

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    fun `every translatable English key has a Bangla string and Bangla has no extra key`() {
        val missing = en.filter { it.value.translatable }.keys - bn.keys
        assertTrue("values-bn is missing: $missing", missing.isEmpty())

        val extra = bn.keys - en.keys
        assertTrue("values-bn has keys that values lacks: $extra", extra.isEmpty())

        val shouldNotBeTranslated = bn.keys.filter { en[it]?.translatable == false }
        assertTrue(
            "values-bn translates keys marked translatable=false: $shouldNotBeTranslated",
            shouldNotBeTranslated.isEmpty(),
        )
    }

    @Test
    fun `placeholders match the English string for every key`() {
        val problems = mutableListOf<String>()
        for ((key, b) in bn) {
            val e = en[key] ?: continue
            val enPh = placeholders(e.value)
            val bnPh = placeholders(b.value)
            val ok = if (key in placeholderSubsetOk) enPh.containsAll(bnPh) else enPh == bnPh
            if (!ok) problems += "$key: en=$enPh bn=$bnPh"
            if (percentLiterals(e.value) != percentLiterals(b.value)) {
                problems += "$key: different count of %%"
            }
        }
        assertTrue("placeholder mismatch:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `no Bangla string contains an English word outside the allow-list`() {
        val problems = mutableListOf<String>()
        for ((key, b) in bn) {
            if (key in sameAsEnglishKeys) continue
            val words = Regex("[A-Za-z]{2,}").findAll(visible(b.value)).map { it.value }.toList()
            val bad = words.filter { it !in latinAllowed }
            if (bad.isNotEmpty()) problems += "$key: $bad"
        }
        assertTrue(
            "English words in Bangla strings (the reader cannot read them):\n" +
                problems.joinToString("\n"),
            problems.isEmpty(),
        )
    }

    @Test
    fun `no Bangla string contains a banned book word`() {
        val problems = mutableListOf<String>()
        for ((key, b) in bn) {
            val hit = bannedWords.filter { b.value.contains(it) }
            if (hit.isNotEmpty()) problems += "$key: $hit"
        }
        assertTrue(
            "banned words (style guide rule 3):\n" + problems.joinToString("\n"),
            problems.isEmpty(),
        )
    }

    @Test
    fun `no Bangla string is a copy of the English string`() {
        val copies = bn.filter { (key, b) ->
            key !in sameAsEnglishKeys && b.value.isNotBlank() && b.value == en[key]?.value
        }.keys
        assertTrue("Bangla value identical to English: $copies", copies.isEmpty())
    }

    @Test
    fun `every Bangla string has Bangla letters in it`() {
        // Catches an empty value or one that is only placeholders and punctuation.
        val bengali = Regex("[\\u0980-\\u09FF]")
        val empty = bn.filter { (key, b) ->
            key !in sameAsEnglishKeys && !bengali.containsMatchIn(b.value)
        }.keys
        assertTrue("Bangla value with no Bangla letters: $empty", empty.isEmpty())
    }

    @Test
    fun `allow-lists only name things that exist`() {
        // A stale allow-list entry silently widens the guard.
        for (key in sameAsEnglishKeys + placeholderSubsetOk) {
            assertTrue("allow-listed key $key is not in values-bn", key in bn)
        }
        val allText = bn.values.joinToString(" ") { it.value }
        for (word in latinAllowed) {
            assertTrue("allow-listed word $word is not used in values-bn any more", allText.contains(word))
        }
    }
}
