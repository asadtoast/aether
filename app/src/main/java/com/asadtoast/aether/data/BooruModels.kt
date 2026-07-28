package com.asadtoast.aether.data

data class ParsedTag(
    val name: String,
    val namespace: String,
    var isSelected: Boolean = true
)