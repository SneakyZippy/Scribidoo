package com.scribidoo.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.desktop.toolbar.AudioTool
import com.scribidoo.desktop.toolbar.EraserTool
import com.scribidoo.desktop.toolbar.FingerTool
import com.scribidoo.desktop.toolbar.HandTool
import com.scribidoo.desktop.toolbar.HighlighterTool
import com.scribidoo.desktop.toolbar.ImageTool
import com.scribidoo.desktop.toolbar.LassoTool
import com.scribidoo.desktop.toolbar.PenTool
import com.scribidoo.desktop.toolbar.RedoTool
import com.scribidoo.desktop.toolbar.ShapeTool
import com.scribidoo.desktop.toolbar.TextTool
import com.scribidoo.desktop.toolbar.UndoTool

@Composable
fun DesktopRibbonToolbar(
    state: CanvasState,
    onNavigateBack: () -> Unit,
    onRenameClick: () -> Unit,
    onSaveClick: () -> Unit,
    onOpenImagePicker: () -> Unit,
    onOpenVoiceRecorder: () -> Unit,
    onOpenCalibration: () -> Unit,
    onExportPngCurrent: () -> Unit,
    onExportPngAll: () -> Unit,
    onExportPdf: () -> Unit,
    onImportPdf: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    var showShapeMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = Color(0x1F000000)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Back button
            IconButton(onClick = onNavigateBack, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Notebooks",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 2. Document Title
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onRenameClick() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = state.document.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Rename",
                    modifier = Modifier.size(12.dp),
                    tint = Color.Gray
                )
            }

            // Save button
            IconButton(onClick = onSaveClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Save, contentDescription = "Save (Ctrl+S)", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }

            RibbonSeparator()

            // 3. Primary Drawing & Selection Tools
            HandTool(state)
            PenTool(state)
            HighlighterTool(state)
            EraserTool(state)
            LassoTool(state)
            ShapeTool(state, showShapeMenu) { showShapeMenu = it }
            TextTool(state)
            ImageTool(state, onOpenImagePicker)
            AudioTool(state, onOpenVoiceRecorder)
            FingerTool(state)

            RibbonSeparator()

            // 4. Color & Width Properties
            RibbonColorChips(state)
            Spacer(modifier = Modifier.width(2.dp))
            RibbonStrokeWidthChips(state)

            RibbonSeparator()

            // 5. Undo / Redo
            UndoTool(state)
            RedoTool(state)

            RibbonSeparator()

            // 6. Zoom & Fit
            RibbonZoomControls(state, onFitWindow = {
                val page = state.currentPage
                state.viewportState.centerAndFit(page.width, page.height, padding = 48f)
            })

            // 7. Calibration Shortcut
            IconButton(onClick = onOpenCalibration, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Tune, contentDescription = "Input & Touch Calibration", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 8. Overflow Menu
            Box {
                IconButton(onClick = { showMoreMenu = !showMoreMenu }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More Options", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Export Current Page (PNG)", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(16.dp)) },
                        onClick = { showMoreMenu = false; onExportPngCurrent() }
                    )
                    DropdownMenuItem(
                        text = { Text("Export All Pages (PNG)", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(16.dp)) },
                        onClick = { showMoreMenu = false; onExportPngAll() }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Document to PDF", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp)) },
                        onClick = { showMoreMenu = false; onExportPdf() }
                    )
                    DropdownMenuItem(
                        text = { Text("Import & Annotate PDF...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(16.dp)) },
                        onClick = { showMoreMenu = false; onImportPdf() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Export JSON Backup", fontSize = 13.sp) },
                        onClick = { showMoreMenu = false; onExportJson() }
                    )
                    DropdownMenuItem(
                        text = { Text("Import JSON Backup", fontSize = 13.sp) },
                        onClick = { showMoreMenu = false; onImportJson() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            val modeLabel = if (state.document.layoutMode == CanvasLayoutMode.PAGED) "Switch to Infinite Canvas" else "Switch to A4 Paged Mode"
                            Text(modeLabel, fontSize = 13.sp)
                        },
                        onClick = {
                            val newMode = if (state.document.layoutMode == CanvasLayoutMode.PAGED) CanvasLayoutMode.INFINITE else CanvasLayoutMode.PAGED
                            state.setLayoutMode(newMode)
                            showMoreMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear Current Page", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.CleaningServices, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                        onClick = { state.clearPage(); showMoreMenu = false }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Canvas Settings...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp)) },
                        onClick = { showMoreMenu = false; onOpenSettings() }
                    )
                }
            }
        }
    }
}

@Composable
private fun RibbonSeparator() {
    Box(
        modifier = Modifier
            .height(22.dp)
            .width(1.dp)
            .background(Color(0x22000000))
    )
}
