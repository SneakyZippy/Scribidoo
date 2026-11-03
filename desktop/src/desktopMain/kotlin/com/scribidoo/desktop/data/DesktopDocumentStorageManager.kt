package com.scribidoo.desktop.data

import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.util.UniqueNamingUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

object DesktopDocumentStorageManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    val rootDir: File by lazy {
        val appData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        val dir = File(appData, "Scribidoo")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    val docsDir: File
        get() = File(rootDir, "documents").apply { if (!exists()) mkdirs() }

    private val legacyAutosaveFile: File
        get() = File(rootDir, "autosave.json")

    init {
        migrateLegacyAutosave()
    }

    private fun migrateLegacyAutosave() {
        try {
            if (legacyAutosaveFile.exists() && legacyAutosaveFile.length() > 0) {
                val legacyDoc = json.decodeFromString<DocumentModel>(legacyAutosaveFile.readText())
                val targetFile = File(docsDir, "${legacyDoc.id}.json")
                if (!targetFile.exists()) {
                    saveDocument(legacyDoc)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun listDocuments(): List<DocumentModel> {
        val files = docsDir.listFiles { _, name -> name.endsWith(".json") && !name.endsWith(".tmp") }
            ?: emptyArray()
        val docs = files.mapNotNull { file ->
            try {
                json.decodeFromString<DocumentModel>(file.readText())
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.modifiedAt }

        if (docs.isEmpty()) {
            val defaultDoc = DocumentModel(
                id = UUID.randomUUID().toString(),
                title = "Welcome to Scribidoo"
            )
            saveDocument(defaultDoc)
            return listOf(defaultDoc)
        }
        return docs
    }

    fun loadDocument(id: String): DocumentModel? {
        val file = File(docsDir, "$id.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString<DocumentModel>(file.readText())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveDocument(document: DocumentModel): File {
        val updatedDoc = document.copy(modifiedAt = System.currentTimeMillis())
        val text = json.encodeToString(updatedDoc)
        val targetFile = File(docsDir, "${updatedDoc.id}.json")
        val tmpFile = File(docsDir, "${updatedDoc.id}.json.tmp")
        tmpFile.writeText(text)
        if (tmpFile.exists()) {
            if (targetFile.exists()) targetFile.delete()
            tmpFile.renameTo(targetFile)
        }
        return targetFile
    }

    fun deleteDocument(id: String): Boolean {
        val file = File(docsDir, "$id.json")
        return if (file.exists()) file.delete() else false
    }

    fun duplicateDocument(originalId: String): DocumentModel? {
        val original = loadDocument(originalId) ?: return null
        val existingTitles = listDocuments().map { it.title }
        val newTitle = UniqueNamingUtils.generateDuplicateDocumentTitle(original.title, existingTitles)
        val duplicate = original.copy(
            id = UUID.randomUUID().toString(),
            title = newTitle,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )
        saveDocument(duplicate)
        return duplicate
    }

    fun exportBackupWithDialog(document: DocumentModel): File? {
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Save Note Backup", java.awt.FileDialog.SAVE)
        dialog.file = "${document.title.replace(" ", "_")}.json"
        dialog.isVisible = true
        return dialog.file?.let {
            val file = File(dialog.directory, it)
            file.writeText(json.encodeToString(document))
            file
        }
    }

    fun importBackupWithDialog(): DocumentModel? {
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Open Note Backup", java.awt.FileDialog.LOAD)
        dialog.isVisible = true
        return dialog.file?.let {
            val file = File(dialog.directory, it)
            json.decodeFromString<DocumentModel>(file.readText())
        }
    }
}
