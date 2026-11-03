package com.scribidoo.desktop.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.input.DominantHand
import com.scribidoo.app.canvas.input.PalmRejectionConfig
import com.scribidoo.app.onboarding.CalibrationMath
import com.scribidoo.app.onboarding.TouchSample

enum class DesktopCalibrationStep {
    INTRO,
    DRAW_TIP,
    ZOOM_PINCH,
    COMPLETE
}

@Composable
fun DesktopCalibrationDialog(
    initialConfig: PalmRejectionConfig,
    onDismiss: () -> Unit,
    onComplete: (PalmRejectionConfig) -> Unit
) {
    var step by remember { mutableStateOf(DesktopCalibrationStep.INTRO) }
    var dominantHand by remember { mutableStateOf(initialConfig.dominantHand) }
    var fingerDraw by remember { mutableStateOf(initialConfig.isFingerDrawingEnabled) }
    val barrelAction by remember { mutableStateOf(initialConfig.stylusBarrelAction) }
    var pressureCurve by remember { mutableStateOf(initialConfig.stylusPressureCurve) }

    val capturedSamples = remember { mutableStateListOf<TouchSample>() }
    val capturedZoomSamples = remember { mutableStateListOf<TouchSample>() }
    val drawnStrokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = when (step) {
                        DesktopCalibrationStep.INTRO -> "Input & Touch Setup"
                        DesktopCalibrationStep.DRAW_TIP -> "Step 1: Drawing Contact & Pressure"
                        DesktopCalibrationStep.ZOOM_PINCH -> "Step 2: Zoom & Navigation"
                        DesktopCalibrationStep.COMPLETE -> "Calibration Complete"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (step) {
                    DesktopCalibrationStep.INTRO -> {
                        Text("Configure drawing input for your touchscreen, stylus, or trackpad:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Draw with Touch / Finger", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("When disabled, fingers only pan & navigate", fontSize = 10.sp, color = Color.Gray)
                            }
                            Switch(checked = fingerDraw, onCheckedChange = { fingerDraw = it })
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Writing Hand:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = dominantHand == DominantHand.RIGHT_HANDED, onClick = { dominantHand = DominantHand.RIGHT_HANDED }, label = { Text("Right-Handed") })
                                FilterChip(selected = dominantHand == DominantHand.LEFT_HANDED, onClick = { dominantHand = DominantHand.LEFT_HANDED }, label = { Text("Left-Handed") })
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Stylus Pressure Curve:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("SOFT" to "Soft", "LINEAR" to "Linear", "FIRM" to "Firm", "OFF" to "Off").forEach { (curve, label) ->
                                    FilterChip(selected = pressureCurve == curve, onClick = { pressureCurve = curve }, label = { Text(label, fontSize = 11.sp) })
                                }
                            }
                        }
                    }

                    DesktopCalibrationStep.DRAW_TIP -> {
                        Text("Draw a few strokes or dots below with your finger or stylus:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DesktopCalibrationDrawPad(
                            drawnStrokes = drawnStrokes,
                            currentStroke = currentStroke,
                            onCurrentStrokeChange = { currentStroke = it },
                            onStrokeFinished = {
                                drawnStrokes.add(it)
                                if (capturedSamples.size >= 4) {
                                    CalibrationMath.calculateCalibration(capturedSamples, capturedZoomSamples)
                                }
                            },
                            onSampleCaptured = { capturedSamples.add(it) }
                        )
                        Text("Samples captured: ${capturedSamples.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    DesktopCalibrationStep.ZOOM_PINCH -> {
                        Text("Test zoom responsiveness below (Trackpad pinch, mouse wheel, or buttons):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DesktopCalibrationZoomPad(
                            zoomScale = zoomScale,
                            onZoomScaleChange = { zoomScale = it },
                            onSampleCaptured = { capturedZoomSamples.add(it) }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    zoomScale = (zoomScale * 0.85f).coerceIn(0.5f, 2.5f)
                                    capturedZoomSamples.add(TouchSample(100f, 100f, 110f, 0.12f, 0.5f))
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
                            }

                            FilledTonalButton(
                                onClick = {
                                    zoomScale = 1.0f
                                    capturedZoomSamples.add(TouchSample(100f, 100f, 110f, 0.12f, 0.5f))
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("1.0x", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    zoomScale = (zoomScale * 1.2f).coerceIn(0.5f, 2.5f)
                                    capturedZoomSamples.add(TouchSample(100f, 100f, 110f, 0.12f, 0.5f))
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
                            }
                        }

                        Text("On Windows laptops: zoom with trackpad pinch, double-tap, or the on-canvas zoom pill.", fontSize = 11.sp, color = Color.Gray)
                    }

                    DesktopCalibrationStep.COMPLETE -> {
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text("Input Configured Successfully", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Text("• Touch drawing: ${if (fingerDraw) "Enabled" else "Disabled (Stylus Only)"}", fontSize = 11.sp)
                                Text("• Writing hand: ${if (dominantHand == DominantHand.RIGHT_HANDED) "Right-handed" else "Left-handed"}", fontSize = 11.sp)
                                Text("• Stylus pressure curve: $pressureCurve", fontSize = 11.sp)
                                Text("• Zoom & Navigation: Verified (${String.format("%.1fx", zoomScale)})", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                DesktopCalibrationStep.INTRO -> Button(onClick = { step = DesktopCalibrationStep.DRAW_TIP }) { Text("Next: Draw") }
                DesktopCalibrationStep.DRAW_TIP -> Button(onClick = { step = DesktopCalibrationStep.ZOOM_PINCH }) { Text("Next: Zoom") }
                DesktopCalibrationStep.ZOOM_PINCH -> Button(onClick = { step = DesktopCalibrationStep.COMPLETE }) { Text("Review") }
                DesktopCalibrationStep.COMPLETE -> Button(onClick = {
                    val updated = initialConfig.copy(
                        isFingerDrawingEnabled = fingerDraw,
                        dominantHand = dominantHand,
                        stylusPressureCurve = pressureCurve,
                        stylusBarrelAction = barrelAction
                    )
                    onComplete(updated)
                    onDismiss()
                }) { Text("Save & Apply") }
            }
        },
        dismissButton = {
            if (step == DesktopCalibrationStep.DRAW_TIP) {
                TextButton(onClick = { capturedSamples.clear(); drawnStrokes.clear(); currentStroke = emptyList() }) { Text("Clear") }
            } else if (step == DesktopCalibrationStep.ZOOM_PINCH) {
                TextButton(onClick = { zoomScale = 1f }) { Text("Reset Zoom") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
