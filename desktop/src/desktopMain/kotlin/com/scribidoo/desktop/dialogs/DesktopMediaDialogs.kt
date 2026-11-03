package com.scribidoo.desktop.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.desktop.media.DesktopAudioPlayer
import com.scribidoo.desktop.media.DesktopAudioRecorder
import com.scribidoo.desktop.media.DesktopImageUtils
import kotlinx.coroutines.delay
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun DesktopImagePickerDialog(
    onDismiss: () -> Unit,
    onImageSelected: (filePath: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Insert Picture", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Select an image file (PNG, JPG, WebP) from your computer to place on the canvas:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        val dialog = FileDialog(null as Frame?, "Select Image File", FileDialog.LOAD)
                        dialog.setFilenameFilter { _, name ->
                            val lower = name.lowercase()
                            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
                        }
                        dialog.isVisible = true
                        val selected = dialog.file?.let { File(dialog.directory, it) }
                        if (selected != null && selected.exists()) {
                            val localCopy = DesktopImageUtils.copyImageToLocalStorage(selected)
                            if (localCopy != null) {
                                onImageSelected(localCopy.absolutePath)
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose Image from PC...")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun DesktopVoiceRecorderDialog(
    recorder: DesktopAudioRecorder,
    player: DesktopAudioPlayer,
    onDismiss: () -> Unit,
    onAttach: (filePath: String, durationMs: Long, title: String) -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var durationSec by remember { mutableIntStateOf(0) }
    var noteTitle by remember { mutableStateOf("Voice Note") }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            durationSec = 0
            while (isRecording) {
                delay(1000)
                durationSec += 1
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) recorder.stopRecording()
            player.stop()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (isRecording) recorder.stopRecording()
            player.stop()
            onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Record Audio Memo", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    label = { Text("Label (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val mins = durationSec / 60
                val secs = durationSec % 60
                Text(
                    text = String.format("%02d:%02d", mins, secs),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRecording) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!isRecording && recordedFile == null) {
                        Button(
                            onClick = {
                                val file = recorder.startRecording()
                                if (file != null) {
                                    recordedFile = file
                                    isRecording = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Record")
                        }
                    } else if (isRecording) {
                        Button(
                            onClick = {
                                recorder.stopRecording()
                                isRecording = false
                            }
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop")
                        }
                    } else if (recordedFile != null) {
                        IconButton(
                            onClick = {
                                if (player.isPlaying) player.pause()
                                else player.play(recordedFile!!.absolutePath)
                            }
                        ) {
                            Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Preview")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val file = recordedFile
                    if (file != null && file.exists()) {
                        val durationMs = (durationSec * 1000L).coerceAtLeast(1000L)
                        onAttach(file.absolutePath, durationMs, noteTitle.ifBlank { "Voice Note" })
                        onDismiss()
                    }
                },
                enabled = recordedFile != null && !isRecording
            ) {
                Text("Insert Note")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                recordedFile?.delete()
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}
