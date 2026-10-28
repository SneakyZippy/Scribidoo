package com.scribidoo.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.scribidoo.app.canvas.input.DominantHand
import com.scribidoo.app.canvas.input.PalmRejectionConfig
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.canvas.state.CanvasState
import kotlinx.coroutines.delay

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Scribidoo - Canvas for Windows Touch PC",
        state = WindowState(width = 1280.dp, height = 800.dp)
    ) {
        val savedDoc = remember { DesktopStorageManager.loadAutoSave() }
        val state = remember { CanvasState(initialDocument = savedDoc ?: DocumentModel()) }
        var showSettingsDialog by remember { mutableStateOf(false) }
        var showRenameDialog by remember { mutableStateOf(false) }
        var showExportDialog by remember { mutableStateOf(false) }
        var exportSuccessMessage by remember { mutableStateOf<String?>(null) }
        var renameInput by remember { mutableStateOf("") }

        var isPalmShieldVisible by remember { mutableStateOf(false) }
        var palmShieldHeightPx by remember { mutableFloatStateOf(260f) }

        // Autosave whenever document changes
        LaunchedEffect(state.document) {
            DesktopStorageManager.saveAutoSave(state.document)
        }

        // Initial page center fit
        LaunchedEffect(state.viewportState.containerSize) {
            if (state.viewportState.containerSize.width > 0 && state.viewportState.containerSize.height > 0) {
                val page = state.currentPage
                state.viewportState.centerAndFit(page.width, page.height, padding = 48f)
            }
        }

        MaterialTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                // 1. Edge-to-Edge Canvas
                DesktopCanvasView(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    isPalmShieldVisible = isPalmShieldVisible,
                    palmShieldHeightPx = palmShieldHeightPx,
                    onPalmShieldHeightChange = { palmShieldHeightPx = it }
                )

                // 2. Compact Document Capsule in Top Corner
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 16.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(18.dp))
                        .border(1.dp, Color(0x1F000000), RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    renameInput = state.document.title
                                    showRenameDialog = true
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.document.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename",
                                modifier = Modifier.size(14.dp).padding(start = 4.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(modifier = Modifier.height(18.dp).width(1.dp).background(Color(0x1F000000)))

                        // Page navigation
                        IconButton(
                            onClick = { state.prevPage() },
                            enabled = state.document.activePageIndex > 0,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous Page")
                        }

                        Text(
                            text = "${state.document.activePageIndex + 1}/${state.document.pages.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        IconButton(
                            onClick = { state.nextPage() },
                            enabled = state.document.activePageIndex < state.document.pages.size - 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next Page")
                        }

                        IconButton(
                            onClick = { state.addNewPage() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add New Page")
                        }

                        Box(modifier = Modifier.height(18.dp).width(1.dp).background(Color(0x1F000000)))

                        // Export Button
                        IconButton(
                            onClick = { showExportDialog = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export to PNG",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 3. Floating Tool Palette (Draggable, Horizontal / 2-column Vertical)
                DesktopToolPalette(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    isPalmShieldVisible = isPalmShieldVisible,
                    onTogglePalmShield = { isPalmShieldVisible = !isPalmShieldVisible },
                    onOpenSettings = { showSettingsDialog = true }
                )

                // 4. Text Note Input / Edit Dialog
                if (state.activeTextBoxForEdit != null) {
                    val targetBox = state.activeTextBoxForEdit!!
                    DesktopTextBoxDialog(
                        initialText = targetBox.text,
                        initialFontSize = targetBox.fontSizeSp,
                        isEditing = true,
                        onDismiss = { state.activeTextBoxForEdit = null },
                        onSave = { newText, newFontSize ->
                            state.updateTextBox(targetBox.id, newText, newFontSize, targetBox.colorArgb)
                            state.activeTextBoxForEdit = null
                        },
                        onDelete = {
                            state.deleteTextBox(targetBox.id)
                            state.activeTextBoxForEdit = null
                        }
                    )
                } else if (state.pendingTextInsertPoint != null) {
                    val insertPoint = state.pendingTextInsertPoint!!
                    DesktopTextBoxDialog(
                        initialText = "",
                        initialFontSize = 20f,
                        isEditing = false,
                        onDismiss = { state.pendingTextInsertPoint = null },
                        onSave = { newText, newFontSize ->
                            state.addTextBox(
                                canvasPoint = insertPoint,
                                text = newText,
                                fontSizeSp = newFontSize,
                                colorArgb = state.currentColor.toArgb()
                            )
                            state.pendingTextInsertPoint = null
                        }
                    )
                }

                // 5. Settings Dialog
                if (showSettingsDialog) {
                    DesktopSettingsDialog(
                        state = state,
                        onDismiss = { showSettingsDialog = false }
                    )
                }

                // 6. Rename Note Dialog
                if (showRenameDialog) {
                    AlertDialog(
                        onDismissRequest = { showRenameDialog = false },
                        title = { Text("Rename Note") },
                        text = {
                            OutlinedTextField(
                                value = renameInput,
                                onValueChange = { renameInput = it },
                                singleLine = true,
                                label = { Text("Title") }
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                if (renameInput.isNotBlank()) {
                                    state.renameDocument(renameInput)
                                }
                                showRenameDialog = false
                            }) {
                                Text("Done")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRenameDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // 7. Export Dialog
                if (showExportDialog) {
                    AlertDialog(
                        onDismissRequest = { showExportDialog = false },
                        title = { Text("Export Document") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Export high-resolution PNG images to Pictures/Scribidoo:", fontSize = 13.sp)
                                Button(
                                    onClick = {
                                        try {
                                            val file = DesktopExporter.exportPageToPng(state.currentPage, state.document.title)
                                            exportSuccessMessage = "Exported: ${file.name}"
                                        } catch (e: Exception) {
                                            exportSuccessMessage = "Export failed: ${e.message}"
                                        }
                                        showExportDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Export Current Page (PNG)")
                                }
                                Button(
                                    onClick = {
                                        try {
                                            val files = DesktopExporter.exportDocumentToPngs(state.document)
                                            exportSuccessMessage = "Exported ${files.size} pages to Pictures/Scribidoo"
                                        } catch (e: Exception) {
                                            exportSuccessMessage = "Export failed: ${e.message}"
                                        }
                                        showExportDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Export All Pages (PNG)")
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showExportDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // 8. Notification Toast / Banner
                if (exportSuccessMessage != null) {
                    LaunchedEffect(exportSuccessMessage) {
                        delay(3500)
                        exportSuccessMessage = null
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 18.dp)
                            .shadow(8.dp, RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        tonalElevation = 6.dp
                    ) {
                        Text(
                            text = exportSuccessMessage!!,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopTextBoxDialog(
    initialText: String,
    initialFontSize: Float,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: (text: String, fontSize: Float) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var text by remember { mutableStateOf(initialText) }
    var fontSize by remember { mutableFloatStateOf(initialFontSize) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Text Note" else "Add Text Note",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().width(320.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Type note text...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Size:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    listOf(14f to "Small", 20f to "Medium", 28f to "Large").forEach { (size, label) ->
                        FilterChip(
                            selected = fontSize == size,
                            onClick = { fontSize = size },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text, fontSize) },
                enabled = text.isNotBlank()
            ) {
                Text("Done")
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditing && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun DesktopSettingsDialog(
    state: CanvasState,
    onDismiss: () -> Unit
) {
    var fingerDraw by remember { mutableStateOf(state.palmConfig.isFingerDrawingEnabled) }
    var touchSize by remember { mutableFloatStateOf(state.palmConfig.touchSizeThreshold) }
    var touchMajor by remember { mutableFloatStateOf(state.palmConfig.touchMajorThresholdPx) }
    var shieldRatio by remember { mutableFloatStateOf(state.palmConfig.palmShieldBottomRatio) }
    var dominantHand by remember { mutableStateOf(state.palmConfig.dominantHand) }
    var layoutMode by remember { mutableStateOf(state.document.layoutMode) }

    val onResetDefaults = {
        val defaults = PalmRejectionConfig()
        fingerDraw = defaults.isFingerDrawingEnabled
        touchSize = defaults.touchSizeThreshold
        touchMajor = defaults.touchMajorThresholdPx
        shieldRatio = defaults.palmShieldBottomRatio
        dominantHand = defaults.dominantHand
        layoutMode = CanvasLayoutMode.PAGED
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Canvas & Input Preferences",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.width(360.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Draw with Touch / Finger", fontSize = 13.sp)
                    Switch(checked = fingerDraw, onCheckedChange = { fingerDraw = it })
                }

                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Contact Size Filter", fontSize = 12.sp)
                        Text("${(touchSize * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(value = touchSize, onValueChange = { touchSize = it }, valueRange = 0.15f..0.60f)
                }

                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Palm Shield Bottom Zone", fontSize = 12.sp)
                        Text("${(shieldRatio * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(value = shieldRatio, onValueChange = { shieldRatio = it }, valueRange = 0.0f..0.45f)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { layoutMode = CanvasLayoutMode.PAGED },
                        modifier = Modifier.weight(1f),
                        enabled = layoutMode != CanvasLayoutMode.PAGED
                    ) {
                        Text("Paged (A4)")
                    }
                    Button(
                        onClick = { layoutMode = CanvasLayoutMode.INFINITE },
                        modifier = Modifier.weight(1f),
                        enabled = layoutMode != CanvasLayoutMode.INFINITE
                    ) {
                        Text("Infinite Board")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
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
            }) {
                Text("Apply & Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
