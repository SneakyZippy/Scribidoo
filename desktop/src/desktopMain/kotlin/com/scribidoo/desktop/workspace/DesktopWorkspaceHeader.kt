package com.scribidoo.desktop.workspace

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.canvas.model.CanvasLayoutMode
import com.scribidoo.app.canvas.state.CanvasState

@Composable
fun DesktopWorkspaceHeader(
    state: CanvasState,
    onNavigateBack: () -> Unit,
    onRenameClick: () -> Unit,
    onSaveClick: () -> Unit,
    onExportPngCurrent: () -> Unit,
    onExportPngAll: () -> Unit,
    onExportPdf: () -> Unit = {},
    onImportPdf: () -> Unit = {},
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(18.dp))
            .border(1.dp, Color(0x1F000000), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Back to Gallery button
            IconButton(onClick = onNavigateBack, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Gallery",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(modifier = Modifier.height(18.dp).width(1.dp).background(Color(0x22000000)))

            // Note Title (Click to rename)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRenameClick)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = state.document.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp)
                )
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (state.document.layoutMode == CanvasLayoutMode.PAGED) {
                Box(modifier = Modifier.height(18.dp).width(1.dp).background(Color(0x22000000)))

                IconButton(
                    onClick = { state.prevPage() },
                    enabled = state.document.activePageIndex > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous Page")
                }

                Text(
                    text = "${state.document.activePageIndex + 1}/${state.document.pages.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                IconButton(
                    onClick = { state.nextPage() },
                    enabled = state.document.activePageIndex < state.document.pages.size - 1,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next Page")
                }

                IconButton(onClick = { state.addNewPage() }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add New Page", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Box(modifier = Modifier.height(18.dp).width(1.dp).background(Color(0x22000000)))

            // Overflow Actions Menu
            Box {
                IconButton(onClick = { showMenu = !showMenu }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions", modifier = Modifier.size(18.dp))
                }

                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Save Document") },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { showMenu = false; onSaveClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Current Page (PNG)") },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { showMenu = false; onExportPngCurrent() }
                    )
                    DropdownMenuItem(
                        text = { Text("Export All Pages (PNG)") },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { showMenu = false; onExportPngAll() }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Document to PDF") },
                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                        onClick = { showMenu = false; onExportPdf() }
                    )
                    DropdownMenuItem(
                        text = { Text("Import PDF Document") },
                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                        onClick = { showMenu = false; onImportPdf() }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Note Backup (JSON)") },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { showMenu = false; onExportJson() }
                    )
                    DropdownMenuItem(
                        text = { Text("Import Note (JSON)") },
                        leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { showMenu = false; onImportJson() }
                    )
                    DropdownMenuItem(
                        text = { Text("Canvas Preferences") },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { showMenu = false; onOpenSettings() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Clear Page", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                        onClick = { showMenu = false; state.clearPage() }
                    )
                }
            }
        }
    }
}
