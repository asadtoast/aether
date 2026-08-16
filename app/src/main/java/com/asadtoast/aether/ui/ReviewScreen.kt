package com.asadtoast.aether.ui

import android.annotation.SuppressLint
import android.graphics.drawable.Animatable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.asadtoast.aether.viewmodel.HydrusViewModel

@Composable
fun ReviewScreen(
    viewModel: HydrusViewModel,
    pagedImages: androidx.paging.compose.LazyPagingItems<com.asadtoast.aether.data.FileMetadata>,
    onClose: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            viewModel.resetReviewIndex()
        }
    }

    // if done, end message (based on absolute end)
    if (pagedImages.itemCount > 0 && uiState.reviewIndex >= pagedImages.itemCount) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = onClose) { Text("Review completed. Close") }
        }
        return
    }

    // obtain current picture from list
    val currentImage = if (pagedImages.itemCount > 0 && uiState.reviewIndex < pagedImages.itemCount) {
        pagedImages[uiState.reviewIndex]
    } else null

    if (currentImage == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    // get highress file always
    val urlToLoad = currentImage.fileUrl

    // intercept android back button
    BackHandler { onClose() }

    //file loader (for massive files)
    val context = LocalContext.current
    val settingsManager = remember { com.asadtoast.aether.data.SettingsManager(context) }
    val apiKey by settingsManager.apiKeyFlow.collectAsState(initial = "")

    // Media states
    val isPlaying = remember { androidx.compose.runtime.mutableStateOf(true) }
    val isLooping = remember { androidx.compose.runtime.mutableStateOf(true) }
    val scale = remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val offset = remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }

    // Reset states on image change
    LaunchedEffect(uiState.reviewIndex) {
        scale.floatValue = 1f
        offset.value = Offset.Zero
        isPlaying.value = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // layer 1 (background) Image/video fill
        val mimeStr = currentImage.mime.lowercase()
        val mimeInt = mimeStr.toIntOrNull() ?: -1
        val isVideo = mimeInt in listOf(2, 3, 4, 8, 42) ||
                mimeStr.startsWith("video/") ||
                mimeStr.contains("webm") ||
                mimeStr.contains("matroska") ||
                mimeStr.contains("mkv")
        val isGif = mimeStr == "image/gif" || mimeInt == 1

        if (isVideo) {
            androidx.compose.runtime.key(currentImage.id) {
                val ext = when (mimeInt) {
                    42 -> ".mkv"
                    8 -> ".webm"
                    4 -> ".mp4"
                    else -> ".mp4"
                }

                val authenticatedUrl = if (urlToLoad.contains("?")) {
                    "$urlToLoad&Hydrus-Client-API-Access-Key=$apiKey&ext=$ext"
                } else {
                    "$urlToLoad?Hydrus-Client-API-Access-Key=$apiKey&ext=$ext"
                }

                FullScreenVideoPlayer(
                    url = authenticatedUrl,
                    apiKey = apiKey,
                    mime = currentImage.mime,
                    isActive = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(bottom = 110.dp),
                    muteButtonAtTop = true,
                    showSubtitlesButton = false
                )
            }
        } else {
            val animatable = remember { androidx.compose.runtime.mutableStateOf<Animatable?>(null) }

            LaunchedEffect(isPlaying.value, animatable.value) {
                animatable.value?.let {
                    if (isPlaying.value) it.start() else it.stop()
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // normal image
                SubcomposeAsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(urlToLoad)
                        .size(coil.size.Size.ORIGINAL)
                        .setParameter("coil#repeat_count", if (isLooping.value) -1 else 0)
                        .build(),
                    contentDescription = "Review",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale.floatValue = (scale.floatValue * zoom).coerceIn(1f, 5f)
                                offset.value = if (scale.floatValue > 1f) offset.value + pan else Offset.Zero
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    scale.floatValue = if (scale.floatValue > 1f) 1f else 2.5f
                                    offset.value = Offset.Zero
                                },
                                onTap = {
                                    if (isGif) {
                                        isPlaying.value = !isPlaying.value
                                    }
                                }
                            )
                        }
                        .graphicsLayer(
                            scaleX = scale.floatValue,
                            scaleY = scale.floatValue,
                            translationX = offset.value.x,
                            translationY = offset.value.y
                        ),
                    onSuccess = { state ->
                        val drawable = state.result.drawable
                        if (drawable is Animatable) {
                            animatable.value = drawable
                        }
                    },
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White.copy(alpha = 0.5f),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                )

                // gif play overlay
                if (isGif && !isPlaying.value) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }
            }
        }

        // layer 2, close button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        // layer 2.1, remaining files counter
        val remainingFiles = (uiState.totalFilesCount - uiState.reviewIndex).coerceAtLeast(0)
        if (uiState.totalFilesCount > 0) {
            Text(
                text = "Remaining: $remainingFiles",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // layer 2.5, file metadata info
        FileMetadataOverlay(
            size = currentImage.size ?: 0L,
            width = currentImage.width ?: 0,
            height = currentImage.height ?: 0,
            duration = currentImage.duration,
            framerate = currentImage.framerate,
            tagCount = currentImage.tagCount,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = if (isVideo || isGif) 56.dp else 8.dp, end = 8.dp)
        )

        // layer 2.6, GIF controls
        if (isGif) {
            IconButton(
                onClick = { isLooping.value = !isLooping.value },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isLooping.value) Icons.Default.Repeat else Icons.Default.RepeatOne,
                    contentDescription = "Loop",
                    tint = if (isLooping.value) Color.Cyan else Color.White
                )
            }
        }

        // layer 3, floating button panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
                .navigationBarsPadding()
                .padding(top = 48.dp, bottom = 32.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // undo/goback
            FloatingActionButton(onClick = {
                if (uiState.reviewIndex > 0) {
                    val previousImage = pagedImages[uiState.reviewIndex - 1]
                    if (previousImage != null) {
                        viewModel.undoReview(previousImage.id)
                    }
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }

            // skip
            FloatingActionButton(
                onClick = { viewModel.skipReview() },
                containerColor = Color.DarkGray,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Skip")
            }

            // archive
            FloatingActionButton(
                onClick = {
                    viewModel.skipReview()
                    viewModel.archiveMultipleFiles(setOf(currentImage.id), skipRefresh = true)
                },
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Check, contentDescription = "Archive")
            }

            // trash
            FloatingActionButton(
                onClick = {
                    viewModel.skipReview()
                    viewModel.trashMultipleFiles(setOf(currentImage.id), skipRefresh = true)
                },
                containerColor = Color(0xFFF44336),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Trash")
            }
        }
    }
}


@Composable
fun FileMetadataOverlay(
    size: Long,
    width: Int,
    height: Int,
    duration: Double? = null,
    framerate: Double? = null,
    tagCount: Int = 0,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val formattedSize = when {
        size >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", size / (1024f * 1024f * 1024f))
        size >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f MB", size / (1024f * 1024f))
        size >= 1024 -> String.format(java.util.Locale.US, "%.2f KB", size / 1024f)
        else -> "$size Bytes"
    }

    val resolutionText = if (width > 0 && height > 0) "${width}x${height}" else "Unknown Res"

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(text = formattedSize, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Text(text = resolutionText, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        Text(text = "$tagCount tags", color = Color(0xFFFFD54F), style = MaterialTheme.typography.bodySmall)

        if (duration != null && duration > 0.0) {
            val totalSeconds = (duration / 1000.0).toInt()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            Text(
                text = String.format(java.util.Locale.US, "%02d:%02d min", minutes, seconds),
                color = Color.Cyan,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (framerate != null && framerate > 0.0) {
            val formattedFps = if (framerate % 1.0 == 0.0) "${framerate.toInt()} FPS" else String.format(java.util.Locale.US, "%.2f FPS", framerate)
            Text(
                text = formattedFps,
                color = Color.Cyan,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}