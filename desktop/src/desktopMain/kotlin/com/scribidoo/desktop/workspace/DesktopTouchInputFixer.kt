package com.scribidoo.desktop.workspace

import java.awt.AWTEvent
import java.awt.Component
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

object DesktopTouchInputFixer {

    @Volatile
    private var isQueueInstalled = false

    fun initializeWindowsTouchSupport() {
        configureJvmTouchProperties()
        installTouchEventQueue()
    }

    fun configureJvmTouchProperties() {
        // Prevent JDK WToolkit from calling RegisterTouchWindow(hwnd, TWF_WANTPALM),
        // which disables Windows WM_GESTURE promotion and breaks touchscreen interaction.
        System.setProperty("awt.touchKeyboardAutoShowIsEnabled", "false")
    }

    @Synchronized
    fun installTouchEventQueue() {
        if (isQueueInstalled) return
        try {
            val systemQueue = Toolkit.getDefaultToolkit().systemEventQueue
            systemQueue.push(DesktopTouchEventQueue())
            isQueueInstalled = true
        } catch (e: Exception) {
            System.err.println("Failed to install DesktopTouchEventQueue: ${e.message}")
        }
    }

    internal fun normalizeMouseEvent(
        event: MouseEvent,
        isPrimaryDown: Boolean = true,
        onUpdatePrimaryDown: (Boolean) -> Unit = {}
    ): MouseEvent {
        if (event is MouseWheelEvent) return event
        val sourceComponent = event.component ?: (event.source as? Component) ?: return event
        val nonPrimaryMask = InputEvent.BUTTON2_DOWN_MASK or InputEvent.BUTTON3_DOWN_MASK
        val hasNonPrimary = (event.modifiersEx and nonPrimaryMask) != 0

        return when (event.id) {
            MouseEvent.MOUSE_PRESSED -> normalizePress(event, sourceComponent, hasNonPrimary, onUpdatePrimaryDown)
            MouseEvent.MOUSE_DRAGGED -> normalizeDrag(event, sourceComponent, hasNonPrimary, onUpdatePrimaryDown)
            MouseEvent.MOUSE_RELEASED -> normalizeRelease(event, sourceComponent, isPrimaryDown, hasNonPrimary, onUpdatePrimaryDown)
            else -> event
        }
    }

    private fun normalizePress(
        event: MouseEvent,
        sourceComponent: Component,
        hasNonPrimary: Boolean,
        onUpdatePrimaryDown: (Boolean) -> Unit
    ): MouseEvent {
        val isPrimaryButton = event.button == MouseEvent.BUTTON1 || event.button == MouseEvent.NOBUTTON
        if (!isPrimaryButton || hasNonPrimary) return event

        onUpdatePrimaryDown(true)
        val hasButton1Mask = (event.modifiersEx and InputEvent.BUTTON1_DOWN_MASK) != 0
        if (hasButton1Mask && event.button == MouseEvent.BUTTON1) return event

        return copyMouseEvent(
            event = event,
            source = sourceComponent,
            modifiers = event.modifiersEx or InputEvent.BUTTON1_DOWN_MASK,
            button = MouseEvent.BUTTON1
        )
    }

    private fun normalizeDrag(
        event: MouseEvent,
        sourceComponent: Component,
        hasNonPrimary: Boolean,
        onUpdatePrimaryDown: (Boolean) -> Unit
    ): MouseEvent {
        val hasButton1Mask = (event.modifiersEx and InputEvent.BUTTON1_DOWN_MASK) != 0
        if (hasButton1Mask || hasNonPrimary) return event

        onUpdatePrimaryDown(true)
        return copyMouseEvent(
            event = event,
            source = sourceComponent,
            modifiers = event.modifiersEx or InputEvent.BUTTON1_DOWN_MASK,
            button = if (event.button == MouseEvent.NOBUTTON) MouseEvent.BUTTON1 else event.button
        )
    }

    private fun normalizeRelease(
        event: MouseEvent,
        sourceComponent: Component,
        isPrimaryDown: Boolean,
        hasNonPrimary: Boolean,
        onUpdatePrimaryDown: (Boolean) -> Unit
    ): MouseEvent {
        val isPrimaryRelease = event.button == MouseEvent.BUTTON1 ||
            (event.button == MouseEvent.NOBUTTON && isPrimaryDown && !hasNonPrimary)
        if (!isPrimaryRelease) return event

        onUpdatePrimaryDown(false)
        val hasStuckButton1Mask = (event.modifiersEx and InputEvent.BUTTON1_DOWN_MASK) != 0
        if (!hasStuckButton1Mask && event.button == MouseEvent.BUTTON1) return event

        return copyMouseEvent(
            event = event,
            source = sourceComponent,
            modifiers = event.modifiersEx and InputEvent.BUTTON1_DOWN_MASK.inv(),
            button = MouseEvent.BUTTON1
        )
    }

    private fun copyMouseEvent(
        event: MouseEvent,
        source: Component,
        modifiers: Int,
        button: Int
    ): MouseEvent {
        return MouseEvent(
            source,
            event.id,
            event.`when`,
            modifiers,
            event.x,
            event.y,
            event.xOnScreen,
            event.yOnScreen,
            event.clickCount,
            event.isPopupTrigger,
            button
        )
    }
}

private class DesktopTouchEventQueue : EventQueue() {
    private var isPrimaryDown = false

    override fun dispatchEvent(event: AWTEvent) {
        val dispatched = if (event is MouseEvent) {
            DesktopTouchInputFixer.normalizeMouseEvent(
                event = event,
                isPrimaryDown = isPrimaryDown,
                onUpdatePrimaryDown = { isPrimaryDown = it }
            )
        } else {
            event
        }
        super.dispatchEvent(dispatched)
    }
}
