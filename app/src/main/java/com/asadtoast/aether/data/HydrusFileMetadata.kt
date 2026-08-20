package com.asadtoast.aether.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HydrusTagService(
    val name: String,
    val display_tags: Map<String, List<String>>? = null
)

// real date decoding
@JsonClass(generateAdapter = true)
data class HydrusFileServiceDetail(
    val time_imported: Long? = null
)

@JsonClass(generateAdapter = true)
data class HydrusFileServices(
    val current: Map<String, HydrusFileServiceDetail>? = null
)


@JsonClass(generateAdapter = true)
data class HydrusFileMetadata(
    val file_id: Long,
    val mime: String = "",
    val tags: Map<String, HydrusTagService>? = null,
    val size: Long? = null,
    val width: Long? = null,
    val height: Long? = null,

    @param:Json(name = "imported_time")
    val importedTime: Long? = null,

    // block mapping
    @param:Json(name = "file_services")
    val fileServices: HydrusFileServices? = null,

    val duration: Double? = null,
    val num_frames: Int? = null,
    val hash: String? = null,
    val known_urls: List<String>? = null,
    val alternates: List<String>? = null,

    val is_inbox: Boolean? = false,
    val is_trashed: Boolean? = false
) {

    // smart time calculator
    val realImportedTime: Long
        get() = fileServices?.current?.values?.firstOrNull()?.time_imported ?: importedTime ?: 0L
}

@JsonClass(generateAdapter = true)
data class FileMetadataResponse(
    val metadata: List<HydrusFileMetadata>
)