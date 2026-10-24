package com.asadtoast.aether.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asadtoast.aether.data.FileRelationship
import com.asadtoast.aether.data.HydrusFileMetadata
import com.asadtoast.aether.data.HydrusRepository
import com.asadtoast.aether.data.SetFileRelationshipsRequest
import com.asadtoast.aether.data.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class GroupDuplicateUiState {
    object Loading : GroupDuplicateUiState()
    object Empty : GroupDuplicateUiState()
    data class Success(
        val currentGroupIndex: Int,
        val totalGroups: Int,
        val currentGroupMetadata: List<HydrusFileMetadata>
    ) : GroupDuplicateUiState()
    data class Error(val message: String) : GroupDuplicateUiState()
}

class GroupDuplicateViewModel(
    private val repository: HydrusRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<GroupDuplicateUiState>(GroupDuplicateUiState.Loading)
    val uiState: StateFlow<GroupDuplicateUiState> = _uiState.asStateFlow()

    private var allGroups: List<List<String>> = emptyList()
    private var currentIndex = 0
    private val seenGroups = mutableSetOf<Set<String>>()

    init {
        viewModelScope.launch {
            combine(
                settingsManager.maxHammingDistanceFlow,
                settingsManager.fileDomainFlow
            ) { distance, domain ->
                Pair(distance, domain)
            }.collectLatest {
                allGroups = emptyList()
                currentIndex = 0
                seenGroups.clear()
                loadGroups()
            }
        }
    }

    private fun loadGroups() {
        viewModelScope.launch {
            _uiState.value = GroupDuplicateUiState.Loading
            try {
                val distance = settingsManager.maxHammingDistanceFlow.first()

                val response = repository.getPotentialPairs(
                    maxHammingDistance = distance
                ).getOrNull()

                val pairs = response?.potential_duplicate_pairs ?: emptyList()

                // clustering algorithm
                val adjList = mutableMapOf<String, MutableSet<String>>()
                for (pair in pairs) {
                    if (pair.size >= 2) {
                        val hashA = pair[0]
                        val hashB = pair[1]
                        adjList.getOrPut(hashA) { mutableSetOf() }.add(hashB)
                        adjList.getOrPut(hashB) { mutableSetOf() }.add(hashA)
                    }
                }

                val visited = mutableSetOf<String>()
                val computedGroups = mutableListOf<List<String>>()

                for (node in adjList.keys) {
                    if (node !in visited) {
                        val group = mutableListOf<String>()
                        val queue = ArrayDeque<String>()
                        queue.add(node)
                        visited.add(node)

                        while (queue.isNotEmpty()) {
                            val curr = queue.removeFirst()
                            group.add(curr)
                            for (neighbor in adjList[curr] ?: emptySet()) {
                                if (neighbor !in visited) {
                                    visited.add(neighbor)
                                    queue.add(neighbor)
                                }
                            }
                        }

                        // only if more than 2 present
                        if (group.size > 2) {
                            val groupSet = group.toSet()
                            if (!seenGroups.contains(groupSet)) {
                                computedGroups.add(group)
                                seenGroups.add(groupSet)
                            }
                        }
                    }
                }

                allGroups = computedGroups

                if (allGroups.isEmpty()) {
                    _uiState.value = GroupDuplicateUiState.Empty
                } else {
                    currentIndex = 0
                    loadCurrentGroupMetadata()
                }
            } catch (e: Exception) {
                _uiState.value = GroupDuplicateUiState.Error(e.message ?: "Error loading duplicate groups")
            }
        }
    }

    private suspend fun loadCurrentGroupMetadata() {
        if (currentIndex >= allGroups.size) {
            loadGroups()
            return
        }

        _uiState.value = GroupDuplicateUiState.Loading
        try {
            val currentHashes = allGroups[currentIndex]
            val response = repository.getFileMetadataByHashes(currentHashes).getOrNull()
            val metadataList = response?.metadata ?: emptyList()
            _uiState.value = GroupDuplicateUiState.Success(
                currentGroupIndex = currentIndex + 1,
                totalGroups = allGroups.size,
                currentGroupMetadata = metadataList
            )
        } catch (e: Exception) {
            _uiState.value = GroupDuplicateUiState.Error(e.message ?: "Error loading group metadata")
        }
    }

    fun setKing(kingHash: String) {
        viewModelScope.launch {
            try {
                val currentHashes = allGroups[currentIndex]
                val duplicates = currentHashes.filter { it != kingHash }

                val relationships = duplicates.map { duplicateHash ->
                    FileRelationship(
                        hash_a = kingHash,
                        hash_b = duplicateHash,
                        relationship = 4 // 4 = A is better than B
                    )
                }

                repository.setFileRelationships(SetFileRelationshipsRequest(relationships))
                nextGroup()
            } catch (e: Exception) {
                _uiState.value = GroupDuplicateUiState.Error(e.message ?: "Error establishing king")
            }
        }
    }

    fun markAsFalsePositive() {
        viewModelScope.launch {
            try {
                val currentHashes = allGroups[currentIndex]
                val relationships = mutableListOf<FileRelationship>()

                for (i in currentHashes.indices) {
                    for (j in i + 1 until currentHashes.size) {
                        relationships.add(
                            FileRelationship(
                                hash_a = currentHashes[i],
                                hash_b = currentHashes[j],
                                relationship = 1 // 1 = false positive
                            )
                        )
                    }
                }

                repository.setFileRelationships(SetFileRelationshipsRequest(relationships))
                nextGroup()
            } catch (e: Exception) {
                _uiState.value = GroupDuplicateUiState.Error(e.message ?: "Error while establishing false positive")
            }
        }
    }

    fun setAsAlternates() {
        viewModelScope.launch {
            try {
                val currentHashes = allGroups[currentIndex]
                val relationships = mutableListOf<FileRelationship>()

                for (i in currentHashes.indices) {
                    for (j in i + 1 until currentHashes.size) {
                        relationships.add(
                            FileRelationship(
                                hash_a = currentHashes[i],
                                hash_b = currentHashes[j],
                                relationship = 2 // 2 = alternates
                            )
                        )
                    }
                }

                repository.setFileRelationships(SetFileRelationshipsRequest(relationships))
                nextGroup()
            } catch (e: Exception) {
                _uiState.value = GroupDuplicateUiState.Error(e.message ?: "Error while establishing alternates")
            }
        }
    }

    fun skipGroup() {
        viewModelScope.launch {
            nextGroup()
        }
    }

    fun trashCurrentFile(file: HydrusFileMetadata) {
        viewModelScope.launch {
            try {
                repository.trashFile(file.file_id)

                val mutableGroups = allGroups.toMutableList()
                val currentGroup = mutableGroups[currentIndex].toMutableList()
                val fileHash = file.hash ?: return@launch
                currentGroup.remove(fileHash)

                if (currentGroup.size <= 1) {
                    nextGroup()
                } else {
                    mutableGroups[currentIndex] = currentGroup
                    allGroups = mutableGroups
                    loadCurrentGroupMetadata()
                }
            } catch (e: Exception) {
                _uiState.value = GroupDuplicateUiState.Error(e.message ?: "Error trashing file")
            }
        }
    }

    fun trashAllCurrentGroup() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                if (currentState is GroupDuplicateUiState.Success) {
                    currentState.currentGroupMetadata.forEach { file ->
                        repository.trashFile(file.file_id)
                    }
                    nextGroup()
                }
            } catch (e: Exception) {
                _uiState.value = GroupDuplicateUiState.Error(e.message ?: "Error trashing all files")
            }
        }
    }

    private suspend fun nextGroup() {
        currentIndex++
        loadCurrentGroupMetadata()
    }
}