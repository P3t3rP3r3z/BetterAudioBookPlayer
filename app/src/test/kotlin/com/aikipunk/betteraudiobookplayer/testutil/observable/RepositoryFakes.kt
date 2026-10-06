package com.aikipunk.betteraudiobookplayer.testutil.observable

// Fakes whose flows update as data changes; used by the ViewModel and ShelfBackup tests.
// (testutil/TestFakes.kt has call-recording fakes for the playback tests.)

import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import com.aikipunk.betteraudiobookplayer.domain.repository.AudiobookRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.BookmarkRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.PlaybackStateRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.TrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

class FakeAudiobookRepository(initial: List<Audiobook> = emptyList()) : AudiobookRepository {
    val books = MutableStateFlow(initial)
    val updates = mutableListOf<Audiobook>()
    override fun getAllAudiobooksFlow() = books
    override suspend fun getAudiobookById(id: String) = books.value.firstOrNull { it.id == id }
    override suspend fun addAudiobook(audiobook: Audiobook) { books.value = books.value + audiobook }
    override suspend fun updateAudiobook(audiobook: Audiobook) {
        updates += audiobook
        books.value = books.value.filterNot { it.id == audiobook.id } + audiobook
    }
    override suspend fun deleteAudiobook(id: String) { books.value = books.value.filterNot { it.id == id } }
}

class FakeTrackRepository(initial: List<Track> = emptyList()) : TrackRepository {
    val tracks = MutableStateFlow(initial)
    override fun getTracksForAudiobookFlow(audiobookId: String) = tracks.map { list -> list.filter { it.audiobookId == audiobookId } }
    override suspend fun getTracksForAudiobook(audiobookId: String) = tracks.value.filter { it.audiobookId == audiobookId }
    override fun getAllTracksFlow() = tracks
    override suspend fun getTrackById(id: String) = tracks.value.firstOrNull { it.id == id }
    override suspend fun addTracks(tracks: List<Track>) { this.tracks.value = this.tracks.value + tracks }
    override suspend fun addTrack(track: Track) { tracks.value = tracks.value + track }
    override suspend fun deleteTracksForAudiobook(audiobookId: String) { tracks.value = tracks.value.filterNot { it.audiobookId == audiobookId } }
}

class FakePlaybackStateRepository(initial: List<PlaybackState> = emptyList()) : PlaybackStateRepository {
    val states = MutableStateFlow(initial)
    override fun getPlaybackStateFlow(audiobookId: String) = states.map { list -> list.firstOrNull { it.audiobookId == audiobookId } }
    override suspend fun getPlaybackState(audiobookId: String) = states.value.firstOrNull { it.audiobookId == audiobookId }
    override fun getAllPlaybackStatesFlow() = states
    override suspend fun savePlaybackState(playbackState: PlaybackState) {
        states.value = states.value.filterNot { it.audiobookId == playbackState.audiobookId } + playbackState
    }
    override suspend fun getMostRecentPlaybackState() = states.value.maxByOrNull { it.lastPlayedAt }
    override suspend fun updatePosition(audiobookId: String, positionMs: Long) {
        states.value = states.value.map { if (it.audiobookId == audiobookId) it.copy(positionMs = positionMs) else it }
    }
    override suspend fun updatePlaybackState(audiobookId: String, positionMs: Long, speed: Float, isPlaying: Boolean) {
        val current = getPlaybackState(audiobookId) ?: PlaybackState(audiobookId, "", lastPlayedAt = LocalDateTime.now())
        savePlaybackState(current.copy(positionMs = positionMs, speed = speed, isPlaying = isPlaying))
    }
    override suspend fun deletePlaybackState(audiobookId: String) { states.value = states.value.filterNot { it.audiobookId == audiobookId } }
}

class FakeBookmarkRepository(initial: List<Bookmark> = emptyList()) : BookmarkRepository {
    val bookmarks = MutableStateFlow(initial)
    override fun getBookmarksForAudiobookFlow(audiobookId: String) = bookmarks.map { list -> list.filter { it.audiobookId == audiobookId } }
    override fun getAllBookmarksFlow() = bookmarks
    override suspend fun getBookmarksForAudiobook(audiobookId: String) = bookmarks.value.filter { it.audiobookId == audiobookId }
    override suspend fun getBookmarkById(id: String) = bookmarks.value.firstOrNull { it.id == id }
    override suspend fun addBookmark(bookmark: Bookmark) { bookmarks.value = bookmarks.value + bookmark }
    override suspend fun updateBookmark(bookmark: Bookmark) { bookmarks.value = bookmarks.value.map { if (it.id == bookmark.id) bookmark else it } }
    override suspend fun deleteBookmark(id: String) { bookmarks.value = bookmarks.value.filterNot { it.id == id } }
    override suspend fun deleteBookmarksForAudiobook(audiobookId: String) { bookmarks.value = bookmarks.value.filterNot { it.audiobookId == audiobookId } }
}
