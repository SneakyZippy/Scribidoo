package com.scribidoo.app.onboarding

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.scribidoo.app.canvas.input.DominantHand
import com.scribidoo.app.canvas.input.PalmRejectionConfig
import com.scribidoo.app.preference.UserPreferencesManager

private enum class CalibrationStep {
    INTRO,
    DRAW_TIP,
    ZOOM_PINCH,
    COMPLETE
}

enum class BufferStrictness(val label: String, val factor: Float, val subtitle: String) {
    STRICT("Strict (+15%)", 1.15f, "Most aggressive palm rejection"),
    BALANCED("Balanced (+25%)", 1.25f, "Tight buffer with zero missed strokes"),
    RELAXED("Relaxed (+35%)", 1.35f, "More forgiving for tilted finger drawing")
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OnboardingCalibrationDialog(
    initialConfig: PalmRejectionConfig,
    onDismiss: () -> Unit,
    onComplete: (PalmRejectionConfig) -> Unit
) {
    val context = LocalContext.current
    val prefManager = remember { UserPreferencesManager(context) }

    var step by remember { mutableStateOf(CalibrationStep.INTRO) }
    var dominantHand by remember { mutableStateOf(initialConfig.dominantHand) }
    var isFingerDrawing by remember { mutableStateOf(initialConfig.isFingerDrawingEnabled) }
    var selectedStrictness by remember { mutableStateOf(BufferStrictness.BALANCED) }

    val capturedSamples = remember { mutableStateListOf<TouchSample>() }
    val capturedZoomSamples = remember { mutableStateListOf<TouchSample>() }
    val drawnStrokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var initialSpan by remember { mutableFloatStateOf(0f) }

    var calibrationResult by remember {
        mutableStateOf(CalibrationMath.calculateCalibration(emptyList()))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = when (step) {
                        CalibrationStep.INTRO -> "Welcome & Input Setup"
                        CalibrationStep.DRAW_TIP -> "Step 1: Tip Drawing Size"
                        CalibrationStep.ZOOM_PINCH -> "Step 2: Pinch to Zoom Size"
                        CalibrationStep.COMPLETE -> "Calibration Complete"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (step) {
                    CalibrationStep.INTRO -> {
                        Text(
                            text = "Calibrate palm rejection and drawing behavior to match your screen and writing style.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text("Writing Hand", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HandOptionButton("Right-Handed", dominantHand == DominantHand.RIGHT_HANDED, { dominantHand = DominantHand.RIGHT_HANDED }, Modifier.weight(1f))
                            HandOptionButton("Left-Handed", dominantHand == DominantHand.LEFT_HANDED, { dominantHand = DominantHand.LEFT_HANDED }, Modifier.weight(1f))
                        }

                        Text("Primary Drawing Method", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HandOptionButton("Finger / Passive Pen", isFingerDrawing, { isFingerDrawing = true }, Modifier.weight(1f))
                            HandOptionButton("Active Stylus Only", !isFingerDrawing, { isFingerDrawing = false }, Modifier.weight(1f))
                        }

                        if (!isFingerDrawing) {
                            Text("Active pens (S-Pen, USI) are identified by digitizer hardware. Finger touches will only pan and pinch-zoom.", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    CalibrationStep.DRAW_TIP -> {
                        Text(
                            text = "Use your finger to draw (try to use only the tip) or use a passive pen (no active stylus) in the pad below:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFFAFAFA))
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .pointerInteropFilter { event ->
                                    for (i in 0 until event.pointerCount) {
                                        capturedSamples.add(TouchSample(event.getX(i), event.getY(i), event.getTouchMajor(i), event.getSize(i), event.getPressure(i)))
                                    }
                                    val pt = Offset(event.x, event.y)
                                    when (event.actionMasked) {
                                        MotionEvent.ACTION_DOWN -> currentStroke = listOf(pt)
                                        MotionEvent.ACTION_MOVE -> currentStroke = currentStroke + pt
                                        MotionEvent.ACTION_UP -> { drawnStrokes.add(currentStroke + pt); currentStroke = emptyList() }
                                        MotionEvent.ACTION_CANCEL -> { if (currentStroke.isNotEmpty()) drawnStrokes.add(currentStroke); currentStroke = emptyList() }
                                    }
                                    if (capturedSamples.size >= 4) {
                                        calibrationResult = CalibrationMath.calculateCalibration(capturedSamples, capturedZoomSamples, selectedStrictness.factor)
                                    }
                                    true
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                                val allStrokes = drawnStrokes + listOfNotNull(currentStroke.takeIf { it.isNotEmpty() })
                                for (stroke in allStrokes) {
                                    if (stroke.size == 1) {
                                        drawCircle(Color(0xFF2563EB), radius = 4f, center = stroke.first())
                                    } else if (stroke.size > 1) {
                                        val path = Path().apply {
                                            moveTo(stroke.first().x, stroke.first().y)
                                            for (i in 1 until stroke.size) lineTo(stroke[i].x, stroke[i].y)
                                        }
                                        drawPath(path, Color(0xFF2563EB), style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                    }
                                }
                            }
                            if (capturedSamples.isEmpty()) {
                                Text("Draw strokes or dots using only the tip", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Samples: ${capturedSamples.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (capturedSamples.size >= 4) {
                                Text("Measured Tip: ~${calibrationResult.measuredMajorPx.toInt()} px", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    CalibrationStep.ZOOM_PINCH -> {
                        Text(
                            text = "Now pinch with two fingers (thumb & index) to zoom in and out to calibrate your multi-touch size:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFFAFAFA))
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .pointerInteropFilter { event ->
                                    if (event.pointerCount >= 2) {
                                        val p0 = Offset(event.getX(0), event.getY(0))
                                        val p1 = Offset(event.getX(1), event.getY(1))
                                        val span = kotlin.math.hypot(p1.x - p0.x, p1.y - p0.y).coerceAtLeast(10f)

                                        for (i in 0 until 2) {
                                            capturedZoomSamples.add(TouchSample(event.getX(i), event.getY(i), event.getTouchMajor(i), event.getSize(i), event.getPressure(i)))
                                        }

                                        when (event.actionMasked) {
                                            MotionEvent.ACTION_POINTER_DOWN -> initialSpan = span
                                            MotionEvent.ACTION_MOVE -> {
                                                if (initialSpan > 0f) {
                                                    val ratio = span / initialSpan
                                                    zoomScale = (zoomScale * (1f + (ratio - 1f) * 0.12f)).coerceIn(0.6f, 2.0f)
                                                }
                                            }
                                        }
                                        calibrationResult = CalibrationMath.calculateCalibration(capturedSamples, capturedZoomSamples, selectedStrictness.factor)
                                    }
                                    true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((56 * zoomScale).dp.coerceIn(36.dp, 100.dp))
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ZoomIn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Text(String.format("%.1fx", zoomScale), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Zoom samples: ${capturedZoomSamples.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (capturedZoomSamples.isNotEmpty()) {
                                Text("Zoom contact: ~${calibrationResult.measuredZoomMajorPx.toInt()} px", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    CalibrationStep.COMPLETE -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Calibrated Successfully", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Text("• Tip drawing limit: ${calibrationResult.recommendedMajorThresholdPx.toInt()} px (Buffer: ${selectedStrictness.label})", fontSize = 11.sp)
                                Text("• Two-finger zoom limit: ${calibrationResult.recommendedZoomMajorThresholdPx.toInt()} px", fontSize = 11.sp)
                                Text("• Writing hand: ${if (dominantHand == DominantHand.RIGHT_HANDED) "Right-handed" else "Left-handed"}", fontSize = 11.sp)
                            }
                        }

                        Text("Palm Rejection Buffer Strictness", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BufferStrictness.values().forEach { strictness ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedStrictness == strictness) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .clickable {
                                            selectedStrictness = strictness
                                            calibrationResult = CalibrationMath.calculateCalibration(capturedSamples, capturedZoomSamples, strictness.factor)
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = strictness.label,
                                        fontSize = 10.sp,
                                        fontWeight = if (selectedStrictness == strictness) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedStrictness == strictness) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Text(selectedStrictness.subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                CalibrationStep.INTRO -> {
                    Button(onClick = { step = if (isFingerDrawing) CalibrationStep.DRAW_TIP else CalibrationStep.COMPLETE }) {
                        Text(if (isFingerDrawing) "Next: Draw with Tip" else "Done")
                    }
                }
                CalibrationStep.DRAW_TIP -> {
                    Button(onClick = { step = CalibrationStep.ZOOM_PINCH }, enabled = capturedSamples.size >= 4) {
                        Text("Next: Pinch to Zoom")
                    }
                }
                CalibrationStep.ZOOM_PINCH -> {
                    Button(onClick = { step = CalibrationStep.COMPLETE }) {
                        Text("Review Calibration")
                    }
                }
                CalibrationStep.COMPLETE -> {
                    Button(
                        onClick = {
                            val updatedConfig = initialConfig.copy(
                                isFingerDrawingEnabled = isFingerDrawing,
                                dominantHand = dominantHand,
                                touchMajorThresholdPx = calibrationResult.recommendedMajorThresholdPx,
                                touchSizeThreshold = calibrationResult.recommendedSizeThreshold,
                                twoFingerMajorThresholdPx = calibrationResult.recommendedZoomMajorThresholdPx
                            )
                            prefManager.savePalmConfig(updatedConfig)
                            prefManager.saveCalibratedTipMetrics(calibrationResult.measuredMajorPx, calibrationResult.measuredSize)
                            prefManager.hasCompletedOnboarding = true
                            onComplete(updatedConfig)
                            onDismiss()
                        }
                    ) {
                        Text("Save & Start")
                    }
                }
            }
        },
        dismissButton = {
            if (step == CalibrationStep.DRAW_TIP) {
                TextButton(onClick = { capturedSamples.clear(); drawnStrokes.clear(); currentStroke = emptyList() }) { Text("Clear") }
            } else if (step == CalibrationStep.ZOOM_PINCH) {
                TextButton(onClick = { capturedZoomSamples.clear(); zoomScale = 1f }) { Text("Reset Zoom") }
            } else {
                TextButton(onClick = onDismiss) { Text("Skip / Close") }
            }
        }
    )
}

@Composable
private fun HandOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(width = if (selected) 2.dp else 1.dp, color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape = RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
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
