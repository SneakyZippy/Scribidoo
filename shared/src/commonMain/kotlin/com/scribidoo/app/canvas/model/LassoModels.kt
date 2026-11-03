package com.scribidoo.app.canvas.model

import androidx.compose.ui.geometry.Rect

data class LassoSelection(
    val strokeIds: Set<String> = emptySet(),
    val shapeIds: Set<String> = emptySet(),
    val textBoxIds: Set<String> = emptySet(),
    val imageIds: Set<String> = emptySet(),
    val bounds: Rect = Rect.Zero
) {
    val isEmpty: Boolean 
        get() = strokeIds.isEmpty() && shapeIds.isEmpty() && textBoxIds.isEmpty() && imageIds.isEmpty()
        
    val isNotEmpty: Boolean 
        get() = !isEmpty

    companion object {
        val Empty = LassoSelection()
    }
}
