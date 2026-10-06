package com.aikipunk.betteraudiobookplayer.domain.playback

import com.aikipunk.betteraudiobookplayer.data.preferences.UserPreferences
import com.aikipunk.betteraudiobookplayer.domain.audio.AudioPlayer
import com.aikipunk.betteraudiobookplayer.domain.audio.NowPlayingInfo
import com.aikipunk.betteraudiobookplayer.domain.audio.PlaylistItem
import com.aikipunk.betteraudiobookplayer.domain.library.BookProgress
import com.aikipunk.betteraudiobookplayer.domain.library.BookStatus
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import com.aikipunk.betteraudiobookplayer.domain.repository.AudiobookRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.PlaybackStateRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.TrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A book loaded into the player (or already playing), with where it starts. */
data class OpenedBook(
    val audiobook: Audiobook?,
    val tracks: List<Track>,
    /** Saved progress; reset to the start when a finished book is reopened. */
    val savedState: PlaybackState?,
    val startTrack: Track?,
    val wasAlreadyLoaded: Boolean,
)

/**
 * Loads a book into the shared player at its saved position. Used by the in-app player
 * and by system browsers (Android Auto) so both open books the same way.
 */
@Singleton
class BookLauncher @Inject constructor(
    private val audioPlayer: AudioPlayer,
    private val audiobookRepository: AudiobookRepository,
    private val trackRepository: TrackRepository,
    private val playbackStateRepository: PlaybackStateRepository,
    private val progressTracker: PlaybackProgressTracker,
    private val userPreferences: UserPreferences,
) {
    private val _openedBookId = MutableStateFlow<String?>(null)

    /** The book most recently opened in the player, from any entry point. */
    val openedBookId: StateFlow<String?> = _openedBookId.asStateFlow()

    suspend fun open(audiobookId: String, initialTrackId: String? = null): OpenedBook {
        val audiobook = audiobookRepository.getAudiobookById(audiobookId)
        val tracks = trackRepository.getTracksForAudiobook(audiobookId)
        val stored = playbackStateRepository.getPlaybackState(audiobookId)
        val alreadyLoaded = audioPlayer.currentPlaylistKey() == audiobookId
        // A finished book opens at the start again instead of at its last second
        val finished = !alreadyLoaded && audiobook != null && stored != null &&
            BookProgress.of(audiobook, tracks, stored).status == BookStatus.FINISHED
        val saved = if (finished) stored.copy(currentTrackId = tracks.firstOrNull()?.id.orEmpty(), positionMs = 0) else stored

        val startTrack = saved?.let { s -> tracks.find { it.id == s.currentTrackId } }
            ?: initialTrackId?.let { id -> tracks.find { it.id == id } }
            ?: tracks.firstOrNull()

        if (startTrack != null) {
            progressTracker.setSession(audiobookId, tracks.map { it.id }, saved?.lastPlayedAt)
            if (!alreadyLoaded) {
                // Prepare paused so duration/position are real before Play is pressed
                if (audioPlayer.isPlaying()) audioPlayer.pause()
                val startAt = if (saved?.currentTrackId == startTrack.id) saved.positionMs else 0L
                load(audiobookId, audiobook, tracks, tracks.indexOf(startTrack), startAt)
                audioPlayer.setSpeed(saved?.speed ?: userPreferences.defaultSpeed.value)
            }
            _openedBookId.value = audiobookId
        }
        return OpenedBook(audiobook, tracks, saved, startTrack, alreadyLoaded)
    }

    suspend fun load(audiobookId: String, audiobook: Audiobook?, tracks: List<Track>, startIndex: Int, positionMs: Long) {
        audioPlayer.loadPlaylist(
            key = audiobookId,
            items = tracks.map { PlaylistItem(it.filePath, nowPlaying(audiobook, tracks, it)) },
            startIndex = startIndex,
            positionMs = positionMs,
        )
        _openedBookId.value = audiobookId
    }

    /** The most recently played book, for resuming playback from system controls. */
    suspend fun mostRecentBookId(): String? = playbackStateRepository.getMostRecentPlaybackState()?.audiobookId

    companion object {
        fun nowPlaying(book: Audiobook?, tracks: List<Track>, track: Track): NowPlayingInfo {
            val hasChapters = tracks.size > 1
            return NowPlayingInfo(
                title = if (hasChapters || book == null) track.title else book.displayTitle,
                artist = book?.author ?: track.metadata.author,
                album = book?.displayTitle,
                artworkUri = book?.coverArtPath,
            )
        }
    }
}
