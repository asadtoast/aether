package com.asadtoast.aether.ui

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.asadtoast.aether.ui.components.FileMetadataOverlay
import com.asadtoast.aether.utils.ExoPlayerPool
import com.asadtoast.aether.viewmodel.DuplicateViewModel
import com.asadtoast.aether.viewmodel.DuplicateAction

@Composable
fun DuplicateProcessingScreen(
    viewModel: DuplicateViewModel,
    onNavigateBack: () -> Unit
) {
    val duplicatePairs by viewModel.duplicatePairs.collectAsState()
    val totalPairsCount by viewModel.totalPairsCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentPair = duplicatePairs.firstOrNull()

    val context = LocalContext.current
    val settingsManager = remember { com.asadtoast.aether.data.SettingsManager(context) }
    val apiKey by settingsManager.apiKeyFlow.collectAsState(initial = "")
    val serverIp by settingsManager.serverIpFlow.collectAsState(initial = "")
    val port by settingsManager.serverPortFlow.collectAsState(initial = "")

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            // restore on exit
            val windowToRestore = (context as? Activity)?.window
            if (windowToRestore != null) {
                WindowCompat.getInsetsController(windowToRestore, windowToRestore.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // load queue if empty
    LaunchedEffect(Unit) {
        if (duplicatePairs.isEmpty()) {
            viewModel.fetchDuplicatePotentials()
        }
    }

    if (currentPair == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No duplicates available for processing", color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateBack) {
                        Text("Go back")
                    }
                }
            }
        }
        return
    }

    // status to decide which picture gets shown when holding
    var showImageB by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {

            // image viewer
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                showImageB = true
                                tryAwaitRelease()
                                showImageB = false
                            }
                        )
                    },
                contentAlignment = Alignment.TopStart
            ) {
                // image layers
                key(currentPair.hash_a) {
                    DuplicateMediaLayer(
                        hash = currentPair.hash_a,
                        mimeType = currentPair.meta_a.mime,
                        apiKey = apiKey,
                        serverIp = serverIp,
                        port = port,
                        isVisible = !showImageB
                    )
                }
                key(currentPair.hash_b) {
                    DuplicateMediaLayer(
                        hash = currentPair.hash_b,
                        mimeType = currentPair.meta_b.mime,
                        apiKey = apiKey,
                        serverIp = serverIp,
                        port = port,
                        isVisible = showImageB
                    )
                }

                // overlay
                val activeMeta = if (showImageB) currentPair.meta_b else currentPair.meta_a
                FileMetadataOverlay(
                    size = activeMeta.size ?: 0L,
                    width = activeMeta.width ?: 0,
                    height = activeMeta.height ?: 0,
                    duration = activeMeta.duration,
                    framerate = activeMeta.framerate,
                    tagCount = activeMeta.tagCount,
                    ext = activeMeta.mime.substringAfterLast('/').lowercase(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 25.dp, end = 25.dp)
                )

                // A/B visual indicator
                Text(
                    text = if (showImageB) "B" else "A",
                    color = Color.Red.copy(alpha = 0.8f),
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )

                // HUD for remaining counts
                Text(
                    text = "Remaining: $totalPairsCount",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 25.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // bottom control panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // row 1: main decisions
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.resolveDuplicate(currentPair, DuplicateAction.A_IS_BETTER) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        enabled = !isLoading
                    ) {
                        Text("A is better", textAlign = TextAlign.Center, maxLines = 1)
                    }
                    Button(
                        onClick = { viewModel.resolveDuplicate(currentPair, DuplicateAction.B_IS_BETTER) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        enabled = !isLoading
                    ) {
                        Text("B is better", textAlign = TextAlign.Center, maxLines = 1)
                    }
                    Button(
                        onClick = { viewModel.resolveDuplicate(currentPair, DuplicateAction.ALTERNATES) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        enabled = !isLoading
                    ) {
                        Text("Alternates", textAlign = TextAlign.Center, color = Color.White, maxLines = 1)
                    }
                }

                // row 2: secondary / destructive / navigation
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.resolveDuplicate(currentPair, DuplicateAction.DELETE_BOTH) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        enabled = !isLoading
                    ) {
                        Text("Delete Both", textAlign = TextAlign.Center, color = Color.White, maxLines = 1)
                    }
                    Button(
                        onClick = { viewModel.resolveDuplicate(currentPair, DuplicateAction.FALSE_POSITIVE) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        enabled = !isLoading
                    ) {
                        Text("False Pos.", textAlign = TextAlign.Center, color = Color.White, maxLines = 1)
                    }
                    Button(
                        onClick = { viewModel.undoLastAction() },
                        modifier = Modifier.weight(0.75f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        enabled = !isLoading
                    ) {
                        Text("Undo", textAlign = TextAlign.Center, color = Color.White, maxLines = 1)
                    }
                    Button(
                        onClick = { viewModel.resolveDuplicate(currentPair, DuplicateAction.SKIP) },
                        modifier = Modifier.weight(0.75f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        enabled = !isLoading
                    ) {
                        Text("Skip", textAlign = TextAlign.Center, color = Color.White, maxLines = 1)
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun DuplicateMediaLayer(
    hash: String,
    mimeType: String,
    apiKey: String,
    serverIp: String,
    port: String,
    isVisible: Boolean
) {
    val context = LocalContext.current
    val fileUrl = "http://$serverIp:$port/get_files/file?hash=$hash&Hydrus-Client-API-Access-Key=$apiKey"

    // video type detection
    val isVideo = mimeType.startsWith("video/", ignoreCase = true) ||
            mimeType.contains("mp4", ignoreCase = true) ||
            mimeType.contains("webm", ignoreCase = true)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (isVideo) {
            val exoPlayer = remember { ExoPlayerPool.getPlayer(context, apiKey) }
            var isBuffering by remember { mutableStateOf(true) }
            var playbackError by remember { mutableStateOf(false) }

            DisposableEffect(fileUrl) {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        playbackError = true
                        isBuffering = false
                    }
                }
                exoPlayer.addListener(listener)

                val mediaItem = MediaItem.fromUri(fileUrl)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
                exoPlayer.volume = 0f
                exoPlayer.prepare()
                exoPlayer.play()

                onDispose {
                    exoPlayer.removeListener(listener)
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    ExoPlayerPool.releasePlayer(exoPlayer)
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (playbackError) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.graphicsLayer { alpha = if (isVisible) 1f else 0f }
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                            contentDescription = "Playback Error",
                            tint = Color.Red,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Decoding Failed / Unsupported Codec", color = Color.White)
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            androidx.media3.ui.AspectRatioFrameLayout(ctx).apply {
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                val textureView = android.view.TextureView(ctx)
                                addView(textureView, android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                ))
                                exoPlayer.setVideoTextureView(textureView)

                                exoPlayer.addListener(object : Player.Listener {
                                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                                        if (videoSize.width == 0 || videoSize.height == 0) return
                                        setAspectRatio(videoSize.width.toFloat() / videoSize.height.toFloat() * videoSize.pixelWidthHeightRatio)
                                    }
                                })
                            }
                        },
                        update = { view ->
                            view.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.INVISIBLE
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isBuffering && isVisible) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        } else {
            coil.compose.SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(fileUrl)
                    .crossfade(true)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = "Duplicate Media",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (isVisible) 1f else 0f },
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            )
        }
    }
}