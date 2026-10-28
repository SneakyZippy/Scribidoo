package com.scribidoo.app.canvas.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
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
    val backgroundBitmaps = mutableStateMapOf<Int, ImageBitmap>()

    var currentTool by mutableStateOf(DrawingTool.PEN)
    var currentShapeType by mutableStateOf(ShapeType.RECTANGLE)
    var currentColor by mutableStateOf(Color.Black)
    var currentStrokeWidth by mutableFloatStateOf(4.0f)
    var palmConfig by mutableStateOf(PalmRejectionConfig())

    // Active in-progress items
    var activeStroke by mutableStateOf<StrokeModel?>(null)
    var activeShape by mutableStateOf<ShapeModel?>(null)
    var activeTextBoxForEdit by mutableStateOf<TextBoxModel?>(null)
    var pendingTextInsertPoint by mutableStateOf<Offset?>(null)
    var selectedTextBoxId by mutableStateOf<String?>(null)

    // History for Undo / Redo
    private val undoStack = mutableListOf<DocumentModel>()
    private val redoStack = mutableListOf<DocumentModel>()

    var undoCount by mutableStateOf(0)
        private set
    var redoCount by mutableStateOf(0)
        private set

    val canUndo: Boolean get() = undoCount > 0
    val canRedo: Boolean get() = redoCount > 0

    val currentPage: PageModel
        get() = document.pages.getOrElse(document.activePageIndex) { PageModel() }

    private var preEraseDocument: DocumentModel? = null

    val isErasing: Boolean
        get() = preEraseDocument != null

    fun startErasing() {
        if (preEraseDocument == null) {
            preEraseDocument = document
        }
    }

    fun finishErasing() {
        val prev = preEraseDocument
        preEraseDocument = null
        if (prev != null && prev != document) {
            undoStack.add(prev)
            redoStack.clear()
            if (undoStack.size > 50) {
                undoStack.removeAt(0)
            }
            undoCount = undoStack.size
            redoCount = 0
        }
    }

    fun pushHistory() {
        undoStack.add(document)
        redoStack.clear()
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
        undoCount = undoStack.size
        redoCount = 0
    }

    fun undo() {
        if (!canUndo) return
        redoStack.add(document)
        document = undoStack.removeAt(undoStack.lastIndex)
        undoCount = undoStack.size
        redoCount = redoStack.size
    }

    fun redo() {
        if (!canRedo) return
        undoStack.add(document)
        document = redoStack.removeAt(redoStack.lastIndex)
        undoCount = undoStack.size
        redoCount = redoStack.size
    }

    fun toggleFingerDrawing() {
        palmConfig = palmConfig.copy(isFingerDrawingEnabled = !palmConfig.isFingerDrawingEnabled)
    }

    fun setLayoutMode(mode: CanvasLayoutMode) {
        if (document.layoutMode != mode) {
            pushHistory()
            document = document.copy(layoutMode = mode, modifiedAt = System.currentTimeMillis())
        }
    }

    fun startStroke(canvasPoint: Offset, pressure: Float = 0.5f) {
        val basePoint = SerializablePoint(canvasPoint.x, canvasPoint.y, pressure)
        activeStroke = StrokeModel(
            points = listOf(basePoint),
            colorArgb = currentColor.toArgb(),
            strokeWidth = currentStrokeWidth,
            isHighlighter = currentTool == DrawingTool.HIGHLIGHTER,
            alpha = if (currentTool == DrawingTool.HIGHLIGHTER) 0.55f else 1.0f
        )
    }

    fun appendStrokePoint(canvasPoint: Offset, pressure: Float = 0.5f) {
        val stroke = activeStroke ?: return
        val newPoint = SerializablePoint(canvasPoint.x, canvasPoint.y, pressure)
        activeStroke = stroke.copy(points = stroke.points + newPoint)
    }

    fun finishStroke() {
        val stroke = activeStroke ?: return
        activeStroke = null
        if (stroke.points.size < 2) return

        pushHistory()
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        updatedPages[document.activePageIndex] = curPage.copy(strokes = curPage.strokes + stroke)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun cancelStroke() {
        activeStroke = null
    }

    fun eraseAt(canvasPoint: Offset, eraserRadius: Float) {
        startErasing()
        val curPage = currentPage
        // Also check if any text boxes intersect with eraser
        val remainingTextBoxes = curPage.textBoxes.filterNot { tb ->
            val dx = canvasPoint.x - tb.x
            val dy = canvasPoint.y - tb.y
            kotlin.math.hypot(dx, dy) <= (eraserRadius + 30f)
        }
        val textChanged = remainingTextBoxes.size != curPage.textBoxes.size

        when (currentTool) {
            DrawingTool.STROKE_ERASER -> {
                val remainingStrokes = curPage.strokes.filterNot { stroke ->
                    EraserEngine.intersectsStroke(stroke, canvasPoint, eraserRadius)
                }
                if (remainingStrokes.size != curPage.strokes.size || textChanged) {
                    val updatedPages = document.pages.toMutableList()
                    updatedPages[document.activePageIndex] = curPage.copy(
                        strokes = remainingStrokes,
                        textBoxes = remainingTextBoxes
                    )
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
                if (changed || textChanged) {
                    val updatedPages = document.pages.toMutableList()
                    updatedPages[document.activePageIndex] = curPage.copy(
                        strokes = newStrokes,
                        textBoxes = remainingTextBoxes
                    )
                    document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
                }
            }
            else -> {
                // Also support active stylus eraser tail even when pen is selected
                val remainingStrokes = curPage.strokes.filterNot { stroke ->
                    EraserEngine.intersectsStroke(stroke, canvasPoint, eraserRadius)
                }
                if (remainingStrokes.size != curPage.strokes.size || textChanged) {
                    val updatedPages = document.pages.toMutableList()
                    updatedPages[document.activePageIndex] = curPage.copy(
                        strokes = remainingStrokes,
                        textBoxes = remainingTextBoxes
                    )
                    document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
                }
            }
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

    fun addTextBox(
        canvasPoint: Offset,
        text: String,
        fontSizeSp: Float = 20f,
        colorArgb: Int = currentColor.toArgb()
    ) {
        if (text.isBlank()) return
        pushHistory()
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val textBox = TextBoxModel(
            x = canvasPoint.x,
            y = canvasPoint.y,
            text = text,
            fontSizeSp = fontSizeSp,
            colorArgb = colorArgb
        )
        updatedPages[document.activePageIndex] = curPage.copy(textBoxes = curPage.textBoxes + textBox)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun updateTextBox(id: String, newText: String, fontSizeSp: Float, colorArgb: Int) {
        if (newText.isBlank()) {
            deleteTextBox(id)
            return
        }
        pushHistory()
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val updatedBoxes = curPage.textBoxes.map {
            if (it.id == id) it.copy(text = newText, fontSizeSp = fontSizeSp, colorArgb = colorArgb) else it
        }
        updatedPages[document.activePageIndex] = curPage.copy(textBoxes = updatedBoxes)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun deleteTextBox(id: String) {
        pushHistory()
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val updatedBoxes = curPage.textBoxes.filterNot { it.id == id }
        updatedPages[document.activePageIndex] = curPage.copy(textBoxes = updatedBoxes)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
        if (selectedTextBoxId == id) {
            selectedTextBoxId = null
        }
    }

    fun moveTextBox(id: String, deltaCanvas: Offset) {
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val updatedBoxes = curPage.textBoxes.map {
            if (it.id == id) it.copy(x = it.x + deltaCanvas.x, y = it.y + deltaCanvas.y) else it
        }
        updatedPages[document.activePageIndex] = curPage.copy(textBoxes = updatedBoxes)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun resizeTextBox(id: String, newFontSize: Float) {
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val updatedBoxes = curPage.textBoxes.map {
            if (it.id == id) it.copy(fontSizeSp = newFontSize.coerceIn(10f, 96f)) else it
        }
        updatedPages[document.activePageIndex] = curPage.copy(textBoxes = updatedBoxes)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun commitTransform() {
        pushHistory()
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

    fun prevPage() {
        if (document.activePageIndex > 0) {
            setActivePage(document.activePageIndex - 1)
        }
    }

    fun nextPage() {
        if (document.activePageIndex < document.pages.size - 1) {
            setActivePage(document.activePageIndex + 1)
        }
    }

    fun setBackgroundBitmap(pageIndex: Int, bitmap: ImageBitmap) {
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
