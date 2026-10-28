package com.scribidoo.app.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.canvas.model.PageModel
import com.scribidoo.app.canvas.model.ShapeModel
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.model.StrokeModel
import com.scribidoo.app.canvas.model.TextBoxModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot

class CanvasExporter(private val context: Context) {

    private val exportsDir: File
        get() = File(context.cacheDir, "exports").apply { mkdirs() }

    /**
     * Exports a page or whole document to a high-resolution PDF file.
     */
    suspend fun exportToPdf(
        document: DocumentModel,
        pageIndex: Int? = null,
        backgroundBitmaps: Map<Int, ImageBitmap> = emptyMap()
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        val pagesToExport = if (pageIndex != null) {
            listOf(document.pages.getOrElse(pageIndex) { document.pages.first() })
        } else {
            document.pages
        }

        pagesToExport.forEachIndexed { idx, page ->
            val pageInfo = PdfDocument.PageInfo.Builder(
                page.width.toInt(),
                page.height.toInt(),
                idx + 1
            ).create()

            val pdfPage = pdfDocument.startPage(pageInfo)
            val canvas = pdfPage.canvas

            // 1. Draw Page Background
            val bgBitmap = backgroundBitmaps[page.index]
            if (bgBitmap != null) {
                canvas.drawBitmap(bgBitmap.asAndroidBitmap(), 0f, 0f, null)
            } else {
                // White paper background
                canvas.drawColor(android.graphics.Color.WHITE)
            }

            // 1b. Draw Imported Images
            renderImagesToAndroidCanvas(canvas, page.images)

            // 2. Draw Vector Strokes
            renderStrokesToAndroidCanvas(canvas, page.strokes)

            // 3. Draw Shapes
            renderShapesToAndroidCanvas(canvas, page.shapes)

            // 4. Draw Text Boxes
            renderTextBoxesToAndroidCanvas(canvas, page.textBoxes)

            pdfDocument.finishPage(pdfPage)
        }

        val sanitizedTitle = document.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFile = File(exportsDir, "${sanitizedTitle}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        outputFile
    }

    /**
     * Exports a single page as a PNG image.
     */
    suspend fun exportToPng(
        page: PageModel,
        backgroundBitmap: ImageBitmap? = null
    ): File = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(
            page.width.toInt(),
            page.height.toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)

        if (backgroundBitmap != null) {
            canvas.drawBitmap(backgroundBitmap.asAndroidBitmap(), 0f, 0f, null)
        } else {
            canvas.drawColor(android.graphics.Color.WHITE)
        }

        renderImagesToAndroidCanvas(canvas, page.images)
        renderStrokesToAndroidCanvas(canvas, page.strokes)
        renderShapesToAndroidCanvas(canvas, page.shapes)
        renderTextBoxesToAndroidCanvas(canvas, page.textBoxes)

        val outputFile = File(exportsDir, "page_${page.index}_${System.currentTimeMillis()}.png")
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        outputFile
    }

    /**
     * Creates a system share intent for an exported file.
     */
    fun shareFile(file: File, mimeType: String = "application/pdf") {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Document via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun renderStrokesToAndroidCanvas(canvas: Canvas, strokes: List<StrokeModel>) {
        val penPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val highlighterPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        }

        for (stroke in strokes) {
            if (stroke.points.size < 2) continue

            val path = Path()
            val points = stroke.points
            path.moveTo(points[0].x, points[0].y)

            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val midX = (p1.x + p2.x) / 2f
                val midY = (p1.y + p2.y) / 2f
                path.quadTo(p1.x, p1.y, midX, midY)
            }
            path.lineTo(points.last().x, points.last().y)

            if (stroke.isHighlighter) {
                highlighterPaint.color = stroke.colorArgb
                highlighterPaint.alpha = (stroke.alpha * 255).toInt()
                highlighterPaint.strokeWidth = stroke.strokeWidth * 3.2f
                canvas.drawPath(path, highlighterPaint)
            } else {
                penPaint.color = stroke.colorArgb
                penPaint.alpha = (stroke.alpha * 255).toInt()
                penPaint.strokeWidth = stroke.strokeWidth
                canvas.drawPath(path, penPaint)
            }
        }
    }

    private fun renderShapesToAndroidCanvas(canvas: Canvas, shapes: List<ShapeModel>) {
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        for (shape in shapes) {
            paint.color = shape.strokeColorArgb
            paint.strokeWidth = shape.strokeWidth

            when (shape.type) {
                ShapeType.LINE -> {
                    canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, paint)
                }
                ShapeType.ARROW -> {
                    canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, paint)
                    val dx = shape.endX - shape.startX
                    val dy = shape.endY - shape.startY
                    val len = hypot(dx, dy)
                    if (len > 10f) {
                        val headLength = (shape.strokeWidth * 4f).coerceIn(16f, 48f)
                        val angle = Math.atan2(dy.toDouble(), dx.toDouble())
                        val arrowAngle = Math.PI / 6.0

                        val x1 = (shape.endX - headLength * Math.cos(angle - arrowAngle)).toFloat()
                        val y1 = (shape.endY - headLength * Math.sin(angle - arrowAngle)).toFloat()
                        val x2 = (shape.endX - headLength * Math.cos(angle + arrowAngle)).toFloat()
                        val y2 = (shape.endY - headLength * Math.sin(angle + arrowAngle)).toFloat()

                        val arrowHead = Path().apply {
                            moveTo(shape.endX, shape.endY)
                            lineTo(x1, y1)
                            moveTo(shape.endX, shape.endY)
                            lineTo(x2, y2)
                        }
                        canvas.drawPath(arrowHead, paint)
                    }
                }
                ShapeType.RECTANGLE -> {
                    val left = minOf(shape.startX, shape.endX)
                    val top = minOf(shape.startY, shape.endY)
                    val right = maxOf(shape.startX, shape.endX)
                    val bottom = maxOf(shape.startY, shape.endY)
                    canvas.drawRect(left, top, right, bottom, paint)
                }
                ShapeType.ELLIPSE -> {
                    val left = minOf(shape.startX, shape.endX)
                    val top = minOf(shape.startY, shape.endY)
                    val right = maxOf(shape.startX, shape.endX)
                    val bottom = maxOf(shape.startY, shape.endY)
                    canvas.drawOval(left, top, right, bottom, paint)
                }
            }
        }
    }

    private fun renderImagesToAndroidCanvas(canvas: Canvas, images: List<com.scribidoo.app.canvas.model.ImageModel>) {
        for (img in images) {
            val file = File(img.localPath)
            if (file.exists()) {
                val bmp = android.graphics.BitmapFactory.decodeFile(img.localPath)
                if (bmp != null) {
                    val destRect = android.graphics.RectF(img.x, img.y, img.x + img.width, img.y + img.height)
                    canvas.drawBitmap(bmp, null, destRect, null)
                }
            }
        }
    }

    private fun renderTextBoxesToAndroidCanvas(canvas: Canvas, textBoxes: List<TextBoxModel>) {
        for (tb in textBoxes) {
            val textPaint = Paint().apply {
                isAntiAlias = true
                color = tb.colorArgb
                textSize = tb.fontSizeSp * 2.2f
                typeface = Typeface.DEFAULT
            }
            canvas.drawText(tb.text, tb.x, tb.y, textPaint)
        }
    }
}
