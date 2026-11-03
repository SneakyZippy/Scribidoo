package com.scribidoo.desktop.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import com.scribidoo.app.canvas.viewport.ViewportState
import kotlin.math.hypot

object DesktopCanvasGestureHandler {

    fun handleScroll(event: PointerEvent, viewportState: ViewportState) {
        val change = event.changes.firstOrNull() ?: return
        val delta = change.scrollDelta
        val focalPoint = change.position
        val isCtrl = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
        val isFractionalTrackpad = (delta.y != 0f && delta.y % 1.0f != 0f) || delta.x != 0f

        if (isCtrl) {
            val zoomFactor = if (delta.y < 0) 1.15f else 0.87f
            viewportState.zoomBy(zoomFactor, focalPoint)
        } else if (isFractionalTrackpad) {
            viewportState.panBy(Offset(-delta.x * 24f, -delta.y * 24f))
        } else {
            val zoomFactor = if (delta.y < 0) 1.15f else 0.87f
            viewportState.zoomBy(zoomFactor, focalPoint)
        }
        change.consume()
    }

    fun computeTwoPointerSpanAndFocal(pointers: Map<PointerId, Offset>): Pair<Offset, Float>? {
        if (pointers.size < 2) return null
        val pList = pointers.values.toList()
        val focal = (pList[0] + pList[1]) / 2f
        val span = hypot(pList[1].x - pList[0].x, pList[1].y - pList[0].y).coerceAtLeast(1f)
        return Pair(focal, span)
    }

    fun applyTwoPointerTransform(
        currentFocal: Offset,
        currentSpan: Float,
        previousFocal: Offset?,
        previousSpan: Float,
        viewportState: ViewportState
    ) {
        val prevFocal = previousFocal ?: currentFocal
        viewportState.panBy(currentFocal - prevFocal)
        if (previousSpan > 0f) {
            viewportState.zoomBy(currentSpan / previousSpan, currentFocal)
        }
    }
}
