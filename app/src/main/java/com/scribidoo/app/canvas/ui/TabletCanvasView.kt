package com.scribidoo.app.canvas.ui

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import com.scribidoo.app.canvas.ink.InkPathGenerator
import com.scribidoo.app.canvas.input.ClassifiedTool
import com.scribidoo.app.canvas.input.InputClassifier
import com.scribidoo.app.canvas.input.InputVerdict
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.model.ShapeModel
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.model.StrokeModel
import com.scribidoo.app.canvas.state.CanvasState
import kotlin.math.hypot

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TabletCanvasView(
    state: CanvasState,
    modifier: Modifier = Modifier
) {
    val inputClassifier = remember(state.palmConfig) {
        InputClassifier(state.palmConfig)
    }

    // Gesture tracking state for 2-finger pan & pinch-to-zoom
    var previousFocalPoint by remember { mutableStateOf<Offset?>(null) }
    var previousSpan by remember { mutableFloatStateOf(0f) }
    var isViewportGestureActive by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFFF3F4F6))
            .onSizeChanged { size ->
                state.viewportState.containerSize = size.toSize()
            }
            .pointerInteropFilter { motionEvent ->
                val pointerCount = motionEvent.pointerCount

                when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isViewportGestureActive = false
                        val verdict = inputClassifier.evaluateTouch(
                            event = motionEvent,
                            pointerIndex = 0,
                            containerHeight = state.viewportState.containerSize.height,
                            activePointersCount = 1
                        )

                        when (verdict) {
                            InputVerdict.ACCEPT_FOR_DRAWING -> {
                                val screenPoint = Offset(motionEvent.x, motionEvent.y)
                                val canvasPoint = state.viewportState.screenToCanvas(screenPoint)
                                val pressure = motionEvent.pressure.coerceIn(0.1f, 1.0f)

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
                                        state.addTextBox(canvasPoint)
                                    }
                                    else -> {}
                                }
                            }
                            InputVerdict.ACCEPT_FOR_VIEWPORT -> {
                                isViewportGestureActive = true
                                previousFocalPoint = Offset(motionEvent.x, motionEvent.y)
                            }
                            InputVerdict.DISCARD_PALM -> {
                                // Discard accidental palm touch
                            }
                        }
                    }

                    MotionEvent.ACTION_POINTER_DOWN -> {
                        // Multi-touch entered (e.g. 2nd finger placed down)
                        // If drawing was in progress with finger, cancel it in favor of navigation
                        if (state.activeStroke != null) {
                            state.cancelStroke()
                        }
                        if (state.activeShape != null) {
                            state.activeShape = null
                        }

                        isViewportGestureActive = true
                        val p0 = Offset(motionEvent.getX(0), motionEvent.getY(0))
                        val p1 = Offset(motionEvent.getX(1), motionEvent.getY(1))
                        previousFocalPoint = (p0 + p1) / 2f
                        previousSpan = hypot(p1.x - p0.x, p1.y - p0.y).coerceAtLeast(1f)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (isViewportGestureActive || pointerCount >= 2) {
                            // Viewport Pan & Pinch-Zoom
                            if (pointerCount >= 2) {
                                val p0 = Offset(motionEvent.getX(0), motionEvent.getY(0))
                                val p1 = Offset(motionEvent.getX(1), motionEvent.getY(1))
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
                            } else if (pointerCount == 1 && previousFocalPoint != null) {
                                // Single-finger panning (e.g., when Finger Drawing is OFF)
                                val currentFocal = Offset(motionEvent.x, motionEvent.y)
                                val delta = currentFocal - previousFocalPoint!!
                                state.viewportState.panBy(delta)
                                previousFocalPoint = currentFocal
                            }
                        } else {
                            // Single pointer drawing / erasing / shaping
                            val verdict = inputClassifier.evaluateTouch(
                                event = motionEvent,
                                pointerIndex = 0,
                                containerHeight = state.viewportState.containerSize.height,
                                activePointersCount = 1
                            )

                            if (verdict == InputVerdict.ACCEPT_FOR_DRAWING) {
                                val screenPoint = Offset(motionEvent.x, motionEvent.y)
                                val canvasPoint = state.viewportState.screenToCanvas(screenPoint)
                                val pressure = motionEvent.pressure.coerceIn(0.1f, 1.0f)

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

                    MotionEvent.ACTION_POINTER_UP -> {
                        // One of the multi-touch fingers was lifted
                        previousFocalPoint = null
                        previousSpan = 0f
                    }

                    MotionEvent.ACTION_UP -> {
                        if (isViewportGestureActive) {
                            isViewportGestureActive = false
                            previousFocalPoint = null
                            previousSpan = 0f
                        } else {
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
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        state.cancelStroke()
                        state.activeShape = null
                        isViewportGestureActive = false
                        previousFocalPoint = null
                        previousSpan = 0f
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
                        // Page background
                        drawRect(
                            color = Color.White,
                            topLeft = Offset.Zero,
                            size = Size(curPage.width, curPage.height)
                        )
                        // Page border
                        drawRect(
                            color = Color(0xFFE5E7EB),
                            topLeft = Offset.Zero,
                            size = Size(curPage.width, curPage.height),
                            style = Stroke(width = 1f)
                        )

                        // Subtle ruled lines or dot grid on paper
                        drawPaperGuidelines(curPage.width, curPage.height)
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
