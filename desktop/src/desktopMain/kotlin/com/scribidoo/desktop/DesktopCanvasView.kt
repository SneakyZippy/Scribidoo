package com.scribidoo.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.scribidoo.app.canvas.ink.EraserEngine
import com.scribidoo.app.canvas.ink.InkPathGenerator
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.model.ShapeModel
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.model.StrokeModel
import com.scribidoo.app.canvas.model.TextBoxModel
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.app.canvas.viewport.ViewportState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

@Composable
fun DesktopCanvasView(
    state: CanvasState,
    modifier: Modifier = Modifier,
    isPalmShieldVisible: Boolean = false,
    palmShieldHeightPx: Float = 260f,
    onPalmShieldHeightChange: (Float) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    // Text box transformation & selection states
    var isMovingTextBox by remember { mutableStateOf(false) }
    var isResizingTextBox by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var initialTouchCanvas by remember { mutableStateOf(Offset.Zero) }
    var lastTouchCanvas by remember { mutableStateOf(Offset.Zero) }
    var initialFontSize by remember { mutableFloatStateOf(16f) }
    var initialBoxWidth by remember { mutableFloatStateOf(100f) }
    var pendingTouchTextBoxId by remember { mutableStateOf<String?>(null) }

    // Multi-touch tracking for Windows touch screens (2-finger pan & zoom)
    val activePointers = remember { mutableMapOf<androidx.compose.ui.input.pointer.PointerId, Offset>() }
    var previousFocalPoint by remember { mutableStateOf<Offset?>(null) }
    var previousSpan by remember { mutableFloatStateOf(0f) }

    // Navigation & Pan drag tracking (Hand tool, mouse right/middle-click drag, or 1-finger drag in Stylus-only mode)
    var isPanDragging by remember { mutableStateOf(false) }
    var lastPanScreenPoint by remember { mutableStateOf(Offset.Zero) }
    var isStylusErasing by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFFE5E7EB))
            .onSizeChanged { size ->
                state.viewportState.containerSize = size.toSize()
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        val pointerCount = changes.size

                        when (event.type) {
                            PointerEventType.Scroll -> {
                                // Mouse wheel zoom towards cursor on Windows PC
                                val change = changes.first()
                                val scrollY = change.scrollDelta.y
                                val focalPoint = change.position
                                val zoomFactor = if (scrollY < 0) 1.15f else 0.87f
                                state.viewportState.zoomBy(zoomFactor, focalPoint)
                                change.consume()
                            }

                            PointerEventType.Press -> {
                                for (c in changes) {
                                    if (c.pressed) activePointers[c.id] = c.position
                                }

                                if (activePointers.size >= 2) {
                                    // 2-Finger Touch gesture on Windows Touch Screen
                                    val pList = activePointers.values.toList()
                                    val p0 = pList[0]
                                    val p1 = pList[1]
                                    previousFocalPoint = (p0 + p1) / 2f
                                    previousSpan = hypot(p1.x - p0.x, p1.y - p0.y).coerceAtLeast(1f)
                                    if (state.activeStroke != null) state.cancelStroke()
                                    if (state.activeShape != null) state.activeShape = null
                                    isPanDragging = false
                                    isStylusErasing = false
                                    longPressJob?.cancel()
                                    longPressJob = null
                                    changes.forEach { it.consume() }
                                } else {
                                    val change = changes.first()
                                    val screenPoint = change.position
                                    val canvasPoint = state.viewportState.screenToCanvas(screenPoint)

                                    // Palm Rest Shield check
                                    val containerH = state.viewportState.containerSize.height
                                    val isInsidePalmShield = isPalmShieldVisible && containerH > 0 && screenPoint.y >= (containerH - palmShieldHeightPx)
                                    if (isInsidePalmShield) {
                                        if (state.activeStroke != null) state.cancelStroke()
                                        if (state.activeShape != null) state.activeShape = null
                                        change.consume()
                                        continue
                                    }

                                    // 1. Navigation / Pan triggers:
                                    // - Hand / Pan tool selected
                                    val isHandTool = state.currentTool == DrawingTool.HAND
                                    // - Right-click or middle-click drag with mouse
                                    val isMousePan = change.type == PointerType.Mouse && (event.buttons.isSecondaryPressed || event.buttons.isTertiaryPressed)
                                    // - Touch pan when finger drawing is disabled (Stylus Only / Palm rejection mode)
                                    val isTouchPan = change.type == PointerType.Touch && !state.palmConfig.isFingerDrawingEnabled

                                    if (isHandTool || isMousePan || isTouchPan) {
                                        isPanDragging = true
                                        lastPanScreenPoint = screenPoint
                                        if (state.activeStroke != null) state.cancelStroke()
                                        if (state.activeShape != null) state.activeShape = null
                                        change.consume()
                                        continue
                                    }
                                    isPanDragging = false

                                    // 2. Active stylus eraser tail support
                                    if (change.type == PointerType.Eraser) {
                                        isStylusErasing = true
                                        val eraserRadius = state.viewportState.screenToCanvasDist(36f)
                                        state.eraseAt(canvasPoint, eraserRadius)
                                        change.consume()
                                        continue
                                    }
                                    isStylusErasing = false

                                    val isStylus = change.type == PointerType.Stylus
                                    val rawPressure = change.pressure
                                    val pressure = if (isStylus && rawPressure > 0f) rawPressure.coerceIn(0.1f, 1.0f) else 0.5f

                                    // 3. Check if tapping an ALREADY SELECTED text box (resize handle, pills, or inside)
                                    val selectedBox = state.currentPage.textBoxes.firstOrNull { it.id == state.selectedTextBoxId }
                                    if (selectedBox != null) {
                                        val bounds = getDesktopTextBoxBounds(selectedBox)
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
                                            change.consume()
                                            continue
                                        }

                                        val pillY = bounds.top - 28f
                                        val editRect = Rect(bounds.left, pillY - 4f, bounds.left + 50f, pillY + 24f)
                                        if (editRect.contains(canvasPoint)) {
                                            state.activeTextBoxForEdit = selectedBox
                                            state.selectedTextBoxId = null
                                            change.consume()
                                            continue
                                        }

                                        val delRect = Rect(bounds.left + 54f, pillY - 4f, bounds.left + 106f, pillY + 24f)
                                        if (delRect.contains(canvasPoint)) {
                                            state.deleteTextBox(selectedBox.id)
                                            state.selectedTextBoxId = null
                                            change.consume()
                                            continue
                                        }

                                        if (bounds.contains(canvasPoint)) {
                                            isMovingTextBox = true
                                            isResizingTextBox = false
                                            lastTouchCanvas = canvasPoint
                                            initialTouchCanvas = canvasPoint
                                            change.consume()
                                            continue
                                        }

                                        state.selectedTextBoxId = null
                                    }

                                    // 4. Check for Long-press on any text box
                                    val hitBox = state.currentPage.textBoxes.lastOrNull { tb ->
                                        getDesktopTextBoxBounds(tb).contains(canvasPoint)
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
                                            if (state.activeStroke != null) state.cancelStroke()
                                        }
                                    } else {
                                        pendingTouchTextBoxId = null
                                        longPressJob?.cancel()
                                    }

                                    // Start drawing action
                                    when (state.currentTool) {
                                        DrawingTool.PEN,
                                        DrawingTool.HIGHLIGHTER -> {
                                            state.startStroke(canvasPoint, pressure)
                                        }
                                        DrawingTool.STROKE_ERASER,
                                        DrawingTool.SEGMENT_ERASER -> {
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
                                    change.consume()
                                }
                            }

                            PointerEventType.Move -> {
                                for (c in changes) {
                                    if (c.pressed) activePointers[c.id] = c.position
                                    else activePointers.remove(c.id)
                                }

                                if (activePointers.size >= 2) {
                                    // 2-Finger pan & pinch-zoom on Windows Touch Screen
                                    val pList = activePointers.values.toList()
                                    val p0 = pList[0]
                                    val p1 = pList[1]
                                    val currentFocal = (p0 + p1) / 2f
                                    val currentSpan = hypot(p1.x - p0.x, p1.y - p0.y).coerceAtLeast(1f)

                                    if (state.activeStroke != null) state.cancelStroke()
                                    if (state.activeShape != null) state.activeShape = null
                                    isPanDragging = false
                                    isStylusErasing = false
                                    longPressJob?.cancel()
                                    longPressJob = null

                                    val prevFocal = previousFocalPoint ?: currentFocal
                                    val delta = currentFocal - prevFocal
                                    state.viewportState.panBy(delta)

                                    if (previousSpan > 0f) {
                                        val scaleFactor = currentSpan / previousSpan
                                        state.viewportState.zoomBy(scaleFactor, currentFocal)
                                    }

                                    previousFocalPoint = currentFocal
                                    previousSpan = currentSpan
                                    changes.forEach { it.consume() }
                                } else if (isPanDragging && changes.isNotEmpty()) {
                                    // Hand tool, 1-finger touch pan (Stylus Only mode), or mouse right/middle drag pan
                                    val change = changes.first()
                                    val screenPoint = change.position
                                    val delta = screenPoint - lastPanScreenPoint
                                    lastPanScreenPoint = screenPoint
                                    state.viewportState.panBy(delta)
                                    change.consume()
                                } else if (changes.isNotEmpty()) {
                                    val change = changes.first()
                                    val screenPoint = change.position
                                    val canvasPoint = state.viewportState.screenToCanvas(screenPoint)

                                    // Palm Rest Shield check
                                    val containerH = state.viewportState.containerSize.height
                                    val isInsidePalmShield = isPalmShieldVisible && containerH > 0 && screenPoint.y >= (containerH - palmShieldHeightPx)
                                    if (isInsidePalmShield) {
                                        if (state.activeStroke != null) state.cancelStroke()
                                        if (state.activeShape != null) state.activeShape = null
                                        change.consume()
                                        continue
                                    }

                                    if (isStylusErasing || change.type == PointerType.Eraser) {
                                        val eraserRadius = state.viewportState.screenToCanvasDist(36f)
                                        state.eraseAt(canvasPoint, eraserRadius)
                                        change.consume()
                                        continue
                                    }

                                    val isStylus = change.type == PointerType.Stylus
                                    val rawPressure = change.pressure
                                    val pressure = if (isStylus && rawPressure > 0f) rawPressure.coerceIn(0.1f, 1.0f) else 0.5f

                                    // Cancel long press if moved significantly before timer
                                    if (pendingTouchTextBoxId != null && !isMovingTextBox && !isResizingTextBox) {
                                        val dTouch = canvasPoint - initialTouchCanvas
                                        val dist = hypot(dTouch.x, dTouch.y)
                                        if (dist > state.viewportState.screenToCanvasDist(12f)) {
                                            longPressJob?.cancel()
                                            longPressJob = null
                                            pendingTouchTextBoxId = null
                                        }
                                    }

                                    if (isMovingTextBox && state.selectedTextBoxId != null) {
                                        val delta = canvasPoint - lastTouchCanvas
                                        lastTouchCanvas = canvasPoint
                                        state.moveTextBox(state.selectedTextBoxId!!, delta)
                                        change.consume()
                                        continue
                                    }

                                    if (isResizingTextBox && state.selectedTextBoxId != null) {
                                        val selected = state.currentPage.textBoxes.firstOrNull { it.id == state.selectedTextBoxId }
                                        if (selected != null) {
                                            val bounds = getDesktopTextBoxBounds(selected)
                                            val currentWidth = (canvasPoint.x - bounds.left).coerceAtLeast(30f)
                                            val scale = currentWidth / initialBoxWidth
                                            state.resizeTextBox(selected.id, initialFontSize * scale)
                                        }
                                        change.consume()
                                        continue
                                    }

                                    // In-progress drawing
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
                                    change.consume()
                                }
                            }

                            PointerEventType.Release -> {
                                for (c in changes) {
                                    activePointers.remove(c.id)
                                }
                                if (activePointers.size < 2) {
                                    previousFocalPoint = null
                                    previousSpan = 0f
                                }
                                longPressJob?.cancel()
                                longPressJob = null

                                if (isPanDragging) {
                                    isPanDragging = false
                                    changes.forEach { it.consume() }
                                    continue
                                }

                                if (isStylusErasing) {
                                    isStylusErasing = false
                                    state.finishErasing()
                                    changes.forEach { it.consume() }
                                    continue
                                }

                                if (state.currentTool == DrawingTool.STROKE_ERASER || state.currentTool == DrawingTool.SEGMENT_ERASER) {
                                    state.finishErasing()
                                }

                                if (isMovingTextBox || isResizingTextBox) {
                                    isMovingTextBox = false
                                    isResizingTextBox = false
                                    state.commitTransform()
                                } else {
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

                                    when (state.currentTool) {
                                        DrawingTool.PEN,
                                        DrawingTool.HIGHLIGHTER -> {
                                            state.finishStroke()
                                        }
                                        DrawingTool.SHAPE -> {
                                            state.finishShape()
                                        }
                                        else -> {}
                                    }
                                }
                                changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val curPage = state.currentPage
            val layoutMode = state.document.layoutMode

            withTransform({
                translate(left = state.viewportState.panX, top = state.viewportState.panY)
                scale(scaleX = state.viewportState.scale, scaleY = state.viewportState.scale, pivot = Offset.Zero)
            }) {
                // 1. Page Paper Background
                if (layoutMode == CanvasLayoutMode.PAGED) {
                    drawRect(
                        color = Color.White,
                        topLeft = Offset.Zero,
                        size = Size(curPage.width, curPage.height)
                    )
                    drawRect(
                        color = Color(0x30000000),
                        topLeft = Offset.Zero,
                        size = Size(curPage.width, curPage.height),
                        style = Stroke(width = 1.5f)
                    )
                    drawPaperGuidelines(curPage.width, curPage.height)
                } else {
                    drawInfiniteGrid(state.viewportState)
                }

                // 2. Vector Strokes
                val allStrokes = if (state.activeStroke != null) {
                    curPage.strokes + state.activeStroke!!
                } else {
                    curPage.strokes
                }
                for (stroke in allStrokes) {
                    drawVectorStroke(stroke)
                }

                // 3. Shapes
                val allShapes = if (state.activeShape != null) {
                    curPage.shapes + state.activeShape!!
                } else {
                    curPage.shapes
                }
                for (shape in allShapes) {
                    drawVectorShape(shape)
                }

                // 4. Text Boxes
                drawDesktopTextBoxes(
                    textBoxes = curPage.textBoxes,
                    isTextToolActive = state.currentTool == DrawingTool.TEXT,
                    selectedTextBoxId = state.selectedTextBoxId,
                    textMeasurer = textMeasurer
                )
            }
        }

        // 5. Draggable Palm Rest Shield (Writing Guard)
        if (isPalmShieldVisible) {
            val density = LocalDensity.current
            val guardHeightDp = with(density) { palmShieldHeightPx.toDp() }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(guardHeightDp)
                    .background(Color(0x332563EB))
                    .border(
                        width = 1.5.dp,
                        color = Color(0x882563EB),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-14).dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val newHeight = (palmShieldHeightPx - dragAmount.y).coerceIn(120f, 650f)
                                onPalmShieldHeightChange(newHeight)
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Adjust Guard Height",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Palm Rest Guard (Rest palm here / Drag to resize)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

fun getDesktopTextBoxBounds(tb: TextBoxModel): Rect {
    val approxWidth = maxOf(60f, tb.text.length * tb.fontSizeSp * 1.3f)
    val approxHeight = tb.fontSizeSp * 2.4f
    return Rect(
        left = tb.x - 12f,
        top = tb.y - approxHeight - 6f,
        right = tb.x + approxWidth + 12f,
        bottom = tb.y + 10f
    )
}

private fun DrawScope.drawDesktopTextBoxes(
    textBoxes: List<TextBoxModel>,
    isTextToolActive: Boolean,
    selectedTextBoxId: String?,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    for (tb in textBoxes) {
        val bounds = getDesktopTextBoxBounds(tb)
        val isSelected = tb.id == selectedTextBoxId

        if (isSelected) {
            // Selected Text Box: solid border, soft tint, corner handles, resize pill
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

            // 3 Corner Dots
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

            // Top action pills: [ Edit ]  [ Delete ]
            val pillY = bounds.top - 28f
            drawRoundRect(
                color = Color(0xFF1E293B),
                topLeft = Offset(bounds.left, pillY),
                size = Size(50f, 22f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "Edit",
                topLeft = Offset(bounds.left + 14f, pillY + 3f),
                style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )

            drawRoundRect(
                color = Color(0xFFDC2626),
                topLeft = Offset(bounds.left + 54f, pillY),
                size = Size(52f, 22f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "Delete",
                topLeft = Offset(bounds.left + 60f, pillY + 3f),
                style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )

        } else if (isTextToolActive) {
            // Subtle dashed hint when text tool is active
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

        // Draw the text
        drawText(
            textMeasurer = textMeasurer,
            text = tb.text,
            topLeft = Offset(tb.x, tb.y - tb.fontSizeSp * 2.2f),
            style = TextStyle(
                color = Color(tb.colorArgb),
                fontSize = (tb.fontSizeSp * 1.8f).sp,
                fontWeight = FontWeight.Medium
            )
        )
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

private fun DrawScope.drawInfiniteGrid(viewport: ViewportState) {
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
