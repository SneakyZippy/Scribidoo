package com.scribidoo.app.canvas.ui

import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.scribidoo.app.canvas.ink.InkPathGenerator
import com.scribidoo.app.canvas.input.ClassifiedTool
import com.scribidoo.app.canvas.input.InputClassifier
import com.scribidoo.app.canvas.input.InputVerdict
import com.scribidoo.app.canvas.model.AudioNoteModel
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.model.ImageModel
import com.scribidoo.app.canvas.model.PageModel
import com.scribidoo.app.canvas.model.ShapeModel
import com.scribidoo.app.canvas.shape.ShapeRecognitionEngine
import com.scribidoo.app.canvas.model.TextBoxModel
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.model.StrokeModel
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.app.canvas.state.SelectableType
import com.scribidoo.app.canvas.state.SelectedItem
import com.scribidoo.app.media.AudioPlaybackManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.hypot

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TabletCanvasView(
    state: CanvasState,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    val inputClassifier = remember(state.palmConfig) {
        InputClassifier(state.palmConfig)
    }

    // Gesture tracking state for 2-finger pan & pinch-to-zoom
    var previousFocalPoint by remember { mutableStateOf<Offset?>(null) }
    var previousSpan by remember { mutableFloatStateOf(0f) }
    var isViewportGestureActive by remember { mutableStateOf(false) }

    // Multi-touch & palm rejection pointer trackers
    var activeDrawingPointerId by remember { mutableStateOf<Int?>(null) }
    var viewportPointer0Id by remember { mutableStateOf<Int?>(null) }
    var viewportPointer1Id by remember { mutableStateOf<Int?>(null) }

    val audioManager = remember { AudioPlaybackManager(coroutineScope) }
    DisposableEffect(Unit) {
        onDispose { audioManager.stop() }
    }

    // Transformation & interaction states
    var isMovingItem by remember { mutableStateOf(false) }
    var isLiftingAndDragging by remember { mutableStateOf(false) }
    var isResizingItem by remember { mutableStateOf(false) }
    var isRotatingItem by remember { mutableStateOf(false) }
    var isMarginPanning by remember { mutableStateOf(false) }

    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var shapeHoldJob by remember { mutableStateOf<Job?>(null) }
    var isAdjustingSnappedShape by remember { mutableStateOf(false) }
    var lastHoldCheckCanvasPoint by remember { mutableStateOf(Offset.Zero) }
    var pendingTouchItem by remember { mutableStateOf<SelectedItem?>(null) }
    var pendingAudioPlay by remember { mutableStateOf<AudioNoteModel?>(null) }
    val strokePathCache = remember { mutableMapOf<String, androidx.compose.ui.graphics.Path>() }
    var initialTouchCanvas by remember { mutableStateOf(Offset.Zero) }
    var lastTouchCanvas by remember { mutableStateOf(Offset.Zero) }
    var initialItemWidth by remember { mutableFloatStateOf(100f) }
    var initialItemHeight by remember { mutableFloatStateOf(100f) }
    var initialFontSize by remember { mutableFloatStateOf(16f) }
    var initialRotationAngle by remember { mutableFloatStateOf(0f) }
    var initialImageRotation by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFFF3F4F6))
            .onSizeChanged { size ->
                state.viewportState.containerSize = size.toSize()
            }
            .pointerInteropFilter { motionEvent ->
                val cW = state.viewportState.containerSize.width
                val cH = state.viewportState.containerSize.height
                fun isPalm(pointerIdx: Int): Boolean = inputClassifier.isPalmTouch(motionEvent, pointerIdx, cW, cH)

                when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isViewportGestureActive = false
                        viewportPointer0Id = null
                        viewportPointer1Id = null
                        activeDrawingPointerId = null
                        previousFocalPoint = null
                        previousSpan = 0f
                        isMarginPanning = false
                        isMovingItem = false
                        isLiftingAndDragging = false
                        isResizingItem = false
                        isRotatingItem = false
                        shapeHoldJob?.cancel()
                        shapeHoldJob = null
                        isAdjustingSnappedShape = false

                        // Palm check on initial touch down:
                        if (isPalm(0)) {
                            return@pointerInteropFilter true
                        }

                        val screenPoint = Offset(motionEvent.x, motionEvent.y)
                        val canvasPoint = state.viewportState.screenToCanvas(screenPoint)

                        // 1. Check Paper Constrained Mode: Margin touches become canvas pan
                        val isConstrained = state.palmConfig.constrainDrawingToPage && state.document.layoutMode == CanvasLayoutMode.PAGED
                        val isOutsidePaper = isConstrained && (canvasPoint.x !in 0f..state.currentPage.width || canvasPoint.y !in 0f..state.currentPage.height)
                        if (isOutsidePaper) {
                            isMarginPanning = true
                            previousFocalPoint = screenPoint
                            activeDrawingPointerId = motionEvent.getPointerId(0)
                            return@pointerInteropFilter true
                        }

                        // 2. Check if tapping an ALREADY SELECTED item (resize handle, action buttons, or drag)
                        val curSel = state.selectedItem
                        if (curSel != null) {
                            val bounds = getSelectedItemBounds(curSel, state.currentPage)
                            if (bounds != null) {
                                val selImg = if (curSel.type == SelectableType.IMAGE) state.currentPage.images.firstOrNull { it.id == curSel.id } else null
                                val rotDeg = selImg?.rotationDegrees ?: 0f
                                val selCenter = Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f)
                                val testPoint = if (rotDeg != 0f) rotatePoint(canvasPoint, selCenter, -rotDeg) else canvasPoint

                                val resizeCenter = Offset(bounds.right, bounds.bottom)
                                val handleTouchDist = state.viewportState.screenToCanvasDist(36f)
                                val dResize = testPoint - resizeCenter

                                // A. Bottom-right Resize Handle
                                if (hypot(dResize.x, dResize.y) <= handleTouchDist) {
                                    isResizingItem = true
                                    lastTouchCanvas = canvasPoint
                                    initialTouchCanvas = canvasPoint
                                    initialItemWidth = bounds.width.coerceAtLeast(30f)
                                    initialItemHeight = bounds.height.coerceAtLeast(30f)
                                    if (curSel.type == SelectableType.TEXT_BOX) {
                                        val tb = state.currentPage.textBoxes.firstOrNull { it.id == curSel.id }
                                        initialFontSize = tb?.fontSizeSp ?: 16f
                                    }
                                    return@pointerInteropFilter true
                                }

                                // B. Top Rotation Handle for Images
                                if (curSel.type == SelectableType.IMAGE) {
                                    val invScale = 1f / state.viewportState.scale.coerceIn(0.15f, 4f)
                                    val rotHandleCenter = Offset(selCenter.x, bounds.top - 28f * invScale)
                                    val dRot = testPoint - rotHandleCenter
                                    if (hypot(dRot.x, dRot.y) <= handleTouchDist) {
                                        isRotatingItem = true
                                        initialRotationAngle = Math.toDegrees(kotlin.math.atan2((canvasPoint.y - selCenter.y).toDouble(), (canvasPoint.x - selCenter.x).toDouble())).toFloat()
                                        initialImageRotation = rotDeg
                                        return@pointerInteropFilter true
                                    }
                                }

                                val toolbarBounds = calculateItemFloatingToolbarBounds(curSel, state.currentPage, state.viewportState.scale)
                                val hitPad = state.viewportState.screenToCanvasDist(12f)
                                fun Rect.hitTest(pt: Offset): Boolean =
                                    pt.x in (left - hitPad)..(right + hitPad) && pt.y in (top - hitPad)..(bottom + hitPad)

                                // C. Action button (Edit Text / Aspect Ratio / Audio Options)
                                if (toolbarBounds?.actionBtnRect != null && toolbarBounds.actionBtnRect.hitTest(canvasPoint)) {
                                    when (curSel.type) {
                                        SelectableType.TEXT_BOX -> {
                                            state.activeTextBoxForEdit = state.currentPage.textBoxes.firstOrNull { it.id == curSel.id }
                                            state.selectedItem = null
                                        }
                                        SelectableType.IMAGE -> {
                                            state.toggleImageAspectRatio(curSel.id)
                                        }
                                        SelectableType.AUDIO_NOTE -> {
                                            state.activeAudioForMenu = state.currentPage.audioNotes.firstOrNull { it.id == curSel.id }
                                        }
                                        SelectableType.SHAPE -> {}
                                    }
                                    return@pointerInteropFilter true
                                }

                                // D. Rotate 90° button for Images
                                if (toolbarBounds?.rot90BtnRect != null && toolbarBounds.rot90BtnRect.hitTest(canvasPoint)) {
                                    state.rotateImageBy(curSel.id, 90f)
                                    return@pointerInteropFilter true
                                }

                                // E. Opacity toggle button for Images
                                if (toolbarBounds?.opacityBtnRect != null && toolbarBounds.opacityBtnRect.hitTest(canvasPoint)) {
                                    state.cycleImageOpacity(curSel.id)
                                    return@pointerInteropFilter true
                                }

                                // F. Delete button
                                if (toolbarBounds != null && toolbarBounds.deleteBtnRect.hitTest(canvasPoint)) {
                                    state.deleteSelectedItem()
                                    return@pointerInteropFilter true
                                }

                                // F. Inside bounds -> Move
                                if (bounds.contains(testPoint)) {
                                    isMovingItem = true
                                    lastTouchCanvas = canvasPoint
                                    initialTouchCanvas = canvasPoint
                                    return@pointerInteropFilter true
                                }

                                // Tapped outside the selected item -> deselect
                                state.selectedItem = null
                            }
                        }

                        // 2b. Check if interacting with an active Lasso Selection
                        if (state.currentTool == DrawingTool.LASSO && state.lassoSelection.isNotEmpty) {
                            val lassoBounds = state.lassoSelection.bounds
                            if (isInsideLassoMenu(screenPoint, lassoBounds, state.viewportState, density)) {
                                return@pointerInteropFilter false
                            }
                            if (isInsideLassoScaleHandle(canvasPoint, lassoBounds, state.viewportState.scale)) {
                                state.isScalingLassoSelection = true
                                state.pushHistory()
                                lastTouchCanvas = canvasPoint
                                initialTouchCanvas = canvasPoint
                                activeDrawingPointerId = motionEvent.getPointerId(0)
                                return@pointerInteropFilter true
                            } else if (lassoBounds.contains(canvasPoint)) {
                                state.isDraggingLassoSelection = true
                                state.pushHistory()
                                lastTouchCanvas = canvasPoint
                                initialTouchCanvas = canvasPoint
                                activeDrawingPointerId = motionEvent.getPointerId(0)
                                return@pointerInteropFilter true
                            } else {
                                state.clearLasso()
                            }
                        }

                        // 3. Check if tapping an Audio Note PLAY button directly (held for ACTION_UP so long-press doesn't play)
                        val hitAudioPlay = state.currentPage.audioNotes.lastOrNull { audio ->
                            val bounds = getAudioNoteBounds(audio)
                            val playBtnRadius = (bounds.height * 0.32f).coerceIn(10f, 24f)
                            val playCenter = Offset(bounds.right - playBtnRadius - 8f, audio.y)
                            val touchDist = state.viewportState.screenToCanvasDist(26f)
                            hypot(canvasPoint.x - playCenter.x, canvasPoint.y - playCenter.y) <= touchDist
                        }
                        pendingAudioPlay = hitAudioPlay

                        // 4. Hit Testing for LONG-PRESS (Instant Lift & Drag)
                        val hitAudio = state.currentPage.audioNotes.lastOrNull { getAudioNoteBounds(it).contains(canvasPoint) }
                        val hitText = if (hitAudio == null) state.currentPage.textBoxes.lastOrNull { getTextBoxBounds(it).contains(canvasPoint) } else null
                        val hitImage = if (hitAudio == null && hitText == null) state.currentPage.images.lastOrNull { isPointInImage(it, canvasPoint) } else null
                        val hitShape = if (hitAudio == null && hitText == null && hitImage == null) state.currentPage.shapes.lastOrNull { getShapeBounds(it).contains(canvasPoint) } else null

                        val hitItem = when {
                            hitAudio != null -> SelectedItem(hitAudio.id, SelectableType.AUDIO_NOTE)
                            hitText != null -> SelectedItem(hitText.id, SelectableType.TEXT_BOX)
                            hitImage != null -> SelectedItem(hitImage.id, SelectableType.IMAGE)
                            hitShape != null -> SelectedItem(hitShape.id, SelectableType.SHAPE)
                            else -> null
                        }

                        if (hitItem != null) {
                            pendingTouchItem = hitItem
                            initialTouchCanvas = canvasPoint
                            lastTouchCanvas = canvasPoint
                            longPressJob?.cancel()
                            longPressJob = coroutineScope.launch {
                                delay(350)
                                pendingAudioPlay = null // Cancel audio playback on long press lift
                                state.selectedItem = hitItem
                                isLiftingAndDragging = true
                                isMovingItem = true
                                if (state.activeStroke != null) {
                                    state.cancelStroke()
                                }
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            }
                        } else {
                            pendingTouchItem = null
                            pendingAudioPlay = null
                            longPressJob?.cancel()
                        }

                        // Register pointer 0 as active drawing pointer
                        activeDrawingPointerId = motionEvent.getPointerId(0)

                        if (state.currentTool == DrawingTool.HAND) {
                            previousFocalPoint = screenPoint
                        } else {
                            val pressure = motionEvent.pressure.coerceIn(0.1f, 1.0f)
                            when (state.currentTool) {
                                DrawingTool.PEN,
                                DrawingTool.HIGHLIGHTER -> {
                                    state.startStroke(canvasPoint, pressure)
                                }
                                DrawingTool.STROKE_ERASER,
                                DrawingTool.SEGMENT_ERASER -> {
                                    state.startErasing()
                                    val eraserRadius = state.viewportState.screenToCanvasDist(32f)
                                    state.eraseAt(canvasPoint, eraserRadius)
                                }
                                DrawingTool.SHAPE -> {
                                    state.startShape(canvasPoint)
                                }
                                DrawingTool.TEXT -> {
                                    if (hitText == null) {
                                        state.activeTextBoxForEdit = null
                                        state.pendingTextInsertPoint = canvasPoint
                                    }
                                }
                                DrawingTool.LASSO -> {
                                    state.startLasso(canvasPoint)
                                }
                                else -> {}
                            }
                        }
                    }

                    MotionEvent.ACTION_POINTER_DOWN -> {
                        longPressJob?.cancel()
                        longPressJob = null

                        if (inputClassifier.isGenuineTwoFingerGesture(motionEvent)) {
                            if (state.activeStroke != null) state.cancelStroke()
                            if (state.activeShape != null) state.activeShape = null
                            if (state.isErasing) state.finishErasing()
                            activeDrawingPointerId = null

                            isViewportGestureActive = true
                            viewportPointer0Id = motionEvent.getPointerId(0)
                            viewportPointer1Id = motionEvent.getPointerId(1)

                            val p0 = Offset(motionEvent.getX(0), motionEvent.getY(0))
                            val p1 = Offset(motionEvent.getX(1), motionEvent.getY(1))
                            previousFocalPoint = (p0 + p1) / 2f
                            previousSpan = hypot(p1.x - p0.x, p1.y - p0.y).coerceAtLeast(1f)
                        } else {
                            if (activeDrawingPointerId == null) {
                                val bestIdx = inputClassifier.findBestDrawingPointerIndex(motionEvent)
                                if (bestIdx != null) {
                                    activeDrawingPointerId = motionEvent.getPointerId(bestIdx)
                                    val screenPoint = Offset(motionEvent.getX(bestIdx), motionEvent.getY(bestIdx))
                                    val canvasPoint = state.viewportState.screenToCanvas(screenPoint)

                                    if (state.currentTool == DrawingTool.HAND) {
                                        previousFocalPoint = screenPoint
                                    } else {
                                        val pressure = motionEvent.getPressure(bestIdx).coerceIn(0.1f, 1.0f)
                                        when (state.currentTool) {
                                            DrawingTool.PEN,
                                            DrawingTool.HIGHLIGHTER -> state.startStroke(canvasPoint, pressure)
                                            DrawingTool.STROKE_ERASER,
                                            DrawingTool.SEGMENT_ERASER -> {
                                                state.startErasing()
                                                val eraserRadius = state.viewportState.screenToCanvasDist(32f)
                                                state.eraseAt(canvasPoint, eraserRadius)
                                            }
                                            DrawingTool.SHAPE -> state.startShape(canvasPoint)
                                            DrawingTool.LASSO -> state.startLasso(canvasPoint)
                                            else -> {}
                                        }
                                    }
                                }
                            }
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val screenPoint = Offset(motionEvent.x, motionEvent.y)
                        val canvasPoint = state.viewportState.screenToCanvas(screenPoint)

                        // Margin panning
                        if (isMarginPanning) {
                            if (previousFocalPoint != null) {
                                val delta = screenPoint - previousFocalPoint!!
                                state.viewportState.panBy(delta)
                            }
                            previousFocalPoint = screenPoint
                            return@pointerInteropFilter true
                        }

                        // Cancel pending long press and audio play if finger moved before 350ms elapsed
                        if ((pendingTouchItem != null || pendingAudioPlay != null) && !isLiftingAndDragging && !isMovingItem && !isResizingItem) {
                            val dTouch = canvasPoint - initialTouchCanvas
                            val dist = hypot(dTouch.x, dTouch.y)
                            if (dist > state.viewportState.screenToCanvasDist(12f)) {
                                longPressJob?.cancel()
                                longPressJob = null
                                pendingTouchItem = null
                                pendingAudioPlay = null
                            }
                        }

                        // Handle moving item (Instant Lift & Drag or selected drag)
                        if ((isMovingItem || isLiftingAndDragging) && state.selectedItem != null) {
                            val delta = canvasPoint - lastTouchCanvas
                            lastTouchCanvas = canvasPoint
                            state.moveSelectedItem(delta)
                            return@pointerInteropFilter true
                        }

                        // Handle Lasso selection move or scale
                        if (state.isScalingLassoSelection) {
                            val bounds = state.lassoSelection.bounds
                            val pivot = bounds.topLeft
                            val prevDistX = lastTouchCanvas.x - pivot.x
                            val curDistX = canvasPoint.x - pivot.x
                            if (prevDistX > 10f && curDistX > 10f) {
                                val factor = curDistX / prevDistX
                                state.scaleLassoSelection(factor, pivot)
                            }
                            lastTouchCanvas = canvasPoint
                            return@pointerInteropFilter true
                        }
                        if (state.isDraggingLassoSelection) {
                            val delta = canvasPoint - lastTouchCanvas
                            lastTouchCanvas = canvasPoint
                            state.moveLassoSelection(delta.x, delta.y)
                            return@pointerInteropFilter true
                        }

                        // Handle rotating selected image
                        if (isRotatingItem && state.selectedItem != null) {
                            val item = state.selectedItem!!
                            if (item.type == SelectableType.IMAGE) {
                                val img = state.currentPage.images.firstOrNull { it.id == item.id }
                                if (img != null) {
                                    val center = Offset(img.x + img.width / 2f, img.y + img.height / 2f)
                                    val curAngle = Math.toDegrees(kotlin.math.atan2((canvasPoint.y - center.y).toDouble(), (canvasPoint.x - center.x).toDouble())).toFloat()
                                    val delta = curAngle - initialRotationAngle
                                    var newRot = (initialImageRotation + delta % 360f + 360f) % 360f
                                    listOf(0f, 90f, 180f, 270f, 360f).forEach { snap ->
                                        if (kotlin.math.abs(newRot - snap) < 4f) newRot = snap % 360f
                                    }
                                    state.rotateImage(img.id, newRot)
                                }
                            }
                            return@pointerInteropFilter true
                        }

                        // Handle resizing selected item
                        if (isResizingItem && state.selectedItem != null) {
                            val item = state.selectedItem!!
                            val deltaX = canvasPoint.x - initialTouchCanvas.x
                            val deltaY = canvasPoint.y - initialTouchCanvas.y

                            when (item.type) {
                                SelectableType.IMAGE -> {
                                    val newW = (initialItemWidth + deltaX).coerceAtLeast(50f)
                                    val newH = (initialItemHeight + deltaY).coerceAtLeast(40f)
                                    state.resizeImage(item.id, newW, newH)
                                }
                                SelectableType.TEXT_BOX -> {
                                    val scale = ((initialItemWidth + deltaX).coerceAtLeast(30f)) / initialItemWidth
                                    state.resizeTextBox(item.id, initialFontSize * scale)
                                }
                                SelectableType.SHAPE -> {
                                    state.resizeShape(item.id, canvasPoint.x, canvasPoint.y)
                                }
                                SelectableType.AUDIO_NOTE -> {
                                    val newW = (initialItemWidth + deltaX * 2f).coerceIn(100f, 600f)
                                    val newH = (initialItemHeight + deltaY * 2f).coerceIn(36f, 160f)
                                    state.resizeAudioNote(item.id, newW, newH)
                                }
                            }
                            return@pointerInteropFilter true
                        }

                        if (isViewportGestureActive) {
                            val i0 = viewportPointer0Id?.let { motionEvent.findPointerIndex(it) } ?: -1
                            val i1 = viewportPointer1Id?.let { motionEvent.findPointerIndex(it) } ?: -1

                            if (i0 >= 0 && i1 >= 0) {
                                val p0 = Offset(motionEvent.getX(i0), motionEvent.getY(i0))
                                val p1 = Offset(motionEvent.getX(i1), motionEvent.getY(i1))
                                val currentFocal = (p0 + p1) / 2f
                                val currentSpan = hypot(p1.x - p0.x, p1.y - p0.y).coerceAtLeast(1f)

                                val prevFocal = previousFocalPoint ?: currentFocal
                                val delta = currentFocal - prevFocal
                                state.viewportState.panBy(delta)

                                if (previousSpan > 0f) {
                                    val scaleFactor = currentSpan / previousSpan
                                    state.viewportState.zoomBy(scaleFactor, currentFocal)
                                }

                                previousFocalPoint = currentFocal
                                previousSpan = currentSpan
                            }
                        } else if (activeDrawingPointerId != null) {
                            val fingerIdx = motionEvent.findPointerIndex(activeDrawingPointerId!!)
                            if (fingerIdx >= 0) {
                                if (!isPalm(fingerIdx)) {
                                    if (state.currentTool == DrawingTool.HAND) {
                                        if (previousFocalPoint != null) {
                                            val delta = screenPoint - previousFocalPoint!!
                                            state.viewportState.panBy(delta)
                                        }
                                        previousFocalPoint = screenPoint
                                    } else {
                                        val pressure = motionEvent.getPressure(fingerIdx).coerceIn(0.1f, 1.0f)
                                        when (state.currentTool) {
                                            DrawingTool.PEN,
                                            DrawingTool.HIGHLIGHTER -> {
                                                if (isAdjustingSnappedShape) {
                                                    if (state.adjustSnappedShape(canvasPoint)) {
                                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                    }
                                                } else {
                                                    state.appendStrokePoint(canvasPoint, pressure)
                                                    if (state.palmConfig.isShapeRecognitionEnabled) {
                                                        val dHold = canvasPoint - lastHoldCheckCanvasPoint
                                                        val distHold = hypot(dHold.x, dHold.y)
                                                        if (distHold > state.viewportState.screenToCanvasDist(14f)) {
                                                            lastHoldCheckCanvasPoint = canvasPoint
                                                            shapeHoldJob?.cancel()
                                                            shapeHoldJob = coroutineScope.launch {
                                                                delay(state.palmConfig.shapeHoldDurationMs)
                                                                val stroke = state.activeStroke
                                                                if (stroke != null && stroke.points.size >= 8) {
                                                                    val recognized = ShapeRecognitionEngine.recognize(stroke.points)
                                                                    if (recognized != null) {
                                                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
                                                                        state.snapStrokeToShape(shape, holdOrigin = canvasPoint)
                                                                        isAdjustingSnappedShape = true
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            DrawingTool.STROKE_ERASER,
                                            DrawingTool.SEGMENT_ERASER -> {
                                                val eraserRadius = state.viewportState.screenToCanvasDist(32f)
                                                state.eraseAt(canvasPoint, eraserRadius)
                                            }
                                            DrawingTool.SHAPE -> {
                                                if (state.updateShape(canvasPoint)) {
                                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                }
                                            }
                                            DrawingTool.LASSO -> {
                                                state.appendLassoPoint(canvasPoint)
                                            }
                                            else -> {}
                                        }
                                    }
                                }
                            }
                        }
                    }

                    MotionEvent.ACTION_POINTER_UP -> {
                        val liftedIndex = motionEvent.actionIndex
                        val liftedId = motionEvent.getPointerId(liftedIndex)

                        if (liftedId == viewportPointer0Id || liftedId == viewportPointer1Id) {
                            isViewportGestureActive = false
                            previousFocalPoint = null
                            previousSpan = 0f
                            viewportPointer0Id = null
                            viewportPointer1Id = null
                        }

                        shapeHoldJob?.cancel()
                        shapeHoldJob = null

                        if (isAdjustingSnappedShape) {
                            isAdjustingSnappedShape = false
                            state.finishShape()
                            activeDrawingPointerId = null
                            return@pointerInteropFilter true
                        }

                        if (liftedId == activeDrawingPointerId) {
                            when (state.currentTool) {
                                DrawingTool.PEN,
                                DrawingTool.HIGHLIGHTER -> state.finishStroke()
                                DrawingTool.STROKE_ERASER,
                                DrawingTool.SEGMENT_ERASER -> state.finishErasing()
                                DrawingTool.SHAPE -> state.finishShape()
                                DrawingTool.LASSO -> state.finishLasso()
                                else -> {}
                            }
                            activeDrawingPointerId = null
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        longPressJob?.cancel()
                        longPressJob = null

                        if (isMarginPanning) {
                            isMarginPanning = false
                            pendingAudioPlay = null
                            return@pointerInteropFilter true
                        }

                        if (isLiftingAndDragging || isMovingItem || isResizingItem || isRotatingItem) {
                            isLiftingAndDragging = false
                            isMovingItem = false
                            isResizingItem = false
                            isRotatingItem = false
                            pendingAudioPlay = null
                            state.commitTransform()
                            return@pointerInteropFilter true
                        }

                        if (state.isDraggingLassoSelection || state.isScalingLassoSelection) {
                            state.isDraggingLassoSelection = false
                            state.isScalingLassoSelection = false
                            return@pointerInteropFilter true
                        }

                        // Play audio ONLY on genuine quick tap of the play button
                        if (pendingAudioPlay != null) {
                            val audioToPlay = pendingAudioPlay!!
                            pendingAudioPlay = null
                            pendingTouchItem = null
                            audioManager.play(audioToPlay.localPath, audioToPlay.trimStartMs, audioToPlay.trimEndMs)
                            return@pointerInteropFilter true
                        }

                        // Quick tap on an item without holding for long press
                        if (pendingTouchItem != null) {
                            val tappedItem = pendingTouchItem!!
                            pendingTouchItem = null
                            state.selectedItem = tappedItem
                            when (tappedItem.type) {
                                SelectableType.AUDIO_NOTE -> {
                                    state.activeAudioForMenu = state.currentPage.audioNotes.firstOrNull { it.id == tappedItem.id }
                                }
                                SelectableType.TEXT_BOX -> {
                                    if (state.currentTool == DrawingTool.TEXT) {
                                        state.activeTextBoxForEdit = state.currentPage.textBoxes.firstOrNull { it.id == tappedItem.id }
                                    }
                                }
                                else -> {}
                            }
                        }

                        isViewportGestureActive = false
                        previousFocalPoint = null
                        previousSpan = 0f
                        viewportPointer0Id = null
                        viewportPointer1Id = null
                        shapeHoldJob?.cancel()
                        shapeHoldJob = null

                        if (isAdjustingSnappedShape) {
                            isAdjustingSnappedShape = false
                            state.finishShape()
                            activeDrawingPointerId = null
                            return@pointerInteropFilter true
                        }

                        if (activeDrawingPointerId != null) {
                            when (state.currentTool) {
                                DrawingTool.PEN,
                                DrawingTool.HIGHLIGHTER -> state.finishStroke()
                                DrawingTool.STROKE_ERASER,
                                DrawingTool.SEGMENT_ERASER -> state.finishErasing()
                                DrawingTool.SHAPE -> state.finishShape()
                                DrawingTool.LASSO -> state.finishLasso()
                                else -> {}
                            }
                            activeDrawingPointerId = null
                        }
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        longPressJob?.cancel()
                        longPressJob = null
                        shapeHoldJob?.cancel()
                        shapeHoldJob = null
                        isAdjustingSnappedShape = false
                        isLiftingAndDragging = false
                        isMovingItem = false
                        isResizingItem = false
                        isRotatingItem = false
                        shapeHoldJob?.cancel()
                        shapeHoldJob = null
                        isAdjustingSnappedShape = false
                        isMarginPanning = false
                        state.isDraggingLassoSelection = false
                        state.isScalingLassoSelection = false
                        pendingTouchItem = null
                        pendingAudioPlay = null
                        isViewportGestureActive = false
                        previousFocalPoint = null
                        previousSpan = 0f
                        viewportPointer0Id = null
                        viewportPointer1Id = null
                        shapeHoldJob?.cancel()
                        shapeHoldJob = null

                        if (isAdjustingSnappedShape) {
                            isAdjustingSnappedShape = false
                            state.finishShape()
                            activeDrawingPointerId = null
                            return@pointerInteropFilter true
                        }
                        if (state.activeStroke != null) state.cancelStroke()
                        if (state.activeShape != null) state.activeShape = null
                        if (state.isErasing) state.finishErasing()
                        activeDrawingPointerId = null
                    }
                }
                true
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val viewport = state.viewportState
            val layoutMode = state.document.layoutMode
            val curPage = state.currentPage

            // Apply matrix transformation for Pan & Zoom
            withTransform({
                translate(left = viewport.panX, top = viewport.panY)
                scale(scaleX = viewport.scale, scaleY = viewport.scale, pivot = Offset.Zero)
            }) {
                // 1. Background Layer
                when (layoutMode) {
                    CanvasLayoutMode.PAGED -> {
                        // Draw Page Sheet with subtle shadow/border
                        val pageRect = Rect(0f, 0f, curPage.width, curPage.height)
                        // Page drop shadow
                        drawRect(
                            color = Color(0x1F000000),
                            topLeft = Offset(4f, 6f),
                            size = Size(curPage.width, curPage.height)
                        )

                        // Page background (Render imported PDF/Image or Paper Guidelines)
                        val bgBitmap = state.backgroundBitmaps[curPage.index]
                        if (bgBitmap != null) {
                            drawImage(
                                image = bgBitmap,
                                dstSize = IntSize(curPage.width.toInt(), curPage.height.toInt())
                            )
                        } else {
                            drawRect(
                                color = Color.White,
                                topLeft = Offset.Zero,
                                size = Size(curPage.width, curPage.height)
                            )
                            drawPaperGuidelines(curPage.width, curPage.height, viewport.scale)
                        }

                        // Page border
                        drawRect(
                            color = Color(0xFFE5E7EB),
                            topLeft = Offset.Zero,
                            size = Size(curPage.width, curPage.height),
                            style = Stroke(width = 1f)
                        )
                    }
                    CanvasLayoutMode.INFINITE -> {
                        // Draw infinite dot grid relative to canvas coordinates
                        drawInfiniteGrid(viewport)
                    }
                }

                // 2. Paper-Constrained Layer (Images, Strokes, Shapes)
                val allStrokes = if (state.activeStroke != null) {
                    curPage.strokes + state.activeStroke!!
                } else {
                    curPage.strokes
                }
                val allShapes = if (state.activeShape != null) {
                    curPage.shapes + state.activeShape!!
                } else {
                    curPage.shapes
                }

                val shouldClip = state.palmConfig.constrainDrawingToPage && layoutMode == CanvasLayoutMode.PAGED
                val drawCoreContent = {
                    // 2a. Images
                    drawImages(curPage.images, state.imageBitmaps)

                    // 2b. Strokes (Cached smooth paths for finished strokes, dynamic for active stroke)
                    for (stroke in curPage.strokes) {
                        drawVectorStroke(stroke, strokePathCache)
                    }
                    if (state.activeStroke != null) {
                        drawVectorStroke(state.activeStroke!!, null)
                    }

                    // 2c. Shapes
                    for (shape in allShapes) {
                        drawVectorShape(shape)
                    }
                }

                if (shouldClip) {
                    clipRect(0f, 0f, curPage.width, curPage.height) {
                        drawCoreContent()
                    }
                } else {
                    drawCoreContent()
                }

                // 3. Text Boxes
                drawTextBoxes(
                    textBoxes = curPage.textBoxes,
                    isTextToolActive = state.currentTool == DrawingTool.TEXT,
                    selectedTextBoxId = if (state.selectedItem?.type == SelectableType.TEXT_BOX) state.selectedItem?.id else null
                )

                // 4. Audio Notes
                drawAudioNotes(
                    audioNotes = curPage.audioNotes,
                    playingAudioPath = audioManager.currentlyPlayingPath,
                    isPlaying = audioManager.isPlaying
                )

                // 5. Selected Item Transform & Action Overlay
                val activeSel = state.selectedItem
                if (activeSel != null) {
                    drawSelectedItemOverlay(activeSel, curPage, viewport.scale)
                }

                // 6. Lasso Selection & Active Path Overlay
                if (state.activeLassoPath.size > 1) {
                    drawLassoPath(state.activeLassoPath, viewport.scale)
                }
                if (state.lassoSelection.isNotEmpty) {
                    drawLassoSelectionOverlay(state.lassoSelection.bounds, viewport.scale)
                }
            }
        }

        // 7. Floating Action Bar for active Lasso Selection
        LassoFloatingActionBar(state = state)
    }
}

private fun DrawScope.drawVectorStroke(
    stroke: StrokeModel,
    pathCache: MutableMap<String, androidx.compose.ui.graphics.Path>?
) {
    if (stroke.points.isEmpty()) return
    val path = if (pathCache != null) {
        pathCache.getOrPut(stroke.id) {
            InkPathGenerator.createSmoothPath(stroke.points)
        }
    } else {
        InkPathGenerator.createSmoothPath(stroke.points)
    }

    if (stroke.isHighlighter) {
        drawPath(
            path = path,
            color = Color(stroke.colorArgb).copy(alpha = stroke.alpha),
            style = Stroke(
                width = stroke.strokeWidth * 3.2f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            ),
            blendMode = BlendMode.Multiply
        )
    } else {
        drawPath(
            path = path,
            color = Color(stroke.colorArgb).copy(alpha = stroke.alpha),
            style = Stroke(
                width = stroke.strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            ),
            blendMode = BlendMode.SrcOver
        )
    }
}

private fun DrawScope.drawVectorShape(shape: ShapeModel) {
    val start = Offset(shape.startX, shape.startY)
    val end = Offset(shape.endX, shape.endY)
    val color = Color(shape.strokeColorArgb)
    val style = Stroke(width = shape.strokeWidth, cap = StrokeCap.Round)

    when (shape.type) {
        ShapeType.LINE -> {
            drawLine(color = color, start = start, end = end, strokeWidth = shape.strokeWidth, cap = StrokeCap.Round)
        }
        ShapeType.ARROW -> {
            drawLine(color = color, start = start, end = end, strokeWidth = shape.strokeWidth, cap = StrokeCap.Round)
            // Arrowhead calculations
            val dx = end.x - start.x
            val dy = end.y - start.y
            val len = hypot(dx, dy)
            if (len > 10f) {
                val headLength = (shape.strokeWidth * 4f).coerceIn(16f, 48f)
                val angle = Math.atan2(dy.toDouble(), dx.toDouble())
                val arrowAngle = Math.PI / 6.0

                val x1 = end.x - headLength * Math.cos(angle - arrowAngle).toFloat()
                val y1 = end.y - headLength * Math.sin(angle - arrowAngle).toFloat()
                val x2 = end.x - headLength * Math.cos(angle + arrowAngle).toFloat()
                val y2 = end.y - headLength * Math.sin(angle + arrowAngle).toFloat()

                val arrowHead = Path().apply {
                    moveTo(end.x, end.y)
                    lineTo(x1, y1)
                    moveTo(end.x, end.y)
                    lineTo(x2, y2)
                }
                drawPath(path = arrowHead, color = color, style = style)
            }
        }
        ShapeType.RECTANGLE -> {
            val left = minOf(start.x, end.x)
            val top = minOf(start.y, end.y)
            val right = maxOf(start.x, end.x)
            val bottom = maxOf(start.y, end.y)
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = style
            )
        }
        ShapeType.ELLIPSE -> {
            val left = minOf(start.x, end.x)
            val top = minOf(start.y, end.y)
            val right = maxOf(start.x, end.x)
            val bottom = maxOf(start.y, end.y)
            drawOval(
                color = color,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = style
            )
        }
        ShapeType.TRIANGLE -> {
            val triPath = Path().apply {
                if (shape.vertexX != null && shape.vertexY != null) {
                    moveTo(shape.startX, shape.startY)
                    lineTo(shape.vertexX, shape.vertexY)
                    lineTo(shape.endX, shape.endY)
                    close()
                } else {
                    val left = minOf(start.x, end.x)
                    val top = minOf(start.y, end.y)
                    val right = maxOf(start.x, end.x)
                    val bottom = maxOf(start.y, end.y)
                    moveTo((left + right) / 2f, top)
                    lineTo(right, bottom)
                    lineTo(left, bottom)
                    close()
                }
            }
            drawPath(path = triPath, color = color, style = style)
        }
        ShapeType.RIGHT_ANGLE -> {
            val vertex = if (shape.vertexX != null && shape.vertexY != null) {
                Offset(shape.vertexX, shape.vertexY)
            } else {
                Offset(start.x, end.y)
            }
            val anglePath = Path().apply {
                moveTo(start.x, start.y)
                lineTo(vertex.x, vertex.y)
                lineTo(end.x, end.y)
            }
            drawPath(path = anglePath, color = color, style = style)
        }
    }
}

private fun DrawScope.drawPaperGuidelines(pageWidth: Float, pageHeight: Float, scale: Float) {
    // If lines would be drawn closer than 4 screen pixels, skip drawing them to prevent overdraw and lag
    if (36f * scale < 4f) return

    val lineSpacing = 36f
    var y = 80f
    val lineColor = Color(0x18000000)
    while (y < pageHeight - 60f) {
        drawLine(
            color = lineColor,
            start = Offset(40f, y),
            end = Offset(pageWidth - 40f, y),
            strokeWidth = 1f
        )
        y += lineSpacing
    }
}

private fun DrawScope.drawInfiniteGrid(viewport: com.scribidoo.app.canvas.viewport.ViewportState) {
    var effectiveSpacing = 40f
    // Adaptive Level of Detail: maintain dot screen density between 20px and 45px
    while (effectiveSpacing * viewport.scale < 24f) {
        effectiveSpacing *= 2f
    }

    val visibleLeft = viewport.screenToCanvas(Offset.Zero).x
    val visibleTop = viewport.screenToCanvas(Offset.Zero).y
    val visibleRight = viewport.screenToCanvas(Offset(viewport.containerSize.width, 0f)).x
    val visibleBottom = viewport.screenToCanvas(Offset(0f, viewport.containerSize.height)).y

    val startX = (Math.floor((visibleLeft / effectiveSpacing).toDouble()) * effectiveSpacing).toFloat()
    val endX = (Math.ceil((visibleRight / effectiveSpacing).toDouble()) * effectiveSpacing).toFloat()
    val startY = (Math.floor((visibleTop / effectiveSpacing).toDouble()) * effectiveSpacing).toFloat()
    val endY = (Math.ceil((visibleBottom / effectiveSpacing).toDouble()) * effectiveSpacing).toFloat()

    val dotColor = Color(0x309CA3AF)
    val dotRadius = (1.5f / viewport.scale).coerceIn(1.2f, 4.0f)
    var x = startX
    while (x <= endX) {
        var y = startY
        while (y <= endY) {
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(x, y)
            )
            y += effectiveSpacing
        }
        x += effectiveSpacing
    }
}

fun getTextBoxBounds(tb: TextBoxModel): Rect {
    val textPaint = android.graphics.Paint().apply {
        textSize = tb.fontSizeSp * 2.2f
        typeface = android.graphics.Typeface.DEFAULT
    }
    val textWidth = textPaint.measureText(tb.text).coerceAtLeast(60f)
    val textHeight = tb.fontSizeSp * 2.4f
    return Rect(
        left = tb.x - 12f,
        top = tb.y - textHeight - 6f,
        right = tb.x + textWidth + 12f,
        bottom = tb.y + 10f
    )
}

private fun DrawScope.drawTextBoxes(
    textBoxes: List<TextBoxModel>,
    isTextToolActive: Boolean,
    selectedTextBoxId: String?
) {
    for (tb in textBoxes) {
        val bounds = getTextBoxBounds(tb)
        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = tb.colorArgb
            textSize = tb.fontSizeSp * 2.2f
            typeface = android.graphics.Typeface.DEFAULT
        }

        val isSelected = tb.id == selectedTextBoxId

        if (isSelected) {
            // Selected Text Box: solid border, soft tint, corner handles, resize pill, and action buttons
            drawRoundRect(
                color = Color(0x222563EB),
                topLeft = Offset(bounds.left, bounds.top),
                size = Size(bounds.width, bounds.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = Color(0xFF2563EB),
                topLeft = Offset(bounds.left, bounds.top),
                size = Size(bounds.width, bounds.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                style = Stroke(width = 2.5f)
            )

            // 3 Corner Dots (top-left, top-right, bottom-left)
            val dotRadius = 4.5f
            listOf(
                Offset(bounds.left, bounds.top),
                Offset(bounds.right, bounds.top),
                Offset(bounds.left, bounds.bottom)
            ).forEach { pt ->
                drawCircle(color = Color.White, radius = dotRadius, center = pt)
                drawCircle(color = Color(0xFF2563EB), radius = dotRadius, center = pt, style = Stroke(width = 1.5f))
            }

            // Bottom-right RESIZE HANDLE
            val resizeCenter = Offset(bounds.right, bounds.bottom)
            val resizeRadius = 9f
            drawCircle(color = Color(0xFF2563EB), radius = resizeRadius, center = resizeCenter)
            drawCircle(color = Color.White, radius = resizeRadius, center = resizeCenter, style = Stroke(width = 2f))
            drawLine(
                color = Color.White,
                start = Offset(resizeCenter.x - 3.5f, resizeCenter.y - 3.5f),
                end = Offset(resizeCenter.x + 3.5f, resizeCenter.y + 3.5f),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(resizeCenter.x + 3.5f, resizeCenter.y + 0.5f),
                end = Offset(resizeCenter.x + 3.5f, resizeCenter.y + 3.5f),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(resizeCenter.x + 0.5f, resizeCenter.y + 3.5f),
                end = Offset(resizeCenter.x + 3.5f, resizeCenter.y + 3.5f),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )

            // Top action buttons: [ Edit ]  [ Delete ]
            val pillY = bounds.top - 28f
            drawRoundRect(
                color = Color(0xFF1E293B),
                topLeft = Offset(bounds.left, pillY),
                size = Size(50f, 22f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
            val editPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            drawContext.canvas.nativeCanvas.drawText("Edit", bounds.left + 14f, pillY + 15f, editPaint)

            drawRoundRect(
                color = Color(0xFFDC2626),
                topLeft = Offset(bounds.left + 54f, pillY),
                size = Size(52f, 22f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
            val delPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            drawContext.canvas.nativeCanvas.drawText("Delete", bounds.left + 64f, pillY + 15f, delPaint)

        } else if (isTextToolActive) {
            // Subtle dashed boundary when Text tool is active
            drawRoundRect(
                color = Color(0x12000000),
                topLeft = Offset(bounds.left, bounds.top),
                size = Size(bounds.width, bounds.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = Color(0x40000000),
                topLeft = Offset(bounds.left, bounds.top),
                size = Size(bounds.width, bounds.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                style = Stroke(
                    width = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
            )
        }

        drawContext.canvas.nativeCanvas.drawText(
            tb.text,
            tb.x,
            tb.y,
            textPaint
        )
    }
}

fun getImageBounds(img: ImageModel): Rect {
    return Rect(img.x, img.y, img.x + img.width, img.y + img.height)
}

fun rotatePoint(pt: Offset, center: Offset, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    val cos = kotlin.math.cos(rad).toFloat()
    val sin = kotlin.math.sin(rad).toFloat()
    val dx = pt.x - center.x
    val dy = pt.y - center.y
    return Offset(
        x = center.x + (dx * cos - dy * sin),
        y = center.y + (dx * sin + dy * cos)
    )
}

fun isPointInImage(img: ImageModel, pt: Offset): Boolean {
    val bounds = getImageBounds(img)
    if (img.rotationDegrees == 0f) return bounds.contains(pt)
    val center = Offset(img.x + img.width / 2f, img.y + img.height / 2f)
    val unrotated = rotatePoint(pt, center, -img.rotationDegrees)
    return bounds.contains(unrotated)
}

fun getShapeBounds(shape: ShapeModel): Rect {
    val pad = maxOf(shape.strokeWidth / 2f, 16f)
    val left = minOf(shape.startX, shape.endX) - pad
    val top = minOf(shape.startY, shape.endY) - pad
    val right = maxOf(shape.startX, shape.endX) + pad
    val bottom = maxOf(shape.startY, shape.endY) + pad
    return Rect(left, top, right, bottom)
}

fun getAudioNoteBounds(audio: AudioNoteModel): Rect {
    val halfW = audio.width / 2f
    val halfH = audio.height / 2f
    return Rect(audio.x - halfW, audio.y - halfH, audio.x + halfW, audio.y + halfH)
}

fun getSelectedItemBounds(item: SelectedItem, page: PageModel): Rect? {
    return when (item.type) {
        SelectableType.TEXT_BOX -> page.textBoxes.firstOrNull { it.id == item.id }?.let { getTextBoxBounds(it) }
        SelectableType.IMAGE -> page.images.firstOrNull { it.id == item.id }?.let { getImageBounds(it) }
        SelectableType.SHAPE -> page.shapes.firstOrNull { it.id == item.id }?.let { getShapeBounds(it) }
        SelectableType.AUDIO_NOTE -> page.audioNotes.firstOrNull { it.id == item.id }?.let { getAudioNoteBounds(it) }
    }
}

private fun DrawScope.drawImages(
    images: List<ImageModel>,
    bitmapCache: MutableMap<String, androidx.compose.ui.graphics.ImageBitmap>
) {
    for (img in images) {
        var bmp = bitmapCache[img.id]
        if (bmp == null) {
            val file = File(img.localPath)
            if (file.exists()) {
                val decoded = BitmapFactory.decodeFile(img.localPath)
                if (decoded != null) {
                    bmp = decoded.asImageBitmap()
                    bitmapCache[img.id] = bmp
                }
            }
        }
        if (bmp != null) {
            val dstOffset = IntOffset(img.x.toInt(), img.y.toInt())
            val dstSize = IntSize(img.width.toInt(), img.height.toInt())
            val alpha = img.opacity.coerceIn(0.05f, 1.0f)
            if (img.rotationDegrees != 0f) {
                withTransform({
                    rotate(
                        degrees = img.rotationDegrees,
                        pivot = Offset(img.x + img.width / 2f, img.y + img.height / 2f)
                    )
                }) {
                    drawImage(
                        image = bmp,
                        dstOffset = dstOffset,
                        dstSize = dstSize,
                        alpha = alpha
                    )
                }
            } else {
                drawImage(
                    image = bmp,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    alpha = alpha
                )
            }
        }
    }
}

private fun DrawScope.drawAudioNotes(
    audioNotes: List<AudioNoteModel>,
    playingAudioPath: String?,
    isPlaying: Boolean
) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        color = android.graphics.Color.WHITE
    }

    for (audio in audioNotes) {
        val bounds = getAudioNoteBounds(audio)
        val isCurrentPlaying = playingAudioPath == audio.localPath && isPlaying
        val pillBgColor = if (isCurrentPlaying) Color(0xFF2563EB) else Color(0xFF1E293B)
        val cornerRadiusPx = (bounds.height / 2f).coerceAtMost(24f)

        // Pill background with subtle shadow
        drawRoundRect(
            color = Color(0x33000000),
            topLeft = Offset(bounds.left + 2f, bounds.top + 3f),
            size = Size(bounds.width, bounds.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )
        drawRoundRect(
            color = pillBgColor,
            topLeft = Offset(bounds.left, bounds.top),
            size = Size(bounds.width, bounds.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )

        // Mic indicator dot
        val micRadius = (bounds.height * 0.14f).coerceIn(4f, 8f)
        val micX = bounds.left + micRadius * 3f
        drawCircle(
            color = if (isCurrentPlaying) Color(0xFF60A5FA) else Color(0xFFEF4444),
            radius = micRadius,
            center = Offset(micX, audio.y)
        )

        // Duration / timer text
        val durSec = (audio.durationMs / 1000L).coerceAtLeast(1L)
        val timeStr = String.format("%02d:%02d", durSec / 60, durSec % 60)
        val fontSize = (bounds.height * 0.40f).coerceIn(12f, 26f)
        paint.textSize = fontSize

        val textX = micX + micRadius * 2f + 4f
        val textY = audio.y + fontSize * 0.35f
        drawContext.canvas.nativeCanvas.drawText(
            timeStr,
            textX,
            textY,
            paint
        )

        // Play/Pause circular button area on right side of pill
        val playBtnRadius = (bounds.height * 0.32f).coerceIn(10f, 24f)
        val playCenter = Offset(bounds.right - playBtnRadius - 8f, audio.y)
        drawCircle(
            color = Color.White.copy(alpha = 0.25f),
            radius = playBtnRadius,
            center = playCenter
        )

        val iconScale = (playBtnRadius / 12f).coerceIn(0.8f, 2.0f)
        if (isCurrentPlaying) {
            drawLine(
                color = Color.White,
                start = Offset(playCenter.x - 3f * iconScale, playCenter.y - 5f * iconScale),
                end = Offset(playCenter.x - 3f * iconScale, playCenter.y + 5f * iconScale),
                strokeWidth = 2.5f * iconScale
            )
            drawLine(
                color = Color.White,
                start = Offset(playCenter.x + 3f * iconScale, playCenter.y - 5f * iconScale),
                end = Offset(playCenter.x + 3f * iconScale, playCenter.y + 5f * iconScale),
                strokeWidth = 2.5f * iconScale
            )
        } else {
            val playPath = Path().apply {
                moveTo(playCenter.x - 3.5f * iconScale, playCenter.y - 5.5f * iconScale)
                lineTo(playCenter.x + 5f * iconScale, playCenter.y)
                lineTo(playCenter.x - 3.5f * iconScale, playCenter.y + 5.5f * iconScale)
                close()
            }
            drawPath(playPath, color = Color.White)
        }
    }
}

data class FloatingToolbarBounds(
    val actionBtnRect: Rect?,
    val rot90BtnRect: Rect?,
    val opacityBtnRect: Rect?,
    val deleteBtnRect: Rect
)

fun calculateItemFloatingToolbarBounds(
    item: SelectedItem,
    page: PageModel,
    viewportScale: Float
): FloatingToolbarBounds? {
    val bounds = getSelectedItemBounds(item, page) ?: return null
    val invScale = 1f / viewportScale.coerceIn(0.15f, 4f)
    val btnH = (32f * invScale).coerceIn(26f, 68f)
    val gap = (6f * invScale).coerceIn(4f, 14f)
    val marginAbove = (14f * invScale).coerceIn(10f, 32f)

    val (centerX, topY) = if (item.type == SelectableType.IMAGE) {
        val img = page.images.firstOrNull { it.id == item.id }
        val rot = img?.rotationDegrees ?: 0f
        val cx = bounds.left + bounds.width / 2f
        val cy = bounds.top + bounds.height / 2f
        if (rot == 0f) {
            Pair(cx, bounds.top - 28f * invScale)
        } else {
            val rad = Math.toRadians(rot.toDouble())
            val cos = kotlin.math.cos(rad)
            val sin = kotlin.math.sin(rad)
            val hw = bounds.width / 2.0
            val hh = bounds.height / 2.0

            val y1 = cy + (-hw * sin - hh * cos).toFloat()
            val y2 = cy + (hw * sin - hh * cos).toFloat()
            val y3 = cy + (hw * sin + hh * cos).toFloat()
            val y4 = cy + (-hw * sin + hh * cos).toFloat()

            val rotHandleDy = -(hh + 28.0 * invScale)
            val rotHandleY = cy + (rotHandleDy * cos).toFloat()

            val minY = minOf(y1, y2, y3, y4, rotHandleY)
            Pair(cx, minY)
        }
    } else {
        Pair(bounds.left + bounds.width / 2f, bounds.top)
    }

    val toolbarTop = topY - marginAbove - btnH

    val actionW = if (item.type != SelectableType.SHAPE) (88f * invScale).coerceIn(72f, 160f) else 0f
    val rot90W = if (item.type == SelectableType.IMAGE) (58f * invScale).coerceIn(50f, 110f) else 0f
    val opacityW = if (item.type == SelectableType.IMAGE) (86f * invScale).coerceIn(68f, 150f) else 0f
    val deleteW = (54f * invScale).coerceIn(46f, 100f)

    val totalW = (if (actionW > 0f) actionW + gap else 0f) +
            (if (rot90W > 0f) rot90W + gap else 0f) +
            (if (opacityW > 0f) opacityW + gap else 0f) +
            deleteW

    var curX = centerX - totalW / 2f

    val actionRect = if (actionW > 0f) Rect(curX, toolbarTop, curX + actionW, toolbarTop + btnH).also { curX += actionW + gap } else null
    val rot90Rect = if (rot90W > 0f) Rect(curX, toolbarTop, curX + rot90W, toolbarTop + btnH).also { curX += rot90W + gap } else null
    val opacityRect = if (opacityW > 0f) Rect(curX, toolbarTop, curX + opacityW, toolbarTop + btnH).also { curX += opacityW + gap } else null
    val deleteRect = Rect(curX, toolbarTop, curX + deleteW, toolbarTop + btnH)

    return FloatingToolbarBounds(actionRect, rot90Rect, opacityRect, deleteRect)
}

private fun DrawScope.drawSelectedItemOverlay(
    item: SelectedItem,
    page: PageModel,
    viewportScale: Float
) {
    val bounds = getSelectedItemBounds(item, page) ?: return
    val img = if (item.type == SelectableType.IMAGE) page.images.firstOrNull { it.id == item.id } else null
    val rot = img?.rotationDegrees ?: 0f
    val center = Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f)
    val invScale = 1f / viewportScale.coerceIn(0.15f, 4f)

    // Part 1: Rotated Selection Frame & Handles (rotates with image)
    withTransform({
        if (rot != 0f) {
            rotate(degrees = rot, pivot = center)
        }
    }) {
        // Crisp outline stroke around the item (NO filled tint to preserve original photo visibility)
        drawRoundRect(
            color = Color(0xFF2563EB),
            topLeft = Offset(bounds.left, bounds.top),
            size = Size(bounds.width, bounds.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * invScale, 6f * invScale),
            style = Stroke(width = (2f * invScale).coerceIn(1.5f, 5f))
        )

        // Corner dots
        val dotRadius = (4.5f * invScale).coerceIn(4f, 12f)
        listOf(
            Offset(bounds.left, bounds.top),
            Offset(bounds.right, bounds.top),
            Offset(bounds.left, bounds.bottom)
        ).forEach { pt ->
            drawCircle(color = Color.White, radius = dotRadius, center = pt)
            drawCircle(color = Color(0xFF2563EB), radius = dotRadius, center = pt, style = Stroke(width = (1.5f * invScale).coerceIn(1f, 3.5f)))
        }

        // Bottom-right RESIZE HANDLE
        val resizeCenter = Offset(bounds.right, bounds.bottom)
        val resizeRadius = (10f * invScale).coerceIn(9f, 24f)
        drawCircle(color = Color(0xFF2563EB), radius = resizeRadius, center = resizeCenter)
        drawCircle(color = Color.White, radius = resizeRadius, center = resizeCenter, style = Stroke(width = (2f * invScale).coerceIn(1.5f, 4f)))
        drawLine(
            color = Color.White,
            start = Offset(resizeCenter.x - 4f * invScale, resizeCenter.y - 4f * invScale),
            end = Offset(resizeCenter.x + 4f * invScale, resizeCenter.y + 4f * invScale),
            strokeWidth = (2f * invScale).coerceIn(1.5f, 4f),
            cap = StrokeCap.Round
        )

        // Top ROTATE HANDLE for Images
        if (item.type == SelectableType.IMAGE) {
            val rotHandleCenter = Offset(center.x, bounds.top - 28f * invScale)
            val rotRadius = (9f * invScale).coerceIn(8f, 22f)
            drawLine(
                color = Color(0xFF2563EB),
                start = Offset(center.x, bounds.top),
                end = rotHandleCenter,
                strokeWidth = (2f * invScale).coerceIn(1.5f, 4f)
            )
            drawCircle(color = Color(0xFF2563EB), radius = rotRadius, center = rotHandleCenter)
            drawCircle(color = Color.White, radius = rotRadius, center = rotHandleCenter, style = Stroke(width = (2f * invScale).coerceIn(1.5f, 4f)))
        }
    }

    // Part 2: Upright Floating Toolbar (OUTSIDE rotation transform so it NEVER rotates with the image)
    val toolbar = calculateItemFloatingToolbarBounds(item, page, viewportScale) ?: return
    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * invScale, 6f * invScale)
    val textPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textSize = (11f * invScale).coerceIn(10f, 24f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    // A. Action Button
    if (toolbar.actionBtnRect != null) {
        val actionText = when (item.type) {
            SelectableType.TEXT_BOX -> "Edit"
            SelectableType.IMAGE -> if (img?.keepAspectRatio == true) "Aspect: Lock" else "Aspect: Free"
            SelectableType.AUDIO_NOTE -> "Options"
            SelectableType.SHAPE -> ""
        }
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(toolbar.actionBtnRect.left, toolbar.actionBtnRect.top),
            size = Size(toolbar.actionBtnRect.width, toolbar.actionBtnRect.height),
            cornerRadius = cornerRadius
        )
        val textY = toolbar.actionBtnRect.top + toolbar.actionBtnRect.height * 0.68f
        drawContext.canvas.nativeCanvas.drawText(
            actionText,
            toolbar.actionBtnRect.left + 8f * invScale,
            textY,
            textPaint
        )
    }

    // B. Rotate 90 Button
    if (toolbar.rot90BtnRect != null) {
        drawRoundRect(
            color = Color(0xFF334155),
            topLeft = Offset(toolbar.rot90BtnRect.left, toolbar.rot90BtnRect.top),
            size = Size(toolbar.rot90BtnRect.width, toolbar.rot90BtnRect.height),
            cornerRadius = cornerRadius
        )
        val textY = toolbar.rot90BtnRect.top + toolbar.rot90BtnRect.height * 0.68f
        drawContext.canvas.nativeCanvas.drawText(
            "⟳ 90°",
            toolbar.rot90BtnRect.left + 8f * invScale,
            textY,
            textPaint
        )
    }

    // C. Opacity Button
    if (toolbar.opacityBtnRect != null && img != null) {
        val opacityPct = (img.opacity * 100f).toInt()
        drawRoundRect(
            color = Color(0xFF475569),
            topLeft = Offset(toolbar.opacityBtnRect.left, toolbar.opacityBtnRect.top),
            size = Size(toolbar.opacityBtnRect.width, toolbar.opacityBtnRect.height),
            cornerRadius = cornerRadius
        )
        val textY = toolbar.opacityBtnRect.top + toolbar.opacityBtnRect.height * 0.68f
        drawContext.canvas.nativeCanvas.drawText(
            "Opacity $opacityPct%",
            toolbar.opacityBtnRect.left + 6f * invScale,
            textY,
            textPaint
        )
    }

    // D. Delete Button
    drawRoundRect(
        color = Color(0xFFDC2626),
        topLeft = Offset(toolbar.deleteBtnRect.left, toolbar.deleteBtnRect.top),
        size = Size(toolbar.deleteBtnRect.width, toolbar.deleteBtnRect.height),
        cornerRadius = cornerRadius
    )
    val delTextY = toolbar.deleteBtnRect.top + toolbar.deleteBtnRect.height * 0.68f
    drawContext.canvas.nativeCanvas.drawText(
        "Delete",
        toolbar.deleteBtnRect.left + 8f * invScale,
        delTextY,
        textPaint
    )
}
