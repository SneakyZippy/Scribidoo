package com.scribidoo.app.canvas.viewport

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.scribidoo.app.canvas.model.CanvasLayoutMode

class ViewportState(
    initialPan: Offset = Offset.Zero,
    initialScale: Float = 1.0f,
    val minScale: Float = 0.15f,
    val maxScale: Float = 8.0f
) {
    var panX by mutableFloatStateOf(initialPan.x)
    var panY by mutableFloatStateOf(initialPan.y)
    var scale by mutableFloatStateOf(initialScale)

    var containerSize by mutableStateOf(Size.Zero)

    val pan: Offset
        get() = Offset(panX, panY)

    /**
     * Converts a point from Screen (pixel) space into Document/Canvas local space.
     * canvasPoint = (screenPoint - pan) / scale
     */
    fun screenToCanvas(screenOffset: Offset): Offset {
        return Offset(
            x = (screenOffset.x - panX) / scale,
            y = (screenOffset.y - panY) / scale
        )
    }

    /**
     * Converts a point from Document/Canvas local space to Screen (pixel) space.
     * screenPoint = canvasPoint * scale + pan
     */
    fun canvasToScreen(canvasOffset: Offset): Offset {
        return Offset(
            x = canvasOffset.x * scale + panX,
            y = canvasOffset.y * scale + panY
        )
    }

    fun screenToCanvasDist(distance: Float): Float {
        return distance / scale
    }

    fun canvasToScreenDist(distance: Float): Float {
        return distance * scale
    }

    fun panBy(delta: Offset) {
        panX += delta.x
        panY += delta.y
    }

    /**
     * Zooms around a screen focal point so the content under the focal point stays in place.
     */
    fun zoomBy(zoomFactor: Float, focalPoint: Offset = Offset(containerSize.width / 2f, containerSize.height / 2f)) {
        val oldScale = scale
        val newScale = (oldScale * zoomFactor).coerceIn(minScale, maxScale)
        if (oldScale == newScale) return

        val scaleRatio = newScale / oldScale
        panX = focalPoint.x - (focalPoint.x - panX) * scaleRatio
        panY = focalPoint.y - (focalPoint.y - panY) * scaleRatio
        scale = newScale
    }

    fun centerAndFit(pageWidth: Float, pageHeight: Float, padding: Float = 48f) {
        if (containerSize.width <= 0 || containerSize.height <= 0) return

        val availWidth = (containerSize.width - padding * 2).coerceAtLeast(100f)
        val availHeight = (containerSize.height - padding * 2).coerceAtLeast(100f)

        val fitScaleW = availWidth / pageWidth
        val fitScaleH = availHeight / pageHeight
        val targetScale = minOf(fitScaleW, fitScaleH).coerceIn(minScale, maxScale)

        scale = targetScale
        panX = (containerSize.width - pageWidth * targetScale) / 2f
        panY = (containerSize.height - pageHeight * targetScale) / 2f
    }

    fun resetZoom(centerPoint: Offset = Offset(containerSize.width / 2f, containerSize.height / 2f)) {
        zoomBy(1.0f / scale, centerPoint)
    }
}
