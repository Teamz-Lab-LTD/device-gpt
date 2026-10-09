package com.teamz.lab.debugger.design

import com.teamz.lab.debugger.ui.icons.DgIcons
import com.teamz.lab.debugger.ui.icons.EmojiIcons
import com.teamz.lab.debugger.ui.icons.EmojiText
import com.teamz.lab.debugger.ui.icons.IconTone
import com.teamz.lab.debugger.ui.icons.splitLeadingEmoji
import com.teamz.lab.debugger.ui.icons.stripEmoji
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The emoji table and the two text helpers built on it. Plain JVM: no Android runtime is needed.
 *
 * The last test reads the source tree, so it fails the day someone types an emoji the table does not know.
 */
class EmojiIconsTest {

    private val warningWithSelector = "⚠️"
    private val warningBare = "⚠"

    // ---- splitLeadingEmoji ----

    @Test fun `plain text comes back unchanged with no icon`() {
        val (icon, text) = splitLeadingEmoji("Battery is fine")
        assertNull(icon)
        assertEquals("Battery is fine", text)
    }

    @Test fun `plain text keeps its leading spaces`() {
        assertEquals(null to "  indented", splitLeadingEmoji("  indented"))
    }

    @Test fun `empty string comes back empty`() {
        assertEquals(null to "", splitLeadingEmoji(""))
        assertEquals("", stripEmoji(""))
    }

    @Test fun `leading status mark gives its icon and tone`() {
        val (icon, text) = splitLeadingEmoji("✅ Camera looks fine")
        assertEquals(IconTone.Good, icon!!.tone)
        assertEquals("Camera looks fine", text)
        assertEquals(IconTone.Bad, splitLeadingEmoji("❌ Failed").first!!.tone)
    }

    @Test fun `variation selector or not, the same icon comes back`() {
        val (withSelector, textA) = splitLeadingEmoji("$warningWithSelector Battery is hot")
        val (bare, textB) = splitLeadingEmoji("$warningBare Battery is hot")
        assertNotNull(withSelector)
        assertEquals(withSelector, bare)
        assertEquals(IconTone.Warn, bare!!.tone)
        assertEquals("Battery is hot", textA)
        assertEquals("Battery is hot", textB)
    }

    @Test fun `whitespace before the emoji and several spaces after it are removed`() {
        val (icon, text) = splitLeadingEmoji("  🔋   92%")
        assertSame(DgIcons.Battery, icon!!.icon)
        assertEquals("92%", text)
    }

    @Test fun `emoji with no space after it still splits`() {
        assertEquals("Phone", splitLeadingEmoji("📱Phone").second)
    }

    @Test fun `emoji in the middle is not a leading emoji`() {
        val (icon, text) = splitLeadingEmoji("Camera ✅ passed")
        assertNull(icon)
        assertEquals("Camera ✅ passed", text)
    }

    @Test fun `decorative emoji is dropped without an icon`() {
        val (icon, text) = splitLeadingEmoji("🎉 Well done")
        assertNull(icon)
        assertEquals("Well done", text)
        assertTrue(EmojiIcons.isDropped("🎉"))
    }

    @Test fun `unknown emoji is removed too so no glyph reaches the screen`() {
        val unicorn = "🦄"
        assertFalse(EmojiIcons.isKnown(unicorn))
        assertEquals(null to "Rare", splitLeadingEmoji("$unicorn Rare"))
    }

    @Test fun `several leading emoji all go and the first one with an icon wins`() {
        val (icon, text) = splitLeadingEmoji("📱🧠 Device brain")
        assertSame(DgIcons.Phone, icon!!.icon)
        assertEquals("Device brain", text)
        assertSame(DgIcons.Tip, splitLeadingEmoji("✨ 💡 Tip").first!!.icon)
    }

    @Test fun `multi code point emoji are one cluster`() {
        val family = "👨‍👩‍👧"
        val thumbsUpDark = "👍🏿"
        val flag = "🇧🇩"
        val keycap = "1️⃣"
        for (cluster in listOf(family, thumbsUpDark, flag, keycap)) {
            assertEquals(cluster.length, EmojiText.clusterLengthAt("$cluster tail", 0))
            assertEquals("tail", splitLeadingEmoji("$cluster tail").second)
            assertEquals("head tail", stripEmoji("head $cluster tail"))
        }
        // A skin tone does not need its own table entry.
        assertEquals(EmojiIcons.lookup("👍"), EmojiIcons.lookup(thumbsUpDark))
        assertEquals(IconTone.Good, splitLeadingEmoji("$thumbsUpDark Nice").first!!.tone)
    }

    @Test fun `digits and hash signs are not emoji`() {
        assertEquals(null to "5 apps", splitLeadingEmoji("5 apps"))
        assertEquals("#1 of 20", stripEmoji("#1 of 20"))
    }

    @Test fun `bangla text is left alone`() {
        val bangla = "ব্যাটারি ভালো আছে"
        assertEquals(null to bangla, splitLeadingEmoji(bangla))
        assertEquals(bangla, stripEmoji(bangla))
        val (icon, text) = splitLeadingEmoji("✅ $bangla")
        assertEquals(IconTone.Good, icon!!.tone)
        assertEquals(bangla, text)
        assertEquals("ক্যামেরা ঠিক আছে।", stripEmoji("ক্যামেরা ঠিক আছে ✅।"))
    }

    @Test fun `leading arrow becomes an arrow icon`() {
        val (icon, text) = splitLeadingEmoji("→ Next")
        assertNotNull(icon)
        assertEquals("Next", text)
    }

    // ---- stripEmoji ----

    @Test fun `strip leaves plain text exactly as written`() {
        val text = "Speed :  12 Mbps \n  second line "
        assertEquals(text, stripEmoji(text))
    }

    @Test fun `strip removes emoji at the start, middle and end`() {
        assertEquals("Battery is hot", stripEmoji("$warningWithSelector Battery is hot"))
        assertEquals("Battery is hot", stripEmoji("$warningBare Battery is hot"))
        assertEquals("Camera passed", stripEmoji("Camera ✅ passed"))
        assertEquals("All done", stripEmoji("All done 🎉"))
        assertEquals("All done!", stripEmoji("All done 🎉!"))
        assertEquals("AB", stripEmoji("A🔥B"))
        assertEquals("A B", stripEmoji("A ✅ ❌ B"))
        assertEquals("", stripEmoji("🎉🎉"))
    }

    @Test fun `strip works line by line and keeps line breaks`() {
        assertEquals("CPU: fine\nRAM: low\n\nDone", stripEmoji("🧠 CPU: fine ✅\n💾 RAM: low ⚠️\n\n🎉 Done"))
    }

    @Test fun `strip keeps text arrows`() {
        assertEquals("Settings → Battery", stripEmoji("⚙️ Settings → Battery"))
    }

    // ---- the table ----

    @Test fun `status marks use different shapes as well as different tones`() {
        val good = EmojiIcons.lookup("✅")!!
        val warn = EmojiIcons.lookup("⚠️")!!
        val bad = EmojiIcons.lookup("❌")!!
        assertEquals(listOf(IconTone.Good, IconTone.Warn, IconTone.Bad), listOf(good.tone, warn.tone, bad.tone))
        assertEquals(3, setOf(good.icon.name, warn.icon.name, bad.icon.name).size)
        assertEquals(good, EmojiIcons.StatusGood)
        assertEquals(warn, EmojiIcons.StatusWarn)
        assertEquals(bad, EmojiIcons.StatusBad)
    }

    @Test fun `escapes decode to the characters they stand for`() {
        assertEquals("🔗", EmojiText.decodeEscapes("\\uD83D\\uDD17"))
        assertEquals("🔗 🔗", EmojiText.decodeEscapes("&#x1F517; &#128279;"))
        assertEquals("a &amp; b", EmojiText.decodeEscapes("a &amp; b"))
    }

    @Test fun `every emoji in the source tree has a table entry`() {
        val counts = scanSourceTree()
        assertTrue("scan found only ${counts.size} emoji: the scan or the tree drifted", counts.size > 100)
        val missing = counts.keys.filterNot { EmojiIcons.isKnown(it) }
        assertTrue(
            "No EmojiIcons entry for: " + missing.joinToString { emoji ->
                emoji + " (" + emoji.codePoints().toArray().joinToString(" ") { "U+%04X".format(it) } +
                    ", ${counts[emoji]} uses)"
            } + ". Add each to EmojiIcons.buildTable(): an icon with a tone, or drop() if it is decoration.",
            missing.isEmpty(),
        )
    }

    private fun locate(rel: String): File {
        val root = System.getProperty("user.dir") ?: "."
        return listOf(File(root, rel), File(root, "app/$rel"), File("../app/$rel"))
            .firstOrNull { it.exists() } ?: File(root, rel)
    }

    /** Kotlin and Java sources, every `strings*.xml` in every locale, and the layouts. */
    private fun scanSourceTree(): Map<String, Int> {
        val main = locate("src/main")
        assertTrue("src/main not found from ${System.getProperty("user.dir")}", main.isDirectory)
        val ownPackage = "ui${File.separator}icons${File.separator}"
        val files = main.walkTopDown().filter { file ->
            val path = file.path
            when {
                !file.isFile -> false
                ownPackage in path -> false // the table and its preview hold emoji on purpose
                file.extension == "kt" || file.extension == "java" -> true
                file.extension != "xml" -> false
                file.parentFile?.name == "layout" -> true
                else -> file.parentFile?.name.orEmpty().startsWith("values") && file.name.startsWith("strings")
            }
        }.toList()
        assertTrue("only ${files.size} files scanned", files.size > 50)
        val counts = HashMap<String, Int>()
        for (file in files) {
            for (cluster in EmojiText.findAll(EmojiText.decodeEscapes(file.readText()))) {
                counts.merge(EmojiText.normalize(cluster), 1, Int::plus)
            }
        }
        return counts
    }
}
