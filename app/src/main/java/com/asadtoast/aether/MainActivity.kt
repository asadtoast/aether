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
import com.asadtoast.aether.viewmodel.AetherViewModel
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
import androidx.compose.runtime.rememberCoroutineScope
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.asadtoast.aether.ui.DuplicateProcessingScreen
import com.asadtoast.aether.viewmodel.DuplicateViewModel
import com.asadtoast.aether.viewmodel.WikiViewModel
import kotlinx.coroutines.launch

val LocalApiKey = androidx.compose.runtime.compositionLocalOf<String> { error("API Key not initialized") }

sealed class OverlayScreen {
    data class TagDetail(val entity: com.asadtoast.aether.data.TagWikiEntity) : OverlayScreen()
    data class EditTagWiki(val entity: com.asadtoast.aether.data.TagWikiEntity) : OverlayScreen()
    data class GalleryViewer(val list: List<com.asadtoast.aether.data.FileMetadata>, val currentIndex: Int) : OverlayScreen()
    data class WikiSingleViewer(val fileMetadata: com.asadtoast.aether.data.FileMetadata) : OverlayScreen()
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

            // instanced dependencies via koin
            val settingsManager: SettingsManager = org.koin.compose.koinInject()
            val hydrusRepository: com.asadtoast.aether.data.HydrusRepository = org.koin.compose.koinInject()
            val viewModel: AetherViewModel = org.koin.androidx.compose.koinViewModel()
            val wikiViewModel: WikiViewModel = org.koin.androidx.compose.koinViewModel()

            // DataStore async status retrieval
            val savedIp by settingsManager.serverIpFlow.collectAsState(initial = null)
            val savedApiKey by settingsManager.apiKeyFlow.collectAsState(initial = null)
            val isDarkMode by settingsManager.darkModeFlow.collectAsState(initial = false)
            val gridColumns by settingsManager.gridColumnsFlow.collectAsState(initial = 3)

            val currentApiKey = savedApiKey ?: ""

            // stop render NavHost when no data
            if (savedIp == null || savedApiKey == null) return@setContent

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

            CompositionLocalProvider(
                LocalImageLoader provides optimizedImageLoader,
                LocalApiKey provides currentApiKey
            ) {
                HydrusClientTheme(darkTheme = isDarkMode) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val overlayStack = remember { androidx.compose.runtime.mutableStateListOf<OverlayScreen>() }
                        val coroutineScope = rememberCoroutineScope()
                        val mutedTags by wikiViewModel.mutedTags.collectAsState(initial = emptySet())

                        BackHandler(enabled = overlayStack.isNotEmpty()) {
                            overlayStack.removeLastOrNull()
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            NavHost(
                                navController = navController,
                                startDestination = startDest
                            ) {

                                // route: settings
                                composable("settings") {
                                    SettingsScreen(
                                        settingsManager = settingsManager,
                                        onTestConnection = { ip, port, apiKey ->
                                            hydrusRepository.testConnection(ip, port, apiKey)
                                        },
                                        onFetchDomains = {
                                            val result = hydrusRepository.getServices()
                                            val tagDomains = mutableListOf<String>()
                                            val fileDomains = mutableListOf<String>()
                                            val backupDomains = mutableListOf<String>()

                                            result.getOrNull()?.body()?.let { response ->
                                                // domain name combination based on service category keys
                                                response.services_v2?.forEach { service ->
                                                    val name = service.name ?: return@forEach
                                                    val type = service.type ?: return@forEach

                                                    if (type == 5) {
                                                        tagDomains.add(name)
                                                    }
                                                    if (type == 2) {
                                                        fileDomains.add(name)
                                                        backupDomains.add(name)
                                                    }
                                                }
                                            }

                                            mapOf(
                                                "tags" to tagDomains,
                                                "files" to fileDomains,
                                                "backups" to backupDomains
                                            )
                                        },
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
                                                viewModel.selectImage(null)
                                            }

                                            FullScreenViewer(
                                                viewModel = viewModel,
                                                pagedImages = pagedImages,
                                                initialIndex = index,
                                                customImageList = null,
                                                onClose = { viewModel.selectImage(null) },
                                                onPageChanged = { newIndex: Int ->
                                                    viewModel.selectImage(newIndex)
                                                },
                                                onAddToSearch = { clickedTag: String ->
                                                    // auto assign string,
                                                    val currentQuery = viewModel.uiState.value.searchQuery
                                                    val newQuery = if (currentQuery.isBlank()) clickedTag else "$currentQuery, $clickedTag"
                                                    viewModel.updateSearchQuery(newQuery)

                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "Tag added to search",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                onNavigateToTagWiki = { clickedTag: String ->
                                                    val entity = viewModel.getTagWikiEntity(clickedTag)
                                                    overlayStack.add(OverlayScreen.TagDetail(entity))
                                                },
                                                onAlternateClick = { hash: String ->
                                                    coroutineScope.launch {
                                                        val allHashes = viewModel.currentAlternates.value
                                                        if (allHashes.isNotEmpty()) {
                                                            val alternatesMetadata = viewModel.fetchAlternatesMetadata(allHashes)
                                                            val targetIndex = alternatesMetadata.indexOfFirst { it.hash == hash }.coerceAtLeast(0)
                                                            if (alternatesMetadata.isNotEmpty()) {
                                                                overlayStack.add(OverlayScreen.GalleryViewer(alternatesMetadata, targetIndex))
                                                            } else {
                                                                android.widget.Toast.makeText(context, "Failed to load alternates", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
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
                                                overlayStack.add(OverlayScreen.TagDetail(entity))
                                            }
                                        )
                                    }
                                }

                                // route: duplicate processing
                                composable("duplicate_processing") {
                                    val duplicateViewModel: DuplicateViewModel = org.koin.androidx.compose.koinViewModel()
                                    DuplicateProcessingScreen(
                                        viewModel = duplicateViewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }

                                // route: group duplicate processing
                                composable("group_duplicate_processing") {
                                    val groupDuplicateViewModel: com.asadtoast.aether.viewmodel.GroupDuplicateViewModel = org.koin.androidx.compose.koinViewModel()
                                    com.asadtoast.aether.ui.GroupDuplicateProcessingScreen(
                                        viewModel = groupDuplicateViewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                            }

                            // Global Overlay Renderer
                            when (val currentOverlay = overlayStack.lastOrNull()) {
                                is OverlayScreen.TagDetail -> {
                                    TagDetailDialog(
                                        entryDetail = currentOverlay.entity,
                                        wikiViewModel = wikiViewModel,
                                        isMuted = mutedTags.contains(currentOverlay.entity.tagName),
                                        onMuteToggle = {
                                            wikiViewModel.toggleMuteTag(currentOverlay.entity.tagName)
                                        },
                                        onDismiss = {
                                            overlayStack.removeLastOrNull()
                                        },
                                        onEditClick = { updatedEntity ->
                                            overlayStack.add(OverlayScreen.EditTagWiki(updatedEntity))
                                        },
                                        onOpenExample = { list, index ->
                                            val clickedMetadata = list.getOrNull(index)
                                            if (clickedMetadata != null) {
                                                overlayStack.add(OverlayScreen.WikiSingleViewer(clickedMetadata))
                                            }
                                        },
                                        onTagLinkClick = { clickedTag: String ->
                                            val entity = viewModel.getTagWikiEntity(clickedTag)
                                            overlayStack.add(OverlayScreen.TagDetail(entity))
                                        }
                                    )
                                }

                                is OverlayScreen.EditTagWiki -> {
                                    val allEntries by wikiViewModel.allWikiEntries.collectAsState(initial = emptyList())

                                    com.asadtoast.aether.ui.FullScreenEditDialog(
                                        initialTag = currentOverlay.entity.tagName,
                                        initialDescription = currentOverlay.entity.description,
                                        initialAliases = currentOverlay.entity.aliases,
                                        initialReferences = currentOverlay.entity.references,
                                        initialCount = currentOverlay.entity.count,
                                        allWikiEntries = allEntries,
                                        onDismiss = {
                                            overlayStack.removeLastOrNull()
                                        },
                                        onSave = { tag, desc, aliases, refs, count ->
                                            wikiViewModel.saveWikiEntry(tag, desc, aliases, refs, count)
                                            val updatedEntity = currentOverlay.entity.copy(
                                                tagName = tag,
                                                description = desc,
                                                aliases = aliases,
                                                references = refs,
                                                count = count
                                            )
                                            overlayStack.removeLastOrNull()
                                            overlayStack.removeLastOrNull()
                                            overlayStack.add(OverlayScreen.TagDetail(updatedEntity))
                                        },
                                        onDelete = {
                                            wikiViewModel.deleteWikiEntry(currentOverlay.entity.tagName)
                                            overlayStack.removeLastOrNull()
                                            overlayStack.removeLastOrNull()
                                        }
                                    )
                                }

                                is OverlayScreen.GalleryViewer -> {
                                    FullScreenViewer(
                                        viewModel = viewModel,
                                        pagedImages = null,
                                        initialIndex = currentOverlay.currentIndex,
                                        customImageList = currentOverlay.list,
                                        onClose = {
                                            overlayStack.removeLastOrNull()
                                        },
                                        onPageChanged = { newIndex: Int ->
                                            val lastIdx = overlayStack.lastIndex
                                            if (lastIdx >= 0 && overlayStack[lastIdx] is OverlayScreen.GalleryViewer) {
                                                overlayStack[lastIdx] = currentOverlay.copy(currentIndex = newIndex)
                                            }
                                        },
                                        onAddToSearch = { clickedTag: String ->
                                            val currentQuery = viewModel.uiState.value.searchQuery
                                            val newQuery = if (currentQuery.isBlank()) clickedTag else "$currentQuery, $clickedTag"
                                            viewModel.updateSearchQuery(newQuery)

                                            android.widget.Toast.makeText(
                                                context,
                                                "Tag added to search",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onNavigateToTagWiki = { clickedTag: String ->
                                            val entity = viewModel.getTagWikiEntity(clickedTag)
                                            overlayStack.add(OverlayScreen.TagDetail(entity))
                                        },
                                        onAlternateClick = { hash: String ->
                                            coroutineScope.launch {
                                                val allHashes = viewModel.currentAlternates.value
                                                if (allHashes.isNotEmpty()) {
                                                    val alternatesMetadata = viewModel.fetchAlternatesMetadata(allHashes)
                                                    val targetIndex = alternatesMetadata.indexOfFirst { it.hash == hash }.coerceAtLeast(0)
                                                    if (alternatesMetadata.isNotEmpty()) {
                                                        overlayStack.add(OverlayScreen.GalleryViewer(alternatesMetadata, targetIndex))
                                                    } else {
                                                        android.widget.Toast.makeText(context, "Failed to load alternates", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }

                                is OverlayScreen.WikiSingleViewer -> {
                                    FullScreenViewer(
                                        viewModel = viewModel,
                                        pagedImages = null,
                                        initialIndex = 0,
                                        customImageList = listOf(currentOverlay.fileMetadata),
                                        onClose = {
                                            overlayStack.removeLastOrNull()
                                        },
                                        onPageChanged = { _: Int -> },
                                        onAddToSearch = { clickedTag: String ->
                                            val currentQuery = viewModel.uiState.value.searchQuery
                                            val newQuery = if (currentQuery.isBlank()) clickedTag else "$currentQuery, $clickedTag"
                                            viewModel.updateSearchQuery(newQuery)

                                            android.widget.Toast.makeText(
                                                context,
                                                "Tag added to search",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onNavigateToTagWiki = { clickedTag: String ->
                                            val entity = viewModel.getTagWikiEntity(clickedTag)
                                            overlayStack.add(OverlayScreen.TagDetail(entity))
                                        },
                                        onAlternateClick = { hash: String ->
                                            coroutineScope.launch {
                                                val allHashes = viewModel.currentAlternates.value
                                                if (allHashes.isNotEmpty()) {
                                                    val alternatesMetadata = viewModel.fetchAlternatesMetadata(allHashes)
                                                    val targetIndex = alternatesMetadata.indexOfFirst { it.hash == hash }.coerceAtLeast(0)
                                                    if (alternatesMetadata.isNotEmpty()) {
                                                        overlayStack.add(OverlayScreen.GalleryViewer(alternatesMetadata, targetIndex))
                                                    } else {
                                                        android.widget.Toast.makeText(context, "Failed to load alternates", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }

                                null -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}