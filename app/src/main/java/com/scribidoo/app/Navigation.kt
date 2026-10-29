package com.scribidoo.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.scribidoo.app.canvas.ui.CanvasWorkspaceScreen
import com.scribidoo.app.gallery.DocumentGalleryScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(GalleryNavKey)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<GalleryNavKey> {
          DocumentGalleryScreen(
            onOpenDocument = { docId ->
              backStack.add(CanvasNavKey(docId))
            },
            onNewDocument = {
              backStack.add(CanvasNavKey(null))
            },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<CanvasNavKey> { key ->
          CanvasWorkspaceScreen(
            documentId = key.documentId,
            onNavigateBackToGallery = {
              backStack.removeLastOrNull()
            },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<Main> {
          DocumentGalleryScreen(
            onOpenDocument = { docId ->
              backStack.add(CanvasNavKey(docId))
            },
            onNewDocument = {
              backStack.add(CanvasNavKey(null))
            },
            modifier = Modifier.fillMaxSize()
          )
        }
      },
  )
}
