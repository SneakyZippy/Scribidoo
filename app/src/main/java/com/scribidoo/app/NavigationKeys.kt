package com.scribidoo.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object GalleryNavKey : NavKey

@Serializable data class CanvasNavKey(val documentId: String? = null) : NavKey
