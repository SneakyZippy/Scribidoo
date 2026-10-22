package com.scribidoo.app.canvas.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.Serializable
import java.util.UUID

enum class CanvasLayoutMode {
    PAGED,
    INFINITE
}

enum class DrawingTool {
    PEN,
    HIGHLIGHTER,
    STROKE_ERASER,
    SEGMENT_ERASER,
    SHAPE,
    TEXT,
    LASSO
}

enum class ShapeType {
    LINE,
    ARROW,
    RECTANGLE,
    ELLIPSE
}

@Serializable
data class SerializablePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toOffset() = Offset(x, y)
}

@Serializable
data class StrokeModel(
    val id: String = UUID.randomUUID().toString(),
    val points: List<SerializablePoint>,
    val colorArgb: Int,
    val strokeWidth: Float,
    val alpha: Float = 1.0f,
    val isHighlighter: Boolean = false
) {
    val bounds: Rect by lazy {
        if (points.isEmpty()) Rect.Zero
        else {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            for (p in points) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }
            val pad = strokeWidth / 2f
            Rect(minX - pad, minY - pad, maxX + pad, maxY + pad)
        }
    }
}

@Serializable
data class ShapeModel(
    val id: String = UUID.randomUUID().toString(),
    val type: ShapeType,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val strokeColorArgb: Int,
    val strokeWidth: Float,
    val fillColorArgb: Int? = null
)

@Serializable
data class TextBoxModel(
    val id: String = UUID.randomUUID().toString(),
    val x: Float,
    val y: Float,
    val width: Float = 240f,
    val height: Float = 120f,
    val text: String,
    val fontSizeSp: Float = 16f,
    val colorArgb: Int
)

@Serializable
data class PageModel(
    val id: String = UUID.randomUUID().toString(),
    val index: Int = 0,
    val width: Float = 1240f, // Default A4 portrait standard at 150 DPI
    val height: Float = 1754f,
    val strokes: List<StrokeModel> = emptyList(),
    val shapes: List<ShapeModel> = emptyList(),
    val textBoxes: List<TextBoxModel> = emptyList(),
    val backgroundPdfPage: Int? = null,
    val backgroundUri: String? = null
)

@Serializable
data class DocumentModel(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled Note",
    val layoutMode: CanvasLayoutMode = CanvasLayoutMode.PAGED,
    val pages: List<PageModel> = listOf(PageModel()),
    val activePageIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)
