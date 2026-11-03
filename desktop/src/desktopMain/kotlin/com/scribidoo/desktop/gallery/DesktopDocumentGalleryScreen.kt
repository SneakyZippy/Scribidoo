package com.scribidoo.desktop.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.input.PalmRejectionConfig
import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.canvas.model.PageModel
import com.scribidoo.app.util.UniqueNamingUtils
import com.scribidoo.desktop.data.DesktopDocumentStorageManager
import com.scribidoo.desktop.dialogs.DesktopCalibrationDialog
import com.scribidoo.desktop.pdf.DesktopPdfManager
import com.scribidoo.desktop.workspace.DesktopDropTargetEffect
import kotlinx.serialization.json.Json
import java.awt.Component
import java.util.UUID

@Composable
fun DesktopDocumentGalleryScreen(
    onOpenDocument: (DocumentModel) -> Unit,
    modifier: Modifier = Modifier,
    window: Component? = null
) {
    var documents by remember { mutableStateOf<List<DocumentModel>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showRenameDialogFor by remember { mutableStateOf<DocumentModel?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showDeleteConfirmFor by remember { mutableStateOf<DocumentModel?>(null) }
    var showCalibrationDialog by remember { mutableStateOf(false) }
    var isDraggingOver by remember { mutableStateOf(false) }

    fun refreshDocs() {
        documents = DesktopDocumentStorageManager.listDocuments()
    }

    LaunchedEffect(Unit) {
        refreshDocs()
    }

    DesktopDropTargetEffect(
        component = window,
        onDragStateChanged = { isDraggingOver = it },
        onFilesDropped = { files, _ ->
            com.scribidoo.desktop.workspace.DesktopDropHandler.handleDroppedFilesInGallery(files) {
                refreshDocs()
            }
        }
    )

    val filteredDocs = remember(documents, searchQuery) {
        if (searchQuery.isBlank()) documents
        else documents.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
    ) {
        // Top Navigation Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource("scribidoo_brand_icon.png"),
                            contentDescription = "Scribidoo",
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Scribidoo",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${documents.size} Notebooks",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { showCalibrationDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Calibrate Input", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val titles = documents.map { it.title }
                                val newTitle = UniqueNamingUtils.generateUniqueDocumentTitle(titles)
                                val newDoc = DocumentModel(id = UUID.randomUUID().toString(), title = newTitle)
                                DesktopDocumentStorageManager.saveDocument(newDoc)
                                refreshDocs()
                                onOpenDocument(newDoc)
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Notebook", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    placeholder = { Text("Search notebooks and sketches...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }

        // Notebook Cards Grid
        if (filteredDocs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Text(
                        text = if (searchQuery.isNotBlank()) "No notebooks match '$searchQuery'" else "No notebooks found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 240.dp),
                contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredDocs, key = { it.id }) { doc ->
                    DesktopDocumentCard(
                        document = doc,
                        onClick = { onOpenDocument(doc) },
                        onRename = {
                            renameInput = doc.title
                            showRenameDialogFor = doc
                        },
                        onDuplicate = {
                            DesktopDocumentStorageManager.duplicateDocument(doc.id)
                            refreshDocs()
                        },
                        onDelete = { showDeleteConfirmFor = doc }
                    )
                }
            }
        }
    }

    // Rename Dialog
    showRenameDialogFor?.let { doc ->
        DesktopGalleryRenameDialog(
            document = doc,
            input = renameInput,
            onInputChange = { renameInput = it },
            onConfirm = {
                if (renameInput.isNotBlank()) {
                    DesktopDocumentStorageManager.saveDocument(doc.copy(title = renameInput.trim()))
                    refreshDocs()
                }
                showRenameDialogFor = null
            },
            onDismiss = { showRenameDialogFor = null }
        )
    }

    // Delete Confirmation Dialog
    showDeleteConfirmFor?.let { doc ->
        DesktopGalleryDeleteDialog(
            document = doc,
            onConfirm = {
                DesktopDocumentStorageManager.deleteDocument(doc.id)
                refreshDocs()
                showDeleteConfirmFor = null
            },
            onDismiss = { showDeleteConfirmFor = null }
        )
    }

    if (showCalibrationDialog) {
        DesktopCalibrationDialog(
            initialConfig = PalmRejectionConfig(),
            onDismiss = { showCalibrationDialog = false },
            onComplete = {
                showCalibrationDialog = false
            }
        )
    }
}
