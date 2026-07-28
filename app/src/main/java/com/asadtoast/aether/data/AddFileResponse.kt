package com.asadtoast.aether.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AddFileResponse(
    val status: Int,
    val hash: String
)