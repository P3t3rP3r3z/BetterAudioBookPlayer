package com.aikipunk.betteraudiobookplayer.testutil

import com.aikipunk.betteraudiobookplayer.domain.audio.AudioPlayer
import com.aikipunk.betteraudiobookplayer.domain.audio.NowPlayingInfo
import com.aikipunk.betteraudiobookplayer.domain.audio.PlaybackState as AudioPlaybackState
import com.aikipunk.betteraudiobookplayer.domain.audio.PlaylistItem
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import com.aikipunk.betteraudiobookplayer.domain.playback.ShakeDetector
import com.aikipunk.betteraudiobookplayer.domain.repository.AudiobookRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.BookmarkRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.PlaybackStateRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.TrackRepository
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAudioPlayer : AudioPlayer {
    private val _playbackState = MutableStateFlow(AudioPlaybackState.IDLE)
    override val playbackState: StateFlow<AudioPlaybackState> = _playbackState
    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition
    private val _playbackSpeed = MutableStateFlow(1f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed
    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration
    private val _error = MutableStateFlow<String?>(null)
    override val error: Flow<String?> = _error
    private val _currentItemIndex = MutableStateFlow(0)
    override val currentItemIndex: StateFlow<Int> = _currentItemIndex

    var playlistKey: String? = null
    var source: String? = null
    var loadCalls = mutableListOf<LoadCall>()
    var playlistCalls = mutableListOf<PlaylistCall>()
    var seekItemCalls = mutableListOf<Pair<Int, Long>>()
    var seekCalls = mutableListOf<Long>()
    var volumes = mutableListOf<Float>()
    var playCalls = 0
    var pauseCalls = 0
    var stopCalls = 0
    var released = false
    var nowPlayingUpdates = emptyList<NowPlayingInfo>()

    data class LoadCall(val filePath: String, val positionMs: Long, val nowPlaying: NowPlayingInfo?)
    data class PlaylistCall(val key: String, val items: List<PlaylistItem>, val startIndex: Int, val positionMs: Long)

    fun setPlaying(playing: Boolean) { _playbackState.value = if (playing) AudioPlaybackState.PLAYING else AudioPlaybackState.PAUSED }
    fun setState(state: AudioPlaybackState) { _playbackState.value = state }
    fun setPosition(positionMs: Long) { _currentPosition.value = positionMs }
    fun setDuration(durationMs: Long) { _duration.value = durationMs }
    fun setItemIndex(index: Int) { _currentItemIndex.value = index }
    fun setCurrentSpeed(speed: Float) { _playbackSpeed.value = speed }

    override suspend fun loadAudio(filePath: String, positionMs: Long, nowPlaying: NowPlayingInfo?) {
        source = filePath
        playlistKey = null
        _currentItemIndex.value = 0
        _currentPosition.value = positionMs
        loadCalls += LoadCall(filePath, positionMs, nowPlaying)
    }

    override suspend fun loadPlaylist(key: String, items: List<PlaylistItem>, startIndex: Int, positionMs: Long) {
        playlistKey = key
        source = items.getOrNull(startIndex)?.source
        _currentItemIndex.value = startIndex
        _currentPosition.value = positionMs
        playlistCalls += PlaylistCall(key, items, startIndex, positionMs)
    }

    override suspend fun updateNowPlaying(items: List<NowPlayingInfo>) { nowPlayingUpdates = items }
    override suspend fun seekToItem(index: Int, positionMs: Long) { _currentItemIndex.value = index; _currentPosition.value = positionMs; seekItemCalls += index to positionMs }
    override fun currentPlaylistKey(): String? = playlistKey
    override suspend fun play() { playCalls++; _playbackState.value = AudioPlaybackState.PLAYING }
    override suspend fun pause() { pauseCalls++; _playbackState.value = AudioPlaybackState.PAUSED }
    override suspend fun stop() { stopCalls++; _playbackState.value = AudioPlaybackState.IDLE }
    override suspend fun seekTo(positionMs: Long) { _currentPosition.value = positionMs; seekCalls += positionMs }
    override suspend fun setSpeed(speed: Float) { _playbackSpeed.value = speed }
    override fun setVolume(volume: Float) { volumes += volume }
    override fun isPlaying(): Boolean = _playbackState.value == AudioPlaybackState.PLAYING
    override fun currentSource(): String? = source
    override fun getCurrentPositionMs(): Long = _currentPosition.value
    override fun getCurrentSpeed(): Float = _playbackSpeed.value
    override fun release() { released = true }
}

class FakeShakeDetector : ShakeDetector {
    private var callback: (() -> Unit)? = null
    var started = 0
    var stopped = 0
    var acknowledged = 0
    val listening: Boolean get() = callback != null
    override fun start(onShake: () -> Unit) { started++; callback = onShake }
    override fun stop() { stopped++; callback = null }
    override fun acknowledge() { acknowledged++ }
    fun shake() { callback?.invoke() }
}

class FakeAudiobookRepository(
    initial: List<Audiobook> = emptyList(),
) : AudiobookRepository {
    private val books = linkedMapOf<String, Audiobook>().apply { initial.forEach { put(it.id, it) } }
    val flow = MutableStateFlow(books.values.toList())
    override fun getAllAudiobooksFlow(): Flow<List<Audiobook>> = flow
    override suspend fun getAudiobookById(id: String): Audiobook? = books[id]
    override suspend fun addAudiobook(audiobook: Audiobook) { books[audiobook.id] = audiobook; flow.value = books.values.toList() }
    override suspend fun updateAudiobook(audiobook: Audiobook) { addAudiobook(audiobook) }
    override suspend fun deleteAudiobook(id: String) { books.remove(id); flow.value = books.values.toList() }
}

class FakeTrackRepository(
    initial: List<Track> = emptyList(),
) : TrackRepository {
    private val tracks = linkedMapOf<String, Track>().apply { initial.forEach { put(it.id, it) } }
    val flow = MutableStateFlow(tracks.values.toList())
    override fun getTracksForAudiobookFlow(audiobookId: String): Flow<List<Track>> = MutableStateFlow(tracks.values.filter { it.audiobookId == audiobookId })
    override suspend fun getTracksForAudiobook(audiobookId: String): List<Track> = tracks.values.filter { it.audiobookId == audiobookId }.sortedBy { it.trackNumber }
    override fun getAllTracksFlow(): Flow<List<Track>> = flow
    override suspend fun getTrackById(id: String): Track? = tracks[id]
    override suspend fun addTracks(tracks: List<Track>) { tracks.forEach { addTrack(it) } }
    override suspend fun addTrack(track: Track) { tracks[track.id] = track; flow.value = this.tracks.values.toList() }
    override suspend fun deleteTracksForAudiobook(audiobookId: String) { tracks.entries.removeIf { it.value.audiobookId == audiobookId }; flow.value = tracks.values.toList() }
}

class FakePlaybackStateRepository(
    initial: List<PlaybackState> = emptyList(),
) : PlaybackStateRepository {
    private val states = linkedMapOf<String, PlaybackState>().apply { initial.forEach { put(it.audiobookId, it) } }
    val flow = MutableStateFlow(states.values.toList())
    val savedStates = mutableListOf<PlaybackState>()
    val positionUpdates = mutableListOf<Pair<String, Long>>()
    val stateUpdates = mutableListOf<StateUpdate>()
    data class StateUpdate(val audiobookId: String, val positionMs: Long, val speed: Float, val isPlaying: Boolean)
    override fun getPlaybackStateFlow(audiobookId: String): Flow<PlaybackState?> = MutableStateFlow(states[audiobookId])
    override suspend fun getPlaybackState(audiobookId: String): PlaybackState? = states[audiobookId]
    override fun getAllPlaybackStatesFlow(): Flow<List<PlaybackState>> = flow
    override suspend fun savePlaybackState(playbackState: PlaybackState) { states[playbackState.audiobookId] = playbackState; savedStates += playbackState; flow.value = states.values.toList() }
    override suspend fun getMostRecentPlaybackState(): PlaybackState? = states.values.maxByOrNull { it.lastPlayedAt }
    override suspend fun updatePosition(audiobookId: String, positionMs: Long) {
        positionUpdates += audiobookId to positionMs
        states[audiobookId]?.let { states[audiobookId] = it.copy(positionMs = positionMs) }
        flow.value = states.values.toList()
    }
    override suspend fun updatePlaybackState(audiobookId: String, positionMs: Long, speed: Float, isPlaying: Boolean) {
        stateUpdates += StateUpdate(audiobookId, positionMs, speed, isPlaying)
        val current = states[audiobookId] ?: PlaybackState(audiobookId, "track", positionMs, speed, isPlaying)
        states[audiobookId] = current.copy(positionMs = positionMs, speed = speed, isPlaying = isPlaying, lastPlayedAt = LocalDateTime.now())
        flow.value = states.values.toList()
    }
    override suspend fun deletePlaybackState(audiobookId: String) { states.remove(audiobookId); flow.value = states.values.toList() }
}

class FakeBookmarkRepository(
    initial: List<Bookmark> = emptyList(),
) : BookmarkRepository {
    private val bookmarks = linkedMapOf<String, Bookmark>().apply { initial.forEach { put(it.id, it) } }
    val added = mutableListOf<Bookmark>()
    override fun getBookmarksForAudiobookFlow(audiobookId: String): Flow<List<Bookmark>> = MutableStateFlow(bookmarks.values.filter { it.audiobookId == audiobookId })
    override fun getAllBookmarksFlow(): Flow<List<Bookmark>> = MutableStateFlow(bookmarks.values.toList())
    override suspend fun getBookmarksForAudiobook(audiobookId: String): List<Bookmark> = bookmarks.values.filter { it.audiobookId == audiobookId }
    override suspend fun getBookmarkById(id: String): Bookmark? = bookmarks[id]
    override suspend fun addBookmark(bookmark: Bookmark) { bookmarks[bookmark.id] = bookmark; added += bookmark }
    override suspend fun updateBookmark(bookmark: Bookmark) { bookmarks[bookmark.id] = bookmark }
    override suspend fun deleteBookmark(id: String) { bookmarks.remove(id) }
    override suspend fun deleteBookmarksForAudiobook(audiobookId: String) { bookmarks.entries.removeIf { it.value.audiobookId == audiobookId } }
}

fun audiobook(id: String, title: String = "Book $id", duration: Long = 120_000L, author: String? = "Author") = Audiobook(
    id = id,
    title = title,
    author = author,
    duration = duration,
    coverArtPath = null,
    filePath = "/books/$id",
    createdAt = LocalDateTime.of(2026, 1, id.filter { it.isDigit() }.ifEmpty { "1" }.take(1).toInt(), 12, 0),
)

fun track(id: String, audiobookId: String = "book1", number: Int = 1, duration: Long = 60_000L, title: String = "Track $id") = Track(
    id = id,
    audiobookId = audiobookId,
    filePath = "/books/$audiobookId/$id.mp3",
    title = title,
    duration = duration,
    trackNumber = number,
)
