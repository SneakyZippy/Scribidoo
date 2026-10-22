package com.scribidoo.app.canvas.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.graphics.Bitmap
import com.scribidoo.app.canvas.ink.EraserEngine
import com.scribidoo.app.canvas.input.PalmRejectionConfig
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.model.PageModel
import com.scribidoo.app.canvas.model.SerializablePoint
import com.scribidoo.app.canvas.model.ShapeModel
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.model.StrokeModel
import com.scribidoo.app.canvas.model.TextBoxModel
import com.scribidoo.app.canvas.viewport.ViewportState
import java.io.File

class CanvasState(
    initialDocument: DocumentModel = DocumentModel(),
    val viewportState: ViewportState = ViewportState()
) {
    var document by mutableStateOf(initialDocument)
        private set

    // Cache of page background bitmaps (from PDF or imported photo)
    val backgroundBitmaps = mutableStateMapOf<Int, Bitmap>()

    var currentTool by mutableStateOf(DrawingTool.PEN)
    var currentShapeType by mutableStateOf(ShapeType.RECTANGLE)
    var currentColor by mutableStateOf(Color.Black)
    var currentStrokeWidth by mutableFloatStateOf(4.0f)
    var palmConfig by mutableStateOf(PalmRejectionConfig())

    // Active in-progress items
    var activeStroke by mutableStateOf<StrokeModel?>(null)
    var activeShape by mutableStateOf<ShapeModel?>(null)

    // History for Undo / Redo
    private val undoStack = mutableListOf<DocumentModel>()
    private val redoStack = mutableListOf<DocumentModel>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    val currentPage: PageModel
        get() = document.pages.getOrElse(document.activePageIndex) { PageModel() }

    private fun pushHistory() {
        undoStack.add(document)
        redoStack.clear()
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
    }

    fun undo() {
        if (!canUndo) return
        redoStack.add(document)
        document = undoStack.removeAt(undoStack.lastIndex)
    }

    fun redo() {
        if (!canRedo) return
        undoStack.add(document)
        document = redoStack.removeAt(redoStack.lastIndex)
    }

    fun setLayoutMode(mode: CanvasLayoutMode) {
        if (document.layoutMode == mode) return
        pushHistory()
        document = document.copy(layoutMode = mode)
    }

    fun toggleLayoutMode() {
        setLayoutMode(
            if (document.layoutMode == CanvasLayoutMode.PAGED) CanvasLayoutMode.INFINITE
            else CanvasLayoutMode.PAGED
        )
    }

    fun toggleFingerDrawing() {
        palmConfig = palmConfig.copy(isFingerDrawingEnabled = !palmConfig.isFingerDrawingEnabled)
    }

    fun startStroke(canvasPoint: Offset, pressure: Float) {
        val isHighlighter = currentTool == DrawingTool.HIGHLIGHTER
        val initialPoints = listOf(
            SerializablePoint(
                x = canvasPoint.x,
                y = canvasPoint.y,
                pressure = pressure
            )
        )
        activeStroke = StrokeModel(
            points = initialPoints,
            colorArgb = currentColor.toArgb(),
            strokeWidth = currentStrokeWidth,
            alpha = if (isHighlighter) 0.45f else 1.0f,
            isHighlighter = isHighlighter
        )
    }

    fun appendStrokePoint(canvasPoint: Offset, pressure: Float) {
        val current = activeStroke ?: return
        val updatedPoints = current.points + SerializablePoint(
            x = canvasPoint.x,
            y = canvasPoint.y,
            pressure = pressure
        )
        activeStroke = current.copy(points = updatedPoints)
    }

    fun finishStroke() {
        val stroke = activeStroke ?: return
        activeStroke = null
        if (stroke.points.size >= 1) {
            pushHistory()
            val updatedPages = document.pages.toMutableList()
            val curPage = currentPage
            val updatedPage = curPage.copy(strokes = curPage.strokes + stroke)
            updatedPages[document.activePageIndex] = updatedPage
            document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
        }
    }

    fun cancelStroke() {
        activeStroke = null
    }

    fun eraseAt(canvasPoint: Offset, eraserRadius: Float) {
        val curPage = currentPage
        when (currentTool) {
            DrawingTool.STROKE_ERASER -> {
                val remainingStrokes = curPage.strokes.filterNot { stroke ->
                    EraserEngine.intersectsStroke(stroke, canvasPoint, eraserRadius)
                }
                if (remainingStrokes.size != curPage.strokes.size) {
                    pushHistory()
                    val updatedPages = document.pages.toMutableList()
                    updatedPages[document.activePageIndex] = curPage.copy(strokes = remainingStrokes)
                    document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
                }
            }
            DrawingTool.SEGMENT_ERASER -> {
                var changed = false
                val newStrokes = mutableListOf<StrokeModel>()
                for (stroke in curPage.strokes) {
                    if (EraserEngine.intersectsStroke(stroke, canvasPoint, eraserRadius)) {
                        changed = true
                        newStrokes.addAll(EraserEngine.sliceStroke(stroke, canvasPoint, eraserRadius))
                    } else {
                        newStrokes.add(stroke)
                    }
                }
                if (changed) {
                    pushHistory()
                    val updatedPages = document.pages.toMutableList()
                    updatedPages[document.activePageIndex] = curPage.copy(strokes = newStrokes)
                    document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
                }
            }
            else -> {}
        }
    }

    fun startShape(canvasPoint: Offset) {
        activeShape = ShapeModel(
            type = currentShapeType,
            startX = canvasPoint.x,
            startY = canvasPoint.y,
            endX = canvasPoint.x,
            endY = canvasPoint.y,
            strokeColorArgb = currentColor.toArgb(),
            strokeWidth = currentStrokeWidth
        )
    }

    fun updateShape(canvasPoint: Offset) {
        val shape = activeShape ?: return
        activeShape = shape.copy(endX = canvasPoint.x, endY = canvasPoint.y)
    }

    fun finishShape() {
        val shape = activeShape ?: return
        activeShape = null
        pushHistory()
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        updatedPages[document.activePageIndex] = curPage.copy(shapes = curPage.shapes + shape)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun addTextBox(canvasPoint: Offset, text: String = "Tap to edit") {
        pushHistory()
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val textBox = TextBoxModel(
            x = canvasPoint.x,
            y = canvasPoint.y,
            text = text,
            colorArgb = currentColor.toArgb()
        )
        updatedPages[document.activePageIndex] = curPage.copy(textBoxes = curPage.textBoxes + textBox)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun clearPage() {
        pushHistory()
        val updatedPages = document.pages.toMutableList()
        updatedPages[document.activePageIndex] = currentPage.copy(
            strokes = emptyList(),
            shapes = emptyList(),
            textBoxes = emptyList()
        )
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun addNewPage() {
        pushHistory()
        val newPage = PageModel(index = document.pages.size)
        val updatedPages = document.pages + newPage
        document = document.copy(
            pages = updatedPages,
            activePageIndex = updatedPages.lastIndex,
            modifiedAt = System.currentTimeMillis()
        )
    }

    fun setActivePage(index: Int) {
        if (index in document.pages.indices) {
            document = document.copy(activePageIndex = index)
        }
    }

    fun setBackgroundBitmap(pageIndex: Int, bitmap: Bitmap) {
        backgroundBitmaps[pageIndex] = bitmap
    }

    fun loadDocument(doc: DocumentModel) {
        pushHistory()
        document = doc
        backgroundBitmaps.clear()
    }

    fun renameDocument(newTitle: String) {
        if (newTitle.isNotBlank()) {
            pushHistory()
            document = document.copy(title = newTitle.trim(), modifiedAt = System.currentTimeMillis())
        }
    }

    fun initializeFromPdf(pdfFile: File, pageCount: Int) {
        pushHistory()
        val pages = (0 until pageCount).map { idx ->
            PageModel(
                index = idx,
                backgroundPdfPage = idx,
                backgroundUri = pdfFile.absolutePath
            )
        }
        document = DocumentModel(
            title = pdfFile.nameWithoutExtension,
            pages = pages,
            activePageIndex = 0
        )
        backgroundBitmaps.clear()
    }
}
