package com.scribidoo.app.canvas.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.state.CanvasState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasWorkspaceScreen(
    modifier: Modifier = Modifier,
    state: CanvasState = remember { CanvasState() }
) {
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
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
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
