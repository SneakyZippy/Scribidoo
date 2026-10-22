package com.scribidoo.app.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.scribidoo.app.canvas.viewport.ViewportState
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewportStateTest {

    @Test
    fun testScreenToCanvasTransform() {
        val viewport = ViewportState(
            initialPan = Offset(100f, 200f),
            initialScale = 2.0f
        )

        // screenPoint = (300, 400)
        // canvasPoint = (screenPoint - pan) / scale = ( (300-100)/2, (400-200)/2 ) = (100, 100)
        val canvasPt = viewport.screenToCanvas(Offset(300f, 400f))
        assertEquals(100f, canvasPt.x, 0.001f)
        assertEquals(100f, canvasPt.y, 0.001f)

        // Reverse check: canvasToScreen(100, 100) -> (100*2 + 100, 100*2 + 200) = (300, 400)
        val screenPt = viewport.canvasToScreen(canvasPt)
        assertEquals(300f, screenPt.x, 0.001f)
        assertEquals(400f, screenPt.y, 0.001f)
    }

    @Test
    fun testZoomByFocalPointInvariance() {
        val viewport = ViewportState(
            initialPan = Offset(50f, 50f),
            initialScale = 1.0f
        )
        val focalPoint = Offset(200f, 200f)

        val canvasPtBefore = viewport.screenToCanvas(focalPoint)
        viewport.zoomBy(2.0f, focalPoint)
        val canvasPtAfter = viewport.screenToCanvas(focalPoint)

        // Point on canvas under focal point must not change during zoom
        assertEquals(canvasPtBefore.x, canvasPtAfter.x, 0.001f)
        assertEquals(canvasPtBefore.y, canvasPtAfter.y, 0.001f)
        assertEquals(2.0f, viewport.scale, 0.001f)
    }

    @Test
    fun testCenterAndFit() {
        val viewport = ViewportState()
        viewport.containerSize = Size(1000f, 2000f)

        val pageWidth = 500f
        val pageHeight = 1000f

        viewport.centerAndFit(pageWidth, pageHeight, padding = 0f)

        // Scale should be 2.0f
        assertEquals(2.0f, viewport.scale, 0.001f)
        assertEquals(0f, viewport.panX, 0.001f)
        assertEquals(0f, viewport.panY, 0.001f)
    }
}
