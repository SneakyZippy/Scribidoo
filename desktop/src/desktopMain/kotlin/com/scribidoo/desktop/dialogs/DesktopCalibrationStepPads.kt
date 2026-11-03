package com.scribidoo.desktop.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.onboarding.TouchSample
import kotlin.math.hypot

@Composable
fun DesktopCalibrationDrawPad(
    drawnStrokes: List<List<Offset>>,
    currentStroke: List<Offset>,
    onCurrentStrokeChange: (List<Offset>) -> Unit,
    onStrokeFinished: (List<Offset>) -> Unit,
    onSampleCaptured: (TouchSample) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFAFAFA))
            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val pt = change.position

                        when (event.type) {
                            PointerEventType.Press -> {
                                onCurrentStrokeChange(listOf(pt))
                                onSampleCaptured(TouchSample(pt.x, pt.y, 60f, 0.08f, change.pressure))
                            }
                            PointerEventType.Move -> {
                                onCurrentStrokeChange(currentStroke + pt)
                                onSampleCaptured(TouchSample(pt.x, pt.y, 60f, 0.08f, change.pressure))
                            }
                            PointerEventType.Release -> {
                                onStrokeFinished(currentStroke)
                                onCurrentStrokeChange(emptyList())
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val all = drawnStrokes + listOfNotNull(currentStroke.takeIf { it.isNotEmpty() })
            for (s in all) {
                if (s.size > 1) {
                    val p = Path().apply {
                        moveTo(s.first().x, s.first().y)
                        for (i in 1 until s.size) lineTo(s[i].x, s[i].y)
                    }
                    drawPath(p, Color(0xFF2563EB), style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        if (drawnStrokes.isEmpty() && currentStroke.isEmpty()) {
            Text("Draw strokes inside this test pad", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun DesktopCalibrationZoomPad(
    zoomScale: Float,
    onZoomScaleChange: (Float) -> Unit,
    onSampleCaptured: (TouchSample) -> Unit,
    modifier: Modifier = Modifier
) {
    var lastTwoFingerSpan by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFAFAFA))
            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                val pointers = mutableMapOf<PointerId, Offset>()
                var lastTap = 0L
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        for (c in event.changes) {
                            if (c.pressed) pointers[c.id] = c.position else pointers.remove(c.id)
                        }
                        if (pointers.size >= 2) {
                            val p = pointers.values.toList()
                            val span = hypot(p[1].x - p[0].x, p[1].y - p[0].y).coerceAtLeast(10f)
                            if (lastTwoFingerSpan > 0f) {
                                val newScale = (zoomScale * (span / lastTwoFingerSpan)).coerceIn(0.5f, 2.5f)
                                onZoomScaleChange(newScale)
                                onSampleCaptured(TouchSample(p[0].x, p[0].y, 110f, 0.12f, 0.5f))
                            }
                            lastTwoFingerSpan = span
                        } else {
                            lastTwoFingerSpan = 0f
                        }
                        if (event.type == PointerEventType.Scroll) {
                            val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            val factor = if (dy < 0) 1.15f else 0.87f
                            val newScale = (zoomScale * factor).coerceIn(0.5f, 2.5f)
                            onZoomScaleChange(newScale)
                            onSampleCaptured(TouchSample(100f, 100f, 110f, 0.12f, 0.5f))
                        } else if (event.type == PointerEventType.Press) {
                            val now = System.currentTimeMillis()
                            if (now - lastTap < 320L) {
                                val newScale = if (zoomScale > 1.1f) 1.0f else 1.5f
                                onZoomScaleChange(newScale)
                                onSampleCaptured(TouchSample(100f, 100f, 110f, 0.12f, 0.5f))
                                lastTap = 0L
                            } else lastTap = now
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size((56 * zoomScale).dp.coerceIn(32.dp, 110.dp))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ZoomIn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(String.format("%.1fx", zoomScale), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    onZoomScaleChange((zoomScale * 0.85f).coerceIn(0.5f, 2.5f))
                    onSampleCaptured(TouchSample(100f, 100f, 110f, 0.12f, 0.5f))
                },
                modifier = Modifier.height(28.dp)
            ) { Text("-", fontSize = 13.sp, fontWeight = FontWeight.Bold) }

            Button(
                onClick = {
                    onZoomScaleChange(1.0f)
                    onSampleCaptured(TouchSample(100f, 100f, 110f, 0.12f, 0.5f))
                },
                modifier = Modifier.height(28.dp)
            ) { Text("1.0x", fontSize = 11.sp) }

            Button(
                onClick = {
                    onZoomScaleChange((zoomScale * 1.2f).coerceIn(0.5f, 2.5f))
                    onSampleCaptured(TouchSample(100f, 100f, 110f, 0.12f, 0.5f))
                },
                modifier = Modifier.height(28.dp)
            ) { Text("+", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
