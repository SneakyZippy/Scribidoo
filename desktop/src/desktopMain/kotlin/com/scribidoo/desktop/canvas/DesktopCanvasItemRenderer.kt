package com.scribidoo.desktop.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.model.AudioNoteModel
import com.scribidoo.app.canvas.model.ImageModel
import com.scribidoo.app.canvas.model.PageModel
import com.scribidoo.app.canvas.model.TextBoxModel
import com.scribidoo.app.canvas.state.SelectableType
import com.scribidoo.app.canvas.state.SelectedItem
import com.scribidoo.desktop.media.DesktopImageUtils
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

fun getImageBounds(img: ImageModel): Rect = Rect(img.x, img.y, img.x + img.width, img.y + img.height)

fun getAudioNoteBounds(audio: AudioNoteModel): Rect {
    val hw = audio.width / 2f
    val hh = audio.height / 2f
    return Rect(audio.x - hw, audio.y - hh, audio.x + hw, audio.y + hh)
}

fun getDesktopTextBoxBounds(tb: TextBoxModel): Rect {
    val approxWidth = maxOf(60f, tb.text.length * tb.fontSizeSp * 1.3f)
    val approxHeight = tb.fontSizeSp * 2.4f
    return Rect(tb.x - 12f, tb.y - approxHeight - 6f, tb.x + approxWidth + 12f, tb.y + 10f)
}

fun getSelectedItemBounds(item: SelectedItem, page: PageModel): Rect? {
    return when (item.type) {
        SelectableType.TEXT_BOX -> page.textBoxes.firstOrNull { it.id == item.id }?.let { getDesktopTextBoxBounds(it) }
        SelectableType.IMAGE -> page.images.firstOrNull { it.id == item.id }?.let { getImageBounds(it) }
        SelectableType.SHAPE -> page.shapes.firstOrNull { it.id == item.id }?.let {
            val pad = maxOf(it.strokeWidth / 2f, 16f)
            Rect(minOf(it.startX, it.endX) - pad, minOf(it.startY, it.endY) - pad, maxOf(it.startX, it.endX) + pad, maxOf(it.startY, it.endY) + pad)
        }
        SelectableType.AUDIO_NOTE -> page.audioNotes.firstOrNull { it.id == item.id }?.let { getAudioNoteBounds(it) }
    }
}

fun isPointInImage(img: ImageModel, pt: Offset): Boolean {
    val bounds = getImageBounds(img)
    if (img.rotationDegrees == 0f) return bounds.contains(pt)
    val center = Offset(img.x + img.width / 2f, img.y + img.height / 2f)
    val rad = Math.toRadians(-img.rotationDegrees.toDouble())
    val cos = cos(rad).toFloat()
    val sin = sin(rad).toFloat()
    val dx = pt.x - center.x
    val dy = pt.y - center.y
    val unrotated = Offset(center.x + (dx * cos - dy * sin), center.y + (dx * sin + dy * cos))
    return bounds.contains(unrotated)
}

fun DrawScope.drawImages(
    images: List<ImageModel>,
    bitmapCache: MutableMap<String, ImageBitmap>
) {
    for (img in images) {
        var bmp = bitmapCache[img.id]
        if (bmp == null) {
            val loaded = DesktopImageUtils.loadBitmap(img.localPath)
            if (loaded != null) {
                bmp = loaded
                bitmapCache[img.id] = loaded
            }
        }
        if (bmp != null) {
            val dstOffset = IntOffset(img.x.toInt(), img.y.toInt())
            val dstSize = IntSize(img.width.toInt(), img.height.toInt())
            val alpha = img.opacity.coerceIn(0.05f, 1.0f)
            if (img.rotationDegrees != 0f) {
                withTransform({
                    rotate(degrees = img.rotationDegrees, pivot = Offset(img.x + img.width / 2f, img.y + img.height / 2f))
                }) {
                    drawImage(image = bmp, dstOffset = dstOffset, dstSize = dstSize, alpha = alpha)
                }
            } else {
                drawImage(image = bmp, dstOffset = dstOffset, dstSize = dstSize, alpha = alpha)
            }
        }
    }
}

fun DrawScope.drawAudioNotes(
    audioNotes: List<AudioNoteModel>,
    playingAudioPath: String?,
    isPlaying: Boolean,
    textMeasurer: TextMeasurer
) {
    for (audio in audioNotes) {
        val bounds = getAudioNoteBounds(audio)
        val isCurrentPlaying = playingAudioPath == audio.localPath && isPlaying
        val pillBgColor = if (isCurrentPlaying) Color(0xFF2563EB) else Color(0xFF1E293B)
        val cornerRadiusPx = (bounds.height / 2f).coerceAtMost(24f)

        drawRoundRect(
            color = Color(0x33000000),
            topLeft = Offset(bounds.left + 2f, bounds.top + 3f),
            size = Size(bounds.width, bounds.height),
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )
        drawRoundRect(
            color = pillBgColor,
            topLeft = Offset(bounds.left, bounds.top),
            size = Size(bounds.width, bounds.height),
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )

        // Play / Pause Circle
        val playCenter = Offset(bounds.left + 26f, bounds.center.y)
        drawCircle(color = Color.White, radius = 13f, center = playCenter)
        if (isCurrentPlaying) {
            drawLine(Color(0xFF2563EB), Offset(playCenter.x - 3f, playCenter.y - 5f), Offset(playCenter.x - 3f, playCenter.y + 5f), 2.5f, StrokeCap.Round)
            drawLine(Color(0xFF2563EB), Offset(playCenter.x + 3f, playCenter.y - 5f), Offset(playCenter.x + 3f, playCenter.y + 5f), 2.5f, StrokeCap.Round)
        } else {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(playCenter.x - 3f, playCenter.y - 5f)
                lineTo(playCenter.x + 5f, playCenter.y)
                lineTo(playCenter.x - 3f, playCenter.y + 5f)
                close()
            }
            drawPath(path, Color(0xFF1E293B))
        }

        // Title and Duration
        val titleText = audio.label.take(18)
        val mins = (audio.durationMs / 1000L) / 60L
        val secs = (audio.durationMs / 1000L) % 60L
        val durText = String.format("%02d:%02d", mins, secs)

        drawText(
            textMeasurer = textMeasurer,
            text = "$titleText  $durText",
            topLeft = Offset(bounds.left + 46f, bounds.top + 7f),
            style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        )
    }
}

fun DrawScope.drawDesktopSelectedItemOverlay(
    item: SelectedItem,
    page: PageModel,
    scale: Float,
    textMeasurer: TextMeasurer
) {
    val bounds = getSelectedItemBounds(item, page) ?: return

    drawRoundRect(
        color = Color(0x222563EB),
        topLeft = Offset(bounds.left, bounds.top),
        size = Size(bounds.width, bounds.height),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawRoundRect(
        color = Color(0xFF2563EB),
        topLeft = Offset(bounds.left, bounds.top),
        size = Size(bounds.width, bounds.height),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 2.5f)
    )

    // Corner Dots
    val dotRadius = 5f
    listOf(
        Offset(bounds.left, bounds.top),
        Offset(bounds.right, bounds.top),
        Offset(bounds.left, bounds.bottom)
    ).forEach { pt ->
        drawCircle(color = Color.White, radius = dotRadius, center = pt)
        drawCircle(color = Color(0xFF2563EB), radius = dotRadius, center = pt, style = Stroke(width = 1.5f))
    }

    // Bottom-Right Resize Handle
    val resizeCenter = Offset(bounds.right, bounds.bottom)
    drawCircle(color = Color(0xFF2563EB), radius = 10f, center = resizeCenter)
    drawCircle(color = Color.White, radius = 10f, center = resizeCenter, style = Stroke(width = 2f))

    // Top action pills: [ Edit/Menu ] [ Delete ]
    val pillY = bounds.top - 28f
    val editLabel = if (item.type == SelectableType.TEXT_BOX) "Edit" else if (item.type == SelectableType.AUDIO_NOTE) "Trim" else "Info"
    drawRoundRect(color = Color(0xFF1E293B), topLeft = Offset(bounds.left, pillY), size = Size(50f, 22f), cornerRadius = CornerRadius(6f, 6f))
    drawText(textMeasurer, editLabel, Offset(bounds.left + 12f, pillY + 3f), TextStyle(Color.White, 11.sp, FontWeight.Bold))

    drawRoundRect(color = Color(0xFFDC2626), topLeft = Offset(bounds.left + 54f, pillY), size = Size(52f, 22f), cornerRadius = CornerRadius(6f, 6f))
    drawText(textMeasurer, "Delete", Offset(bounds.left + 60f, pillY + 3f), TextStyle(Color.White, 11.sp, FontWeight.Bold))
}
