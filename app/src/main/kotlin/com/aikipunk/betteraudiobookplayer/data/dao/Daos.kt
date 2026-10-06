package com.aikipunk.betteraudiobookplayer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aikipunk.betteraudiobookplayer.data.entity.AudiobookEntity
import com.aikipunk.betteraudiobookplayer.data.entity.BookmarkEntity
import com.aikipunk.betteraudiobookplayer.data.entity.PlaybackStateEntity
import com.aikipunk.betteraudiobookplayer.data.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudiobookDao {
    @Insert
    suspend fun insert(audiobook: AudiobookEntity)

    @Update
    suspend fun update(audiobook: AudiobookEntity)

    @Delete
    suspend fun delete(audiobook: AudiobookEntity)

    @Query("SELECT * FROM audiobooks WHERE id = :id")
    suspend fun getById(id: String): AudiobookEntity?

    @Query("SELECT * FROM audiobooks ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<AudiobookEntity>>

    @Query("SELECT * FROM audiobooks ORDER BY createdAt DESC")
    suspend fun getAll(): List<AudiobookEntity>
}

@Dao
interface TrackDao {
    @Insert
    suspend fun insert(track: TrackEntity)

    @Insert
    suspend fun insertAll(tracks: List<TrackEntity>)

    @Update
    suspend fun update(track: TrackEntity)

    @Delete
    suspend fun delete(track: TrackEntity)

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE audiobookId = :audiobookId ORDER BY trackNumber ASC")
    suspend fun getTracksForAudiobook(audiobookId: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE audiobookId = :audiobookId ORDER BY trackNumber ASC")
    fun getTracksForAudiobookFlow(audiobookId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY audiobookId, trackNumber ASC")
    fun getAllFlow(): Flow<List<TrackEntity>>

    @Query("DELETE FROM tracks WHERE audiobookId = :audiobookId")
    suspend fun deleteTracksForAudiobook(audiobookId: String)
}

@Dao
interface PlaybackStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playbackState: PlaybackStateEntity)

    @Query("SELECT * FROM playback_state ORDER BY lastPlayedAt DESC LIMIT 1")
    suspend fun getMostRecent(): PlaybackStateEntity?

    @Update
    suspend fun update(playbackState: PlaybackStateEntity)

    @Query("SELECT * FROM playback_state WHERE audiobookId = :audiobookId")
    suspend fun getByAudiobookId(audiobookId: String): PlaybackStateEntity?

    @Query("SELECT * FROM playback_state WHERE audiobookId = :audiobookId")
    fun getByAudiobookIdFlow(audiobookId: String): Flow<PlaybackStateEntity?>

    @Query("UPDATE playback_state SET positionMs = :positionMs, speed = :speed, isPlaying = :isPlaying WHERE audiobookId = :audiobookId")
    suspend fun updatePlaybackState(audiobookId: String, positionMs: Long, speed: Float, isPlaying: Boolean)

    @Query("UPDATE playback_state SET positionMs = :positionMs WHERE audiobookId = :audiobookId")
    suspend fun updatePosition(audiobookId: String, positionMs: Long)

    @Query("SELECT * FROM playback_state")
    fun getAllFlow(): Flow<List<PlaybackStateEntity>>

    @Query("DELETE FROM playback_state WHERE audiobookId = :audiobookId")
    suspend fun deleteByAudiobookId(audiobookId: String)
}

@Dao
interface BookmarkDao {
    @Insert
    suspend fun insert(bookmark: BookmarkEntity)

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun getById(id: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE audiobookId = :audiobookId ORDER BY positionMs ASC")
    suspend fun getBookmarksForAudiobook(audiobookId: String): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE audiobookId = :audiobookId ORDER BY positionMs ASC")
    fun getBookmarksForAudiobookFlow(audiobookId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY positionMs ASC")
    fun getAllBookmarksFlow(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE trackId = :trackId ORDER BY positionMs ASC")
    suspend fun getBookmarksForTrack(trackId: String): List<BookmarkEntity>

    @Query("DELETE FROM bookmarks WHERE audiobookId = :audiobookId")
    suspend fun deleteBookmarksForAudiobook(audiobookId: String)
}
