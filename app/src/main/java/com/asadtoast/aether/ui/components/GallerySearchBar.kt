package com.asadtoast.aether.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.asadtoast.aether.ui.theme.getNamespaceColor
import com.asadtoast.aether.viewmodel.GalleryUiState
import com.asadtoast.aether.viewmodel.AetherViewModel
import com.asadtoast.aether.data.SettingsManager


@Composable
fun GallerySearchBar(
    viewModel: AetherViewModel,
    uiState: GalleryUiState,
    focusManager: FocusManager
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val isPrivacyKeyboardEnabled by settingsManager.privacyKeyboardFlow.collectAsState(initial = true)

    val coroutineScope = rememberCoroutineScope()
    var tagSuggestions by remember { mutableStateOf(emptyList<com.asadtoast.aether.data.TagSuggestion>()) }
    val keyboardController = LocalSoftwareKeyboardController.current

    var searchTextFieldValue by remember {
        mutableStateOf(TextFieldValue(text = uiState.searchQuery))
    }

    LaunchedEffect(uiState.searchQuery) {
        if (uiState.searchQuery != searchTextFieldValue.text) {
            searchTextFieldValue = TextFieldValue(
                text = uiState.searchQuery,
                selection = TextRange(uiState.searchQuery.length)
            )
        }
    }

    // state to manage the visibility of tag suggestions
    var showSuggestions by remember { mutableStateOf(searchTextFieldValue.text.isNotEmpty()) }

    LaunchedEffect(searchTextFieldValue.text) {
        val trimmedText = searchTextFieldValue.text.trim()
        if (trimmedText.isNotEmpty() && !trimmedText.trimEnd().endsWith(",")) {
            tagSuggestions = viewModel.fetchTagSuggestions(searchTextFieldValue.text)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        key(isPrivacyKeyboardEnabled) {
            OutlinedTextField(
                value = searchTextFieldValue,
                onValueChange = { newTextFieldValue ->
                    searchTextFieldValue = newTextFieldValue

                    // fetch tag suggestions if text contains a non-comma character or is not ending with ","
                    val trimmedText = newTextFieldValue.text.trim()
                    if (trimmedText.isNotEmpty() && !trimmedText.trimEnd().endsWith(",")) {
                        showSuggestions = true
                    } else {
                        showSuggestions = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = { Text("Tags") },
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Unspecified,
                    autoCorrectEnabled = !isPrivacyKeyboardEnabled,
                    keyboardType = if (isPrivacyKeyboardEnabled) KeyboardType.Password else KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        showSuggestions = false
                        tagSuggestions = emptyList()
                        viewModel.fetchGallery(searchTextFieldValue.text)
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                ),
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (searchTextFieldValue.text.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchTextFieldValue = TextFieldValue(text = "")
                                    tagSuggestions = emptyList()
                                    viewModel.fetchGallery("")
                                }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                        IconButton(
                            onClick = {
                                showSuggestions = false
                                tagSuggestions = emptyList()
                                viewModel.fetchGallery(searchTextFieldValue.text)
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                }
            )
        }

        AnimatedVisibility(visible = showSuggestions && tagSuggestions.isNotEmpty() && searchTextFieldValue.text.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                    items(tagSuggestions) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val currentText = searchTextFieldValue.text
                                    val prefix = if (currentText.contains(",")) currentText.substringBeforeLast(",") + ", " else ""

                                    val rawValue = suggestion.value
                                    val cleanTag = if (rawValue.contains("➔")) rawValue.substringAfter("➔").trim() else rawValue

                                    val newText = "$prefix$cleanTag, "
                                    searchTextFieldValue = TextFieldValue(text = newText, selection = TextRange(newText.length))
                                    tagSuggestions = emptyList()
                                    showSuggestions = false
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = suggestion.value,
                                style = MaterialTheme.typography.bodyLarge,
                                color = getNamespaceColor(suggestion.value),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = suggestion.count.toInt().toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }

        // hide suggestions when focus is lost or the user starts typing a new tag after a comma followed by space
        DisposableEffect(Unit) {
            onDispose {
                showSuggestions = false
            }
        }
    }
}