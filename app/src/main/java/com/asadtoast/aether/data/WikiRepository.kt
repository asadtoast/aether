package com.asadtoast.aether.data

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

@JsonClass(generateAdapter = true)
data class TagWikiEntity(
    val tagName: String,
    val domain: String = "my tags",
    val description: String = "",
    val aliases: List<String> = emptyList(),
    val references: List<WikiReference> = emptyList(),
    val count: Long = 0L,
    val lastUpdated: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class WikiReference(
    val siteName: String,
    val url: String,
    val originalTag: String = "",
    val romaji: String = ""
)

@JsonClass(generateAdapter = true)
data class TagWikiBackup(
    val wikiEntries: List<TagWikiEntity>,
    val mutedTags: List<String> = emptyList()
)

class WikiRepository(context: Context, private val settingsManager: SettingsManager) {

    private val file = File(context.filesDir, "hydrus_wiki.json")
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val backupAdapter = moshi.adapter(TagWikiBackup::class.java)

    private val _allWikiEntries = MutableStateFlow<List<TagWikiEntity>>(emptyList())
    val allWikiEntries = combine(_allWikiEntries, settingsManager.tagDomainFlow) { entries, domain ->
        entries.filter { it.domain == domain }
    }

    private val _mutedTags = MutableStateFlow<Set<String>>(emptySet())
    val mutedTags = _mutedTags.asStateFlow()

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch { loadWiki() }
    }

    private suspend fun loadWiki() = mutex.withLock {
        if (file.exists()) {
            try {
                val json = file.readText()
                val backup = backupAdapter.fromJson(json) ?: TagWikiBackup(emptyList())
                _allWikiEntries.value = backup.wikiEntries
                _mutedTags.value = backup.mutedTags.toSet()
            } catch (_: Exception) {
                _allWikiEntries.value = emptyList()
                _mutedTags.value = emptySet()
            }
        }
    }

    suspend fun saveWikiEntry(tagName: String, description: String, aliases: List<String>, references: List<WikiReference>, count: Long) {
        val currentDomain = settingsManager.tagDomainFlow.first()
        val newEntry = TagWikiEntity(
            tagName = tagName.lowercase().trim(),
            domain = currentDomain,
            description = description.trim(),
            aliases = aliases.map { it.trim() }.filter { it.isNotEmpty() },
            references = references.filter { it.siteName.isNotBlank() || it.url.isNotBlank() },
            count = count
        )

        mutex.withLock {
            val currentList = _allWikiEntries.value.toMutableList()
            currentList.removeAll { it.tagName == newEntry.tagName && it.domain == currentDomain }
            currentList.add(newEntry)
            currentList.sortBy { it.tagName }
            updateStateAndSaveUnsafe(entries = currentList)
        }
    }

    suspend fun deleteWikiEntry(tagName: String) {
        val currentDomain = settingsManager.tagDomainFlow.first()
        mutex.withLock {
            val currentList = _allWikiEntries.value.toMutableList()
            currentList.removeAll { it.tagName == tagName && it.domain == currentDomain }
            updateStateAndSaveUnsafe(entries = currentList)
        }
    }

    suspend fun toggleMuteTag(tagName: String) {
        mutex.withLock {
            val currentMuted = _mutedTags.value.toMutableSet()
            if (currentMuted.contains(tagName)) {
                currentMuted.remove(tagName)
            } else {
                currentMuted.add(tagName)
            }
            updateStateAndSaveUnsafe(muted = currentMuted)
        }
    }

    suspend fun setMutedTags(tags: Set<String>) {
        mutex.withLock {
            updateStateAndSaveUnsafe(muted = tags)
        }
    }

    suspend fun syncTagsFromServer(rawApiTags: List<TagSuggestion>) {
        val currentDomain = settingsManager.tagDomainFlow.first()
        val apiTags = rawApiTags.distinctBy { it.value }
        val incomingTagsMap = apiTags.associateBy { it.value }

        mutex.withLock {
            val currentList = _allWikiEntries.value.toMutableList()
            var changed = false

            for (i in currentList.indices) {
                val existing = currentList[i]
                if (existing.domain != currentDomain) continue

                val incoming = incomingTagsMap[existing.tagName]

                val updatedAliases = if (existing.tagName.startsWith("system:") && existing.aliases.isEmpty()) {
                    listOf(existing.tagName.removePrefix("system:"))
                } else {
                    existing.aliases
                }

                if (incoming != null) {
                    val newCount = incoming.count.toLong()
                    if (existing.count != newCount || existing.aliases != updatedAliases) {
                        currentList[i] = existing.copy(count = newCount, aliases = updatedAliases)
                        changed = true
                    }
                } else {
                    if (existing.count != 0L || existing.aliases != updatedAliases) {
                        currentList[i] = existing.copy(count = 0L, aliases = updatedAliases)
                        changed = true
                    }
                }
            }

            val existingNamesInDomain = currentList.filter { it.domain == currentDomain }.map { it.tagName }.toSet()
            for (apiTag in apiTags) {
                if (!existingNamesInDomain.contains(apiTag.value)) {
                    val defaultAliases = if (apiTag.value.startsWith("system:")) {
                        listOf(apiTag.value.removePrefix("system:"))
                    } else {
                        emptyList()
                    }

                    currentList.add(TagWikiEntity(
                        tagName = apiTag.value,
                        domain = currentDomain,
                        count = apiTag.count.toLong(),
                        aliases = defaultAliases
                    ))
                    changed = true
                }
            }

            if (changed || currentList.size != _allWikiEntries.value.size) {
                currentList.sortBy { it.tagName }
                updateStateAndSaveUnsafe(entries = currentList)
            }
        }
    }

    suspend fun restoreWikiFromJson(json: String, merge: Boolean): Boolean {
        return try {
            val importedBackup = backupAdapter.fromJson(json) ?: return false

            mutex.withLock {
                val currentList = if (merge) _allWikiEntries.value.toMutableList() else mutableListOf()
                val currentMuted = if (merge) _mutedTags.value.toMutableSet() else mutableSetOf()

                if (merge) {
                    importedBackup.wikiEntries.forEach { newEntry ->
                        val existingIndex = currentList.indexOfFirst { it.tagName == newEntry.tagName && it.domain == newEntry.domain }
                        if (existingIndex != -1) {
                            currentList[existingIndex] = newEntry.copy(count = currentList[existingIndex].count)
                        } else {
                            currentList.add(newEntry.copy(count = 0L))
                        }
                    }
                    currentMuted.addAll(importedBackup.mutedTags)
                } else {
                    currentList.addAll(importedBackup.wikiEntries.map { it.copy(count = 0L) })
                    currentMuted.addAll(importedBackup.mutedTags)
                }

                currentList.sortBy { it.tagName }
                updateStateAndSaveUnsafe(entries = currentList, muted = currentMuted)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun updateExistingTagCounts(apiTags: List<TagSuggestion>) {
        val currentDomain = settingsManager.tagDomainFlow.first()
        mutex.withLock {
            val currentList = _allWikiEntries.value.toMutableList()
            var changed = false

            for (apiTag in apiTags) {
                val existingIndex = currentList.indexOfFirst { it.tagName == apiTag.value && it.domain == currentDomain }
                if (existingIndex != -1) {
                    val existing = currentList[existingIndex]
                    val newCount = apiTag.count.toLong()

                    if (existing.count != newCount) {
                        currentList[existingIndex] = existing.copy(count = newCount)
                        changed = true
                    }
                }
            }

            if (changed) {
                updateStateAndSaveUnsafe(entries = currentList)
            }
        }
    }

    private fun updateStateAndSaveUnsafe(
        entries: List<TagWikiEntity> = _allWikiEntries.value,
        muted: Set<String> = _mutedTags.value
    ) {
        _allWikiEntries.value = entries
        _mutedTags.value = muted

        val backup = TagWikiBackup(wikiEntries = entries, mutedTags = muted.toList())
        file.writeText(backupAdapter.toJson(backup))
    }
}