package com.asadtoast.aether.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asadtoast.aether.data.AddTagsRequest
import com.asadtoast.aether.data.FileMetadata
import com.asadtoast.aether.data.HydrusRepository
import com.asadtoast.aether.data.ScraperSource
import com.asadtoast.aether.data.SettingsManager
import com.asadtoast.aether.data.TagSuggestion
import com.asadtoast.aether.data.TagWikiEntity
import com.asadtoast.aether.data.WikiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.asadtoast.aether.data.HydrusPagingSource
import kotlinx.coroutines.isActive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import kotlin.coroutines.cancellation.CancellationException

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
}

sealed class BooruScrapeState {
    object Idle : BooruScrapeState()
    object Loading : BooruScrapeState()
    data class Success(val tags: List<com.asadtoast.aether.data.ParsedTag>) : BooruScrapeState()
    data class Error(val message: String) : BooruScrapeState()
}
data class GalleryUiState(
    val isLoading: Boolean = false,
    val isTrashMode: Boolean = false,
    val searchQuery: String = "",
    val sortType: Int = 2,
    val isAscending: Boolean = false,
    val totalFilesCount: Int = 0,
    val isReviewMode: Boolean = false,
    val reviewIndex: Int = 0,
    val isProcessingBatch: Boolean = false,
    val batchOperationMessage: String = "",
    val batchProgress: Int = 0,
    val batchTotal: Int = 0,
    val gifAutoLoop: Boolean = true,
    val showUploadResults: Boolean = false,
    val uploadSuccessCount: Int = 0,
    val uploadDuplicateCount: Int = 0,
    val uploadErrorCount: Int = 0,
    val uploadErrors: List<Pair<String, String>> = emptyList()
)

class AetherViewModel(
    private val wikiRepo: WikiRepository,
    private val settingsManager: SettingsManager,
    private val hydrusRepository: HydrusRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState = _uiState.asStateFlow()

    private val _searchTrigger = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedImages: Flow<PagingData<FileMetadata>> = _searchTrigger
        .flatMapLatest {
            if (currentActiveIds.isEmpty()) {
                flowOf(PagingData.empty())
            } else {
                val pagerFlow = Pager(
                    config = PagingConfig(pageSize = 30, enablePlaceholders = false),
                    pagingSourceFactory = {
                        HydrusPagingSource(hydrusRepository, currentActiveIds.toList()).also { currentPagingSource = it }
                    }
                ).flow

                combine(
                    pagerFlow,
                    _hidePdf,
                    _hidePsd,
                    _hideZip,
                    _hideVideos
                ) { pagingData, hidePdf, hidePsd, hideZip, hideVideos ->
                    pagingData.filter { file ->
                        val mimeStr = file.mime.lowercase()
                        val mimeInt = mimeStr.toIntOrNull() ?: -1

                        val isVideo = mimeInt in listOf(2, 3, 4, 8) || mimeStr.startsWith("video/") // hydrus video mimes
                        val isPdf = mimeInt == 14 || mimeStr.contains("pdf")
                        val isZip = mimeInt == 9 || mimeStr.contains("zip")
                        val isPsd = mimeInt == 16 || mimeStr.contains("photoshop") || mimeStr.contains("psd")

                        !(hidePdf && isPdf) && !(hidePsd && isPsd) && !(hideZip && isZip) && !(hideVideos && isVideo)
                    }
                }
            }
        }
        .cachedIn(viewModelScope)
    private var currentPagingSource: HydrusPagingSource? = null
    private var currentActiveIds = mutableListOf<Long>()
    private var hasModifications = false


    private val _hidePdf = MutableStateFlow(false)
    private val _hidePsd = MutableStateFlow(false)
    private val _hideZip = MutableStateFlow(false)
    private val _hideVideos = MutableStateFlow(false)

    // event channel
    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    // index for horizontal pager
    private val _selectedImageIndex = MutableStateFlow<Int?>(null)
    val selectedImageIndex = _selectedImageIndex.asStateFlow()

    // state to save the actual tags currently being edited
    private val _currentImageTags = MutableStateFlow("")
    val currentImageTags = _currentImageTags.asStateFlow()
    private val _currentMimeType = MutableStateFlow("")

    // state for clipboard tags
    private val _clipboardTags = MutableStateFlow<List<String>>(emptyList())
    val clipboardTags = _clipboardTags.asStateFlow()

    // state for alternate files
    private val _currentAlternates = MutableStateFlow<List<String>>(emptyList())
    val currentAlternates = _currentAlternates.asStateFlow()

    // state to define if metadata is being loaded
    private val _isLoadingMetadata = MutableStateFlow(false)

    private var lastSearchQuery = ""
    private var galleryJob: kotlinx.coroutines.Job? = null
    private var batchJob: kotlinx.coroutines.Job? = null

    // database engine
    val allWikiEntries = wikiRepo.allWikiEntries

    init {
        viewModelScope.launch { settingsManager.hidePdfFlow.collect { _hidePdf.value = it } }
        viewModelScope.launch { settingsManager.hidePsdFlow.collect { _hidePsd.value = it } }
        viewModelScope.launch { settingsManager.hideZipFlow.collect { _hideZip.value = it } }
        viewModelScope.launch { settingsManager.hideVideosFlow.collect { _hideVideos.value = it } }
        viewModelScope.launch { settingsManager.gifAutoLoopFlow.collect { loop -> _uiState.update { it.copy(gifAutoLoop = loop) } } }
    }


    // datastore
    fun saveGifAutoLoop(loop: Boolean) {
        viewModelScope.launch { settingsManager.saveGifAutoLoop(loop) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // alias translation
    private fun translateTags(tags: List<String>): List<String> {
        return tags.map { inputTag ->
            val cleanTag = if (inputTag.contains("➔")) {
                inputTag.substringAfter("➔").trim()
            } else {
                inputTag.trim()
            }

            val wikiEntry = wikiRepo.allWikiEntries.value.find { entry ->
                entry.tagName.equals(cleanTag, ignoreCase = true) ||
                        entry.aliases.any { it.equals(cleanTag, ignoreCase = true) }
            }
            wikiEntry?.tagName ?: cleanTag
        }.distinct()
    }

    fun fetchGallery(
        tagsText: String = "",
        sortType: Int = _uiState.value.sortType,
        sortAscending: Boolean = _uiState.value.isAscending,
        isTrash: Boolean = _uiState.value.isTrashMode
    ) {
        if (tagsText.isBlank() && !isTrash) {
            lastSearchQuery = ""
            currentActiveIds.clear()
            _searchTrigger.value += 1
            _uiState.update { it.copy(isLoading = false, totalFilesCount = 0, searchQuery = tagsText, sortType = sortType, isAscending = sortAscending, isTrashMode = false) }
            galleryJob?.cancel()
            return
        }
        galleryJob?.cancel()
        _uiState.update { it.copy(isLoading = true, searchQuery = tagsText, sortType = sortType, isAscending = sortAscending, isTrashMode = isTrash) }

        currentActiveIds.clear()
        _searchTrigger.value += 1

        if (tagsText.isNotEmpty()) {
            lastSearchQuery = tagsText
        }
        val activeTagsText = tagsText.ifEmpty { lastSearchQuery }

        galleryJob = viewModelScope.launch {
            try {
                // tag cleanup
                val baseTagsList = activeTagsText.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                // translate aliases to tags
                val translatedTagsList = translateTags(baseTagsList)

                // trash filter, applied to translated files
                val tagsList = if (isTrash) {
                    translatedTagsList.filter {
                        !it.equals("system:inbox", ignoreCase = true) &&
                                !it.equals("system:archive", ignoreCase = true)
                    }
                } else {
                    translatedTagsList
                }

                val targetService = if (isTrash) "trash" else "my files"

                // network request
                val searchResponse = hydrusRepository.searchFiles(
                    tags = tagsList,
                    sortType = sortType,
                    sortAsc = sortAscending,
                    service = targetService
                )

                if (searchResponse.isSuccess) {
                    val response = searchResponse.getOrNull()!!
                    val fetchedIds = response.file_ids
                    currentActiveIds = fetchedIds.toMutableList()
                    _uiState.update { it.copy(totalFilesCount = currentActiveIds.size) }

                    if (currentActiveIds.isNotEmpty()) {
                        _searchTrigger.value += 1
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(UiEvent.ShowToast("Error fetching gallery"))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }


    // gallery screen search functions
    fun selectImage(index: Int?) {
        _selectedImageIndex.value = index
    }

    // add tag to file function
    fun addTagsToImage(fileId: Long, tagsText: String) {
        viewModelScope.launch {
            try {
                val tagsList = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (tagsList.isEmpty()) return@launch

                val translatedTags = translateTags(tagsList)
                val request = AddTagsRequest(file_id = fileId, service_names_to_tags = mapOf("my tags" to translatedTags))

                val result = hydrusRepository.addTags(request)
                if (result.isSuccess) {
                    fetchExistingTagsForImage(fileId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(UiEvent.ShowToast("Error adding tags"))
            }
        }
    }

    // copy clipboard
    fun copyTagsToClipboard(tags: List<String>) {
        _clipboardTags.value = tags
    }

    // paste from clipboard
    fun pasteTagsFromClipboard(fileId: Long) {
        val tagsList = _clipboardTags.value
        if (tagsList.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    val translatedTags = translateTags(tagsList)
                    val request = AddTagsRequest(
                        file_id = fileId,
                        service_names_to_tags = mapOf("my tags" to translatedTags)
                    )
                    val result = hydrusRepository.addTags(request)
                    if (result.isSuccess) {
                        fetchExistingTagsForImage(fileId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _uiEvents.emit(UiEvent.ShowToast("Error pasting tags"))
                }
            }
        }
    }

    // remove tags from file function
    fun removeTagsFromImage(fileId: Long, tagsToRemove: String) {
        viewModelScope.launch {
            try {
                val tagsList = tagsToRemove.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (tagsList.isEmpty()) return@launch

                val translatedTags = translateTags(tagsList)
                val request = AddTagsRequest(file_id = fileId, service_names_to_actions_to_tags = mapOf("my tags" to mapOf("1" to translatedTags)))

                val result = hydrusRepository.addTags(request)
                if (result.isSuccess) {
                    fetchExistingTagsForImage(fileId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(UiEvent.ShowToast("Error removing tags"))
            }
        }
    }

    // function to request existing tags from server
    fun fetchExistingTagsForImage(fileId: Long) {
        viewModelScope.launch {
            _isLoadingMetadata.value = true
            _currentImageTags.value = "Loading tags..."

            try {
                val result = hydrusRepository.getFileMetadata(listOf(fileId))
                if (result.isSuccess) {
                    val response = result.getOrNull()!!
                    if (response.metadata.isNotEmpty()) {
                        val metadataItem = response.metadata[0]
                        _currentMimeType.value = metadataItem.mime

                        val tagsList = metadataItem.tags?.values?.find { it.name == "my tags" }?.display_tags?.get("0") ?: emptyList()
                        _currentImageTags.value = tagsList.joinToString(", ")
                    } else {
                        _currentImageTags.value = ""
                    }
                } else {
                    _currentImageTags.value = ""
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _currentImageTags.value = ""
            } finally {
                _isLoadingMetadata.value = false
            }
        }
    }

    fun setReviewMode(isReview: Boolean) {
        _uiState.update { it.copy(isReviewMode = isReview, reviewIndex = 0) }
        if (!isReview) {
            // gallery refresh after processing
            _uiState.update { it.copy(totalFilesCount = currentActiveIds.size) }
            if (hasModifications) {
                _searchTrigger.value += 1
                hasModifications = false
            } else {
                currentPagingSource?.invalidate()
            }
        }
    }

    fun resetReviewIndex() {
        _uiState.update { it.copy(reviewIndex = 0) }
        if (hasModifications) {
            _uiState.update { it.copy(totalFilesCount = currentActiveIds.size) }
            _searchTrigger.value += 1
            hasModifications = false
        }
    }

    // function to read, send to trash and update
    fun trashSpecificFile(fileId: Long, onEmptyList: () -> Unit) {
        viewModelScope.launch {
            try {
                val result = hydrusRepository.trashFile(fileId = fileId)
                if (result.isSuccess) {
                    currentActiveIds.remove(fileId)
                    hasModifications = true
                    if (currentActiveIds.isEmpty()) onEmptyList()
                    if (!_uiState.value.isReviewMode) {
                        _uiState.update { it.copy(totalFilesCount = currentActiveIds.size) }
                        currentPagingSource?.invalidate()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(UiEvent.ShowToast("Error trashing file"))
            }
        }
    }

    // archive specific file and update
    fun archiveFile(fileId: Long, onEmptyList: () -> Unit) {
        viewModelScope.launch {
            try {
                val result = hydrusRepository.archiveFile(fileId = fileId)
                if (result.isSuccess) {
                    currentActiveIds.remove(fileId)
                    hasModifications = true
                    if (currentActiveIds.isEmpty()) onEmptyList()
                    if (!_uiState.value.isReviewMode) {
                        _uiState.update { it.copy(totalFilesCount = currentActiveIds.size) }
                        currentPagingSource?.invalidate()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(UiEvent.ShowToast("Error archiving file"))
            }
        }
    }

    // inbox specific file and update
    fun inboxFile(fileId: Long, onEmptyList: () -> Unit) {
        viewModelScope.launch {
            try {
                val result = hydrusRepository.unarchiveFile(fileId = fileId)
                if (result.isSuccess) {
                    currentActiveIds.remove(fileId)
                    hasModifications = true
                    if (currentActiveIds.isEmpty()) onEmptyList()
                    if (!_uiState.value.isReviewMode) {
                        _uiState.update { it.copy(totalFilesCount = currentActiveIds.size) }
                        currentPagingSource?.invalidate()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(UiEvent.ShowToast("Error moving file to inbox"))
            }
        }
    }

    // skip function
    fun skipReview() {
        _uiState.update { it.copy(reviewIndex = it.reviewIndex + 1) }
    }

    // restore from trash
    fun restoreImage(fileId: Long, onEmptyList: () -> Unit) {
        viewModelScope.launch {
            try {
                val result = hydrusRepository.restoreFile(fileId = fileId)
                if (result.isSuccess) {
                    currentActiveIds.remove(fileId)
                    hasModifications = true
                    if (currentActiveIds.isEmpty()) onEmptyList()
                    if (!_uiState.value.isReviewMode) {
                        _uiState.update { it.copy(totalFilesCount = currentActiveIds.size) }
                        currentPagingSource?.invalidate()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(UiEvent.ShowToast("Error restoring file"))
            }
        }
    }

    // archive/delete undo
    fun undoReview(revertedFileId: Long) {
        if (_uiState.value.reviewIndex > 0) {
            _uiState.update { it.copy(reviewIndex = it.reviewIndex - 1) }
            viewModelScope.launch {
                try {
                    hydrusRepository.restoreFile(fileId = revertedFileId)
                } catch (e: Exception) {
                    e.printStackTrace()
                    _uiEvents.emit(UiEvent.ShowToast("Error undoing review"))
                }
            }
        }
    }

    // function for tag retrieving in real time
    private val _tagSuggestions = MutableStateFlow<List<TagSuggestion>>(emptyList())
    val tagSuggestions = _tagSuggestions.asStateFlow()

    // smart tag suggestion function
    suspend fun fetchTagSuggestions(query: String): List<TagSuggestion> {
        if (query.isBlank()) {
            return emptyList()
        }

        // search matches for local alias
        val currentWord = query.substringAfterLast(",").trim()
        if (currentWord.isEmpty()) {
            return emptyList()
        }
        val localEntries = wikiRepo.allWikiEntries.value

        val localMatches = localEntries.filter { entry ->
            entry.aliases.any { it.contains(currentWord, ignoreCase = true) }
        }.take(10)

        val localSuggestions = localMatches.map { wikiEntry ->
            val matchedAlias = wikiEntry.aliases.firstOrNull {
                it.contains(currentWord, ignoreCase = true)
            } ?: wikiEntry.aliases.firstOrNull() ?: "Aliases"

            TagSuggestion(
                value = "$matchedAlias ➔ ${wikiEntry.tagName}",
                count = wikiEntry.count.toFloat()
            )
        }

        return try {
            // remote search and merge
            val result = hydrusRepository.searchTags(currentWord)
            if (result.isSuccess) {
                val response = result.getOrNull()
                val apiSuggestions = response?.tags ?: emptyList()

                // update local database count in background
                wikiRepo.updateExistingTagCounts(apiSuggestions)

                // combine server and local, then delete dupes
                (apiSuggestions + localSuggestions).distinctBy {
                    if (it.value.contains("➔")) it.value.substringAfter("➔").trim() else it.value
                }.sortedByDescending { it.count }
            } else {
                // if no network, show local (check if still useful)
                localSuggestions
            }
        } catch (e: Exception) {
            e.printStackTrace()
            localSuggestions
        }
    }

    fun getTagCount(tagName: String): Int {
        val list = allWikiEntries.value

        // binary logarithmic search
        val index = list.binarySearchBy(tagName) { it.tagName }

        return if (index >= 0) {
            when (val countValue = list[index].count) {
                else -> countValue.toInt()
            }
        } else {
            0
        }
    }

    fun getTagWikiEntity(tagName: String): TagWikiEntity {
        return allWikiEntries.value.find { entry ->
            entry.tagName.equals(tagName, ignoreCase = true) ||
                    entry.aliases.any { it.equals(tagName, ignoreCase = true) }
        } ?: TagWikiEntity(
            tagName = tagName,
            description = "",
            aliases = emptyList(),
            count = 0
        )
    }

    fun trashMultipleFiles(fileIds: Set<Long>, skipRefresh: Boolean = false) {
        if (fileIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingBatch = true, batchOperationMessage = "Processing", batchTotal = fileIds.size, batchProgress = 0) }

            try {
                // background work
                withContext(Dispatchers.IO) {
                    fileIds.chunked(50).forEach { batch ->
                        batch.map { id ->
                            async { hydrusRepository.trashFile(fileId = id) }
                        }.awaitAll()

                        withContext(Dispatchers.Main) {
                            currentActiveIds.removeAll(batch)
                            hasModifications = true
                            _uiState.update { it.copy(batchProgress = it.batchProgress + batch.size) }
                        }
                    }
                }

                if (!skipRefresh) {
                    _uiState.update { it.copy(totalFilesCount = currentActiveIds.size) }
                    if (!_uiState.value.isReviewMode) {
                        currentPagingSource?.invalidate()
                    } else {
                        _searchTrigger.value += 1
                        hasModifications = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(UiEvent.ShowToast("Error trashing multiple files"))
            } finally {
                _uiState.update { it.copy(isProcessingBatch = false) }
            }
        }
    }

    fun archiveMultipleFiles(fileIds: Set<Long>, skipRefresh: Boolean = false) {
        if (fileIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingBatch = true, batchOperationMessage = "Processing", batchTotal = fileIds.size, batchProgress = 0) }

            try {
                withContext(Dispatchers.IO) {
                    fileIds.chunked(50).forEach { batch ->
                        batch.map { id ->
                            async {
                                hydrusRepository.restoreFile(fileId = id)
                                hydrusRepository.archiveFile(fileId = id)
                            }
                        }.awaitAll()
                        withContext(Dispatchers.Main) {
                            currentActiveIds.removeAll(batch)
                            hasModifications = true
                            _uiState.update { it.copy(batchProgress = it.batchProgress + batch.size) }
                        }
                    }
                }

                if (!skipRefresh) {
                    _uiState.update { it.copy(totalFilesCount = currentActiveIds.size) }
                    if (!_uiState.value.isReviewMode) {
                        currentPagingSource?.invalidate()
                    } else {
                        _searchTrigger.value += 1
                        hasModifications = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(UiEvent.ShowToast("Error archiving multiple files"))
            } finally {
                _uiState.update { it.copy(isProcessingBatch = false) }
            }
        }
    }

    fun inboxMultipleFiles(fileIds: Set<Long>, skipRefresh: Boolean = false) {
        if (fileIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingBatch = true, batchOperationMessage = "Processing", batchTotal = fileIds.size, batchProgress = 0) }

            try {
                withContext(Dispatchers.IO) {
                    fileIds.chunked(50).forEach { batch ->
                        batch.map { id ->
                            async {
                                hydrusRepository.restoreFile(fileId = id)
                                hydrusRepository.unarchiveFile(fileId = id)
                            }
                        }.awaitAll()
                        withContext(Dispatchers.Main) {
                            currentActiveIds.removeAll(batch)
                            hasModifications = true
                            _uiState.update { it.copy(batchProgress = it.batchProgress + batch.size) }
                        }
                    }
                }

                if (!skipRefresh) {
                    _uiState.update { it.copy(totalFilesCount = currentActiveIds.size) }
                    if (!_uiState.value.isReviewMode) {
                        currentPagingSource?.invalidate()
                    } else {
                        _searchTrigger.value += 1
                        hasModifications = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(UiEvent.ShowToast("Error moving multiple files to inbox"))
            } finally {
                _uiState.update { it.copy(isProcessingBatch = false) }
            }
        }
    }

    fun modifyTagsForMultipleFiles(
        fileIds: Set<Long>,
        tagsText: String,
        isDelete: Boolean,
        targetService: String
    ) {
        if (fileIds.isEmpty() || tagsText.isBlank()) return

        viewModelScope.launch {
            try {
                val tagsList = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (tagsList.isEmpty()) return@launch

                val translatedTags = translateTags(tagsList)

                _uiState.update { it.copy(isProcessingBatch = true, batchOperationMessage = "Processing", batchTotal = 1, batchProgress = 0) }

                val actionMap = if (isDelete) {
                    mapOf(targetService to mapOf("1" to translatedTags))
                } else {
                    mapOf(targetService to mapOf("0" to translatedTags))
                }

                val request = AddTagsRequest(
                    file_ids = fileIds.toList(),
                    service_names_to_actions_to_tags = actionMap
                )

                val result = hydrusRepository.addTags(request)
                if (result.isSuccess) {
                    currentPagingSource?.invalidate()
                    _uiEvents.emit(UiEvent.ShowToast("Tags updated successfully"))
                } else {
                    _uiEvents.emit(UiEvent.ShowToast("Error while updating tag"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(UiEvent.ShowToast("Error modifying tags for multiple files"))
            } finally {
                _uiState.update { it.copy(isProcessingBatch = false) }
            }
        }
    }

    fun linkMultipleFiles(fileIds: Set<Long>, skipRefresh: Boolean = false) {
        if (fileIds.size < 2) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingBatch = true, batchOperationMessage = "Processing", batchTotal = fileIds.size - 1, batchProgress = 0) }

            try {
                // fetch metadata to get the hashes required by the relationship API
                val metadataResult = hydrusRepository.getFileMetadata(fileIds.toList())

                if (metadataResult.isSuccess) {
                    val metadataList = metadataResult.getOrNull()?.metadata ?: emptyList()
                    val hashes = metadataList.mapNotNull { it.hash }.filter { it.isNotEmpty() }

                    if (hashes.size >= 2) {
                        val hashA = hashes.first()
                        val remainingHashes = hashes.drop(1)

                        withContext(Dispatchers.IO) {
                            // relationship 0 = alternates
                            val relationshipsList = remainingHashes.map { hashB ->
                                com.asadtoast.aether.data.FileRelationship(
                                    hash_a = hashA,
                                    hash_b = hashB,
                                    relationship = 0
                                )
                            }

                            val request = com.asadtoast.aether.data.SetFileRelationshipsRequest(
                                relationships = relationshipsList
                            )

                            hydrusRepository.setFileRelationships(request)

                            withContext(Dispatchers.Main) {
                                _uiState.update { it.copy(batchProgress = remainingHashes.size) }
                            }
                        }
                        _uiEvents.emit(UiEvent.ShowToast("Files grouped as alternates successfully"))
                    } else {
                        _uiEvents.emit(UiEvent.ShowToast("Could not retrieve enough hashes to group files"))
                    }
                } else {
                    _uiEvents.emit(UiEvent.ShowToast("Error retrieving file metadata"))
                }

                if (!skipRefresh) {
                    currentPagingSource?.invalidate()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(UiEvent.ShowToast("Error linking multiple files"))
            } finally {
                _uiState.update { it.copy(isProcessingBatch = false) }
            }
        }
    }


    // booru scraper state and repository
    private val booruRepository = com.asadtoast.aether.data.BooruRepository()

    private val _booruScrapeState = MutableStateFlow<BooruScrapeState>(BooruScrapeState.Idle)
    val booruScrapeState = _booruScrapeState.asStateFlow()

    fun scrapeBooruTags(file: FileMetadata, source: ScraperSource) {
        viewModelScope.launch {
            _booruScrapeState.value = BooruScrapeState.Loading

            try {
                if (source == ScraperSource.PAWCHIVE) {
                    val hash = file.hash
                    if (hash.isNullOrEmpty()) {
                        _booruScrapeState.value = BooruScrapeState.Error("SHA256 hash is missing for this file.")
                        return@launch
                    }

                    val result = booruRepository.getTagsForHash(md5 = null, sha256 = hash, source = source)
                    if (result.isSuccess) {
                        val tags = result.getOrNull()!!
                        if (tags.isEmpty()) {
                            _booruScrapeState.value = BooruScrapeState.Error("No tags found in ${source.name} for this file.")
                        } else {
                            _booruScrapeState.value = BooruScrapeState.Success(tags)
                        }
                    } else {
                        _booruScrapeState.value = BooruScrapeState.Error(result.exceptionOrNull()?.message ?: "Error querying ${source.name}.")
                    }
                    return@launch
                }

                /*
                filesize limit for md5 calculation on the fly
                fallback, calculate MD5 hash by downloading file in streaming memory/bandwidth limit 10MB filesize
                val sizeLimit = 10 * 1024 * 1024L // 10 MB
                if (file.size != null && file.size > sizeLimit) {
                    _booruScrapeState.value = BooruScrapeState.Error("File is too large (${file.size / 1024 / 1024}MB) to calculate MD5. Max allowed is 10MB.")
                    return@launch
                }
                */

                // calculate md5 hash by downloading file in streaming
                val md5Result = hydrusRepository.getFileMd5(fileId = file.id)

                if (md5Result.isSuccess) {
                    val md5Hash = md5Result.getOrNull()!!
                    if (md5Hash.isEmpty()) {
                        _booruScrapeState.value = BooruScrapeState.Error("Failed to calculate MD5 hash.")
                        return@launch
                    }

                    // search on booru
                    val booruResult = booruRepository.getTagsForHash(md5 = md5Hash, sha256 = null, source = source)
                    if (booruResult.isSuccess) {
                        val tags = booruResult.getOrNull()!!
                        if (tags.isEmpty()) {
                            _booruScrapeState.value = BooruScrapeState.Error("No tags found in ${source.name} for this file.")
                        } else {
                            _booruScrapeState.value = BooruScrapeState.Success(tags)
                        }
                    } else {
                        _booruScrapeState.value = BooruScrapeState.Error(booruResult.exceptionOrNull()?.message ?: "Error querying ${source.name}.")
                    }
                } else {
                    _booruScrapeState.value = BooruScrapeState.Error(md5Result.exceptionOrNull()?.message ?: "Error downloading file to calculate MD5.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _booruScrapeState.value = BooruScrapeState.Error("Network error while scraping tags.")
            }
        }
    }

    fun resetBooruState() {
        _booruScrapeState.value = BooruScrapeState.Idle
    }

    // fetch metadata for alternate files using their hashes
    suspend fun fetchAlternatesMetadata(hashes: List<String>): List<FileMetadata> {
        return try {
            val result = hydrusRepository.getFileMetadataByHashes(hashes)
            val metadataList = result.getOrNull()?.metadata ?: return emptyList()

            metadataList.map { metadata ->
                FileMetadata(
                    id = metadata.file_id,
                    hash = metadata.hash,
                    size = metadata.size,
                    mime = metadata.mime,
                    width = metadata.width?.toInt(),
                    height = metadata.height?.toInt(),
                    thumbnailUrl = hydrusRepository.getFileUrl(metadata.file_id, isThumbnail = true),
                    fileUrl = hydrusRepository.getFileUrl(metadata.file_id, isThumbnail = false)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun loadAlternates(hash: String) {
        viewModelScope.launch {
            try {
                val result = hydrusRepository.getFileRelationships(hash)

                if (result.isSuccess) {
                    val response = result.getOrNull()

                    val alternatesList = mutableListOf<String>()

                    val responseMap = response?.file_relationships
                    val hashData = responseMap?.get(hash) as? Map<*, *>

                    hashData?.forEach { (key, value) ->
                        // verify key number
                        if (key.toString().toIntOrNull() != null && value is List<*>) {
                            value.forEach { hashItem ->
                                val hashString = hashItem.toString()
                                // thing to not include original hash and duplicate stuff
                                if (hashString != hash && !alternatesList.contains(hashString)) {
                                    alternatesList.add(hashString)
                                }
                            }
                        }
                    }

                    _currentAlternates.value = alternatesList
                } else {
                    _currentAlternates.value = emptyList()
                }
            } catch (_: Exception) {
                _currentAlternates.value = emptyList()
            }
        }
    }

    // function to upload files via streaming to avoid OOM on large files
    fun uploadFiles(context: Context, uris: List<Uri>, deleteAfterUpload: Boolean = false) {
        if (uris.isEmpty()) return

        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isProcessingBatch = true, batchOperationMessage = "Uploading", batchTotal = uris.size, batchProgress = 0) }
                _uiEvents.emit(UiEvent.ShowToast("Uploading ${uris.size} file(s)..."))

                var successCount = 0
                var presentCount = 0
                var failedCount = 0
                val errorsList = mutableListOf<Pair<String, String>>()
                val contentResolver = context.contentResolver

                for (uri in uris) {
                    if (!isActive) break

                    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

                    var fileName = "Unknown file"
                    var fileSize = -1L
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                fileName = cursor.getString(nameIndex)
                            }
                            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (sizeIndex != -1) {
                                fileSize = cursor.getLong(sizeIndex)
                            }
                        }
                    }

                    val requestBody = object : RequestBody() {
                        override fun contentType() = mimeType.toMediaTypeOrNull()

                        override fun contentLength(): Long = fileSize

                        override fun writeTo(sink: BufferedSink) {
                            contentResolver.openInputStream(uri)?.use { inputStream ->
                                inputStream.source().use { source ->
                                    sink.writeAll(source)
                                }
                            }
                        }
                    }

                    val result = hydrusRepository.addFile(requestBody)
                    if (result.isSuccess) {
                        val response = result.getOrNull()
                        val status = response?.status ?: 3 // default to failed if null

                        when (status) {
                            1 -> successCount++
                            2 -> presentCount++
                            3 -> {
                                failedCount++
                                errorsList.add(fileName to "Rejected: File is in the server's trash")
                            }
                            4 -> {
                                failedCount++
                                errorsList.add(fileName to "Rejected: Server processing failed (FFmpeg timeout or invalid format)")
                            }
                            else -> {
                                failedCount++
                                errorsList.add(fileName to "Rejected: Unknown API status $status")
                            }
                        }

                        if (deleteAfterUpload && (status == 1 || status == 2)) {
                            try {
                                android.provider.DocumentsContract.deleteDocument(contentResolver, uri)
                            } catch (_: SecurityException) {
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } else {
                        failedCount++
                        val exception = result.exceptionOrNull()
                        val errorMsg = exception?.message ?: "Unknown network error"
                        errorsList.add(fileName to errorMsg)
                    }
                    _uiState.update { it.copy(batchProgress = it.batchProgress + 1) }
                }

                _uiState.update {
                    it.copy(
                        showUploadResults = true,
                        uploadSuccessCount = successCount,
                        uploadDuplicateCount = presentCount,
                        uploadErrorCount = failedCount,
                        uploadErrors = errorsList
                    )
                }

                if (successCount > 0 || presentCount > 0) {
                    // refresh gallery to show the new file if applicable
                    fetchGallery(tagsText = _uiState.value.searchQuery)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(UiEvent.ShowToast("Error uploading files"))
            } finally {
                _uiState.update { it.copy(isProcessingBatch = false) }
            }
        }
    }

    fun cancelBatchOperation() {
        batchJob?.cancel()
        batchJob = null
        _uiState.update { it.copy(isProcessingBatch = false) }
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowToast("Upload cancelled"))
        }
    }

    fun dismissUploadResults() {
        _uiState.update { it.copy(showUploadResults = false, uploadErrors = emptyList()) }
    }
}