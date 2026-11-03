package com.scribidoo.desktop.workspace

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.desktop.DesktopSettingsDialog
import com.scribidoo.desktop.DesktopTextBoxDialog
import com.scribidoo.desktop.dialogs.DesktopAudioMenuDialog
import com.scribidoo.desktop.dialogs.DesktopImagePickerDialog
import com.scribidoo.desktop.dialogs.DesktopVoiceRecorderDialog
import com.scribidoo.desktop.media.DesktopAudioPlayer
import com.scribidoo.desktop.media.DesktopAudioRecorder

@Composable
fun DesktopWorkspaceDialogs(
    state: CanvasState,
    audioRecorder: DesktopAudioRecorder,
    audioPlayer: DesktopAudioPlayer,
    showSettingsDialog: Boolean,
    onDismissSettings: () -> Unit,
    showRenameDialog: Boolean,
    renameInput: String,
    onRenameInputChange: (String) -> Unit,
    onConfirmRename: (String) -> Unit,
    onDismissRename: () -> Unit
) {
    // 1. Text Note Dialog (Edit existing or insert new)
    if (state.activeTextBoxForEdit != null) {
        val targetBox = state.activeTextBoxForEdit!!
        DesktopTextBoxDialog(
            initialText = targetBox.text,
            initialFontSize = targetBox.fontSizeSp,
            isEditing = true,
            onDismiss = { state.activeTextBoxForEdit = null },
            onSave = { text, size ->
                state.updateTextBox(targetBox.id, text, size, targetBox.colorArgb)
                state.activeTextBoxForEdit = null
            },
            onDelete = {
                state.deleteTextBox(targetBox.id)
                state.activeTextBoxForEdit = null
            }
        )
    } else if (state.pendingTextInsertPoint != null) {
        val pt = state.pendingTextInsertPoint!!
        DesktopTextBoxDialog(
            initialText = "",
            initialFontSize = 20f,
            isEditing = false,
            onDismiss = { state.pendingTextInsertPoint = null },
            onSave = { text, size ->
                state.addTextBox(pt, text, size, state.currentColor.toArgb())
                state.pendingTextInsertPoint = null
            }
        )
    }

    // 2. Image & File Picker Dialog
    if (state.showImageSourceDialog) {
        DesktopImagePickerDialog(
            onDismiss = { state.showImageSourceDialog = false },
            onImageSelected = { path ->
                val center = state.viewportState.screenToCanvas(
                    Offset(state.viewportState.containerSize.width / 2f, state.viewportState.containerSize.height / 2f)
                )
                state.addImage(path, 360f, 270f, center)
            }
        )
    }

    // 3. Voice Recorder Dialog
    if (state.showVoiceRecorderDialog) {
        DesktopVoiceRecorderDialog(
            recorder = audioRecorder,
            player = audioPlayer,
            onDismiss = { state.showVoiceRecorderDialog = false },
            onAttach = { path, duration, title ->
                val center = state.viewportState.screenToCanvas(
                    Offset(state.viewportState.containerSize.width / 2f, state.viewportState.containerSize.height / 2f)
                )
                state.addAudioNote(path, duration, center, title)
            }
        )
    }

    // 4. Audio Note Menu Dialog
    state.activeAudioForMenu?.let { audio ->
        DesktopAudioMenuDialog(
            audioNote = audio,
            onDismiss = { state.activeAudioForMenu = null },
            onSaveTrimAndRename = { newTitle, startMs, endMs ->
                state.renameAudioNote(audio.id, newTitle)
                state.updateAudioTrim(audio.id, startMs, endMs)
            },
            onDelete = { state.deleteAudioNote(audio.id) }
        )
    }

    // 5. Settings Dialog
    if (showSettingsDialog) {
        DesktopSettingsDialog(state = state, onDismiss = onDismissSettings)
    }

    // 6. Rename Note Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = onDismissRename,
            title = { Text("Rename Note") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = onRenameInputChange,
                    singleLine = true,
                    label = { Text("Title") }
                )
            },
            confirmButton = {
                Button(onClick = { onConfirmRename(renameInput) }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = onDismissRename) { Text("Cancel") }
            }
        )
    }
}
