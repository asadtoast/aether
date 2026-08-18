package com.asadtoast.aether.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
data class ParsedTag(
    val name: String,
    val namespace: String,
    var isSelected: Boolean = true
)

@JsonClass(generateAdapter = true)
data class DanbooruPost(
    @Json(name = "tag_string_character") val tagStringCharacter: String?,
    @Json(name = "tag_string_copyright") val tagStringCopyright: String?,
    @Json(name = "tag_string_artist") val tagStringArtist: String?,
    @Json(name = "tag_string_general") val tagStringGeneral: String?,
    @Json(name = "tag_string_meta") val tagStringMeta: String?
)

@JsonClass(generateAdapter = true)
data class PawchiveHashSearchResponse(
    val posts: List<PawchivePost>?
)

@JsonClass(generateAdapter = true)
data class PawchivePost(
    val user: String?,
    val service: String?,
    val title: String?
)

enum class ScraperSource {
    DANBOORU,
    PAWCHIVE
}