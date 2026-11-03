package com.scribidoo.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.toSize
import com.scribidoo.app.canvas.ink.InkPathGenerator
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.model.ShapeModel
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.model.StrokeModel
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.app.canvas.ui.drawLassoPath
import com.scribidoo.app.canvas.ui.drawLassoSelectionOverlay
import com.scribidoo.app.canvas.viewport.ViewportState
import com.scribidoo.desktop.canvas.DesktopCanvasPointerState
import com.scribidoo.desktop.canvas.drawAudioNotes
import com.scribidoo.desktop.canvas.drawDesktopSelectedItemOverlay
import com.scribidoo.desktop.canvas.drawImages
import com.scribidoo.desktop.canvas.getDesktopTextBoxBounds
import com.scribidoo.desktop.media.DesktopAudioPlayer
import java.awt.Cursor
import kotlin.math.hypot

@Composable
fun DesktopCanvasView(
    state: CanvasState,
    audioPlayer: DesktopAudioPlayer,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    val strokePathCache = remember { mutableMapOf<String, Path>() }

    val pointerState = remember(state, audioPlayer) {
        DesktopCanvasPointerState(state, audioPlayer, coroutineScope, density)
    }

    val cursor = remember(state.currentTool) {
        val awtCursor = when (state.currentTool) {
            DrawingTool.HAND -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            DrawingTool.TEXT -> Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
            DrawingTool.PEN, DrawingTool.HIGHLIGHTER, DrawingTool.SHAPE -> Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
            else -> Cursor.getDefaultCursor()
        }
        PointerIcon(awtCursor)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFFE5E7EB))
            .pointerHoverIcon(cursor)
            .onSizeChanged { size ->
                state.viewportState.containerSize = size.toSize()
            }
            .pointerInput(pointerState) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        pointerState.handlePointerEvent(event)
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
                // 1. Paper / Background Layer
                if (layoutMode == CanvasLayoutMode.PAGED) {
                    drawRect(color = Color.White, topLeft = Offset.Zero, size = Size(curPage.width, curPage.height))
                    drawRect(color = Color(0x30000000), topLeft = Offset.Zero, size = Size(curPage.width, curPage.height), style = Stroke(1.5f))
                    val bgBitmap = state.backgroundBitmaps[curPage.index]
                    if (bgBitmap != null) {
                        drawImage(
                            image = bgBitmap,
                            dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                            dstSize = androidx.compose.ui.unit.IntSize(curPage.width.toInt(), curPage.height.toInt())
                        )
                    } else {
                        drawPaperGuidelines(curPage.width, curPage.height, state.viewportState.scale)
                    }
                } else {
                    drawInfiniteGrid(state.viewportState)
                }

                // 2. Images (Photos and Brand Stickers)
                drawImages(curPage.images, state.imageBitmaps)

                // 3. Vector Strokes (Cached smooth paths for completed strokes, direct path for active stroke)
                for (stroke in curPage.strokes) {
                    drawVectorStroke(stroke, strokePathCache)
                }
                state.activeStroke?.let {
                    drawVectorStroke(it, null)
                }

                // 4. Shapes
                val allShapes = if (state.activeShape != null) curPage.shapes + state.activeShape!! else curPage.shapes
                for (shape in allShapes) {
                    drawVectorShape(shape)
                }

                // 5. Audio Notes
                drawAudioNotes(
                    audioNotes = curPage.audioNotes,
                    playingAudioPath = audioPlayer.currentlyPlayingPath,
                    isPlaying = audioPlayer.isPlaying,
                    textMeasurer = textMeasurer
                )

                // 6. Selected Item Overlay
                state.selectedItem?.let {
                    drawDesktopSelectedItemOverlay(it, curPage, state.viewportState.scale, textMeasurer)
                }

                // 7. Lasso Selection
                if (state.activeLassoPath.size > 1) {
                    drawLassoPath(state.activeLassoPath, state.viewportState.scale)
                }
                if (state.lassoSelection.isNotEmpty) {
                    drawLassoSelectionOverlay(state.lassoSelection.bounds, state.viewportState.scale)
                }
            }
        }

        // Floating Action Bar for Lasso Selection
        DesktopLassoFloatingActionBar(state = state)
    }
}

private fun DrawScope.drawVectorStroke(stroke: StrokeModel, pathCache: MutableMap<String, Path>?) {
    if (stroke.points.isEmpty()) return
    val path = if (pathCache != null) {
        pathCache.getOrPut(stroke.id) { InkPathGenerator.createSmoothPath(stroke.points) }
    } else {
        InkPathGenerator.createSmoothPath(stroke.points)
    }

    val blend = if (stroke.isHighlighter) BlendMode.Multiply else BlendMode.SrcOver
    val strokeW = if (stroke.isHighlighter) stroke.strokeWidth * 3.2f else stroke.strokeWidth
    drawPath(
        path = path,
        color = Color(stroke.colorArgb).copy(alpha = stroke.alpha),
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = blend
    )
}

private fun DrawScope.drawVectorShape(shape: ShapeModel) {
    val start = Offset(shape.startX, shape.startY)
    val end = Offset(shape.endX, shape.endY)
    val color = Color(shape.strokeColorArgb)
    val style = Stroke(width = shape.strokeWidth, cap = StrokeCap.Round)

    when (shape.type) {
        ShapeType.LINE -> drawLine(color, start, end, shape.strokeWidth, StrokeCap.Round)
        ShapeType.ARROW -> {
            drawLine(color, start, end, shape.strokeWidth, StrokeCap.Round)
            val dx = end.x - start.x
            val dy = end.y - start.y
            if (hypot(dx, dy) > 10f) {
                val headLength = (shape.strokeWidth * 4f).coerceIn(16f, 48f)
                val angle = Math.atan2(dy.toDouble(), dx.toDouble())
                val arrowHead = Path().apply {
                    moveTo(end.x, end.y)
                    lineTo((end.x - headLength * Math.cos(angle - Math.PI / 6.0)).toFloat(), (end.y - headLength * Math.sin(angle - Math.PI / 6.0)).toFloat())
                    moveTo(end.x, end.y)
                    lineTo((end.x - headLength * Math.cos(angle + Math.PI / 6.0)).toFloat(), (end.y - headLength * Math.sin(angle + Math.PI / 6.0)).toFloat())
                }
                drawPath(arrowHead, color, style = style)
            }
        }
        ShapeType.RECTANGLE -> {
            drawRect(color, Offset(minOf(start.x, end.x), minOf(start.y, end.y)), Size(Math.abs(end.x - start.x), Math.abs(end.y - start.y)), style = style)
        }
        ShapeType.ELLIPSE -> {
            drawOval(color, Offset(minOf(start.x, end.x), minOf(start.y, end.y)), Size(Math.abs(end.x - start.x), Math.abs(end.y - start.y)), style = style)
        }
        ShapeType.TRIANGLE -> {
            val triPath = Path().apply {
                if (shape.vertexX != null && shape.vertexY != null) {
                    moveTo(shape.startX, shape.startY)
                    lineTo(shape.vertexX, shape.vertexY)
                    lineTo(shape.endX, shape.endY)
                    close()
                } else {
                    val l = minOf(start.x, end.x)
                    val r = maxOf(start.x, end.x)
                    moveTo((l + r) / 2f, minOf(start.y, end.y))
                    lineTo(r, maxOf(start.y, end.y))
                    lineTo(l, maxOf(start.y, end.y))
                    close()
                }
            }
            drawPath(triPath, color, style = style)
        }
        ShapeType.RIGHT_ANGLE -> {
            val vx = shape.vertexX ?: start.x
            val vy = shape.vertexY ?: end.y
            val anglePath = Path().apply {
                moveTo(start.x, start.y)
                lineTo(vx, vy)
                lineTo(end.x, end.y)
            }
            drawPath(anglePath, color, style = style)
        }
    }
}

private fun DrawScope.drawPaperGuidelines(pageWidth: Float, pageHeight: Float, scale: Float) {
    if (36f * scale < 4f) return
    var y = 80f
    val lineColor = Color(0x18000000)
    while (y < pageHeight - 60f) {
        drawLine(lineColor, Offset(40f, y), Offset(pageWidth - 40f, y), 1f)
        y += 36f
    }
}

private fun DrawScope.drawInfiniteGrid(viewport: ViewportState) {
    var spacing = 40f
    while (spacing * viewport.scale < 24f) spacing *= 2f
    val visL = viewport.screenToCanvas(Offset.Zero).x
    val visT = viewport.screenToCanvas(Offset.Zero).y
    val visR = viewport.screenToCanvas(Offset(viewport.containerSize.width, 0f)).x
    val visB = viewport.screenToCanvas(Offset(0f, viewport.containerSize.height)).y

    val sX = (Math.floor((visL / spacing).toDouble()) * spacing).toFloat()
    val eX = (Math.ceil((visR / spacing).toDouble()) * spacing).toFloat()
    val sY = (Math.floor((visT / spacing).toDouble()) * spacing).toFloat()
    val eY = (Math.ceil((visB / spacing).toDouble()) * spacing).toFloat()

    val dotColor = Color(0x309CA3AF)
    val dotRadius = (1.5f / viewport.scale).coerceIn(1.2f, 4.0f)
    var x = sX
    while (x <= eX) {
        var y = sY
        while (y <= eY) {
            drawCircle(dotColor, dotRadius, Offset(x, y))
            y += spacing
        }
        x += spacing
    }
}
