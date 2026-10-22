package com.scribidoo.app.canvas.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.app.data.DocumentStorageManager
import com.scribidoo.app.export.CanvasExporter
import com.scribidoo.app.pdf.PdfDocumentManager
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
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

    var showExportMenu by remember { mutableStateOf(false) }

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
        // 1. Interactive 2D Vector Canvas
        TabletCanvasView(
            state = state,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Minimalist Semi-transparent Top Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            color = Color.Transparent
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = state.document.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    if (state.document.layoutMode == CanvasLayoutMode.PAGED) {
                        // Previous page
                        IconButton(
                            onClick = { state.setActivePage(state.document.activePageIndex - 1) },
                            enabled = state.document.activePageIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous Page")
                        }
                        Text(
                            text = "${state.document.activePageIndex + 1} / ${state.document.pages.size}",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        // Next page
                        IconButton(
                            onClick = { state.setActivePage(state.document.activePageIndex + 1) },
                            enabled = state.document.activePageIndex < state.document.pages.size - 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next Page")
                        }
                    }

                    // Import PDF button
                    IconButton(onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Import PDF",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Save Document button
                    IconButton(onClick = {
                        coroutineScope.launch {
                            storageManager.saveDocument(state.document)
                            Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Note",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Export / Share Menu
                    Box {
                        IconButton(onClick = { showExportMenu = !showExportMenu }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export & Share",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export Document to PDF") },
                                onClick = {
                                    showExportMenu = false
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
                                onClick = {
                                    showExportMenu = false
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
                        }
                    }

                    // Clear page button
                    IconButton(onClick = { state.clearPage() }) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Clear Page",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            )
        }

        // 3. Floating Draggable Tool Palette
        FloatingToolPalette(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}
