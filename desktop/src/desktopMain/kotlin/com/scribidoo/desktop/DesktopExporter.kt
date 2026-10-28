package com.scribidoo.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asSkiaPath
import com.scribidoo.app.canvas.ink.InkPathGenerator
import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.canvas.model.PageModel
import com.scribidoo.app.canvas.model.ShapeModel
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.model.StrokeModel
import com.scribidoo.app.canvas.model.TextBoxModel
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.Typeface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object DesktopExporter {

    private val exportDir: File by lazy {
        val userHome = System.getProperty("user.home")
        val picturesDir = File(userHome, "Pictures")
        val baseDir = if (picturesDir.exists()) picturesDir else File(userHome, "Documents")
        val dir = File(baseDir, "Scribidoo")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    fun exportPageToPng(page: PageModel, docTitle: String = "Scribidoo"): File {
        val width = page.width.toInt().coerceAtLeast(100)
        val height = page.height.toInt().coerceAtLeast(100)

        val imageInfo = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
        val bitmap = Bitmap()
        bitmap.allocPixels(imageInfo)
        val canvas = Canvas(bitmap)

        // 1. Paper Background
        canvas.clear(Color.WHITE)

        // 2. Ruled guidelines
        val guidePaint = Paint().apply {
            color = Color.makeARGB(35, 37, 99, 235)
            mode = PaintMode.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        var y = 80f
        while (y < height - 60f) {
            canvas.drawLine(60f, y, width - 60f, y, guidePaint)
            y += 36f
        }

        // 3. Vector Strokes
        for (stroke in page.strokes) {
            if (stroke.points.size < 2) continue
            val composePath = InkPathGenerator.generateSmoothPath(stroke.points)
            val skiaPath = composePath.asSkiaPath()

            val strokePaint = Paint().apply {
                color = stroke.colorArgb
                mode = PaintMode.STROKE
                strokeWidth = stroke.strokeWidth
                strokeCap = PaintStrokeCap.ROUND
                strokeJoin = PaintStrokeJoin.ROUND
                isAntiAlias = true
                if (stroke.isHighlighter) {
                    alpha = (stroke.alpha * 140).toInt().coerceIn(0, 255)
                }
            }
            canvas.drawPath(skiaPath, strokePaint)
        }

        // 4. Shapes
        for (shape in page.shapes) {
            val shapePaint = Paint().apply {
                color = shape.strokeColorArgb
                mode = PaintMode.STROKE
                strokeWidth = shape.strokeWidth
                strokeCap = PaintStrokeCap.ROUND
                strokeJoin = PaintStrokeJoin.ROUND
                isAntiAlias = true
            }
            when (shape.type) {
                ShapeType.LINE -> {
                    canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, shapePaint)
                }
                ShapeType.RECTANGLE -> {
                    val l = minOf(shape.startX, shape.endX)
                    val t = minOf(shape.startY, shape.endY)
                    val r = maxOf(shape.startX, shape.endX)
                    val b = maxOf(shape.startY, shape.endY)
                    canvas.drawRect(org.jetbrains.skia.Rect.makeLTRB(l, t, r, b), shapePaint)
                }
                ShapeType.ELLIPSE -> {
                    val l = minOf(shape.startX, shape.endX)
                    val t = minOf(shape.startY, shape.endY)
                    val r = maxOf(shape.startX, shape.endX)
                    val b = maxOf(shape.startY, shape.endY)
                    canvas.drawOval(org.jetbrains.skia.Rect.makeLTRB(l, t, r, b), shapePaint)
                }
                ShapeType.ARROW -> {
                    canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, shapePaint)
                    val angle = atan2(shape.endY - shape.startY, shape.endX - shape.startX)
                    val arrowHeadSize = shape.strokeWidth * 3.5f + 14f
                    val a1 = angle - Math.PI / 6.0
                    val a2 = angle + Math.PI / 6.0
                    val x1 = (shape.endX - arrowHeadSize * cos(a1)).toFloat()
                    val y1 = (shape.endY - arrowHeadSize * sin(a1)).toFloat()
                    val x2 = (shape.endX - arrowHeadSize * cos(a2)).toFloat()
                    val y2 = (shape.endY - arrowHeadSize * sin(a2)).toFloat()
                    canvas.drawLine(shape.endX, shape.endY, x1, y1, shapePaint)
                    canvas.drawLine(shape.endX, shape.endY, x2, y2, shapePaint)
                }
            }
        }

        // 5. Text Boxes
        val font = Font(Typeface.makeDefault(), 18f)
        for (tb in page.textBoxes) {
            val textPaint = Paint().apply {
                color = tb.colorArgb
                isAntiAlias = true
            }
            font.size = tb.fontSizeSp * 1.25f
            val lines = tb.text.split("\n")
            var lineY = tb.y
            for (line in lines) {
                canvas.drawString(line, tb.x, lineY, font, textPaint)
                lineY += font.size * 1.3f
            }
        }

        // Encode to PNG file
        val image = Image.makeFromBitmap(bitmap)
        val data = image.encodeToData(EncodedImageFormat.PNG, 100)
            ?: throw IllegalStateException("Failed to encode image to PNG format")

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanTitle = docTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val outputFile = File(exportDir, "${cleanTitle}_p${page.index + 1}_$timestamp.png")
        outputFile.writeBytes(data.bytes)
        return outputFile
    }

    fun exportDocumentToPngs(document: DocumentModel): List<File> {
        return document.pages.map { page ->
            exportPageToPng(page, document.title)
        }
    }
}
