package com.aikipunk.betteraudiobookplayer.domain.repository

import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface AudiobookRepository {
    fun getAllAudiobooksFlow(): Flow<List<Audiobook>>
    suspend fun getAudiobookById(id: String): Audiobook?
    suspend fun addAudiobook(audiobook: Audiobook)
    suspend fun updateAudiobook(audiobook: Audiobook)
    suspend fun deleteAudiobook(id: String)
}

interface TrackRepository {
    fun getTracksForAudiobookFlow(audiobookId: String): Flow<List<Track>>
    suspend fun getTracksForAudiobook(audiobookId: String): List<Track>
    fun getAllTracksFlow(): Flow<List<Track>>
    suspend fun getTrackById(id: String): Track?
    suspend fun addTracks(tracks: List<Track>)
    suspend fun addTrack(track: Track)
    suspend fun deleteTracksForAudiobook(audiobookId: String)
}

interface PlaybackStateRepository {
    fun getPlaybackStateFlow(audiobookId: String): Flow<PlaybackState?>
    suspend fun getPlaybackState(audiobookId: String): PlaybackState?
    fun getAllPlaybackStatesFlow(): Flow<List<PlaybackState>>
    /** Inserts or replaces the saved state for the book. */
    suspend fun savePlaybackState(playbackState: PlaybackState)
    suspend fun getMostRecentPlaybackState(): PlaybackState?
    suspend fun updatePosition(audiobookId: String, positionMs: Long)
    suspend fun updatePlaybackState(audiobookId: String, positionMs: Long, speed: Float, isPlaying: Boolean)
    suspend fun deletePlaybackState(audiobookId: String)
}

interface BookmarkRepository {
    fun getBookmarksForAudiobookFlow(audiobookId: String): Flow<List<Bookmark>>
    fun getAllBookmarksFlow(): Flow<List<Bookmark>>
    suspend fun getBookmarksForAudiobook(audiobookId: String): List<Bookmark>
    suspend fun getBookmarkById(id: String): Bookmark?
    suspend fun addBookmark(bookmark: Bookmark)
    suspend fun updateBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(id: String)
    suspend fun deleteBookmarksForAudiobook(audiobookId: String)
}
