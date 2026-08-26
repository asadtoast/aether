package com.asadtoast.aether.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.asadtoast.aether.data.SettingsManager
import com.asadtoast.aether.viewmodel.GroupDuplicateUiState
import com.asadtoast.aether.viewmodel.GroupDuplicateViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupDuplicateProcessingScreen(
    viewModel: GroupDuplicateViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val serverIp = settingsManager.serverIpFlow.collectAsState(initial = "").value
    val port = settingsManager.serverPortFlow.collectAsState(initial = "45869").value
    val apiKey = settingsManager.apiKeyFlow.collectAsState(initial = "").value

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            // restore on exit
            val windowToRestore = (context as? android.app.Activity)?.window
            if (windowToRestore != null) {
                androidx.core.view.WindowCompat.getInsetsController(windowToRestore, windowToRestore.decorView)
                    .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = uiState) {
            is GroupDuplicateUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is GroupDuplicateUiState.Empty -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No more duplicate groups left.",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateBack) {
                        Text("Go Back")
                    }
                }
            }
            is GroupDuplicateUiState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateBack) {
                        Text("Go Back")
                    }
                }
            }
            is GroupDuplicateUiState.Success -> {
                val group = state.currentGroupMetadata
                var isProcessing by remember { mutableStateOf(false) }
                var showDeleteAllDialog by remember { mutableStateOf(false) }
                var currentIndex by remember { mutableIntStateOf(0) }

                LaunchedEffect(group) {
                    currentIndex = 0
                }

                Text(
                    text = "${currentIndex + 1}",
                    fontSize = 200.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier.align(Alignment.Center)
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    group.forEachIndexed { index, file ->
                        if (kotlin.math.abs(index - currentIndex) <= 1) {
                            val hash = file.hash ?: ""
                            val imageUrl = "http://$serverIp:$port/get_files/file?hash=$hash&Hydrus-Client-API-Access-Key=$apiKey"

                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Duped image ${index + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = if (index == currentIndex) 1f else 0f
                                    }
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (currentIndex > 0) {
                                        currentIndex--
                                    }
                                }
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (currentIndex < group.size - 1) {
                                        currentIndex++
                                    }
                                }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .systemBarsPadding()
                        .padding(start = 16.dp, top = 16.dp)
                ) {
                    Text(
                        text = "${currentIndex + 1}",
                        color = Color.Red.copy(alpha = 0.8f),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 80.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .systemBarsPadding()
                        .padding(top = 25.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Files in group: ${group.size}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }

                val currentFile = group[currentIndex]
                FileMetadataOverlay(
                    size = currentFile.size ?: 0L,
                    width = currentFile.width?.toInt() ?: 0,
                    height = currentFile.height?.toInt() ?: 0,
                    tagCount = currentFile.tags?.size ?: 0,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .systemBarsPadding()
                        .padding(top = 25.dp, end = 25.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                isProcessing = true
                                viewModel.setKing(group[currentIndex].hash ?: "")
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("Set King", color = Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = {
                                isProcessing = true
                                viewModel.setAsAlternates()
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("Alternates", color = Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = {
                                isProcessing = true
                                viewModel.skipGroup()
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.DarkGray,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("Skip", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                isProcessing = true
                                viewModel.trashCurrentFile(group[currentIndex])
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("Delete current", color = Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = { showDeleteAllDialog = true },
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("Delete All", color = Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = {
                                isProcessing = true
                                viewModel.markAsFalsePositive()
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("False Pos", color = Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }

                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (showDeleteAllDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteAllDialog = false },
                        title = { Text("Delete All") },
                        text = { Text("Are you sure you want to delete ALL files in this group?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showDeleteAllDialog = false
                                    isProcessing = true
                                    viewModel.trashAllCurrentGroup()
                                }
                            ) {
                                Text("Yes, Delete All", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteAllDialog = false }) {
                                Text("Cancel", color = Color.White)
                            }
                        },
                        containerColor = Color.DarkGray,
                        titleContentColor = Color.White,
                        textContentColor = Color.White
                    )
                }
            }
        }
    }
}
