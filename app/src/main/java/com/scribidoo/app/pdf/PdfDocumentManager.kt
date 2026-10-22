package com.scribidoo.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfDocumentManager(private val context: Context) {

    /**
     * Copies an external content URI into the internal storage cache for stable access.
     */
    suspend fun importPdf(uri: Uri): Pair<File, Int> = withContext(Dispatchers.IO) {
        val pdfsDir = File(context.filesDir, "imported_pdfs").apply { mkdirs() }
        val targetFile = File(pdfsDir, "doc_${System.currentTimeMillis()}.pdf")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Cannot open content URI $uri")

        val pfd = ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val pageCount = renderer.pageCount
        renderer.close()
        pfd.close()

        Pair(targetFile, pageCount)
    }

    /**
     * Renders a specific PDF page to an Android Bitmap at the target resolution.
     */
    suspend fun renderPageBitmap(
        pdfFile: File,
        pageIndex: Int,
        targetWidth: Int = 1240,
        targetHeight: Int = 1754
    ): Bitmap = withContext(Dispatchers.IO) {
        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val page = renderer.openPage(pageIndex.coerceIn(0, renderer.pageCount - 1))

        // Calculate aspect ratio
        val pageWidth = page.width.toFloat()
        val pageHeight = page.height.toFloat()
        val scale = minOf(targetWidth / pageWidth, targetHeight / pageHeight)
        val bmpWidth = (pageWidth * scale).toInt().coerceAtLeast(1)
        val bmpHeight = (pageHeight * scale).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        // White background for transparent PDF pages
        bitmap.eraseColor(android.graphics.Color.WHITE)

        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        pfd.close()

        bitmap
    }
}
