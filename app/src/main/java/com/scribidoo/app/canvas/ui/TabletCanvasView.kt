package com.scribidoo.app.canvas.ui

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.scribidoo.app.canvas.ink.InkPathGenerator
import com.scribidoo.app.canvas.input.ClassifiedTool
import com.scribidoo.app.canvas.input.InputClassifier
import com.scribidoo.app.canvas.input.InputVerdict
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.model.ShapeModel
import com.scribidoo.app.canvas.model.TextBoxModel
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.model.StrokeModel
import com.scribidoo.app.canvas.state.CanvasState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    // Text box transformation & selection states
    var isMovingTextBox by remember { mutableStateOf(false) }
    var isResizingTextBox by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var initialTouchCanvas by remember { mutableStateOf(Offset.Zero) }
    var lastTouchCanvas by remember { mutableStateOf(Offset.Zero) }
    var initialFontSize by remember { mutableFloatStateOf(16f) }
    var initialBoxWidth by remember { mutableFloatStateOf(100f) }
    var pendingTouchTextBoxId by remember { mutableStateOf<String?>(null) }

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

                val pCount = motionEvent.pointerCount
                val p0Major = if (pCount > 0) motionEvent.getTouchMajor(0) else 0f
                val p0Size = if (pCount > 0) motionEvent.getSize(0) else 0f
                val p1Major = if (pCount > 1) motionEvent.getTouchMajor(1) else 0f
                val p1Size = if (pCount > 1) motionEvent.getSize(1) else 0f
                android.util.Log.d("ScribidooTouch", "act=${motionEvent.actionMasked} cnt=$pCount p0(m=$p0Major,s=$p0Size) p1(m=$p1Major,s=$p1Size)")

                when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isViewportGestureActive = false
                        viewportPointer0Id = null
                        viewportPointer1Id = null
                        activeDrawingPointerId = null
                        previousFocalPoint = null
                        previousSpan = 0f

                        // Palm check on initial touch down:
                        if (isPalm(0)) {
                            // First contact is palm -> ignore completely
                            return@pointerInteropFilter true
                        }

                        val screenPoint = Offset(motionEvent.x, motionEvent.y)
                        val canvasPoint = state.viewportState.screenToCanvas(screenPoint)

                        // 1. Check if tapping an ALREADY SELECTED text box (resize handle, pills, or inside to move)
                        val selectedBox = state.currentPage.textBoxes.firstOrNull { it.id == state.selectedTextBoxId }
                        if (selectedBox != null) {
                            val bounds = getTextBoxBounds(selectedBox)
                            val resizeCenter = Offset(bounds.right, bounds.bottom)
                            val handleTouchDist = state.viewportState.screenToCanvasDist(36f)

                            val dResize = canvasPoint - resizeCenter
                            if (hypot(dResize.x, dResize.y) <= handleTouchDist) {
                                isResizingTextBox = true
                                isMovingTextBox = false
                                lastTouchCanvas = canvasPoint
                                initialTouchCanvas = canvasPoint
                                initialFontSize = selectedBox.fontSizeSp
                                initialBoxWidth = bounds.width.coerceAtLeast(30f)
                                return@pointerInteropFilter true
                            }

                            val pillY = bounds.top - 28f
                            val editRect = Rect(bounds.left, pillY - 4f, bounds.left + 50f, pillY + 24f)
                            if (editRect.contains(canvasPoint)) {
                                state.activeTextBoxForEdit = selectedBox
                                state.selectedTextBoxId = null
                                return@pointerInteropFilter true
                            }

                            val delRect = Rect(bounds.left + 54f, pillY - 4f, bounds.left + 106f, pillY + 24f)
                            if (delRect.contains(canvasPoint)) {
                                state.deleteTextBox(selectedBox.id)
                                state.selectedTextBoxId = null
                                return@pointerInteropFilter true
                            }

                            if (bounds.contains(canvasPoint)) {
                                isMovingTextBox = true
                                isResizingTextBox = false
                                lastTouchCanvas = canvasPoint
                                initialTouchCanvas = canvasPoint
                                return@pointerInteropFilter true
                            }

                            // Tapped outside the selected box -> deselect
                            state.selectedTextBoxId = null
                        }

                        // 2. Check if pressing down on ANY text box to initiate LONG-PRESS move & resize
                        val hitBox = state.currentPage.textBoxes.lastOrNull { tb ->
                            getTextBoxBounds(tb).contains(canvasPoint)
                        }
                        if (hitBox != null) {
                            pendingTouchTextBoxId = hitBox.id
                            initialTouchCanvas = canvasPoint
                            lastTouchCanvas = canvasPoint
                            longPressJob?.cancel()
                            longPressJob = coroutineScope.launch {
                                delay(380)
                                state.selectedTextBoxId = hitBox.id
                                isMovingTextBox = true
                                if (state.activeStroke != null) {
                                    state.cancelStroke()
                                }
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            }
                        } else {
                            pendingTouchTextBoxId = null
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
                                    if (hitBox == null) {
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
                            // 2 genuine fingertips on screen -> Enter 2-Finger Viewport Pan & Pinch-Zoom!
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
                            // Palm resting on screen alongside drawing finger/pen!
                            // Do NOT enter viewport mode; do NOT interrupt drawing.
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
                        // Cancel pending long press if user moved finger before long-press timer elapsed
                        if (pendingTouchTextBoxId != null && !isMovingTextBox && !isResizingTextBox) {
                            val screenPoint = Offset(motionEvent.x, motionEvent.y)
                            val canvasPoint = state.viewportState.screenToCanvas(screenPoint)
                            val dTouch = canvasPoint - initialTouchCanvas
                            val dist = hypot(dTouch.x, dTouch.y)
                            if (dist > state.viewportState.screenToCanvasDist(12f)) {
                                longPressJob?.cancel()
                                longPressJob = null
                                pendingTouchTextBoxId = null
                            }
                        }

                        // Handle active text box moving
                        if (isMovingTextBox && state.selectedTextBoxId != null) {
                            val screenPoint = Offset(motionEvent.x, motionEvent.y)
                            val canvasPoint = state.viewportState.screenToCanvas(screenPoint)
                            val delta = canvasPoint - lastTouchCanvas
                            lastTouchCanvas = canvasPoint
                            state.moveTextBox(state.selectedTextBoxId!!, delta)
                            return@pointerInteropFilter true
                        }

                        // Handle active text box resizing
                        if (isResizingTextBox && state.selectedTextBoxId != null) {
                            val screenPoint = Offset(motionEvent.x, motionEvent.y)
                            val canvasPoint = state.viewportState.screenToCanvas(screenPoint)
                            val selected = state.currentPage.textBoxes.firstOrNull { it.id == state.selectedTextBoxId }
                            if (selected != null) {
                                val bounds = getTextBoxBounds(selected)
                                val currentWidth = (canvasPoint.x - bounds.left).coerceAtLeast(30f)
                                val scale = currentWidth / initialBoxWidth
                                state.resizeTextBox(selected.id, initialFontSize * scale)
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
                                    val screenPoint = Offset(motionEvent.getX(fingerIdx), motionEvent.getY(fingerIdx))
                                    val canvasPoint = state.viewportState.screenToCanvas(screenPoint)

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

                        if (isMovingTextBox || isResizingTextBox) {
                            isMovingTextBox = false
                            isResizingTextBox = false
                            state.commitTransform()
                            return@pointerInteropFilter true
                        }

                        // If released quickly on a text box without long-press:
                        if (pendingTouchTextBoxId != null) {
                            val tappedId = pendingTouchTextBoxId
                            pendingTouchTextBoxId = null
                            if (state.currentTool == DrawingTool.TEXT) {
                                val tappedBox = state.currentPage.textBoxes.firstOrNull { it.id == tappedId }
                                if (tappedBox != null) {
                                    state.activeTextBoxForEdit = tappedBox
                                }
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
                        isMovingTextBox = false
                        isResizingTextBox = false
                        pendingTouchTextBoxId = null
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

                // 2. Vector Stroke Layer (Highlighters first with BlendMode.Multiply, then Pens)
                val allStrokes = if (state.activeStroke != null) {
                    curPage.strokes + state.activeStroke!!
                } else {
                    curPage.strokes
                }

                // Group strokes to draw highlighters with Multiply blend
                for (stroke in allStrokes) {
                    drawVectorStroke(stroke)
                }

                // 3. Shape Layer
                val allShapes = if (state.activeShape != null) {
                    curPage.shapes + state.activeShape!!
                } else {
                    curPage.shapes
                }
                for (shape in allShapes) {
                    drawVectorShape(shape)
                }

                // 4. Text Boxes
                drawTextBoxes(
                    textBoxes = curPage.textBoxes,
                    isTextToolActive = state.currentTool == DrawingTool.TEXT,
                    selectedTextBoxId = state.activeTextBoxForEdit?.id
                )
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
