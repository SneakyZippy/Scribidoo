package com.scribidoo.desktop

import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.canvas.model.PageModel
import com.scribidoo.app.util.UniqueNamingUtils
import com.scribidoo.desktop.data.DesktopDocumentStorageManager
import com.scribidoo.desktop.media.DesktopImageUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DesktopParityTest {

    @Test
    fun testUniqueNamingParity() {
        val titles = listOf("Untitled Note 1", "Untitled Note 2")
        val next = UniqueNamingUtils.generateUniqueDocumentTitle(titles)
        assertEquals("Untitled Note 3", next)

        val duplicate = UniqueNamingUtils.generateDuplicateDocumentTitle("Meeting Notes", listOf("Meeting Notes"))
        assertEquals("Meeting Notes (Copy)", duplicate)

        val secondDup = UniqueNamingUtils.generateDuplicateDocumentTitle("Meeting Notes", listOf("Meeting Notes", "Meeting Notes (Copy)"))
        assertEquals("Meeting Notes (Copy 2)", secondDup)
    }

    @Test
    fun testImageDimensionScalingPreservesAspectRatio() {
        val (w, h) = DesktopImageUtils.calculateScaledDimensions(1200f, 600f, maxBound = 600f)
        assertEquals(600f, w, 0.1f)
        assertEquals(300f, h, 0.1f)
    }

    @Test
    fun testDocumentStorageLifecycle() {
        val testDoc = DocumentModel(
            id = "test-${UUID.randomUUID()}",
            title = "Test Parity Note",
            pages = listOf(PageModel())
        )

        // Save
        val file = DesktopDocumentStorageManager.saveDocument(testDoc)
        assertTrue(file.exists())

        // Load
        val loaded = DesktopDocumentStorageManager.loadDocument(testDoc.id)
        assertNotNull(loaded)
        assertEquals(testDoc.title, loaded?.title)

        // Duplicate
        val dup = DesktopDocumentStorageManager.duplicateDocument(testDoc.id)
        assertNotNull(dup)
        assertEquals("Test Parity Note (Copy)", dup?.title)

        // Cleanup
        DesktopDocumentStorageManager.deleteDocument(testDoc.id)
        if (dup != null) DesktopDocumentStorageManager.deleteDocument(dup.id)
    }

    @Test
    fun testDesktopExporterPageToPng() {
        val page = PageModel(width = 800f, height = 600f)
        val exported = DesktopExporter.exportPageToPng(page, "TestExport")
        assertTrue(exported.exists())
        assertTrue(exported.length() > 0)
        exported.delete()
    }
}
