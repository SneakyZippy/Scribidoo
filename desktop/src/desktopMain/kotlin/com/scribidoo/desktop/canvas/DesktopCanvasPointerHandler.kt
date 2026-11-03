package com.scribidoo.desktop.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.app.canvas.state.SelectableType
import com.scribidoo.app.canvas.state.SelectedItem
import com.scribidoo.desktop.media.DesktopAudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

class DesktopCanvasPointerState(
    val state: CanvasState,
    val audioPlayer: DesktopAudioPlayer,
    val scope: CoroutineScope,
    val density: Float
) {
    val activePointers = mutableMapOf<PointerId, Offset>()
    var previousFocalPoint: Offset? = null
    var previousSpan = 0f
    var isPanDragging = false
    var lastPanScreenPoint = Offset.Zero
    var isStylusErasing = false
    var isMultiTouchGesture = false
    var isMovingItem = false
    var isResizingItem = false
    var initialTouchCanvas = Offset.Zero
    var lastTouchCanvas = Offset.Zero
    var initialItemWidth = 100f
    var initialItemHeight = 100f
    var initialFontSize = 16f
    var pendingTouchItem: SelectedItem? = null
    var longPressJob: Job? = null
    var shapeHoldJob: Job? = null
    var isAdjustingSnappedShape = false
    var lastHoldCheckCanvasPoint = Offset.Zero
    var lastTapTime = 0L
    var lastTapPoint = Offset.Zero

    fun handlePointerEvent(event: PointerEvent) {
        when (event.type) {
            PointerEventType.Scroll -> DesktopCanvasGestureHandler.handleScroll(event, state.viewportState)
            PointerEventType.Press -> handlePress(event)
            PointerEventType.Move -> handleMove(event)
            PointerEventType.Release -> handleRelease(event)
            else -> if (event.changes.none { it.pressed }) activePointers.clear()
        }
    }

    private fun syncActivePointers(event: PointerEvent) {
        activePointers.clear()
        event.changes.forEach { if (it.pressed) activePointers[it.id] = it.position }
    }

    private fun handlePress(event: PointerEvent) {
        syncActivePointers(event)
        DesktopCanvasGestureHandler.computeTwoPointerSpanAndFocal(activePointers)?.let { (focal, span) ->
            previousFocalPoint = focal; previousSpan = span; isMultiTouchGesture = true
            cancelInking()
            event.changes.forEach { it.consume() }
            return
        }

        val change = event.changes.firstOrNull { it.pressed } ?: event.changes.firstOrNull() ?: return
        val screenPoint = change.position
        val canvasPoint = state.viewportState.screenToCanvas(screenPoint)
        if (handleNavigationOrStylusOnPress(event, change, screenPoint, canvasPoint)) return
        if (handleItemOrLassoOnPress(change, screenPoint, canvasPoint)) return

        val hitItem = DesktopCanvasItemHandler.findHitItem(state, canvasPoint)
        startLongPressDetection(hitItem, canvasPoint)
        shapeHoldJob?.cancel()
        isAdjustingSnappedShape = false
        lastHoldCheckCanvasPoint = canvasPoint
        DesktopCanvasDrawingHandler.startDrawing(state, canvasPoint, change, hasHitItem = hitItem != null)
        change.consume()
    }

    private fun handleNavigationOrStylusOnPress(
        event: PointerEvent,
        change: PointerInputChange,
        screenPoint: Offset,
        canvasPoint: Offset
    ): Boolean {
        val now = System.currentTimeMillis()
        if (DesktopCanvasGestureHandler.isDoubleTapNavigationPress(event, change, state, now, lastTapTime, lastTapPoint)) {
            lastTapTime = 0L
            DesktopCanvasGestureHandler.applyDoubleTapZoom(state.viewportState, state.currentPage.width, state.currentPage.height, screenPoint)
            cancelInking()
            change.consume()
            return true
        }
        lastTapTime = now; lastTapPoint = screenPoint

        if (DesktopCanvasGestureHandler.isPanNavigationPress(event, change, state)) {
            cancelInking()
            isPanDragging = true; lastPanScreenPoint = screenPoint
            change.consume()
            return true
        }
        isPanDragging = false

        val isBarrel = DesktopStylusHelper.isBarrelButtonPressed(change, event.buttons.isSecondaryPressed)
        if (change.type == PointerType.Eraser || (isBarrel && state.palmConfig.stylusBarrelAction == "ERASER")) {
            isStylusErasing = true
            state.eraseAt(canvasPoint, state.viewportState.screenToCanvasDist(36f))
            change.consume()
            return true
        }
        isStylusErasing = false
        if (isBarrel && state.palmConfig.stylusBarrelAction == "LASSO") {
            state.startLasso(canvasPoint)
            change.consume()
            return true
        }
        return false
    }

    private fun handleItemOrLassoOnPress(change: PointerInputChange, screenPoint: Offset, canvasPoint: Offset): Boolean {
        val handledItem = DesktopCanvasItemHandler.handleSelectedItemPress(
            state = state, canvasPoint = canvasPoint, audioPlayer = audioPlayer,
            onStartResize = { bounds ->
                isResizingItem = true; initialTouchCanvas = canvasPoint; lastTouchCanvas = canvasPoint
                initialItemWidth = bounds.width.coerceAtLeast(30f); initialItemHeight = bounds.height.coerceAtLeast(30f)
                val sel = state.selectedItem
                if (sel?.type == SelectableType.TEXT_BOX) {
                    initialFontSize = state.currentPage.textBoxes.firstOrNull { it.id == sel.id }?.fontSizeSp ?: 16f
                }
            },
            onStartMove = { isMovingItem = true; initialTouchCanvas = canvasPoint; lastTouchCanvas = canvasPoint }
        )
        val handledLasso = !handledItem && DesktopCanvasItemHandler.handleLassoPress(
            state = state, canvasPoint = canvasPoint, screenPoint = screenPoint, density = density,
            onStartLassoDrag = { initialTouchCanvas = canvasPoint; lastTouchCanvas = canvasPoint },
            onStartLassoScale = { initialTouchCanvas = canvasPoint; lastTouchCanvas = canvasPoint }
        )
        if (handledItem || handledLasso) change.consume()
        return handledItem || handledLasso
    }

    private fun startLongPressDetection(hitItem: SelectedItem?, canvasPoint: Offset) {
        longPressJob?.cancel()
        pendingTouchItem = hitItem
        if (hitItem == null) return
        initialTouchCanvas = canvasPoint; lastTouchCanvas = canvasPoint
        longPressJob = scope.launch {
            delay(350)
            if (state.activeStroke != null) state.cancelStroke()
            if (state.activeShape != null) state.activeShape = null
            state.selectedItem = hitItem
            isMovingItem = true
        }
    }

    private fun handleMove(event: PointerEvent) {
        syncActivePointers(event)
        if (activePointers.isEmpty()) return

        DesktopCanvasGestureHandler.computeTwoPointerSpanAndFocal(activePointers)?.let { (focal, span) ->
            isMultiTouchGesture = true
            cancelInking()
            DesktopCanvasGestureHandler.applyTwoPointerTransform(focal, span, previousFocalPoint, previousSpan, state.viewportState)
            previousFocalPoint = focal; previousSpan = span
            event.changes.forEach { it.consume() }
            return
        }
        if (isMultiTouchGesture) {
            event.changes.forEach { it.consume() }
            return
        }

        val change = event.changes.firstOrNull { it.pressed } ?: return
        if (hypot(change.position.x - lastTapPoint.x, change.position.y - lastTapPoint.y) > 14f) {
            lastTapTime = 0L
            if (pendingTouchItem != null && !isMovingItem && !isResizingItem) {
                longPressJob?.cancel(); pendingTouchItem = null
            }
        }
        if (isPanDragging) {
            state.viewportState.panBy(change.position - lastPanScreenPoint)
            lastPanScreenPoint = change.position
            change.consume()
            return
        }

        val canvasPoint = state.viewportState.screenToCanvas(change.position)
        if (handleStylusOrItemMove(event, change, canvasPoint)) return

        DesktopCanvasDrawingHandler.continueDrawing(state, canvasPoint, change, isAdjustingSnappedShape)
        scheduleShapeHoldCheck(canvasPoint)
        change.consume()
    }

    private fun handleStylusOrItemMove(event: PointerEvent, change: PointerInputChange, canvasPoint: Offset): Boolean {
        val isBarrel = DesktopStylusHelper.isBarrelButtonPressed(change, event.buttons.isSecondaryPressed)
        if (isStylusErasing || change.type == PointerType.Eraser || (isBarrel && state.palmConfig.stylusBarrelAction == "ERASER")) {
            state.eraseAt(canvasPoint, state.viewportState.screenToCanvasDist(36f))
            change.consume()
            return true
        }
        if (isBarrel && state.palmConfig.stylusBarrelAction == "LASSO") {
            state.appendLassoPoint(canvasPoint)
            change.consume()
            return true
        }
        val transformed = DesktopCanvasItemHandler.handleItemDragOrTransform(
            state, canvasPoint, lastTouchCanvas, isMovingItem, isResizingItem,
            initialItemWidth, initialItemHeight, initialFontSize
        )
        if (transformed) {
            lastTouchCanvas = canvasPoint
            change.consume()
        }
        return transformed
    }

    private fun scheduleShapeHoldCheck(canvasPoint: Offset) {
        val isInk = state.currentTool == DrawingTool.PEN || state.currentTool == DrawingTool.HIGHLIGHTER
        if (!isInk || isAdjustingSnappedShape || !state.palmConfig.isShapeRecognitionEnabled) return
        val dHold = canvasPoint - lastHoldCheckCanvasPoint
        if (hypot(dHold.x, dHold.y) > state.viewportState.screenToCanvasDist(14f)) {
            lastHoldCheckCanvasPoint = canvasPoint
            shapeHoldJob?.cancel()
            shapeHoldJob = scope.launch {
                delay(state.palmConfig.shapeHoldDurationMs)
                if (DesktopCanvasDrawingHandler.trySnapActiveStrokeToShape(state, canvasPoint)) {
                    isAdjustingSnappedShape = true
                }
            }
        }
    }

    private fun handleRelease(event: PointerEvent) {
        syncActivePointers(event)
        longPressJob?.cancel(); shapeHoldJob?.cancel()

        if (isMultiTouchGesture) {
            if (activePointers.isEmpty()) {
                isMultiTouchGesture = false; previousFocalPoint = null; previousSpan = 0f
            }
            event.changes.forEach { it.consume() }
            return
        }
        if (isPanDragging || isStylusErasing || isAdjustingSnappedShape || isMovingItem || isResizingItem) {
            finishSpecialInteractionOnRelease()
            event.changes.forEach { it.consume() }
            return
        }
        if (state.isDraggingLassoSelection || state.isScalingLassoSelection) {
            state.isDraggingLassoSelection = false; state.isScalingLassoSelection = false
            event.changes.forEach { it.consume() }
            return
        }

        val tappedItem = pendingTouchItem
        pendingTouchItem = null
        if (tappedItem?.type == SelectableType.TEXT_BOX && state.currentTool == DrawingTool.TEXT) {
            state.activeTextBoxForEdit = state.currentPage.textBoxes.firstOrNull { it.id == tappedItem.id }
        } else {
            DesktopCanvasDrawingHandler.finishDrawing(state)
            if (state.activeLassoPath.isNotEmpty() && state.currentTool != DrawingTool.LASSO) state.finishLasso()
        }
        event.changes.forEach { it.consume() }
    }

    private fun finishSpecialInteractionOnRelease() {
        isPanDragging = false
        if (isStylusErasing) { isStylusErasing = false; state.finishErasing() }
        if (isAdjustingSnappedShape) { isAdjustingSnappedShape = false; state.finishShape() }
        if (isMovingItem || isResizingItem) {
            isMovingItem = false; isResizingItem = false; pendingTouchItem = null
            state.commitTransform()
        }
    }

    private fun cancelInking() {
        if (state.activeStroke != null) state.cancelStroke()
        if (state.activeShape != null) state.activeShape = null
        isPanDragging = false; isStylusErasing = false
        longPressJob?.cancel(); shapeHoldJob?.cancel()
    }
}
