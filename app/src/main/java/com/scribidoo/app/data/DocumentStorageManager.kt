package com.scribidoo.app.data

import android.content.Context
import com.scribidoo.app.canvas.model.DocumentModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class DocumentStorageManager(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val docsDir: File
        get() = File(context.filesDir, "documents").apply { mkdirs() }

    suspend fun saveDocument(document: DocumentModel): File = withContext(Dispatchers.IO) {
        val file = File(docsDir, "${document.id}.json")
        val jsonString = json.encodeToString(document)
        file.writeText(jsonString)
        file
    }

    suspend fun loadDocument(id: String): DocumentModel? = withContext(Dispatchers.IO) {
        val file = File(docsDir, "$id.json")
        if (!file.exists()) return@withContext null
        try {
            val jsonString = file.readText()
            json.decodeFromString<DocumentModel>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun listDocuments(): List<DocumentModel> = withContext(Dispatchers.IO) {
        val files = docsDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
        files.mapNotNull { file ->
            try {
                json.decodeFromString<DocumentModel>(file.readText())
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.modifiedAt }
    }

    suspend fun deleteDocument(id: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(docsDir, "$id.json")
        if (file.exists()) file.delete() else false
    }
}
