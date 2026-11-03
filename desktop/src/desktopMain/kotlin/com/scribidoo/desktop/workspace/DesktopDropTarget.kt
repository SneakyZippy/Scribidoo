package com.scribidoo.desktop.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.canvas.state.CanvasState
import com.scribidoo.desktop.data.DesktopDocumentStorageManager
import com.scribidoo.desktop.media.DesktopImageUtils
import com.scribidoo.desktop.pdf.DesktopPdfManager
import kotlinx.serialization.json.Json
import java.awt.Component
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File

@Composable
fun DesktopDropTargetEffect(
    component: Component?,
    onDragStateChanged: ((Boolean) -> Unit)? = null,
    onFilesDropped: (files: List<File>, screenPoint: Offset) -> Unit
) {
    DisposableEffect(component) {
        if (component == null) return@DisposableEffect onDispose {}

        val previousDropTarget = component.dropTarget
        val listener = object : DropTargetAdapter() {
            override fun dragEnter(dtde: DropTargetDragEvent) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                    onDragStateChanged?.invoke(true)
                } else {
                    dtde.rejectDrag()
                }
            }

            override fun dragOver(dtde: DropTargetDragEvent) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                }
            }

            override fun dragExit(dte: DropTargetEvent) {
                onDragStateChanged?.invoke(false)
            }

            override fun drop(dtde: DropTargetDropEvent) {
                onDragStateChanged?.invoke(false)
                try {
                    if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY)
                        val transferable = dtde.transferable
                        val rawList = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                        val files = rawList?.filterIsInstance<File>() ?: emptyList()
                        if (files.isNotEmpty()) {
                            val pt = Offset(dtde.location.x.toFloat(), dtde.location.y.toFloat())
                            onFilesDropped(files, pt)
                            dtde.dropComplete(true)
                            return
                        }
                    }
                    dtde.dropComplete(false)
                } catch (e: Exception) {
                    e.printStackTrace()
                    dtde.dropComplete(false)
                }
            }
        }

        component.dropTarget = DropTarget(component, DnDConstants.ACTION_COPY, listener, true)

        onDispose {
            component.dropTarget = previousDropTarget
        }
    }
}

object DesktopDropHandler {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun handleDroppedFilesOnCanvas(
        files: List<File>,
        dropScreenPoint: Offset,
        state: CanvasState,
        onImportDocument: (DocumentModel) -> Unit,
        showToast: (String) -> Unit
    ) {
        val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp")
        val imageFiles = files.filter { it.extension.lowercase() in imageExtensions }
        val jsonFiles = files.filter { it.extension.lowercase() == "json" }
        val pdfFiles = files.filter { it.extension.lowercase() == "pdf" }

        if (pdfFiles.isNotEmpty()) {
            try {
                val pdfFile = pdfFiles.first()
                val (importedPdf, pageCount) = DesktopPdfManager.importPdf(pdfFile)
                state.initializeFromPdf(importedPdf, pageCount)
                DesktopPdfManager.renderPageBitmap(importedPdf, 0)?.let { bmp ->
                    state.setBackgroundBitmap(0, bmp)
                }
                DesktopDocumentStorageManager.saveDocument(state.document)
                showToast("Imported PDF (${pageCount} pages)")
                return
            } catch (e: Exception) {
                showToast("Failed to load PDF: ${e.message}")
            }
        }

        if (jsonFiles.isNotEmpty()) {
            try {
                val jsonFile = jsonFiles.first()
                val imported = jsonParser.decodeFromString<DocumentModel>(jsonFile.readText())
                onImportDocument(imported)
                showToast("Imported note '${imported.title}'")
                return
            } catch (e: Exception) {
                showToast("Failed to load note: ${e.message}")
            }
        }

        if (imageFiles.isNotEmpty()) {
            val baseCenter = state.viewportState.screenToCanvas(dropScreenPoint)
            var addedCount = 0
            imageFiles.forEachIndexed { index, file ->
                val saved = DesktopImageUtils.copyImageToLocalStorage(file)
                if (saved != null) {
                    val (origW, origH) = DesktopImageUtils.getImageDimensions(saved.absolutePath) ?: Pair(400f, 300f)
                    val (w, h) = DesktopImageUtils.calculateScaledDimensions(origW, origH)
                    val cascadeOffset = Offset(index * 24f, index * 24f)
                    state.addImage(saved.absolutePath, w, h, baseCenter + cascadeOffset)
                    addedCount++
                }
            }
            if (addedCount > 0) {
                showToast("Added $addedCount image${if (addedCount > 1) "s" else ""}")
            }
        }
    }

    fun handleDroppedFilesInGallery(files: List<File>, onRefresh: () -> Unit) {
        val pdfFiles = files.filter { it.extension.lowercase() == "pdf" }
        for (pdf in pdfFiles) {
            try {
                val (importedPdf, pageCount) = DesktopPdfManager.importPdf(pdf)
                val pages = (0 until pageCount).map { idx ->
                    com.scribidoo.app.canvas.model.PageModel(index = idx, backgroundPdfPage = idx, backgroundUri = importedPdf.absolutePath)
                }
                val doc = DocumentModel(
                    id = java.util.UUID.randomUUID().toString(),
                    title = pdf.nameWithoutExtension,
                    pages = pages
                )
                DesktopDocumentStorageManager.saveDocument(doc)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val jsonFiles = files.filter { it.extension.lowercase() == "json" }
        for (f in jsonFiles) {
            try {
                val imported = jsonParser.decodeFromString<DocumentModel>(f.readText())
                DesktopDocumentStorageManager.saveDocument(imported)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        onRefresh()
    }
}
