package com.aikipunk.betteraudiobookplayer.domain.playback

import com.aikipunk.betteraudiobookplayer.domain.audio.AudioPlayer
import com.aikipunk.betteraudiobookplayer.domain.audio.PlaybackState as AudioPlaybackState
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.repository.PlaybackStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-lifetime owner of "where am I in which book". Persists the position periodically while
 * playing and whenever playback stops, independent of any screen's lifecycle.
 */
@Singleton
class PlaybackProgressTracker @Inject constructor(
    private val audioPlayer: AudioPlayer,
    private val playbackStateRepository: PlaybackStateRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var audiobookId: String? = null
    private var trackIds: List<String> = emptyList()
    private var lastSavedAtMs = 0L
    private var lastSavedPositionMs = -1L

    /** Wall-clock time playback last stopped; null if it hasn't been paused since loading. */
    var pausedAtMs: Long? = null
        private set

    init {
        scope.launch {
            var wasPlaying = false
            audioPlayer.playbackState.collect { state ->
                val playing = state == AudioPlaybackState.PLAYING
                if (wasPlaying && !playing) {
                    pausedAtMs = System.currentTimeMillis()
                    saveNow()
                } else if (playing && !wasPlaying) {
                    saveNow()
                }
                wasPlaying = playing
            }
        }
        scope.launch {
            audioPlayer.currentPosition.collect { position ->
                val now = System.currentTimeMillis()
                val shouldSave = if (audioPlayer.isPlaying()) {
                    now - lastSavedAtMs >= SAVE_INTERVAL_MS
                } else {
                    // Paused position only changes on a seek; persist it right away
                    kotlin.math.abs(position - lastSavedPositionMs) >= 500
                }
                if (shouldSave) saveNow()
            }
        }
        scope.launch { audioPlayer.currentItemIndex.collect { saveNow() } }
        scope.launch { audioPlayer.playbackSpeed.collect { saveNow() } }
    }

    /**
     * Points the tracker at a book (chapters in playlist order). Saves the previous book first so switching books
     * never loses its position. [lastPlayedAt] seeds the pause time after an app restart.
     */
    fun setSession(audiobookId: String, trackIds: List<String>, lastPlayedAt: LocalDateTime?) {
        val isNewBook = this.audiobookId != audiobookId
        if (isNewBook) saveNow()
        this.audiobookId = audiobookId
        this.trackIds = trackIds
        if (isNewBook) {
            pausedAtMs = lastPlayedAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        }
    }

    fun clearSession(audiobookId: String) {
        if (this.audiobookId == audiobookId) {
            this.audiobookId = null
            this.trackIds = emptyList()
            pausedAtMs = null
        }
    }

    fun currentAudiobookId(): String? = audiobookId

    /** Chapter track ids of the current book, in playlist order. */
    fun currentTrackIds(): List<String> = trackIds

    fun pausedForMs(): Long? = pausedAtMs?.let { System.currentTimeMillis() - it }

    fun saveNow() {
        val bookId = audiobookId ?: return
        // Until the player has actually loaded this book, its position belongs elsewhere
        if (audioPlayer.currentPlaylistKey() != bookId) return
        val chapterId = trackIds.getOrNull(audioPlayer.currentItemIndex.value) ?: return
        val position = audioPlayer.currentPosition.value
        val speed = audioPlayer.playbackSpeed.value
        val playing = audioPlayer.isPlaying()
        lastSavedAtMs = System.currentTimeMillis()
        lastSavedPositionMs = position
        scope.launch {
            playbackStateRepository.savePlaybackState(
                PlaybackState(
                    audiobookId = bookId,
                    currentTrackId = chapterId,
                    positionMs = position,
                    speed = speed,
                    isPlaying = playing,
                    lastPlayedAt = LocalDateTime.now(),
                ),
            )
        }
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 10_000L
    }
}
