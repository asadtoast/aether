package com.asadtoast.aether.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VerifyAccessResponse(
    @param:Json(name = "human_description") val humanDescription: String,
    @param:Json(name = "basic_permissions") val basicPermissions: List<Int>
)