package com.scribidoo.app.canvas.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.scribidoo.app.R
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.app.data.DocumentStorageManager
import com.scribidoo.app.export.CanvasExporter
import com.scribidoo.app.media.AudioExtendedMenuDialog
import com.scribidoo.app.media.VoiceRecorderDialog
import com.scribidoo.app.pdf.PdfDocumentManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Composable
fun CanvasWorkspaceScreen(
    modifier: Modifier = Modifier,
    documentId: String? = null,
    onNavigateBackToGallery: (() -> Unit)? = null,
    state: CanvasState = remember { CanvasState() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val storageManager = remember { DocumentStorageManager(context) }
    val pdfManager = remember { PdfDocumentManager(context) }
    val exporter = remember { CanvasExporter(context) }

    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showBrandStickerDialog by remember { mutableStateOf(false) }
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
                    state.setBackgroundBitmap(0, bmp.asImageBitmap())
                    state.viewportState.centerAndFit(state.currentPage.width, state.currentPage.height)
                    Toast.makeText(context, "Loaded PDF (${count} pages)", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Camera photo capture
    var activeCameraFile by remember { mutableStateOf<File?>(null) }
    var activeCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = activeCameraFile
        if (success && file != null && file.exists() && file.length() > 0) {
            val center = state.viewportState.screenToCanvas(
                Offset(state.viewportState.containerSize.width / 2f, state.viewportState.containerSize.height / 2f)
            )
            state.addImage(file.absolutePath, 500f, 400f, center)
            Toast.makeText(context, "Photo added to note", Toast.LENGTH_SHORT).show()
        }
        activeCameraFile = null
        activeCameraUri = null
    }

    // Gallery picture picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val file = copyUriToInternalFile(context, uri, "images", "img_${UUID.randomUUID()}.png")
                if (file != null && file.exists()) {
                    val center = state.viewportState.screenToCanvas(
                        Offset(state.viewportState.containerSize.width / 2f, state.viewportState.containerSize.height / 2f)
                    )
                    state.addImage(file.absolutePath, 500f, 400f, center)
                    Toast.makeText(context, "Picture added", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Audio file picker
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val file = copyUriToInternalFile(context, uri, "audio", "audio_${UUID.randomUUID()}.m4a")
                if (file != null && file.exists()) {
                    val center = state.viewportState.screenToCanvas(
                        Offset(state.viewportState.containerSize.width / 2f, state.viewportState.containerSize.height / 2f)
                    )
                    state.addAudioNote(file.absolutePath, 10000L, center, "Audio Clip")
                    Toast.makeText(context, "Audio note added", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // JSON Note Export launcher
    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val jsonStr = Json {
                        prettyPrint = true
                        ignoreUnknownKeys = true
                    }.encodeToString(DocumentModel.serializer(), state.document)
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(jsonStr.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "Note exported to JSON", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // JSON Note Import launcher
    val jsonImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val jsonStr = context.contentResolver.openInputStream(uri)?.use { it.reader().readText() } ?: ""
                    val importedDoc = Json {
                        ignoreUnknownKeys = true
                    }.decodeFromString(DocumentModel.serializer(), jsonStr)
                    state.loadDocument(importedDoc)
                    Toast.makeText(context, "Imported '${importedDoc.title}'", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    var isLoaded by remember { mutableStateOf(false) }

    // Load document on launch (specific document if requested, else blank/new document)
    LaunchedEffect(documentId) {
        if (documentId != null) {
            val doc = storageManager.loadDocument(documentId)
            if (doc != null) {
                state.loadDocument(doc)
            }
        } else {
            val docs = storageManager.listDocuments()
            if (docs.isNotEmpty()) {
                val mostRecent = docs.first()
                state.loadDocument(mostRecent)
            } else {
                val newDoc = DocumentModel()
                state.loadDocument(newDoc)
                storageManager.saveDocument(newDoc)
            }
        }
        isLoaded = true
    }

    // Auto-save whenever document changes
    LaunchedEffect(state.document) {
        if (isLoaded) {
            delay(500)
            storageManager.saveDocument(state.document)
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
                state.setBackgroundBitmap(curPage.index, bmp.asImageBitmap())
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
                // Back to Gallery button
                if (onNavigateBackToGallery != null) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                storageManager.saveDocument(state.document)
                                onNavigateBackToGallery()
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Gallery",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .width(1.dp)
                            .background(Color(0x22000000))
                    )
                }

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
                    IconButton(
                        onClick = {
                            state.addNewPage()
                            Toast.makeText(context, "Added Page ${state.document.pages.size}", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add New Page",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
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
                        if (onNavigateBackToGallery != null) {
                            DropdownMenuItem(
                                text = { Text("Notebooks Gallery") },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    coroutineScope.launch {
                                        storageManager.saveDocument(state.document)
                                        onNavigateBackToGallery()
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
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
                            text = { Text("Add New Page") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showMenu = false
                                state.addNewPage()
                                Toast.makeText(context, "Added Page ${state.document.pages.size}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Note (JSON)") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                showMenu = false
                                jsonExportLauncher.launch("${state.document.title.replace(" ", "_")}.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import Note (JSON)") },
                            leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                showMenu = false
                                jsonImportLauncher.launch(arrayOf("application/json", "*/*"))
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
                        DropdownMenuItem(
                            text = { Text("Canvas & Palm Settings") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showMenu = false
                                showSettingsDialog = true
                            }
                        )
                        HorizontalDivider()
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

        // 3. Floating Draggable Tool Palette (Supports Horizontal & Vertical dock)
        FloatingToolPalette(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            onOpenSettings = { showSettingsDialog = true }
        )
    }

    // Canvas Settings Dialog
    if (showSettingsDialog) {
        CanvasSettingsDialog(
            state = state,
            onDismiss = { showSettingsDialog = false }
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

    // Text Box Input / Edit Dialog
    if (state.activeTextBoxForEdit != null) {
        val targetBox = state.activeTextBoxForEdit!!
        TextBoxDialog(
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
        TextBoxDialog(
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

    // Voice Recorder Dialog
    if (state.showVoiceRecorderDialog) {
        VoiceRecorderDialog(
            onDismiss = { state.showVoiceRecorderDialog = false },
            onAttach = { filePath, durationMs, title ->
                val center = state.viewportState.screenToCanvas(
                    Offset(state.viewportState.containerSize.width / 2f, state.viewportState.containerSize.height / 2f)
                )
                state.addAudioNote(filePath, durationMs, center, title)
                state.showVoiceRecorderDialog = false
            }
        )
    }

    // Audio Extended Menu Dialog
    if (state.activeAudioForMenu != null) {
        val targetAudio = state.activeAudioForMenu!!
        AudioExtendedMenuDialog(
            audioNote = targetAudio,
            onDismiss = { state.activeAudioForMenu = null },
            onSaveTrimAndRename = { newTitle, trimStartMs, trimEndMs ->
                state.renameAudioNote(targetAudio.id, newTitle)
                state.updateAudioTrim(targetAudio.id, trimStartMs, trimEndMs)
                state.activeAudioForMenu = null
            },
            onDelete = {
                state.deleteAudioNote(targetAudio.id)
                state.activeAudioForMenu = null
            }
        )
    }

    // Image Source Picker Dialog (Camera or Gallery)
    if (state.showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { state.showImageSourceDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Insert Picture", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose how to add an image or sticker to your canvas:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            state.showImageSourceDialog = false
                            showBrandStickerDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scribidoo Brand Stickers & Art")
                    }
                    Button(
                        onClick = {
                            state.showImageSourceDialog = false
                            val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
                            val photoFile = File(imagesDir, "photo_${UUID.randomUUID()}.jpg")
                            activeCameraFile = photoFile
                            val photoUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                photoFile
                            )
                            activeCameraUri = photoUri
                            cameraLauncher.launch(photoUri)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take Photo with Camera")
                    }
                    Button(
                        onClick = {
                            state.showImageSourceDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose from Gallery / Files")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { state.showImageSourceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Scribidoo Brand Art & Sticker Gallery Dialog
    if (showBrandStickerDialog) {
        BrandStickerGalleryDialog(
            onDismiss = { showBrandStickerDialog = false },
            onSelectSticker = { resId, defaultW, defaultH, label ->
                val file = copyDrawableToFile(context, resId, "sticker_${resId}.png")
                val center = state.viewportState.screenToCanvas(
                    Offset(state.viewportState.containerSize.width / 2f, state.viewportState.containerSize.height / 2f)
                )
                state.addImage(file.absolutePath, defaultW, defaultH, center)
                Toast.makeText(context, "Added $label sticker", Toast.LENGTH_SHORT).show()
                showBrandStickerDialog = false
            }
        )
    }
}

private fun copyUriToInternalFile(context: android.content.Context, uri: Uri, subDir: String, targetFileName: String): File? {
    return try {
        val destDir = File(context.filesDir, subDir).apply { mkdirs() }
        val destFile = File(destDir, targetFileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        destFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun TextBoxDialog(
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
                text = if (isEditing) "Edit Text" else "Add Text Note",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
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

private fun copyDrawableToFile(context: android.content.Context, resId: Int, fileName: String): File {
    val dir = File(context.filesDir, "branding").apply { mkdirs() }
    val outFile = File(dir, fileName)
    if (!outFile.exists() || outFile.length() == 0L) {
        val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, resId)
        java.io.FileOutputStream(outFile).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
    }
    return outFile
}

private data class StickerItem(
    val resId: Int,
    val label: String,
    val defaultW: Float,
    val defaultH: Float
)

@Composable
fun BrandStickerGalleryDialog(
    onDismiss: () -> Unit,
    onSelectSticker: (resId: Int, defaultW: Float, defaultH: Float, label: String) -> Unit
) {
    val stickers = remember {
        listOf(
            StickerItem(R.drawable.scribidoo_logo_watercolor, "Watercolor Crest", 400f, 420f),
            StickerItem(R.drawable.scribidoo_brand_icon, "Brand Icon & Stars", 350f, 390f),
            StickerItem(R.drawable.scribidoo_logotype, "Script Logotype", 440f, 330f),
            StickerItem(R.drawable.scribidoo_badge, "Monogram Badge", 320f, 320f)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Scribidoo Stickers & Art", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Tap any brand sticker to place it on your canvas (movable & resizable):",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(280.dp)
                ) {
                    items(stickers.size) { index ->
                        val item = stickers[index]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEFAF7),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1F000000)),
                            modifier = Modifier
                                .clickable { onSelectSticker(item.resId, item.defaultW, item.defaultH, item.label) }
                                .padding(2.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = item.resId),
                                    contentDescription = item.label,
                                    modifier = Modifier
                                        .size(76.dp)
                                        .padding(4.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xDD000000),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
