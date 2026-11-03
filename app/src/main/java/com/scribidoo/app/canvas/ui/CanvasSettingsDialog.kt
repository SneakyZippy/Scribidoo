package com.scribidoo.app.canvas.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.input.DominantHand
import com.scribidoo.app.canvas.input.PalmRejectionConfig
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.app.onboarding.OnboardingCalibrationDialog
import com.scribidoo.app.preference.UserPreferencesManager

@Composable
fun CanvasSettingsDialog(
    state: CanvasState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefManager = remember { UserPreferencesManager(context) }

    var fingerDraw by remember { mutableStateOf(state.palmConfig.isFingerDrawingEnabled) }
    var touchSize by remember { mutableFloatStateOf(state.palmConfig.touchSizeThreshold) }
    var touchMajor by remember { mutableFloatStateOf(state.palmConfig.touchMajorThresholdPx) }
    var dominantHand by remember { mutableStateOf(state.palmConfig.dominantHand) }
    var layoutMode by remember { mutableStateOf(state.document.layoutMode) }
    var constrainDrawing by remember { mutableStateOf(state.palmConfig.constrainDrawingToPage) }
    var isShapeRecognitionEnabled by remember { mutableStateOf(state.palmConfig.isShapeRecognitionEnabled) }
    var shapeHoldDurationMs by remember { mutableFloatStateOf(state.palmConfig.shapeHoldDurationMs.toFloat()) }
    var hiddenTools by remember { mutableStateOf(state.palmConfig.hiddenTools) }

    var showCalibrationDialog by remember { mutableStateOf(false) }
    val calibratedMetrics = remember { prefManager.loadCalibratedTipMetrics() }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val onResetDefaults = {
        val defaults = PalmRejectionConfig()
        fingerDraw = defaults.isFingerDrawingEnabled
        touchSize = defaults.touchSizeThreshold
        touchMajor = defaults.touchMajorThresholdPx
        dominantHand = defaults.dominantHand
        layoutMode = CanvasLayoutMode.PAGED
        constrainDrawing = false
        isShapeRecognitionEnabled = defaults.isShapeRecognitionEnabled
        shapeHoldDurationMs = defaults.shapeHoldDurationMs.toFloat()
        hiddenTools = emptySet()
    }

    if (showCalibrationDialog) {
        OnboardingCalibrationDialog(
            initialConfig = state.palmConfig,
            onDismiss = { showCalibrationDialog = false },
            onComplete = { newConfig ->
                state.palmConfig = newConfig
                fingerDraw = newConfig.isFingerDrawingEnabled
                touchSize = newConfig.touchSizeThreshold
                touchMajor = newConfig.touchMajorThresholdPx
                dominantHand = newConfig.dominantHand
                showCalibrationDialog = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = if (isLandscape) 640.dp else 420.dp),
        title = {
            Text(
                text = "Canvas & Input Preferences",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (isLandscape) 320.dp else 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Calibration Quick Launcher Banner
                OutlinedButton(
                    onClick = { showCalibrationDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Calibrate Touch Size & Palm Rejection", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            text = if (calibratedMetrics != null) "Current calibration: tip ~${calibratedMetrics.first.toInt()} px"
                            else "Tap to draw with tip and optimize thresholds",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                // 1. Drawing Tool Mode
                SettingSwitchRow(
                    title = "Draw with Finger / Passive Pen",
                    subtitle = "When disabled, only active digital pens (S-Pen, USI) ink; fingers pan & pinch-zoom.",
                    checked = fingerDraw,
                    onCheckedChange = { fingerDraw = it }
                )

                // 2. Writing Hand
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Dominant Writing Hand", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Aligns spatial palm rejection zone with where your hand rests on glass.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HandSelectorButton(
                            label = "Right-Handed",
                            selected = dominantHand == DominantHand.RIGHT_HANDED,
                            onClick = { dominantHand = DominantHand.RIGHT_HANDED },
                            modifier = Modifier.weight(1f)
                        )
                        HandSelectorButton(
                            label = "Left-Handed",
                            selected = dominantHand == DominantHand.LEFT_HANDED,
                            onClick = { dominantHand = DominantHand.LEFT_HANDED },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider()

                // 3. Palm Sensitivity Sliders with Explanations
                SettingSliderRow(
                    title = "Contact Size Filter",
                    subtitle = "Filters touches where contact patch exceeds this percentage. Lower values reject palms more aggressively.",
                    displayValue = "${(touchSize * 100).toInt()}%",
                    value = touchSize,
                    onValueChange = { touchSize = it },
                    valueRange = 0.15f..0.60f
                )

                SettingSliderRow(
                    title = "Contact Diameter Limit",
                    subtitle = "Maximum touch ellipse diameter (px). Fingertips are ~70-130px; resting palms typically exceed 180px.",
                    displayValue = "${touchMajor.toInt()} px",
                    value = touchMajor,
                    onValueChange = { touchMajor = it },
                    valueRange = 60f..260f
                )

                HorizontalDivider()

                // Smart Shape Recognition
                SettingSwitchRow(
                    title = "Draw-and-Hold Shape Recognition",
                    subtitle = "Draw rough shapes (lines, arrows, circles, rectangles, triangles) and hold to snap to geometry.",
                    checked = isShapeRecognitionEnabled,
                    onCheckedChange = { isShapeRecognitionEnabled = it }
                )

                if (isShapeRecognitionEnabled) {
                    SettingSliderRow(
                        title = "Hold Snap Duration",
                        subtitle = "Duration to pause stylus before snapping to shape.",
                        displayValue = "${shapeHoldDurationMs.toInt()} ms",
                        value = shapeHoldDurationMs,
                        onValueChange = { shapeHoldDurationMs = it },
                        valueRange = 300f..800f
                    )
                }

                HorizontalDivider()

                // 4. Paper Margins & Canvas Layout
                SettingSwitchRow(
                    title = "Constrain Drawing to Paper",
                    subtitle = "Clips ink to the page margins and enables panning by dragging margins outside paper.",
                    checked = constrainDrawing,
                    onCheckedChange = { constrainDrawing = it }
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Canvas Board Layout", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Paged A4 provides standard export pages; Infinite Board provides boundless whiteboard.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HandSelectorButton(
                            label = "Paged (A4)",
                            selected = layoutMode == CanvasLayoutMode.PAGED,
                            onClick = { layoutMode = CanvasLayoutMode.PAGED },
                            modifier = Modifier.weight(1f)
                        )
                        HandSelectorButton(
                            label = "Infinite Board",
                            selected = layoutMode == CanvasLayoutMode.INFINITE,
                            onClick = { layoutMode = CanvasLayoutMode.INFINITE },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider()

                // 5. Toolbar Tools Customization
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Toolbar Customization", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (hiddenTools.isEmpty()) "Long-press any tool icon on the palette to hide it."
                            else "${hiddenTools.size} tool(s) hidden. Long-press on toolbar or tap to restore:",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hiddenTools.isNotEmpty()) {
                        TextButton(onClick = { hiddenTools = emptySet() }) {
                            Text("Unhide All")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedConfig = state.palmConfig.copy(
                        isFingerDrawingEnabled = fingerDraw,
                        touchSizeThreshold = touchSize,
                        touchMajorThresholdPx = touchMajor,
                        dominantHand = dominantHand,
                        constrainDrawingToPage = constrainDrawing,
                        isShapeRecognitionEnabled = isShapeRecognitionEnabled,
                        shapeHoldDurationMs = shapeHoldDurationMs.toLong(),
                        hiddenTools = hiddenTools
                    )
                    state.palmConfig = updatedConfig
                    prefManager.savePalmConfig(updatedConfig)
                    state.setLayoutMode(layoutMode)
                    onDismiss()
                }
            ) {
                Text("Apply & Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onResetDefaults) {
                    Text("Reset Defaults", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
