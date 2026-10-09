package com.teamz.lab.debugger.design

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No stock framework colour (`Color.Red`, `Color.Gray`, `Color.White`, ...) under `ui/`.
 *
 * A stock colour carries no meaning and no partner: it is how white text ends up on a lime button. Colours
 * come from `MaterialTheme.colorScheme`, from `dgSemanticColors()` (good / warn / accent / info, each with its
 * on-colour), or from `contrastOn(background)` for a background that is not a token.
 */
class StockColorGuardTest {

    private fun locate(rel: String): File {
        val root = System.getProperty("user.dir") ?: "."
        return listOf(File(root, rel), File(root, "app/$rel"), File("../app/$rel"))
            .firstOrNull { it.exists() } ?: File(root, rel)
    }

    private val ui = locate("src/main/java/com/teamz/lab/debugger/ui")

    /**
     * Files where a stock colour is the point, not a styling choice:
     *
     * - `screen_test_card.kt`: the dead-pixel test paints the whole screen pure red, green, blue, cyan,
     *   magenta, yellow, white, black and grey on purpose, the scratch grid is pure black on white (or the
     *   reverse), and the touch test draws on pure black. The text on those screens is black or white to match.
     * - `icons/DgIcons.kt`: vector paths are drawn in black and tinted where they are used, as every
     *   `ImageVector` is.
     */
    private val allowedFiles = setOf("screen_test_card.kt", "DgIcons.kt")

    private val stock = Regex(
        """\bColor\.(Red|Green|Blue|Yellow|Cyan|Magenta|Gray|DarkGray|LightGray|White|Black)\b"""
    )

    @Test fun `no stock colour under ui outside the test screens`() {
        assertTrue("ui/ not found from ${System.getProperty("user.dir")}", ui.isDirectory)
        val found = ui.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in allowedFiles }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, raw ->
                    val line = raw.substringBefore("//").trim()
                    if (line.startsWith("*") || !stock.containsMatchIn(line)) null
                    else "${file.relativeTo(ui)}:${index + 1}: $line"
                }
            }
            .toList()
        assertTrue(
            "Use a colour from MaterialTheme.colorScheme, dgSemanticColors() or contrastOn():\n" +
                found.joinToString("\n"),
            found.isEmpty(),
        )
    }

    @Test fun `the allow-list names real files that still need it`() {
        for (name in allowedFiles) {
            val file = ui.walkTopDown().firstOrNull { it.name == name }
            assertTrue("$name is on the allow-list but is gone", file != null)
            assertTrue("$name no longer uses a stock colour; take it off the allow-list", stock.containsMatchIn(file!!.readText()))
        }
    }
}
