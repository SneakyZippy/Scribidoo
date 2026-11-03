package com.scribidoo.desktop.pdf

import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.desktop.DesktopExporter
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DesktopPdfExporter {

    fun exportDocumentToPdf(document: DocumentModel): File {
        val pdDoc = PDDocument()
        try {
            for (page in document.pages) {
                val pngBytes = DesktopExporter.renderPageToPngBytes(page)
                val pdImage = PDImageXObject.createFromByteArray(pdDoc, pngBytes, "page_${page.index}")

                val pdPage = PDPage(PDRectangle(page.width, page.height))
                pdDoc.addPage(pdPage)

                PDPageContentStream(pdDoc, pdPage).use { stream ->
                    stream.drawImage(pdImage, 0f, 0f, page.width, page.height)
                }
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val cleanTitle = document.title.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val outputFile = File(DesktopExporter.exportDir, "${cleanTitle}_$timestamp.pdf")
            pdDoc.save(outputFile)
            return outputFile
        } finally {
            pdDoc.close()
        }
    }
}
