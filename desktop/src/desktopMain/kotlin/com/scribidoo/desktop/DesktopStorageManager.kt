package com.scribidoo.desktop

import com.scribidoo.app.canvas.model.DocumentModel
import kotlinx.serialization.json.Json
import java.io.File

object DesktopStorageManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val storageDir: File by lazy {
        val appData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        val dir = File(appData, "Scribidoo")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val autosaveFile: File
        get() = File(storageDir, "autosave.json")

    fun loadAutoSave(): DocumentModel? {
        return try {
            if (autosaveFile.exists() && autosaveFile.length() > 0) {
                json.decodeFromString<DocumentModel>(autosaveFile.readText())
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveAutoSave(document: DocumentModel) {
        try {
            val text = json.encodeToString(DocumentModel.serializer(), document)
            val tmp = File(storageDir, "autosave.json.tmp")
            tmp.writeText(text)
            if (tmp.exists()) {
                if (autosaveFile.exists()) autosaveFile.delete()
                tmp.renameTo(autosaveFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
