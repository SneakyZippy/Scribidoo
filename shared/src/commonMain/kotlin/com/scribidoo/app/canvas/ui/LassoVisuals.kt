package com.scribidoo.app.canvas.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.scribidoo.app.canvas.viewport.ViewportState
import kotlin.math.hypot

object LassoVisualConstants {
    val SelectionColor = Color(0xFF0284C7)
    val FillColor = Color(0x180284C7)
    val PresetColors = listOf(
        Color(0xFF1E293B),
        Color(0xFF2563EB),
        Color(0xFFDC2626),
        Color(0xFF059669),
        Color(0xFF7C3AED),
        Color(0xFFF59E0B)
    )
}

fun DrawScope.drawLassoPath(points: List<Offset>, viewportScale: Float) {
    if (points.size < 2) return
    val invScale = 1f / viewportScale.coerceIn(0.15f, 4f)
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
        close()
    }

    drawPath(path = path, color = LassoVisualConstants.FillColor, style = Fill)
    drawPath(
        path = path,
        color = LassoVisualConstants.SelectionColor,
        style = Stroke(
            width = 2.2f * invScale,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f * invScale, 8f * invScale))
        )
    )
}

fun DrawScope.drawLassoSelectionOverlay(bounds: Rect, viewportScale: Float) {
    if (bounds.isEmpty) return
    val invScale = 1f / viewportScale.coerceIn(0.15f, 4f)

    drawRect(
        color = LassoVisualConstants.SelectionColor,
        topLeft = bounds.topLeft,
        size = bounds.size,
        style = Stroke(
            width = 2f * invScale,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * invScale, 6f * invScale))
        )
    )

    val handleRadius = 9f * invScale
    drawCircle(
        color = Color.White,
        radius = handleRadius,
        center = bounds.bottomRight
    )
    drawCircle(
        color = LassoVisualConstants.SelectionColor,
        radius = handleRadius,
        center = bounds.bottomRight,
        style = Stroke(width = 2.5f * invScale)
    )
}

fun isInsideLassoScaleHandle(canvasPoint: Offset, bounds: Rect, viewportScale: Float): Boolean {
    val invScale = 1f / viewportScale.coerceIn(0.15f, 4f)
    val hitRadius = 24f * invScale
    return hypot(canvasPoint.x - bounds.bottomRight.x, canvasPoint.y - bounds.bottomRight.y) <= hitRadius
}

fun calculateLassoMenuScreenBounds(
    selectionBounds: Rect,
    viewport: ViewportState,
    density: Float = 1f
): Rect {
    if (selectionBounds.isEmpty) return Rect.Zero
    val screenTopLeft = viewport.canvasToScreen(selectionBounds.topLeft)
    val screenTopRight = viewport.canvasToScreen(selectionBounds.topRight)
    val screenBottom = viewport.canvasToScreen(selectionBounds.bottomRight).y
    val centerX = (screenTopLeft.x + screenTopRight.x) / 2f

    val menuWidth = 190f * density
    val menuHeight = 44f * density
    val spacing = 12f * density

    val topOptionY = screenTopLeft.y - menuHeight - spacing
    val barY = if (topOptionY >= 16f * density) {
        topOptionY
    } else {
        screenBottom + spacing
    }

    val minX = 16f * density
    val maxX = if (viewport.containerSize.width > 0f) {
        (viewport.containerSize.width - menuWidth - 16f * density).coerceAtLeast(minX)
    } else {
        minX + 500f * density
    }
    val barX = (centerX - menuWidth / 2f).coerceIn(minX, maxX)

    return Rect(barX, barY, barX + menuWidth, barY + menuHeight)
}

fun isInsideLassoMenu(
    screenPoint: Offset,
    selectionBounds: Rect,
    viewport: ViewportState,
    density: Float = 1f,
    hasActivePopup: Boolean = false
): Boolean {
    val bounds = calculateLassoMenuScreenBounds(selectionBounds, viewport, density)
    if (bounds.isEmpty) return false

    val hitPadding = 12f * density
    val extraBottom = if (hasActivePopup) 65f * density else 0f
    val hitRect = Rect(
        left = bounds.left - hitPadding,
        top = bounds.top - hitPadding,
        right = bounds.right + hitPadding,
        bottom = bounds.bottom + hitPadding + extraBottom
    )
    return hitRect.contains(screenPoint)
}

