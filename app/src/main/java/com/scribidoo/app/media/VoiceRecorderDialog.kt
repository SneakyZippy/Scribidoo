package com.scribidoo.app.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun VoiceRecorderDialog(
    onDismiss: () -> Unit,
    onAttach: (filePath: String, durationMs: Long, title: String) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    var isRecording by remember { mutableStateOf(false) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }

    // Playback preview state
    var isPlayingPreview by remember { mutableStateOf(false) }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var noteTitle by remember { mutableStateOf("Voice Note") }

    // Timer effect for active recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDurationSec = 0
            while (isRecording) {
                delay(1000)
                recordingDurationSec += 1
            }
        }
    }

    // Cleanup on dialog dismiss
    DisposableEffect(Unit) {
        onDispose {
            try {
                if (isRecording) {
                    mediaRecorder?.stop()
                }
                mediaRecorder?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                previewPlayer?.stop()
                previewPlayer?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (isRecording) {
                try {
                    mediaRecorder?.stop()
                    mediaRecorder?.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                recordedFile?.delete()
            }
            onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Record Voice Note", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!hasPermission) {
                    Text(
                        text = "Microphone access is required to record voice notes directly onto your canvas.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("Grant Microphone Access")
                    }
                } else {
                    // Title field
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Label (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Timer display
                    val mins = recordingDurationSec / 60
                    val secs = recordingDurationSec % 60
                    val timeFormatted = String.format("%02d:%02d", mins, secs)

                    Text(
                        text = timeFormatted,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRecording) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface
                    )

                    // Control buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isRecording && recordedFile == null) {
                            // Start Record Button
                            Button(
                                onClick = {
                                    val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
                                    val file = File(audioDir, "voice_${UUID.randomUUID()}.m4a")
                                    recordedFile = file
                                    try {
                                        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            MediaRecorder(context)
                                        } else {
                                            @Suppress("DEPRECATION")
                                            MediaRecorder()
                                        }
                                        recorder.apply {
                                            setAudioSource(MediaRecorder.AudioSource.MIC)
                                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                            setOutputFile(file.absolutePath)
                                            prepare()
                                            start()
                                        }
                                        mediaRecorder = recorder
                                        isRecording = true
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        recordedFile = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                            ) {
                                Icon(Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Record")
                            }
                        } else if (isRecording) {
                            // Stop Record Button
                            Button(
                                onClick = {
                                    try {
                                        mediaRecorder?.stop()
                                        mediaRecorder?.release()
                                        mediaRecorder = null
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    isRecording = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop")
                            }
                        } else {
                            // Recorded! Show Preview Play / Re-record
                            val file = recordedFile
                            if (file != null && file.exists()) {
                                IconButton(
                                    onClick = {
                                        if (isPlayingPreview) {
                                            previewPlayer?.pause()
                                            isPlayingPreview = false
                                        } else {
                                            if (previewPlayer == null) {
                                                previewPlayer = MediaPlayer().apply {
                                                    setDataSource(file.absolutePath)
                                                    prepare()
                                                    setOnCompletionListener {
                                                        isPlayingPreview = false
                                                    }
                                                }
                                            }
                                            previewPlayer?.start()
                                            isPlayingPreview = true
                                        }
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingPreview) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Preview Play",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                FilledTonalButton(
                                    onClick = {
                                        previewPlayer?.stop()
                                        previewPlayer?.release()
                                        previewPlayer = null
                                        isPlayingPreview = false
                                        recordedFile?.delete()
                                        recordedFile = null
                                        recordingDurationSec = 0
                                    }
                                ) {
                                    Text("Re-record")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (recordedFile != null && !isRecording) {
                Button(
                    onClick = {
                        val file = recordedFile!!
                        val duration = (recordingDurationSec * 1000L).coerceAtLeast(1000L)
                        onAttach(file.absolutePath, duration, noteTitle.ifBlank { "Voice Note" })
                        onDismiss()
                    }
                ) {
                    Text("Attach to Note")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isRecording) {
                        try {
                            mediaRecorder?.stop()
                            mediaRecorder?.release()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    recordedFile?.delete()
                    onDismiss()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}
