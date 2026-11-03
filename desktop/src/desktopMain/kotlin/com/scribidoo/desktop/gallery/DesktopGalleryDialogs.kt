package com.scribidoo.desktop.gallery

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.scribidoo.app.canvas.model.DocumentModel

@Composable
fun DesktopGalleryRenameDialog(
    document: DocumentModel,
    input: String,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Notebook") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                singleLine = true,
                label = { Text("Notebook Title") }
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DesktopGalleryDeleteDialog(
    document: DocumentModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Notebook?") },
        text = { Text("Are you sure you want to delete '${document.title}'? This action cannot be undone.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
