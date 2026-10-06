package com.aikipunk.betteraudiobookplayer.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "audiobooks")
data class AudiobookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val duration: Long, // milliseconds
    val coverArtPath: String?,
    val filePath: String, // directory containing audio files
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val totalTracks: Int = 0,
    val notes: String? = null,
    val customTitle: String? = null,
    val narrator: String? = null,
    val publisher: String? = null,
    val publishedYear: Int? = null,
)

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val audiobookId: String,
    val filePath: String,
    val title: String,
    val duration: Long, // milliseconds
    val trackNumber: Int,
    val artist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val bitrate: Int? = null, // kbps
)

@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val audiobookId: String,
    val currentTrackId: String,
    val positionMs: Long = 0,
    val speed: Float = 1.0f,
    val isPlaying: Boolean = false,
    val lastPlayedAt: LocalDateTime = LocalDateTime.now(),
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val audiobookId: String,
    val trackId: String,
    val positionMs: Long,
    val title: String,
    val description: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
