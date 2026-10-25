package com.asadtoast.aether.utils

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object ExoPlayerPool : KoinComponent {
    private val okHttpClient: OkHttpClient by inject()
    var isMuted: Boolean = true

    @Synchronized
    fun getPlayer(context: Context, apiKey: String): ExoPlayer {
        val appContext = context.applicationContext

        val httpFactory = OkHttpDataSource.Factory(okHttpClient).apply {
            setUserAgent("HydrusClient/ExoPlayer")
            setDefaultRequestProperties(mapOf("Hydrus-Client-API-Access-Key" to apiKey))
        }

        // constant bitrate search for files with no Cues
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()

        val sourceFactory = DefaultMediaSourceFactory(appContext, extractorsFactory)
            .setDataSourceFactory(httpFactory)

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
        player.release()
    }
}