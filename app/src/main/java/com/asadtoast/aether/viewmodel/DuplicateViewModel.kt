package com.asadtoast.aether.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asadtoast.aether.data.DuplicatePair
import com.asadtoast.aether.data.FileMetadata
import com.asadtoast.aether.data.FileRelationship
import com.asadtoast.aether.data.HydrusRepository
import com.asadtoast.aether.data.SetFileRelationshipsRequest
import com.asadtoast.aether.data.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.collections.flatten

data class HistoryEntry(
    val pair: DuplicatePair,
    val actionTaken: DuplicateAction
)

enum class DuplicateAction {
    A_IS_BETTER, B_IS_BETTER, ALTERNATES, FALSE_POSITIVE, SKIP, DELETE_BOTH
}

class DuplicateViewModel(
    private val hydrusRepository: HydrusRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val historyStack = mutableListOf<HistoryEntry>()

    private val _duplicatePairs = MutableStateFlow<List<DuplicatePair>>(emptyList())
    val duplicatePairs: StateFlow<List<DuplicatePair>> = _duplicatePairs.asStateFlow()

    private val _totalPairsCount = MutableStateFlow(0)
    val totalPairsCount: StateFlow<Int> = _totalPairsCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var allPendingPairs = mutableListOf<List<String>>()

    private var lastUsedDistance: Int? = null
    private var lastUsedDomain: String? = null

    init {
        viewModelScope.launch {
            combine(
                settingsManager.maxHammingDistanceFlow,
                settingsManager.fileDomainFlow
            ) { distance, domain ->
                Pair(distance, domain)
            }.collect { (distance, domain) ->
                if (lastUsedDistance != distance || lastUsedDomain != domain) {
                    lastUsedDistance = distance
                    lastUsedDomain = domain
                    historyStack.clear()
                    allPendingPairs.clear()
                    _duplicatePairs.value = emptyList()
                    _totalPairsCount.value = 0
                    _isLoading.value = false
                    fetchDuplicatePotentials()
                }
            }
        }
    }

    fun fetchDuplicatePotentials() {
        if (_isLoading.value) return
        _isLoading.value = true

        viewModelScope.launch {
            val distance = settingsManager.maxHammingDistanceFlow.first()
            val domain = settingsManager.fileDomainFlow.first()
            lastUsedDistance = distance
            lastUsedDomain = domain

            val realCount = hydrusRepository.getPotentialPairsCount(distance).getOrNull()?.potential_duplicates_count

            hydrusRepository.getPotentialPairs(distance)
                .onSuccess { response ->
                    val pairs = response?.potential_duplicate_pairs ?: run {
                        _isLoading.value = false
                        return@onSuccess
                    }
                    allPendingPairs = pairs.toMutableList()
                    _totalPairsCount.value = realCount ?: pairs.size
                    loadNextBatch()
                }
                .onFailure { _ ->
                    _isLoading.value = false
                }
        }
    }

    private fun loadNextBatch() {
        if (allPendingPairs.isEmpty()) {
            _duplicatePairs.value = emptyList()
            _isLoading.value = false
            return
        }

        _isLoading.value = true
        val batchedPairs = allPendingPairs.take(15)
        allPendingPairs = allPendingPairs.drop(15).toMutableList()

        val allHashes = batchedPairs.flatMap { listOf(it[0], it[1]) }.distinct()

        viewModelScope.launch {
            hydrusRepository.getFileMetadataByHashes(allHashes)
                .onSuccess { metadataResponse ->
                    val metaMap = metadataResponse.metadata.associateBy { it.hash }

                    val finalPairs = batchedPairs.mapNotNull { hashList ->
                        val apiMetaA = metaMap[hashList[0]]
                        val apiMetaB = metaMap[hashList[1]]

                        if (apiMetaA != null && apiMetaB != null) {
                            DuplicatePair(
                                hash_a = hashList[0],
                                hash_b = hashList[1],
                                meta_a = FileMetadata(
                                    id = apiMetaA.file_id,
                                    thumbnailUrl = hydrusRepository.getFileUrl(apiMetaA.file_id, isThumbnail = true),
                                    fileUrl = hydrusRepository.getFileUrl(apiMetaA.file_id, isThumbnail = false),
                                    mime = apiMetaA.mime,
                                    size = apiMetaA.size,
                                    hash = apiMetaA.hash,
                                    width = apiMetaA.width?.toInt(),
                                    height = apiMetaA.height?.toInt(),
                                    tags = apiMetaA.tags?.values?.flatMap { it.display_tags?.values?.flatten() ?: emptyList() } ?: emptyList()
                                ),
                                meta_b = FileMetadata(
                                    id = apiMetaB.file_id,
                                    thumbnailUrl = hydrusRepository.getFileUrl(apiMetaB.file_id, isThumbnail = true),
                                    fileUrl = hydrusRepository.getFileUrl(apiMetaB.file_id, isThumbnail = false),
                                    mime = apiMetaB.mime,
                                    size = apiMetaB.size,
                                    hash = apiMetaB.hash,
                                    width = apiMetaB.width?.toInt(),
                                    height = apiMetaB.height?.toInt(),
                                    tags = apiMetaB.tags?.values?.flatMap { it.display_tags?.values?.flatten() ?: emptyList() } ?: emptyList()
                                )
                            )
                        } else null
                    }

                    _duplicatePairs.value = finalPairs
                    _isLoading.value = false
                }
                .onFailure { _ ->
                    _isLoading.value = false
                }
        }
    }

    fun resolveDuplicate(currentPair: DuplicatePair, action: DuplicateAction) {
        viewModelScope.launch {
            if (action != DuplicateAction.SKIP) {
                if (action == DuplicateAction.DELETE_BOTH) {
                    val idA = currentPair.meta_a.id
                    val idB = currentPair.meta_b.id

                    hydrusRepository.trashFile(fileId = idA)
                    hydrusRepository.trashFile(fileId = idB)

                } else {
                    val relationshipCode = when (action) {
                        DuplicateAction.FALSE_POSITIVE -> 1
                        DuplicateAction.ALTERNATES -> 3
                        DuplicateAction.A_IS_BETTER, DuplicateAction.B_IS_BETTER -> 4
                    }

                    val finalHashA = if (action == DuplicateAction.B_IS_BETTER) currentPair.hash_b else currentPair.hash_a
                    val finalHashB = if (action == DuplicateAction.B_IS_BETTER) currentPair.hash_a else currentPair.hash_b

                    val request = SetFileRelationshipsRequest(
                        relationships = listOf(FileRelationship(hash_a = finalHashA, hash_b = finalHashB, relationship = relationshipCode, do_default_content_merge = true))
                    )

                    hydrusRepository.setFileRelationships(request)

                    if (action == DuplicateAction.A_IS_BETTER) {
                        hydrusRepository.trashFile(fileId = currentPair.meta_b.id)
                    } else if (action == DuplicateAction.B_IS_BETTER) {
                        hydrusRepository.trashFile(fileId = currentPair.meta_a.id)
                    }
                }
            }

            historyStack.add(HistoryEntry(currentPair, action))

            val currentList = _duplicatePairs.value.toMutableList()
            if (currentList.isNotEmpty()) {
                currentList.removeAt(0)
                _duplicatePairs.value = currentList
            }

            _totalPairsCount.value -= 1

            if (_duplicatePairs.value.isEmpty()) {
                if (allPendingPairs.isNotEmpty()) {
                    loadNextBatch()
                } else {
                    fetchDuplicatePotentials()
                }
            }
        }
    }

    fun undoLastAction() {
        if (historyStack.isEmpty()) return

        val lastEntry = historyStack.removeAt(historyStack.lastIndex)
        val pairToRestore = lastEntry.pair

        viewModelScope.launch {
            // undo serer action if it wasn't a skip
            when (lastEntry.actionTaken) {
                DuplicateAction.SKIP -> {
                }
                DuplicateAction.DELETE_BOTH -> {
                    val idA = pairToRestore.meta_a.id
                    val idB = pairToRestore.meta_b.id

                    hydrusRepository.restoreFile(fileId = idA)
                    hydrusRepository.restoreFile(fileId = idB)
                }
                else -> {
                    val finalHashA = if (lastEntry.actionTaken == DuplicateAction.B_IS_BETTER) pairToRestore.hash_b else pairToRestore.hash_a
                    val finalHashB = if (lastEntry.actionTaken == DuplicateAction.B_IS_BETTER) pairToRestore.hash_a else pairToRestore.hash_b

                    val request = SetFileRelationshipsRequest(
                        relationships = listOf(FileRelationship(hash_a = finalHashA, hash_b = finalHashB, relationship = 0, do_default_content_merge = false))
                    )
                    hydrusRepository.setFileRelationships(request)

                    if (lastEntry.actionTaken == DuplicateAction.A_IS_BETTER) {
                        hydrusRepository.restoreFile(fileId = pairToRestore.meta_b.id)
                    } else if (lastEntry.actionTaken == DuplicateAction.B_IS_BETTER) {
                        hydrusRepository.restoreFile(fileId = pairToRestore.meta_a.id)
                    }
                }
            }

            // retrieve current pair for list
            val currentList = _duplicatePairs.value.toMutableList()
            currentList.add(0, pairToRestore)
            _duplicatePairs.value = currentList

            _totalPairsCount.value += 1
        }
    }
}