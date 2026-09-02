@file:Suppress("DEPRECATION")
package com.asadtoast.aether.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import coil.request.repeatCount
import com.asadtoast.aether.viewmodel.AetherViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.asadtoast.aether.ui.theme.getNamespaceColor
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri

@SuppressLint("DefaultLocale", "UseOfNonLambdaOffsetOverload")
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullScreenViewer(
    viewModel: AetherViewModel,
    initialIndex: Int,
    onClose: () -> Unit,
    onAddToSearch: (String) -> Unit,
    onNavigateToTagWiki: (String) -> Unit,
    onAlternateClick: (String) -> Unit = {},
    customImageList: List<com.asadtoast.aether.data.FileMetadata>? = null,
    pagedImages: androidx.paging.compose.LazyPagingItems<com.asadtoast.aether.data.FileMetadata>? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentAlternates by viewModel.currentAlternates.collectAsState()
    val context = LocalContext.current
    val settingsManager = remember { com.asadtoast.aether.data.SettingsManager(context) }
    val apiKey by settingsManager.apiKeyFlow.collectAsState(initial = "")

    // edit mode for tag panel
    val focusManager = LocalFocusManager.current
    var isEditMode by remember { mutableStateOf(false) }
    var tagsPendingDelete by remember { mutableStateOf(setOf<String>()) }

    // booru scraper state
    val booruScrapeState by viewModel.booruScrapeState.collectAsState()
    var showBooruDialog by remember { mutableStateOf(false) }

    // pdf/psd/zip... filtered view values
    val itemCount = customImageList?.size ?: pagedImages?.itemCount ?: 0
    val isRefreshing = pagedImages?.loadState?.refresh is androidx.paging.LoadState.Loading

    // autoclose if no more pictures on action erase
    if (itemCount == 0) {
        if (!isRefreshing) {
            LaunchedEffect(Unit) { onClose() }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0)),
        pageCount = { itemCount }
    )

    val getFileAtIndex: (Int) -> com.asadtoast.aether.data.FileMetadata? = { index ->
        if (customImageList != null) {
            customImageList.getOrNull(index)
        } else if (pagedImages != null) {
            if (index in 0 until pagedImages.itemCount) pagedImages[index] else null
        } else null
    }

    val currentFile = getFileAtIndex(pagerState.currentPage)
    val settledFileId = getFileAtIndex(pagerState.settledPage)?.id

    // ui visibility toggle for fullscreen video
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    var isUiVisible by remember { mutableStateOf(true) }


    // hide top bar
    LaunchedEffect(isUiVisible) {
        window?.let { win ->
            val insetsController = WindowInsetsControllerCompat(win, view)
            if (isUiVisible) {
                // show status bar and navigation
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            } else {
                // hide bars
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                // small slide from border to show bar briefly
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    // info panel
    var isPagerScrollEnabled by remember { mutableStateOf(true) }
    val existingTagsText by viewModel.currentImageTags.collectAsState()
    val clipboardTags by viewModel.clipboardTags.collectAsState()

    // slideshow states
    var isSlideshowActive by remember { mutableStateOf(false) }
    var showSlideshowDialog by remember { mutableStateOf(false) }
    var slideshowInterval by remember { mutableStateOf("5") }
    var waitForVideo by remember { mutableStateOf(true) }
    var currentVideoEnded by remember { mutableStateOf(false) }
    var loopSlideshow by remember { mutableStateOf(false) }

    // slideshow engine
    LaunchedEffect(isSlideshowActive) {
        if (!isSlideshowActive) return@LaunchedEffect

        // loop to prevent premature engine death
        while (isSlideshowActive) {
            currentVideoEnded = false
            if (pagerState.pageCount == 0) { delay(100.milliseconds); continue }

            val fileForSlide = getFileAtIndex(pagerState.currentPage)
            if (fileForSlide == null) {
                // wait for Paging3 to load the item
                delay(500.milliseconds)
                continue
            }

            val mimeStr = fileForSlide.mime.lowercase()
            val mimeInt = mimeStr.toIntOrNull() ?: -1
            val isVideo = mimeInt in listOf(2, 3, 4, 8, 42) ||
                    mimeStr.startsWith("video/") ||
                    mimeStr.contains("webm") ||
                    mimeStr.contains("matroska") ||
                    mimeStr.contains("mkv")
            val intervalMs = (slideshowInterval.toLongOrNull() ?: 5L).coerceAtLeast(1L) * 1000L

            if (isVideo && waitForVideo) {
                delay(1000.milliseconds)
                // wait until video finishes
                snapshotFlow { currentVideoEnded }.first { it }
                delay(500.milliseconds)
            } else {
                // if file is photo, just wait
                delay(intervalMs.milliseconds)
            }

            if (pagerState.currentPage < pagerState.pageCount - 1) {
                pagerState.scrollToPage(pagerState.currentPage + 1)
            } else {
                if (loopSlideshow) pagerState.scrollToPage(0) else isSlideshowActive = false
            }
        }
    }

    BackHandler { onClose() }

    LaunchedEffect(settledFileId) {
        if (settledFileId != null) {
            viewModel.fetchExistingTagsForImage(settledFileId)
            val settledFile = getFileAtIndex(pagerState.settledPage)
            if (settledFile?.hash != null) {
                viewModel.loadAlternates(settledFile.hash)
            }
            isEditMode = false
            tagsPendingDelete = emptySet()
        }
    }

    var tagSuggestions by remember { mutableStateOf<List<com.asadtoast.aether.data.TagSuggestion>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    // fullscreen container with new tag + info view
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // layer one, background image/video pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = isPagerScrollEnabled,
            beyondViewportPageCount = 1
        ) { page ->

            val isCurrentPage = page == pagerState.settledPage

            // per page scroll
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            var videoCodec by remember(page) { mutableStateOf<String?>(null) }
            var videoResolution by remember(page) { mutableStateOf<String?>(null) }

            // reset scroll when moving to next file
            LaunchedEffect(pagerState.settledPage) {
                if (pagerState.settledPage != page) {
                    listState.scrollToItem(0)
                }
            }

            val pageFile = getFileAtIndex(page)
            if (pageFile != null) {
                val mimeStr = pageFile.mime.lowercase()
                val mimeInt = mimeStr.toIntOrNull() ?: -1
                val isVideo = mimeInt in listOf(2, 3, 4, 8, 42) ||
                        mimeStr.startsWith("video/") ||
                        mimeStr.contains("webm") ||
                        mimeStr.contains("matroska") ||
                        mimeStr.contains("mkv")

                // block thumbnails for videoplayer and force absolute file url
                val url = if (isVideo) {
                    pageFile.fileUrl
                } else {
                    pageFile.fileUrl
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val screenHeight = maxHeight

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState
                    ) {
                        // media
                        item {
                            Box(
                                modifier = Modifier
                                    .height(screenHeight)
                                    .fillMaxWidth()
                            ) {
                                if (isVideo) {
                                    FullScreenVideoPlayer(
                                        url = url,
                                        mime = pageFile.mime,
                                        isActive = isCurrentPage,
                                        apiKey = apiKey,
                                        autoLoop = uiState.gifAutoLoop,
                                        isUiVisible = isUiVisible,
                                        onControllerVisibilityChanged = { isVisible ->
                                            isUiVisible = isVisible
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                        onVideoEnded = { currentVideoEnded = true },
                                        onCodecInfoExtracted = { videoCodec = it },
                                        onResolutionExtracted = { videoResolution = it }
                                    )
                                } else {
                                    ZoomableImageItem(
                                        url = url,
                                        mime = pageFile.mime,
                                        gifAutoLoop = uiState.gifAutoLoop,
                                        onZoomChanged = { isZoomed ->
                                            if (page == pagerState.currentPage) {
                                                isPagerScrollEnabled = !isZoomed
                                            }
                                        },
                                        onImageTap = { isUiVisible = !isUiVisible }
                                    )
                                }

                                if (isCurrentPage) {
                                    // visual cue for slide up
                                    val infiniteTransition = rememberInfiniteTransition(label = "scroll_bounce")
                                    val offsetY by infiniteTransition.animateFloat(
                                        initialValue = 0f,
                                        targetValue = 12f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "bounce_offset"
                                    )

                                    // up arrow
                                    AnimatedVisibility(
                                        visible = isUiVisible,
                                        enter = fadeIn(),
                                        exit = fadeOut(),
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Slide up",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .padding(bottom = 40.dp)
                                                .offset(y = offsetY.dp)
                                                .size(40.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (isCurrentPage) {
                            // tag/info panel
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF121212))
                                        .padding(16.dp)
                                        .navigationBarsPadding()
                                ) {
                                    // alternates carousel
                                    if (currentAlternates.isNotEmpty()) {
                                        Text("Alternates (${currentAlternates.size})", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        androidx.compose.foundation.lazy.LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(currentAlternates) { altHash ->
                                                val isSelected = altHash == pageFile.hash
                                                Box(
                                                    modifier = Modifier
                                                        .size(96.dp)
                                                        .clickable {
                                                            onAlternateClick(altHash)
                                                        }
                                                        .border(
                                                            width = if (isSelected) 2.dp else 0.dp,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .clip(RoundedCornerShape(8.dp))
                                                ) {
                                                    val thumbUrl = pageFile.thumbnailUrl
                                                        .replace(Regex("file_id=\\d+"), "hash=$altHash")
                                                    coil.compose.AsyncImage(
                                                        model = thumbUrl,
                                                        contentDescription = "Alternate",
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp), color = Color.DarkGray)
                                    }

                                    Text("Tags", color = Color.White, style = MaterialTheme.typography.titleMedium)

                                    // tag searchbar with edit button
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            InlineTagInputField(
                                                suggestions = tagSuggestions,
                                                onQueryChanged = { query ->
                                                    coroutineScope.launch {
                                                        tagSuggestions = viewModel.fetchTagSuggestions(query)
                                                    }
                                                },
                                                onTagSubmitted = { newTag ->
                                                    if (newTag.isNotBlank()) {
                                                        viewModel.addTagsToImage(pageFile.id, newTag)
                                                        tagSuggestions = emptyList()
                                                        viewModel.fetchExistingTagsForImage(pageFile.id)
                                                    }
                                                }
                                            )
                                        }

                                        if (!isEditMode) {
                                            IconButton(
                                                onClick = {
                                                    val tagsList = existingTagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                                    viewModel.copyTagsToClipboard(tagsList)
                                                },
                                                modifier = Modifier.padding(start = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy Tags",
                                                    tint = Color.White
                                                )
                                            }
                                        }

                                        if (isEditMode && clipboardTags.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.pasteTagsFromClipboard(pageFile.id)
                                                },
                                                modifier = Modifier.padding(start = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentPaste,
                                                    contentDescription = "Paste Tags",
                                                    tint = Color.White
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                focusManager.clearFocus()
                                                if (isEditMode) {
                                                    // apply pending deletions on confirmation
                                                    tagsPendingDelete.forEach { tag ->
                                                        viewModel.removeTagsFromImage(pageFile.id, tag)
                                                    }
                                                    if (tagsPendingDelete.isNotEmpty()) {
                                                        viewModel.fetchExistingTagsForImage(pageFile.id)
                                                    }
                                                    tagsPendingDelete = emptySet()
                                                }
                                                isEditMode = !isEditMode
                                            },
                                            modifier = Modifier.padding(start = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                                contentDescription = "Toggle Edit Mode",
                                                tint = if (isEditMode) Color.Green else Color.White
                                            )
                                        }
                                    }

                                    // tag list
                                    AdvancedTagsSection(
                                        tagsText = if (page == pagerState.settledPage) existingTagsText else "",
                                        viewModel = viewModel,
                                        isEditMode = isEditMode,
                                        tagsPendingDelete = tagsPendingDelete,
                                        onAddToSearch = onAddToSearch,
                                        onNavigateToTagWiki = onNavigateToTagWiki,
                                        onTogglePendingDelete = { tag ->
                                            tagsPendingDelete = if (tagsPendingDelete.contains(tag)) {
                                                tagsPendingDelete - tag
                                            } else {
                                                tagsPendingDelete + tag
                                            }
                                        }
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp), color = Color.DarkGray)

                                    // file info
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Information", color = Color.White, style = MaterialTheme.typography.titleMedium)

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            SuggestionChip(
                                                onClick = {
                                                    showBooruDialog = true
                                                },
                                                label = { Text("Scrape Tags", fontWeight = FontWeight.Bold, color = Color(0xFFE0E0E0)) },
                                                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color.DarkGray.copy(alpha = 0.5f)),
                                                border = BorderStroke(1.dp, Color.Gray)
                                            )

                                            Box {
                                                var statusMenuExpanded by remember { mutableStateOf(false) }
                                                var localIsTrashed by remember(pageFile.id) { mutableStateOf(pageFile.is_trashed == true) }
                                                var localIsInbox by remember(pageFile.id) { mutableStateOf(pageFile.is_inbox == true) }

                                                if (localIsTrashed) {
                                                    SuggestionChip(
                                                        onClick = { statusMenuExpanded = true },
                                                        label = { Text("Trash", fontWeight = FontWeight.Bold, color = Color.Red) },
                                                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color.Red.copy(alpha = 0.15f)),
                                                        border = BorderStroke(1.dp, Color.Red)
                                                    )
                                                } else if (localIsInbox) {
                                                    SuggestionChip(
                                                        onClick = { statusMenuExpanded = true },
                                                        label = { Text("Inbox", fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6)) },
                                                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF64B5F6).copy(alpha = 0.15f)),
                                                        border = BorderStroke(1.dp, Color(0xFF64B5F6))
                                                    )
                                                } else {
                                                    SuggestionChip(
                                                        onClick = { statusMenuExpanded = true },
                                                        label = { Text("Archived", fontWeight = FontWeight.Bold, color = Color.Green) },
                                                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color.Green.copy(alpha = 0.15f)),
                                                        border = BorderStroke(1.dp, Color.Green)
                                                    )
                                                }

                                                DropdownMenu(
                                                    expanded = statusMenuExpanded,
                                                    onDismissRequest = { statusMenuExpanded = false },
                                                    modifier = Modifier.background(Color(0xFF1E1E1E))
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("Inbox", color = Color(0xFF64B5F6)) },
                                                        onClick = {
                                                            statusMenuExpanded = false
                                                            localIsInbox = true
                                                            localIsTrashed = false
                                                            viewModel.inboxFile(pageFile.id, onClose)
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Archive", color = Color.Green) },
                                                        onClick = {
                                                            statusMenuExpanded = false
                                                            localIsInbox = false
                                                            localIsTrashed = false
                                                            viewModel.archiveFile(pageFile.id, onClose)
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Trash", color = Color.Red) },
                                                        onClick = {
                                                            statusMenuExpanded = false
                                                            localIsTrashed = true
                                                            localIsInbox = false
                                                            viewModel.trashSpecificFile(pageFile.id, onClose)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // file id
                                    InfoRow(label = "File ID:", value = pageFile.id.toString())
                                    // file format
                                    InfoRow(label = "Format (MIME):", value = pageFile.mime)
                                    // file size
                                    pageFile.size?.let { bytes ->
                                        val formattedSize = when {
                                            bytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024f * 1024f * 1024f))
                                            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024f * 1024f))
                                            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024f)
                                            else -> "$bytes Bytes"
                                        }
                                        InfoRow(label = "Size:", value = formattedSize)
                                    }

                                    // imported date
                                    pageFile.importedDate?.let { timestamp ->
                                        val formattedDate = remember(timestamp) {
                                            try {
                                                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                                                sdf.format(java.util.Date(timestamp * 1000))
                                            } catch (_: Exception) {
                                                "Unknown"
                                            }
                                        }
                                        InfoRow(label = "Imported:", value = formattedDate)
                                    }

                                    // filetype based data
                                    val mimeStrInfo = pageFile.mime.lowercase()
                                    val mimeIntInfo = mimeStrInfo.toIntOrNull() ?: -1
                                    val isVideoOrWebm = mimeIntInfo in listOf(2, 3, 4, 8, 42) ||
                                            mimeStrInfo.startsWith("video/") ||
                                            mimeStrInfo.contains("webm") ||
                                            mimeStrInfo.contains("matroska") ||
                                            mimeStrInfo.contains("mkv")
                                    val isGif = mimeStrInfo == "image/gif" || mimeIntInfo == 1

                                    if (isVideoOrWebm || isGif) {
                                        InfoRow(label = "Filetype:", value = if (isVideoOrWebm) "Video" else "Animated")

                                        // file duration
                                        pageFile.duration?.let { rawDuration ->
                                            if (rawDuration > 0.0) {
                                                val totalSeconds = (rawDuration / 1000.0).toInt()
                                                val minutes = totalSeconds / 60
                                                val seconds = totalSeconds % 60
                                                InfoRow(label = "Duration:", value = String.format("%02d:%02d min", minutes, seconds))
                                            }
                                        }


                                        // framerate
                                        pageFile.framerate?.let { fps ->
                                            if (fps > 0.0) {
                                                val formattedFps = if (fps % 1.0 == 0.0) "${fps.toInt()} FPS" else String.format(java.util.Locale.US, "%.2f FPS", fps)
                                                InfoRow(label = "Framerate:", value = formattedFps)
                                            }
                                        }

                                        // codec
                                        videoCodec?.let { codec ->
                                            InfoRow(label = "Codec:", value = codec)
                                        }
                                    } else {
                                        InfoRow(label = "Filetype:", value = "Image")
                                    }

                                    // resolution (universal for all files)
                                    if (pageFile.width != null && pageFile.height != null && pageFile.width > 0 && pageFile.height > 0) {
                                        InfoRow(label = "Resolution:", value = "${pageFile.width}x${pageFile.height}")
                                    } else {
                                        // fallback to ExoPlayer resolution if metadata is missing
                                        videoResolution?.let { res ->
                                            InfoRow(label = "Resolution:", value = res)
                                        }
                                    }
                                    // alphanumeric hash truncated
                                    pageFile.hash?.let { fileHash ->
                                        val shortenedHash = if (fileHash.length > 12) {
                                            "${fileHash.take(8)}...${fileHash.takeLast(4)}"
                                        } else {
                                            fileHash
                                        }
                                        InfoRow(label = "SHA256 Hash:", value = shortenedHash)
                                    }

                                    Spacer(modifier = Modifier.height(32.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                // placeholder while Paging3 loads the item
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        // floating top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.4f)) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "return", tint = Color.White)
                    }
                }
            }

            if (currentFile != null) {
                AnimatedVisibility(
                    visible = isUiVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Surface(shape = RoundedCornerShape(24.dp), color = Color.Black.copy(alpha = 0.4f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // autoloop button
                            val mimeStr = currentFile.mime.lowercase()
                            val mimeInt = mimeStr.toIntOrNull() ?: -1
                            val isVideoOrGif = mimeStr == "image/gif" || mimeInt == 1 ||
                                    mimeInt in listOf(2, 3, 4, 8, 42) ||
                                    mimeStr.startsWith("video/") ||
                                    mimeStr.contains("webm") ||
                                    mimeStr.contains("matroska") ||
                                    mimeStr.contains("mkv")

                            if (isVideoOrGif) {
                                IconButton(
                                    onClick = { viewModel.saveGifAutoLoop(!uiState.gifAutoLoop) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Repeat,
                                        contentDescription = "toggle loop",
                                        tint = if (uiState.gifAutoLoop) Color.White else Color.Gray
                                    )
                                }
                            }

                            // slideshow button
                            IconButton(onClick = {
                                if (isSlideshowActive) isSlideshowActive = false
                                else showSlideshowDialog = true
                            }) {
                                Icon(if (isSlideshowActive) Icons.Default.Stop else Icons.Default.PlayArrow, "slideshow", tint = if (isSlideshowActive) Color.Red else Color.White)
                            }

                            // trash button
                            val inTrashMode = uiState.isTrashMode

                            if (inTrashMode) {
                                IconButton(onClick = {
                                    viewModel.restoreImage(
                                        fileId = currentFile.id,
                                        onEmptyList = {
                                            onClose()
                                        }
                                    )
                                }) {
                                    Icon(Icons.Default.RestoreFromTrash, "Restore to Inbox", tint = Color.White)
                                }
                            } else {
                                IconButton(onClick = {
                                    viewModel.trashSpecificFile(currentFile.id) {
                                        onClose()
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, "Send to trash", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // slideshow dialog
        if (showSlideshowDialog) {
            AlertDialog(
                onDismissRequest = { showSlideshowDialog = false },
                title = { Text("Slideshow Config") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = slideshowInterval,
                            onValueChange = { slideshowInterval = it.filter { char -> char.isDigit() } },
                            label = { Text("Seconds per file") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = waitForVideo, onCheckedChange = { waitForVideo = it })
                            Text("Wait for video to finish")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = loopSlideshow, onCheckedChange = { loopSlideshow = it })
                            Text("Loop slideshow")
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showSlideshowDialog = false
                        isSlideshowActive = true
                    }) { Text("Start") }
                },
                dismissButton = {
                    TextButton(onClick = { showSlideshowDialog = false }) { Text("Cancel") }
                }
            )
        }

        // booru scrape dialog
        if (showBooruDialog && currentFile != null) {
            var selectedSource by remember { mutableStateOf<com.asadtoast.aether.data.ScraperSource?>(null) }

            AlertDialog(
                onDismissRequest = {
                    showBooruDialog = false
                    selectedSource = null
                    viewModel.resetBooruState()
                },
                containerColor = Color(0xFF1A1A1A),
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = { Text("Booru Scraper") },
                text = {
                    when (val state = booruScrapeState) {
                        is com.asadtoast.aether.viewmodel.BooruScrapeState.Idle -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Select source to scrape:", color = Color.White, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        selectedSource = com.asadtoast.aether.data.ScraperSource.DANBOORU
                                        viewModel.scrapeBooruTags(currentFile, com.asadtoast.aether.data.ScraperSource.DANBOORU)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0073CC))
                                ) { Text("Danbooru", color = Color.White) }

                                // removed until fixed by pawchive
                                /*
                                Button(
                                    onClick = {
                                        selectedSource = com.asadtoast.aether.data.ScraperSource.PAWCHIVE
                                        viewModel.scrapeBooruTags(currentFile, com.asadtoast.aether.data.ScraperSource.PAWCHIVE)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
                                ) { Text("Pawchive", color = Color.White) }
                                */
                            }
                        }
                        is com.asadtoast.aether.viewmodel.BooruScrapeState.Loading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                val loadingText = if (selectedSource == com.asadtoast.aether.data.ScraperSource.PAWCHIVE) {
                                    "Searching via SHA256..."
                                } else {
                                    "Searching via MD5..."
                                }
                                Text(loadingText)
                            }
                        }
                        is com.asadtoast.aether.viewmodel.BooruScrapeState.Error -> {
                            Text("Error: ${state.message}", color = Color.Red)
                        }
                        is com.asadtoast.aether.viewmodel.BooruScrapeState.Success -> {
                            if (state.tags.isEmpty()) {
                                Text("No tags found for this image.")
                            } else {
                                var selectedTags by remember { mutableStateOf(emptySet<com.asadtoast.aether.data.ParsedTag>()) }
                                val allSelected = selectedTags.size == state.tags.size

                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("Found ${state.tags.size} tags. Select to apply:", fontWeight = FontWeight.Bold)

                                    // select/unselect all toggle
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .clickable {
                                                selectedTags = if (allSelected) emptySet() else state.tags.toSet()
                                            }
                                    ) {
                                        Checkbox(
                                            checked = allSelected,
                                            onCheckedChange = { checked ->
                                                selectedTags = if (checked) state.tags.toSet() else emptySet()
                                            }
                                        )
                                        Text(
                                            text = if (allSelected) "Unselect All" else "Select All",
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 300.dp)) {
                                        items(state.tags) { tag ->
                                            val isSelected = selectedTags.contains(tag)
                                            val tagString = if (tag.namespace.isNotEmpty() && tag.namespace != "general") "${tag.namespace}:${tag.name}" else tag.name

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(40.dp)
                                                    .clickable {
                                                        selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag
                                                    }
                                            ) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { checked ->
                                                        selectedTags = if (checked) selectedTags + tag else selectedTags - tag
                                                    }
                                                )
                                                Text(
                                                    text = tagString,
                                                    color = getNamespaceColor(tagString),
                                                    modifier = Modifier.padding(start = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            selectedTags.forEach { tag ->
                                                val tagString = if (tag.namespace.isNotEmpty() && tag.namespace != "general") "${tag.namespace}:${tag.name}" else tag.name
                                                viewModel.addTagsToImage(currentFile.id, tagString)
                                            }
                                            viewModel.fetchExistingTagsForImage(currentFile.id)
                                            showBooruDialog = false
                                            selectedSource = null
                                            viewModel.resetBooruState()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                    ) {
                                        Text("Apply Selected Tags", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (booruScrapeState !is com.asadtoast.aether.viewmodel.BooruScrapeState.Success || (booruScrapeState as? com.asadtoast.aether.viewmodel.BooruScrapeState.Success)?.tags?.isEmpty() == true) {
                        TextButton(onClick = {
                            showBooruDialog = false
                            selectedSource = null
                            viewModel.resetBooruState()
                        }) { Text("Close", color = Color.White) }
                    }
                }
            )
        }
    }
}


// zoomable box for gallery
@Composable
fun ZoomableImageItem(
    url: String,
    mime: String,
    gifAutoLoop: Boolean,
    onZoomChanged: (Boolean) -> Unit = {},
    onImageTap: () -> Unit = {}
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isPlaying by remember { mutableStateOf(true) }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1f)
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        // only pan if zoomed
        if (scale > 1f) {
            offset += panChange
        } else {
            offset = Offset.Zero
        }
    }

    val mimeStr = mime.lowercase()
    val mimeInt = mimeStr.toIntOrNull() ?: -1

    // edge case for PDFs and PSDs
    if (mimeInt == 14 || mimeStr.contains("pdf") || mimeInt == 16 || mimeStr.contains("photoshop")) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onImageTap() })
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (mimeInt == 14 || mimeStr.contains("pdf")) Icons.Default.PictureAsPdf else Icons.Default.Description,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (mimeInt == 14 || mimeStr.contains("pdf")) "PDF" else "PSD",
                    color = Color.LightGray
                )
                Text(
                    text = "Unsupported format for preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    } else {
        val painter = coil.compose.rememberAsyncImagePainter(
            model = coil.request.ImageRequest.Builder(context)
                .data(url)
                .repeatCount(if (gifAutoLoop) -1 else 0)
                .build()
        )
        val state = painter.state

        // play/pause trigger for when tapping the screen
        LaunchedEffect(isPlaying, state) {
            if (state is coil.compose.AsyncImagePainter.State.Success) {
                val drawable = state.result.drawable
                if (drawable is android.graphics.drawable.Animatable) {
                    if (isPlaying) drawable.start() else drawable.stop()
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                        // play/pause for GIFs
                        onTap = {
                            if (mime == "image/gif") {
                                isPlaying = !isPlaying
                            }
                            onImageTap()
                        }
                    )
                }
                .transformable(
                    state = transformableState,
                    canPan = { scale > 1f }
                )
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            androidx.compose.foundation.Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )

            // if loading, show circle
            if (state is coil.compose.AsyncImagePainter.State.Loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.5f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // if paused gif show play icon
            if (!isPlaying && mime == "image/gif" && state is coil.compose.AsyncImagePainter.State.Success) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleOutline,
                        contentDescription = "Paused",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(80.dp)
                    )
                }
            }
        }
    }
}

object VideoStateCache {
    // retain max 20 las positions
    val positions = LruCache<String, Long>(20)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun FullScreenVideoPlayer(
    url: String,
    mime: String,
    isActive: Boolean,
    apiKey: String,
    modifier: Modifier = Modifier,
    autoLoop: Boolean = false,
    muteButtonAtTop: Boolean = false,
    isUiVisible: Boolean = true,
    showSubtitlesButton: Boolean = true,
    onControllerVisibilityChanged: (Boolean) -> Unit = {},
    onVideoEnded: () -> Unit = {},
    onCodecInfoExtracted: (String) -> Unit = {},
    onResolutionExtracted: (String) -> Unit = {}
) {
    var hasBeenActive by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isActive) {
        if (isActive) {
            hasBeenActive = true
        }
    }

    if (!hasBeenActive) {
        // placeholder while swiping
        Box(modifier = modifier.fillMaxSize().background(Color.Black))
        return
    }

    val context = LocalContext.current
    var isMuted by rememberSaveable { mutableStateOf(true) }
    var showSubtitles by rememberSaveable { mutableStateOf(false) }
    var isZoomed by rememberSaveable { mutableStateOf(false) }

    // retrieve player from pool
    val exoPlayer = remember(context, apiKey) {
        com.asadtoast.aether.utils.ExoPlayerPool.getPlayer(context, apiKey)
    }

    // lifecycle control (pause if minimized)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
            } else if (event == Lifecycle.Event.ON_RESUME && isActive) {
                exoPlayer.play()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ondispose send player back to pool
    DisposableEffect(exoPlayer, url) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    VideoStateCache.positions.put(url, 0L)
                    onVideoEnded()
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                for (trackGroup in tracks.groups) {
                    if (trackGroup.type == C.TRACK_TYPE_VIDEO) {
                        val format = trackGroup.getTrackFormat(0)
                        val codecStr = listOfNotNull(format.sampleMimeType, format.codecs).joinToString(" / ")
                        if (codecStr.isNotEmpty()) {
                            onCodecInfoExtracted(codecStr)
                        }
                        if (format.width > 0 && format.height > 0) {
                            onResolutionExtracted("${format.width}x${format.height}")
                        }
                        break
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // external video player option in case of playback error
                playbackError = error.message ?: "Unknown decoding error"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            VideoStateCache.positions.put(url, kotlin.math.max(0L, exoPlayer.currentPosition))
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            com.asadtoast.aether.utils.ExoPlayerPool.releasePlayer(exoPlayer) // fancy recycling
        }
    }

    // background video prep
    LaunchedEffect(url, mime) {
        val mimeStr = mime.lowercase()
        val mimeInt = mimeStr.toIntOrNull() ?: -1

        // explicitly set MIME type for ExoPlayer to handle extension-less Hydrus URLs
        val exoMimeType = when {
            mimeInt == 4 || mimeStr.contains("mp4") -> androidx.media3.common.MimeTypes.VIDEO_MP4
            mimeInt == 8 || mimeStr.contains("webm") -> androidx.media3.common.MimeTypes.VIDEO_WEBM
            mimeInt == 42 || mimeStr.contains("matroska") || mimeStr.contains("mkv") -> androidx.media3.common.MimeTypes.VIDEO_MATROSKA
            else -> null
        }

        val parsedUri = url.toUri()
        val builder = parsedUri.buildUpon()

        if (parsedUri.isHierarchical) {
            builder.clearQuery()
            parsedUri.queryParameterNames.forEach { paramName ->
                if (paramName != "Hydrus-Client-API-Access-Key") {
                    parsedUri.getQueryParameters(paramName).forEach { paramValue ->
                        builder.appendQueryParameter(paramName, paramValue)
                    }
                }
            }
        }

        val cleanUrl = builder.build().toString()

        val mediaItemBuilder = MediaItem.Builder().setUri(cleanUrl)
        if (exoMimeType != null) {
            mediaItemBuilder.setMimeType(exoMimeType)
        }

        exoPlayer.setMediaItem(mediaItemBuilder.build())

        val pos = VideoStateCache.positions.get(url) ?: 0L
        if (pos > 0L) {
            exoPlayer.seekTo(pos)
        }

        exoPlayer.prepare()
    }

    // dynamic autoloop control
    LaunchedEffect(autoLoop) {
        exoPlayer.repeatMode = if (autoLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // smart playback controls (play, muted and subtitles)
    LaunchedEffect(isActive) {
        if (isActive) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        exoPlayer.setAudioAttributes(audioAttributes, !isMuted)
    }

    LaunchedEffect(showSubtitles) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !showSubtitles)
            .build()
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (playbackError != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Hardware decoding failed", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Your device doesn't support this video profile.", color = Color.Gray, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(url.toUri(), mime)
                            val headers = android.os.Bundle().apply {
                                putString("Hydrus-Client-API-Access-Key", apiKey)
                            }
                            putExtra(android.provider.Browser.EXTRA_HEADERS, headers)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Open with..."))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Open in External Player", color = Color.White)
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setControllerVisibilityListener(
                            androidx.media3.ui.PlayerView.ControllerVisibilityListener { visibility ->
                                val isVisible = visibility == android.view.View.VISIBLE
                                onControllerVisibilityChanged(isVisible)
                            }
                        )
                    }
                },
                update = { playerView ->
                    playerView.resizeMode = if (isZoomed) {
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                onRelease = { playerView ->
                    playerView.player = null
                },
                modifier = Modifier.fillMaxSize()
            )

            // mute button
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(if (muteButtonAtTop) Alignment.TopEnd else Alignment.BottomEnd)
            ) {
                Row(
                    modifier = Modifier.padding(
                        top = if (muteButtonAtTop) 8.dp else 0.dp,
                        bottom = if (muteButtonAtTop) 0.dp else 80.dp,
                        end = if (muteButtonAtTop) 8.dp else 16.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { isZoomed = !isZoomed },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isZoomed) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Toggle Zoom",
                            tint = Color.White
                        )
                    }

                    if (showSubtitlesButton) {
                        IconButton(
                            onClick = { showSubtitles = !showSubtitles },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = "Toggle Subtitles",
                                tint = if (showSubtitles) Color.White else Color.Gray
                            )
                        }
                    }

                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Mute",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

// swipe up tag/info section
@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedTagsSection(
    tagsText: String,
    viewModel: AetherViewModel,
    isEditMode: Boolean,
    tagsPendingDelete: Set<String>,
    onAddToSearch: (String) -> Unit,
    onNavigateToTagWiki: (String) -> Unit,
    onTogglePendingDelete: (String) -> Unit
) {
    val rawTags = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val namespaceOrder = listOf("creator", "artist", "series", "copyright", "character", "general", "meta")
    val groupedTags = rawTags.groupBy { tag ->
        if (tag.contains(":")) tag.substringBefore(":").lowercase() else "general"
    }.toSortedMap(compareBy { namespaceOrder.indexOf(it).let { i -> if (i == -1) 99 else i } })

    if (groupedTags.isEmpty()) {
        Text("No tags assigned.", color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        groupedTags.forEach { (namespace, tags) ->
            Column {
                val categoryColor = tags.firstOrNull()?.let { getNamespaceColor(it) } ?: Color.LightGray
                Text(
                    text = namespace.replaceFirstChar { it.uppercase() },
                    color = categoryColor,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                tags.forEach { fullTag ->
                    val displayName = if (fullTag.contains(":")) fullTag.substringAfter(":") else fullTag
                    val color = getNamespaceColor(fullTag)
                    val count = viewModel.getTagCount(fullTag)
                    val formattedCount = if (count > 999) String.format("%.1fk", count / 1000f) else count.toString()
                    val isPendingDelete = tagsPendingDelete.contains(fullTag)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        if (isEditMode) {
                            IconButton(
                                onClick = { onTogglePendingDelete(fullTag) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPendingDelete) Icons.Default.Undo else Icons.Default.Clear,
                                    contentDescription = if (isPendingDelete) "Restore" else "Remove",
                                    tint = if (isPendingDelete) Color.Green else Color.Red,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { onAddToSearch(fullTag) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    "Add",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = displayName,
                            color = if (isPendingDelete) Color.Gray else color,
                            style = if (isPendingDelete) {
                                LocalTextStyle.current.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                            } else {
                                LocalTextStyle.current
                            },
                            modifier = Modifier
                                .clickable { if (!isPendingDelete) onNavigateToTagWiki(fullTag) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .weight(1f)
                        )
                        if (!isEditMode) {
                            Text(
                                text = formattedCount,
                                color = Color.DarkGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

// tag info panel input field
@Composable
fun InlineTagInputField(
    suggestions: List<com.asadtoast.aether.data.TagSuggestion>,
    onQueryChanged: (String) -> Unit,
    onTagSubmitted: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val settingsManager = remember { com.asadtoast.aether.data.SettingsManager(context) }
    val privacyKeyboardEnabled by settingsManager.privacyKeyboardFlow.collectAsState(initial = true)

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        key(privacyKeyboardEnabled) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onQueryChanged(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add tag...", fontSize = 14.sp) },
                singleLine = true,
                maxLines = 1,
                trailingIcon = {
                    IconButton(onClick = {
                        if (text.isNotBlank()) {
                            onTagSubmitted(text.trim())
                            text = ""
                            focusManager.clearFocus()
                        } else {
                            focusManager.clearFocus()
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Unspecified,
                    autoCorrectEnabled = !privacyKeyboardEnabled,
                    keyboardType = if (privacyKeyboardEnabled) KeyboardType.Password else KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (text.isNotBlank()) {
                        onTagSubmitted(text.trim())
                        text = ""
                        focusManager.clearFocus()
                    } else {
                        focusManager.clearFocus()
                    }
                })
            )
        }

        if (suggestions.isNotEmpty() && text.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(suggestions) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val finalTag = if (suggestion.value.contains("➔")) {
                                        suggestion.value.substringAfter("➔").trim()
                                    } else {
                                        suggestion.value
                                    }
                                    onTagSubmitted(finalTag)
                                    text = ""
                                    focusManager.clearFocus()
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = suggestion.value, fontSize = 14.sp)
                            if (suggestion.count > 0) {
                                Text(
                                    text = suggestion.count.toInt().toString(),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}