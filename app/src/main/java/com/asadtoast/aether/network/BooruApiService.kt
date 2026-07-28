package com.asadtoast.aether.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class DanbooruPost(
    @param:Json(name = "tag_string_artist") val tagStringArtist: String? = "",
    @param:Json(name = "tag_string_character") val tagStringCharacter: String? = "",
    @param:Json(name = "tag_string_copyright") val tagStringCopyright: String? = "",
    @param:Json(name = "tag_string_general") val tagStringGeneral: String? = "",
    @param:Json(name = "tag_string_meta") val tagStringMeta: String? = ""
)

interface BooruApiService {
    @GET("posts.json")
    suspend fun getPostByMd5(
        @Query("tags") tags: String // expected format: "md5:{hash}"
    ): List<DanbooruPost>
}