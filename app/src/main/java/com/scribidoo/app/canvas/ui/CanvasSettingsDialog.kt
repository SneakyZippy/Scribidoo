package com.scribidoo.app.canvas.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.input.DominantHand
import com.scribidoo.app.canvas.input.PalmRejectionConfig
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.state.CanvasState

@Composable
fun CanvasSettingsDialog(
    state: CanvasState,
    onDismiss: () -> Unit
) {
    var fingerDraw by remember { mutableStateOf(state.palmConfig.isFingerDrawingEnabled) }
    var touchSize by remember { mutableFloatStateOf(state.palmConfig.touchSizeThreshold) }
    var touchMajor by remember { mutableFloatStateOf(state.palmConfig.touchMajorThresholdPx) }
    var shieldRatio by remember { mutableFloatStateOf(state.palmConfig.palmShieldBottomRatio) }
    var dominantHand by remember { mutableStateOf(state.palmConfig.dominantHand) }
    var layoutMode by remember { mutableStateOf(state.document.layoutMode) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = if (isLandscape) 640.dp else 400.dp),
        title = {
            Text(
                text = "Canvas & Input Preferences",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            if (isLandscape) {
                // Wide landscape layout: 2 columns side-by-side (No vertical overflow!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Column 1: Palm Sliders & Handedness
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "PALM REJECTION SENSITIVITY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Contact Size Threshold Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Contact Size Filter", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("${(touchSize * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = touchSize,
                                onValueChange = { touchSize = it },
                                valueRange = 0.15f..0.60f
                            )
                        }

                        // Major Axis Limit Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Contact Diameter", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("${touchMajor.toInt()} px", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = touchMajor,
                                onValueChange = { touchMajor = it },
                                valueRange = 60f..260f
                            )
                        }

                        // Palm Shield Bottom Ratio Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Palm Shield Bottom Zone", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("${(shieldRatio * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = shieldRatio,
                                onValueChange = { shieldRatio = it },
                                valueRange = 0.0f..0.45f
                            )
                        }
                    }

                    // Column 2: Toggles, Dominant Hand, and Layout
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "INPUT & ORIENTATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Finger Draw Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Draw with Finger", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = fingerDraw,
                                onCheckedChange = { fingerDraw = it }
                            )
                        }

                        // Dominant Hand Selector
                        Text("Writing Hand", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
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

                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                        Text(
                            text = "CANVAS MODE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HandSelectorButton(
                                label = "Paged A4",
                                selected = layoutMode == CanvasLayoutMode.PAGED,
                                onClick = { layoutMode = CanvasLayoutMode.PAGED },
                                modifier = Modifier.weight(1f)
                            )
                            HandSelectorButton(
                                label = "Infinite",
                                selected = layoutMode == CanvasLayoutMode.INFINITE,
                                onClick = { layoutMode = CanvasLayoutMode.INFINITE },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else {
                // Portrait Layout
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Finger Drawing toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Draw with Finger / Passive Pen", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = fingerDraw,
                            onCheckedChange = { fingerDraw = it }
                        )
                    }

                    // Dominant Hand Selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Dominant Drawing Hand", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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

                    // Sliders
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Contact Size Threshold", fontSize = 12.sp)
                            Text("${(touchSize * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(value = touchSize, onValueChange = { touchSize = it }, valueRange = 0.15f..0.60f)
                    }

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Contact Diameter Limit", fontSize = 12.sp)
                            Text("${touchMajor.toInt()} px", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(value = touchMajor, onValueChange = { touchMajor = it }, valueRange = 60f..260f)
                    }

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Palm Shield Bottom Zone", fontSize = 12.sp)
                            Text("${(shieldRatio * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(value = shieldRatio, onValueChange = { shieldRatio = it }, valueRange = 0.0f..0.45f)
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    state.palmConfig = PalmRejectionConfig(
                        isFingerDrawingEnabled = fingerDraw,
                        touchSizeThreshold = touchSize,
                        touchMajorThresholdPx = touchMajor,
                        palmShieldBottomRatio = shieldRatio,
                        isPalmShieldActive = shieldRatio > 0.01f,
                        dominantHand = dominantHand
                    )
                    state.setLayoutMode(layoutMode)
                    onDismiss()
                }
            ) {
                Text("Apply & Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun HandSelectorButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
