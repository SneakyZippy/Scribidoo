package com.scribidoo.app.canvas.lasso

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.scribidoo.app.canvas.model.ImageModel
import com.scribidoo.app.canvas.model.LassoSelection
import com.scribidoo.app.canvas.model.PageModel
import com.scribidoo.app.canvas.model.SerializablePoint
import com.scribidoo.app.canvas.model.ShapeModel
import com.scribidoo.app.canvas.model.StrokeModel
import com.scribidoo.app.canvas.model.TextBoxModel
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

object LassoEngine {

    fun isPointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            val condition = ((pi.y > point.y) != (pj.y > point.y)) &&
                    (point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y + 0.00001f) + pi.x)
            if (condition) inside = !inside
            j = i
        }
        return inside
    }

    private fun ccw(a: Offset, b: Offset, c: Offset): Boolean {
        return (c.y - a.y) * (b.x - a.x) > (b.y - a.y) * (c.x - a.x)
    }

    fun doSegmentsIntersect(p1: Offset, p2: Offset, p3: Offset, p4: Offset): Boolean {
        return (ccw(p1, p3, p4) != ccw(p2, p3, p4)) && (ccw(p1, p2, p3) != ccw(p1, p2, p4))
    }

    fun polygonBounds(polygon: List<Offset>): Rect {
        if (polygon.isEmpty()) return Rect.Zero
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (p in polygon) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        return Rect(minX, minY, maxX, maxY)
    }

    fun isStrokeSelected(stroke: StrokeModel, polygon: List<Offset>, polyBounds: Rect): Boolean {
        if (stroke.points.isEmpty() || !polyBounds.overlaps(stroke.bounds)) return false
        if (stroke.points.any { isPointInPolygon(it.toOffset(), polygon) }) return true

        val strokePoints = stroke.points
        for (i in 0 until strokePoints.size - 1) {
            val s1 = strokePoints[i].toOffset()
            val s2 = strokePoints[i + 1].toOffset()
            var j = polygon.size - 1
            for (k in polygon.indices) {
                if (doSegmentsIntersect(s1, s2, polygon[j], polygon[k])) return true
                j = k
            }
        }
        return false
    }

    fun isRectSelected(rect: Rect, polygon: List<Offset>, polyBounds: Rect): Boolean {
        if (!polyBounds.overlaps(rect)) return false
        val corners = listOf(
            rect.topLeft,
            rect.topRight,
            rect.bottomRight,
            rect.bottomLeft
        )
        if (corners.any { isPointInPolygon(it, polygon) }) return true

        for (i in 0..3) {
            val r1 = corners[i]
            val r2 = corners[(i + 1) % 4]
            var j = polygon.size - 1
            for (k in polygon.indices) {
                if (doSegmentsIntersect(r1, r2, polygon[j], polygon[k])) return true
                j = k
            }
        }
        return false
    }

    fun findSelection(page: PageModel, polygon: List<Offset>): LassoSelection {
        if (polygon.size < 3) return LassoSelection.Empty
        val polyBounds = polygonBounds(polygon)

        val selectedStrokes = page.strokes.filter { isStrokeSelected(it, polygon, polyBounds) }.map { it.id }.toSet()
        val selectedShapes = page.shapes.filter { s ->
            val rect = Rect(min(s.startX, s.endX), min(s.startY, s.endY), max(s.startX, s.endX), max(s.startY, s.endY))
            isRectSelected(rect, polygon, polyBounds)
        }.map { it.id }.toSet()

        val selectedTexts = page.textBoxes.filter { tb ->
            val rect = Rect(tb.x, tb.y, tb.x + tb.width, tb.y + tb.height)
            isRectSelected(rect, polygon, polyBounds)
        }.map { it.id }.toSet()

        val selectedImages = page.images.filter { img ->
            val rect = Rect(img.x, img.y, img.x + img.width, img.y + img.height)
            isRectSelected(rect, polygon, polyBounds)
        }.map { it.id }.toSet()

        val selection = LassoSelection(
            strokeIds = selectedStrokes,
            shapeIds = selectedShapes,
            textBoxIds = selectedTexts,
            imageIds = selectedImages
        )
        return selection.copy(bounds = computeBounds(page, selection))
    }

    fun computeBounds(page: PageModel, selection: LassoSelection): Rect {
        if (selection.isEmpty) return Rect.Zero
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        fun expand(r: Rect) {
            if (r.left < minX) minX = r.left
            if (r.right > maxX) maxX = r.right
            if (r.top < minY) minY = r.top
            if (r.bottom > maxY) maxY = r.bottom
        }

        page.strokes.filter { it.id in selection.strokeIds }.forEach { expand(it.bounds) }
        page.shapes.filter { it.id in selection.shapeIds }.forEach {
            expand(Rect(min(it.startX, it.endX), min(it.startY, it.endY), max(it.startX, it.endX), max(it.startY, it.endY)))
        }
        page.textBoxes.filter { it.id in selection.textBoxIds }.forEach {
            expand(Rect(it.x, it.y, it.x + it.width, it.y + it.height))
        }
        page.images.filter { it.id in selection.imageIds }.forEach {
            expand(Rect(it.x, it.y, it.x + it.width, it.y + it.height))
        }

        if (minX == Float.MAX_VALUE) return Rect.Zero
        val padding = 6f
        return Rect(minX - padding, minY - padding, maxX + padding, maxY + padding)
    }

    fun translate(page: PageModel, selection: LassoSelection, dx: Float, dy: Float): PageModel {
        if (selection.isEmpty || (dx == 0f && dy == 0f)) return page

        val updatedStrokes = page.strokes.map { stroke ->
            if (stroke.id in selection.strokeIds) {
                stroke.copy(points = stroke.points.map { p ->
                    p.copy(x = p.x + dx, y = p.y + dy)
                })
            } else stroke
        }

        val updatedShapes = page.shapes.map { shape ->
            if (shape.id in selection.shapeIds) {
                shape.copy(
                    startX = shape.startX + dx,
                    startY = shape.startY + dy,
                    endX = shape.endX + dx,
                    endY = shape.endY + dy
                )
            } else shape
        }

        val updatedTexts = page.textBoxes.map { tb ->
            if (tb.id in selection.textBoxIds) tb.copy(x = tb.x + dx, y = tb.y + dy) else tb
        }

        val updatedImages = page.images.map { img ->
            if (img.id in selection.imageIds) img.copy(x = img.x + dx, y = img.y + dy) else img
        }

        return page.copy(
            strokes = updatedStrokes,
            shapes = updatedShapes,
            textBoxes = updatedTexts,
            images = updatedImages
        )
    }

    fun scale(page: PageModel, selection: LassoSelection, scaleFactor: Float, pivot: Offset): PageModel {
        if (selection.isEmpty || scaleFactor <= 0.05f) return page

        val updatedStrokes = page.strokes.map { stroke ->
            if (stroke.id in selection.strokeIds) {
                stroke.copy(
                    points = stroke.points.map { p ->
                        p.copy(
                            x = pivot.x + (p.x - pivot.x) * scaleFactor,
                            y = pivot.y + (p.y - pivot.y) * scaleFactor
                        )
                    },
                    strokeWidth = stroke.strokeWidth * scaleFactor
                )
            } else stroke
        }

        val updatedShapes = page.shapes.map { shape ->
            if (shape.id in selection.shapeIds) {
                shape.copy(
                    startX = pivot.x + (shape.startX - pivot.x) * scaleFactor,
                    startY = pivot.y + (shape.startY - pivot.y) * scaleFactor,
                    endX = pivot.x + (shape.endX - pivot.x) * scaleFactor,
                    endY = pivot.y + (shape.endY - pivot.y) * scaleFactor,
                    strokeWidth = shape.strokeWidth * scaleFactor
                )
            } else shape
        }

        val updatedTexts = page.textBoxes.map { tb ->
            if (tb.id in selection.textBoxIds) {
                tb.copy(
                    x = pivot.x + (tb.x - pivot.x) * scaleFactor,
                    y = pivot.y + (tb.y - pivot.y) * scaleFactor,
                    width = tb.width * scaleFactor,
                    height = tb.height * scaleFactor,
                    fontSizeSp = tb.fontSizeSp * scaleFactor
                )
            } else tb
        }

        val updatedImages = page.images.map { img ->
            if (img.id in selection.imageIds) {
                img.copy(
                    x = pivot.x + (img.x - pivot.x) * scaleFactor,
                    y = pivot.y + (img.y - pivot.y) * scaleFactor,
                    width = img.width * scaleFactor,
                    height = img.height * scaleFactor
                )
            } else img
        }

        return page.copy(
            strokes = updatedStrokes,
            shapes = updatedShapes,
            textBoxes = updatedTexts,
            images = updatedImages
        )
    }

    fun recolorStrokes(page: PageModel, strokeIds: Set<String>, colorArgb: Int): PageModel {
        if (strokeIds.isEmpty()) return page
        return page.copy(strokes = page.strokes.map { stroke ->
            if (stroke.id in strokeIds) stroke.copy(colorArgb = colorArgb) else stroke
        })
    }

    fun delete(page: PageModel, selection: LassoSelection): PageModel {
        if (selection.isEmpty) return page
        return page.copy(
            strokes = page.strokes.filterNot { it.id in selection.strokeIds },
            shapes = page.shapes.filterNot { it.id in selection.shapeIds },
            textBoxes = page.textBoxes.filterNot { it.id in selection.textBoxIds },
            images = page.images.filterNot { it.id in selection.imageIds }
        )
    }

    fun duplicate(
        page: PageModel,
        selection: LassoSelection,
        offset: Offset = Offset(40f, 40f)
    ): Pair<PageModel, LassoSelection> {
        if (selection.isEmpty) return Pair(page, selection)

        val newStrokes = page.strokes.filter { it.id in selection.strokeIds }.map { stroke ->
            stroke.copy(
                id = UUID.randomUUID().toString(),
                points = stroke.points.map { p -> p.copy(x = p.x + offset.x, y = p.y + offset.y) }
            )
        }

        val newShapes = page.shapes.filter { it.id in selection.shapeIds }.map { shape ->
            shape.copy(
                id = UUID.randomUUID().toString(),
                startX = shape.startX + offset.x,
                startY = shape.startY + offset.y,
                endX = shape.endX + offset.x,
                endY = shape.endY + offset.y
            )
        }

        val newTexts = page.textBoxes.filter { it.id in selection.textBoxIds }.map { tb ->
            tb.copy(id = UUID.randomUUID().toString(), x = tb.x + offset.x, y = tb.y + offset.y)
        }

        val newImages = page.images.filter { it.id in selection.imageIds }.map { img ->
            img.copy(id = UUID.randomUUID().toString(), x = img.x + offset.x, y = img.y + offset.y)
        }

        val newSelection = LassoSelection(
            strokeIds = newStrokes.map { it.id }.toSet(),
            shapeIds = newShapes.map { it.id }.toSet(),
            textBoxIds = newTexts.map { it.id }.toSet(),
            imageIds = newImages.map { it.id }.toSet()
        )

        val updatedPage = page.copy(
            strokes = page.strokes + newStrokes,
            shapes = page.shapes + newShapes,
            textBoxes = page.textBoxes + newTexts,
            images = page.images + newImages
        )

        return Pair(updatedPage, newSelection.copy(bounds = computeBounds(updatedPage, newSelection)))
    }
}
