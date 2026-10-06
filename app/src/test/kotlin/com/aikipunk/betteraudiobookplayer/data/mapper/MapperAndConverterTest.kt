package com.aikipunk.betteraudiobookplayer.data.mapper

import com.aikipunk.betteraudiobookplayer.data.db.LocalDateTimeConverter
import com.aikipunk.betteraudiobookplayer.data.entity.AudiobookEntity
import com.aikipunk.betteraudiobookplayer.data.entity.BookmarkEntity
import com.aikipunk.betteraudiobookplayer.data.entity.PlaybackStateEntity
import com.aikipunk.betteraudiobookplayer.data.entity.TrackEntity
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import com.aikipunk.betteraudiobookplayer.domain.model.TrackMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class MapperAndConverterTest {

    @Test
    fun `audiobook mapper preserves editable metadata in both directions`() {
        val domain = audiobook()
        val entity = domain.toEntity()
        val roundTrip = entity.toDomain()

        assertEquals(domain, roundTrip)
        assertEquals("Custom", entity.customTitle)
        assertEquals(2020, entity.publishedYear)
    }

    @Test
    fun `track mapper preserves persisted metadata in both directions`() {
        val domain = Track(
            id = "track",
            audiobookId = "book",
            filePath = "/synthetic/track.mp3",
            title = "Track",
            duration = 1_000,
            trackNumber = 2,
            metadata = TrackMetadata(artist = "Artist", album = "Album", year = "2020", genre = "Speech", bitrate = 128),
        )

        val entity = domain.toEntity()
        val roundTrip = entity.toDomain()

        assertEquals(domain, roundTrip)
        assertEquals("Artist", entity.artist)
        assertEquals(128, roundTrip.metadata.bitrate)
    }

    @Test
    fun `playback and bookmark mappers preserve all values`() {
        val playback = PlaybackState("book", "track", 1_234, 1.25f, true, TIME)
        val bookmark = Bookmark("bookmark", "book", "track", 2_345, "Title", "Description", TIME)

        assertEquals(playback, playback.toEntity().toDomain())
        assertEquals(bookmark, bookmark.toEntity().toDomain())
        assertEquals(PlaybackStateEntity("book", "track", 1_234, 1.25f, true, TIME), playback.toEntity())
        assertEquals(BookmarkEntity("bookmark", "book", "track", 2_345, "Title", "Description", TIME), bookmark.toEntity())
    }

    @Test
    fun `LocalDateTimeConverter formats ISO local timestamps and handles nulls`() {
        val converter = LocalDateTimeConverter()

        assertEquals("2026-09-29T12:30:45", converter.fromLocalDateTime(LocalDateTime.of(2026, 9, 29, 12, 30, 45)))
        assertEquals(LocalDateTime.of(2026, 9, 29, 12, 30, 45), converter.toLocalDateTime("2026-09-29T12:30:45"))
        assertNull(converter.fromLocalDateTime(null))
        assertNull(converter.toLocalDateTime(null))
    }

    @Test
    fun `entity defaults keep optional metadata empty`() {
        val entity = AudiobookEntity("book", "Title", null, 1_000, null, "/synthetic/book")
        val track = TrackEntity("track", "book", "/synthetic/track.mp3", "Track", 1_000, 1)

        assertNull(entity.notes)
        assertEquals(0, entity.totalTracks)
        assertNull(track.album)
        assertNull(track.bitrate)
    }

    private fun audiobook() = Audiobook(
        id = "book",
        title = "Title",
        author = "Author",
        duration = 1_000,
        coverArtPath = "cover",
        filePath = "/synthetic/book",
        createdAt = TIME,
        totalTracks = 2,
        notes = "Notes",
        customTitle = "Custom",
        narrator = "Narrator",
        publisher = "Publisher",
        publishedYear = 2020,
    )

    private companion object {
        val TIME: LocalDateTime = LocalDateTime.of(2026, 9, 29, 12, 0)
    }
}
