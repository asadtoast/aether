@file:Suppress("OPT_IN_ARGUMENT_IS_NOT_MARKER")

package com.asadtoast.aether.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Warning
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
import androidx.media3.common.util.UnstableApi
import com.asadtoast.aether.data.SettingsManager
import com.asadtoast.aether.ui.components.FileMetadataOverlay
import com.asadtoast.aether.viewmodel.GroupDuplicateUiState
import com.asadtoast.aether.viewmodel.GroupDuplicateViewModel

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class, UnstableApi::class)
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
                            key(hash) {
                                val fileUrl = "http://$serverIp:$port/get_files/file?hash=$hash&Hydrus-Client-API-Access-Key=$apiKey"
                                val isVideo = file.mime.startsWith("video/")

                                Box(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (isVideo) {
                                        var isVideoLoading by remember { mutableStateOf(true) }
                                        var playerError by remember { mutableStateOf(false) }
                                        val exoPlayer = remember {
                                            androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                                                repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                                                volume = 0f
                                                addListener(object : androidx.media3.common.Player.Listener {
                                                    override fun onPlaybackStateChanged(playbackState: Int) {
                                                        if (playbackState == androidx.media3.common.Player.STATE_READY) {
                                                            isVideoLoading = false
                                                        }
                                                    }
                                                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                                        playerError = true
                                                        isVideoLoading = false
                                                    }
                                                })
                                            }
                                        }

                                        LaunchedEffect(fileUrl) {
                                            isVideoLoading = true
                                            playerError = false
                                            exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(fileUrl))
                                            exoPlayer.prepare()
                                        }

                                        LaunchedEffect(index, currentIndex) {
                                            if (index == currentIndex && !playerError) {
                                                exoPlayer.play()
                                            } else {
                                                exoPlayer.pause()
                                            }
                                        }

                                        DisposableEffect(Unit) {
                                            onDispose {
                                                exoPlayer.release()
                                            }
                                        }

                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (!playerError) {
                                                androidx.compose.ui.viewinterop.AndroidView(
                                                    factory = { ctx ->
                                                        androidx.media3.ui.PlayerView(ctx).apply {
                                                            player = exoPlayer
                                                            useController = false
                                                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                            isClickable = false
                                                            isFocusable = false
                                                        }
                                                    },
                                                    update = { view ->
                                                        view.visibility = if (index == currentIndex) android.view.View.VISIBLE else android.view.View.INVISIBLE
                                                    },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer { alpha = if (index == currentIndex) 1f else 0f }
                                                ) {
                                                    coil.compose.SubcomposeAsyncImage(
                                                        model = fileUrl,
                                                        contentDescription = "Unsupported video fallback",
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier.fillMaxSize(),
                                                        loading = {
                                                            Box(
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                                            }
                                                        }
                                                    )
                                                    Icon(
                                                        imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                                                        contentDescription = "Unsupported Codec",
                                                        tint = Color.Red.copy(alpha = 0.7f),
                                                        modifier = Modifier
                                                            .align(Alignment.Center)
                                                            .size(64.dp)
                                                    )
                                                }
                                            }
                                            if (isVideoLoading && index == currentIndex) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.align(Alignment.Center),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    } else {
                                        coil.compose.SubcomposeAsyncImage(
                                            model = fileUrl,
                                            contentDescription = "Duped image ${index + 1}",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer { alpha = if (index == currentIndex) 1f else 0f },
                                            loading = {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
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
                val calculatedFramerate = if (currentFile.num_frames != null && currentFile.duration != null && currentFile.duration > 0) {
                    currentFile.num_frames.toDouble() / (currentFile.duration / 1000.0)
                } else {
                    null
                }

                val actualTagCount = currentFile.tags?.values
                    ?.flatMap { it.display_tags?.get("0") ?: emptyList() }
                    ?.distinct()
                    ?.size ?: 0

                FileMetadataOverlay(
                    size = currentFile.size ?: 0L,
                    width = currentFile.width?.toInt() ?: 0,
                    height = currentFile.height?.toInt() ?: 0,
                    ext = currentFile.mime.substringAfterLast("/"),
                    tagCount = actualTagCount,
                    duration = currentFile.duration,
                    framerate = calculatedFramerate,
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
