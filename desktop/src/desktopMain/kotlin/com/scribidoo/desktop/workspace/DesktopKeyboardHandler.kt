package com.scribidoo.desktop.workspace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.app.canvas.state.SelectableType
import com.scribidoo.desktop.media.DesktopImageUtils

object DesktopKeyboardHandler {

    fun handleWorkspaceKeyEvent(
        event: KeyEvent,
        state: CanvasState,
        isInputActive: Boolean,
        onSaveRequested: () -> Unit,
        showToast: (String) -> Unit
    ): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (isInputActive) return false

        if (event.key == Key.Spacebar) {
            return when (event.type) {
                KeyEventType.KeyDown -> {
                    state.isSpacebarPanActive = true
                    true
                }
                KeyEventType.KeyUp -> {
                    state.isSpacebarPanActive = false
                    true
                }
                else -> false
            }
        }

        val isModifier = event.isCtrlPressed || event.isMetaPressed
        return if (isModifier) {
            handleModifierShortcuts(event, state, onSaveRequested, showToast)
        } else {
            handleDirectShortcuts(event, state, showToast)
        }
    }

    private fun handleModifierShortcuts(
        event: KeyEvent,
        state: CanvasState,
        onSaveRequested: () -> Unit,
        showToast: (String) -> Unit
    ): Boolean {
        return when (event.key) {
            Key.Z -> {
                if (event.isShiftPressed) {
                    state.redo()
                    showToast("Redo")
                } else {
                    state.undo()
                    showToast("Undo")
                }
                true
            }
            Key.Y -> {
                state.redo()
                showToast("Redo")
                true
            }
            Key.S -> {
                onSaveRequested()
                true
            }
            Key.C -> {
                val item = state.selectedItem
                if (item?.type == SelectableType.TEXT_BOX) {
                    val box = state.currentPage.textBoxes.firstOrNull { it.id == item.id }
                    if (box != null) {
                        DesktopImageUtils.copyTextToClipboard(box.text)
                        showToast("Copied text")
                        return true
                    }
                }
                false
            }
            Key.V -> {
                pasteFromClipboard(state, showToast)
                true
            }
            Key.Plus, Key.Equals, Key.NumPadAdd -> {
                val center = Offset(
                    state.viewportState.containerSize.width / 2f,
                    state.viewportState.containerSize.height / 2f
                )
                state.viewportState.zoomBy(1.10f, center)
                true
            }
            Key.Minus, Key.NumPadSubtract -> {
                val center = Offset(
                    state.viewportState.containerSize.width / 2f,
                    state.viewportState.containerSize.height / 2f
                )
                state.viewportState.zoomBy(1f / 1.10f, center)
                true
            }
            Key.Zero, Key.NumPad0 -> {
                val page = state.currentPage
                state.viewportState.centerAndFit(page.width, page.height, padding = 48f)
                showToast("Fit to window")
                true
            }
            else -> false
        }
    }

    private fun handleDirectShortcuts(
        event: KeyEvent,
        state: CanvasState,
        showToast: (String) -> Unit
    ): Boolean {
        return when (event.key) {
            Key.One, Key.V -> {
                state.currentTool = DrawingTool.HAND
                showToast("Hand Tool (Pan & Zoom)")
                true
            }
            Key.Two, Key.P -> {
                state.currentTool = DrawingTool.PEN
                showToast("Pen")
                true
            }
            Key.Three, Key.H, Key.B -> {
                state.currentTool = DrawingTool.HIGHLIGHTER
                showToast("Highlighter")
                true
            }
            Key.Four, Key.E -> {
                state.currentTool = if (state.currentTool == DrawingTool.STROKE_ERASER) {
                    DrawingTool.SEGMENT_ERASER
                } else {
                    DrawingTool.STROKE_ERASER
                }
                showToast(if (state.currentTool == DrawingTool.SEGMENT_ERASER) "Segment Eraser" else "Stroke Eraser")
                true
            }
            Key.Five, Key.L -> {
                state.currentTool = DrawingTool.LASSO
                showToast("Lasso Tool")
                true
            }
            Key.Six, Key.S -> {
                state.currentTool = DrawingTool.SHAPE
                showToast("Shape Tool")
                true
            }
            Key.Seven, Key.T -> {
                state.currentTool = DrawingTool.TEXT
                showToast("Text Tool")
                true
            }
            Key.Delete, Key.Backspace -> {
                if (state.selectedItem != null) {
                    state.deleteSelectedItem()
                    showToast("Deleted item")
                    true
                } else if (!state.lassoSelection.isEmpty) {
                    state.deleteLassoSelection()
                    showToast("Deleted lasso selection")
                    true
                } else false
            }
            Key.Escape -> {
                when {
                    state.selectedItem != null -> {
                        state.selectedItem = null
                        true
                    }
                    !state.lassoSelection.isEmpty || state.activeLassoPath.isNotEmpty() -> {
                        state.clearLasso()
                        true
                    }
                    else -> false
                }
            }
            else -> false
        }
    }

    private fun pasteFromClipboard(state: CanvasState, showToast: (String) -> Unit) {
        val imageFile = DesktopImageUtils.pasteImageFromClipboard()
        if (imageFile != null) {
            val (origW, origH) = DesktopImageUtils.getImageDimensions(imageFile.absolutePath) ?: Pair(400f, 300f)
            val (w, h) = DesktopImageUtils.calculateScaledDimensions(origW, origH)
            val center = state.viewportState.screenToCanvas(
                Offset(state.viewportState.containerSize.width / 2f, state.viewportState.containerSize.height / 2f)
            )
            state.addImage(imageFile.absolutePath, w, h, center)
            showToast("Pasted image")
            return
        }

        val text = DesktopImageUtils.getClipboardText()
        if (!text.isNullOrBlank()) {
            val center = state.viewportState.screenToCanvas(
                Offset(state.viewportState.containerSize.width / 2f, state.viewportState.containerSize.height / 2f)
            )
            state.addTextBox(center, text)
            showToast("Pasted text note")
            return
        }

        showToast("Clipboard has no supported image or text")
    }
}
