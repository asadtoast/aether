package com.asadtoast.aether.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asadtoast.aether.data.AddTagsRequest
import com.asadtoast.aether.data.BackupManager
import com.asadtoast.aether.data.FileMetadata
import com.asadtoast.aether.data.HydrusRepository
import com.asadtoast.aether.data.SettingsManager
import com.asadtoast.aether.data.TagSuggestion
import com.asadtoast.aether.data.WikiReference
import com.asadtoast.aether.data.WikiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class WikiViewModel(
    private val wikiRepository: WikiRepository,
    private val settingsManager: SettingsManager,
    private val hydrusRepository: HydrusRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    val allWikiEntries = wikiRepository.allWikiEntries
    val mutedTags = wikiRepository.mutedTags

    // UI states
    var searchQuery by mutableStateOf("")
    var scrollIndex by mutableIntStateOf(0)
    var scrollOffset by mutableIntStateOf(0)
    var isSyncing by mutableStateOf(false)
        private set
    var showMutedTags by mutableStateOf(false)
        private set
    var isCheckingBackup by mutableStateOf(false)
        private set
    var isBackupProcessing by mutableStateOf(false)
        private set

    // tag suggestion flow
    private val _tagSuggestions = MutableStateFlow<List<TagSuggestion>>(emptyList())
    val tagSuggestions: StateFlow<List<TagSuggestion>> = _tagSuggestions.asStateFlow()

    // last backup handling
    data class BackupInfo(val fileId: Long, val timestamp: Long)
    private val _latestBackup = MutableStateFlow<BackupInfo?>(null)
    val latestBackup: StateFlow<BackupInfo?> = _latestBackup.asStateFlow()

    fun toggleShowMutedTags() { showMutedTags = !showMutedTags }
    fun stopSyncingState() { isSyncing = false }
    fun toggleMuteTag(tagName: String) {
        viewModelScope.launch {
            wikiRepository.toggleMuteTag(tagName)
        }
    }

    fun muteMultipleTags(tags: Set<String>) {
        viewModelScope.launch {
            val currentMuted = wikiRepository.mutedTags.value.toMutableSet()
            currentMuted.addAll(tags)
            wikiRepository.setMutedTags(currentMuted)
        }
    }

    fun saveWikiEntry(tagName: String, description: String, aliases: List<String>, references: List<WikiReference>, count: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                wikiRepository.saveWikiEntry(tagName, description, aliases, references, count)
            } catch (_: Exception) {
                try {
                    // if not available, register first
                    wikiRepository.syncTagsFromServer(listOf(TagSuggestion(tagName, count.toFloat())))
                    wikiRepository.saveWikiEntry(tagName, description, aliases, references, count)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }

    // tag suggestions retrieving
    fun fetchTagSuggestions(query: String) {
        if (query.isBlank()) {
            _tagSuggestions.value = emptyList()
            return
        }
        viewModelScope.launch {
            val currentWord = query.substringAfterLast(",").trim()
            val localMatches = allWikiEntries.value.filter { entry ->
                entry.aliases.any { it.contains(currentWord, ignoreCase = true) }
            }.take(10)

            val localSuggestions = localMatches.map { wikiEntry ->
                val matchedAlias = wikiEntry.aliases.firstOrNull { it.contains(currentWord, ignoreCase = true) } ?: wikiEntry.aliases.firstOrNull() ?: "Aliases"
                TagSuggestion("$matchedAlias ➔ ${wikiEntry.tagName}", wikiEntry.count.toFloat())
            }

            hydrusRepository.searchTags(currentWord)
                .onSuccess { response ->
                    val apiTags = response?.tags ?: emptyList()
                    val combined = (apiTags + localSuggestions).distinctBy {
                        if (it.value.contains("➔")) it.value.substringAfter("➔").trim() else it.value
                    }.sortedByDescending { it.count }

                    _tagSuggestions.value = combined
                }
                .onFailure { _tagSuggestions.value = localSuggestions }
        }
    }


    // alphanumerical scraper
    fun launchAlphanumericWikiScraper(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            isSyncing = true
            var importingError = false
            withContext(Dispatchers.IO) {
                try {
                    val collectedTags = mutableListOf<TagSuggestion>()
                    for (char in ('a'..'z') + ('0'..'9')) {
                        val result = hydrusRepository.searchTags(char.toString())
                        if (result.isSuccess) result.getOrNull()?.tags?.let { collectedTags.addAll(it) } else importingError = true
                        delay(100.milliseconds)
                    }

                    val systemTagsToFetch = listOf("system:inbox", "system:archive", "system:everything", "system:untagged")
                    for (sysTag in systemTagsToFetch) {
                        val result = hydrusRepository.searchFiles(tags = listOf(sysTag), service = "my files")
                        if (result.isSuccess) {
                            collectedTags.add(TagSuggestion(sysTag, result.getOrNull()?.file_ids?.size?.toFloat() ?: 0f))
                        } else importingError = true
                    }
                    wikiRepository.syncTagsFromServer(collectedTags)
                } catch (_: Exception) { importingError = true }
            }
            isSyncing = false
            fetchTagSuggestions("")
            onResult(!importingError)
        }
    }

    // check for backup
    fun checkForBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            isCheckingBackup = true
            val backupTag = "app_backup:tag_wiki"
            hydrusRepository.searchFiles(tags = listOf(backupTag), service = "my files")
                .onSuccess { response ->
                    if (response.file_ids.isNotEmpty()) {
                        hydrusRepository.getFileMetadata(fileIds = response.file_ids).onSuccess { metaResponse ->
                            val backup = metaResponse.metadata.maxByOrNull { it.realImportedTime }
                            _latestBackup.value = if (backup != null) BackupInfo(backup.file_id,
                                backup.realImportedTime
                            ) else null
                        }
                    } else _latestBackup.value = null
                }
            isCheckingBackup = false
        }
    }






    // manual backup
    fun forceManualBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            isBackupProcessing = true
            try {
                val backupTag = "app_backup:tag_wiki"
                hydrusRepository.searchFiles(tags = listOf(backupTag), service = "my files").onSuccess { res ->
                    res.file_ids.forEach { hydrusRepository.trashFile(fileId = it) }
                }

                backupManager.createBackupZip()?.let { requestBody ->
                    hydrusRepository.addFile(requestBody).onSuccess { addResponse ->
                        hydrusRepository.restoreFile(hash = addResponse.hash)
                        val tagRequest = AddTagsRequest(hash = addResponse.hash, service_names_to_tags = mapOf("my tags" to listOf(backupTag)))
                        hydrusRepository.addTags(tagRequest)
                        checkForBackup()
                    }
                }
            } finally {
                isBackupProcessing = false
            }
        }
    }


    // restore backup
    fun executeRestore(fileId: Long, merge: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            isBackupProcessing = true
            try {
                hydrusRepository.getFile(fileId).onSuccess { responseBody ->
                    responseBody.byteStream().use { inputStream ->
                        val zis = java.util.zip.ZipInputStream(inputStream)
                        var jsonString = ""
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name.endsWith(".json")) {
                                jsonString = zis.bufferedReader().use { it.readText() }
                                break
                            }
                            entry = zis.nextEntry
                        }
                        zis.close()
                        if (jsonString.isNotEmpty()) wikiRepository.restoreWikiFromJson(jsonString, merge)
                    }
                }.onFailure { e ->
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isBackupProcessing = false
            }
        }
    }


    // wiki categories
    private val _visibleCategories = MutableStateFlow<Set<String>>(emptySet())
    val visibleCategories: StateFlow<Set<String>> = _visibleCategories.asStateFlow()

    init {
        viewModelScope.launch {
            settingsManager.visibleCategoriesFlow.collect { categories ->
                _visibleCategories.value = categories
            }
        }
    }
    fun toggleCategoryVisibility(category: String) {
        val current = _visibleCategories.value.toMutableSet()
        if (current.contains(category)) {
            current.remove(category)
        } else {
            current.add(category)
        }
        viewModelScope.launch { settingsManager.saveVisibleCategories(current) }
    }

    fun deleteWikiEntry(tagName: String) {
        viewModelScope.launch { wikiRepository.deleteWikiEntry(tagName) }
    }

    // example images for tag details
    private val _exampleImages = MutableStateFlow<List<FileMetadata>>(emptyList())
    val exampleImages: StateFlow<List<FileMetadata>> = _exampleImages.asStateFlow()

    fun fetchExampleImagesForTag(tagName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _exampleImages.value = emptyList()
            hydrusRepository.searchFiles(tags = listOf(tagName), service = "my files")
                .onSuccess { searchResponse ->
                    val topIds = searchResponse.file_ids.take(4)
                    if (topIds.isNotEmpty()) {
                        hydrusRepository.getFileMetadata(fileIds = topIds)
                            .onSuccess { metaResponse ->
                                val mappedImages = metaResponse.metadata.map { detail ->
                                    FileMetadata(
                                        id = detail.file_id,
                                        thumbnailUrl = hydrusRepository.getFileUrl(detail.file_id, isThumbnail = true),
                                        fileUrl = hydrusRepository.getFileUrl(detail.file_id, isThumbnail = false),
                                        mime = detail.mime,
                                        size = detail.size,
                                        width = detail.width?.toInt() ?: 0,
                                        height = detail.height?.toInt() ?: 0,
                                        tags = emptyList(),
                                        importedDate = detail.realImportedTime,
                                        duration = detail.duration,
                                        framerate = null,
                                        hash = detail.hash,
                                        is_inbox = detail.is_inbox,
                                        is_trashed = detail.is_trashed,
                                        knownUrls = detail.known_urls ?: emptyList()
                                    )
                                }
                                _exampleImages.value = mappedImages
                            }
                    }
                }
        }
    }
}