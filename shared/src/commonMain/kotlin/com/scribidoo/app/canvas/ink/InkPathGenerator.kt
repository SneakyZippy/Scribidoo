package com.scribidoo.app.canvas.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.scribidoo.app.canvas.model.SerializablePoint

object InkPathGenerator {

    /**
     * Builds a smooth Bezier path through a list of points.
     * Uses midpoint quadratic Bezier interpolation for smooth handwriting.
     */
    fun createSmoothPath(points: List<SerializablePoint>): Path {
        val path = Path()
        if (points.isEmpty()) return path

        if (points.size == 1) {
            val p = points[0]
            path.moveTo(p.x, p.y)
            path.lineTo(p.x + 0.1f, p.y + 0.1f)
            return path
        }

        path.moveTo(points[0].x, points[0].y)

        if (points.size == 2) {
            path.lineTo(points[1].x, points[1].y)
            return path
        }

        // Quadratic Bezier through midpoints
        for (i in 0 until points.size - 1) {
            val pCurrent = points[i]
            val pNext = points[i + 1]

            val midX = (pCurrent.x + pNext.x) / 2f
            val midY = (pCurrent.y + pNext.y) / 2f

            path.quadraticTo(pCurrent.x, pCurrent.y, midX, midY)
        }

        val last = points.last()
        path.lineTo(last.x, last.y)

        return path
    }

    /**
     * Calculates stroke width dynamically according to pressure.
     */
    fun computeWidth(baseWidth: Float, pressure: Float, isHighlighter: Boolean): Float {
        if (isHighlighter) {
            return baseWidth * 3.5f // Highlighters are broader
        }
        val clampedPressure = pressure.coerceIn(0.1f, 1.0f)
        return baseWidth * (0.35f + 0.65f * clampedPressure)
    }
}
