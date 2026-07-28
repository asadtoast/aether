package com.asadtoast.aether.ui.theme

import androidx.compose.ui.graphics.Color

// tag colors
fun getNamespaceColor(tag: String): Color {
    val namespace = if (tag.contains(":")) tag.substringBefore(":") else ""
    return when (namespace) {
        // soft red
        "creator" -> Color(0xFFE57373)
        "artist" -> Color(0xFFE57373)
        "studio" -> Color(0xFFE57373)
        // soft purple
        "series" -> Color(0xFFBA68C8)
        "copyright" -> Color(0xFFBA68C8)
        // soft green
        "character" -> Color(0xFF81C784)
        "person" -> Color(0xFF81C784)
        // soft yellow
        "system" -> Color(0xFFFFB74D)
        "meta" -> Color(0xFFFFB74D)

        // default soft blue
        else -> Color(0xFF64B5F6)
    }
}