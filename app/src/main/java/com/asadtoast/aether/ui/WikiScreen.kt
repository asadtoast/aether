@file:Suppress("DEPRECATION")

package com.asadtoast.aether.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import com.asadtoast.aether.data.TagWikiEntity
import com.asadtoast.aether.data.WikiReference
import com.asadtoast.aether.ui.theme.getNamespaceColor
import com.asadtoast.aether.utils.formatDuration
import com.asadtoast.aether.viewmodel.WikiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenEditDialog(
    initialTag: String,
    initialDescription: String,
    initialAliases: List<String>,
    initialReferences: List<WikiReference>,
    initialCount: Long,
    allWikiEntries: List<TagWikiEntity>,
    onSave: (String, String, List<String>, List<WikiReference>, Long) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var description by remember(initialTag) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initialDescription)) }
    val aliases = remember(initialTag) { initialAliases.toMutableStateList() }
    val references = remember(initialTag) { initialReferences.toMutableStateList() }
    var showDeleteConfirm by remember(initialTag) { mutableStateOf(false) }
    var currentTagName by remember(initialTag) { mutableStateOf(initialTag) }

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val settingsManager = remember { com.asadtoast.aether.data.SettingsManager(context) }
    val isPrivacyKeyboardEnabled by settingsManager.privacyKeyboardFlow.collectAsState(initial = true)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(if (initialTag.isEmpty()) "New tag" else "Edit tag") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cancel") }
                    },
                    actions = {
                        TextButton(onClick = { onSave(currentTagName, description.text, aliases.toList(), references.toList(), initialCount) }) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    key(isPrivacyKeyboardEnabled) {
                        OutlinedTextField(
                            value = currentTagName,
                            onValueChange = { currentTagName = it.replace("\n", "") },
                            label = { Text("Hydrus tag") },
                            readOnly = initialTag.isNotEmpty(),
                            enabled = initialTag.isEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Unspecified,
                                autoCorrectEnabled = !isPrivacyKeyboardEnabled,
                                keyboardType = if (isPrivacyKeyboardEnabled) KeyboardType.Password else KeyboardType.Text,
                                imeAction = ImeAction.Next
                            )
                        )
                    }
                }

                item {
                    key(isPrivacyKeyboardEnabled) {
                        Column {
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Description") },
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Unspecified,
                                    autoCorrectEnabled = !isPrivacyKeyboardEnabled,
                                    keyboardType = if (isPrivacyKeyboardEnabled) KeyboardType.Password else KeyboardType.Text,
                                    imeAction = ImeAction.Done
                                )
                            )

                            val cursorPosition = description.selection.start
                            val textBeforeCursor = description.text.substring(0, cursorPosition.coerceIn(0, description.text.length))
                            val lastOpenBracket = textBeforeCursor.lastIndexOf("[[")
                            val lastCloseBracket = textBeforeCursor.lastIndexOf("]]")

                            var showAutocomplete by remember { mutableStateOf(false) }
                            var autocompleteQuery by remember { mutableStateOf("") }

                            if (lastOpenBracket != -1 && lastOpenBracket > lastCloseBracket) {
                                autocompleteQuery = textBeforeCursor.substring(lastOpenBracket + 2)
                                showAutocomplete = autocompleteQuery.isNotEmpty()
                            } else {
                                showAutocomplete = false
                            }

                            val suggestions = remember(autocompleteQuery, allWikiEntries) {
                                if (autocompleteQuery.isBlank()) emptyList()
                                else allWikiEntries.filter { it.tagName.contains(autocompleteQuery, ignoreCase = true) }.take(5)
                            }

                            AnimatedVisibility(visible = showAutocomplete && suggestions.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                ) {
                                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                        items(suggestions) { entry ->
                                            Text(
                                                text = entry.tagName,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val textBefore = description.text.substring(0, lastOpenBracket)
                                                        val textAfter = description.text.substring(cursorPosition)
                                                        val insertedText = "[[${entry.tagName}]] "
                                                        val newText = textBefore + insertedText + textAfter
                                                        val newCursorPos = textBefore.length + insertedText.length
                                                        description = androidx.compose.ui.text.input.TextFieldValue(
                                                            text = newText,
                                                            selection = androidx.compose.ui.text.TextRange(newCursorPos)
                                                        )
                                                        showAutocomplete = false
                                                    }
                                                    .padding(16.dp)
                                            )
                                            HorizontalDivider()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // aliases
                item { Text("Aliases", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                itemsIndexed(aliases) { index, alias ->
                    key(isPrivacyKeyboardEnabled) {
                        OutlinedTextField(
                            value = alias,
                            onValueChange = { aliases[index] = it.replace("\n", "") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { aliases.removeAt(index) }) { Icon(Icons.Default.Delete, "Delete") }
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Unspecified,
                                autoCorrectEnabled = !isPrivacyKeyboardEnabled,
                                keyboardType = if (isPrivacyKeyboardEnabled) KeyboardType.Password else KeyboardType.Text,
                                imeAction = ImeAction.Done
                            )
                        )
                    }
                }
                item {
                    OutlinedButton(onClick = { aliases.add("") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add alias")
                    }
                }

                // references
                item { Text("External references", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                itemsIndexed(references) { index, ref ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            key(isPrivacyKeyboardEnabled) {
                                OutlinedTextField(
                                    value = ref.siteName,
                                    onValueChange = { references[index] = ref.copy(siteName = it.replace("\n", "")) },
                                    label = { Text("Origin (Ex: Pixiv, Danbooru)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Words,
                                        autoCorrectEnabled = !isPrivacyKeyboardEnabled,
                                        keyboardType = if (isPrivacyKeyboardEnabled) KeyboardType.Password else KeyboardType.Text,
                                        imeAction = ImeAction.Next
                                    )
                                )
                            }
                            key(isPrivacyKeyboardEnabled) {
                                OutlinedTextField(
                                    value = ref.originalTag,
                                    onValueChange = { references[index] = ref.copy(originalTag = it.replace("\n", "")) },
                                    label = { Text("Original Tag (Optional)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Unspecified,
                                        autoCorrectEnabled = !isPrivacyKeyboardEnabled,
                                        keyboardType = if (isPrivacyKeyboardEnabled) KeyboardType.Password else KeyboardType.Text,
                                        imeAction = ImeAction.Next
                                    )
                                )
                            }
                            key(isPrivacyKeyboardEnabled) {
                                OutlinedTextField(
                                    value = ref.romaji,
                                    onValueChange = { references[index] = ref.copy(romaji = it.replace("\n", "")) },
                                    label = { Text("Tag in romaji (Optional)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Words,
                                        autoCorrectEnabled = !isPrivacyKeyboardEnabled,
                                        keyboardType = if (isPrivacyKeyboardEnabled) KeyboardType.Password else KeyboardType.Text,
                                        imeAction = ImeAction.Next
                                    )
                                )
                            }
                            key(isPrivacyKeyboardEnabled) {
                                OutlinedTextField(
                                    value = ref.url,
                                    onValueChange = { references[index] = ref.copy(url = it.replace("\n", "")) },
                                    label = { Text("URL") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = if (isPrivacyKeyboardEnabled) KeyboardType.Password else KeyboardType.Uri,
                                        capitalization = KeyboardCapitalization.Unspecified,
                                        autoCorrectEnabled = false,
                                        imeAction = ImeAction.Done
                                    ),
                                    trailingIcon = {
                                        IconButton(onClick = { try { uriHandler.openUri(ref.url) } catch(_: Exception) {} }) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open")
                                        }
                                    }
                                )
                            }
                            IconButton(onClick = { references.removeAt(index) }, modifier = Modifier.align(Alignment.End)) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { references.add(WikiReference("", "", ""))}, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add reference")
                    }
                }

                // delete button
                if (initialTag.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD32F2F),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteForever, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Remove tag from wiki")
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }

        // remove confirmation
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Are you sure?") },
                text = { Text("This will NOT DELETE '${initialTag}' from Hydrus, only remove the description, alias and references from the tag wiki.\n\nIf you want to delete the tag from Hydrus, remove the tags from the files or use Hydrus to replace it with a different one.") },
                confirmButton = {
                    TextButton(
                        onClick = { showDeleteConfirm = false; onDelete(initialTag); onDismiss() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F))
                    ) { Text("Remove permanently") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WikiScreen(
    wikiViewModel: WikiViewModel,
    onClose: () -> Unit,
    navController: NavController,
    onOpenTagDetail: (TagWikiEntity) -> Unit
) {
    // network/database flows
    val allWikiEntries by wikiViewModel.allWikiEntries.collectAsState(initial = emptyList())
    val mutedTags by wikiViewModel.mutedTags.collectAsState(initial = emptySet())
    val tagSuggestions by wikiViewModel.tagSuggestions.collectAsState(initial = emptyList())
    val latestBackup by wikiViewModel.latestBackup.collectAsState(initial = null)

    // simple booleans
    val isSyncing = wikiViewModel.isSyncing
    val isBackupProcessing = wikiViewModel.isBackupProcessing
    val showMutedTags = wikiViewModel.showMutedTags

    var showSyncPopup by remember { mutableStateOf(false) }
    var syncWasSuccessful by remember { mutableStateOf(false) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = wikiViewModel.scrollIndex,
        initialFirstVisibleItemScrollOffset = wikiViewModel.scrollOffset
    )

    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            wikiViewModel.scrollIndex = index
            wikiViewModel.scrollOffset = offset
        }
    }

    val filteredEntries = remember(wikiViewModel.searchQuery, allWikiEntries) {
        if (wikiViewModel.searchQuery.isBlank()) allWikiEntries
        else allWikiEntries.filter {
            it.tagName.contains(wikiViewModel.searchQuery, ignoreCase = true) ||
                    it.aliases.any { alias -> alias.contains(wikiViewModel.searchQuery, ignoreCase = true) }
        }
    }

    // category states
    val namespacesOrder = listOf("creator", "artist", "series", "copyright", "character", "general","system" , "meta")

    val visibleCategories by wikiViewModel.visibleCategories.collectAsState(initial = emptySet())
    var showFilterMenu by remember { mutableStateOf(false) }

    // filtering
    val groupedAndFilteredTags = remember(filteredEntries, visibleCategories, mutedTags, showMutedTags) {
        filteredEntries
            .filter { entry -> showMutedTags || !mutedTags.contains(entry.tagName) }
            .groupBy { entry ->
                val ns = if (entry.tagName.contains(":")) entry.tagName.substringBefore(":").lowercase() else "general"
                if (ns in namespacesOrder) ns else "general"
            }
            .filterKeys { it in visibleCategories }
            .toSortedMap(compareBy { namespacesOrder.indexOf(it).let { i -> if (i == -1) 99 else i } })
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showSyncConfirmDialog by remember { mutableStateOf(false) }
    var expandedMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var previousBackupState by remember { mutableStateOf(isBackupProcessing) }
    LaunchedEffect(isBackupProcessing) {
        if (previousBackupState && !isBackupProcessing) {
            Toast.makeText(context, "Backup finished", Toast.LENGTH_SHORT).show()
        }
        previousBackupState = isBackupProcessing
    }

    val settingsManager = remember { com.asadtoast.aether.data.SettingsManager(context) }
    val isPrivacyKeyboardEnabled by settingsManager.privacyKeyboardFlow.collectAsState(initial = true)
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    var editingTag by remember { mutableStateOf("") }
    var editingCount by remember { mutableLongStateOf(0L) }
    var editingDescription by remember { mutableStateOf("") }
    var editingAliases by remember { mutableStateOf<List<String>>(emptyList()) }
    var editingReferences by remember { mutableStateOf<List<WikiReference>>(emptyList()) }

    // variable for autobackup module
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var restoreMergeMode by remember { mutableStateOf(false) }

    // mute selection
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTagsForMute by remember { mutableStateOf<Set<String>>(emptySet()) }

    // clean if user not in selection mode
    LaunchedEffect(isSelectionMode) {
        if (!isSelectionMode) selectedTagsForMute = emptySet()
    }


    Scaffold(
        topBar = {
            Column {
                if (isSelectionMode) {
                    // contextual textbar
                    TopAppBar(
                        title = { Text("${selectedTagsForMute.size} selected") },
                        navigationIcon = {
                            IconButton(onClick = {
                                isSelectionMode = false
                                selectedTagsForMute = emptySet()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                wikiViewModel.muteMultipleTags(selectedTagsForMute)
                                isSelectionMode = false
                                selectedTagsForMute = emptySet()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = "Mute selected",
                                    tint = Color.Red
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                } else {
                    // normal topbar
                    TopAppBar(
                        title = { Text("Tag Wiki") },
                        navigationIcon = {
                            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go back") }
                        },
                        actions = {
                            if (isSyncing || isBackupProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 16.dp).size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                // filter button
                                Box {
                                    IconButton(onClick = { showFilterMenu = true }) {
                                        Icon(Icons.Default.FilterList, contentDescription = "Filter categories")
                                    }

                                    DropdownMenu(
                                        expanded = showFilterMenu,
                                        onDismissRequest = { showFilterMenu = false }
                                    ) {
                                        namespacesOrder.forEach { category ->
                                            val isChecked = visibleCategories.contains(category)

                                            DropdownMenuItem(
                                                text = { Text(category.replaceFirstChar { it.uppercase() } )},
                                                trailingIcon = {
                                                    Checkbox(
                                                        checked = isChecked,
                                                        onCheckedChange = null
                                                    )
                                                },
                                                onClick = {
                                                    wikiViewModel.toggleCategoryVisibility(category)
                                                }
                                            )
                                        }

                                        // hidden toggle
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.DarkGray)

                                        DropdownMenuItem(
                                            text = { Text("Show muted tags", color = if (wikiViewModel.showMutedTags) Color.White else Color.Gray) },
                                            trailingIcon = { Checkbox(checked = wikiViewModel.showMutedTags, onCheckedChange = null) },
                                            onClick = { wikiViewModel.toggleShowMutedTags() }
                                        )
                                    }
                                }

                                // if not hidden, show more options
                                IconButton(onClick = { expandedMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Import options")
                                }

                                DropdownMenu(
                                    expanded = expandedMenu,
                                    onDismissRequest = { expandedMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Import special/individual tag") },
                                        onClick = {
                                            expandedMenu = false
                                            showImportDialog = true
                                            wikiViewModel.fetchTagSuggestions("")
                                        },
                                        leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Full sync") },
                                        onClick = {
                                            expandedMenu = false
                                            showSyncConfirmDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Force manual backup") },
                                        onClick = {
                                            wikiViewModel.forceManualBackup()
                                            showBackupDialog = false
                                            Toast.makeText(context, "Backup initiated", Toast.LENGTH_SHORT).show()
                                        },
                                        leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Restore backup") },
                                        onClick = {
                                            expandedMenu = false
                                            showBackupDialog = true
                                            wikiViewModel.checkForBackup()
                                        },
                                        leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) }
                                    )
                                }
                            }
                        }
                    )
                }

                // search bar
                key(isPrivacyKeyboardEnabled) {
                    OutlinedTextField(
                        value = wikiViewModel.searchQuery,
                        onValueChange = { wikiViewModel.searchQuery = it },
                        placeholder = { Text("Search on wiki...") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (wikiViewModel.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { wikiViewModel.searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, null)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Unspecified,
                            autoCorrectEnabled = !isPrivacyKeyboardEnabled,
                            keyboardType = if (isPrivacyKeyboardEnabled) KeyboardType.Password else KeyboardType.Text,
                            imeAction = ImeAction.Done
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // cleanup
                    editingTag = ""
                    editingCount = 0L
                    editingDescription = ""
                    editingAliases = emptyList()
                    editingReferences = emptyList()
                    showEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add new tag")
            }
        }
    ) { paddingValues ->
        // lazycolumn for tag category
        if (groupedAndFilteredTags.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text(if (allWikiEntries.isEmpty()) "Wiki is empty." else "No results found or all categories hidden.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {

                groupedAndFilteredTags.forEach { (namespace, tagsList) ->
                    // category header
                    item(key = "header_$namespace") {
                        val categoryColor = tagsList.firstOrNull()?.let { getNamespaceColor(it.tagName) } ?: Color.LightGray
                        Text(
                            text = namespace.replaceFirstChar { it.uppercase() },
                            color = categoryColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp, end = 16.dp)
                        )
                    }

                    // tag list for category
                    items(tagsList, key = { it.tagName }) { entry ->
                        val isMuted = mutedTags.contains(entry.tagName)
                        val isSelected = selectedTagsForMute.contains(entry.tagName)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 50.dp)
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                // selection toggle
                                                selectedTagsForMute = if (isSelected) {
                                                    selectedTagsForMute - entry.tagName
                                                } else {
                                                    selectedTagsForMute + entry.tagName
                                                }
                                                // if last unselected, exit selection
                                                if (selectedTagsForMute.isEmpty()) isSelectionMode = false
                                            } else {
                                                keyboardController?.hide()
                                                onOpenTagDetail(entry)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                isSelectionMode = true
                                                selectedTagsForMute = setOf(entry.tagName)
                                            }
                                        }
                                    )
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // checkbox
                                if (isSelectionMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }

                                // tag texts
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "${entry.tagName} (${entry.count})",
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMuted) Color.Gray else getNamespaceColor(entry.tagName),
                                        textDecoration = if (isMuted) TextDecoration.LineThrough else TextDecoration.None,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (entry.aliases.isNotEmpty()) {
                                        Text(
                                            text = "Aliases: ${entry.aliases.joinToString(", ")}",
                                            color = if (isMuted) Color.DarkGray else MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                // hide add to search on selection
                                if (!isSelectionMode) {
                                    IconButton(
                                        onClick = {
                                            // save tag in memory
                                            navController.previousBackStackEntry
                                                ?.savedStateHandle
                                                ?.set("selected_tag_from_wiki", entry.tagName)

                                            Toast.makeText(
                                                context,
                                                "Tag added to search",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add to search")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showEditDialog) {
            FullScreenEditDialog(
                initialTag = editingTag,
                initialCount = editingCount,
                initialDescription = editingDescription,
                initialAliases = editingAliases,
                initialReferences = editingReferences,
                allWikiEntries = allWikiEntries,
                onSave = { tag, desc, aliases, refs, count ->
                    wikiViewModel.saveWikiEntry(tag, desc, aliases, refs, count)
                    showEditDialog = false
                },
                onDelete = { tag ->
                    wikiViewModel.deleteWikiEntry(tag)
                    showEditDialog = false
                },
                onDismiss = {
                    showEditDialog = false
                }
            )
        }

        if (showSyncConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showSyncConfirmDialog = false },
                title = { Text("Sync tags") },
                text = { Text("This will download every alphanumeric tag from Hydrus and will add them to the Wiki.\n\nDon't worry your current descriptions, aliases will NOT be deleted") },
                confirmButton = {
                    TextButton(onClick = {
                        wikiViewModel.launchAlphanumericWikiScraper { isSuccess ->
                            syncWasSuccessful = isSuccess
                            showSyncPopup = true
                        }
                        showSyncConfirmDialog = false
                    }) {
                        Text("Sync")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSyncConfirmDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showImportDialog) {
            var importQuery by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("Import from hydrus") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        key(isPrivacyKeyboardEnabled) {
                            OutlinedTextField(
                                value = importQuery,
                                onValueChange = { newQuery ->
                                    importQuery = newQuery
                                    wikiViewModel.fetchTagSuggestions(newQuery)
                                },
                                placeholder = { Text("Ex: character:asuka...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = { Icon(Icons.Default.Search, null) },
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Unspecified,
                                    autoCorrectEnabled = !isPrivacyKeyboardEnabled,
                                    keyboardType = if (isPrivacyKeyboardEnabled) KeyboardType.Password else KeyboardType.Text,
                                    imeAction = ImeAction.Done
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                            items(tagSuggestions) { suggestion ->
                                val cleanTag = if (suggestion.value.contains("➔")) {
                                    suggestion.value.substringAfter("➔").trim()
                                } else {
                                    suggestion.value
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showImportDialog = false
                                            editingTag = cleanTag
                                            editingCount = suggestion.count.toLong()
                                            editingDescription = ""
                                            editingAliases = emptyList()
                                            editingReferences = emptyList()
                                            showEditDialog = true
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = suggestion.value,
                                        color = getNamespaceColor(cleanTag),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = suggestion.count.toInt().toString(),
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showImportDialog = false }) { Text("Close") }
                }
            )
        }

        // full sync popup
        if (showSyncPopup) {
            AlertDialog(
                onDismissRequest = {
                    showSyncPopup = false
                    wikiViewModel.stopSyncingState()
                },
                title = {
                    Text(if (syncWasSuccessful) "Sync successful" else "Unable to sync")
                },
                text = {
                    Text(
                        if (syncWasSuccessful)
                            "All alphanumerical tags have been retrieved from Hydrus successfully."
                        else
                            "Sync was unable to retrieve some tags. Check your server connection."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSyncPopup = false
                            wikiViewModel.stopSyncingState()
                        }
                    ) {
                        Text("Accept")
                    }
                }
            )
        }


        // autobackup dialogs
        if (showBackupDialog) {
            AlertDialog(
                onDismissRequest = { showBackupDialog = false },
                title = { Text("Restore Tag Wiki") },
                text = {
                    if (wikiViewModel.isCheckingBackup) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Searching Hydrus for backups...")
                        }
                    } else if (latestBackup != null) {
                        val formattedDate = remember(latestBackup!!.timestamp) {
                            java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(latestBackup!!.timestamp*1000))
                        }
                        Column {
                            Text("Backup found on server:", fontWeight = FontWeight.Bold)
                            Text("Date: $formattedDate", color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("How would you like to restore this backup?")
                        }
                    } else {
                        Text("No backups found with the tag 'app_backup:tag_wiki' on the server.")
                    }
                },
                confirmButton = {
                    if (!wikiViewModel.isCheckingBackup && latestBackup != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {

                            // merge
                            TextButton(onClick = {
                                restoreMergeMode = true
                                showBackupDialog = false
                                showRestoreConfirmDialog = true
                            }) { Text("Merge with local data") }

                            // overwrite
                            TextButton(
                                onClick = {
                                    restoreMergeMode = false
                                    showBackupDialog = false
                                    showRestoreConfirmDialog = true
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F))
                            ) { Text("Overwrite local data") }

                            // cancel
                            TextButton(onClick = { showBackupDialog = false }) { Text("Cancel") }
                        }
                    } else if (!wikiViewModel.isCheckingBackup) {
                        TextButton(onClick = { showBackupDialog = false }) { Text("Close") }
                    }
                },
                dismissButton = null
            )
        }

        if (showRestoreConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirmDialog = false },
                title = { Text("Irreversible Action") },
                text = {
                    val warningText = if (restoreMergeMode) {
                        "This will merge the backup with your current local tags. Existing tags in the backup will OVERWRITE your local modifications for those specific tags."
                    } else {
                        "This will DELETE all your current local tags and replace them completely with the backup from Hydrus."
                    }
                    Text(warningText)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val currentBackup = latestBackup
                            if (currentBackup != null) {
                                wikiViewModel.executeRestore(
                                    fileId = currentBackup.fileId,
                                    merge = restoreMergeMode
                                )
                                showRestoreConfirmDialog = false
                                Toast.makeText(context, "Restore initiated", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No backup found", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F))
                    ) { Text("I understand, proceed") }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirmDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDetailDialog(
    entryDetail: TagWikiEntity,
    wikiViewModel: WikiViewModel,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    onDismiss: () -> Unit,
    onEditClick: (TagWikiEntity) -> Unit,
    onOpenExample: (List<com.asadtoast.aether.data.FileMetadata>, Int) -> Unit,
    onTagLinkClick: (String) -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    val previewFiles by wikiViewModel.exampleImages.collectAsState()

    val allEntries by wikiViewModel.allWikiEntries.collectAsState(initial = emptyList())
    val currentEntry = allEntries.find { it.tagName == entryDetail.tagName } ?: entryDetail

    LaunchedEffect(currentEntry.tagName) {
        wikiViewModel.fetchExampleImagesForTag(currentEntry.tagName)
    }

    androidx.activity.compose.BackHandler(onBack = onDismiss)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        currentEntry.tagName,
                        color = if (isMuted) Color.Gray else Color.Unspecified,
                        textDecoration = if (isMuted) TextDecoration.LineThrough else TextDecoration.None
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go back")
                    }
                },
                actions = {
                    // mute button
                    IconButton(onClick = onMuteToggle) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isMuted) "Unmute tag" else "Mute tag",
                            tint = if (isMuted) Color.Red else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { onEditClick(currentEntry) }) {
                        Icon(Icons.Default.Edit, "Edit tag")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // tag file examples
            item {
                Text(
                    "Examples",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (previewFiles.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        previewFiles.chunked(2).forEach { rowFiles ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowFiles.forEach { file ->
                                    val isVideo = file.mime.startsWith("video/") || file.mime == "3" || file.mime == "4"
                                    val isGif = file.mime == "image/gif" || file.mime == "2"

                                    Box(
                                        modifier = Modifier.weight(1f).aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                val actualIndex = previewFiles.indexOf(file)
                                                if (actualIndex != -1) {
                                                    onOpenExample(previewFiles, actualIndex)
                                                }
                                            }
                                    ) {
                                        SubcomposeAsyncImage(
                                            model = coil.request.ImageRequest.Builder(
                                                LocalContext.current
                                            )
                                                .data(file.thumbnailUrl)
                                                .build(),
                                            contentDescription = "Examples",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                            loading = {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.align(Alignment.Center)
                                                        .size(16.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            },
                                            error = { Icon(Icons.Default.Warning, "Error") }
                                        )

                                        if (file.duration != null && file.duration > 0.0) {
                                            Text(
                                                text = formatDuration(file.duration),
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .background(
                                                        color = Color.Black.copy(
                                                            alpha = 0.7f
                                                        ),
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        } else if (isVideo || isGif) {
                                            Box(
                                                modifier = Modifier.align(Alignment.TopEnd)
                                                    .padding(4.dp).size(24.dp).background(
                                                        Color.Black.copy(alpha = 0.6f),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    "Video/Gif",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                if (rowFiles.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "No example files available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            }

            // main hydrus reference tag
            item {
                Text(
                    "Hydrus reference tag",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    currentEntry.tagName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = getNamespaceColor(currentEntry.tagName)
                )
            }

            // description
            item {
                Text(
                    "Description",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (currentEntry.description.isNotEmpty()) {
                    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

                    val annotatedDescription = remember(currentEntry.description) {
                        androidx.compose.ui.text.buildAnnotatedString {
                            val regex = Regex("\\[\\[(.*?)]]")
                            var lastIndex = 0
                            regex.findAll(currentEntry.description).forEach { matchResult ->
                                append(currentEntry.description.substring(lastIndex, matchResult.range.first))

                                val tag = matchResult.groupValues[1]
                                pushStringAnnotation(tag = "WIKI_LINK", annotation = tag)
                                withStyle(
                                    style = androidx.compose.ui.text.SpanStyle(
                                        color = getNamespaceColor(tag)
                                    )
                                ) {
                                    append(tag.substringAfter(":"))
                                }
                                pop()

                                lastIndex = matchResult.range.last + 1
                            }
                            append(currentEntry.description.substring(lastIndex))
                        }
                    }

                    ClickableText(
                        text = annotatedDescription,
                        style = MaterialTheme.typography.bodyLarge.copy(color = onSurfaceColor),
                        onClick = { offset ->
                            annotatedDescription.getStringAnnotations(tag = "WIKI_LINK", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    onTagLinkClick(annotation.item)
                                }
                        }
                    )
                } else {
                    Text(
                        "No description available for this tag",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            }

            // aliases
            item {
                Text(
                    "Aliases",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (currentEntry.aliases.isNotEmpty()) {
                    currentEntry.aliases.forEach { alias ->
                        Text("• $alias", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    Text(
                        "No aliases available for this tag",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            }

            // references
            item {
                Text(
                    "External references",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (currentEntry.references.isNotEmpty()) {
                    currentEntry.references.forEach { ref ->
                        val displayText = if (ref.originalTag.isNotBlank()) {
                            "${ref.siteName} (${ref.originalTag})"
                        } else {
                            ref.siteName
                        }

                        // clickable text
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickable {
                                    if (ref.url.isNotBlank()) {
                                        try {
                                            uriHandler.openUri(ref.url)
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                        )
                    }
                } else {
                    Text(
                        "No references available for this tag",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}