package com.asadtoast.aether.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AddTagsRequest(
    val file_id: Long? = null,
    val file_ids: List<Long>? = null,
    val hash: String? = null,
    val hashes: List<String>? = null,
    val service_names_to_tags: Map<String, List<String>>? = null,
    val service_names_to_actions_to_tags: Map<String, Map<String, List<String>>>? = null
)