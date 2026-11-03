package com.scribidoo.app.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.scribidoo.app.canvas.lasso.LassoEngine
import com.scribidoo.app.canvas.model.PageModel
import com.scribidoo.app.canvas.model.SerializablePoint
import com.scribidoo.app.canvas.model.ShapeModel
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.model.StrokeModel
import com.scribidoo.app.canvas.model.TextBoxModel
import com.scribidoo.app.canvas.ui.calculateLassoMenuScreenBounds
import com.scribidoo.app.canvas.ui.isInsideLassoMenu
import com.scribidoo.app.canvas.viewport.ViewportState
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LassoEngineTest {

    private val squarePolygon = listOf(
        Offset(10f, 10f),
        Offset(100f, 10f),
        Offset(100f, 100f),
        Offset(10f, 100f)
    )

    @Test
    fun testPointInPolygon() {
        assertTrue(LassoEngine.isPointInPolygon(Offset(50f, 50f), squarePolygon))
        assertFalse(LassoEngine.isPointInPolygon(Offset(150f, 50f), squarePolygon))
        assertFalse(LassoEngine.isPointInPolygon(Offset(0f, 0f), squarePolygon))
    }

    @Test
    fun testSegmentIntersection() {
        assertTrue(
            LassoEngine.doSegmentsIntersect(
                Offset(0f, 50f), Offset(100f, 50f),
                Offset(50f, 0f), Offset(50f, 100f)
            )
        )
        assertFalse(
            LassoEngine.doSegmentsIntersect(
                Offset(0f, 0f), Offset(10f, 0f),
                Offset(20f, 0f), Offset(30f, 0f)
            )
        )
    }

    @Test
    fun testFindSelectionEnclosedAndCrossing() {
        val enclosedStroke = StrokeModel(
            id = "s-enclosed",
            points = listOf(SerializablePoint(20f, 20f), SerializablePoint(40f, 40f)),
            colorArgb = 0xFF000000.toInt(),
            strokeWidth = 2f
        )
        val crossingStroke = StrokeModel(
            id = "s-crossing",
            points = listOf(SerializablePoint(0f, 50f), SerializablePoint(150f, 50f)),
            colorArgb = 0xFF000000.toInt(),
            strokeWidth = 2f
        )
        val outsideStroke = StrokeModel(
            id = "s-outside",
            points = listOf(SerializablePoint(200f, 200f), SerializablePoint(220f, 220f)),
            colorArgb = 0xFF000000.toInt(),
            strokeWidth = 2f
        )

        val page = PageModel(
            strokes = listOf(enclosedStroke, crossingStroke, outsideStroke),
            shapes = listOf(
                ShapeModel(
                    id = "sh-1",
                    type = ShapeType.RECTANGLE,
                    startX = 20f,
                    startY = 20f,
                    endX = 40f,
                    endY = 40f,
                    strokeColorArgb = 0xFF000000.toInt(),
                    strokeWidth = 2f
                )
            ),
            textBoxes = listOf(
                TextBoxModel(
                    id = "tb-1",
                    x = 25f,
                    y = 25f,
                    width = 30f,
                    height = 20f,
                    text = "Hello",
                    colorArgb = 0xFF000000.toInt()
                )
            )
        )

        val selection = LassoEngine.findSelection(page, squarePolygon)

        assertEquals(2, selection.strokeIds.size)
        assertTrue(selection.strokeIds.contains("s-enclosed"))
        assertTrue(selection.strokeIds.contains("s-crossing"))
        assertFalse(selection.strokeIds.contains("s-outside"))

        assertEquals(1, selection.shapeIds.size)
        assertTrue(selection.shapeIds.contains("sh-1"))

        assertEquals(1, selection.textBoxIds.size)
        assertTrue(selection.textBoxIds.contains("tb-1"))
    }

    @Test
    fun testTranslateSelection() {
        val stroke = StrokeModel(
            id = "s1",
            points = listOf(SerializablePoint(10f, 10f), SerializablePoint(20f, 20f)),
            colorArgb = 0xFF000000.toInt(),
            strokeWidth = 2f
        )
        val page = PageModel(strokes = listOf(stroke))
        val selection = LassoEngine.findSelection(page, squarePolygon)

        val movedPage = LassoEngine.translate(page, selection, dx = 15f, dy = 25f)
        val movedStroke = movedPage.strokes.first { it.id == "s1" }

        assertEquals(25f, movedStroke.points[0].x, 0.01f)
        assertEquals(35f, movedStroke.points[0].y, 0.01f)
    }

    @Test
    fun testScaleSelection() {
        val stroke = StrokeModel(
            id = "s1",
            points = listOf(SerializablePoint(10f, 10f), SerializablePoint(20f, 20f)),
            colorArgb = 0xFF000000.toInt(),
            strokeWidth = 2f
        )
        val page = PageModel(strokes = listOf(stroke))
        val selection = LassoEngine.findSelection(page, squarePolygon)

        val scaledPage = LassoEngine.scale(page, selection, scaleFactor = 2f, pivot = Offset(0f, 0f))
        val scaledStroke = scaledPage.strokes.first { it.id == "s1" }

        assertEquals(20f, scaledStroke.points[0].x, 0.01f)
        assertEquals(20f, scaledStroke.points[0].y, 0.01f)
        assertEquals(40f, scaledStroke.points[1].x, 0.01f)
        assertEquals(40f, scaledStroke.points[1].y, 0.01f)
        assertEquals(4f, scaledStroke.strokeWidth, 0.01f)
    }

    @Test
    fun testRecolorAndDuplicateAndDelete() {
        val stroke = StrokeModel(
            id = "s1",
            points = listOf(SerializablePoint(10f, 10f)),
            colorArgb = 0xFF000000.toInt(),
            strokeWidth = 2f
        )
        val page = PageModel(strokes = listOf(stroke))
        val selection = LassoEngine.findSelection(page, squarePolygon)

        // Recolor
        val recoloredPage = LassoEngine.recolorStrokes(page, selection.strokeIds, 0xFFFF0000.toInt())
        assertEquals(0xFFFF0000.toInt(), recoloredPage.strokes.first().colorArgb)

        // Duplicate
        val (dupPage, dupSelection) = LassoEngine.duplicate(page, selection, Offset(50f, 50f))
        assertEquals(2, dupPage.strokes.size)
        assertEquals(1, dupSelection.strokeIds.size)

        // Delete
        val deletedPage = LassoEngine.delete(page, selection)
        assertEquals(0, deletedPage.strokes.size)
    }

    @Test
    fun testLassoFloatingMenuBoundsAndHitTesting() {
        val viewport = ViewportState().apply {
            containerSize = Size(1000f, 1000f)
            panX = 0f
            panY = 0f
            scale = 1f
        }

        // 1. Selection in middle of screen: menu should be placed above selection
        val midBounds = Rect(200f, 300f, 400f, 500f)
        val menuBoundsAbove = calculateLassoMenuScreenBounds(midBounds, viewport, density = 1f)
        assertTrue(menuBoundsAbove.bottom <= midBounds.top)
        assertEquals(300f, (menuBoundsAbove.left + menuBoundsAbove.right) / 2f, 1f) // Centered around centerX=300

        // 2. Selection near top: menu should flip to below selection
        val topBounds = Rect(200f, 20f, 400f, 200f)
        val menuBoundsBelow = calculateLassoMenuScreenBounds(topBounds, viewport, density = 1f)
        assertTrue(menuBoundsBelow.top >= topBounds.bottom)

        // 3. Hit testing
        val tapOnMenu = Offset(menuBoundsAbove.left + 20f, menuBoundsAbove.top + 10f)
        assertTrue(isInsideLassoMenu(tapOnMenu, midBounds, viewport, density = 1f))

        // Inside selection, not on menu -> false
        val tapInsideSelection = Offset(300f, 400f)
        assertFalse(isInsideLassoMenu(tapInsideSelection, midBounds, viewport, density = 1f))

        // Far outside -> false
        val tapFarAway = Offset(50f, 50f)
        assertFalse(isInsideLassoMenu(tapFarAway, midBounds, viewport, density = 1f))
    }
}
