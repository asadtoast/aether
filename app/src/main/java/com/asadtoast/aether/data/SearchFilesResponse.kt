package com.asadtoast.aether.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchFilesResponse(
    val file_ids: List<Long> = emptyList()
)