package com.asadtoast.aether.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.asadtoast.aether.data.FileMetadata
import com.asadtoast.aether.utils.formatDuration
import com.asadtoast.aether.viewmodel.AetherViewModel
import com.asadtoast.aether.viewmodel.UiEvent
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun GalleryScreen(
    navController: NavController,
    viewModel: AetherViewModel,
    pagedImages: LazyPagingItems<FileMetadata>,
    gridColumns: Int,
    onOpenSettings: () -> Unit,
    onOpenWiki: () -> Unit
) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()

    val uiState by viewModel.uiState.collectAsState()
    val tagSuggestions by viewModel.tagSuggestions.collectAsState()

    // reset scroll position when search query or trash mode changes
    LaunchedEffect(uiState.searchQuery, uiState.isTrashMode) {
        gridState.scrollToItem(0)
    }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectionPivotIndex by remember { mutableStateOf<Int?>(null) }

    var showTagDialog by remember { mutableStateOf(false) }
    var tagsInputState by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var isDeleteAction by remember { mutableStateOf(false) }
    var autocompleteQuery by remember { mutableStateOf<String?>(null) }
    var cursorPosition by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowToast -> {
                    android.widget.Toast.makeText(
                        context,
                        event.message,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(uiState.isReviewMode, pagedImages.itemCount) {
        if (uiState.isReviewMode && pagedImages.itemCount == 0) {
            viewModel.setReviewMode(false)
            android.widget.Toast.makeText(context, "Search something first", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // search only if no loaded images
    LaunchedEffect(Unit) {
        if (pagedImages.itemCount == 0) {
            viewModel.fetchGallery(
                tagsText = uiState.searchQuery,
                sortType = uiState.sortType,
                sortAscending = uiState.isAscending
            )
        }
    }

    // variables for the auto close keyboard
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isSelectionMode) {
        if (!isSelectionMode) {
            selectedFiles = emptySet()
            selectionPivotIndex = null
        }
    }

    val currentBackStackEntry = navController.currentBackStackEntry

    LaunchedEffect(currentBackStackEntry) {
        currentBackStackEntry?.savedStateHandle?.getStateFlow<String?>(
            "selected_tag_from_wiki",
            null
        )
            ?.collect { tagToAdd ->
                if (tagToAdd != null) {
                    val currentText = uiState.searchQuery
                    val prefix =
                        if (currentText.contains(",")) currentText.substringBeforeLast(",") + ", " else ""

                    viewModel.fetchGallery(tagsText = "$prefix$tagToAdd, ")

                    currentBackStackEntry.savedStateHandle.remove<String>("selected_tag_from_wiki")
                }
            }
    }

    if (showTagDialog) {
        val settingsManager = remember { com.asadtoast.aether.data.SettingsManager(context) }
        val isPrivacyKeyboardEnabled by settingsManager.privacyKeyboardFlow.collectAsState(initial = false)

        var dialogTagSuggestions by remember { mutableStateOf(tagSuggestions) }

        LaunchedEffect(autocompleteQuery) {
            autocompleteQuery?.let { query ->
                if (query.isNotEmpty()) {
                    kotlinx.coroutines.delay(300.milliseconds) // debounce
                    dialogTagSuggestions = viewModel.fetchTagSuggestions(query)
                } else {
                    dialogTagSuggestions = emptyList()
                }
            } ?: run {
                dialogTagSuggestions = emptyList()
            }
        }

        AlertDialog(
            onDismissRequest = {
                showTagDialog = false
                tagsInputState = androidx.compose.ui.text.input.TextFieldValue("")
                autocompleteQuery = null
                dialogTagSuggestions = emptyList()
            },
            title = { Text("Edit Tags in Batch") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Apply changes to ${selectedFiles.size} selected files.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Box {
                        OutlinedTextField(
                            value = tagsInputState,
                            onValueChange = { newValue ->
                                tagsInputState = newValue
                                val text = newValue.text
                                val cursor = newValue.selection.start
                                val textBeforeCursor = text.substring(0, cursor)
                                val lastComma = textBeforeCursor.lastIndexOf(',')
                                val currentTerm = if (lastComma == -1) textBeforeCursor else textBeforeCursor.substring(lastComma + 1)
                                val trimmedTerm = currentTerm.trimStart()

                                if (trimmedTerm.isNotEmpty()) {
                                    autocompleteQuery = trimmedTerm
                                    cursorPosition = cursor
                                } else {
                                    autocompleteQuery = null
                                }
                            },
                            label = { Text("Tags (comma separated)") },
                            placeholder = { Text("e.g. tag1, tag2") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = if (isPrivacyKeyboardEnabled) {
                                KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Unspecified,
                                    autoCorrectEnabled = false,
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                                    imeAction = ImeAction.Unspecified
                                )
                            } else {
                                KeyboardOptions.Default
                            }
                        )

                        DropdownMenu(
                            expanded = autocompleteQuery != null && dialogTagSuggestions.isNotEmpty(),
                            onDismissRequest = { autocompleteQuery = null },
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .heightIn(max = 250.dp),
                            properties = PopupProperties(focusable = false)
                        ) {
                            dialogTagSuggestions.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text("${suggestion.value} (${suggestion.count.toInt()})") },
                                    onClick = {
                                        val currentText = tagsInputState.text
                                        val cursor = tagsInputState.selection.start
                                        val textBeforeCursor = currentText.substring(0, cursor)
                                        val textAfterCursor = currentText.substring(cursor)

                                        val lastCommaIndex = textBeforeCursor.lastIndexOf(',')
                                        val prefix = if (lastCommaIndex == -1) "" else textBeforeCursor.substring(0, lastCommaIndex + 1) + " "

                                        val newInsertion = "$prefix${suggestion.value}, "
                                        val newText = newInsertion + textAfterCursor

                                        tagsInputState = androidx.compose.ui.text.input.TextFieldValue(
                                            text = newText,
                                            selection = androidx.compose.ui.text.TextRange(newInsertion.length)
                                        )
                                        autocompleteQuery = null
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Action: ", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = !isDeleteAction,
                            onClick = { isDeleteAction = false },
                            label = { Text("Add") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = isDeleteAction,
                            onClick = { isDeleteAction = true },
                            label = { Text("Remove") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tagsList = tagsInputState.text.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        if (tagsList.isNotEmpty()) {
                            viewModel.modifyTagsForMultipleFiles(
                                fileIds = selectedFiles,
                                tagsText = tagsInputState.text,
                                isDelete = isDeleteAction,
                                targetService = "my tags"
                            )
                        }
                        showTagDialog = false
                        tagsInputState = androidx.compose.ui.text.input.TextFieldValue("")
                        autocompleteQuery = null
                        dialogTagSuggestions = emptyList()
                        isSelectionMode = false
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTagDialog = false
                    tagsInputState = androidx.compose.ui.text.input.TextFieldValue("")
                    autocompleteQuery = null
                    dialogTagSuggestions = emptyList()
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(top = 8.dp, bottom = 12.dp)
        ) {
            com.asadtoast.aether.ui.components.GalleryTopBar(
                viewModel = viewModel,
                uiState = uiState,
                navController = navController,
                isSelectionMode = isSelectionMode,
                selectedFiles = selectedFiles,
                onSelectionModeChange = { isSelectionMode = it },
                onOpenWiki = onOpenWiki,
                onOpenSettings = onOpenSettings,
                onEditTags = { showTagDialog = true }
            )

            Spacer(modifier = Modifier.height(6.dp))

            com.asadtoast.aether.ui.components.GallerySearchBar(
                viewModel = viewModel,
                uiState = uiState,
                focusManager = focusManager
            )

            Spacer(modifier = Modifier.height(10.dp))

            // progress bar
            AnimatedVisibility(visible = uiState.isProcessingBatch) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Processing files in background...",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray
                        )
                        Text(
                            text = "${uiState.batchProgress} / ${uiState.batchTotal}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray
                        )
                    }
                    LinearProgressIndicator(
                        progress = {
                            if (uiState.batchTotal > 0) {
                                uiState.batchProgress.toFloat() / uiState.batchTotal.toFloat()
                            } else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // gallery content
            val isRefreshing = pagedImages.loadState.refresh is LoadState.Loading
            val isEmpty = pagedImages.itemCount == 0

            if (isEmpty) {
                var dots by remember { mutableIntStateOf(0) }

                LaunchedEffect(isRefreshing) {
                    if (isRefreshing) {
                        while (isActive) {
                            dots = (dots + 1) % 4
                            kotlinx.coroutines.delay(300.milliseconds)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val message = when {
                        // if nothing has been searched
                        uiState.searchQuery.isEmpty() && !uiState.isTrashMode -> "No active search. Type a tag to begin."
                        // if network data is loading
                        isRefreshing -> "Loading${".".repeat(dots)}"
                        // if searched but not found
                        else -> "No results found."
                    }

                    Text(
                        text = message,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(
                        count = pagedImages.itemCount,
                        key = pagedImages.itemKey { it.id }
                    ) { index ->
                        val image = pagedImages[index]
                        if (image != null) {
                            val isSelected = selectedFiles.contains(image.id)
                            GalleryGridItem(
                                image = image,
                                isSelected = isSelected,
                                onSelect = {
                                    if (isSelectionMode) {
                                        if (isSelected) {
                                            selectedFiles = selectedFiles - image.id
                                            if (selectedFiles.isEmpty()) isSelectionMode = false
                                        } else {
                                            selectedFiles = selectedFiles + image.id
                                            selectionPivotIndex = index
                                        }
                                    } else {
                                        viewModel.selectImage(index)
                                    }
                                },
                                onLongSelect = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedFiles = setOf(image.id)
                                        selectionPivotIndex = index
                                    } else {
                                        if (selectionPivotIndex != null) {
                                            val start = minOf(selectionPivotIndex!!, index)
                                            val end = maxOf(selectionPivotIndex!!, index)
                                            val subListIds = mutableListOf<Long>()
                                            for (i in start..end) {
                                                pagedImages[i]?.let { subListIds.add(it.id) }
                                            }
                                            selectedFiles = selectedFiles + subListIds
                                            selectionPivotIndex = index
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // conditional for archive/delete gallery overlay
        if (uiState.isReviewMode) {
            ReviewScreen(
                viewModel = viewModel,
                pagedImages = pagedImages,
                onClose = { viewModel.setReviewMode(false) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryGridItem(
    image: FileMetadata,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLongSelect: () -> Unit
) {
    val isPdf = image.mime == "14"
    val isPsd = image.mime == "16"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(onClick = onSelect, onLongClick = onLongSelect)
    ) {
        if (isPdf || isPsd) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Description,
                    contentDescription = null,
                    tint = if (isPdf) Color.Red.copy(alpha = 0.7f) else Color.Cyan.copy(
                        alpha = 0.7f
                    ),
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = if (isPdf) "PDF" else "PSD",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(image.thumbnailUrl)
                    .crossfade(true)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = "Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White.copy(alpha = 0.25f), strokeWidth = 1.5.dp, modifier = Modifier.size(22.dp))
                    }
                }
            )
            if (image.duration != null && image.duration > 0.0) {
                Text(
                    text = formatDuration(image.duration),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF64B5F6).copy(alpha = 0.3f))
            )
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = Color(0xFF64B5F6),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.White, CircleShape)
            )
        }
    }
}