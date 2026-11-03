package com.scribidoo.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.desktop.toolbar.AudioTool
import com.scribidoo.desktop.toolbar.ColorTool
import com.scribidoo.desktop.toolbar.EraserTool
import com.scribidoo.desktop.toolbar.FingerTool
import com.scribidoo.desktop.toolbar.FitTool
import com.scribidoo.desktop.toolbar.HandTool
import com.scribidoo.desktop.toolbar.HighlighterTool
import com.scribidoo.desktop.toolbar.ImageTool
import com.scribidoo.desktop.toolbar.LassoTool
import com.scribidoo.desktop.toolbar.PenTool
import com.scribidoo.desktop.toolbar.RedoTool
import com.scribidoo.desktop.toolbar.ShapeTool
import com.scribidoo.desktop.toolbar.TextTool
import com.scribidoo.desktop.toolbar.UndoTool
import com.scribidoo.desktop.toolbar.WidthTool
import kotlin.math.roundToInt

enum class DesktopToolbarOrientation { HORIZONTAL, VERTICAL }

@Composable
fun DesktopToolPalette(
    state: CanvasState,
    onOpenSettings: () -> Unit,
    onOpenImagePicker: () -> Unit,
    onOpenVoiceRecorder: () -> Unit,
    modifier: Modifier = Modifier
) {
    var orientation by remember { mutableStateOf(DesktopToolbarOrientation.HORIZONTAL) }
    var offsetX by remember { mutableFloatStateOf(60f) }
    var offsetY by remember { mutableFloatStateOf(40f) }

    var showColorMenu by remember { mutableStateOf(false) }
    var showWidthMenu by remember { mutableStateOf(false) }
    var showShapeMenu by remember { mutableStateOf(false) }

    val presetColors = listOf(
        Color(0xFF1E293B), Color(0xFF2563EB), Color(0xFFDC2626),
        Color(0xFF059669), Color(0xFF7C3AED), Color(0xFFF59E0B)
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
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .border(1.dp, Color(0x1F000000), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 8.dp
    ) {
        if (orientation == DesktopToolbarOrientation.HORIZONTAL) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top drag handle
                Box(modifier = Modifier.then(dragModifier).padding(bottom = 5.dp).width(48.dp).height(10.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.width(42.dp).height(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f), RoundedCornerShape(2.dp)))
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HandTool(state)
                    PenTool(state)
                    HighlighterTool(state)
                    EraserTool(state)
                    LassoTool(state)
                    ShapeTool(state, showShapeMenu) { showShapeMenu = it }
                    TextTool(state)
                    ImageTool(state, onOpenImagePicker)
                    AudioTool(state, onOpenVoiceRecorder)

                    Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color(0x1F000000)))

                    ColorTool(state, presetColors, showColorMenu) { showColorMenu = it }
                    WidthTool(state, showWidthMenu) { showWidthMenu = it }

                    Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color(0x1F000000)))

                    FingerTool(state)
                    UndoTool(state)
                    RedoTool(state)
                    FitTool(state)

                    Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color(0x1F000000)))

                    IconButton(onClick = { orientation = DesktopToolbarOrientation.VERTICAL }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.VerticalSplit, contentDescription = "Dock vertical", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                    }
                }

                // Status info
                Row(modifier = Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tool: ${state.currentTool.name}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    Text(if (state.palmConfig.isFingerDrawingEnabled) "Touch: ON" else "Stylus Only", fontSize = 10.sp, color = if (state.palmConfig.isFingerDrawingEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
                    Text("Zoom: ${(state.viewportState.scale * 100).toInt()}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            // Vertical Layout
            Column(
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp).width(92.dp).heightIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.then(dragModifier).padding(bottom = 6.dp).width(44.dp).height(12.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.width(36.dp).height(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f), RoundedCornerShape(2.dp)))
                }

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { HandTool(state); PenTool(state) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { HighlighterTool(state); EraserTool(state) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { LassoTool(state); ShapeTool(state, showShapeMenu) { showShapeMenu = it } }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { TextTool(state); ImageTool(state, onOpenImagePicker) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { AudioTool(state, onOpenVoiceRecorder); FingerTool(state) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ColorTool(state, presetColors, showColorMenu) { showColorMenu = it }
                        WidthTool(state, showWidthMenu) { showWidthMenu = it }
                    }
                    Box(modifier = Modifier.width(68.dp).height(1.dp).background(Color(0x1F000000)))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { UndoTool(state); RedoTool(state) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FitTool(state)
                        IconButton(onClick = { orientation = DesktopToolbarOrientation.HORIZONTAL }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.VerticalSplit, contentDescription = "Dock horizontal", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
