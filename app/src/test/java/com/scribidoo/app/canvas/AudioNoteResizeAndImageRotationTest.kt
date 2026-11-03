package com.scribidoo.app.canvas

import androidx.compose.ui.geometry.Offset
import com.scribidoo.app.canvas.model.AudioNoteModel
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.canvas.model.ImageModel
import com.scribidoo.app.canvas.model.PageModel
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.app.canvas.ui.getAudioNoteBounds
import com.scribidoo.app.canvas.ui.getImageBounds
import com.scribidoo.app.canvas.ui.isPointInImage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioNoteResizeAndImageRotationTest {

    @Test
    fun testAudioNoteDefaultSizeAndResizing() {
        val state = CanvasState()
        state.addAudioNote("/path/sample.m4a", 5000L, Offset(200f, 200f), "Voice Note")

        val audio = state.currentPage.audioNotes.first()
        assertEquals(140f, audio.width, 0.1f)
        assertEquals(44f, audio.height, 0.1f)
        assertEquals("Voice Note 1", audio.label)

        val bounds = getAudioNoteBounds(audio)
        assertEquals(130f, bounds.left, 0.1f)
        assertEquals(270f, bounds.right, 0.1f)
        assertEquals(178f, bounds.top, 0.1f)
        assertEquals(222f, bounds.bottom, 0.1f)

        // Resize audio note
        state.resizeAudioNote(audio.id, 240f, 60f)
        val resized = state.currentPage.audioNotes.first()
        assertEquals(240f, resized.width, 0.1f)
        assertEquals(60f, resized.height, 0.1f)

        val resizedBounds = getAudioNoteBounds(resized)
        assertEquals(80f, resizedBounds.left, 0.1f)
        assertEquals(320f, resizedBounds.right, 0.1f)
    }

    @Test
    fun testAudioNoteDeduplicationOnSameDocument() {
        val state = CanvasState()
        state.addAudioNote("/path/1.m4a", 1000L, Offset(100f, 100f))
        state.addAudioNote("/path/2.m4a", 2000L, Offset(200f, 200f))
        state.addAudioNote("/path/3.m4a", 3000L, Offset(300f, 300f))

        val labels = state.currentPage.audioNotes.map { it.label }
        assertEquals(listOf("Voice Note 1", "Voice Note 2", "Voice Note 3"), labels)
    }

    @Test
    fun testImageRotationAndNormalization() {
        val state = CanvasState()
        state.addImage("/path/image.jpg", 200f, 100f, Offset(200f, 200f))

        val img = state.currentPage.images.first()
        assertEquals(0f, img.rotationDegrees, 0.01f)

        // Rotate 90 degrees
        state.rotateImageBy(img.id, 90f)
        assertEquals(90f, state.currentPage.images.first().rotationDegrees, 0.01f)

        // Rotate another 300 degrees -> 390 -> 30 degrees normalized
        state.rotateImageBy(img.id, 300f)
        assertEquals(30f, state.currentPage.images.first().rotationDegrees, 0.01f)

        // Negative rotation -> normalized to 0..360
        state.rotateImage(img.id, -45f)
        assertEquals(315f, state.currentPage.images.first().rotationDegrees, 0.01f)
    }

    @Test
    fun testRotatedImageHitTesting() {
        // Image centered at (200, 200), width=100, height=200 (vertical rectangle from x=150..250, y=100..300)
        val img = ImageModel(
            id = "img1",
            localPath = "/path/test.png",
            x = 150f,
            y = 100f,
            width = 100f,
            height = 200f,
            rotationDegrees = 90f // Rotated 90 degrees -> effectively horizontal from x=100..300, y=150..250
        )

        // Point (270, 200) is inside the 90-degree rotated image, but outside the unrotated rect (x max 250)
        assertTrue(isPointInImage(img, Offset(270f, 200f)))

        // Point (200, 120) is inside unrotated rect, but outside the 90-degree rotated rect
        assertFalse(isPointInImage(img, Offset(200f, 120f)))
    }

    @Test
    fun testBackwardsCompatibilityJsonSerialization() {
        // Legacy JSON without width, height, or rotationDegrees
        val legacyJson = """
            {
              "id": "doc1",
              "title": "Legacy Note",
              "layoutMode": "PAGED",
              "pages": [
                {
                  "id": "page1",
                  "index": 0,
                  "width": 1240.0,
                  "height": 1754.0,
                  "strokes": [],
                  "shapes": [],
                  "textBoxes": [],
                  "images": [
                    {
                      "id": "img1",
                      "localPath": "/legacy/photo.png",
                      "x": 50.0,
                      "y": 60.0,
                      "width": 300.0,
                      "height": 200.0,
                      "keepAspectRatio": true
                    }
                  ],
                  "audioNotes": [
                    {
                      "id": "aud1",
                      "localPath": "/legacy/voice.m4a",
                      "x": 100.0,
                      "y": 150.0,
                      "durationMs": 4000,
                      "trimStartMs": 0,
                      "trimEndMs": 4000,
                      "label": "Old Audio"
                    }
                  ]
                }
              ],
              "activePageIndex": 0,
              "createdAt": 1000,
              "modifiedAt": 2000
            }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val doc = json.decodeFromString(DocumentModel.serializer(), legacyJson)

        assertEquals("Legacy Note", doc.title)
        val loadedImg = doc.pages[0].images[0]
        assertEquals(0f, loadedImg.rotationDegrees, 0.01f)
        assertEquals(1.0f, loadedImg.opacity, 0.01f)

        val loadedAudio = doc.pages[0].audioNotes[0]
        assertEquals(140f, loadedAudio.width, 0.1f)
        assertEquals(44f, loadedAudio.height, 0.1f)
        assertEquals("Old Audio", loadedAudio.label)
    }

    @Test
    fun testImageOpacityCycle() {
        val state = CanvasState()
        state.addImage("/path/image.jpg", 200f, 100f, Offset(200f, 200f))
        val imgId = state.currentPage.images.first().id

        assertEquals(1.0f, state.currentPage.images.first().opacity, 0.01f)

        state.cycleImageOpacity(imgId)
        assertEquals(0.75f, state.currentPage.images.first().opacity, 0.01f)

        state.cycleImageOpacity(imgId)
        assertEquals(0.50f, state.currentPage.images.first().opacity, 0.01f)

        state.cycleImageOpacity(imgId)
        assertEquals(0.25f, state.currentPage.images.first().opacity, 0.01f)

        state.cycleImageOpacity(imgId)
        assertEquals(1.0f, state.currentPage.images.first().opacity, 0.01f)
    }

    @Test
    fun testFloatingToolbarAlwaysUprightAndScaleCompensated() {
        val page = PageModel(
            images = listOf(
                ImageModel(id = "img1", localPath = "/p.jpg", x = 100f, y = 100f, width = 200f, height = 100f, rotationDegrees = 90f)
            )
        )
        val sel = com.scribidoo.app.canvas.state.SelectedItem(id = "img1", type = com.scribidoo.app.canvas.state.SelectableType.IMAGE)

        // At zoom 1.0
        val tb1 = com.scribidoo.app.canvas.ui.calculateItemFloatingToolbarBounds(sel, page, 1.0f)
        org.junit.Assert.assertNotNull(tb1)
        assertTrue(tb1!!.deleteBtnRect.top < 50f)

        // At zoom 0.5 (zoomed out) -> button height should be scale-compensated in canvas coordinates
        val tb05 = com.scribidoo.app.canvas.ui.calculateItemFloatingToolbarBounds(sel, page, 0.5f)
        org.junit.Assert.assertNotNull(tb05)
        assertTrue(tb05!!.deleteBtnRect.height > tb1.deleteBtnRect.height)
    }
}
