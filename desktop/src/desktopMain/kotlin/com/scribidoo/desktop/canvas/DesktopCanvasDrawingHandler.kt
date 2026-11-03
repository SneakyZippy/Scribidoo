package com.scribidoo.desktop.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.model.ShapeModel
import com.scribidoo.app.canvas.shape.ShapeRecognitionEngine
import com.scribidoo.app.canvas.state.CanvasState

object DesktopCanvasDrawingHandler {

    fun startDrawing(
        state: CanvasState,
        canvasPoint: Offset,
        change: PointerInputChange,
        hasHitItem: Boolean = false
    ) {
        val pressure = extractPressure(change, state)
        when (state.currentTool) {
            DrawingTool.PEN, DrawingTool.HIGHLIGHTER -> state.startStroke(canvasPoint, pressure)
            DrawingTool.STROKE_ERASER, DrawingTool.SEGMENT_ERASER -> {
                state.eraseAt(canvasPoint, state.viewportState.screenToCanvasDist(32f))
            }
            DrawingTool.SHAPE -> state.startShape(canvasPoint)
            DrawingTool.TEXT -> {
                if (!hasHitItem) {
                    state.activeTextBoxForEdit = null
                    state.pendingTextInsertPoint = canvasPoint
                }
            }
            DrawingTool.LASSO -> state.startLasso(canvasPoint)
            else -> {}
        }
    }

    fun continueDrawing(
        state: CanvasState,
        canvasPoint: Offset,
        change: PointerInputChange,
        isAdjustingSnappedShape: Boolean
    ) {
        val pressure = extractPressure(change, state)
        when (state.currentTool) {
            DrawingTool.PEN, DrawingTool.HIGHLIGHTER -> {
                if (isAdjustingSnappedShape) state.adjustSnappedShape(canvasPoint)
                else state.appendStrokePoint(canvasPoint, pressure)
            }
            DrawingTool.STROKE_ERASER, DrawingTool.SEGMENT_ERASER -> {
                state.eraseAt(canvasPoint, state.viewportState.screenToCanvasDist(32f))
            }
            DrawingTool.SHAPE -> state.updateShape(canvasPoint)
            DrawingTool.LASSO -> state.appendLassoPoint(canvasPoint)
            else -> {}
        }
    }

    fun trySnapActiveStrokeToShape(state: CanvasState, holdOrigin: Offset): Boolean {
        val stroke = state.activeStroke ?: return false
        if (stroke.points.size < 8) return false
        val recognized = ShapeRecognitionEngine.recognize(stroke.points) ?: return false
        val shape = ShapeModel(
            type = recognized.type,
            startX = recognized.startX,
            startY = recognized.startY,
            endX = recognized.endX,
            endY = recognized.endY,
            vertexX = recognized.vertexX,
            vertexY = recognized.vertexY,
            strokeColorArgb = stroke.colorArgb,
            strokeWidth = stroke.strokeWidth
        )
        state.snapStrokeToShape(shape, holdOrigin = holdOrigin)
        return true
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
