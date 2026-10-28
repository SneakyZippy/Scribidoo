package com.scribidoo.app.media

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.model.AudioNoteModel
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun AudioExtendedMenuDialog(
    audioNote: AudioNoteModel,
    onDismiss: () -> Unit,
    onSaveTrimAndRename: (newTitle: String, trimStartMs: Long, trimEndMs: Long) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(audioNote.label) }
    val totalDurationMs = audioNote.durationMs.coerceAtLeast(1000L).toFloat()

    val initialStart = audioNote.trimStartMs.toFloat().coerceIn(0f, totalDurationMs)
    val initialEnd = (if (audioNote.trimEndMs > 0L) audioNote.trimEndMs.toFloat() else totalDurationMs)
        .coerceIn(initialStart, totalDurationMs)

    var trimRange by remember { mutableStateOf(initialStart..initialEnd) }

    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentPlaybackMs by remember { mutableFloatStateOf(initialStart) }

    // Helper formatting mm:ss
    fun formatMs(ms: Float): String {
        val totalSec = (ms / 1000f).toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%02d:%02d", m, s)
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    val pos = player.currentPosition.toFloat()
                    currentPlaybackMs = pos
                    if (pos >= trimRange.endInclusive) {
                        player.pause()
                        player.seekTo(trimRange.start.toInt())
                        isPlaying = false
                        currentPlaybackMs = trimRange.start
                    }
                }
            }
            delay(50)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Edit Voice Note",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Rename field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                // Audio preview player row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = {
                            val file = File(audioNote.localPath)
                            if (!file.exists()) return@IconButton

                            if (isPlaying) {
                                mediaPlayer?.pause()
                                isPlaying = false
                            } else {
                                if (mediaPlayer == null) {
                                    mediaPlayer = MediaPlayer().apply {
                                        setDataSource(audioNote.localPath)
                                        prepare()
                                        setOnCompletionListener {
                                            isPlaying = false
                                            seekTo(trimRange.start.toInt())
                                        }
                                    }
                                }
                                val p = mediaPlayer!!
                                p.seekTo(trimRange.start.toInt())
                                p.start()
                                isPlaying = true
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }

                    Column {
                        Text(
                            text = "Playback Window: ${formatMs(trimRange.start)} - ${formatMs(trimRange.endInclusive)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Total recording length: ${formatMs(totalDurationMs)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Non-destructive Trim Range Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Start: ${formatMs(trimRange.start)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Text("End: ${formatMs(trimRange.endInclusive)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    RangeSlider(
                        value = trimRange,
                        onValueChange = { newRange ->
                            trimRange = newRange
                            mediaPlayer?.seekTo(newRange.start.toInt())
                        },
                        valueRange = 0f..totalDurationMs,
                        steps = 0
                    )
                    Text(
                        text = "Drag the handles to trim playback non-destructively.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveTrimAndRename(
                        title.ifBlank { "Voice Note" },
                        trimRange.start.toLong(),
                        trimRange.endInclusive.toLong()
                    )
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
