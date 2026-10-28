package com.scribidoo.app.canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Layers
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.R
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.state.CanvasState
import kotlin.math.roundToInt

enum class ToolbarOrientation {
    HORIZONTAL,
    VERTICAL
}

@Composable
fun FloatingToolPalette(
    state: CanvasState,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {}
) {
    var orientation by remember { mutableStateOf(ToolbarOrientation.HORIZONTAL) }
    var offsetX by remember { mutableFloatStateOf(40f) }
    var offsetY by remember { mutableFloatStateOf(40f) }

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
        if (orientation == ToolbarOrientation.HORIZONTAL) {
            // Horizontal Layout (Bottom/Top bar)
            Column(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top subtle pill drag handle
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
                    // Tactile side drag handle grip
                    TactileGrip(isHorizontal = true, modifier = dragModifier)

                    // Core Drawing Tools
                    HandTool(state)
                    PenTool(state)
                    HighlighterTool(state)
                    EraserTool(state)
                    ShapeTool(
                        state = state,
                        showShapeMenu = showShapeMenu,
                        onShowShapeMenuChange = { showShapeMenu = it }
                    )
                    TextTool(state)

                    DividerItem(orientation)

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

                    DividerItem(orientation)

                    FingerTool(state)
                    UndoTool(state)
                    RedoTool(state)
                    FitTool(state)

                    DividerItem(orientation)

                    OrientationTool(
                        targetOrientation = ToolbarOrientation.VERTICAL,
                        onToggleOrientation = { orientation = ToolbarOrientation.VERTICAL }
                    )
                    SettingsTool(onOpenSettings = onOpenSettings)
                }

                // Status row
                HorizontalStatusRow(state)
            }
        } else {
            // Vertical Layout (2-Column compact grid)
            Column(
                modifier = Modifier
                    .padding(vertical = 6.dp, horizontal = 6.dp)
                    .width(88.dp)
                    .heightIn(max = 440.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top drag pill handle
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
                    // Row 1: Pan / Hand Tool
                    Row(
                        modifier = Modifier.width(76.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        HandTool(state)
                    }

                    // Row 2: Pen + Highlighter
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PenTool(state)
                        HighlighterTool(state)
                    }

                    // Row 2: Eraser + Shapes
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        EraserTool(state)
                        ShapeTool(
                            state = state,
                            showShapeMenu = showShapeMenu,
                            onShowShapeMenuChange = { showShapeMenu = it }
                        )
                    }

                    // Row 3: Text + Color
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextTool(state)
                        ColorTool(
                            state = state,
                            presetColors = presetColors,
                            showColorMenu = showColorMenu,
                            onShowColorMenuChange = { showColorMenu = it }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(68.dp)
                            .height(1.dp)
                            .background(Color(0x1F000000))
                    )

                    // Row 4: Width + Finger Toggle
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WidthTool(
                            state = state,
                            showWidthMenu = showWidthMenu,
                            onShowWidthMenuChange = { showWidthMenu = it }
                        )
                        FingerTool(state)
                    }

                    // Row 5: Undo + Redo
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        UndoTool(state)
                        RedoTool(state)
                    }

                    Box(
                        modifier = Modifier
                            .width(68.dp)
                            .height(1.dp)
                            .background(Color(0x1F000000))
                    )

                    // Row 6: Fit Screen + Flip Orientation
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FitTool(state)
                        OrientationTool(
                            targetOrientation = ToolbarOrientation.HORIZONTAL,
                            onToggleOrientation = { orientation = ToolbarOrientation.HORIZONTAL }
                        )
                    }

                    // Row 7: Settings
                    Row(
                        modifier = Modifier.width(76.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        SettingsTool(onOpenSettings = onOpenSettings)
                    }
                }

                // Bottom grip handle
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
private fun HandTool(state: CanvasState) {
    ToolButton(
        icon = Icons.Default.PanTool,
        label = "Pan Tool",
        isSelected = state.currentTool == DrawingTool.HAND,
        onClick = { state.currentTool = DrawingTool.HAND }
    )
}

@Composable
private fun PenTool(state: CanvasState) {
    ToolButton(
        icon = Icons.Default.ModeEdit,
        label = "Pen",
        isSelected = state.currentTool == DrawingTool.PEN,
        onClick = { state.currentTool = DrawingTool.PEN }
    )
}

@Composable
private fun HighlighterTool(state: CanvasState) {
    ToolButton(
        painter = painterResource(id = R.drawable.ic_highlighter_marker),
        label = "Highlighter Marker",
        isSelected = state.currentTool == DrawingTool.HIGHLIGHTER,
        onClick = { state.currentTool = DrawingTool.HIGHLIGHTER }
    )
}

@Composable
private fun EraserTool(state: CanvasState) {
    val isSegmentEraser = state.currentTool == DrawingTool.SEGMENT_ERASER
    val isEraserActive = state.currentTool == DrawingTool.STROKE_ERASER || isSegmentEraser
    ToolButton(
        painter = painterResource(
            id = if (isSegmentEraser) R.drawable.ic_eraser_precision else R.drawable.ic_eraser_stroke
        ),
        label = if (isSegmentEraser) "Precision Segment Eraser" else "Stroke Eraser",
        isSelected = isEraserActive,
        onClick = {
            state.currentTool = if (state.currentTool == DrawingTool.STROKE_ERASER) {
                DrawingTool.SEGMENT_ERASER
            } else {
                DrawingTool.STROKE_ERASER
            }
        }
    )
}

@Composable
private fun ShapeTool(
    state: CanvasState,
    showShapeMenu: Boolean,
    onShowShapeMenuChange: (Boolean) -> Unit
) {
    Box {
        ToolButton(
            icon = Icons.Default.CropSquare,
            label = "Shapes",
            isSelected = state.currentTool == DrawingTool.SHAPE,
            onClick = {
                state.currentTool = DrawingTool.SHAPE
                onShowShapeMenuChange(!showShapeMenu)
            }
        )
        DropdownMenu(
            expanded = showShapeMenu,
            onDismissRequest = { onShowShapeMenuChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text("Rectangle") },
                onClick = {
                    state.currentShapeType = ShapeType.RECTANGLE
                    state.currentTool = DrawingTool.SHAPE
                    onShowShapeMenuChange(false)
                }
            )
            DropdownMenuItem(
                text = { Text("Ellipse / Circle") },
                onClick = {
                    state.currentShapeType = ShapeType.ELLIPSE
                    state.currentTool = DrawingTool.SHAPE
                    onShowShapeMenuChange(false)
                }
            )
            DropdownMenuItem(
                text = { Text("Line") },
                onClick = {
                    state.currentShapeType = ShapeType.LINE
                    state.currentTool = DrawingTool.SHAPE
                    onShowShapeMenuChange(false)
                }
            )
            DropdownMenuItem(
                text = { Text("Arrow") },
                onClick = {
                    state.currentShapeType = ShapeType.ARROW
                    state.currentTool = DrawingTool.SHAPE
                    onShowShapeMenuChange(false)
                }
            )
        }
    }
}

@Composable
private fun TextTool(state: CanvasState) {
    ToolButton(
        icon = Icons.Default.TextFields,
        label = "Text Box",
        isSelected = state.currentTool == DrawingTool.TEXT,
        onClick = { state.currentTool = DrawingTool.TEXT }
    )
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
                .size(30.dp)
                .clip(CircleShape)
                .background(state.currentColor)
                .border(2.dp, Color.White, CircleShape)
                .border(3.dp, Color(0x33000000), CircleShape)
                .clickable { onShowColorMenuChange(!showColorMenu) }
        )
        DropdownMenu(
            expanded = showColorMenu,
            onDismissRequest = { onShowColorMenuChange(false) }
        ) {
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
        DropdownMenu(
            expanded = showWidthMenu,
            onDismissRequest = { onShowWidthMenuChange(false) }
        ) {
            Column(modifier = Modifier.padding(12.dp).width(160.dp)) {
                Text("Stroke Width: ${state.currentStrokeWidth.toInt()} px", fontSize = 12.sp)
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
            contentDescription = if (fingerDrawOn) "Draw with Finger ON" else "Stylus Only (Palm Proof)",
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
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Undo,
            contentDescription = "Undo",
            tint = if (state.canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun RedoTool(state: CanvasState) {
    IconButton(
        onClick = { state.redo() },
        enabled = state.canRedo,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Redo,
            contentDescription = "Redo",
            tint = if (state.canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun FitTool(state: CanvasState) {
    IconButton(
        onClick = {
            val curPage = state.currentPage
            state.viewportState.centerAndFit(curPage.width, curPage.height)
        },
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = Icons.Default.FitScreen,
            contentDescription = "Fit to Screen",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun OrientationTool(
    targetOrientation: ToolbarOrientation,
    onToggleOrientation: () -> Unit
) {
    IconButton(
        onClick = onToggleOrientation,
        modifier = Modifier.size(36.dp)
    ) {
        val icon = if (targetOrientation == ToolbarOrientation.VERTICAL) {
            Icons.Default.VerticalSplit
        } else {
            Icons.Default.HorizontalSplit
        }
        val description = if (targetOrientation == ToolbarOrientation.VERTICAL) {
            "Switch Toolbar to Vertical Dock"
        } else {
            "Switch Toolbar to Horizontal Bar"
        }
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsTool(onOpenSettings: () -> Unit) {
    IconButton(
        onClick = onOpenSettings,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Canvas & Palm Settings",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TactileGrip(isHorizontal: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .size(if (isHorizontal) 14.dp else 28.dp, if (isHorizontal) 28.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isHorizontal) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(3.dp).height(18.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(1.5.dp)))
                Box(modifier = Modifier.width(3.dp).height(18.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(1.5.dp)))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.height(3.dp).width(18.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(1.5.dp)))
                Box(modifier = Modifier.height(3.dp).width(18.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(1.5.dp)))
            }
        }
    }
}

@Composable
private fun DividerItem(orientation: ToolbarOrientation) {
    if (orientation == ToolbarOrientation.HORIZONTAL) {
        Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color(0x1F000000)))
    } else {
        Box(modifier = Modifier.width(24.dp).height(1.dp).background(Color(0x1F000000)))
    }
}

@Composable
private fun HorizontalStatusRow(state: CanvasState) {
    Row(
        modifier = Modifier
            .padding(top = 2.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val toolName = when (state.currentTool) {
            DrawingTool.PEN -> "Pen"
            DrawingTool.HIGHLIGHTER -> "Marker"
            DrawingTool.STROKE_ERASER -> "Eraser: Stroke"
            DrawingTool.SEGMENT_ERASER -> "Eraser: Precision"
            DrawingTool.SHAPE -> "Shape"
            DrawingTool.TEXT -> "Text"
            DrawingTool.LASSO -> "Lasso"
            DrawingTool.HAND -> "Pan"
        }
        Text(text = toolName, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        Text(
            text = if (state.palmConfig.isFingerDrawingEnabled) "Finger: ON" else "Stylus",
            fontSize = 10.sp,
            color = if (state.palmConfig.isFingerDrawingEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
        )
        Text(
            text = if (state.document.layoutMode == CanvasLayoutMode.PAGED) "Page ${state.document.activePageIndex + 1}/${state.document.pages.size}" else "Infinite",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Zoom ${(state.viewportState.scale * 100).toInt()}%",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ToolButton(
    painter: Painter,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Icon(
            painter = painter,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
