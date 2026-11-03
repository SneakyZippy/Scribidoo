package com.scribidoo.app.canvas.shape

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin

object ShapeMathUtils {

    fun distance(p1: Offset, p2: Offset): Float = hypot(p2.x - p1.x, p2.y - p1.y)

    fun perpendicularDistance(p: Offset, lineStart: Offset, lineEnd: Offset): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        val lineLen = hypot(dx, dy)
        if (lineLen == 0f) return distance(p, lineStart)
        return abs(dy * p.x - dx * p.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x) / lineLen
    }

    fun computePathLength(points: List<Offset>): Float {
        var len = 0f
        for (i in 0 until points.size - 1) {
            len += distance(points[i], points[i + 1])
        }
        return len
    }

    fun computeBounds(points: List<Offset>): Rect {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        return Rect(minX, minY, maxX, maxY)
    }

    fun normalizeAngle(angle: Float): Float {
        var a = angle
        val twoPi = (2 * Math.PI).toFloat()
        val pi = Math.PI.toFloat()
        while (a <= -pi) a += twoPi
        while (a > pi) a -= twoPi
        return a
    }

    /**
     * Magnetically snaps the end point to the nearest 45-degree angle (0°, 45°, 90°, etc.)
     * if the angle falls within [thresholdRad] (default ~8 degrees).
     */
    fun snapAngleIfClose(start: Offset, end: Offset, thresholdRad: Float = 0.14f): Pair<Offset, Boolean> {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val len = hypot(dx, dy)
        if (len < 12f) return Pair(end, false)

        val angle = atan2(dy, dx)
        val step = (Math.PI / 4.0).toFloat() // 45 degrees
        val targetStep = round(angle / step)
        val targetAngle = targetStep * step

        val diff = abs(normalizeAngle(angle - targetAngle))
        return if (diff <= thresholdRad) {
            val snappedX = start.x + len * cos(targetAngle)
            val snappedY = start.y + len * sin(targetAngle)
            Pair(Offset(snappedX, snappedY), true)
        } else {
            Pair(end, false)
        }
    }

    fun ramerDouglasPeucker(points: List<Offset>, epsilon: Float): List<Offset> {
        if (points.size < 3) return points
        var maxDist = 0f
        var index = 0
        val start = points.first()
        val end = points.last()

        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistance(points[i], start, end)
            if (dist > maxDist) {
                maxDist = dist
                index = i
            }
        }

        return if (maxDist > epsilon) {
            val recResults1 = ramerDouglasPeucker(points.subList(0, index + 1), epsilon)
            val recResults2 = ramerDouglasPeucker(points.subList(index, points.size), epsilon)
            recResults1.dropLast(1) + recResults2
        } else {
            listOf(start, end)
        }
    }

    fun computePolygonArea(points: List<Offset>): Float {
        if (points.size < 3) return 0f
        var area = 0f
        val n = points.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += points[i].x * points[j].y - points[j].x * points[i].y
        }
        return abs(area) / 2f
    }

    fun extractPolygonCorners(points: List<Offset>, bounds: Rect): List<Offset> {
        val diag = hypot(bounds.width, bounds.height)
        var simplified = ramerDouglasPeucker(points, diag * 0.08f)
        if (simplified.size <= 2) return simplified

        if (distance(simplified.first(), simplified.last()) < diag * 0.22f) {
            simplified = simplified.dropLast(1)
        }

        val merged = mutableListOf<Offset>()
        for (p in simplified) {
            if (merged.isEmpty() || distance(merged.last(), p) > diag * 0.08f) {
                merged.add(p)
            }
        }
        if (merged.size > 2 && distance(merged.first(), merged.last()) <= diag * 0.08f) {
            merged.removeAt(merged.lastIndex)
        }

        if (merged.size > 3) {
            val filtered = mutableListOf<Offset>()
            val n = merged.size
            for (i in 0 until n) {
                val prev = merged[(i - 1 + n) % n]
                val curr = merged[i]
                val next = merged[(i + 1) % n]
                val v1x = prev.x - curr.x
                val v1y = prev.y - curr.y
                val v2x = next.x - curr.x
                val v2y = next.y - curr.y
                val d1 = hypot(v1x, v1y)
                val d2 = hypot(v2x, v2y)
                if (d1 > 0f && d2 > 0f) {
                    val cosAngle = (v1x * v2x + v1y * v2y) / (d1 * d2)
                    if (cosAngle > -0.82f) {
                        filtered.add(curr)
                    }
                } else {
                    filtered.add(curr)
                }
            }
            if (filtered.size >= 3) return filtered
        }
        return merged
    }

    fun pickTriangleVertices(corners: List<Offset>): List<Offset> {
        if (corners.size == 3) return corners
        val n = corners.size
        val sharpness = corners.indices.map { i ->
            val prev = corners[(i - 1 + n) % n]
            val curr = corners[i]
            val next = corners[(i + 1) % n]
            val v1x = prev.x - curr.x
            val v1y = prev.y - curr.y
            val v2x = next.x - curr.x
            val v2y = next.y - curr.y
            val d1 = hypot(v1x, v1y)
            val d2 = hypot(v2x, v2y)
            val cosAngle = if (d1 > 0f && d2 > 0f) (v1x * v2x + v1y * v2y) / (d1 * d2) else -1f
            Pair(i, cosAngle)
        }
        val top3Indices = sharpness.sortedByDescending { it.second }.take(3).map { it.first }.sorted()
        return top3Indices.map { corners[it] }
    }
}
