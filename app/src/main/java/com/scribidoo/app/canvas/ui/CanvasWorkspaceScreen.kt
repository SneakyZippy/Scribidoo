package com.scribidoo.app.canvas.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.app.data.DocumentStorageManager
import com.scribidoo.app.export.CanvasExporter
import com.scribidoo.app.pdf.PdfDocumentManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CanvasWorkspaceScreen(
    modifier: Modifier = Modifier,
    state: CanvasState = remember { CanvasState() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val storageManager = remember { DocumentStorageManager(context) }
    val pdfManager = remember { PdfDocumentManager(context) }
    val exporter = remember { CanvasExporter(context) }

    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }

    // Launcher for PDF file selection
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val (file, count) = pdfManager.importPdf(uri)
                    state.initializeFromPdf(file, count)
                    // Render first page
                    val bmp = pdfManager.renderPageBitmap(file, 0)
                    state.setBackgroundBitmap(0, bmp)
                    state.viewportState.centerAndFit(state.currentPage.width, state.currentPage.height)
                    Toast.makeText(context, "Loaded PDF (${count} pages)", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Effect to render PDF background for active page on demand
    val curPage = state.currentPage
    LaunchedEffect(curPage.index, curPage.backgroundUri) {
        if (curPage.backgroundUri != null && !state.backgroundBitmaps.containsKey(curPage.index)) {
            val file = File(curPage.backgroundUri)
            if (file.exists()) {
                val pageIndex = curPage.backgroundPdfPage ?: curPage.index
                val bmp = pdfManager.renderPageBitmap(file, pageIndex)
                state.setBackgroundBitmap(curPage.index, bmp)
            }
        }
    }

    // Initial page centering once container size is known
    LaunchedEffect(state.viewportState.containerSize) {
        if (state.viewportState.containerSize.width > 0 && state.viewportState.containerSize.height > 0) {
            val page = state.currentPage
            state.viewportState.centerAndFit(page.width, page.height, padding = 36f)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Edge-to-Edge 2D Vector Canvas (takes full screen)
        TabletCanvasView(
            state = state,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Ultra-Compact Floating Document Capsule in Top Corner
        // Leaves maximum vertical drawing space (critical for horizontal/landscape mode!)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(18.dp))
                .border(1.dp, Color(0x1F000000), RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Clickable Note Title (tap to rename)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            renameInput = state.document.title
                            showRenameDialog = true
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = state.document.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename Document",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Compact Page Navigator in Paged mode
                if (state.document.layoutMode == CanvasLayoutMode.PAGED) {
                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .width(1.dp)
                            .background(Color(0x22000000))
                    )
                    IconButton(
                        onClick = { state.setActivePage(state.document.activePageIndex - 1) },
                        enabled = state.document.activePageIndex > 0,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous Page",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "${state.document.activePageIndex + 1}/${state.document.pages.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { state.setActivePage(state.document.activePageIndex + 1) },
                        enabled = state.document.activePageIndex < state.document.pages.size - 1,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next Page",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .width(1.dp)
                        .background(Color(0x22000000))
                )

                // Single compact overflow menu button (⋮) containing infrequent actions
                Box {
                    IconButton(
                        onClick = { showMenu = !showMenu },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Document Actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Save Document") },
                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                showMenu = false
                                coroutineScope.launch {
                                    storageManager.saveDocument(state.document)
                                    Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import PDF") },
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showMenu = false
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Document to PDF") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                showMenu = false
                                coroutineScope.launch {
                                    try {
                                        val pdfFile = exporter.exportToPdf(
                                            document = state.document,
                                            backgroundBitmaps = state.backgroundBitmaps
                                        )
                                        exporter.shareFile(pdfFile, "application/pdf")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Current Page to PNG") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                showMenu = false
                                coroutineScope.launch {
                                    try {
                                        val pngFile = exporter.exportToPng(
                                            page = state.currentPage,
                                            backgroundBitmap = state.backgroundBitmaps[state.currentPage.index]
                                        )
                                        exporter.shareFile(pngFile, "image/png")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Clear Page", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                showMenu = false
                                state.clearPage()
                            }
                        )
                    }
                }
            }
        }

        // 3. Floating Draggable Tool Palette
        FloatingToolPalette(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }

    // Rename Note Dialog
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
                TextButton(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            state.renameDocument(renameInput)
                            coroutineScope.launch {
                                storageManager.saveDocument(state.document)
                            }
                        }
                        showRenameDialog = false
                    }
                ) {
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
}
