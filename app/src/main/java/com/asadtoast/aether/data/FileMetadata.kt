package com.asadtoast.aether.data

import androidx.compose.runtime.Immutable

@Immutable
data class FileMetadata(
    val id: Long,
    val thumbnailUrl: String,
    val fileUrl: String = "",
    val mime: String = "",
    val size: Long? = null,
    val importedDate: Long? = null,
    val duration: Double? = null,
    val framerate: Double? = null,
    val hash: String? = null,
    val is_inbox: Boolean? = false,
    val is_trashed: Boolean? = false,
    val width: Int? = null,
    val height: Int? = null,
    val tags: List<String> = emptyList(),
    val knownUrls: List<String> = emptyList()
) {
    val tagCount: Int
        get() = tags.count { !it.startsWith("system:", ignoreCase = true) }
}