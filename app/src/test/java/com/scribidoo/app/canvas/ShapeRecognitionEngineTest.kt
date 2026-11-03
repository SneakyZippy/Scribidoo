package com.scribidoo.app.canvas

import com.scribidoo.app.canvas.model.SerializablePoint
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.shape.ShapeRecognitionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ShapeRecognitionEngineTest {

    @Test
    fun testStraightLineRecognition() {
        val points = (0..20).map { i ->
            SerializablePoint(i * 10f, i * 5f + (if (i % 2 == 0) 1f else -1f))
        }
        val recognized = ShapeRecognitionEngine.recognize(points)
        assertNotNull(recognized)
        assertEquals(ShapeType.LINE, recognized!!.type)
        assertEquals(0f, recognized.startX, 0.1f)
        assertEquals(200f, recognized.endX, 0.1f)
    }

    @Test
    fun testCircleRecognition() {
        val center = 100f
        val radius = 50f
        val steps = 30
        val points = (0..steps).map { i ->
            val angle = (i.toFloat() / steps) * 2f * Math.PI.toFloat()
            val noise = if (i % 3 == 0) 2f else -2f
            val r = radius + noise
            SerializablePoint(center + r * cos(angle), center + r * sin(angle))
        }
        val recognized = ShapeRecognitionEngine.recognize(points)
        assertNotNull(recognized)
        assertEquals(ShapeType.ELLIPSE, recognized!!.type)
    }

    @Test
    fun testRectangleRecognition() {
        val points = mutableListOf<SerializablePoint>()
        // Top edge
        for (x in 10..100 step 10) points.add(SerializablePoint(x.toFloat(), 10f))
        // Right edge
        for (y in 10..80 step 10) points.add(SerializablePoint(100f, y.toFloat()))
        // Bottom edge
        for (x in 100 downTo 10 step 10) points.add(SerializablePoint(x.toFloat(), 80f))
        // Left edge
        for (y in 80 downTo 10 step 10) points.add(SerializablePoint(10f, y.toFloat()))

        val recognized = ShapeRecognitionEngine.recognize(points)
        assertNotNull(recognized)
        assertEquals(ShapeType.RECTANGLE, recognized!!.type)
    }

    @Test
    fun testTriangleRecognition() {
        val points = mutableListOf<SerializablePoint>()
        // Side 1: (50, 10) to (100, 100)
        for (i in 0..10) {
            val t = i / 10f
            points.add(SerializablePoint(50f + 50f * t, 10f + 90f * t))
        }
        // Side 2: (100, 100) to (0, 100)
        for (i in 0..10) {
            val t = i / 10f
            points.add(SerializablePoint(100f - 100f * t, 100f))
        }
        // Side 3: (0, 100) to (50, 10)
        for (i in 0..10) {
            val t = i / 10f
            points.add(SerializablePoint(50f * t, 100f - 90f * t))
        }

        val recognized = ShapeRecognitionEngine.recognize(points)
        assertNotNull(recognized)
        assertEquals(ShapeType.TRIANGLE, recognized!!.type)
    }

    @Test
    fun testRandomScribbleRejection() {
        val scribble = listOf(
            SerializablePoint(10f, 10f),
            SerializablePoint(50f, 80f),
            SerializablePoint(20f, 30f),
            SerializablePoint(90f, 15f),
            SerializablePoint(15f, 90f)
        )
        val recognized = ShapeRecognitionEngine.recognize(scribble)
        assertNull(recognized)
    }

    @Test
    fun testNearSquareSnapsToExactSquare() {
        val points = mutableListOf<SerializablePoint>()
        // Slightly rectangular: width 90, height 85 (ratio ~ 1.05)
        for (x in 10..100 step 10) points.add(SerializablePoint(x.toFloat(), 10f))
        for (y in 10..95 step 10) points.add(SerializablePoint(100f, y.toFloat()))
        for (x in 100 downTo 10 step 10) points.add(SerializablePoint(x.toFloat(), 95f))
        for (y in 95 downTo 10 step 10) points.add(SerializablePoint(10f, y.toFloat()))

        val recognized = ShapeRecognitionEngine.recognize(points)
        assertNotNull(recognized)
        assertEquals(ShapeType.RECTANGLE, recognized!!.type)
        // Width and height should be snapped to exact equality
        val w = kotlin.math.abs(recognized.endX - recognized.startX)
        val h = kotlin.math.abs(recognized.endY - recognized.startY)
        assertEquals(w, h, 0.001f)
    }

    @Test
    fun testNearCircleSnapsToExactCircle() {
        val center = 100f
        val steps = 30
        // Ellipse with rx=50, ry=48 (ratio ~ 1.04)
        val points = (0..steps).map { i ->
            val angle = (i.toFloat() / steps) * 2f * Math.PI.toFloat()
            SerializablePoint(center + 50f * cos(angle), center + 48f * sin(angle))
        }
        val recognized = ShapeRecognitionEngine.recognize(points)
        assertNotNull(recognized)
        assertEquals(ShapeType.ELLIPSE, recognized!!.type)
        val w = kotlin.math.abs(recognized.endX - recognized.startX)
        val h = kotlin.math.abs(recognized.endY - recognized.startY)
        assertEquals(w, h, 0.001f)
    }

    @Test
    fun testRightAngleRecognition() {
        val points = mutableListOf<SerializablePoint>()
        // Vertical arm: (50, 20) -> (50, 120)
        for (i in 0..10) {
            val t = i / 10f
            points.add(SerializablePoint(50f + (if (i % 2 == 0) 1f else -1f), 20f + 100f * t))
        }
        // Horizontal arm: (50, 120) -> (150, 120)
        for (i in 1..10) {
            val t = i / 10f
            points.add(SerializablePoint(50f + 100f * t, 120f + (if (i % 2 == 0) 1f else -1f)))
        }

        val recognized = ShapeRecognitionEngine.recognize(points)
        assertNotNull(recognized)
        assertEquals(ShapeType.RIGHT_ANGLE, recognized!!.type)
        assertNotNull(recognized.vertexX)
        assertNotNull(recognized.vertexY)
        assertEquals(50f, recognized.vertexX!!, 2.0f)
        assertEquals(120f, recognized.vertexY!!, 2.0f)
        // Verify arms are perpendicular: (startX - vx, startY - vy) dot (endX - vx, endY - vy) == 0
        val v1x = recognized.startX - recognized.vertexX!!
        val v1y = recognized.startY - recognized.vertexY!!
        val v2x = recognized.endX - recognized.vertexX
        val v2y = recognized.endY - recognized.vertexY
        val dot = v1x * v2x + v1y * v2y
        assertEquals(0f, dot, 0.01f)
    }

    @Test
    fun testHorizontalLineAngleSnapping() {
        // Tilted ~2 degrees
        val points = (0..10).map { i ->
            SerializablePoint(10f + i * 10f, 20f + i * 0.35f)
        }
        val recognized = ShapeRecognitionEngine.recognize(points)
        assertNotNull(recognized)
        assertEquals(ShapeType.LINE, recognized!!.type)
        assertEquals(20f, recognized.startY, 0.001f)
        assertEquals(20f, recognized.endY, 0.001f) // snapped to exact horizontal
    }

    @Test
    fun testVerticalLineAngleSnapping() {
        // Tilted ~2 degrees
        val points = (0..10).map { i ->
            SerializablePoint(30f + i * 0.35f, 10f + i * 10f)
        }
        val recognized = ShapeRecognitionEngine.recognize(points)
        assertNotNull(recognized)
        assertEquals(ShapeType.LINE, recognized!!.type)
        assertEquals(30f, recognized.startX, 0.001f)
        assertEquals(30f, recognized.endX, 0.001f) // snapped to exact vertical
    }

    @Test
    fun testDiagonal45LineAngleSnapping() {
        // Tilted slightly from 45 degrees
        val points = (0..10).map { i ->
            SerializablePoint(10f + i * 10f, 10f + i * 10.5f)
        }
        val recognized = ShapeRecognitionEngine.recognize(points)
        assertNotNull(recognized)
        assertEquals(ShapeType.LINE, recognized!!.type)
        val dx = recognized.endX - recognized.startX
        val dy = recognized.endY - recognized.startY
        assertEquals(dx, dy, 0.01f) // 45 degrees means dx == dy
    }

    @Test
    fun testRightAngleTopRightDown() {
        // User draws from (50, 20) right to (150, 20), then down to (150, 120)
        val points = mutableListOf<SerializablePoint>()
        for (i in 0..10) {
            val t = i / 10f
            points.add(SerializablePoint(50f + 100f * t, 20f + (if (i % 2 == 0) 0.5f else -0.5f)))
        }
        for (i in 1..10) {
            val t = i / 10f
            points.add(SerializablePoint(150f + (if (i % 2 == 0) 0.5f else -0.5f), 20f + 100f * t))
        }

        val recognized = ShapeRecognitionEngine.recognize(points)
        assertNotNull(recognized)
        assertEquals(ShapeType.RIGHT_ANGLE, recognized!!.type)
        assertEquals(50f, recognized.startX, 1f)
        assertEquals(20f, recognized.startY, 1f)
        assertEquals(150f, recognized.vertexX!!, 2f)
        assertEquals(20f, recognized.vertexY!!, 2f)
        assertEquals(150f, recognized.endX, 1f)
        assertEquals(120f, recognized.endY, 1f)
    }

    @Test
    fun testWobblyTriangleDoesNotBecomeRectangle() {
        // Triangle with a slight wobble on base that might introduce a 4th point
        val points = mutableListOf<SerializablePoint>()
        // Side 1: (60, 20) -> (120, 140)
        for (i in 0..10) {
            val t = i / 10f
            points.add(SerializablePoint(60f + 60f * t, 20f + 120f * t))
        }
        // Base: (120, 140) -> (0, 140) with a 15px bulge in the middle
        for (i in 0..10) {
            val t = i / 10f
            val wobbleY = if (i in 4..6) 15f else 0f
            points.add(SerializablePoint(120f - 120f * t, 140f + wobbleY))
        }
        // Side 3: (0, 140) -> (60, 20)
        for (i in 0..10) {
            val t = i / 10f
            points.add(SerializablePoint(60f * t, 140f - 120f * t))
        }

        val recognized = ShapeRecognitionEngine.recognize(points)
        assertNotNull(recognized)
        assertEquals(ShapeType.TRIANGLE, recognized!!.type)
        assertNotNull(recognized.vertexX)
        assertNotNull(recognized.vertexY)
    }
}
