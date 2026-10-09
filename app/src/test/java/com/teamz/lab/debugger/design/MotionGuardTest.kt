package com.teamz.lab.debugger.design

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * All motion in the UI goes through `ui/theme/Motion.kt`.
 *
 * A hand-written `tween(...)`, `spring(...)`, `keyframes { }` or `infiniteRepeatable(...)` does not know about
 * the phone's "remove animations" setting, and a duration literal is a value outside the motion tokens. This
 * test reads the sources under `ui/` and fails on either, so the one switch (`LocalReduceMotion`) keeps
 * covering every animation.
 */
class MotionGuardTest {

    private fun locate(rel: String): File {
        val root = System.getProperty("user.dir") ?: "."
        return listOf(File(root, rel), File(root, "app/$rel"), File("../app/$rel"))
            .firstOrNull { it.exists() } ?: File(root, rel)
    }

    private val ui = locate("src/main/java/com/teamz/lab/debugger/ui")
    private val main = locate("src/main/java/com/teamz/lab/debugger")

    /** The one file that may build animation specs. */
    private val motionFile = "ui${File.separator}theme${File.separator}Motion.kt"

    /**
     * Sites that truly cannot use the helpers. Each entry is "file name: the exact trimmed line", with the
     * reason next to it. Empty today; keep it that way if you can.
     */
    private val allowed: Set<String> = emptySet()

    private fun offences(pattern: Regex): List<String> =
        ui.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.path.endsWith(motionFile) }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, raw ->
                    val line = raw.substringBefore("//").trim()
                    if (line.startsWith("*") || line.startsWith("import ")) return@mapIndexedNotNull null
                    if (!pattern.containsMatchIn(line)) return@mapIndexedNotNull null
                    if ("${file.name}: $line" in allowed) return@mapIndexedNotNull null
                    "${file.relativeTo(ui)}:${index + 1}: $line"
                }
            }
            .toList()

    @Test fun `no hand-written animation spec under ui outside Motion kt`() {
        assertTrue("ui/ not found from ${System.getProperty("user.dir")}", ui.isDirectory)
        val found = offences(Regex("""(?<![A-Za-z0-9_.])(tween|spring|keyframes|repeatable|infiniteRepeatable)\s*[({]"""))
        assertTrue(
            "Use motionTween / motionSpring / rememberMotionLoop from ui/theme/Motion.kt:\n" + found.joinToString("\n"),
            found.isEmpty(),
        )
    }

    @Test fun `no raw duration literal under ui outside Motion kt`() {
        val found = offences(Regex("""durationMillis\s*=\s*\d"""))
        assertTrue("Use a DgMotion token:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test fun `every Compose root provides the reduce-motion switch`() {
        val roots = main.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { Regex("""\bsetContent\s*\{""").containsMatchIn(it.readText()) }
            .toList()
        assertTrue("no setContent root found", roots.isNotEmpty())
        for (root in roots) {
            val text = root.readText()
            val after = text.substringAfter("setContent {").take(400)
            assertTrue("${root.name}: wrap the root content in ProvideReduceMotion { }", "ProvideReduceMotion {" in after)
        }
    }
}
