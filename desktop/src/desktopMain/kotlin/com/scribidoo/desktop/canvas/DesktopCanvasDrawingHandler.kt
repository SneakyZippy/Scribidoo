package com.scribidoo.desktop.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.state.CanvasState

object DesktopCanvasDrawingHandler {

    fun startDrawing(state: CanvasState, canvasPoint: Offset, change: PointerInputChange) {
        val pressure = extractPressure(change, state)
        when (state.currentTool) {
            DrawingTool.PEN, DrawingTool.HIGHLIGHTER -> state.startStroke(canvasPoint, pressure)
            DrawingTool.STROKE_ERASER, DrawingTool.SEGMENT_ERASER -> state.eraseAt(canvasPoint, state.viewportState.screenToCanvasDist(32f))
            DrawingTool.SHAPE -> state.startShape(canvasPoint)
            DrawingTool.TEXT -> state.pendingTextInsertPoint = canvasPoint
            DrawingTool.LASSO -> state.startLasso(canvasPoint)
            else -> {}
        }
    }

    fun continueDrawing(state: CanvasState, canvasPoint: Offset, change: PointerInputChange, isAdjustingSnappedShape: Boolean) {
        val pressure = extractPressure(change, state)
        when (state.currentTool) {
            DrawingTool.PEN, DrawingTool.HIGHLIGHTER -> {
                if (isAdjustingSnappedShape) state.adjustSnappedShape(canvasPoint)
                else state.appendStrokePoint(canvasPoint, pressure)
            }
            DrawingTool.STROKE_ERASER, DrawingTool.SEGMENT_ERASER -> state.eraseAt(canvasPoint, state.viewportState.screenToCanvasDist(32f))
            DrawingTool.SHAPE -> state.updateShape(canvasPoint)
            DrawingTool.LASSO -> state.appendLassoPoint(canvasPoint)
            else -> {}
        }
    }

    fun finishDrawing(state: CanvasState) {
        when (state.currentTool) {
            DrawingTool.PEN, DrawingTool.HIGHLIGHTER -> state.finishStroke()
            DrawingTool.STROKE_ERASER, DrawingTool.SEGMENT_ERASER -> state.finishErasing()
            DrawingTool.SHAPE -> state.finishShape()
            DrawingTool.LASSO -> state.finishLasso()
            else -> {}
        }
    }

    private fun extractPressure(change: PointerInputChange, state: CanvasState): Float {
        return DesktopStylusHelper.computeEffectivePressure(change, state.palmConfig.stylusPressureCurve)
    }
}
