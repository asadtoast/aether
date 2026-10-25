package com.asadtoast.aether.utils

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object ExoPlayerPool {
    var isMuted: Boolean = true

    @Synchronized
    fun getPlayer(context: Context, apiKey: String): ExoPlayer {
        val appContext = context.applicationContext

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("HydrusClient/ExoPlayer")
            .setDefaultRequestProperties(mapOf("Hydrus-Client-API-Access-Key" to apiKey))
            .setAllowCrossProtocolRedirects(true)

        // constant bitrate search for files with no Cues
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()

        val sourceFactory = DefaultMediaSourceFactory(appContext, extractorsFactory)
            .setDataSourceFactory(httpFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                2_500,  // minBufferMs
                15_000, // maxBufferMs
                1_000,  // bufferForPlaybackMs
                2_000   // bufferForPlaybackAfterRebufferMs
            )
            .setTargetBufferBytes(16 * 1024 * 1024) // Límite estricto de 16MB en RAM por reproductor
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(appContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        return ExoPlayer.Builder(appContext)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(sourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                setAudioAttributes(audioAttributes, false)
                volume = if (isMuted) 0f else 1f
                repeatMode = Player.REPEAT_MODE_ONE
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
    }

    @Synchronized
    fun releasePlayer(player: ExoPlayer) {
        player.clearVideoSurface()
        player.release()
    }
}