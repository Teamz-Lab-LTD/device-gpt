package com.teamz.lab.debugger.design

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import com.teamz.lab.debugger.ui.icons.DG_ICON_STROKE
import com.teamz.lab.debugger.ui.icons.DG_ICON_VIEWPORT
import com.teamz.lab.debugger.ui.icons.DgIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every [DgIcons] vector builds, shares the one voice, and stays inside the optical padding. */
class DgIconsTest {

    /** Icons found by reflection, so a new one cannot be forgotten in [DgIcons.all]. */
    private val declared: Map<String, ImageVector> =
        DgIcons::class.java.methods
            .filter { it.returnType == ImageVector::class.java && it.parameterCount == 0 && it.name.startsWith("get") }
            .associate { it.name.removePrefix("get") to it.invoke(DgIcons) as ImageVector }

    @Test fun `every icon builds and is listed in all`() {
        assertTrue("expected the full set, found ${declared.size}", declared.size >= 39)
        assertEquals(declared.keys.sorted(), DgIcons.all.keys.sorted())
        declared.forEach { (name, icon) -> assertSame(icon, DgIcons.all[name]) }
    }

    @Test fun `every icon has a 24 by 24 viewport and a 24dp default size`() {
        declared.forEach { (name, icon) ->
            assertEquals(name, DG_ICON_VIEWPORT, icon.viewportWidth, 0f)
            assertEquals(name, DG_ICON_VIEWPORT, icon.viewportHeight, 0f)
            assertEquals(name, 24f, icon.defaultWidth.value, 0f)
            assertEquals(name, 24f, icon.defaultHeight.value, 0f)
            assertEquals(name, "Dg.$name", icon.name)
        }
    }

    @Test fun `every icon is a 2 unit round stroke plus at most one layer of dots`() {
        declared.forEach { (name, icon) ->
            val paths = icon.root.iterator().asSequence().map { it as VectorPath }.toList()
            assertTrue("$name has ${paths.size} paths", paths.size in 1..2)
            val outline = paths[0]
            assertNull("$name outline must not be filled", outline.fill)
            assertTrue("$name outline needs a solid stroke", outline.stroke is SolidColor)
            assertEquals(name, DG_ICON_STROKE, outline.strokeLineWidth, 0f)
            assertEquals(name, StrokeCap.Round, outline.strokeLineCap)
            assertEquals(name, StrokeJoin.Round, outline.strokeLineJoin)
            assertTrue("$name outline is empty", outline.pathData.size > 1)
            if (paths.size == 2) {
                assertTrue("$name dots must be filled", paths[1].fill is SolidColor)
                assertNull("$name dots must not be stroked", paths[1].stroke)
            }
        }
    }

    /**
     * Follows the pen through each path. A stroke centre line must stay 2.9 units from the edge or more, so
     * the 2-unit stroke keeps about 2 units of padding; a mis-parsed arc flag or number would throw the pen
     * far outside and fail here.
     */
    @Test fun `every path end point stays inside the padding`() {
        declared.forEach { (name, icon) ->
            icon.root.iterator().asSequence().map { it as VectorPath }.forEachIndexed { layer, path ->
                val margin = if (layer == 0) 2.9f else 2f
                endPoints(path.pathData).forEach { (x, y) ->
                    assertTrue(
                        "$name layer $layer reaches ($x, $y)",
                        x >= margin && x <= 24f - margin && y >= margin && y <= 24f - margin,
                    )
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun endPoints(nodes: List<PathNode>): List<Pair<Float, Float>> {
        val points = ArrayList<Pair<Float, Float>>()
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f
        for (node in nodes) {
            when (node) {
                is PathNode.MoveTo -> { x = node.x; y = node.y; startX = x; startY = y }
                is PathNode.RelativeMoveTo -> { x += node.dx; y += node.dy; startX = x; startY = y }
                is PathNode.LineTo -> { x = node.x; y = node.y }
                is PathNode.RelativeLineTo -> { x += node.dx; y += node.dy }
                is PathNode.HorizontalTo -> x = node.x
                is PathNode.RelativeHorizontalTo -> x += node.dx
                is PathNode.VerticalTo -> y = node.y
                is PathNode.RelativeVerticalTo -> y += node.dy
                is PathNode.CurveTo -> { x = node.x3; y = node.y3 }
                is PathNode.RelativeCurveTo -> { x += node.dx3; y += node.dy3 }
                is PathNode.ReflectiveCurveTo -> { x = node.x2; y = node.y2 }
                is PathNode.RelativeReflectiveCurveTo -> { x += node.dx2; y += node.dy2 }
                is PathNode.QuadTo -> { x = node.x2; y = node.y2 }
                is PathNode.RelativeQuadTo -> { x += node.dx2; y += node.dy2 }
                is PathNode.ReflectiveQuadTo -> { x = node.x; y = node.y }
                is PathNode.RelativeReflectiveQuadTo -> { x += node.dx; y += node.dy }
                is PathNode.ArcTo -> { x = node.arcStartX; y = node.arcStartY }
                is PathNode.RelativeArcTo -> { x += node.arcStartDx; y += node.arcStartDy }
                PathNode.Close -> { x = startX; y = startY }
            }
            points.add(x to y)
        }
        return points
    }
}
