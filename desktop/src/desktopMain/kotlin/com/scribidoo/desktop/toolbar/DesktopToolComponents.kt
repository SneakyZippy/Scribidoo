package com.scribidoo.desktop.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ModeEdit
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.app.canvas.ui.CanvasIcons

@Composable
fun ToolIconButton(
    icon: ImageVector,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun HandTool(state: CanvasState) = ToolIconButton(Icons.Default.PanTool, "Hand Tool", state.currentTool == DrawingTool.HAND) { state.currentTool = DrawingTool.HAND }

@Composable
fun PenTool(state: CanvasState) = ToolIconButton(Icons.Default.ModeEdit, "Pen", state.currentTool == DrawingTool.PEN) { state.currentTool = DrawingTool.PEN }

@Composable
fun HighlighterTool(state: CanvasState) = ToolIconButton(CanvasIcons.HighlighterMarker, "Highlighter", state.currentTool == DrawingTool.HIGHLIGHTER) { state.currentTool = DrawingTool.HIGHLIGHTER }

@Composable
fun EraserTool(state: CanvasState) = ToolIconButton(CanvasIcons.EraserStroke, "Eraser", state.currentTool == DrawingTool.STROKE_ERASER) { state.currentTool = DrawingTool.STROKE_ERASER }

@Composable
fun LassoTool(state: CanvasState) = ToolIconButton(CanvasIcons.LassoTool, "Lasso Selection", state.currentTool == DrawingTool.LASSO) { state.currentTool = DrawingTool.LASSO }

@Composable
fun TextTool(state: CanvasState) = ToolIconButton(Icons.Default.TextFields, "Text Note", state.currentTool == DrawingTool.TEXT) { state.currentTool = DrawingTool.TEXT }

@Composable
fun ImageTool(state: CanvasState, onOpenImagePicker: () -> Unit) = ToolIconButton(Icons.Default.Image, "Insert Picture", false, onOpenImagePicker)

@Composable
fun AudioTool(state: CanvasState, onOpenVoiceRecorder: () -> Unit) = ToolIconButton(Icons.Default.Mic, "Record Audio Memo", false, onOpenVoiceRecorder)

@Composable
fun FingerTool(state: CanvasState) {
    val enabled = state.palmConfig.isFingerDrawingEnabled
    IconButton(
        onClick = { state.palmConfig = state.palmConfig.copy(isFingerDrawingEnabled = !enabled) },
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
    ) {
        Icon(
            imageVector = Icons.Default.TouchApp,
            contentDescription = "Toggle Finger Drawing",
            tint = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun ShapeTool(
    state: CanvasState,
    showShapeMenu: Boolean,
    onShowShapeMenuChange: (Boolean) -> Unit
) {
    Box {
        ToolIconButton(Icons.Default.CropSquare, "Shapes", state.currentTool == DrawingTool.SHAPE) {
            state.currentTool = DrawingTool.SHAPE
            onShowShapeMenuChange(true)
        }
        DropdownMenu(expanded = showShapeMenu, onDismissRequest = { onShowShapeMenuChange(false) }) {
            listOf(
                ShapeType.RECTANGLE to "Rectangle",
                ShapeType.ELLIPSE to "Circle / Ellipse",
                ShapeType.LINE to "Straight Line",
                ShapeType.ARROW to "Arrow",
                ShapeType.TRIANGLE to "Triangle"
            ).forEach { (type, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontSize = 13.sp) },
                    onClick = {
                        state.currentShapeType = type
                        state.currentTool = DrawingTool.SHAPE
                        onShowShapeMenuChange(false)
                    }
                )
            }
        }
    }
}

@Composable
fun ColorTool(
    state: CanvasState,
    presetColors: List<Color>,
    showColorMenu: Boolean,
    onShowColorMenuChange: (Boolean) -> Unit
) {
    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(state.currentColor)
                .border(2.dp, Color.White, CircleShape)
                .clickable { onShowColorMenuChange(!showColorMenu) }
        )
        DropdownMenu(expanded = showColorMenu, onDismissRequest = { onShowColorMenuChange(false) }) {
            Row(modifier = Modifier.padding(8.dp)) {
                presetColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable {
                                state.currentColor = color
                                onShowColorMenuChange(false)
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun WidthTool(
    state: CanvasState,
    showWidthMenu: Boolean,
    onShowWidthMenuChange: (Boolean) -> Unit
) {
    Box {
        IconButton(onClick = { onShowWidthMenuChange(!showWidthMenu) }, modifier = Modifier.size(36.dp)) {
            Box(
                modifier = Modifier
                    .size(state.currentStrokeWidth.coerceIn(4f, 18f).dp)
                    .clip(CircleShape)
                    .background(state.currentColor)
            )
        }
        DropdownMenu(expanded = showWidthMenu, onDismissRequest = { onShowWidthMenuChange(false) }) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Slider(
                    value = state.currentStrokeWidth,
                    onValueChange = { state.currentStrokeWidth = it },
                    valueRange = 1f..24f,
                    modifier = Modifier.size(width = 140.dp, height = 36.dp)
                )
            }
        }
    }
}

@Composable
fun UndoTool(state: CanvasState) {
    IconButton(onClick = { state.undo() }, enabled = state.canUndo, modifier = Modifier.size(36.dp)) {
        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
    }
}

@Composable
fun RedoTool(state: CanvasState) {
    IconButton(onClick = { state.redo() }, enabled = state.canRedo, modifier = Modifier.size(36.dp)) {
        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
    }
}

@Composable
fun FitTool(state: CanvasState) {
    IconButton(
        onClick = {
            if (state.document.layoutMode == CanvasLayoutMode.PAGED) {
                state.viewportState.centerAndFit(state.currentPage.width, state.currentPage.height, 48f)
            } else {
                state.viewportState.resetZoom()
            }
        },
        modifier = Modifier.size(36.dp)
    ) {
        Icon(Icons.Default.FitScreen, contentDescription = "Fit to Screen")
    }
}
