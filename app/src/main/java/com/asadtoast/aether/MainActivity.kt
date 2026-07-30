package com.asadtoast.aether

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkManager
import com.asadtoast.aether.data.SettingsManager
import com.asadtoast.aether.ui.FullScreenViewer
import com.asadtoast.aether.ui.GalleryScreen
import com.asadtoast.aether.ui.SettingsScreen
import com.asadtoast.aether.ui.WikiScreen
import com.asadtoast.aether.ui.theme.HydrusClientTheme
import com.asadtoast.aether.ui.TagDetailDialog
import com.asadtoast.aether.viewmodel.HydrusViewModel
import com.asadtoast.aether.work.BackupWorker
import java.util.concurrent.TimeUnit
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.asadtoast.aether.ui.DuplicateProcessingScreen
import com.asadtoast.aether.viewmodel.DuplicateViewModel
import com.asadtoast.aether.viewmodel.WikiViewModel

val LocalApiKey = androidx.compose.runtime.compositionLocalOf<String> { error("API Key not initialized") }

sealed class AppOverlay {
    data class CustomImageViewer(val list: List<com.asadtoast.aether.data.FileMetadata>, val index: Int) : AppOverlay()
    data class TagDetails(val entity: com.asadtoast.aether.data.TagWikiEntity) : AppOverlay()
    data class EditTagWiki(val entity: com.asadtoast.aether.data.TagWikiEntity) : AppOverlay()
}

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // just if device connected to internet
            .build()

        val backupWorkRequest = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "WikiBackupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            backupWorkRequest
        )

        setContent {
            val context = LocalContext.current.applicationContext

            val optimizedImageLoader = remember {
                ImageLoader.Builder(context)
                    .components {
                        if (Build.VERSION.SDK_INT >= 28) {
                            add(ImageDecoderDecoder.Factory())
                        } else {
                            add(GifDecoder.Factory())
                        }
                    }
                    .memoryCache {
                        MemoryCache.Builder(context)
                            .maxSizePercent(0.15)
                            .build()
                    }
                    .diskCache {
                        DiskCache.Builder()
                            .directory(context.cacheDir.resolve("coil_thumbnails"))
                            .maxSizeBytes(100L * 1024 * 1024)
                            .build()
                    }
                    .crossfade(true)
                    .respectCacheHeaders(false)
                    .build()
            }

            androidx.compose.runtime.CompositionLocalProvider(
                LocalImageLoader provides optimizedImageLoader
            ) {
                HydrusClientTheme {

                    // instanced dependencies via koin
                    val settingsManager: SettingsManager = org.koin.compose.koinInject()
                    val viewModel: HydrusViewModel = org.koin.androidx.compose.koinViewModel()
                    val duplicateViewModel: DuplicateViewModel = org.koin.androidx.compose.koinViewModel()
                    val wikiViewModel: WikiViewModel = org.koin.androidx.compose.koinViewModel()


            // DataStore async status retrieval
            val savedIp by settingsManager.serverIpFlow.collectAsState(initial = null)
            val savedApiKey by settingsManager.apiKeyFlow.collectAsState(initial = null)
            val isDarkMode by settingsManager.darkModeFlow.collectAsState(initial = false)
            val gridColumns by settingsManager.gridColumnsFlow.collectAsState(initial = 3)


            val currentApiKey = savedApiKey ?: ""

            // stop render NavHost when no data
            if (savedIp == null || savedApiKey == null) return@HydrusClientTheme

            val navController = rememberNavController()
            val startDest = if (savedIp!!.isEmpty() || savedApiKey!!.isEmpty()) "settings" else "gallery"


                    val okHttpClient = remember(currentApiKey) {
                        okhttp3.OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .addInterceptor { chain ->
                                val request = chain.request().newBuilder()
                                    .apply {
                                        // use header only if key is present
                                        if (currentApiKey.isNotEmpty()) {
                                            addHeader("Hydrus-Client-API-Access-Key", currentApiKey)
                                        }
                                    }
                                    .build()
                                chain.proceed(request)
                            }
                            .build()
                    }

                    val optimizedImageLoader = remember(currentApiKey) {
                        ImageLoader.Builder(context)
                            .okHttpClient(okHttpClient)
                            .components {
                                if (Build.VERSION.SDK_INT >= 28) {
                                    add(ImageDecoderDecoder.Factory())
                                } else {
                                    add(GifDecoder.Factory())
                                }
                            }
                            .crossfade(true)
                            .respectCacheHeaders(false)
                            .build()
                    }

                    CompositionLocalProvider(
                        LocalImageLoader provides optimizedImageLoader,
                        LocalApiKey provides currentApiKey
                    ) {
                        HydrusClientTheme(darkTheme = isDarkMode) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                var overlayStack by remember { mutableStateOf(listOf<AppOverlay>()) }
                                val mutedTags by wikiViewModel.mutedTags.collectAsState(initial = emptySet())

                                BackHandler(enabled = overlayStack.isNotEmpty()) {
                                    overlayStack = overlayStack.dropLast(1)
                                }

                                Box(modifier = Modifier.fillMaxSize()) {
                                    NavHost(
                                        navController = navController,
                                        startDestination = startDest
                                    ) {

                                        // route: settings
                                        composable("settings") {
                                            SettingsScreen(
                                                viewModel = viewModel,
                                                settingsManager = settingsManager,
                                                onSettingsSaved = {
                                                    // navigate to gallery and remove settings from history
                                                    navController.navigate("gallery") {
                                                        popUpTo("settings") { inclusive = true }
                                                    }
                                                },
                                                onCancel = {
                                                    if (savedIp!!.isNotEmpty() && savedApiKey!!.isNotEmpty()) {
                                                        navController.popBackStack()
                                                    }
                                                }
                                            )
                                        }

                                        // route: main gallery
                                        composable("gallery") {
                                            val pagedImages = viewModel.pagedImages.collectAsLazyPagingItems()

                                            Box(modifier = Modifier.fillMaxSize()) {
                                                GalleryScreen(
                                                    navController = navController,
                                                    viewModel = viewModel,
                                                    pagedImages = pagedImages,
                                                    gridColumns = gridColumns,
                                                    onOpenSettings = { navController.navigate("settings") },
                                                    onOpenWiki = { navController.navigate("wiki") }
                                                )

                                                // Base gallery viewer (level 0)
                                                val selectedIndex by viewModel.selectedImageIndex.collectAsState()

                                                selectedIndex?.let { index ->
                                                    // Only handle back if overlayStack is empty
                                                    BackHandler(enabled = overlayStack.isEmpty()) {
                                                        viewModel.selectImage(
                                                            null
                                                        )
                                                    }

                                                    FullScreenViewer(
                                                        viewModel = viewModel,
                                                        pagedImages = pagedImages,
                                                        initialIndex = index,
                                                        onClose = { viewModel.selectImage(null) },
                                                        onAddToSearch = { clickedTag ->
                                                            val currentText = viewModel.uiState.value.searchQuery
                                                            val separator =
                                                                if (currentText.isBlank() || currentText.trim()
                                                                        .endsWith(",")
                                                                ) "" else ", "

                                                            // auto assign string,
                                                            viewModel.fetchGallery("$currentText$separator$clickedTag, ")

                                                            android.widget.Toast.makeText(
                                                                context,
                                                                "Tag added to search",
                                                                android.widget.Toast.LENGTH_SHORT
                                                            ).show()
                                                        },
                                                        onNavigateToTagWiki = { clickedTag ->
                                                            val entity = viewModel.getTagWikiEntity(
                                                                clickedTag
                                                            )
                                                            overlayStack =
                                                                overlayStack + AppOverlay.TagDetails(
                                                                    entity
                                                                )
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        // route: wiki
                                        composable("wiki") {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                WikiScreen(
                                                    wikiViewModel = wikiViewModel,
                                                    navController = navController,
                                                    onClose = { navController.popBackStack() },
                                                    onOpenTagDetail = { entity ->
                                                        overlayStack = overlayStack + AppOverlay.TagDetails(entity)
                                                    }
                                                )
                                            }
                                        }

                                        // route: duplicate processing
                                        composable("duplicate_processing") {
                                            DuplicateProcessingScreen(
                                                viewModel = duplicateViewModel,
                                                onNavigateBack = { navController.popBackStack() }
                                            )
                                        }
                                    }

                                    // Global Overlay Renderer
                                    overlayStack.lastOrNull()?.let { currentOverlay ->
                                        when (currentOverlay) {
                                            is AppOverlay.TagDetails -> {
                                                TagDetailDialog(
                                                    entryDetail = currentOverlay.entity,
                                                    wikiViewModel = wikiViewModel,
                                                    isMuted = mutedTags.contains(currentOverlay.entity.tagName),
                                                    onMuteToggle = {
                                                        wikiViewModel.toggleMuteTag(
                                                            currentOverlay.entity.tagName
                                                        )
                                                    },
                                                    onDismiss = {
                                                        overlayStack = overlayStack.dropLast(1)
                                                    },
                                                    onEditClick = { updatedEntity ->
                                                        overlayStack = overlayStack + AppOverlay.EditTagWiki(updatedEntity)
                                                    },
                                                    onOpenExample = { list, index ->
                                                        overlayStack =
                                                            overlayStack + AppOverlay.CustomImageViewer(
                                                                list,
                                                                index
                                                            )
                                                    },
                                                    onTagLinkClick = { clickedTag ->
                                                        val entity = viewModel.getTagWikiEntity(clickedTag)
                                                        overlayStack = overlayStack + AppOverlay.TagDetails(entity)
                                                    }
                                                )
                                            }

                                            is AppOverlay.EditTagWiki -> {
                                                val allEntries by wikiViewModel.allWikiEntries.collectAsState(initial = emptyList())

                                                com.asadtoast.aether.ui.FullScreenEditDialog(
                                                    initialTag = currentOverlay.entity.tagName,
                                                    initialDescription = currentOverlay.entity.description,
                                                    initialAliases = currentOverlay.entity.aliases,
                                                    initialReferences = currentOverlay.entity.references,
                                                    initialCount = currentOverlay.entity.count,
                                                    allWikiEntries = allEntries,
                                                    onDismiss = {
                                                        overlayStack = overlayStack.dropLast(1)
                                                    },
                                                    onSave = { tag, desc, aliases, refs, count ->
                                                        wikiViewModel.saveWikiEntry(tag, desc, aliases, refs, count)
                                                        overlayStack = overlayStack.dropLast(1)
                                                    },
                                                    onDelete = {
                                                        wikiViewModel.deleteWikiEntry(currentOverlay.entity.tagName)
                                                        overlayStack = overlayStack.dropLast(1)
                                                    }
                                                )
                                            }

                                            is AppOverlay.CustomImageViewer -> {
                                                FullScreenViewer(
                                                    viewModel = viewModel,
                                                    initialIndex = currentOverlay.index,
                                                    customImageList = currentOverlay.list,
                                                    onClose = {
                                                        overlayStack = overlayStack.dropLast(1)
                                                    },
                                                    onAddToSearch = { clickedTag ->
                                                        val currentText = viewModel.uiState.value.searchQuery
                                                        val separator =
                                                            if (currentText.isBlank() || currentText.trim()
                                                                    .endsWith(",")
                                                            ) "" else ", "
                                                        viewModel.fetchGallery("$currentText$separator$clickedTag, ")
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "Tag added to search",
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    },
                                                    onNavigateToTagWiki = { tagName ->
                                                        val entity =
                                                            viewModel.getTagWikiEntity(tagName)
                                                        overlayStack =
                                                            overlayStack + AppOverlay.TagDetails(
                                                                entity
                                                            )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}