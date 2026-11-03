package com.scribidoo.desktop.workspace

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.desktop.DesktopCanvasView
import com.scribidoo.desktop.DesktopExporter
import com.scribidoo.desktop.data.DesktopDocumentStorageManager
import com.scribidoo.desktop.dialogs.DesktopCalibrationDialog
import com.scribidoo.desktop.dialogs.openNativeImagePickerDialog
import com.scribidoo.desktop.media.DesktopAudioPlayer
import com.scribidoo.desktop.media.DesktopAudioRecorder
import com.scribidoo.desktop.pdf.DesktopPdfExporter
import com.scribidoo.desktop.pdf.DesktopPdfManager
import com.scribidoo.desktop.ui.DesktopRibbonToolbar
import kotlinx.coroutines.delay
import java.awt.Component
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun DesktopCanvasWorkspaceScreen(
    initialDocument: DocumentModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    window: Component? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val state = remember(initialDocument.id) { CanvasState(initialDocument = initialDocument) }
    val audioPlayer = remember { DesktopAudioPlayer(coroutineScope) }
    val audioRecorder = remember { DesktopAudioRecorder() }
    val focusRequester = remember { FocusRequester() }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCalibrationDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var isDraggingOver by remember { mutableStateOf(false) }

    val isInputActive = showSettingsDialog || showRenameDialog || showCalibrationDialog ||
        state.activeTextBoxForEdit != null || state.pendingTextInsertPoint != null ||
        state.showVoiceRecorderDialog || state.showImageSourceDialog

    // Auto-save whenever document changes
    LaunchedEffect(state.document) {
        delay(600)
        DesktopDocumentStorageManager.saveDocument(state.document)
    }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
            DesktopDocumentStorageManager.saveDocument(state.document)
        }
    }

    // Initial page center fit and focus
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(state.viewportState.containerSize) {
        if (state.viewportState.containerSize.width > 0 && state.viewportState.containerSize.height > 0) {
            val page = state.currentPage
            state.viewportState.centerAndFit(page.width, page.height, padding = 48f)
        }
    }

    // Windows Explorer Drag and Drop handler
    DesktopDropTargetEffect(
        component = window,
        onDragStateChanged = { isDraggingOver = it },
        onFilesDropped = { files, screenPoint ->
            DesktopDropHandler.handleDroppedFilesOnCanvas(
                files = files,
                dropScreenPoint = screenPoint,
                state = state,
                onImportDocument = { imported ->
                    state.loadDocument(imported)
                    DesktopDocumentStorageManager.saveDocument(imported)
                },
                showToast = { toastMessage = it }
            )
        }
    )

    // Load PDF background for active page on demand
    val curPage = state.currentPage
    LaunchedEffect(curPage.index, curPage.backgroundUri) {
        val uri = curPage.backgroundUri
        if (uri != null && !state.backgroundBitmaps.containsKey(curPage.index)) {
            val file = File(uri)
            if (file.exists() && file.extension.lowercase() == "pdf") {
                val pageIndex = curPage.backgroundPdfPage ?: curPage.index
                val bmp = DesktopPdfManager.renderPageBitmap(file, pageIndex)
                if (bmp != null) state.setBackgroundBitmap(curPage.index, bmp)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                DesktopKeyboardHandler.handleWorkspaceKeyEvent(
                    event = event,
                    state = state,
                    isInputActive = isInputActive,
                    onSaveRequested = {
                        DesktopDocumentStorageManager.saveDocument(state.document)
                        toastMessage = "Saved '${state.document.title}'"
                    },
                    showToast = { toastMessage = it }
                )
            }
            .then(
                if (isDraggingOver) {
                    Modifier.border(4.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                } else Modifier
            )
    ) {
        // 1. Docked Modern Top Ribbon Toolbar
        DesktopRibbonToolbar(
            state = state,
            onNavigateBack = {
                DesktopDocumentStorageManager.saveDocument(state.document)
                onNavigateBack()
            },
            onRenameClick = {
                renameInput = state.document.title
                showRenameDialog = true
            },
            onSaveClick = {
                DesktopDocumentStorageManager.saveDocument(state.document)
                toastMessage = "Saved '${state.document.title}'"
            },
            onOpenImagePicker = {
                openNativeImagePickerDialog { filePath ->
                    val center = state.viewportState.screenToCanvas(
                        androidx.compose.ui.geometry.Offset(state.viewportState.containerSize.width / 2f, state.viewportState.containerSize.height / 2f)
                    )
                    state.addImage(filePath, 360f, 270f, center)
                    toastMessage = "Image added"
                }
            },
            onOpenVoiceRecorder = { state.showVoiceRecorderDialog = true },
            onOpenCalibration = { showCalibrationDialog = true },
            onExportPngCurrent = {
                try {
                    val file = DesktopExporter.exportPageToPng(state.currentPage, state.document.title)
                    toastMessage = "Exported page: ${file.name}"
                } catch (e: Exception) {
                    toastMessage = "Export failed: ${e.message}"
                }
            },
            onExportPngAll = {
                try {
                    val files = DesktopExporter.exportDocumentToPngs(state.document)
                    toastMessage = "Exported ${files.size} pages to Pictures/Scribidoo"
                } catch (e: Exception) {
                    toastMessage = "Export failed: ${e.message}"
                }
            },
            onExportPdf = {
                try {
                    val file = DesktopPdfExporter.exportDocumentToPdf(state.document)
                    toastMessage = "Exported PDF: ${file.name}"
                } catch (e: Exception) {
                    toastMessage = "PDF export failed: ${e.message}"
                }
            },
            onImportPdf = {
                try {
                    val dialog = FileDialog(null as Frame?, "Select PDF to Annotate", FileDialog.LOAD)
                    dialog.setFilenameFilter { _, name -> name.lowercase().endsWith(".pdf") }
                    dialog.isVisible = true
                    dialog.file?.let {
                        val file = File(dialog.directory, it)
                        val (importedPdf, pageCount) = DesktopPdfManager.importPdf(file)
                        state.initializeFromPdf(importedPdf, pageCount)
                        DesktopPdfManager.renderPageBitmap(importedPdf, 0)?.let { bmp ->
                            state.setBackgroundBitmap(0, bmp)
                        }
                        DesktopDocumentStorageManager.saveDocument(state.document)
                        toastMessage = "Imported PDF (${pageCount} pages)"
                    }
                } catch (e: Exception) {
                    toastMessage = "Import failed: ${e.message}"
                }
            },
            onExportJson = {
                val file = DesktopDocumentStorageManager.exportBackupWithDialog(state.document)
                if (file != null) toastMessage = "Exported JSON to ${file.name}"
            },
            onImportJson = {
                val imported = DesktopDocumentStorageManager.importBackupWithDialog()
                if (imported != null) {
                    state.loadDocument(imported)
                    DesktopDocumentStorageManager.saveDocument(imported)
                    toastMessage = "Imported '${imported.title}'"
                }
            },
            onOpenSettings = { showSettingsDialog = true }
        )

        // 2. Unobstructed Full Canvas Area
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            DesktopCanvasView(
                state = state,
                audioPlayer = audioPlayer,
                modifier = Modifier.fillMaxSize()
            )

            // Toast Notification
            toastMessage?.let { msg ->
                LaunchedEffect(msg) {
                    delay(2500)
                    toastMessage = null
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                        .shadow(8.dp, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    tonalElevation = 6.dp
                ) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }

    // 3. Calibration Dialog
    if (showCalibrationDialog) {
        DesktopCalibrationDialog(
            initialConfig = state.palmConfig,
            onDismiss = { showCalibrationDialog = false },
            onComplete = { updated ->
                state.palmConfig = updated
                showCalibrationDialog = false
                toastMessage = "Calibration settings applied"
            }
        )
    }

    // 4. Modular Workspace Dialogs
    DesktopWorkspaceDialogs(
        state = state,
        audioRecorder = audioRecorder,
        audioPlayer = audioPlayer,
        showSettingsDialog = showSettingsDialog,
        onDismissSettings = { showSettingsDialog = false },
        showRenameDialog = showRenameDialog,
        renameInput = renameInput,
        onRenameInputChange = { renameInput = it },
        onConfirmRename = { newTitle ->
            if (newTitle.isNotBlank()) state.renameDocument(newTitle)
            showRenameDialog = false
        },
        onDismissRename = { showRenameDialog = false }
    )
}
