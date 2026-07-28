package com.asadtoast.aether.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asadtoast.aether.data.DuplicatePair
import com.asadtoast.aether.data.FileMetadata
import com.asadtoast.aether.data.FileRelationship
import com.asadtoast.aether.data.HydrusRepository
import com.asadtoast.aether.data.SetFileRelationshipsRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val hydrusRepository: HydrusRepository
) : ViewModel() {

    private val historyStack = mutableListOf<HistoryEntry>()

    private val _duplicatePairs = MutableStateFlow<List<DuplicatePair>>(emptyList())
    val duplicatePairs: StateFlow<List<DuplicatePair>> = _duplicatePairs.asStateFlow()

    private val _totalPairsCount = MutableStateFlow(0)
    val totalPairsCount: StateFlow<Int> = _totalPairsCount.asStateFlow()

    private val _totalFilesCount = MutableStateFlow(0)
    val totalFilesCount: StateFlow<Int> = _totalFilesCount.asStateFlow()

    private var allPendingPairs = mutableListOf<List<String>>()

    private fun updateCounts() {
        val loadedPairsCount = _duplicatePairs.value.size
        _totalPairsCount.value = loadedPairsCount + allPendingPairs.size

        val loadedHashes = _duplicatePairs.value.flatMap { listOf(it.hash_a, it.hash_b) }
        val pendingHashes = allPendingPairs.flatten()
        _totalFilesCount.value = (loadedHashes + pendingHashes).distinct().size
    }

    fun fetchDuplicatePotentials() {
        viewModelScope.launch {
            hydrusRepository.getPotentialPairs()
                .onSuccess { response ->
                    val pairs = response?.potential_duplicate_pairs ?: return@onSuccess
                    allPendingPairs = pairs.toMutableList()
                    loadNextBatch()
                }
                .onFailure { _ ->
                }
        }
    }

    private fun loadNextBatch() {
        if (allPendingPairs.isEmpty()) {
            _duplicatePairs.value = emptyList()
            updateCounts()
            return
        }

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
                    updateCounts()
                }
                .onFailure { _ ->
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
                        else -> throw IllegalStateException()
                    }

                    val finalHashA = if (action == DuplicateAction.B_IS_BETTER) currentPair.hash_b else currentPair.hash_a
                    val finalHashB = if (action == DuplicateAction.B_IS_BETTER) currentPair.hash_a else currentPair.hash_b

                    val request = SetFileRelationshipsRequest(
                        relationships = listOf(FileRelationship(hash_a = finalHashA, hash_b = finalHashB, relationship = relationshipCode, do_default_content_merge = true))
                    )

                    hydrusRepository.setFileRelationships(request)
                }
            }


            historyStack.add(HistoryEntry(currentPair, action))

            val currentList = _duplicatePairs.value.toMutableList()
            if (currentList.isNotEmpty()) {
                currentList.removeAt(0)
                _duplicatePairs.value = currentList
            }

            updateCounts()

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
                }
            }

            // retrieve current pair for list
            val currentList = _duplicatePairs.value.toMutableList()
            currentList.add(0, pairToRestore)
            _duplicatePairs.value = currentList

            updateCounts()
        }
    }
}