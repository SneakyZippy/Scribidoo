package com.scribidoo.desktop.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.state.CanvasState
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
        }
    }

    private fun handlePress(event: PointerEvent) {
        for (c in event.changes) {
            if (c.pressed) activePointers[c.id] = c.position
        }

        DesktopCanvasGestureHandler.computeTwoPointerSpanAndFocal(activePointers)?.let { (focal, span) ->
            previousFocalPoint = focal
            previousSpan = span
            isMultiTouchGesture = true
            cancelInking()
            event.changes.forEach { it.consume() }
            return
        }

        val change = event.changes.first()
        val screenPoint = change.position
        val canvasPoint = state.viewportState.screenToCanvas(screenPoint)

        val isHand = state.currentTool == DrawingTool.HAND
        val isTouchPanOnly = change.type == PointerType.Touch && !state.palmConfig.isFingerDrawingEnabled
        val isMousePan = change.type == PointerType.Mouse && (event.buttons.isSecondaryPressed || event.buttons.isTertiaryPressed)

        // Double-tap zoom/fit for touch & mouse (excluding digital stylus to avoid interfering with writing)
        val now = System.currentTimeMillis()
        val isDoubleTap = (now - lastTapTime < 320L) &&
            hypot(screenPoint.x - lastTapPoint.x, screenPoint.y - lastTapPoint.y) < 32f &&
            change.type != PointerType.Stylus && !isHand && !isMousePan

        if (isDoubleTap) {
            lastTapTime = 0L
            if (state.viewportState.scale > 1.05f || state.viewportState.scale < 0.95f) {
                state.viewportState.centerAndFit(state.currentPage.width, state.currentPage.height, 48f)
            } else {
                state.viewportState.zoomBy(1.4f, screenPoint)
            }
            cancelInking()
            change.consume()
            return
        }
        lastTapTime = now
        lastTapPoint = screenPoint

        if (isHand || isMousePan || isTouchPanOnly) {
            isPanDragging = true
            lastPanScreenPoint = screenPoint
            cancelInking()
            change.consume()
            return
        }
        isPanDragging = false

        val isBarrel = DesktopStylusHelper.isBarrelButtonPressed(change, event.buttons.isSecondaryPressed)
        if (change.type == PointerType.Eraser || (isBarrel && state.palmConfig.stylusBarrelAction == "ERASER")) {
            isStylusErasing = true
            state.eraseAt(canvasPoint, state.viewportState.screenToCanvasDist(36f))
            change.consume()
            return
        }
        isStylusErasing = false

        if (isBarrel && state.palmConfig.stylusBarrelAction == "LASSO") {
            state.startLasso(canvasPoint)
            change.consume()
            return
        }

        // Tap Audio Play Button or Selected Item Handles
        if (DesktopCanvasItemHandler.handleSelectedItemPress(
                state = state,
                canvasPoint = canvasPoint,
                audioPlayer = audioPlayer,
                onStartResize = { bounds ->
                    isResizingItem = true
                    initialTouchCanvas = canvasPoint; lastTouchCanvas = canvasPoint
                    initialItemWidth = bounds.width.coerceAtLeast(30f)
                    initialItemHeight = bounds.height.coerceAtLeast(30f)
                },
                onStartMove = {
                    isMovingItem = true
                    initialTouchCanvas = canvasPoint; lastTouchCanvas = canvasPoint
                }
            )
        ) {
            change.consume()
            return
        }

        // Lasso Tool interaction
        if (DesktopCanvasItemHandler.handleLassoPress(
                state = state,
                canvasPoint = canvasPoint,
                screenPoint = screenPoint,
                density = density,
                onStartLassoDrag = { initialTouchCanvas = canvasPoint; lastTouchCanvas = canvasPoint },
                onStartLassoScale = { initialTouchCanvas = canvasPoint; lastTouchCanvas = canvasPoint }
            )
        ) {
            change.consume()
            return
        }

        // Hit testing for 350ms instant lift-and-drag
        val hitItem = DesktopCanvasItemHandler.findHitItem(state, canvasPoint)
        if (hitItem != null) {
            pendingTouchItem = hitItem
            initialTouchCanvas = canvasPoint; lastTouchCanvas = canvasPoint
            longPressJob?.cancel()
            longPressJob = scope.launch {
                delay(350)
                state.selectedItem = hitItem
                isMovingItem = true
            }
        } else {
            pendingTouchItem = null
            longPressJob?.cancel()
        }

        DesktopCanvasDrawingHandler.startDrawing(state, canvasPoint, change)
        change.consume()
    }

    private fun handleMove(event: PointerEvent) {
        for (c in event.changes) {
            if (c.pressed) activePointers[c.id] = c.position
            else activePointers.remove(c.id)
        }

        DesktopCanvasGestureHandler.computeTwoPointerSpanAndFocal(activePointers)?.let { (currentFocal, currentSpan) ->
            isMultiTouchGesture = true
            DesktopCanvasGestureHandler.applyTwoPointerTransform(
                currentFocal = currentFocal,
                currentSpan = currentSpan,
                previousFocal = previousFocalPoint,
                previousSpan = previousSpan,
                viewportState = state.viewportState
            )
            previousFocalPoint = currentFocal
            previousSpan = currentSpan
            event.changes.forEach { it.consume() }
            return
        }

        if (isMultiTouchGesture) {
            event.changes.forEach { it.consume() }
            return
        }

        if (isPanDragging) {
            val change = event.changes.firstOrNull() ?: return
            val delta = change.position - lastPanScreenPoint
            lastPanScreenPoint = change.position
            state.viewportState.panBy(delta)
            change.consume()
            return
        }

        val change = event.changes.firstOrNull() ?: return
        if (hypot(change.position.x - lastTapPoint.x, change.position.y - lastTapPoint.y) > 16f) {
            lastTapTime = 0L
        }
        val canvasPoint = state.viewportState.screenToCanvas(change.position)

        val isBarrel = DesktopStylusHelper.isBarrelButtonPressed(change, event.buttons.isSecondaryPressed)
        if (isStylusErasing || change.type == PointerType.Eraser || (isBarrel && state.palmConfig.stylusBarrelAction == "ERASER")) {
            state.eraseAt(canvasPoint, state.viewportState.screenToCanvasDist(36f))
            change.consume()
            return
        }

        if (isBarrel && state.palmConfig.stylusBarrelAction == "LASSO") {
            state.appendLassoPoint(canvasPoint)
            change.consume()
            return
        }

        if (DesktopCanvasItemHandler.handleItemDragOrTransform(
                state = state,
                canvasPoint = canvasPoint,
                lastTouchCanvas = lastTouchCanvas,
                isMovingItem = isMovingItem,
                isResizingItem = isResizingItem,
                initialItemWidth = initialItemWidth,
                initialItemHeight = initialItemHeight,
                initialFontSize = initialFontSize
            )
        ) {
            lastTouchCanvas = canvasPoint
            change.consume()
            return
        }

        DesktopCanvasDrawingHandler.continueDrawing(state, canvasPoint, change, isAdjustingSnappedShape)
        change.consume()
    }

    private fun handleRelease(event: PointerEvent) {
        for (c in event.changes) {
            activePointers.remove(c.id)
        }
        if (activePointers.isEmpty()) {
            isMultiTouchGesture = false
        }
        previousFocalPoint = null
        previousSpan = 0f
        isPanDragging = false
        isStylusErasing = false
        isMovingItem = false
        isResizingItem = false
        longPressJob?.cancel()

        if (isAdjustingSnappedShape) {
            isAdjustingSnappedShape = false
            state.finishShape()
        }
        shapeHoldJob?.cancel()

        DesktopCanvasDrawingHandler.finishDrawing(state)
        if (state.activeLassoPath.isNotEmpty() && state.currentTool != DrawingTool.LASSO) {
            state.finishLasso()
        }
        state.isDraggingLassoSelection = false
        state.isScalingLassoSelection = false
        event.changes.forEach { it.consume() }
    }

    private fun cancelInking() {
        if (state.activeStroke != null) state.cancelStroke()
        if (state.activeShape != null) state.activeShape = null
        isPanDragging = false
        isStylusErasing = false
        longPressJob?.cancel()
    }
}
