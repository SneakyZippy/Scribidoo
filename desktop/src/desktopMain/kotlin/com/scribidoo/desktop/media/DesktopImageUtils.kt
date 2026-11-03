package com.scribidoo.desktop.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.scribidoo.desktop.data.DesktopDocumentStorageManager
import org.jetbrains.skia.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

object DesktopImageUtils {
    val imagesDir: File
        get() = File(DesktopDocumentStorageManager.rootDir, "images").apply { if (!exists()) mkdirs() }

    fun loadBitmap(filePath: String): ImageBitmap? {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val bytes = file.readBytes()
            val skiaImage = Image.makeFromEncoded(bytes)
            skiaImage.toComposeImageBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getImageDimensions(filePath: String): Pair<Float, Float>? {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val skiaImage = Image.makeFromEncoded(file.readBytes())
            Pair(skiaImage.width.toFloat(), skiaImage.height.toFloat())
        } catch (e: Exception) {
            null
        }
    }

    fun calculateScaledDimensions(
        originalWidth: Float,
        originalHeight: Float,
        maxBound: Float = 600f
    ): Pair<Float, Float> {
        if (originalWidth <= 0f || originalHeight <= 0f) return Pair(400f, 300f)
        val scale = minOf(maxBound / originalWidth, maxBound / originalHeight, 1.0f)
        val targetW = (originalWidth * scale).coerceAtLeast(100f)
        val targetH = (originalHeight * scale).coerceAtLeast(100f)
        return Pair(targetW, targetH)
    }

    fun copyImageToLocalStorage(sourceFile: File): File? {
        if (!sourceFile.exists()) return null
        return try {
            val extension = sourceFile.extension.ifBlank { "png" }
            val destFile = File(imagesDir, "img_${UUID.randomUUID()}.$extension")
            sourceFile.copyTo(destFile, overwrite = true)
            destFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun pasteImageFromClipboard(): File? {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                val awtImage = clipboard.getData(DataFlavor.imageFlavor) as? java.awt.Image ?: return null
                val buffered = if (awtImage is BufferedImage) {
                    awtImage
                } else {
                    val w = awtImage.getWidth(null).coerceAtLeast(1)
                    val h = awtImage.getHeight(null).coerceAtLeast(1)
                    val b = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                    val g = b.createGraphics()
                    g.drawImage(awtImage, 0, 0, null)
                    g.dispose()
                    b
                }
                val destFile = File(imagesDir, "clip_${UUID.randomUUID()}.png")
                ImageIO.write(buffered, "png", destFile)
                destFile
            } else if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
                val files = clipboard.getData(DataFlavor.javaFileListFlavor) as? List<*>
                val imageFile = files?.filterIsInstance<File>()?.firstOrNull { file ->
                    file.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp", "bmp")
                }
                if (imageFile != null) copyImageToLocalStorage(imageFile) else null
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getClipboardText(): String? {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                val content = clipboard.getData(DataFlavor.stringFlavor) as? String
                content?.takeIf { it.isNotBlank() }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun copyTextToClipboard(text: String) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val sel = StringSelection(text)
            clipboard.setContents(sel, sel)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
