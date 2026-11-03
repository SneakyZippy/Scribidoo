package com.scribidoo.app.canvas.shape

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.scribidoo.app.canvas.model.SerializablePoint
import com.scribidoo.app.canvas.model.ShapeType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

data class RecognizedShape(
    val type: ShapeType,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val vertexX: Float? = null,
    val vertexY: Float? = null
) {
    val bounds: Rect get() {
        var minX = min(startX, endX)
        var minY = min(startY, endY)
        var maxX = max(startX, endX)
        var maxY = max(startY, endY)
        if (vertexX != null) {
            minX = min(minX, vertexX)
            maxX = max(maxX, vertexX)
        }
        if (vertexY != null) {
            minY = min(minY, vertexY)
            maxY = max(maxY, vertexY)
        }
        return Rect(minX, minY, maxX, maxY)
    }
}

object ShapeRecognitionEngine {

    fun recognize(points: List<SerializablePoint>): RecognizedShape? {
        if (points.size < 6) return null
        val offsets = points.map { it.toOffset() }
        val totalLength = ShapeMathUtils.computePathLength(offsets)
        if (totalLength < 30f) return null

        val start = offsets.first()
        val end = offsets.last()
        val directDist = ShapeMathUtils.distance(start, end)
        val closureRatio = directDist / totalLength

        val bounds = ShapeMathUtils.computeBounds(offsets)
        val w = bounds.width
        val h = bounds.height
        if (w < 15f && h < 15f) return null

        // 1. Test for straight line or arrow
        if (closureRatio > 0.78f) {
            val arrow = detectArrow(offsets, directDist)
            if (arrow != null) return arrow
            if (isStraightLine(offsets, start, end, directDist)) {
                val (snappedEnd, _) = ShapeMathUtils.snapAngleIfClose(start, end)
                return RecognizedShape(ShapeType.LINE, start.x, start.y, snappedEnd.x, snappedEnd.y)
            }
        }

        // 2. Test for right-angle corner (open L-shape)
        val rightAngle = detectRightAngle(offsets, bounds, totalLength, directDist)
        if (rightAngle != null) return rightAngle

        // 3. Test closed shapes
        if (closureRatio < 0.35f || directDist < 60f) {
            val circle = detectCircleOrEllipse(offsets, bounds)
            if (circle != null) return circle

            val polyArea = ShapeMathUtils.computePolygonArea(offsets)
            val boxArea = w * h
            val areaRatio = if (boxArea > 0f) polyArea / boxArea else 0f
            val corners = ShapeMathUtils.extractPolygonCorners(offsets, bounds)

            if (corners.size == 3 || (areaRatio < 0.62f && corners.size in 3..5)) {
                val triCorners = ShapeMathUtils.pickTriangleVertices(corners)
                if (triCorners.size == 3) {
                    return RecognizedShape(
                        type = ShapeType.TRIANGLE,
                        startX = triCorners[0].x,
                        startY = triCorners[0].y,
                        vertexX = triCorners[1].x,
                        vertexY = triCorners[1].y,
                        endX = triCorners[2].x,
                        endY = triCorners[2].y
                    )
                }
            }

            if (corners.size == 4 && areaRatio >= 0.62f) {
                return snapToSquareOrCircleIfClose(RecognizedShape(ShapeType.RECTANGLE, bounds.left, bounds.top, bounds.right, bounds.bottom))
            }

            if (areaRatio >= 0.65f && isRoughRectangle(offsets, bounds)) {
                return snapToSquareOrCircleIfClose(RecognizedShape(ShapeType.RECTANGLE, bounds.left, bounds.top, bounds.right, bounds.bottom))
            }
        }

        return null
    }

    private fun detectRightAngle(
        points: List<Offset>,
        bounds: Rect,
        totalLength: Float,
        directDist: Float
    ): RecognizedShape? {
        if (directDist < 20f || directDist / totalLength > 0.94f) return null
        val eps = max(bounds.width, bounds.height) * 0.09f
        var rdp = ShapeMathUtils.ramerDouglasPeucker(points, eps)
        if (rdp.size in 4..5) {
            rdp = ShapeMathUtils.ramerDouglasPeucker(points, max(bounds.width, bounds.height) * 0.14f)
        }
        if (rdp.size != 3) return null

        val p0 = rdp[0]
        val v = rdp[1]
        val p2 = rdp[2]
        val d1 = ShapeMathUtils.distance(v, p0)
        val d2 = ShapeMathUtils.distance(v, p2)
        if (d1 < 15f || d2 < 15f) return null

        val v1 = Offset(p0.x - v.x, p0.y - v.y)
        val v2 = Offset(p2.x - v.x, p2.y - v.y)
        val cosAlpha = (v1.x * v2.x + v1.y * v2.y) / (d1 * d2)
        if (abs(cosAlpha) > 0.28f) return null

        val vIdx = points.indices.minByOrNull { ShapeMathUtils.distance(points[it], v) } ?: return null
        if (vIdx <= 1 || vIdx >= points.size - 2) return null
        if (!isStraightLine(points.subList(0, vIdx + 1), p0, v, d1) ||
            !isStraightLine(points.subList(vIdx, points.size), v, p2, d2)
        ) {
            return null
        }

        val (snappedP0, snappedP2) = snapRightAngleArms(p0, v, p2, d1, d2)
        return RecognizedShape(
            type = ShapeType.RIGHT_ANGLE,
            startX = snappedP0.x,
            startY = snappedP0.y,
            endX = snappedP2.x,
            endY = snappedP2.y,
            vertexX = v.x,
            vertexY = v.y
        )
    }

    private fun snapRightAngleArms(
        p0: Offset,
        v: Offset,
        p2: Offset,
        d1: Float,
        d2: Float
    ): Pair<Offset, Offset> {
        val t1 = atan2(p0.y - v.y, p0.x - v.x)
        val t2 = atan2(p2.y - v.y, p2.x - v.x)
        val halfPi = (Math.PI / 2.0).toFloat()

        val c1 = round(t1 / halfPi) * halfPi
        val diff1 = abs(ShapeMathUtils.normalizeAngle(t1 - c1))
        val c2 = round(t2 / halfPi) * halfPi
        val diff2 = abs(ShapeMathUtils.normalizeAngle(t2 - c2))

        val finalT1: Float
        val finalT2: Float
        if (min(diff1, diff2) <= 0.26f) {
            if (diff1 <= diff2) {
                finalT1 = c1
                val candA = finalT1 + halfPi
                val candB = finalT1 - halfPi
                finalT2 = if (abs(ShapeMathUtils.normalizeAngle(t2 - candA)) < abs(ShapeMathUtils.normalizeAngle(t2 - candB))) candA else candB
            } else {
                finalT2 = c2
                val candA = finalT2 + halfPi
                val candB = finalT2 - halfPi
                finalT1 = if (abs(ShapeMathUtils.normalizeAngle(t1 - candA)) < abs(ShapeMathUtils.normalizeAngle(t1 - candB))) candA else candB
            }
        } else {
            finalT1 = t1
            val candA = finalT1 + halfPi
            val candB = finalT1 - halfPi
            finalT2 = if (abs(ShapeMathUtils.normalizeAngle(t2 - candA)) < abs(ShapeMathUtils.normalizeAngle(t2 - candB))) candA else candB
        }

        val s0 = Offset(v.x + d1 * cos(finalT1), v.y + d1 * sin(finalT1))
        val s2 = Offset(v.x + d2 * cos(finalT2), v.y + d2 * sin(finalT2))
        return Pair(s0, s2)
    }

    private fun isStraightLine(points: List<Offset>, start: Offset, end: Offset, lineLen: Float): Boolean {
        if (lineLen < 20f) return false
        val maxDeviation = lineLen * 0.16f
        for (p in points) {
            val dev = ShapeMathUtils.perpendicularDistance(p, start, end)
            if (dev > maxDeviation) return false
        }
        return true
    }

    private fun detectArrow(points: List<Offset>, directDist: Float): RecognizedShape? {
        if (points.size < 12 || directDist < 40f) return null
        val splitIdx = (points.size * 0.75f).toInt()
        val shaft = points.subList(0, splitIdx)
        val head = points.subList(splitIdx, points.size)

        val shaftStart = shaft.first()
        val shaftEnd = shaft.last()
        val shaftDist = ShapeMathUtils.distance(shaftStart, shaftEnd)
        if (!isStraightLine(shaft, shaftStart, shaftEnd, shaftDist)) return null

        val shaftAngle = atan2(shaftEnd.y - shaftStart.y, shaftEnd.x - shaftStart.x)
        var hasArrowBarb = false
        for (p in head) {
            val headAngle = atan2(p.y - shaftEnd.y, p.x - shaftEnd.x)
            val angleDiff = abs(ShapeMathUtils.normalizeAngle(headAngle - shaftAngle))
            if (angleDiff > 1.8f) {
                hasArrowBarb = true
                break
            }
        }
        return if (hasArrowBarb) {
            val (snappedEnd, _) = ShapeMathUtils.snapAngleIfClose(shaftStart, shaftEnd)
            RecognizedShape(ShapeType.ARROW, shaftStart.x, shaftStart.y, snappedEnd.x, snappedEnd.y)
        } else null
    }

    private fun detectCircleOrEllipse(points: List<Offset>, bounds: Rect): RecognizedShape? {
        val centerX = (bounds.left + bounds.right) / 2f
        val centerY = (bounds.top + bounds.bottom) / 2f
        val radiusX = bounds.width / 2f
        val radiusY = bounds.height / 2f
        if (radiusX < 12f || radiusY < 12f) return null

        val minDim = min(bounds.width, bounds.height)
        val corners = listOf(bounds.topLeft, bounds.topRight, bounds.bottomRight, bounds.bottomLeft)
        val cornerDistances = corners.map { c -> points.minOf { ShapeMathUtils.distance(it, c) } }
        if (cornerDistances.all { it < minDim * 0.14f }) {
            return null
        }

        var totalError = 0f
        for (p in points) {
            val normX = (p.x - centerX) / radiusX
            val normY = (p.y - centerY) / radiusY
            val distFromEllipse = abs(sqrt(normX * normX + normY * normY) - 1.0f)
            totalError += distFromEllipse
        }
        val avgError = totalError / points.size
        return if (avgError < 0.20f && cornerDistances.all { it > minDim * 0.08f }) {
            snapToSquareOrCircleIfClose(RecognizedShape(ShapeType.ELLIPSE, bounds.left, bounds.top, bounds.right, bounds.bottom))
        } else null
    }

    private fun snapToSquareOrCircleIfClose(shape: RecognizedShape): RecognizedShape {
        if (shape.type != ShapeType.ELLIPSE && shape.type != ShapeType.RECTANGLE) return shape
        val w = abs(shape.endX - shape.startX)
        val h = abs(shape.endY - shape.startY)
        if (w <= 0f || h <= 0f) return shape
        val ratio = w / h
        if (ratio in 0.84f..1.18f) {
            val side = (w + h) / 2f
            val cx = (shape.startX + shape.endX) / 2f
            val cy = (shape.startY + shape.endY) / 2f
            return shape.copy(
                startX = cx - side / 2f,
                startY = cy - side / 2f,
                endX = cx + side / 2f,
                endY = cy + side / 2f
            )
        }
        return shape
    }

    private fun isRoughRectangle(points: List<Offset>, bounds: Rect): Boolean {
        val minDim = min(bounds.width, bounds.height)
        val corners = listOf(bounds.topLeft, bounds.topRight, bounds.bottomRight, bounds.bottomLeft)
        val cornerDistances = corners.map { c -> points.minOf { ShapeMathUtils.distance(it, c) } }
        return cornerDistances.all { it < minDim * 0.24f }
    }
}
