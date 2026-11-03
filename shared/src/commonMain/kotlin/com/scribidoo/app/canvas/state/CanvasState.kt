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
import com.scribidoo.app.canvas.lasso.LassoEngine
import com.scribidoo.app.canvas.model.AudioNoteModel
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.model.ImageModel
import com.scribidoo.app.canvas.model.LassoSelection
import com.scribidoo.app.canvas.model.PageModel
import com.scribidoo.app.canvas.model.SerializablePoint
import com.scribidoo.app.canvas.model.ShapeModel
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.model.StrokeModel
import com.scribidoo.app.canvas.model.TextBoxModel
import com.scribidoo.app.canvas.shape.ShapeMathUtils
import com.scribidoo.app.canvas.viewport.ViewportState
import java.io.File

enum class SelectableType {
    TEXT_BOX,
    IMAGE,
    SHAPE,
    AUDIO_NOTE
}

data class SelectedItem(
    val id: String,
    val type: SelectableType
)

class CanvasState(
    initialDocument: DocumentModel = DocumentModel(),
    val viewportState: ViewportState = ViewportState()
) {
    var document by mutableStateOf(initialDocument)
        private set

    // Cache of page background bitmaps (from PDF or imported photo)
    val backgroundBitmaps = mutableStateMapOf<Int, ImageBitmap>()
    // Cache of imported image bitmaps by ID
    val imageBitmaps = mutableStateMapOf<String, ImageBitmap>()

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

    // Unified item selection
    var selectedItem by mutableStateOf<SelectedItem?>(null)

    // Active Lasso in-progress loop and selected group
    var activeLassoPath by mutableStateOf<List<Offset>>(emptyList())
    var lassoSelection by mutableStateOf(LassoSelection.Empty)
    var isDraggingLassoSelection by mutableStateOf(false)
    var isScalingLassoSelection by mutableStateOf(false)

    var selectedTextBoxId: String?
        get() = if (selectedItem?.type == SelectableType.TEXT_BOX) selectedItem?.id else null
        set(value) {
            selectedItem = value?.let { SelectedItem(it, SelectableType.TEXT_BOX) }
        }

    // Dialog & overlay states
    var activeAudioForMenu by mutableStateOf<AudioNoteModel?>(null)
    var showVoiceRecorderDialog by mutableStateOf(false)
    var showImageSourceDialog by mutableStateOf(false)

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

    fun startLasso(canvasPoint: Offset) {
        clearLasso()
        activeLassoPath = listOf(canvasPoint)
    }

    fun appendLassoPoint(canvasPoint: Offset) {
        if (activeLassoPath.isNotEmpty()) {
            activeLassoPath = activeLassoPath + canvasPoint
        }
    }

    fun finishLasso() {
        if (activeLassoPath.size >= 3) {
            val selection = LassoEngine.findSelection(currentPage, activeLassoPath)
            lassoSelection = selection
        }
        activeLassoPath = emptyList()
    }

    fun clearLasso() {
        activeLassoPath = emptyList()
        lassoSelection = LassoSelection.Empty
        isDraggingLassoSelection = false
        isScalingLassoSelection = false
    }

    fun moveLassoSelection(dx: Float, dy: Float) {
        if (lassoSelection.isEmpty || (dx == 0f && dy == 0f)) return
        val updatedPage = LassoEngine.translate(currentPage, lassoSelection, dx, dy)
        val updatedPages = document.pages.toMutableList()
        updatedPages[document.activePageIndex] = updatedPage
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
        lassoSelection = lassoSelection.copy(bounds = LassoEngine.computeBounds(updatedPage, lassoSelection))
    }

    fun scaleLassoSelection(scaleFactor: Float, pivot: Offset) {
        if (lassoSelection.isEmpty || scaleFactor <= 0.05f) return
        val updatedPage = LassoEngine.scale(currentPage, lassoSelection, scaleFactor, pivot)
        val updatedPages = document.pages.toMutableList()
        updatedPages[document.activePageIndex] = updatedPage
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
        lassoSelection = lassoSelection.copy(bounds = LassoEngine.computeBounds(updatedPage, lassoSelection))
    }

    fun recolorLassoSelection(color: Color) {
        if (lassoSelection.isEmpty) return
        pushHistory()
        val updatedPage = LassoEngine.recolorStrokes(currentPage, lassoSelection.strokeIds, color.toArgb())
        val updatedPages = document.pages.toMutableList()
        updatedPages[document.activePageIndex] = updatedPage
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun duplicateLassoSelection() {
        if (lassoSelection.isEmpty) return
        pushHistory()
        val (updatedPage, newSelection) = LassoEngine.duplicate(currentPage, lassoSelection)
        val updatedPages = document.pages.toMutableList()
        updatedPages[document.activePageIndex] = updatedPage
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
        lassoSelection = newSelection
    }

    fun deleteLassoSelection() {
        if (lassoSelection.isEmpty) return
        pushHistory()
        val updatedPage = LassoEngine.delete(currentPage, lassoSelection)
        val updatedPages = document.pages.toMutableList()
        updatedPages[document.activePageIndex] = updatedPage
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
        clearLasso()
    }

    var snappedShapeBase by mutableStateOf<ShapeModel?>(null)
        private set
    var snappedShapeHoldOrigin by mutableStateOf<Offset?>(null)
        private set

    fun snapStrokeToShape(shape: ShapeModel, holdOrigin: Offset? = null) {
        val stroke = activeStroke
        if (stroke != null) {
            val curPage = currentPage
            val updatedPages = document.pages.toMutableList()
            updatedPages[document.activePageIndex] = curPage.copy(strokes = curPage.strokes + stroke)
            pushHistory()
            activeStroke = null
        }
        activeShape = shape
        snappedShapeBase = shape
        snappedShapeHoldOrigin = holdOrigin
    }

    fun startShape(canvasPoint: Offset) {
        activeShape = ShapeModel(
            type = currentShapeType,
            startX = canvasPoint.x,
            startY = canvasPoint.y,
            endX = canvasPoint.x,
            endY = canvasPoint.y,
            vertexX = if (currentShapeType == ShapeType.RIGHT_ANGLE) canvasPoint.x else null,
            vertexY = if (currentShapeType == ShapeType.RIGHT_ANGLE) canvasPoint.y else null,
            strokeColorArgb = currentColor.toArgb(),
            strokeWidth = currentStrokeWidth
        )
    }

    var isSnapToSquareCircleActive by mutableStateOf(false)
        private set

    fun updateShape(canvasPoint: Offset): Boolean {
        val shape = activeShape ?: return false
        var targetEndX = canvasPoint.x
        var targetEndY = canvasPoint.y
        var targetVertexX = shape.vertexX
        var targetVertexY = shape.vertexY
        var didSnap = false

        if (shape.type == ShapeType.RECTANGLE || shape.type == ShapeType.ELLIPSE) {
            val width = kotlin.math.abs(targetEndX - shape.startX)
            val height = kotlin.math.abs(targetEndY - shape.startY)
            if (width > 20f && height > 20f) {
                val ratio = width / height
                if (ratio in 0.86f..1.16f) {
                    val side = (width + height) / 2f
                    val signX = if (targetEndX >= shape.startX) 1f else -1f
                    val signY = if (targetEndY >= shape.startY) 1f else -1f
                    targetEndX = shape.startX + signX * side
                    targetEndY = shape.startY + signY * side
                    didSnap = true
                }
            }
        } else if (shape.type == ShapeType.LINE || shape.type == ShapeType.ARROW) {
            val (snappedEnd, snapped) = ShapeMathUtils.snapAngleIfClose(
                start = Offset(shape.startX, shape.startY),
                end = Offset(targetEndX, targetEndY),
                thresholdRad = 0.14f
            )
            if (snapped) {
                targetEndX = snappedEnd.x
                targetEndY = snappedEnd.y
                didSnap = true
            }
        } else if (shape.type == ShapeType.RIGHT_ANGLE) {
            if (shape.vertexX != null && shape.vertexY != null) {
                val vx = shape.vertexX
                val vy = shape.vertexY
                val v1x = vx - shape.startX
                val v1y = vy - shape.startY
                val d1 = kotlin.math.hypot(v1x, v1y)
                if (d1 > 10f) {
                    val u1x = v1x / d1
                    val u1y = v1y / d1
                    val dragX = canvasPoint.x - vx
                    val dragY = canvasPoint.y - vy
                    val dotA = dragX * (-u1y) + dragY * u1x
                    val dotB = dragX * u1y + dragY * (-u1x)
                    val perpDirX = if (dotA >= dotB) -u1y else u1y
                    val perpDirY = if (dotA >= dotB) u1x else -u1x
                    var len2 = kotlin.math.max(20f, kotlin.math.max(dotA, dotB))

                    if (kotlin.math.abs(len2 - d1) / d1 <= 0.15f) {
                        len2 = d1
                        didSnap = true
                    }
                    targetEndX = vx + len2 * perpDirX
                    targetEndY = vy + len2 * perpDirY
                    targetVertexX = vx
                    targetVertexY = vy
                }
            } else {
                targetVertexX = shape.startX
                targetVertexY = targetEndY
                val arm1 = kotlin.math.abs(targetVertexY - shape.startY)
                val arm2 = kotlin.math.abs(targetEndX - targetVertexX)
                if (arm1 > 20f && arm2 > 20f && (arm1 / arm2 in 0.86f..1.16f)) {
                    val side = (arm1 + arm2) / 2f
                    val signY = if (targetEndY >= shape.startY) 1f else -1f
                    val signX = if (targetEndX >= shape.startX) 1f else -1f
                    targetVertexY = shape.startY + signY * side
                    targetEndY = targetVertexY
                    targetEndX = shape.startX + signX * side
                    didSnap = true
                }
            }
        }

        val justSnapped = didSnap && !isSnapToSquareCircleActive
        isSnapToSquareCircleActive = didSnap
        activeShape = shape.copy(
            endX = targetEndX,
            endY = targetEndY,
            vertexX = targetVertexX,
            vertexY = targetVertexY
        )
        return justSnapped
    }

    fun adjustSnappedShape(canvasPoint: Offset): Boolean {
        val base = snappedShapeBase ?: return updateShape(canvasPoint)
        val origin = snappedShapeHoldOrigin ?: return updateShape(canvasPoint)

        when (base.type) {
            ShapeType.LINE, ShapeType.ARROW -> {
                var targetEndX = canvasPoint.x
                var targetEndY = canvasPoint.y
                val (snappedEnd, snapped) = ShapeMathUtils.snapAngleIfClose(
                    start = Offset(base.startX, base.startY),
                    end = Offset(targetEndX, targetEndY),
                    thresholdRad = 0.14f
                )
                if (snapped) {
                    targetEndX = snappedEnd.x
                    targetEndY = snappedEnd.y
                }
                val justSnapped = snapped && !isSnapToSquareCircleActive
                isSnapToSquareCircleActive = snapped
                activeShape = base.copy(endX = targetEndX, endY = targetEndY)
                return justSnapped
            }
            ShapeType.RIGHT_ANGLE -> {
                val vx = base.vertexX ?: base.startX
                val vy = base.vertexY ?: base.endY
                val v1x = vx - base.startX
                val v1y = vy - base.startY
                val d1 = kotlin.math.hypot(v1x, v1y)
                if (d1 > 10f) {
                    val u1x = v1x / d1
                    val u1y = v1y / d1
                    val dragX = canvasPoint.x - vx
                    val dragY = canvasPoint.y - vy
                    val dotA = dragX * (-u1y) + dragY * u1x
                    val dotB = dragX * u1y + dragY * (-u1x)
                    val perpDirX = if (dotA >= dotB) -u1y else u1y
                    val perpDirY = if (dotA >= dotB) u1x else -u1x
                    var len2 = kotlin.math.max(20f, kotlin.math.max(dotA, dotB))
                    var didSnap = false
                    if (kotlin.math.abs(len2 - d1) / d1 <= 0.15f) {
                        len2 = d1
                        didSnap = true
                    }
                    val justSnapped = didSnap && !isSnapToSquareCircleActive
                    isSnapToSquareCircleActive = didSnap
                    activeShape = base.copy(
                        endX = vx + len2 * perpDirX,
                        endY = vy + len2 * perpDirY,
                        vertexX = vx,
                        vertexY = vy
                    )
                    return justSnapped
                }
                return false
            }
            ShapeType.RECTANGLE, ShapeType.ELLIPSE -> {
                val dragDist = ShapeMathUtils.distance(canvasPoint, origin)
                if (dragDist < 16f) {
                    activeShape = base
                    isSnapToSquareCircleActive = false
                    return false
                }
                val cx = (base.startX + base.endX) / 2f
                val cy = (base.startY + base.endY) / 2f
                val origW = kotlin.math.abs(base.endX - base.startX)
                val origH = kotlin.math.abs(base.endY - base.startY)
                val d0 = ShapeMathUtils.distance(origin, Offset(cx, cy)).coerceAtLeast(20f)
                val dCurrent = ShapeMathUtils.distance(canvasPoint, Offset(cx, cy))
                val scale = (dCurrent / d0).coerceIn(0.2f, 5.0f)
                var w = origW * scale
                var h = origH * scale
                var didSnap = false
                if (w > 20f && h > 20f && (w / h in 0.86f..1.16f)) {
                    val side = (w + h) / 2f
                    w = side
                    h = side
                    didSnap = true
                }
                val justSnapped = didSnap && !isSnapToSquareCircleActive
                isSnapToSquareCircleActive = didSnap
                activeShape = base.copy(
                    startX = cx - w / 2f,
                    startY = cy - h / 2f,
                    endX = cx + w / 2f,
                    endY = cy + h / 2f
                )
                return justSnapped
            }
            ShapeType.TRIANGLE -> {
                val dragDist = ShapeMathUtils.distance(canvasPoint, origin)
                if (dragDist < 16f) {
                    activeShape = base
                    return false
                }
                val v0 = Offset(base.startX, base.startY)
                val v1 = Offset(base.vertexX ?: base.startX, base.vertexY ?: base.endY)
                val v2 = Offset(base.endX, base.endY)
                val cx = (v0.x + v1.x + v2.x) / 3f
                val cy = (v0.y + v1.y + v2.y) / 3f
                val d0 = ShapeMathUtils.distance(origin, Offset(cx, cy)).coerceAtLeast(20f)
                val dCurrent = ShapeMathUtils.distance(canvasPoint, Offset(cx, cy))
                val scale = (dCurrent / d0).coerceIn(0.2f, 5.0f)
                val newV0 = Offset(cx + (v0.x - cx) * scale, cy + (v0.y - cy) * scale)
                val newV1 = Offset(cx + (v1.x - cx) * scale, cy + (v1.y - cy) * scale)
                val newV2 = Offset(cx + (v2.x - cx) * scale, cy + (v2.y - cy) * scale)
                activeShape = base.copy(
                    startX = newV0.x,
                    startY = newV0.y,
                    vertexX = newV1.x,
                    vertexY = newV1.y,
                    endX = newV2.x,
                    endY = newV2.y
                )
                return false
            }
        }
    }

    fun finishShape() {
        val shape = activeShape ?: return
        activeShape = null
        snappedShapeBase = null
        snappedShapeHoldOrigin = null
        isSnapToSquareCircleActive = false
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

    // --- Image Operations ---
    fun addImage(localPath: String, width: Float, height: Float, center: Offset) {
        pushHistory()
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val newImage = ImageModel(
            localPath = localPath,
            x = center.x - width / 2f,
            y = center.y - height / 2f,
            width = width,
            height = height,
            keepAspectRatio = true
        )
        updatedPages[document.activePageIndex] = curPage.copy(images = curPage.images + newImage)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
        selectedItem = SelectedItem(newImage.id, SelectableType.IMAGE)
    }

    fun deleteImage(id: String) {
        pushHistory()
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        updatedPages[document.activePageIndex] = curPage.copy(images = curPage.images.filterNot { it.id == id })
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
        if (selectedItem?.id == id) selectedItem = null
    }

    fun moveImage(id: String, deltaCanvas: Offset) {
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val updatedImages = curPage.images.map {
            if (it.id == id) it.copy(x = it.x + deltaCanvas.x, y = it.y + deltaCanvas.y) else it
        }
        updatedPages[document.activePageIndex] = curPage.copy(images = updatedImages)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun resizeImage(id: String, newWidth: Float, newHeight: Float) {
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val updatedImages = curPage.images.map { img ->
            if (img.id == id) {
                val clampedW = newWidth.coerceAtLeast(60f)
                val clampedH = if (img.keepAspectRatio && img.width > 0f) {
                    clampedW * (img.height / img.width)
                } else {
                    newHeight.coerceAtLeast(60f)
                }
                img.copy(width = clampedW, height = clampedH)
            } else img
        }
        updatedPages[document.activePageIndex] = curPage.copy(images = updatedImages)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun toggleImageAspectRatio(id: String) {
        pushHistory()
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val updatedImages = curPage.images.map {
            if (it.id == id) it.copy(keepAspectRatio = !it.keepAspectRatio) else it
        }
        updatedPages[document.activePageIndex] = curPage.copy(images = updatedImages)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun rotateImage(id: String, degrees: Float) {
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val normalized = (degrees % 360f + 360f) % 360f
        val updatedImages = curPage.images.map {
            if (it.id == id) it.copy(rotationDegrees = normalized) else it
        }
        updatedPages[document.activePageIndex] = curPage.copy(images = updatedImages)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun rotateImageBy(id: String, deltaDegrees: Float) {
        val curPage = currentPage
        val img = curPage.images.firstOrNull { it.id == id } ?: return
        rotateImage(id, img.rotationDegrees + deltaDegrees)
    }

    fun cycleImageOpacity(id: String) {
        val curPage = currentPage
        val img = curPage.images.firstOrNull { it.id == id } ?: return
        val nextOpacity = when {
            img.opacity >= 0.95f -> 0.75f
            img.opacity >= 0.70f -> 0.50f
            img.opacity >= 0.45f -> 0.25f
            else -> 1.0f
        }
        setImageOpacity(id, nextOpacity)
    }

    fun setImageOpacity(id: String, opacity: Float) {
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val clamped = opacity.coerceIn(0.1f, 1.0f)
        val updatedImages = curPage.images.map {
            if (it.id == id) it.copy(opacity = clamped) else it
        }
        updatedPages[document.activePageIndex] = curPage.copy(images = updatedImages)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    // --- Audio Note Operations ---
    fun addAudioNote(
        localPath: String,
        durationMs: Long,
        center: Offset,
        title: String = "Voice Note"
    ) {
        pushHistory()
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()

        val existingLabels = document.pages.flatMap { p -> p.audioNotes.map { it.label } }
        val resolvedTitle = if (title == "Voice Note" || title.isBlank()) {
            val regex = Regex("""^Voice Note(?: (\d+))?$""")
            var maxIndex = 0
            for (lbl in existingLabels) {
                val match = regex.matchEntire(lbl.trim())
                if (match != null) {
                    val n = match.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
                    if (n > maxIndex) maxIndex = n
                }
            }
            "Voice Note ${maxIndex + 1}"
        } else {
            title
        }

        val newAudio = AudioNoteModel(
            localPath = localPath,
            x = center.x,
            y = center.y,
            durationMs = durationMs,
            trimStartMs = 0L,
            trimEndMs = durationMs,
            label = resolvedTitle
        )
        updatedPages[document.activePageIndex] = curPage.copy(audioNotes = curPage.audioNotes + newAudio)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
        selectedItem = SelectedItem(newAudio.id, SelectableType.AUDIO_NOTE)
    }

    fun resizeAudioNote(id: String, newWidth: Float, newHeight: Float) {
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val updatedAudio = curPage.audioNotes.map { audio ->
            if (audio.id == id) {
                audio.copy(
                    width = newWidth.coerceIn(100f, 600f),
                    height = newHeight.coerceIn(36f, 160f)
                )
            } else audio
        }
        updatedPages[document.activePageIndex] = curPage.copy(audioNotes = updatedAudio)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun deleteAudioNote(id: String) {
        pushHistory()
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        updatedPages[document.activePageIndex] = curPage.copy(audioNotes = curPage.audioNotes.filterNot { it.id == id })
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
        if (selectedItem?.id == id) selectedItem = null
        if (activeAudioForMenu?.id == id) activeAudioForMenu = null
    }

    fun moveAudioNote(id: String, deltaCanvas: Offset) {
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val updatedAudio = curPage.audioNotes.map {
            if (it.id == id) it.copy(x = it.x + deltaCanvas.x, y = it.y + deltaCanvas.y) else it
        }
        updatedPages[document.activePageIndex] = curPage.copy(audioNotes = updatedAudio)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun updateAudioTrim(id: String, trimStartMs: Long, trimEndMs: Long) {
        pushHistory()
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val updatedAudio = curPage.audioNotes.map {
            if (it.id == id) it.copy(trimStartMs = trimStartMs, trimEndMs = trimEndMs) else it
        }
        updatedPages[document.activePageIndex] = curPage.copy(audioNotes = updatedAudio)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
        if (activeAudioForMenu?.id == id) {
            activeAudioForMenu = activeAudioForMenu?.copy(trimStartMs = trimStartMs, trimEndMs = trimEndMs)
        }
    }

    fun renameAudioNote(id: String, newTitle: String) {
        if (newTitle.isBlank()) return
        pushHistory()
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val updatedAudio = curPage.audioNotes.map {
            if (it.id == id) it.copy(label = newTitle.trim()) else it
        }
        updatedPages[document.activePageIndex] = curPage.copy(audioNotes = updatedAudio)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
        if (activeAudioForMenu?.id == id) {
            activeAudioForMenu = activeAudioForMenu?.copy(label = newTitle.trim())
        }
    }

    // --- Shape Manipulation Operations ---
    fun moveShape(id: String, deltaCanvas: Offset) {
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val updatedShapes = curPage.shapes.map {
            if (it.id == id) {
                it.copy(
                    startX = it.startX + deltaCanvas.x,
                    startY = it.startY + deltaCanvas.y,
                    endX = it.endX + deltaCanvas.x,
                    endY = it.endY + deltaCanvas.y
                )
            } else it
        }
        updatedPages[document.activePageIndex] = curPage.copy(shapes = updatedShapes)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun resizeShape(id: String, newEndX: Float, newEndY: Float) {
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        val updatedShapes = curPage.shapes.map {
            if (it.id == id) it.copy(endX = newEndX, endY = newEndY) else it
        }
        updatedPages[document.activePageIndex] = curPage.copy(shapes = updatedShapes)
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
    }

    fun deleteShape(id: String) {
        pushHistory()
        val curPage = currentPage
        val updatedPages = document.pages.toMutableList()
        updatedPages[document.activePageIndex] = curPage.copy(shapes = curPage.shapes.filterNot { it.id == id })
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
        if (selectedItem?.id == id) selectedItem = null
    }

    // --- Unified Selection & Operations ---
    fun moveSelectedItem(deltaCanvas: Offset) {
        val item = selectedItem ?: return
        when (item.type) {
            SelectableType.TEXT_BOX -> moveTextBox(item.id, deltaCanvas)
            SelectableType.IMAGE -> moveImage(item.id, deltaCanvas)
            SelectableType.SHAPE -> moveShape(item.id, deltaCanvas)
            SelectableType.AUDIO_NOTE -> moveAudioNote(item.id, deltaCanvas)
        }
    }

    fun deleteSelectedItem() {
        val item = selectedItem ?: return
        when (item.type) {
            SelectableType.TEXT_BOX -> deleteTextBox(item.id)
            SelectableType.IMAGE -> deleteImage(item.id)
            SelectableType.SHAPE -> deleteShape(item.id)
            SelectableType.AUDIO_NOTE -> deleteAudioNote(item.id)
        }
        selectedItem = null
    }

    // --- Toolbar Customization ---
    fun hideTool(tool: DrawingTool) {
        palmConfig = palmConfig.copy(hiddenTools = palmConfig.hiddenTools + tool.name)
    }

    fun unhideTool(tool: DrawingTool) {
        palmConfig = palmConfig.copy(hiddenTools = palmConfig.hiddenTools - tool.name)
    }

    fun resetHiddenTools() {
        palmConfig = palmConfig.copy(hiddenTools = emptySet())
    }

    fun isToolHidden(tool: DrawingTool): Boolean {
        return palmConfig.hiddenTools.contains(tool.name)
    }

    fun toggleConstrainDrawingToPage() {
        palmConfig = palmConfig.copy(constrainDrawingToPage = !palmConfig.constrainDrawingToPage)
    }

    fun clearPage() {
        pushHistory()
        val updatedPages = document.pages.toMutableList()
        updatedPages[document.activePageIndex] = currentPage.copy(
            strokes = emptyList(),
            shapes = emptyList(),
            textBoxes = emptyList(),
            images = emptyList(),
            audioNotes = emptyList()
        )
        document = document.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
        selectedItem = null
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
