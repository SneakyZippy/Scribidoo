package com.scribidoo.desktop.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.model.AudioNoteModel

@Composable
fun DesktopAudioMenuDialog(
    audioNote: AudioNoteModel,
    onDismiss: () -> Unit,
    onSaveTrimAndRename: (newTitle: String, trimStartMs: Long, trimEndMs: Long) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(audioNote.label) }
    var trimStart by remember { mutableLongStateOf(audioNote.trimStartMs) }
    var trimEnd by remember { mutableLongStateOf(if (audioNote.trimEndMs > 0L) audioNote.trimEndMs else audioNote.durationMs) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio Note Options", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                Text("Trim Audio (Non-destructive):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Start: ${trimStart / 1000}s", fontSize = 11.sp)
                    Text("End: ${trimEnd / 1000}s", fontSize = 11.sp)
                }
                Slider(
                    value = trimStart.toFloat(),
                    onValueChange = { trimStart = it.toLong().coerceAtMost(trimEnd - 500L) },
                    valueRange = 0f..audioNote.durationMs.toFloat()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSaveTrimAndRename(title, trimStart, trimEnd)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDelete(); onDismiss() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
