package com.scribidoo.desktop.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.app.canvas.state.SelectableType
import com.scribidoo.app.canvas.state.SelectedItem
import com.scribidoo.app.canvas.ui.isInsideLassoMenu
import com.scribidoo.app.canvas.ui.isInsideLassoScaleHandle
import com.scribidoo.desktop.media.DesktopAudioPlayer
import kotlin.math.hypot

object DesktopCanvasItemHandler {

    fun findHitItem(state: CanvasState, canvasPoint: Offset): SelectedItem? {
        state.currentPage.textBoxes.lastOrNull { getDesktopTextBoxBounds(it).contains(canvasPoint) }?.let {
            return SelectedItem(it.id, SelectableType.TEXT_BOX)
        }
        state.currentPage.images.lastOrNull { isPointInImage(it, canvasPoint) }?.let {
            return SelectedItem(it.id, SelectableType.IMAGE)
        }
        state.currentPage.audioNotes.lastOrNull { getAudioNoteBounds(it).contains(canvasPoint) }?.let {
            return SelectedItem(it.id, SelectableType.AUDIO_NOTE)
        }
        return null
    }

    fun handleSelectedItemPress(
        state: CanvasState,
        canvasPoint: Offset,
        audioPlayer: DesktopAudioPlayer,
        onStartResize: (bounds: Rect) -> Unit,
        onStartMove: () -> Unit
    ): Boolean {
        // 1. Audio Note Play Button
        val hitAudio = state.currentPage.audioNotes.lastOrNull { audio ->
            val playCenter = Offset(audio.x - audio.width / 2f + 26f, audio.y)
            hypot(canvasPoint.x - playCenter.x, canvasPoint.y - playCenter.y) <= state.viewportState.screenToCanvasDist(24f)
        }
        if (hitAudio != null) {
            audioPlayer.play(hitAudio.localPath, hitAudio.trimStartMs, hitAudio.trimEndMs)
            return true
        }

        // 2. Selected Item Handles and Action Pills
        val curSel = state.selectedItem ?: return false
        val bounds = getSelectedItemBounds(curSel, state.currentPage) ?: return false

        val resizeCenter = Offset(bounds.right, bounds.bottom)
        if (hypot(canvasPoint.x - resizeCenter.x, canvasPoint.y - resizeCenter.y) <= state.viewportState.screenToCanvasDist(30f)) {
            onStartResize(bounds)
            return true
        }

        val pillY = bounds.top - 28f
        if (canvasPoint.x in bounds.left..(bounds.left + 50f) && canvasPoint.y in (pillY - 4f)..(pillY + 24f)) {
            when (curSel.type) {
                SelectableType.TEXT_BOX -> state.activeTextBoxForEdit = state.currentPage.textBoxes.firstOrNull { it.id == curSel.id }
                SelectableType.AUDIO_NOTE -> state.activeAudioForMenu = state.currentPage.audioNotes.firstOrNull { it.id == curSel.id }
                else -> {}
            }
            state.selectedItem = null
            return true
        }

        if (canvasPoint.x in (bounds.left + 54f)..(bounds.left + 106f) && canvasPoint.y in (pillY - 4f)..(pillY + 24f)) {
            deleteItem(state, curSel)
            state.selectedItem = null
            return true
        }

        if (bounds.contains(canvasPoint)) {
            onStartMove()
            return true
        }

        state.selectedItem = null
        return false
    }

    fun handleLassoPress(
        state: CanvasState,
        canvasPoint: Offset,
        screenPoint: Offset,
        density: Float,
        onStartLassoDrag: () -> Unit,
        onStartLassoScale: () -> Unit
    ): Boolean {
        if (state.currentTool != DrawingTool.LASSO || state.lassoSelection.isEmpty) return false
        val bounds = state.lassoSelection.bounds
        if (isInsideLassoMenu(screenPoint, bounds, state.viewportState, density)) return true

        return if (isInsideLassoScaleHandle(canvasPoint, bounds, state.viewportState.scale)) {
            state.isScalingLassoSelection = true
            state.pushHistory()
            onStartLassoScale()
            true
        } else if (bounds.contains(canvasPoint)) {
            state.isDraggingLassoSelection = true
            state.pushHistory()
            onStartLassoDrag()
            true
        } else {
            state.clearLasso()
            false
        }
    }

    fun moveItem(state: CanvasState, item: SelectedItem, delta: Offset) {
        when (item.type) {
            SelectableType.TEXT_BOX -> state.moveTextBox(item.id, delta)
            SelectableType.IMAGE -> state.moveImage(item.id, delta)
            SelectableType.AUDIO_NOTE -> state.moveAudioNote(item.id, delta)
            SelectableType.SHAPE -> state.moveShape(item.id, delta)
        }
    }

    fun resizeItem(
        state: CanvasState,
        item: SelectedItem,
        scale: Float,
        initialFontSize: Float,
        initialItemWidth: Float,
        initialItemHeight: Float
    ) {
        when (item.type) {
            SelectableType.TEXT_BOX -> {
                val box = state.currentPage.textBoxes.firstOrNull { it.id == item.id } ?: return
                state.resizeTextBox(item.id, (initialFontSize * scale).coerceIn(10f, 64f))
            }
            SelectableType.IMAGE -> {
                val newW = (initialItemWidth * scale).coerceAtLeast(50f)
                val newH = (initialItemHeight * scale).coerceAtLeast(40f)
                state.resizeImage(item.id, newW, newH)
            }
            SelectableType.AUDIO_NOTE -> {
                val newW = (initialItemWidth * scale).coerceIn(120f, 320f)
                val newH = (initialItemHeight * scale).coerceIn(36f, 80f)
                state.resizeAudioNote(item.id, newW, newH)
            }
            else -> {}
        }
    }

    fun deleteItem(state: CanvasState, item: SelectedItem) {
        when (item.type) {
            SelectableType.TEXT_BOX -> state.deleteTextBox(item.id)
            SelectableType.IMAGE -> state.deleteImage(item.id)
            SelectableType.AUDIO_NOTE -> state.deleteAudioNote(item.id)
            SelectableType.SHAPE -> state.deleteShape(item.id)
        }
    }

    fun handleItemDragOrTransform(
        state: CanvasState,
        canvasPoint: Offset,
        lastTouchCanvas: Offset,
        isMovingItem: Boolean,
        isResizingItem: Boolean,
        initialItemWidth: Float,
        initialItemHeight: Float,
        initialFontSize: Float
    ): Boolean {
        if (isMovingItem && state.selectedItem != null) {
            moveItem(state, state.selectedItem!!, canvasPoint - lastTouchCanvas)
            return true
        }
        if (isResizingItem && state.selectedItem != null) {
            val sel = state.selectedItem!!
            val bounds = getSelectedItemBounds(sel, state.currentPage)
            if (bounds != null) {
                val curWidth = (canvasPoint.x - bounds.left).coerceAtLeast(30f)
                val scale = curWidth / initialItemWidth
                resizeItem(state, sel, scale, initialFontSize, initialItemWidth, initialItemHeight)
            }
            return true
        }
        if (state.isDraggingLassoSelection) {
            val delta = canvasPoint - lastTouchCanvas
            state.moveLassoSelection(delta.x, delta.y)
            return true
        }
        if (state.isScalingLassoSelection) {
            val pivot = state.lassoSelection.bounds.topLeft
            val factor = (canvasPoint.x - pivot.x) / (lastTouchCanvas.x - pivot.x).coerceAtLeast(10f)
            state.scaleLassoSelection(factor, pivot)
            return true
        }
        return false
    }
}
