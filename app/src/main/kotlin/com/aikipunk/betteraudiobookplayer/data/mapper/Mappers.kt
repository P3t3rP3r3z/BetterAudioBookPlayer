package com.aikipunk.betteraudiobookplayer.data.mapper

import com.aikipunk.betteraudiobookplayer.data.entity.AudiobookEntity
import com.aikipunk.betteraudiobookplayer.data.entity.BookmarkEntity
import com.aikipunk.betteraudiobookplayer.data.entity.PlaybackStateEntity
import com.aikipunk.betteraudiobookplayer.data.entity.TrackEntity
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import com.aikipunk.betteraudiobookplayer.domain.model.TrackMetadata

// Audiobook mappers
fun AudiobookEntity.toDomain(): Audiobook = Audiobook(
    id = id,
    title = title,
    author = author,
    duration = duration,
    coverArtPath = coverArtPath,
    filePath = filePath,
    createdAt = createdAt,
    totalTracks = totalTracks,
    notes = notes,
    customTitle = customTitle,
    narrator = narrator,
    publisher = publisher,
    publishedYear = publishedYear,
)

fun Audiobook.toEntity(): AudiobookEntity = AudiobookEntity(
    id = id,
    title = title,
    author = author,
    duration = duration,
    coverArtPath = coverArtPath,
    filePath = filePath,
    createdAt = createdAt,
    totalTracks = totalTracks,
    notes = notes,
    customTitle = customTitle,
    narrator = narrator,
    publisher = publisher,
    publishedYear = publishedYear,
)

// Track mappers
fun TrackEntity.toDomain(): Track = Track(
    id = id,
    audiobookId = audiobookId,
    filePath = filePath,
    title = title,
    duration = duration,
    trackNumber = trackNumber,
    metadata = TrackMetadata(
        artist = artist,
        album = album,
        year = year,
        genre = genre,
        bitrate = bitrate,
    ),
)

fun Track.toEntity(): TrackEntity = TrackEntity(
    id = id,
    audiobookId = audiobookId,
    filePath = filePath,
    title = title,
    duration = duration,
    trackNumber = trackNumber,
    artist = metadata.artist,
    album = metadata.album,
    year = metadata.year,
    genre = metadata.genre,
    bitrate = metadata.bitrate,
)

// PlaybackState mappers
fun PlaybackStateEntity.toDomain(): PlaybackState = PlaybackState(
    audiobookId = audiobookId,
    currentTrackId = currentTrackId,
    positionMs = positionMs,
    speed = speed,
    isPlaying = isPlaying,
    lastPlayedAt = lastPlayedAt,
)

fun PlaybackState.toEntity(): PlaybackStateEntity = PlaybackStateEntity(
    audiobookId = audiobookId,
    currentTrackId = currentTrackId,
    positionMs = positionMs,
    speed = speed,
    isPlaying = isPlaying,
    lastPlayedAt = lastPlayedAt,
)

// Bookmark mappers
fun BookmarkEntity.toDomain(): Bookmark = Bookmark(
    id = id,
    audiobookId = audiobookId,
    trackId = trackId,
    positionMs = positionMs,
    title = title,
    description = description,
    createdAt = createdAt,
)

fun Bookmark.toEntity(): BookmarkEntity = BookmarkEntity(
    id = id,
    audiobookId = audiobookId,
    trackId = trackId,
    positionMs = positionMs,
    title = title,
    description = description,
    createdAt = createdAt,
)
