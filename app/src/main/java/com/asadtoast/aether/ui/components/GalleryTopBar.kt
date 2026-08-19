package com.asadtoast.aether.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.asadtoast.aether.viewmodel.AetherViewModel

@Composable
fun GalleryTopBar(
    viewModel: AetherViewModel,
    uiState: com.asadtoast.aether.viewmodel.GalleryUiState,
    navController: NavController,
    isSelectionMode: Boolean,
    selectedFiles: Set<Long>,
    onSelectionModeChange: (Boolean) -> Unit,
    onOpenWiki: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditTags: () -> Unit
) {
    if (isSelectionMode) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSelectionModeChange(false) }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
                Text(
                    text = "${selectedFiles.size} selected",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onEditTags) {
                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Edit Tags", tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(onClick = {
                    viewModel.inboxMultipleFiles(selectedFiles)
                    onSelectionModeChange(false)
                }) {
                    Icon(Icons.Default.MoveToInbox, contentDescription = "To Inbox", tint = Color(0xFF64B5F6))
                }

                IconButton(onClick = {
                    viewModel.archiveMultipleFiles(selectedFiles)
                    onSelectionModeChange(false)
                }) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Archive", tint = Color.Green)
                }

                IconButton(onClick = {
                    viewModel.trashMultipleFiles(selectedFiles)
                    onSelectionModeChange(false)
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Trash", tint = Color.Red)
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenWiki) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.MenuBook,
                    contentDescription = "Open wiki"
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val sortText = when (uiState.sortType) {
                    0 -> "File size"
                    1 -> "Duration"
                    2 -> "Imported date"
                    20 -> "Hash (alph)"
                    15 -> "Framerate"
                    4 -> "Random"
                    else -> "Sort"
                }
                val isRandomActive = uiState.sortType == 4

                IconToggleButton(
                    checked = uiState.isTrashMode,
                    onCheckedChange = { isChecked ->
                        viewModel.fetchGallery(
                            tagsText = uiState.searchQuery,
                            sortType = uiState.sortType,
                            sortAscending = uiState.isAscending,
                            isTrash = isChecked
                        )
                    }
                ) {
                    Icon(
                        imageVector = if (uiState.isTrashMode) Icons.Filled.Delete else Icons.Outlined.Delete,
                        contentDescription = "Alternate trash",
                        tint = if (uiState.isTrashMode) Color.Red else Color.White
                    )
                }

                IconButton(
                    onClick = {
                        val newAscending = if (!isRandomActive) !uiState.isAscending else uiState.isAscending
                        viewModel.fetchGallery(
                            tagsText = uiState.searchQuery,
                            sortType = uiState.sortType,
                            sortAscending = newAscending,
                            isTrash = uiState.isTrashMode
                        )
                    }
                ) {
                    Icon(
                        imageVector = if (isRandomActive) Icons.Default.Refresh else if (uiState.isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = if (isRandomActive) "Reload" else "Change arrow direction",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                var displayMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton(
                        onClick = { displayMenu = true },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = sortText,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 90.dp)
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                    }

                    DropdownMenu(expanded = displayMenu, onDismissRequest = { displayMenu = false }) {
                        val sortOptions = listOf(
                            "Imported date" to 2, "Hash (Alph)" to 20, "File size" to 0,
                            "Duration" to 1, "Framerate" to 15, "Random" to 4
                        )
                        sortOptions.forEach { (label, type) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    displayMenu = false
                                    viewModel.fetchGallery(
                                        tagsText = uiState.searchQuery,
                                        sortType = type,
                                        sortAscending = uiState.isAscending,
                                        isTrash = uiState.isTrashMode
                                    )
                                }
                            )
                        }
                    }
                }

                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Views")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Archive / Delete") },
                            onClick = {
                                showMenu = false
                                viewModel.setReviewMode(true)
                            },
                            leadingIcon = { Icon(Icons.Default.ViewCarousel, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate processing") },
                            onClick = {
                                showMenu = false
                                navController.navigate("duplicate_processing")
                            },
                            leadingIcon = { Icon(Icons.Default.FilterNone, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Group duplicate processing") },
                            onClick = {
                                showMenu = false
                                navController.navigate("group_duplicate_processing")
                            },
                            leadingIcon = { Icon(Icons.Default.Collections, contentDescription = null) }
                        )
                    }
                }

                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
    }
}