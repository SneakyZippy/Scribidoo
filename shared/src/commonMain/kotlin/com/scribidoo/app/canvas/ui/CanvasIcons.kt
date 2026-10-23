package com.scribidoo.app.canvas.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object CanvasIcons {

    val HighlighterMarker: ImageVector by lazy {
        ImageVector.Builder(
            name = "HighlighterMarker",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(16.59f, 2.83f)
                lineTo(21.17f, 7.41f)
                curveTo(21.95f, 8.19f, 21.95f, 9.46f, 21.17f, 10.24f)
                lineTo(17.5f, 13.91f)
                lineTo(10.09f, 6.5f)
                lineTo(13.76f, 2.83f)
                curveTo(14.54f, 2.05f, 15.81f, 2.05f, 16.59f, 2.83f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(8.68f, 7.91f)
                lineTo(16.09f, 15.32f)
                lineTo(13.5f, 17.91f)
                lineTo(6.09f, 10.5f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(4.68f, 11.91f)
                lineTo(12.09f, 19.32f)
                lineTo(8.5f, 21.5f)
                lineTo(2.5f, 21.5f)
                lineTo(2.5f, 15.5f)
                close()
            }
        }.build()
    }

    val EraserStroke: ImageVector by lazy {
        ImageVector.Builder(
            name = "EraserStroke",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(15.14f, 3.14f)
                lineTo(20.86f, 8.86f)
                curveTo(21.64f, 9.64f, 21.64f, 10.9f, 20.86f, 11.68f)
                lineTo(11.44f, 21.1f)
                curveTo(11.05f, 21.49f, 10.54f, 21.71f, 10f, 21.71f)
                lineTo(3f, 21.71f)
                curveTo(2.45f, 21.71f, 2f, 21.26f, 2f, 20.71f)
                curveTo(2f, 20.44f, 2.11f, 20.19f, 2.29f, 20.01f)
                lineTo(7.71f, 14.59f)
                lineTo(15.14f, 3.14f)
                close()
                moveTo(4f, 19.71f)
                lineTo(9.41f, 19.71f)
                lineTo(17.44f, 11.68f)
                lineTo(12.32f, 6.56f)
                lineTo(4f, 14.88f)
                lineTo(4f, 19.71f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(8.5f, 13.8f)
                lineTo(13.6f, 18.9f)
                lineTo(12.2f, 20.3f)
                lineTo(7.1f, 15.2f)
                close()
            }
        }.build()
    }

    val EraserPrecision: ImageVector by lazy {
        ImageVector.Builder(
            name = "EraserPrecision",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(15.14f, 3.14f)
                lineTo(20.86f, 8.86f)
                curveTo(21.64f, 9.64f, 21.64f, 10.9f, 20.86f, 11.68f)
                lineTo(11.44f, 21.1f)
                curveTo(11.05f, 21.49f, 10.54f, 21.71f, 10f, 21.71f)
                lineTo(3f, 21.71f)
                curveTo(2.45f, 21.71f, 2f, 21.26f, 2f, 20.71f)
                curveTo(2f, 20.44f, 2.11f, 20.19f, 2.29f, 20.01f)
                lineTo(7.71f, 14.59f)
                lineTo(15.14f, 3.14f)
                close()
                moveTo(4f, 19.71f)
                lineTo(9.41f, 19.71f)
                lineTo(17.44f, 11.68f)
                lineTo(12.32f, 6.56f)
                lineTo(4f, 14.88f)
                lineTo(4f, 19.71f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(5f, 5f)
                lineTo(9f, 5f)
                lineTo(9f, 7f)
                lineTo(5f, 7f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 3f)
                lineTo(8f, 3f)
                lineTo(8f, 9f)
                lineTo(6f, 9f)
                close()
            }
        }.build()
    }
}
