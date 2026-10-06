package com.aikipunk.betteraudiobookplayer.data.audio

import androidx.core.net.toUri
import android.content.Context
import android.media.AudioDeviceInfo
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.aikipunk.betteraudiobookplayer.domain.audio.AudioPlayer
import com.aikipunk.betteraudiobookplayer.domain.audio.NowPlayingInfo
import com.aikipunk.betteraudiobookplayer.domain.audio.PlaylistItem
import com.aikipunk.betteraudiobookplayer.domain.audio.PlaybackState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.aikipunk.betteraudiobookplayer.data.preferences.UserPreferences
import javax.inject.Singleton

@Singleton
@UnstableApi
class ExoPlayerAdapter @Inject constructor(
    @ApplicationContext context: Context,
    userPreferences: UserPreferences,
) : AudioPlayer {

    private val context = context

    private val exoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            true,
        )
        // Keep the CPU awake while playing with the screen off (sleep timer relies on it)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .setSeekBackIncrementMs(SEEK_BACK_MS)
        .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
        .build()

    /** The underlying player, shared with the MediaSession so system controls drive the same audio. */
    val player: Player get() = exoPlayer

    private val scope = CoroutineScope(Dispatchers.Main)
    private val volumeFader = VolumeFadeController(
        scope = scope,
        nowMs = { SystemClock.elapsedRealtime() },
        sleep = { delay(it) },
        applyVolume = ::applyPlayerVolume,
    )
    @Volatile
    private var appVolumeRatio = DEFAULT_APP_VOLUME_RATIO
    @Volatile
    private var gradualVolumeFadeEnabled = false

    init {
        // Optionally pause when headphones are unplugged or Bluetooth disconnects
        scope.launch {
            userPreferences.pauseOnHeadphoneDisconnect.collect { exoPlayer.setHandleAudioBecomingNoisy(it) }
        }
        scope.launch {
            userPreferences.appVolume.collect { percent ->
                appVolumeRatio = percent / PERCENT_SCALE
                if (!volumeFader.isRunning) applyPlayerVolume(appVolumeRatio)
            }
        }
        scope.launch {
            userPreferences.gradualVolumeFade.collect { enabled ->
                gradualVolumeFadeEnabled = enabled
                if (!enabled && volumeFader.isRunning) {
                    volumeFader.cancel()
                    applyPlayerVolume(appVolumeRatio)
                }
            }
        }
    }

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.IDLE)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    @Volatile
    private var loadedSources: List<String> = emptyList()

    @Volatile
    private var playlistKey: String? = null
    @Volatile
    private var playlistRequestId = 0L

    private val _currentItemIndex = MutableStateFlow(0)
    override val currentItemIndex: StateFlow<Int> = _currentItemIndex.asStateFlow()

    init {
        setupPlayerListener()
    }

    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _playbackState.value = when (playbackState) {
                    Player.STATE_IDLE -> PlaybackState.IDLE
                    Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                    Player.STATE_READY -> {
                        if (exoPlayer.isPlaying) PlaybackState.PLAYING else PlaybackState.READY
                    }
                    Player.STATE_ENDED -> PlaybackState.ENDED
                    else -> PlaybackState.IDLE
                }

                _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                _currentPosition.value = exoPlayer.currentPosition.coerceAtLeast(0L)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = if (isPlaying) PlaybackState.PLAYING else {
                    when (exoPlayer.playbackState) {
                        Player.STATE_ENDED -> PlaybackState.ENDED
                        else -> PlaybackState.PAUSED
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentItemIndex.value = exoPlayer.currentMediaItemIndex
                _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                _currentPosition.value = exoPlayer.currentPosition.coerceAtLeast(0L)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                // Keeps the UI in sync after seeks, even while paused
                _currentPosition.value = newPosition.positionMs.coerceAtLeast(0L)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _playbackState.value = PlaybackState.ERROR
                _error.value = error.message ?: "Unknown playback error"
            }
        })

        // Update position periodically (every 500ms) when playing
        scope.launch {
            while (true) {
                if (exoPlayer.isPlaying) {
                    _currentPosition.value = exoPlayer.currentPosition.coerceAtLeast(0L)
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    override suspend fun loadAudio(filePath: String, positionMs: Long, nowPlaying: NowPlayingInfo?) {
        setPlaylist(null, listOf(PlaylistItem(filePath, nowPlaying)), 0, positionMs)
    }

    override suspend fun loadPlaylist(key: String, items: List<PlaylistItem>, startIndex: Int, positionMs: Long) {
        setPlaylist(key, items, startIndex, positionMs)
    }

    private fun setPlaylist(key: String?, items: List<PlaylistItem>, startIndex: Int, positionMs: Long) {
        val requestId = ++playlistRequestId
        // Claim the key now: loading finishes asynchronously, and anyone opening the same book
        // meanwhile (e.g. another screen reacting to it) must not pause and reload it
        playlistKey = key
        scope.launch {
            try {
                val artwork = loadArtwork(items.mapNotNull { it.nowPlaying?.artworkUri })
                if (requestId != playlistRequestId) return@launch
                val mediaItems = items.map { item ->
                    MediaItem.Builder()
                        .setUri(item.source.toPlayableUri())
                        .setMediaMetadata(item.nowPlaying.toMediaMetadata(artwork))
                        .build()
                }
                val index = startIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0))
                exoPlayer.setMediaItems(mediaItems, index, positionMs.coerceAtLeast(0L))
                exoPlayer.prepare()
                exoPlayer.seekTo(index, positionMs.coerceAtLeast(0L))
                if (requestId != playlistRequestId) return@launch
                loadedSources = items.map { it.source }
                playlistKey = key
                _currentItemIndex.value = index
                _currentPosition.value = positionMs.coerceAtLeast(0L)
                _error.value = null
            } catch (e: Exception) {
                if (requestId != playlistRequestId) return@launch
                playlistKey = null
                _playbackState.value = PlaybackState.ERROR
                _error.value = "Failed to load audio: ${e.message}"
            }
        }
    }

    // Imported books store content:// URIs; legacy scanned books store plain file paths
    private fun String.toPlayableUri(): android.net.Uri =
        if (contains("://")) this.toUri() else android.net.Uri.fromFile(java.io.File(this))

    override suspend fun seekToItem(index: Int, positionMs: Long) {
        scope.launch {
            if (index !in 0 until exoPlayer.mediaItemCount) return@launch
            val target = positionMs.coerceAtLeast(0L)
            exoPlayer.seekTo(index, target)
            _currentItemIndex.value = index
            _currentPosition.value = target
        }
    }

    override fun currentPlaylistKey(): String? = playlistKey

    override suspend fun play() {
        scope.launch {
            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                exoPlayer.prepare()
            }
            exoPlayer.play()
        }
    }

    override suspend fun pause() {
        scope.launch {
            exoPlayer.pause()
        }
    }

    override suspend fun stop() {
        playlistRequestId++
        loadedSources = emptyList()
        playlistKey = null
        _currentItemIndex.value = 0
        _currentPosition.value = 0L
        _duration.value = 0L
        scope.launch {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        scope.launch {
            val target = positionMs.coerceAtLeast(0L)
            exoPlayer.seekTo(target)
            _currentPosition.value = target
        }
    }

    override suspend fun setSpeed(speed: Float) {
        scope.launch {
            val clampedSpeed = speed.coerceIn(0.5f, 2.0f)
            exoPlayer.setPlaybackSpeed(clampedSpeed)
            _playbackSpeed.value = clampedSpeed
        }
    }

    override fun setVolume(volume: Float) {
        volumeFader.cancel()
        scope.launch { applyPlayerVolume(volume) }
    }

    override fun startVolumeFade(durationMs: Long) {
        volumeFader.start(
            durationMs = durationMs,
            enabled = gradualVolumeFadeEnabled,
            currentVolume = exoPlayer.volume,
            targetVolume = { appVolumeRatio },
        )
    }

    /** Route playback to [device], or null to follow the system's default output. */
    fun setPreferredAudioDevice(device: AudioDeviceInfo?) {
        scope.launch { exoPlayer.setPreferredAudioDevice(device) }
    }

    override fun isPlaying(): Boolean = exoPlayer.isPlaying

    override fun currentSource(): String? = loadedSources.getOrNull(_currentItemIndex.value)

    override fun getCurrentPositionMs(): Long = exoPlayer.currentPosition.coerceAtLeast(0L)

    override fun getCurrentSpeed(): Float = exoPlayer.playbackParameters.speed

    override fun release() {
        playlistRequestId++
        volumeFader.cancel()
        loadedSources = emptyList()
        playlistKey = null
        exoPlayer.release()
    }

    override suspend fun updateNowPlaying(items: List<NowPlayingInfo>) {
        scope.launch {
            val artwork = loadArtwork(items.mapNotNull { it.artworkUri })
            val count = minOf(items.size, exoPlayer.mediaItemCount)
            for (i in 0 until count) {
                val current = exoPlayer.getMediaItemAt(i)
                // Same URI, new metadata: ExoPlayer updates in place without re-buffering
                exoPlayer.replaceMediaItem(i, current.buildUpon().setMediaMetadata(items[i].toMediaMetadata(artwork)).build())
            }
        }
    }

    /**
     * Cover art is embedded as bytes because System UI can't open the app's SAF (content://) cover URIs.
     * Keyed by URI; a book's chapters share one cover.
     */
    private suspend fun loadArtwork(uris: List<String>): Map<String, ByteArray> =
        withContext(Dispatchers.IO) {
            uris.filter { it.isNotBlank() }.distinct().mapNotNull { uri ->
                (artworkCache[uri] ?: CoverArtwork.encode(context, uri.toPlayableUri())?.also { artworkCache[uri] = it })
                    ?.let { uri to it }
            }.toMap()
        }

    private val artworkCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    private fun NowPlayingInfo?.toMediaMetadata(artwork: Map<String, ByteArray>): MediaMetadata {
        if (this == null) return MediaMetadata.EMPTY
        val builder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
        val bytes = artworkUri?.let { artwork[it] }
        if (bytes != null) {
            builder.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        } else {
            builder.setArtworkUri(artworkUri?.takeIf { it.isNotBlank() }?.toPlayableUri())
        }
        return builder.build()
    }

    private fun applyPlayerVolume(volume: Float) {
        exoPlayer.volume = volume.coerceIn(MIN_VOLUME_RATIO, MAX_VOLUME_RATIO)
    }

    companion object {
        const val SEEK_BACK_MS = 10_000L
        const val SEEK_FORWARD_MS = 30_000L
        private const val DEFAULT_APP_VOLUME_RATIO = 1.0f
        private const val MIN_VOLUME_RATIO = 0f
        private const val MAX_VOLUME_RATIO = 1.5f
        private const val PERCENT_SCALE = 100f
    }
}
