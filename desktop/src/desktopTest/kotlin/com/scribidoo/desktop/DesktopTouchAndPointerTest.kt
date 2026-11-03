package com.scribidoo.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.desktop.canvas.DesktopCanvasGestureHandler
import com.scribidoo.desktop.canvas.DesktopCanvasPointerState
import com.scribidoo.desktop.media.DesktopAudioPlayer
import com.scribidoo.desktop.workspace.DesktopTouchInputFixer
import java.awt.Canvas
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalComposeUiApi::class)
class DesktopTouchAndPointerTest {

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private fun createPointerState(state: CanvasState): DesktopCanvasPointerState {
        state.viewportState.containerSize = Size(1280f, 800f)
        val audioPlayer = DesktopAudioPlayer(testScope)
        return DesktopCanvasPointerState(state, audioPlayer, testScope, density = 1f)
    }

    private fun makeEvent(changes: List<PointerInputChange>): PointerEvent {
        return PointerEvent(changes = changes)
    }

    private fun makeChange(
        id: Long,
        position: Offset,
        pressed: Boolean,
        previousPosition: Offset = position,
        previousPressed: Boolean = pressed,
        pointerType: PointerType = PointerType.Touch,
        scrollDelta: Offset = Offset.Zero
    ): PointerInputChange {
        return PointerInputChange(
            id = PointerId(id),
            uptimeMillis = System.currentTimeMillis(),
            position = position,
            pressed = pressed,
            pressure = 0.5f,
            previousUptimeMillis = System.currentTimeMillis() - 16L,
            previousPosition = previousPosition,
            previousPressed = previousPressed,
            isInitiallyConsumed = false,
            type = pointerType,
            scrollDelta = scrollDelta
        )
    }

    @Test
    fun testHoverMoveDoesNotEraseOrDraw() {
        val state = CanvasState()
        val pointerState = createPointerState(state)

        // Add an initial stroke
        state.startStroke(Offset(100f, 100f))
        state.appendStrokePoint(Offset(200f, 200f))
        state.finishStroke()
        assertEquals(1, state.currentPage.strokes.size)

        // Switch to Stroke Eraser and send unpressed hover Move over the stroke
        state.currentTool = DrawingTool.STROKE_ERASER
        val hoverChange = makeChange(1L, Offset(150f, 150f), pressed = false, previousPressed = false, pointerType = PointerType.Mouse)
        pointerState.handlePointerEvent(makeEvent(listOf(hoverChange)))

        // Stroke must NOT be erased by unpressed hover movement
        assertEquals(1, state.currentPage.strokes.size)
        assertFalse(hoverChange.isConsumed)
    }

    @Test
    fun testGhostPointerDoesNotLockIntoMultiTouch() {
        val state = CanvasState()
        val pointerState = createPointerState(state)
        state.currentTool = DrawingTool.PEN

        // Simulate Pointer 1 pressing and getting cancelled by OS without a Release event
        val p1Press = makeChange(1L, Offset(100f, 100f), pressed = true, previousPressed = false, pointerType = PointerType.Touch)
        pointerState.handlePointerEvent(makeEvent(listOf(p1Press)))
        assertEquals(1, pointerState.activePointers.size)

        // Next touch comes in as Pointer 2 ONLY (Pointer 1 is no longer in event.changes)
        val p2Press = makeChange(2L, Offset(300f, 300f), pressed = true, previousPressed = false, pointerType = PointerType.Touch)
        pointerState.handlePointerEvent(makeEvent(listOf(p2Press)))

        // Must NOT trigger multi-touch; activePointers should only contain Pointer 2
        assertFalse("Cancelled pointer must not cause ghost multi-touch", pointerState.isMultiTouchGesture)
        assertEquals(1, pointerState.activePointers.size)

        val p2Move = makeChange(2L, Offset(350f, 350f), pressed = true, previousPosition = Offset(300f, 300f), pointerType = PointerType.Touch)
        pointerState.handlePointerEvent(makeEvent(listOf(p2Move)))

        val p2Release = makeChange(2L, Offset(350f, 350f), pressed = false, previousPressed = true, pointerType = PointerType.Touch)
        pointerState.handlePointerEvent(makeEvent(listOf(p2Release)))

        // Stroke should have been completed normally
        assertEquals(1, state.currentPage.strokes.size)
    }

    @Test
    fun testRapidDrawingTapsDoNotTriggerDoubleTapZoom() {
        val state = CanvasState()
        val pointerState = createPointerState(state)
        state.currentTool = DrawingTool.PEN
        val initialScale = state.viewportState.scale

        // Two rapid taps at the same spot while PEN is active
        val tap1Press = makeChange(10L, Offset(200f, 200f), pressed = true, previousPressed = false, pointerType = PointerType.Touch)
        val tap1Release = makeChange(10L, Offset(202f, 202f), pressed = false, previousPressed = true, pointerType = PointerType.Touch)
        pointerState.handlePointerEvent(makeEvent(listOf(tap1Press)))
        pointerState.handlePointerEvent(makeEvent(listOf(tap1Release)))

        val tap2Press = makeChange(11L, Offset(203f, 203f), pressed = true, previousPressed = false, pointerType = PointerType.Touch)
        val tap2Release = makeChange(11L, Offset(205f, 205f), pressed = false, previousPressed = true, pointerType = PointerType.Touch)
        pointerState.handlePointerEvent(makeEvent(listOf(tap2Press)))
        pointerState.handlePointerEvent(makeEvent(listOf(tap2Release)))

        // Viewport scale must remain unchanged during rapid pen taps
        assertEquals(initialScale, state.viewportState.scale, 0.001f)
    }

    @Test
    fun testStylusOnlyModePansWithFingerAndDrawsWithStylus() {
        val state = CanvasState()
        val pointerState = createPointerState(state)
        state.currentTool = DrawingTool.PEN
        state.palmConfig = state.palmConfig.copy(isFingerDrawingEnabled = false)

        val initialPanX = state.viewportState.panX
        val initialPanY = state.viewportState.panY

        // 1. Touch drag should pan when finger drawing is disabled
        val touchPress = makeChange(20L, Offset(400f, 400f), pressed = true, previousPressed = false, pointerType = PointerType.Touch)
        val touchMove = makeChange(20L, Offset(450f, 460f), pressed = true, previousPosition = Offset(400f, 400f), pointerType = PointerType.Touch)
        val touchRelease = makeChange(20L, Offset(450f, 460f), pressed = false, previousPressed = true, pointerType = PointerType.Touch)

        pointerState.handlePointerEvent(makeEvent(listOf(touchPress)))
        assertTrue(pointerState.isPanDragging)
        pointerState.handlePointerEvent(makeEvent(listOf(touchMove)))
        pointerState.handlePointerEvent(makeEvent(listOf(touchRelease)))

        assertEquals(initialPanX + 50f, state.viewportState.panX, 0.1f)
        assertEquals(initialPanY + 60f, state.viewportState.panY, 0.1f)
        assertEquals(0, state.currentPage.strokes.size)

        // 2. Stylus drag in Stylus-Only mode should draw a stroke
        val stylusPress = makeChange(21L, Offset(200f, 200f), pressed = true, previousPressed = false, pointerType = PointerType.Stylus)
        val stylusMove = makeChange(21L, Offset(260f, 260f), pressed = true, previousPosition = Offset(200f, 200f), pointerType = PointerType.Stylus)
        val stylusRelease = makeChange(21L, Offset(260f, 260f), pressed = false, previousPressed = true, pointerType = PointerType.Stylus)

        pointerState.handlePointerEvent(makeEvent(listOf(stylusPress)))
        pointerState.handlePointerEvent(makeEvent(listOf(stylusMove)))
        pointerState.handlePointerEvent(makeEvent(listOf(stylusRelease)))

        assertEquals(1, state.currentPage.strokes.size)
    }

    @Test
    fun testMouseDrawsEvenWhenFingerDrawingDisabled() {
        val state = CanvasState()
        val pointerState = createPointerState(state)
        state.currentTool = DrawingTool.PEN
        state.palmConfig = state.palmConfig.copy(isFingerDrawingEnabled = false)

        val mousePress = makeChange(30L, Offset(200f, 200f), pressed = true, previousPressed = false, pointerType = PointerType.Mouse)
        val mouseMove = makeChange(30L, Offset(260f, 260f), pressed = true, previousPosition = Offset(200f, 200f), pointerType = PointerType.Mouse)
        val mouseRelease = makeChange(30L, Offset(260f, 260f), pressed = false, previousPressed = true, pointerType = PointerType.Mouse)

        pointerState.handlePointerEvent(makeEvent(listOf(mousePress)))
        pointerState.handlePointerEvent(makeEvent(listOf(mouseMove)))
        pointerState.handlePointerEvent(makeEvent(listOf(mouseRelease)))

        assertEquals("Mouse should always draw even if finger drawing is disabled", 1, state.currentPage.strokes.size)
    }

    @Test
    fun testTouchSynthesizedAwtMouseEventsNormalized() {
        val dummyComponent = Canvas()

        // 1. Touch-synthesized MOUSE_PRESSED where GetKeyState(VK_LBUTTON) was not set in modifiersEx (0)
        val rawPress = MouseEvent(
            dummyComponent,
            MouseEvent.MOUSE_PRESSED,
            System.currentTimeMillis(),
            0,
            120,
            240,
            1,
            false,
            MouseEvent.BUTTON1
        )
        val normPress = DesktopTouchInputFixer.normalizeMouseEvent(rawPress)
        assertTrue(
            "Touch MOUSE_PRESSED must include BUTTON1_DOWN_MASK so OnlyValidPrimaryMouseButtonFilter accepts drags",
            (normPress.modifiersEx and InputEvent.BUTTON1_DOWN_MASK) != 0
        )
        assertEquals(MouseEvent.BUTTON1, normPress.button)

        // 2. Touch-synthesized MOUSE_DRAGGED where modifiersEx lacks BUTTON1_DOWN_MASK (0) and button is NOBUTTON
        val rawDrag = MouseEvent(
            dummyComponent,
            MouseEvent.MOUSE_DRAGGED,
            System.currentTimeMillis(),
            0,
            150,
            270,
            1,
            false,
            MouseEvent.NOBUTTON
        )
        val normDrag = DesktopTouchInputFixer.normalizeMouseEvent(rawDrag)
        assertTrue(
            "Touch MOUSE_DRAGGED must include BUTTON1_DOWN_MASK so ComposeSceneMediator keeps pointer pressed",
            (normDrag.modifiersEx and InputEvent.BUTTON1_DOWN_MASK) != 0
        )
        assertEquals(MouseEvent.BUTTON1, normDrag.button)

        // 3. Touch-synthesized MOUSE_RELEASED where modifiersEx still has BUTTON1_DOWN_MASK stuck
        val rawRelease = MouseEvent(
            dummyComponent,
            MouseEvent.MOUSE_RELEASED,
            System.currentTimeMillis(),
            InputEvent.BUTTON1_DOWN_MASK,
            150,
            270,
            1,
            false,
            MouseEvent.NOBUTTON
        )
        val normRelease = DesktopTouchInputFixer.normalizeMouseEvent(rawRelease)
        assertEquals(
            "Touch MOUSE_RELEASED must clear BUTTON1_DOWN_MASK",
            0,
            normRelease.modifiersEx and InputEvent.BUTTON1_DOWN_MASK
        )
        assertEquals(MouseEvent.BUTTON1, normRelease.button)
    }

    @Test
    fun testTrackpadPinchZoomIsSmoothNotBigStep() {
        val state = CanvasState()
        state.viewportState.containerSize = Size(1280f, 800f)
        state.viewportState.scale = 1.0f

        // Simulate 1 tick of pinch-to-zoom (delta.y = -1.0 with Ctrl pressed)
        val pinchChange = makeChange(50L, Offset(640f, 400f), pressed = false, scrollDelta = Offset(0f, -1.0f))
        val ctrlPinchEvent = makeEvent(listOf(pinchChange))
        DesktopCanvasGestureHandler.handleScroll(ctrlPinchEvent, state.viewportState)

        // Must scale progressively (~1.035) instead of old chunky 1.15 step
        assertTrue(
            "Trackpad pinch tick should scale smoothly between 1.02 and 1.06, was ${state.viewportState.scale}",
            state.viewportState.scale in 1.02f..1.06f
        )
    }
}
