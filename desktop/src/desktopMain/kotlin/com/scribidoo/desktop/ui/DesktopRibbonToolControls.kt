package com.scribidoo.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.state.CanvasState

val PRESET_RIBBON_COLORS = listOf(
    Color(0xFF1E293B), // Charcoal/Black
    Color(0xFF2563EB), // Blue
    Color(0xFFDC2626), // Red
    Color(0xFF059669), // Green
    Color(0xFF7C3AED), // Purple
    Color(0xFFF59E0B)  // Amber/Orange
)

val PRESET_STROKE_WIDTHS = listOf(
    2.5f to "Fine",
    5.0f to "Medium",
    10.0f to "Broad"
)

@Composable
fun RibbonColorChips(state: CanvasState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PRESET_RIBBON_COLORS.forEach { color ->
            val isSelected = state.currentColor.toArgb() == color.toArgb()
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x33000000),
                        shape = CircleShape
                    )
                    .clickable { state.currentColor = color },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        modifier = Modifier.size(13.dp),
                        tint = if (color == Color(0xFF1E293B) || color == Color(0xFF2563EB) || color == Color(0xFF7C3AED)) Color.White else Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun RibbonStrokeWidthChips(state: CanvasState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PRESET_STROKE_WIDTHS.forEach { (width, label) ->
            val isSelected = kotlin.math.abs(state.currentStrokeWidth - width) < 0.8f
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { state.currentStrokeWidth = width },
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun RibbonZoomControls(
    state: CanvasState,
    onFitWindow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scalePercent = (state.viewportState.scale * 100).toInt()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                val center = androidx.compose.ui.geometry.Offset(
                    state.viewportState.containerSize.width / 2f,
                    state.viewportState.containerSize.height / 2f
                )
                state.viewportState.zoomBy(1f / 1.15f, center)
            },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
        }

        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onFitWindow() },
            shape = RoundedCornerShape(6.dp),
            color = Color(0x0F000000)
        ) {
            Text(
                text = "${scalePercent}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }

        IconButton(
            onClick = {
                val center = androidx.compose.ui.geometry.Offset(
                    state.viewportState.containerSize.width / 2f,
                    state.viewportState.containerSize.height / 2f
                )
                state.viewportState.zoomBy(1.15f, center)
            },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
        }
    }
}
