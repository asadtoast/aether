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
    private val availablePlayers = ArrayDeque<ExoPlayer>()
    private const val MAX_POOL_SIZE = 3
    private var currentApiKey: String? = null
    private val okHttpClient: OkHttpClient by inject()

    @Synchronized
    fun getPlayer(context: Context, apiKey: String): ExoPlayer {
        if (currentApiKey != apiKey) {
            availablePlayers.forEach { it.release() }
            availablePlayers.clear()
            currentApiKey = apiKey
        }

        val player = availablePlayers.removeFirstOrNull()
        return if (player != null) {
            player
        } else {
            val appContext = context.applicationContext
            // new instance if pool is empty
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

            ExoPlayer.Builder(appContext)
                .setRenderersFactory(renderersFactory)
                .setMediaSourceFactory(sourceFactory)
                .build().apply {
                    setAudioAttributes(audioAttributes, false)
                    repeatMode = Player.REPEAT_MODE_ONE
                    trackSelectionParameters = trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                }
        }
    }

    @Synchronized
    fun releasePlayer(player: ExoPlayer) {
        if (player.playerError != null) {
            player.release()
            return
        }

        player.stop()
        player.clearMediaItems()
        player.clearVideoSurface()
        player.playWhenReady = false

        // if pool si not empty, save for recycling, if it is, delete it
        if (availablePlayers.size < MAX_POOL_SIZE) {
            availablePlayers.addLast(player)
        } else {
            player.release()
        }
    }
}