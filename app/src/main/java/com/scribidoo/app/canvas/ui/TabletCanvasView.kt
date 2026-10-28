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
    var isMarginPanning by remember { mutableStateOf(false) }

    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var pendingTouchItem by remember { mutableStateOf<SelectedItem?>(null) }
    var initialTouchCanvas by remember { mutableStateOf(Offset.Zero) }
    var lastTouchCanvas by remember { mutableStateOf(Offset.Zero) }
    var initialItemWidth by remember { mutableFloatStateOf(100f) }
    var initialItemHeight by remember { mutableFloatStateOf(100f) }
    var initialFontSize by remember { mutableFloatStateOf(16f) }

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
                                val resizeCenter = Offset(bounds.right, bounds.bottom)
                                val handleTouchDist = state.viewportState.screenToCanvasDist(36f)
                                val dResize = canvasPoint - resizeCenter

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

                                val pillY = bounds.top - 28f
                                val hasActionPill = curSel.type != SelectableType.SHAPE
                                val delLeft = if (hasActionPill) bounds.left + 86f else bounds.left

                                // B. Delete button
                                val delRect = Rect(delLeft, pillY - 4f, delLeft + 54f, pillY + 24f)
                                if (delRect.contains(canvasPoint)) {
                                    state.deleteSelectedItem()
                                    return@pointerInteropFilter true
                                }

                                // C. Action button (Edit Text / Aspect Ratio / Audio Options)
                                if (hasActionPill) {
                                    val actionRect = Rect(bounds.left, pillY - 4f, bounds.left + 80f, pillY + 24f)
                                    if (actionRect.contains(canvasPoint)) {
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
                                }

                                // D. Inside bounds -> Move
                                if (bounds.contains(canvasPoint)) {
                                    isMovingItem = true
                                    lastTouchCanvas = canvasPoint
                                    initialTouchCanvas = canvasPoint
                                    return@pointerInteropFilter true
                                }

                                // Tapped outside the selected item -> deselect
                                state.selectedItem = null
                            }
                        }

                        // 3. Check if tapping an Audio Note PLAY button directly
                        val hitAudioPlay = state.currentPage.audioNotes.lastOrNull { audio ->
                            val playCenter = Offset(audio.x + 44f, audio.y)
                            val touchDist = state.viewportState.screenToCanvasDist(26f)
                            hypot(canvasPoint.x - playCenter.x, canvasPoint.y - playCenter.y) <= touchDist
                        }
                        if (hitAudioPlay != null) {
                            audioManager.play(hitAudioPlay.localPath, hitAudioPlay.trimStartMs, hitAudioPlay.trimEndMs)
                            return@pointerInteropFilter true
                        }

                        // 4. Hit Testing for LONG-PRESS (Instant Lift & Drag)
                        val hitAudio = state.currentPage.audioNotes.lastOrNull { getAudioNoteBounds(it).contains(canvasPoint) }
                        val hitText = if (hitAudio == null) state.currentPage.textBoxes.lastOrNull { getTextBoxBounds(it).contains(canvasPoint) } else null
                        val hitImage = if (hitAudio == null && hitText == null) state.currentPage.images.lastOrNull { getImageBounds(it).contains(canvasPoint) } else null
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

                        // Cancel pending long press if finger moved before 350ms elapsed
                        if (pendingTouchItem != null && !isLiftingAndDragging && !isMovingItem && !isResizingItem) {
                            val dTouch = canvasPoint - initialTouchCanvas
                            val dist = hypot(dTouch.x, dTouch.y)
                            if (dist > state.viewportState.screenToCanvasDist(12f)) {
                                longPressJob?.cancel()
                                longPressJob = null
                                pendingTouchItem = null
                            }
                        }

                        // Handle moving item (Instant Lift & Drag or selected drag)
                        if ((isMovingItem || isLiftingAndDragging) && state.selectedItem != null) {
                            val delta = canvasPoint - lastTouchCanvas
                            lastTouchCanvas = canvasPoint
                            state.moveSelectedItem(delta)
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
                                SelectableType.AUDIO_NOTE -> {}
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
                                                state.appendStrokePoint(canvasPoint, pressure)
                                            }
                                            DrawingTool.STROKE_ERASER,
                                            DrawingTool.SEGMENT_ERASER -> {
                                                val eraserRadius = state.viewportState.screenToCanvasDist(32f)
                                                state.eraseAt(canvasPoint, eraserRadius)
                                            }
                                            DrawingTool.SHAPE -> {
                                                state.updateShape(canvasPoint)
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

                        if (liftedId == activeDrawingPointerId) {
                            when (state.currentTool) {
                                DrawingTool.PEN,
                                DrawingTool.HIGHLIGHTER -> state.finishStroke()
                                DrawingTool.STROKE_ERASER,
                                DrawingTool.SEGMENT_ERASER -> state.finishErasing()
                                DrawingTool.SHAPE -> state.finishShape()
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
                            return@pointerInteropFilter true
                        }

                        if (isLiftingAndDragging || isMovingItem || isResizingItem) {
                            isLiftingAndDragging = false
                            isMovingItem = false
                            isResizingItem = false
                            state.commitTransform()
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

                        if (activeDrawingPointerId != null) {
                            when (state.currentTool) {
                                DrawingTool.PEN,
                                DrawingTool.HIGHLIGHTER -> state.finishStroke()
                                DrawingTool.STROKE_ERASER,
                                DrawingTool.SEGMENT_ERASER -> state.finishErasing()
                                DrawingTool.SHAPE -> state.finishShape()
                                else -> {}
                            }
                            activeDrawingPointerId = null
                        }
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        longPressJob?.cancel()
                        longPressJob = null
                        isLiftingAndDragging = false
                        isMovingItem = false
                        isResizingItem = false
                        isMarginPanning = false
                        pendingTouchItem = null
                        isViewportGestureActive = false
                        previousFocalPoint = null
                        previousSpan = 0f
                        viewportPointer0Id = null
                        viewportPointer1Id = null
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
                            drawPaperGuidelines(curPage.width, curPage.height)
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

                    // 2b. Strokes (Highlighters first with Multiply blend, then Pens)
                    for (stroke in allStrokes) {
                        drawVectorStroke(stroke)
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
                    drawSelectedItemOverlay(activeSel, curPage)
                }
            }
        }
    }
}

private fun DrawScope.drawVectorStroke(stroke: StrokeModel) {
    if (stroke.points.isEmpty()) return
    val path = InkPathGenerator.createSmoothPath(stroke.points)

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
    }
}

private fun DrawScope.drawPaperGuidelines(pageWidth: Float, pageHeight: Float) {
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
    val gridSpacing = 40f
    val visibleLeft = viewport.screenToCanvas(Offset.Zero).x
    val visibleTop = viewport.screenToCanvas(Offset.Zero).y
    val visibleRight = viewport.screenToCanvas(Offset(viewport.containerSize.width, 0f)).x
    val visibleBottom = viewport.screenToCanvas(Offset(0f, viewport.containerSize.height)).y

    val startX = (Math.floor((visibleLeft / gridSpacing).toDouble()) * gridSpacing).toFloat()
    val endX = (Math.ceil((visibleRight / gridSpacing).toDouble()) * gridSpacing).toFloat()
    val startY = (Math.floor((visibleTop / gridSpacing).toDouble()) * gridSpacing).toFloat()
    val endY = (Math.ceil((visibleBottom / gridSpacing).toDouble()) * gridSpacing).toFloat()

    val dotColor = Color(0x309CA3AF)
    var x = startX
    while (x <= endX) {
        var y = startY
        while (y <= endY) {
            drawCircle(
                color = dotColor,
                radius = 1.5f,
                center = Offset(x, y)
            )
            y += gridSpacing
        }
        x += gridSpacing
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

fun getShapeBounds(shape: ShapeModel): Rect {
    val pad = maxOf(shape.strokeWidth / 2f, 16f)
    val left = minOf(shape.startX, shape.endX) - pad
    val top = minOf(shape.startY, shape.endY) - pad
    val right = maxOf(shape.startX, shape.endX) + pad
    val bottom = maxOf(shape.startY, shape.endY) + pad
    return Rect(left, top, right, bottom)
}

fun getAudioNoteBounds(audio: AudioNoteModel): Rect {
    return Rect(audio.x - 64f, audio.y - 18f, audio.x + 64f, audio.y + 18f)
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
            drawImage(
                image = bmp,
                dstOffset = IntOffset(img.x.toInt(), img.y.toInt()),
                dstSize = IntSize(img.width.toInt(), img.height.toInt())
            )
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
        textSize = 24f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        color = android.graphics.Color.WHITE
    }

    for (audio in audioNotes) {
        val bounds = getAudioNoteBounds(audio)
        val isCurrentPlaying = playingAudioPath == audio.localPath && isPlaying
        val pillBgColor = if (isCurrentPlaying) Color(0xFF2563EB) else Color(0xFF1E293B)

        // Pill background with subtle shadow
        drawRoundRect(
            color = Color(0x33000000),
            topLeft = Offset(bounds.left + 2f, bounds.top + 3f),
            size = Size(bounds.width, bounds.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
        )
        drawRoundRect(
            color = pillBgColor,
            topLeft = Offset(bounds.left, bounds.top),
            size = Size(bounds.width, bounds.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
        )

        // Mic indicator dot (red dot if recording/audio, blue if currently playing)
        drawCircle(
            color = if (isCurrentPlaying) Color(0xFF60A5FA) else Color(0xFFEF4444),
            radius = 5f,
            center = Offset(bounds.left + 16f, audio.y)
        )

        // Duration / timer text
        val durSec = (audio.durationMs / 1000L).coerceAtLeast(1L)
        val timeStr = String.format("%02d:%02d", durSec / 60, durSec % 60)
        drawContext.canvas.nativeCanvas.drawText(
            timeStr,
            bounds.left + 26f,
            audio.y + 8f,
            paint
        )

        // Play/Pause circular button area on right side of pill
        val playCenter = Offset(bounds.right - 18f, audio.y)
        drawCircle(
            color = Color.White.copy(alpha = 0.25f),
            radius = 12f,
            center = playCenter
        )

        if (isCurrentPlaying) {
            // Pause bars
            drawLine(
                color = Color.White,
                start = Offset(playCenter.x - 3f, playCenter.y - 5f),
                end = Offset(playCenter.x - 3f, playCenter.y + 5f),
                strokeWidth = 2.5f
            )
            drawLine(
                color = Color.White,
                start = Offset(playCenter.x + 3f, playCenter.y - 5f),
                end = Offset(playCenter.x + 3f, playCenter.y + 5f),
                strokeWidth = 2.5f
            )
        } else {
            // Play triangle
            val playPath = Path().apply {
                moveTo(playCenter.x - 3.5f, playCenter.y - 5.5f)
                lineTo(playCenter.x + 5f, playCenter.y)
                lineTo(playCenter.x - 3.5f, playCenter.y + 5.5f)
                close()
            }
            drawPath(playPath, color = Color.White)
        }
    }
}

private fun DrawScope.drawSelectedItemOverlay(
    item: SelectedItem,
    page: PageModel
) {
    val bounds = getSelectedItemBounds(item, page) ?: return

    // Soft border around the selected item
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
        style = Stroke(width = 2f)
    )

    // Corner dots (top-left, top-right, bottom-left)
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
    val resizeRadius = 10f
    drawCircle(color = Color(0xFF2563EB), radius = resizeRadius, center = resizeCenter)
    drawCircle(color = Color.White, radius = resizeRadius, center = resizeCenter, style = Stroke(width = 2f))
    drawLine(
        color = Color.White,
        start = Offset(resizeCenter.x - 4f, resizeCenter.y - 4f),
        end = Offset(resizeCenter.x + 4f, resizeCenter.y + 4f),
        strokeWidth = 2f,
        cap = StrokeCap.Round
    )

    // Top Action buttons: [ Action ]  [ Delete ]
    val pillY = bounds.top - 28f
    val actionText = when (item.type) {
        SelectableType.TEXT_BOX -> "Edit"
        SelectableType.IMAGE -> {
            val img = page.images.firstOrNull { it.id == item.id }
            if (img?.keepAspectRatio == true) "Aspect: Lock" else "Aspect: Free"
        }
        SelectableType.AUDIO_NOTE -> "Options"
        SelectableType.SHAPE -> null
    }

    if (actionText != null) {
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(bounds.left, pillY),
            size = Size(80f, 22f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
        )
        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawContext.canvas.nativeCanvas.drawText(actionText, bounds.left + 8f, pillY + 15f, textPaint)
    }

    val delLeft = if (actionText != null) bounds.left + 86f else bounds.left
    drawRoundRect(
        color = Color(0xFFDC2626),
        topLeft = Offset(delLeft, pillY),
        size = Size(52f, 22f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
    )
    val delPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textSize = 11f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    drawContext.canvas.nativeCanvas.drawText("Delete", delLeft + 10f, pillY + 15f, delPaint)
}
