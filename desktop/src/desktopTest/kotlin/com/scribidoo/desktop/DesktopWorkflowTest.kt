package com.scribidoo.desktop

import androidx.compose.ui.geometry.Offset
import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.canvas.model.PageModel
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.desktop.media.DesktopImageUtils
import com.scribidoo.desktop.workspace.DesktopDropHandler
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class DesktopWorkflowTest {

    @Test
    fun testScaledDimensionsAspectRatios() {
        // Landscape
        val (w1, h1) = DesktopImageUtils.calculateScaledDimensions(1600f, 900f, maxBound = 800f)
        assertEquals(800f, w1, 0.1f)
        assertEquals(450f, h1, 0.1f)

        // Portrait
        val (w2, h2) = DesktopImageUtils.calculateScaledDimensions(900f, 1800f, maxBound = 600f)
        assertEquals(300f, w2, 0.1f)
        assertEquals(600f, h2, 0.1f)

        // Square
        val (w3, h3) = DesktopImageUtils.calculateScaledDimensions(500f, 500f, maxBound = 600f)
        assertEquals(500f, w3, 0.1f)
        assertEquals(500f, h3, 0.1f)
    }

    @Test
    fun testDropJsonDocumentImportsCorrectly() {
        val state = CanvasState()
        val originalTitle = "Meeting Notes 2026"
        val testDoc = DocumentModel(
            id = "drop-${UUID.randomUUID()}",
            title = originalTitle,
            pages = listOf(PageModel())
        )

        val tempFile = File.createTempFile("note_backup", ".json")
        try {
            tempFile.writeText(Json.encodeToString(DocumentModel.serializer(), testDoc))
            var importedDoc: DocumentModel? = null
            var toastMsg = ""

            DesktopDropHandler.handleDroppedFilesOnCanvas(
                files = listOf(tempFile),
                dropScreenPoint = Offset(100f, 100f),
                state = state,
                onImportDocument = { importedDoc = it },
                showToast = { toastMsg = it }
            )

            assertNotNull(importedDoc)
            assertEquals(originalTitle, importedDoc?.title)
            assertTrue(toastMsg.contains("Imported note"))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testDropImageAddsImageToCanvas() {
        val state = CanvasState()
        val tempImage = File(DesktopImageUtils.imagesDir, "test_drop_${UUID.randomUUID()}.png")
        try {
            // Write a dummy 1x1 png or simple file
            val dummyBytes = byteArrayOf(
                0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
                0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
            )
            tempImage.writeBytes(dummyBytes)

            var toastMsg = ""
            DesktopDropHandler.handleDroppedFilesOnCanvas(
                files = listOf(tempImage),
                dropScreenPoint = Offset(200f, 200f),
                state = state,
                onImportDocument = {},
                showToast = { toastMsg = it }
            )

            assertEquals(1, state.currentPage.images.size)
            assertTrue(toastMsg.contains("Added 1 image"))
        } finally {
            tempImage.delete()
        }
    }

    @Test
    fun testPdfExportAndImportLifecycle() {
        // 1. Create a 2-page note
        val testDoc = DocumentModel(
            id = "pdf-test-${UUID.randomUUID()}",
            title = "Test PDF Document",
            pages = listOf(
                PageModel(index = 0, width = 800f, height = 600f),
                PageModel(index = 1, width = 800f, height = 600f)
            )
        )

        // 2. Export to PDF
        val exportedPdf = com.scribidoo.desktop.pdf.DesktopPdfExporter.exportDocumentToPdf(testDoc)
        try {
            assertTrue(exportedPdf.exists())
            assertTrue(exportedPdf.length() > 100)

            // Validate PDF header magic bytes "%PDF-"
            val header = exportedPdf.inputStream().use { it.readNBytes(5) }
            assertEquals("%PDF-", String(header))

            // 3. Import back via DesktopPdfManager
            val (importedFile, pageCount) = com.scribidoo.desktop.pdf.DesktopPdfManager.importPdf(exportedPdf)
            try {
                assertEquals(2, pageCount)
                assertTrue(importedFile.exists())

                // 4. Render page 0
                val bmp = com.scribidoo.desktop.pdf.DesktopPdfManager.renderPageBitmap(importedFile, 0, dpi = 72f)
                assertNotNull(bmp)
            } finally {
                importedFile.delete()
            }
        } finally {
            exportedPdf.delete()
        }
    }

    @Test
    fun testStylusPressureCurves() {
        // Curve "OFF" always returns 1.0f
        val mockChangeOff = androidx.compose.ui.input.pointer.PointerInputChange(
            id = androidx.compose.ui.input.pointer.PointerId(1),
            uptimeMillis = 100L,
            position = Offset(10f, 10f),
            pressed = true,
            pressure = 0.3f,
            previousUptimeMillis = 90L,
            previousPosition = Offset(10f, 10f),
            previousPressed = true,
            isInitiallyConsumed = false,
            type = androidx.compose.ui.input.pointer.PointerType.Stylus
        )
        val pOff = com.scribidoo.desktop.canvas.DesktopStylusHelper.computeEffectivePressure(mockChangeOff, "OFF")
        assertEquals(1.0f, pOff, 0.001f)

        // Curve "SOFT" increases sensitivity for low pressure
        val pSoft = com.scribidoo.desktop.canvas.DesktopStylusHelper.computeEffectivePressure(mockChangeOff, "SOFT")
        assertTrue("Soft curve should elevate low pressure", pSoft > 0.3f)

        // Curve "FIRM" requires harder pressure
        val pFirm = com.scribidoo.desktop.canvas.DesktopStylusHelper.computeEffectivePressure(mockChangeOff, "FIRM")
        assertTrue("Firm curve should dampen low pressure", pFirm < 0.3f)
    }

    @Test
    fun testTwoPointerGestureTransform() {
        val pointers = mapOf(
            androidx.compose.ui.input.pointer.PointerId(1) to Offset(100f, 100f),
            androidx.compose.ui.input.pointer.PointerId(2) to Offset(200f, 100f)
        )
        val result = com.scribidoo.desktop.canvas.DesktopCanvasGestureHandler.computeTwoPointerSpanAndFocal(pointers)
        assertNotNull(result)
        val (focal, span) = result!!
        assertEquals(150f, focal.x, 0.1f)
        assertEquals(100f, focal.y, 0.1f)
        assertEquals(100f, span, 0.1f)
    }
}
