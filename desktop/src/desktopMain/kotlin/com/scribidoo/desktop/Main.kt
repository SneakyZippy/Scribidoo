package com.scribidoo.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.scribidoo.app.canvas.input.PalmRejectionConfig
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.desktop.dialogs.DesktopCalibrationDialog
import com.scribidoo.desktop.gallery.DesktopDocumentGalleryScreen
import com.scribidoo.desktop.workspace.DesktopCanvasWorkspaceScreen

fun main() = application {
    var activeDocument by remember { mutableStateOf<DocumentModel?>(null) }
    val windowTitle = if (activeDocument != null) "Scribidoo - ${activeDocument!!.title}" else "Scribidoo - Notebooks Gallery"

    Window(
        onCloseRequest = ::exitApplication,
        title = windowTitle,
        icon = painterResource("icon.png"),
        state = rememberWindowState(width = 1280.dp, height = 800.dp)
    ) {
        MaterialTheme {
            val currentDoc = activeDocument
            if (currentDoc != null) {
                DesktopCanvasWorkspaceScreen(
                    initialDocument = currentDoc,
                    window = window,
                    onNavigateBack = { activeDocument = null },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                DesktopDocumentGalleryScreen(
                    window = window,
                    onOpenDocument = { doc -> activeDocument = doc },
                    modifier = Modifier.fillMaxSize()
                )
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
        title = { Text(if (isEditing) "Edit Text Note" else "Add Text Note", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            Button(onClick = { onSave(text, fontSize) }, enabled = text.isNotBlank()) { Text("Done") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isEditing && onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
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
    var layoutMode by remember { mutableStateOf(state.document.layoutMode) }
    var barrelAction by remember { mutableStateOf(state.palmConfig.stylusBarrelAction) }
    var pressureCurve by remember { mutableStateOf(state.palmConfig.stylusPressureCurve) }
    var showCalibration by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Canvas & Stylus Preferences", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Draw with Touch / Finger", fontSize = 13.sp)
                    Switch(checked = fingerDraw, onCheckedChange = { fingerDraw = it })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { layoutMode = CanvasLayoutMode.PAGED },
                        modifier = Modifier.weight(1f),
                        enabled = layoutMode != CanvasLayoutMode.PAGED
                    ) { Text("Paged (A4)") }

                    Button(
                        onClick = { layoutMode = CanvasLayoutMode.INFINITE },
                        modifier = Modifier.weight(1f),
                        enabled = layoutMode != CanvasLayoutMode.INFINITE
                    ) { Text("Infinite Board") }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Stylus Barrel Side-Button Action:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ERASER" to "Eraser", "LASSO" to "Lasso").forEach { (action, label) ->
                            FilterChip(
                                selected = barrelAction == action,
                                onClick = { barrelAction = action },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Stylus Pressure Curve:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("SOFT" to "Soft", "LINEAR" to "Linear", "FIRM" to "Firm", "OFF" to "Off").forEach { (curve, label) ->
                            FilterChip(
                                selected = pressureCurve == curve,
                                onClick = { pressureCurve = curve },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = { showCalibration = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Launch Input Calibration Wizard", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                state.palmConfig = state.palmConfig.copy(
                    isFingerDrawingEnabled = fingerDraw,
                    stylusBarrelAction = barrelAction,
                    stylusPressureCurve = pressureCurve
                )
                state.setLayoutMode(layoutMode)
                onDismiss()
            }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showCalibration) {
        DesktopCalibrationDialog(
            initialConfig = state.palmConfig,
            onDismiss = { showCalibration = false },
            onComplete = { newConfig ->
                state.palmConfig = newConfig
                fingerDraw = newConfig.isFingerDrawingEnabled
                barrelAction = newConfig.stylusBarrelAction
                pressureCurve = newConfig.stylusPressureCurve
                showCalibration = false
            }
        )
    }
}
