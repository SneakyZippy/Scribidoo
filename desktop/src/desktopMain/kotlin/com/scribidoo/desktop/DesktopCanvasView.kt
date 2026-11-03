package com.scribidoo.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.toSize
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.app.canvas.ui.drawLassoPath
import com.scribidoo.app.canvas.ui.drawLassoSelectionOverlay
import com.scribidoo.desktop.canvas.DesktopCanvasPointerState
import com.scribidoo.desktop.canvas.drawAudioNotes
import com.scribidoo.desktop.canvas.drawDesktopSelectedItemOverlay
import com.scribidoo.desktop.canvas.drawDesktopTextBoxes
import com.scribidoo.desktop.canvas.drawImages
import com.scribidoo.desktop.canvas.drawInfiniteGrid
import com.scribidoo.desktop.canvas.drawPaperGuidelines
import com.scribidoo.desktop.canvas.drawVectorShape
import com.scribidoo.desktop.canvas.drawVectorStroke
import com.scribidoo.desktop.media.DesktopAudioPlayer
import java.awt.Cursor

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

    val cursor = remember(state.currentTool, state.isSpacebarPanActive) {
        val awtCursor = when {
            state.isSpacebarPanActive || state.currentTool == DrawingTool.HAND -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            state.currentTool == DrawingTool.TEXT -> Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
            state.currentTool in listOf(DrawingTool.PEN, DrawingTool.HIGHLIGHTER, DrawingTool.SHAPE) -> Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
            state.currentTool in listOf(DrawingTool.STROKE_ERASER, DrawingTool.SEGMENT_ERASER) -> Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
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
        // Layer 1: Static / Committed Canvas Content
        Canvas(modifier = Modifier.fillMaxSize()) {
            val curPage = state.currentPage
            val layoutMode = state.document.layoutMode

            withTransform({
                translate(left = state.viewportState.panX, top = state.viewportState.panY)
                scale(scaleX = state.viewportState.scale, scaleY = state.viewportState.scale, pivot = Offset.Zero)
            }) {
                // Background Paper or Grid
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

                // Images
                drawImages(curPage.images, state.imageBitmaps)

                // Committed Vector Strokes
                for (stroke in curPage.strokes) {
                    drawVectorStroke(stroke, strokePathCache)
                }

                // Committed Shapes
                for (shape in curPage.shapes) {
                    drawVectorShape(shape)
                }

                // Text Boxes
                drawDesktopTextBoxes(
                    textBoxes = curPage.textBoxes,
                    isTextToolActive = state.currentTool == DrawingTool.TEXT,
                    selectedTextBoxId = state.selectedTextBoxId,
                    textMeasurer = textMeasurer
                )

                // Audio Notes
                drawAudioNotes(
                    audioNotes = curPage.audioNotes,
                    playingAudioPath = audioPlayer.currentlyPlayingPath,
                    isPlaying = audioPlayer.isPlaying,
                    textMeasurer = textMeasurer
                )
            }
        }

        // Layer 2: Dynamic Inking & Interactive Overlays
        Canvas(modifier = Modifier.fillMaxSize()) {
            val curPage = state.currentPage

            withTransform({
                translate(left = state.viewportState.panX, top = state.viewportState.panY)
                scale(scaleX = state.viewportState.scale, scaleY = state.viewportState.scale, pivot = Offset.Zero)
            }) {
                // Active Stroke in-progress
                state.activeStroke?.let {
                    drawVectorStroke(it, null)
                }

                // Active Shape in-progress
                state.activeShape?.let {
                    drawVectorShape(it)
                }

                // Selected Item Overlay
                state.selectedItem?.let {
                    drawDesktopSelectedItemOverlay(it, curPage, state.viewportState.scale, textMeasurer)
                }

                // Lasso Selection
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
