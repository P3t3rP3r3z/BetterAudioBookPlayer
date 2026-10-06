package com.aikipunk.betteraudiobookplayer.domain.model

import java.time.LocalDateTime

// Audiobook represents a book with multiple tracks
data class Audiobook(
    val id: String,
    val title: String,
    val author: String?,
    val duration: Long, // milliseconds
    val coverArtPath: String?,
    val filePath: String, // directory containing audio files
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val totalTracks: Int = 0,
    val notes: String? = null,
    /** User's preferred title; the file-derived [title] is kept so it can be restored. */
    val customTitle: String? = null,
    val narrator: String? = null,
    val publisher: String? = null,
    val publishedYear: Int? = null,
) {
    val displayTitle: String get() = customTitle?.takeIf { it.isNotBlank() } ?: title
}

// Track represents a single audio file
data class Track(
    val id: String,
    val audiobookId: String,
    val filePath: String,
    val title: String,
    val duration: Long, // milliseconds
    val trackNumber: Int,
    val metadata: TrackMetadata = TrackMetadata(),

)

// Track metadata (artist, album, etc.)
data class TrackMetadata(
    val artist: String? = null,
    val author: String? = null,
    val album: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val bitrate: Int? = null, // kbps
)

// PlaybackState tracks current playback position and settings
data class PlaybackState(
    val audiobookId: String,
    val currentTrackId: String,
    val positionMs: Long = 0,
    val speed: Float = 1.0f, // playback speed multiplier
    val isPlaying: Boolean = false,
    val lastPlayedAt: LocalDateTime = LocalDateTime.now(),
)

// Bookmark allows marking important sections
data class Bookmark(
    val id: String,
    val audiobookId: String,
    val trackId: String,
    val positionMs: Long,
    val title: String,
    val description: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
