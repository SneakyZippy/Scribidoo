package com.scribidoo.app.canvas.ink

import androidx.compose.ui.geometry.Offset
import com.scribidoo.app.canvas.model.SerializablePoint
import com.scribidoo.app.canvas.model.StrokeModel
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object EraserEngine {

    /**
     * Tests if an eraser circle touches or intersects any segment in the stroke.
     */
    fun intersectsStroke(
        stroke: StrokeModel,
        eraserCenter: Offset,
        eraserRadius: Float
    ): Boolean {
        // Fast bounding box reject
        val hitRadius = eraserRadius + stroke.strokeWidth / 2f
        val bounds = stroke.bounds
        if (eraserCenter.x < bounds.left - hitRadius ||
            eraserCenter.x > bounds.right + hitRadius ||
            eraserCenter.y < bounds.top - hitRadius ||
            eraserCenter.y > bounds.bottom + hitRadius
        ) {
            return false
        }

        val points = stroke.points
        if (points.isEmpty()) return false
        if (points.size == 1) {
            return hypot(points[0].x - eraserCenter.x, points[0].y - eraserCenter.y) <= hitRadius
        }

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val dist = pointToSegmentDistance(
                eraserCenter.x, eraserCenter.y,
                p1.x, p1.y,
                p2.x, p2.y
            )
            if (dist <= hitRadius) {
                return true
            }
        }
        return false
    }

    /**
     * Precision segment eraser: splits the stroke into multiple strokes by omitting
     * points that fall within the eraser radius.
     */
    fun sliceStroke(
        stroke: StrokeModel,
        eraserCenter: Offset,
        eraserRadius: Float
    ): List<StrokeModel> {
        val hitRadius = eraserRadius + stroke.strokeWidth / 2f
        if (!intersectsStroke(stroke, eraserCenter, eraserRadius)) {
            return listOf(stroke)
        }

        val newSegments = mutableListOf<List<SerializablePoint>>()
        var currentSegment = mutableListOf<SerializablePoint>()

        for (point in stroke.points) {
            val dist = hypot(point.x - eraserCenter.x, point.y - eraserCenter.y)
            if (dist > hitRadius) {
                currentSegment.add(point)
            } else {
                if (currentSegment.size >= 2) {
                    newSegments.add(currentSegment)
                }
                currentSegment = mutableListOf()
            }
        }
        if (currentSegment.size >= 2) {
            newSegments.add(currentSegment)
        }

        return newSegments.map { points ->
            stroke.copy(
                id = java.util.UUID.randomUUID().toString(),
                points = points
            )
        }
    }

    private fun pointToSegmentDistance(
        px: Float, py: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) {
            return hypot(px - x1, py - y1)
        }

        // Project point onto line segment: t = [(p - a) . (b - a)] / |b - a|^2
        val t = ((px - x1) * dx + (py - y1) * dy) / lengthSquared
        val clampedT = max(0f, min(1f, t))
        val projX = x1 + clampedT * dx
        val projY = y1 + clampedT * dy

        return hypot(px - projX, py - projY)
    }
}
