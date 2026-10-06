package com.aikipunk.betteraudiobookplayer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ModelValueTest {

    @Test
    fun `Audiobook displayTitle falls back to file title when custom title is blank`() {
        val base = audiobook(customTitle = null)

        assertEquals("File Title", base.displayTitle)
        assertEquals("File Title", base.copy(customTitle = "   ").displayTitle)
        assertEquals("Custom Title", base.copy(customTitle = "Custom Title").displayTitle)
    }

    @Test
    fun `Track metadata defaults are empty and audio output carries selected kind`() {
        val track = Track("track", "book", "/synthetic/track.mp3", "Track", 1_000, 1)
        val output = AudioOutput(7, "Headphones", AudioOutput.Kind.BLUETOOTH)

        assertEquals(TrackMetadata(), track.metadata)
        assertEquals(AudioOutput.Kind.BLUETOOTH, output.kind)
        assertEquals("Headphones", output.name)
    }

    @Test
    fun `PlaybackState and Bookmark preserve supplied timestamps and values`() {
        val time = LocalDateTime.of(2026, 9, 29, 12, 0)
        val state = PlaybackState("book", "track", 1_234, 1.5f, true, time)
        val bookmark = Bookmark("mark", "book", "track", 2_345, "Note", "Description", time)

        assertEquals(time, state.lastPlayedAt)
        assertEquals(1.5f, state.speed, 0.0001f)
        assertEquals("Description", bookmark.description)
        assertEquals(2_345, bookmark.positionMs)
    }

    private fun audiobook(customTitle: String?) = Audiobook(
        id = "book",
        title = "File Title",
        author = null,
        duration = 1_000,
        coverArtPath = null,
        filePath = "/synthetic/book",
        customTitle = customTitle,
    )
}
