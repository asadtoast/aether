package com.asadtoast.aether.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchTagsResponse(
    val tags: List<TagSuggestion>
)

@JsonClass(generateAdapter = true)
data class TagSuggestion(
    val value: String,
    val count: Float
)