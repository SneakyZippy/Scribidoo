package com.scribidoo.desktop

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.asSkiaPath
import com.scribidoo.app.canvas.ink.InkPathGenerator
import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.canvas.model.PageModel
import com.scribidoo.app.canvas.model.ShapeModel
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.desktop.pdf.DesktopPdfManager
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
import org.jetbrains.skia.Rect
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object DesktopExporter {
    val exportDir: File by lazy {
        val userHome = System.getProperty("user.home")
        val picturesDir = File(userHome, "Pictures")
        val baseDir = if (picturesDir.exists()) picturesDir else File(userHome, "Documents")
        val dir = File(baseDir, "Scribidoo")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    fun renderPageToPngBytes(page: PageModel): ByteArray {
        val width = page.width.toInt().coerceAtLeast(100)
        val height = page.height.toInt().coerceAtLeast(100)

        val imageInfo = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
        val bitmap = Bitmap()
        bitmap.allocPixels(imageInfo)
        val canvas = Canvas(bitmap)

        // 1. Paper / Background Layer
        canvas.clear(Color.WHITE)

        val bgUri = page.backgroundUri
        if (!bgUri.isNullOrBlank()) {
            val bgFile = File(bgUri)
            if (bgFile.exists()) {
                try {
                    if (bgFile.extension.lowercase() == "pdf") {
                        val bmp = DesktopPdfManager.renderPageBitmap(bgFile, page.backgroundPdfPage ?: page.index, dpi = 150f)
                        if (bmp != null) {
                            val skiaBmp = bmp.asSkiaBitmap()
                            val skiaImg = Image.makeFromBitmap(skiaBmp)
                            canvas.drawImageRect(skiaImg, Rect.makeXYWH(0f, 0f, page.width, page.height))
                        }
                    } else {
                        val skiaImg = Image.makeFromEncoded(bgFile.readBytes())
                        canvas.drawImageRect(skiaImg, Rect.makeXYWH(0f, 0f, page.width, page.height))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            // Guidelines
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
        }

        // 2. Images & Stickers
        for (img in page.images) {
            val file = File(img.localPath)
            if (file.exists()) {
                try {
                    val skiaImg = Image.makeFromEncoded(file.readBytes())
                    val dstRect = Rect.makeXYWH(img.x, img.y, img.width, img.height)
                    if (img.rotationDegrees != 0f) {
                        canvas.save()
                        canvas.rotate(img.rotationDegrees, img.x + img.width / 2f, img.y + img.height / 2f)
                        canvas.drawImageRect(skiaImg, dstRect)
                        canvas.restore()
                    } else {
                        canvas.drawImageRect(skiaImg, dstRect)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 3. Vector Strokes
        for (stroke in page.strokes) {
            if (stroke.points.size < 2) continue
            val composePath = InkPathGenerator.createSmoothPath(stroke.points)
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
            drawSkiaShape(canvas, shape, shapePaint)
        }

        // 5. Text Boxes
        val font = Font().apply { size = 18f }
        for (tb in page.textBoxes) {
            val textPaint = Paint().apply { color = tb.colorArgb; isAntiAlias = true }
            font.size = tb.fontSizeSp * 1.25f
            var lineY = tb.y
            for (line in tb.text.split("\n")) {
                canvas.drawString(line, tb.x, lineY, font, textPaint)
                lineY += font.size * 1.3f
            }
        }

        val image = Image.makeFromBitmap(bitmap)
        val data = image.encodeToData(EncodedImageFormat.PNG, 100)
            ?: throw IllegalStateException("Failed to encode image to PNG format")
        return data.bytes
    }

    fun exportPageToPng(page: PageModel, docTitle: String = "Scribidoo"): File {
        val bytes = renderPageToPngBytes(page)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanTitle = docTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val outputFile = File(exportDir, "${cleanTitle}_p${page.index + 1}_$timestamp.png")
        outputFile.writeBytes(bytes)
        return outputFile
    }

    private fun drawSkiaShape(canvas: Canvas, shape: ShapeModel, paint: Paint) {
        when (shape.type) {
            ShapeType.LINE -> canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, paint)
            ShapeType.RECTANGLE -> {
                val l = minOf(shape.startX, shape.endX)
                val t = minOf(shape.startY, shape.endY)
                val r = maxOf(shape.startX, shape.endX)
                val b = maxOf(shape.startY, shape.endY)
                canvas.drawRect(Rect.makeLTRB(l, t, r, b), paint)
            }
            ShapeType.ELLIPSE -> {
                val l = minOf(shape.startX, shape.endX)
                val t = minOf(shape.startY, shape.endY)
                val r = maxOf(shape.startX, shape.endX)
                val b = maxOf(shape.startY, shape.endY)
                canvas.drawOval(Rect.makeLTRB(l, t, r, b), paint)
            }
            ShapeType.ARROW -> {
                canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, paint)
                val angle = atan2(shape.endY - shape.startY, shape.endX - shape.startX)
                val head = shape.strokeWidth * 3.5f + 14f
                val x1 = (shape.endX - head * cos(angle - Math.PI / 6.0)).toFloat()
                val y1 = (shape.endY - head * sin(angle - Math.PI / 6.0)).toFloat()
                val x2 = (shape.endX - head * cos(angle + Math.PI / 6.0)).toFloat()
                val y2 = (shape.endY - head * sin(angle + Math.PI / 6.0)).toFloat()
                canvas.drawLine(shape.endX, shape.endY, x1, y1, paint)
                canvas.drawLine(shape.endX, shape.endY, x2, y2, paint)
            }
            ShapeType.TRIANGLE -> {
                val path = org.jetbrains.skia.Path().apply {
                    val l = minOf(shape.startX, shape.endX)
                    val r = maxOf(shape.startX, shape.endX)
                    val b = maxOf(shape.startY, shape.endY)
                    moveTo((l + r) / 2f, minOf(shape.startY, shape.endY))
                    lineTo(r, b); lineTo(l, b); closePath()
                }
                canvas.drawPath(path, paint)
            }
            ShapeType.RIGHT_ANGLE -> {
                val path = org.jetbrains.skia.Path().apply {
                    moveTo(shape.startX, shape.startY)
                    lineTo(shape.startX, shape.endY)
                    lineTo(shape.endX, shape.endY)
                }
                canvas.drawPath(path, paint)
            }
        }
    }

    fun exportDocumentToPngs(document: DocumentModel): List<File> {
        return document.pages.map { page -> exportPageToPng(page, document.title) }
    }
}
