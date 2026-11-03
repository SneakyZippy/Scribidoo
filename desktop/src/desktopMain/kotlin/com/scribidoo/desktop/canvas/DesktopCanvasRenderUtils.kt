package com.scribidoo.desktop.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.scribidoo.app.canvas.ink.InkPathGenerator
import com.scribidoo.app.canvas.model.ShapeModel
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.model.StrokeModel
import com.scribidoo.app.canvas.viewport.ViewportState
import kotlin.math.hypot

fun DrawScope.drawVectorStroke(stroke: StrokeModel, pathCache: MutableMap<String, Path>?) {
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

fun DrawScope.drawVectorShape(shape: ShapeModel) {
    val start = Offset(shape.startX, shape.startY)
    val end = Offset(shape.endX, shape.endY)
    val color = Color(shape.strokeColorArgb)
    val style = Stroke(width = shape.strokeWidth, cap = StrokeCap.Round)

    when (shape.type) {
        ShapeType.LINE -> drawLine(color, start, end, shape.strokeWidth, StrokeCap.Round)
        ShapeType.ARROW -> drawArrowShape(start, end, color, style, shape.strokeWidth)
        ShapeType.RECTANGLE -> {
            drawRect(color, Offset(minOf(start.x, end.x), minOf(start.y, end.y)), Size(Math.abs(end.x - start.x), Math.abs(end.y - start.y)), style = style)
        }
        ShapeType.ELLIPSE -> {
            drawOval(color, Offset(minOf(start.x, end.x), minOf(start.y, end.y)), Size(Math.abs(end.x - start.x), Math.abs(end.y - start.y)), style = style)
        }
        ShapeType.TRIANGLE -> drawTriangleShape(shape, start, end, color, style)
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

private fun DrawScope.drawArrowShape(start: Offset, end: Offset, color: Color, style: Stroke, strokeWidth: Float) {
    drawLine(color, start, end, strokeWidth, StrokeCap.Round)
    val dx = end.x - start.x
    val dy = end.y - start.y
    if (hypot(dx, dy) > 10f) {
        val headLength = (strokeWidth * 4f).coerceIn(16f, 48f)
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

private fun DrawScope.drawTriangleShape(shape: ShapeModel, start: Offset, end: Offset, color: Color, style: Stroke) {
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

fun DrawScope.drawPaperGuidelines(pageWidth: Float, pageHeight: Float, scale: Float) {
    if (36f * scale < 4f) return
    var y = 80f
    val lineColor = Color(0x18000000)
    while (y < pageHeight - 60f) {
        drawLine(lineColor, Offset(40f, y), Offset(pageWidth - 40f, y), 1f)
        y += 36f
    }
}

fun DrawScope.drawInfiniteGrid(viewport: ViewportState) {
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
