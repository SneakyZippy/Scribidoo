package com.scribidoo.app.canvas

import androidx.compose.ui.geometry.Offset
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.model.ShapeModel
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.app.canvas.state.SelectableType
import com.scribidoo.app.canvas.state.SelectedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasMediaAndToolbarTest {

    @Test
    fun testAddAndMoveImage() {
        val state = CanvasState()
        assertEquals(0, state.currentPage.images.size)

        state.addImage(
            localPath = "/data/local/img1.png",
            width = 400f,
            height = 300f,
            center = Offset(500f, 600f)
        )

        assertEquals(1, state.currentPage.images.size)
        val img = state.currentPage.images.first()
        assertEquals(300f, img.x, 0.01f)
        assertEquals(450f, img.y, 0.01f)
        assertEquals(400f, img.width, 0.01f)
        assertEquals(300f, img.height, 0.01f)
        assertTrue(img.keepAspectRatio)
        assertNotNull(state.selectedItem)
        assertEquals(SelectableType.IMAGE, state.selectedItem?.type)

        // Move image via unified moveSelectedItem
        state.moveSelectedItem(Offset(50f, 75f))
        val moved = state.currentPage.images.first()
        assertEquals(350f, moved.x, 0.01f)
        assertEquals(525f, moved.y, 0.01f)

        // Resize image with aspect ratio kept (4:3 ratio)
        state.resizeImage(img.id, 600f, 400f)
        val resized = state.currentPage.images.first()
        assertEquals(600f, resized.width, 0.01f)
        assertEquals(450f, resized.height, 0.01f) // 600 * 300 / 400 = 450

        // Delete image
        state.deleteSelectedItem()
        assertEquals(0, state.currentPage.images.size)
        assertNull(state.selectedItem)
    }

    @Test
    fun testAudioNoteLifecycleAndTrimming() {
        val state = CanvasState()
        assertEquals(0, state.currentPage.audioNotes.size)

        state.addAudioNote(
            localPath = "/data/local/voice1.m4a",
            durationMs = 20000L,
            center = Offset(400f, 300f),
            title = "Diagram Explanation"
        )

        assertEquals(1, state.currentPage.audioNotes.size)
        val note = state.currentPage.audioNotes.first()
        assertEquals("Diagram Explanation", note.label)
        assertEquals(20000L, note.durationMs)
        assertEquals(0L, note.trimStartMs)
        assertEquals(20000L, note.trimEndMs)

        // Rename audio note
        state.renameAudioNote(note.id, "Updated Explanation")
        assertEquals("Updated Explanation", state.currentPage.audioNotes.first().label)

        // Non-destructive trim update
        state.updateAudioTrim(note.id, 2000L, 15000L)
        val trimmed = state.currentPage.audioNotes.first()
        assertEquals(2000L, trimmed.trimStartMs)
        assertEquals(15000L, trimmed.trimEndMs)

        // Move audio note
        state.moveSelectedItem(Offset(100f, -50f))
        val moved = state.currentPage.audioNotes.first()
        assertEquals(500f, moved.x, 0.01f)
        assertEquals(250f, moved.y, 0.01f)

        // Undo & Redo
        state.undo() // undo move
        assertEquals(400f, state.currentPage.audioNotes.first().x, 0.01f)
        state.redo() // redo move
        assertEquals(500f, state.currentPage.audioNotes.first().x, 0.01f)
    }

    @Test
    fun testShapeMovementAndSelection() {
        val state = CanvasState()
        val shape = ShapeModel(
            type = ShapeType.RECTANGLE,
            startX = 100f,
            startY = 100f,
            endX = 300f,
            endY = 250f,
            strokeColorArgb = 0xFF000000.toInt(),
            strokeWidth = 4f
        )
        state.loadDocument(state.document.copy(
            pages = listOf(state.currentPage.copy(shapes = listOf(shape)))
        ))
        assertEquals(1, state.currentPage.shapes.size)

        // Select shape
        state.selectedItem = SelectedItem(shape.id, SelectableType.SHAPE)
        state.moveSelectedItem(Offset(40f, 60f))

        val moved = state.currentPage.shapes.first()
        assertEquals(140f, moved.startX, 0.01f)
        assertEquals(160f, moved.startY, 0.01f)
        assertEquals(340f, moved.endX, 0.01f)
        assertEquals(310f, moved.endY, 0.01f)
    }

    @Test
    fun testToolbarHidingAndRestoration() {
        val state = CanvasState()
        assertFalse(state.isToolHidden(DrawingTool.HAND))
        assertFalse(state.isToolHidden(DrawingTool.IMAGE))

        // Hide Hand tool and Image tool
        state.hideTool(DrawingTool.HAND)
        state.hideTool(DrawingTool.IMAGE)
        assertTrue(state.isToolHidden(DrawingTool.HAND))
        assertTrue(state.isToolHidden(DrawingTool.IMAGE))
        assertFalse(state.isToolHidden(DrawingTool.PEN))
        assertEquals(2, state.palmConfig.hiddenTools.size)

        // Unhide Hand tool
        state.unhideTool(DrawingTool.HAND)
        assertFalse(state.isToolHidden(DrawingTool.HAND))
        assertTrue(state.isToolHidden(DrawingTool.IMAGE))

        // Reset all hidden tools
        state.resetHiddenTools()
        assertTrue(state.palmConfig.hiddenTools.isEmpty())
        assertFalse(state.isToolHidden(DrawingTool.IMAGE))
    }

    @Test
    fun testPaperConstraintToggleAndAddNewPage() {
        val state = CanvasState()
        assertFalse(state.palmConfig.constrainDrawingToPage)

        state.toggleConstrainDrawingToPage()
        assertTrue(state.palmConfig.constrainDrawingToPage)

        state.toggleConstrainDrawingToPage()
        assertFalse(state.palmConfig.constrainDrawingToPage)

        // Test Add New Page
        assertEquals(1, state.document.pages.size)
        assertEquals(0, state.document.activePageIndex)

        state.addNewPage()
        assertEquals(2, state.document.pages.size)
        assertEquals(1, state.document.activePageIndex)

        state.prevPage()
        assertEquals(0, state.document.activePageIndex)
        state.nextPage()
        assertEquals(1, state.document.activePageIndex)
    }
}
