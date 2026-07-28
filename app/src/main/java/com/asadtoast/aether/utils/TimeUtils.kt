package com.asadtoast.aether.utils

import java.util.Locale

fun formatDuration(durationMs: Double): String {
    val totalSeconds = (durationMs / 1000).toLong()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        String.format(Locale.US, "%d:%02d:%02d", hours, remainingMinutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}