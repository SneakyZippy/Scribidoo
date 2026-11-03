package com.scribidoo.desktop.pdf

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.scribidoo.desktop.data.DesktopDocumentStorageManager
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File
import java.util.UUID

object DesktopPdfManager {
    val importedPdfsDir: File
        get() = File(DesktopDocumentStorageManager.rootDir, "imported_pdfs").apply { if (!exists()) mkdirs() }

    private val bitmapCache = object : LinkedHashMap<String, ImageBitmap>(12, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > 16
        }
    }

    fun importPdf(sourceFile: File): Pair<File, Int> {
        val targetFile = File(importedPdfsDir, "pdf_${UUID.randomUUID()}.pdf")
        sourceFile.copyTo(targetFile, overwrite = true)
        val doc = PDDocument.load(targetFile)
        val count = doc.numberOfPages
        doc.close()
        return Pair(targetFile, count)
    }

    fun renderPageBitmap(pdfFile: File, pageIndex: Int, dpi: Float = 150f): ImageBitmap? {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return null
        val cacheKey = "${pdfFile.absolutePath}_${pageIndex}_$dpi"
        synchronized(bitmapCache) {
            bitmapCache[cacheKey]?.let { return it }
        }

        return try {
            val doc = PDDocument.load(pdfFile)
            val renderer = PDFRenderer(doc)
            val clampedIndex = pageIndex.coerceIn(0, (doc.numberOfPages - 1).coerceAtLeast(0))
            val bufferedImage = renderer.renderImageWithDPI(clampedIndex, dpi)
            doc.close()

            val composeBitmap = bufferedImage.toComposeImageBitmap()
            synchronized(bitmapCache) {
                bitmapCache[cacheKey] = composeBitmap
            }
            composeBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun clearCache() {
        synchronized(bitmapCache) {
            bitmapCache.clear()
        }
    }
}
