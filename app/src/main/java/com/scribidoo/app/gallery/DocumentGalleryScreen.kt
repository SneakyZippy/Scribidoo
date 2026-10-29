package com.scribidoo.app.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribidoo.app.R
import com.scribidoo.app.canvas.model.DocumentModel
import com.scribidoo.app.data.DocumentStorageManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun DocumentGalleryScreen(
    onOpenDocument: (String) -> Unit,
    onNewDocument: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val storageManager = remember { DocumentStorageManager(context) }

    var documents by remember { mutableStateOf<List<DocumentModel>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showRenameDialogFor by remember { mutableStateOf<DocumentModel?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showDeleteConfirmFor by remember { mutableStateOf<DocumentModel?>(null) }
    var showBrandShowcaseDialog by remember { mutableStateOf(false) }

    fun refreshList() {
        coroutineScope.launch {
            documents = storageManager.listDocuments()
        }
    }

    LaunchedEffect(Unit) {
        refreshList()
    }

    val filteredDocs = remember(documents, searchQuery) {
        if (searchQuery.isBlank()) documents
        else documents.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Bar with Brand Identity Showcase
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Watercolor Logo & Title Lockup
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showBrandShowcaseDialog = true }
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.scribidoo_brand_icon),
                                contentDescription = "Scribidoo Monogram",
                                modifier = Modifier
                                    .size(44.dp)
                                    .shadow(2.dp, CircleShape),
                                contentScale = ContentScale.Fit
                            )
                            Column {
                                Image(
                                    painter = painterResource(id = R.drawable.scribidoo_logotype),
                                    contentDescription = "Scribidoo",
                                    modifier = Modifier.height(28.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Text(
                                    text = if (documents.isEmpty()) "Notebooks Gallery" else "${documents.size} Notebooks",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Right header action buttons
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showBrandShowcaseDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Brand Identity Showcase",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search & Filter Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        placeholder = { Text("Search your notes & sketches...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            }

            // Document Grid or Empty State
            if (filteredDocs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.scribidoo_logo_watercolor),
                            contentDescription = "Scribidoo Brand Mark",
                            modifier = Modifier
                                .size(180.dp)
                                .shadow(6.dp, RoundedCornerShape(24.dp))
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFFFEFAF7))
                                .padding(12.dp),
                            contentScale = ContentScale.Fit
                        )

                        Text(
                            text = if (searchQuery.isNotBlank()) "No notes match '$searchQuery'" else "Welcome to Scribidoo",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = if (searchQuery.isNotBlank())
                                "Try searching with a different term."
                            else
                                "Your digital notebook for active stylus & touch. Create your first note to start sketching!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(320.dp)
                        )

                        Button(
                            onClick = onNewDocument,
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create New Notebook")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(filteredDocs, key = { it.id }) { doc ->
                        DocumentCard(
                            document = doc,
                            onClick = { onOpenDocument(doc.id) },
                            onRename = {
                                renameInput = doc.title
                                showRenameDialogFor = doc
                            },
                            onDuplicate = {
                                coroutineScope.launch {
                                    val copy = doc.copy(
                                        id = UUID.randomUUID().toString(),
                                        title = "${doc.title} (Copy)",
                                        createdAt = System.currentTimeMillis(),
                                        modifiedAt = System.currentTimeMillis()
                                    )
                                    storageManager.saveDocument(copy)
                                    refreshList()
                                }
                            },
                            onDelete = { showDeleteConfirmFor = doc }
                        )
                    }
                }
            }
        }

        // Floating Action Button to create a new notebook
        FloatingActionButton(
            onClick = onNewDocument,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(64.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create New Notebook", modifier = Modifier.size(32.dp))
        }

        // Rename Dialog
        if (showRenameDialogFor != null) {
            val target = showRenameDialogFor!!
            AlertDialog(
                onDismissRequest = { showRenameDialogFor = null },
                title = { Text("Rename Note", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter a new title for this notebook:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = renameInput,
                            onValueChange = { renameInput = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val newName = renameInput.trim()
                            if (newName.isNotBlank()) {
                                coroutineScope.launch {
                                    val updated = target.copy(title = newName, modifiedAt = System.currentTimeMillis())
                                    storageManager.saveDocument(updated)
                                    refreshList()
                                }
                            }
                            showRenameDialogFor = null
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialogFor = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirmFor != null) {
            val target = showDeleteConfirmFor!!
            AlertDialog(
                onDismissRequest = { showDeleteConfirmFor = null },
                title = { Text("Delete Note?", fontWeight = FontWeight.Bold) },
                text = {
                    Text("Are you sure you want to delete '${target.title}'? This action cannot be undone.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                storageManager.deleteDocument(target.id)
                                refreshList()
                            }
                            showDeleteConfirmFor = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmFor = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Brand Showcase Dialog
        if (showBrandShowcaseDialog) {
            AlertDialog(
                onDismissRequest = { showBrandShowcaseDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Scribidoo Identity", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.scribidoo_logo_watercolor),
                            contentDescription = "Watercolor Brand Mark",
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFFEFAF7))
                                .padding(10.dp),
                            contentScale = ContentScale.Fit
                        )

                        Text(
                            text = "Scribidoo",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Crafted for low-latency active stylus inking & multi-touch gestures on Android and Windows Touch devices.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider()

                        Row(
                            horizontalArrangement = Arrangement.SpaceAround,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(id = R.drawable.scribidoo_brand_icon),
                                    contentDescription = "Brand Icon",
                                    modifier = Modifier.size(54.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("01. Brand Icon", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(id = R.drawable.scribidoo_logotype),
                                    contentDescription = "Logotype",
                                    modifier = Modifier
                                        .height(54.dp)
                                        .width(120.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("02. Script Logotype", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBrandShowcaseDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun DocumentCard(
    document: DocumentModel,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val formattedDate = remember(document.modifiedAt) { dateFormat.format(Date(document.modifiedAt)) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, hoveredElevation = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            // Notebook Preview Cover
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.25f)
                    .background(Color(0xFFF9F7F3))
                    .border(1.dp, Color(0x15000000), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Subtle notebook grid/dot watermark
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.scribidoo_brand_icon),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        alpha = 0.85f,
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (document.pages.size == 1) "1 page" else "${document.pages.size} pages",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0x99000000)
                    )
                }

                // Layout Mode Tag (Paged vs Infinite)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0x20000000),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = document.layoutMode.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xCC000000),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Notebook Info Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formattedDate,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open Note") },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onDuplicate()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
