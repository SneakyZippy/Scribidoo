package com.scribidoo.desktop.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.app.canvas.viewport.ViewportState
import kotlin.math.exp
import kotlin.math.hypot

object DesktopCanvasGestureHandler {

    private var lastTrackpadPanTimeMs = 0L

    fun handleScroll(event: PointerEvent, viewportState: ViewportState) {
        val change = event.changes.firstOrNull() ?: return
        val delta = change.scrollDelta
        if (delta.x == 0f && delta.y == 0f) return

        val focalPoint = change.position
        val isCtrl = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
        val isShift = event.keyboardModifiers.isShiftPressed
        val now = System.currentTimeMillis()
        val isFractionalOrHorizontal = (delta.y != 0f && delta.y % 1.0f != 0f) || delta.x != 0f

        if (isCtrl) {
            if (delta.y != 0f) {
                // Smooth progressive zoom for trackpad pinch and Ctrl+scroll
                val zoomFactor = exp(-delta.y * 0.035f).coerceIn(0.85f, 1.18f)
                viewportState.zoomBy(zoomFactor, focalPoint)
            }
        } else if (isShift) {
            val panDelta = if (delta.x != 0f) -delta.x * 24f else -delta.y * 24f
            viewportState.panBy(Offset(panDelta, 0f))
        } else if (isFractionalOrHorizontal || (now - lastTrackpadPanTimeMs < 300L)) {
            if (isFractionalOrHorizontal) lastTrackpadPanTimeMs = now
            viewportState.panBy(Offset(-delta.x * 24f, -delta.y * 24f))
        } else if (delta.y != 0f) {
            // Smooth mouse wheel zoom (approx 5% per notch instead of 15% jump)
            val zoomFactor = exp(-delta.y * 0.05f).coerceIn(0.85f, 1.18f)
            viewportState.zoomBy(zoomFactor, focalPoint)
        }
        change.consume()
    }

    fun isPanNavigationPress(event: PointerEvent, change: PointerInputChange, state: CanvasState): Boolean {
        val isHand = state.currentTool == DrawingTool.HAND
        val isMouseMiddle = change.type == PointerType.Mouse && event.buttons.isTertiaryPressed
        val isMouseSecondary = change.type == PointerType.Mouse && event.buttons.isSecondaryPressed
        val isSpacebarPan = state.isSpacebarPanActive
        val isTouchPanOnly = !state.palmConfig.isFingerDrawingEnabled && change.type == PointerType.Touch
        return isHand || isMouseMiddle || isMouseSecondary || isSpacebarPan || isTouchPanOnly
    }

    fun isDoubleTapNavigationPress(
        event: PointerEvent,
        change: PointerInputChange,
        state: CanvasState,
        nowMs: Long,
        lastTapTimeMs: Long,
        lastTapPoint: Offset
    ): Boolean {
        val isHand = state.currentTool == DrawingTool.HAND
        val isTouchPanOnly = !state.palmConfig.isFingerDrawingEnabled && change.type == PointerType.Touch
        val screenPoint = change.position
        return (isHand || isTouchPanOnly) &&
            change.type == PointerType.Touch &&
            (nowMs - lastTapTimeMs < 320L) &&
            hypot(screenPoint.x - lastTapPoint.x, screenPoint.y - lastTapPoint.y) < 32f
    }

    fun computeTwoPointerSpanAndFocal(pointers: Map<PointerId, Offset>): Pair<Offset, Float>? {
        if (pointers.size < 2) return null
        val pList = pointers.values.toList()
        val focal = (pList[0] + pList[1]) / 2f
        val span = hypot(pList[1].x - pList[0].x, pList[1].y - pList[0].y).coerceAtLeast(10f)
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
        if (previousSpan > 10f) {
            val ratio = (currentSpan / previousSpan).coerceIn(0.85f, 1.15f)
            viewportState.zoomBy(ratio, currentFocal)
        }
    }

    fun applyDoubleTapZoom(
        viewportState: ViewportState,
        pageWidth: Float,
        pageHeight: Float,
        screenPoint: Offset
    ) {
        if (viewportState.scale > 1.05f || viewportState.scale < 0.95f) {
            viewportState.centerAndFit(pageWidth, pageHeight, 48f)
        } else {
            viewportState.zoomBy(1.3f, screenPoint)
        }
    }
}
