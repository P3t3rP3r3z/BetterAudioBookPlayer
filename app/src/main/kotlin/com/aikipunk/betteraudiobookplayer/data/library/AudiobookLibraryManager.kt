package com.aikipunk.betteraudiobookplayer.data.library

import com.aikipunk.betteraudiobookplayer.data.filediscovery.FolderAudiobookImporter
import com.aikipunk.betteraudiobookplayer.domain.audio.AudioPlayer
import com.aikipunk.betteraudiobookplayer.domain.library.BookDetails
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.playback.PlaybackProgressTracker
import com.aikipunk.betteraudiobookplayer.domain.repository.AudiobookRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.BookmarkRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.PlaybackStateRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.TrackRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Edits and removes library books; shared by the bookshelf and the player's book details. */
@Singleton
class AudiobookLibraryManager @Inject constructor(
    private val audiobookRepository: AudiobookRepository,
    private val trackRepository: TrackRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val playbackStateRepository: PlaybackStateRepository,
    private val progressTracker: PlaybackProgressTracker,
    private val audioPlayer: AudioPlayer,
    private val folderImporter: FolderAudiobookImporter,
) {
    /**
     * Saves the user's edits; see [BookDetails.applyTo]. Bookmarks still named after the old title
     * follow the rename; bookmarks the user named themselves are left alone.
     */
    suspend fun updateDetails(audiobook: Audiobook, details: BookDetails) {
        val updated = details.applyTo(audiobook)
        audiobookRepository.updateAudiobook(updated)
        val oldTitle = audiobook.displayTitle
        val newTitle = updated.displayTitle
        if (oldTitle == newTitle) return
        bookmarkRepository.getBookmarksForAudiobook(audiobook.id)
            .filter { it.title == oldTitle }
            .forEach { bookmarkRepository.updateBookmark(it.copy(title = newTitle)) }
    }

    /** Moves the saved position to the end of the last track. */
    suspend fun markFinished(audiobook: Audiobook) {
        val tracks = trackRepository.getTracksForAudiobook(audiobook.id).sortedBy { it.trackNumber }
        val last = tracks.lastOrNull() ?: return
        if (isLoaded(audiobook)) {
            audioPlayer.pause()
            audioPlayer.seekToItem(tracks.lastIndex, last.duration)
            progressTracker.saveNow()
            return
        }
        val saved = playbackStateRepository.getPlaybackState(audiobook.id)
        playbackStateRepository.savePlaybackState(
            PlaybackState(
                audiobookId = audiobook.id,
                currentTrackId = last.id,
                positionMs = last.duration,
                speed = saved?.speed ?: 1.0f,
                isPlaying = false,
                lastPlayedAt = LocalDateTime.now(),
            ),
        )
    }

    /** Clears the saved position so the book starts again from the beginning. */
    suspend fun resetProgress(audiobook: Audiobook) {
        if (isLoaded(audiobook)) {
            audioPlayer.pause()
            audioPlayer.seekToItem(0, 0)
            progressTracker.saveNow()
            return
        }
        playbackStateRepository.deletePlaybackState(audiobook.id)
    }

    // The tracker keeps saving the player's position, so a loaded book has to be moved in the player itself
    private fun isLoaded(audiobook: Audiobook): Boolean =
        progressTracker.currentAudiobookId() == audiobook.id && audioPlayer.currentPlaylistKey() == audiobook.id

    /** Removes the book from the library only; the audio files themselves are left alone. */
    suspend fun remove(audiobook: Audiobook) {
        if (progressTracker.currentAudiobookId() == audiobook.id) {
            progressTracker.clearSession(audiobook.id)
            audioPlayer.stop()
        }
        bookmarkRepository.deleteBookmarksForAudiobook(audiobook.id)
        playbackStateRepository.deletePlaybackState(audiobook.id)
        trackRepository.deleteTracksForAudiobook(audiobook.id)
        audiobookRepository.deleteAudiobook(audiobook.id)
        val remaining = audiobookRepository.getAllAudiobooksFlow().first().map { it.filePath }
        folderImporter.releaseBook(audiobook, remaining)
    }
}
