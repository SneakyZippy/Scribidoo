package com.scribidoo.app.canvas

import androidx.compose.ui.geometry.Offset
import com.scribidoo.app.canvas.ink.EraserEngine
import com.scribidoo.app.canvas.model.SerializablePoint
import com.scribidoo.app.canvas.model.StrokeModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EraserEngineTest {

    @Test
    fun testStrokeEraserIntersection() {
        val stroke = StrokeModel(
            points = listOf(
                SerializablePoint(0f, 0f),
                SerializablePoint(100f, 0f)
            ),
            colorArgb = 0xFF000000.toInt(),
            strokeWidth = 4f
        )

        // Point right on the line segment
        assertTrue(EraserEngine.intersectsStroke(stroke, Offset(50f, 0f), eraserRadius = 10f))

        // Point near the line segment within radius (dist = 5 <= 10 + 2)
        assertTrue(EraserEngine.intersectsStroke(stroke, Offset(50f, 5f), eraserRadius = 10f))

        // Point far outside the line segment
        assertFalse(EraserEngine.intersectsStroke(stroke, Offset(50f, 50f), eraserRadius = 10f))
    }

    @Test
    fun testPrecisionSegmentSlicing() {
        val points = (0..10).map { i ->
            SerializablePoint(i * 10f, 50f) // 0 to 100 on y=50
        }
        val stroke = StrokeModel(
            points = points,
            colorArgb = 0xFF000000.toInt(),
            strokeWidth = 2f
        )

        // Erase center point around (50, 50) with radius 12
        val sliced = EraserEngine.sliceStroke(stroke, Offset(50f, 50f), eraserRadius = 12f)

        // Should produce 2 separate sub-strokes (left segment and right segment)
        assertEquals(2, sliced.size)
        assertTrue(sliced[0].points.all { it.x < 50f })
        assertTrue(sliced[1].points.all { it.x > 50f })
    }
}
