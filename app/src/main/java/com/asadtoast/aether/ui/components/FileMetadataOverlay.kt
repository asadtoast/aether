package com.asadtoast.aether.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun FileMetadataOverlay(
    metadata: com.asadtoast.aether.data.HydrusFileMetadata,
    tagCount: Int = 0,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val fps = if (metadata.duration != null && metadata.duration > 0 && metadata.num_frames != null && metadata.num_frames > 0) {
        metadata.num_frames.toDouble() / (metadata.duration / 1000.0)
    } else null

    FileMetadataOverlay(
        size = metadata.size ?: 0L,
        width = metadata.width?.toInt() ?: 0,
        height = metadata.height?.toInt() ?: 0,
        duration = metadata.duration?.toDouble(),
        framerate = fps,
        tagCount = tagCount,
        ext = metadata.mime?.substringAfterLast('/'),
        modifier = modifier
    )
}

@Composable
fun FileMetadataOverlay(
    size: Long,
    width: Int,
    height: Int,
    duration: Double? = null,
    framerate: Double? = null,
    tagCount: Int = 0,
    ext: String? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val formattedSize = when {
        size >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", size / (1024f * 1024f * 1024f))
        size >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f MB", size / (1024f * 1024f))
        size >= 1024 -> String.format(java.util.Locale.US, "%.2f KB", size / 1024f)
        else -> "$size Bytes"
    }

    // res and filetype info
    val baseRes = if (width > 0 && height > 0) "${width}x${height}" else "Unknown Res"
    val resolutionText = if (!ext.isNullOrEmpty()) "$baseRes • ${ext.lowercase()}" else baseRes

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(text = formattedSize, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Text(text = resolutionText, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        Text(text = "$tagCount tags", color = Color(0xFFFFD54F), style = MaterialTheme.typography.bodySmall)

        if (duration != null && duration > 0.0) {
            val secondsDouble = if (duration > 500.0) duration / 1000.0 else duration
            val formattedDuration = if (secondsDouble < 1.0) {
                String.format(java.util.Locale.US, "%.2fs", secondsDouble)
            } else {
                val totalSeconds = kotlin.math.round(secondsDouble).toInt()
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                String.format(java.util.Locale.US, "%02d:%02d min", minutes, seconds)
            }
            Text(
                text = formattedDuration,
                color = Color.Cyan,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (framerate != null && framerate > 0.0) {
            val formattedFps = if (framerate % 1.0 == 0.0) "${framerate.toInt()} FPS" else String.format(java.util.Locale.US, "%.2f FPS", framerate)
            Text(
                text = formattedFps,
                color = Color.Cyan,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}