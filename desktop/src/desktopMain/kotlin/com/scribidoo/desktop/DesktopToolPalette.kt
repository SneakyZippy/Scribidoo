package com.scribidoo.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.HorizontalSplit
import androidx.compose.material.icons.filled.ModeEdit
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.app.canvas.ui.CanvasIcons
import kotlin.math.roundToInt

enum class DesktopToolbarOrientation {
    HORIZONTAL,
    VERTICAL
}

@Composable
fun DesktopToolPalette(
    state: CanvasState,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {}
) {
    var orientation by remember { mutableStateOf(DesktopToolbarOrientation.HORIZONTAL) }
    var offsetX by remember { mutableFloatStateOf(60f) }
    var offsetY by remember { mutableFloatStateOf(60f) }

    var showColorMenu by remember { mutableStateOf(false) }
    var showWidthMenu by remember { mutableStateOf(false) }
    var showShapeMenu by remember { mutableStateOf(false) }

    val presetColors = listOf(
        Color(0xFF1E293B), // Charcoal Black
        Color(0xFF2563EB), // Vibrant Blue
        Color(0xFFDC2626), // Carmine Red
        Color(0xFF059669), // Emerald Green
        Color(0xFF7C3AED), // Deep Purple
        Color(0xFFF59E0B)  // Highlighter Amber / Yellow
    )

    val dragModifier = Modifier.pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            offsetX += dragAmount.x
            offsetY += dragAmount.y
        }
    }

    Surface(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(24.dp))
            .border(1.dp, Color(0x1F000000), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 8.dp
    ) {
        if (orientation == DesktopToolbarOrientation.HORIZONTAL) {
            // Horizontal Toolbar
            Column(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top drag handle
                Box(
                    modifier = Modifier
                        .then(dragModifier)
                        .padding(bottom = 5.dp)
                        .width(48.dp)
                        .height(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }

                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier.horizontalScroll(scrollState),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PenTool(state)
                    HighlighterTool(state)
                    EraserTool(state)
                    ShapeTool(
                        state = state,
                        showShapeMenu = showShapeMenu,
                        onShowShapeMenuChange = { showShapeMenu = it }
                    )
                    TextTool(state)

                    Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color(0x1F000000)))

                    ColorTool(
                        state = state,
                        presetColors = presetColors,
                        showColorMenu = showColorMenu,
                        onShowColorMenuChange = { showColorMenu = it }
                    )
                    WidthTool(
                        state = state,
                        showWidthMenu = showWidthMenu,
                        onShowWidthMenuChange = { showWidthMenu = it }
                    )

                    Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color(0x1F000000)))

                    FingerTool(state)
                    UndoTool(state)
                    RedoTool(state)
                    FitTool(state)

                    Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color(0x1F000000)))

                    IconButton(
                        onClick = { orientation = DesktopToolbarOrientation.VERTICAL },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerticalSplit,
                            contentDescription = "Dock toolbar to side",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Preferences",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // Status row
                Row(
                    modifier = Modifier.padding(top = 2.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Tool: ${state.currentTool.name}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (state.palmConfig.isFingerDrawingEnabled) "Touch: ON" else "Stylus Only",
                        fontSize = 10.sp,
                        color = if (state.palmConfig.isFingerDrawingEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Zoom: ${(state.viewportState.scale * 100).toInt()}%",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // 2-Column Vertical Layout
            Column(
                modifier = Modifier
                    .padding(vertical = 6.dp, horizontal = 6.dp)
                    .width(88.dp)
                    .heightIn(max = 440.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top drag handle
                Box(
                    modifier = Modifier
                        .then(dragModifier)
                        .padding(bottom = 6.dp)
                        .width(44.dp)
                        .height(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }

                val verticalScroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .verticalScroll(verticalScroll)
                        .weight(1f, fill = false),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PenTool(state)
                        HighlighterTool(state)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        EraserTool(state)
                        ShapeTool(state, showShapeMenu, onShowShapeMenuChange = { showShapeMenu = it })
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextTool(state)
                        ColorTool(state, presetColors, showColorMenu, onShowColorMenuChange = { showColorMenu = it })
                    }

                    Box(modifier = Modifier.width(68.dp).height(1.dp).background(Color(0x1F000000)))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WidthTool(state, showWidthMenu, onShowWidthMenuChange = { showWidthMenu = it })
                        FingerTool(state)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        UndoTool(state)
                        RedoTool(state)
                    }

                    Box(modifier = Modifier.width(68.dp).height(1.dp).background(Color(0x1F000000)))

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FitTool(state)
                        IconButton(
                            onClick = { orientation = DesktopToolbarOrientation.HORIZONTAL },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HorizontalSplit,
                                contentDescription = "Dock toolbar to bottom",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Row(modifier = Modifier.width(76.dp), horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Preferences",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Bottom drag handle
                Box(
                    modifier = Modifier
                        .then(dragModifier)
                        .padding(top = 6.dp)
                        .width(44.dp)
                        .height(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun PenTool(state: CanvasState) {
    IconButton(
        onClick = { state.currentTool = DrawingTool.PEN },
        modifier = Modifier
            .size(36.dp)
            .background(
                if (state.currentTool == DrawingTool.PEN) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Icon(Icons.Default.ModeEdit, contentDescription = "Pen", modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HighlighterTool(state: CanvasState) {
    IconButton(
        onClick = { state.currentTool = DrawingTool.HIGHLIGHTER },
        modifier = Modifier
            .size(36.dp)
            .background(
                if (state.currentTool == DrawingTool.HIGHLIGHTER) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Icon(
            painter = rememberVectorPainter(CanvasIcons.HighlighterMarker),
            contentDescription = "Marker",
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun EraserTool(state: CanvasState) {
    val isSegment = state.currentTool == DrawingTool.SEGMENT_ERASER
    val isActive = state.currentTool == DrawingTool.STROKE_ERASER || isSegment
    IconButton(
        onClick = {
            state.currentTool = if (state.currentTool == DrawingTool.STROKE_ERASER) DrawingTool.SEGMENT_ERASER else DrawingTool.STROKE_ERASER
        },
        modifier = Modifier
            .size(36.dp)
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Icon(
            painter = rememberVectorPainter(if (isSegment) CanvasIcons.EraserPrecision else CanvasIcons.EraserStroke),
            contentDescription = "Eraser",
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ShapeTool(
    state: CanvasState,
    showShapeMenu: Boolean,
    onShowShapeMenuChange: (Boolean) -> Unit
) {
    Box {
        IconButton(
            onClick = {
                state.currentTool = DrawingTool.SHAPE
                onShowShapeMenuChange(!showShapeMenu)
            },
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (state.currentTool == DrawingTool.SHAPE) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Icon(Icons.Default.CropSquare, contentDescription = "Shapes", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = showShapeMenu, onDismissRequest = { onShowShapeMenuChange(false) }) {
            DropdownMenuItem(text = { Text("Rectangle") }, onClick = {
                state.currentShapeType = ShapeType.RECTANGLE
                state.currentTool = DrawingTool.SHAPE
                onShowShapeMenuChange(false)
            })
            DropdownMenuItem(text = { Text("Ellipse / Circle") }, onClick = {
                state.currentShapeType = ShapeType.ELLIPSE
                state.currentTool = DrawingTool.SHAPE
                onShowShapeMenuChange(false)
            })
            DropdownMenuItem(text = { Text("Line") }, onClick = {
                state.currentShapeType = ShapeType.LINE
                state.currentTool = DrawingTool.SHAPE
                onShowShapeMenuChange(false)
            })
            DropdownMenuItem(text = { Text("Arrow") }, onClick = {
                state.currentShapeType = ShapeType.ARROW
                state.currentTool = DrawingTool.SHAPE
                onShowShapeMenuChange(false)
            })
        }
    }
}

@Composable
private fun TextTool(state: CanvasState) {
    IconButton(
        onClick = { state.currentTool = DrawingTool.TEXT },
        modifier = Modifier
            .size(36.dp)
            .background(
                if (state.currentTool == DrawingTool.TEXT) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Icon(Icons.Default.TextFields, contentDescription = "Text Note", modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ColorTool(
    state: CanvasState,
    presetColors: List<Color>,
    showColorMenu: Boolean,
    onShowColorMenuChange: (Boolean) -> Unit
) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(state.currentColor)
                .border(2.dp, Color.White, CircleShape)
                .border(3.dp, Color(0x33000000), CircleShape)
                .clickable { onShowColorMenuChange(!showColorMenu) }
        )
        DropdownMenu(expanded = showColorMenu, onDismissRequest = { onShowColorMenuChange(false) }) {
            Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presetColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (state.currentColor == color) 3.dp else 1.dp,
                                color = if (state.currentColor == color) MaterialTheme.colorScheme.primary else Color(0x33000000),
                                shape = CircleShape
                            )
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
private fun WidthTool(
    state: CanvasState,
    showWidthMenu: Boolean,
    onShowWidthMenuChange: (Boolean) -> Unit
) {
    Box(contentAlignment = Alignment.Center) {
        IconButton(
            onClick = { onShowWidthMenuChange(!showWidthMenu) },
            modifier = Modifier.size(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(state.currentStrokeWidth.coerceIn(3f, 16f).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
        }
        DropdownMenu(expanded = showWidthMenu, onDismissRequest = { onShowWidthMenuChange(false) }) {
            Column(modifier = Modifier.padding(12.dp).width(160.dp)) {
                Text("Stroke: ${state.currentStrokeWidth.toInt()} px", fontSize = 12.sp)
                Slider(
                    value = state.currentStrokeWidth,
                    onValueChange = { state.currentStrokeWidth = it },
                    valueRange = 1f..24f
                )
            }
        }
    }
}

@Composable
private fun FingerTool(state: CanvasState) {
    val fingerDrawOn = state.palmConfig.isFingerDrawingEnabled
    IconButton(
        onClick = { state.toggleFingerDrawing() },
        modifier = Modifier
            .size(36.dp)
            .background(
                if (fingerDrawOn) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = if (fingerDrawOn) Icons.Default.TouchApp else Icons.Default.PanTool,
            contentDescription = if (fingerDrawOn) "Touch Draw ON" else "Stylus Only",
            tint = if (fingerDrawOn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun UndoTool(state: CanvasState) {
    IconButton(
        onClick = { state.undo() },
        enabled = state.canUndo,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun RedoTool(state: CanvasState) {
    IconButton(
        onClick = { state.redo() },
        enabled = state.canRedo,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun FitTool(state: CanvasState) {
    IconButton(
        onClick = {
            val page = state.currentPage
            state.viewportState.centerAndFit(page.width, page.height)
        },
        modifier = Modifier.size(36.dp)
    ) {
        Icon(Icons.Default.FitScreen, contentDescription = "Fit to Screen", modifier = Modifier.size(19.dp))
    }
}
