package com.aikipunk.betteraudiobookplayer.testutil.observable

// Fakes whose flows update as data changes; used by the ViewModel and ShelfBackup tests.
// (testutil/TestFakes.kt has call-recording fakes for the playback tests.)

import com.aikipunk.betteraudiobookplayer.domain.audio.AudioPlayer
import com.aikipunk.betteraudiobookplayer.domain.audio.NowPlayingInfo
import com.aikipunk.betteraudiobookplayer.domain.audio.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.audio.PlaylistItem
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAudioPlayer : AudioPlayer {
    override val playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val currentPosition = MutableStateFlow(0L)
    override val playbackSpeed = MutableStateFlow(1.0f)
    override val duration = MutableStateFlow(0L)
    override val error = MutableStateFlow<String?>(null)
    override val currentItemIndex = MutableStateFlow(0)

    var playlistKey: String? = null
    var source: String? = null
    var playing = false
    val seeks = mutableListOf<Long>()
    val itemSeeks = mutableListOf<Pair<Int, Long>>()
    val loadedPlaylists = mutableListOf<LoadedPlaylist>()
    val nowPlayingUpdates = mutableListOf<List<NowPlayingInfo>>()
    var playerVolume = 1f

    override suspend fun loadAudio(filePath: String, positionMs: Long, nowPlaying: NowPlayingInfo?) {
        source = filePath
        currentPosition.value = positionMs
    }

    override suspend fun loadPlaylist(key: String, items: List<PlaylistItem>, startIndex: Int, positionMs: Long) {
        playlistKey = key
        currentItemIndex.value = startIndex
        currentPosition.value = positionMs
        loadedPlaylists += LoadedPlaylist(key, items, startIndex, positionMs)
    }

    override suspend fun updateNowPlaying(items: List<NowPlayingInfo>) { nowPlayingUpdates += items }
    override suspend fun seekToItem(index: Int, positionMs: Long) {
        currentItemIndex.value = index
        currentPosition.value = positionMs
        itemSeeks += index to positionMs
    }
    override fun currentPlaylistKey() = playlistKey
    override suspend fun play() { playing = true; playbackState.value = PlaybackState.PLAYING }
    override suspend fun pause() { playing = false; playbackState.value = PlaybackState.PAUSED }
    override suspend fun stop() { playing = false; playbackState.value = PlaybackState.IDLE }
    override suspend fun seekTo(positionMs: Long) { currentPosition.value = positionMs; seeks += positionMs }
    override suspend fun setSpeed(speed: Float) { playbackSpeed.value = speed }
    override fun setVolume(volume: Float) { this.playerVolume = volume }
    override fun isPlaying() = playing
    override fun currentSource() = source
    override fun getCurrentPositionMs() = currentPosition.value
    override fun getCurrentSpeed() = playbackSpeed.value
    override fun release() = Unit
}

data class LoadedPlaylist(
    val key: String,
    val items: List<PlaylistItem>,
    val startIndex: Int,
    val positionMs: Long,
)
