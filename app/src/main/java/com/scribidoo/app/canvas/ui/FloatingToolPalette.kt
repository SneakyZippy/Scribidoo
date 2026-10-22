package com.scribidoo.app.canvas.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ModeEdit
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Divider
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DrawingTool
import com.scribidoo.app.canvas.model.ShapeType
import com.scribidoo.app.canvas.state.CanvasState
import kotlin.math.roundToInt

@Composable
fun FloatingToolPalette(
    state: CanvasState,
    modifier: Modifier = Modifier
) {
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

    Surface(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(24.dp))
            .border(1.dp, Color(0x1F000000), RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main tool buttons row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Drag handle icon
                Icon(
                    imageVector = Icons.Default.Gesture,
                    contentDescription = "Drag Palette",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(20.dp)
                        .padding(horizontal = 2.dp)
                )

                // Pen Tool
                ToolButton(
                    icon = Icons.Default.ModeEdit,
                    label = "Pen",
                    isSelected = state.currentTool == DrawingTool.PEN,
                    onClick = { state.currentTool = DrawingTool.PEN }
                )

                // Highlighter Tool
                ToolButton(
                    icon = Icons.Default.Highlight,
                    label = "Highlighter",
                    isSelected = state.currentTool == DrawingTool.HIGHLIGHTER,
                    onClick = { state.currentTool = DrawingTool.HIGHLIGHTER }
                )

                // Eraser Tool (Click switches between Stroke and Segment eraser)
                ToolButton(
                    icon = if (state.currentTool == DrawingTool.SEGMENT_ERASER) Icons.Default.AutoFixHigh else Icons.Default.DeleteSweep,
                    label = if (state.currentTool == DrawingTool.SEGMENT_ERASER) "Slice Eraser" else "Stroke Eraser",
                    isSelected = state.currentTool == DrawingTool.STROKE_ERASER || state.currentTool == DrawingTool.SEGMENT_ERASER,
                    onClick = {
                        state.currentTool = if (state.currentTool == DrawingTool.STROKE_ERASER) {
                            DrawingTool.SEGMENT_ERASER
                        } else {
                            DrawingTool.STROKE_ERASER
                        }
                    }
                )

                // Shapes Tool
                Box {
                    ToolButton(
                        icon = Icons.Default.CropSquare,
                        label = "Shapes",
                        isSelected = state.currentTool == DrawingTool.SHAPE,
                        onClick = {
                            state.currentTool = DrawingTool.SHAPE
                            showShapeMenu = !showShapeMenu
                        }
                    )
                    DropdownMenu(
                        expanded = showShapeMenu,
                        onDismissRequest = { showShapeMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rectangle") },
                            onClick = {
                                state.currentShapeType = ShapeType.RECTANGLE
                                state.currentTool = DrawingTool.SHAPE
                                showShapeMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ellipse / Circle") },
                            onClick = {
                                state.currentShapeType = ShapeType.ELLIPSE
                                state.currentTool = DrawingTool.SHAPE
                                showShapeMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Line") },
                            onClick = {
                                state.currentShapeType = ShapeType.LINE
                                state.currentTool = DrawingTool.SHAPE
                                showShapeMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Arrow") },
                            onClick = {
                                state.currentShapeType = ShapeType.ARROW
                                state.currentTool = DrawingTool.SHAPE
                                showShapeMenu = false
                            }
                        )
                    }
                }

                // Text Box Tool
                ToolButton(
                    icon = Icons.Default.TextFields,
                    label = "Text Box",
                    isSelected = state.currentTool == DrawingTool.TEXT,
                    onClick = { state.currentTool = DrawingTool.TEXT }
                )

                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(Color(0x1F000000))
                )

                // Color Swatch Button
                Box {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(state.currentColor)
                            .border(2.dp, Color.White, CircleShape)
                            .border(3.dp, Color(0x33000000), CircleShape)
                            .clickable { showColorMenu = !showColorMenu }
                    )
                    DropdownMenu(
                        expanded = showColorMenu,
                        onDismissRequest = { showColorMenu = false }
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
                                            showColorMenu = false
                                        }
                                )
                            }
                        }
                    }
                }

                // Stroke Width Indicator & Menu
                Box {
                    IconButton(
                        onClick = { showWidthMenu = !showWidthMenu },
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
                        onDismissRequest = { showWidthMenu = false }
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

                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(Color(0x1F000000))
                )

                // Finger Drawing / Palm Rejection Toggle Button
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
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Layout Switcher: Paged vs Infinite
                val isPaged = state.document.layoutMode == CanvasLayoutMode.PAGED
                IconButton(
                    onClick = { state.toggleLayoutMode() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = if (isPaged) "Paged Mode" else "Infinite Mode",
                        tint = if (isPaged) MaterialTheme.colorScheme.primary else Color(0xFF6B7280),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Reset Zoom / Fit Page Button
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
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Undo / Redo
                IconButton(
                    onClick = { state.undo() },
                    enabled = state.canUndo,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (state.canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { state.redo() },
                    enabled = state.canRedo,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (state.canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Add Page (In Paged mode)
                if (isPaged) {
                    IconButton(
                        onClick = { state.addNewPage() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Page",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Quick Status bar beneath palette showing current mode & zoom
            Row(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (state.palmConfig.isFingerDrawingEnabled) "Finger Draw: ON" else "Active Stylus (Palm Proof)",
                    fontSize = 10.sp,
                    color = if (state.palmConfig.isFingerDrawingEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Mode: ${if (state.document.layoutMode == CanvasLayoutMode.PAGED) "Page ${state.document.activePageIndex + 1}/${state.document.pages.size}" else "Infinite"}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Zoom: ${(state.viewportState.scale * 100).toInt()}%",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
            .size(38.dp)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}
