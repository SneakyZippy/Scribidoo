package com.scribidoo.desktop.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.state.CanvasState

@Composable
fun DesktopZoomPill(
    state: CanvasState,
    modifier: Modifier = Modifier
) {
    val scale = state.viewportState.scale
    val percent = (scale * 100).toInt()

    Surface(
        modifier = modifier.shadow(6.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = {
                    val focal = Offset(
                        state.viewportState.containerSize.width / 2f,
                        state.viewportState.containerSize.height / 2f
                    )
                    state.viewportState.zoomBy(0.85f, focal)
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.clickable {
                    val page = state.currentPage
                    state.viewportState.centerAndFit(page.width, page.height, 48f)
                }
            ) {
                Text(
                    text = "$percent%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            IconButton(
                onClick = {
                    val focal = Offset(
                        state.viewportState.containerSize.width / 2f,
                        state.viewportState.containerSize.height / 2f
                    )
                    state.viewportState.zoomBy(1.2f, focal)
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
            }

            IconButton(
                onClick = {
                    val page = state.currentPage
                    state.viewportState.centerAndFit(page.width, page.height, 48f)
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.FitScreen, contentDescription = "Fit to Screen", modifier = Modifier.size(16.dp))
            }
        }
    }
}
