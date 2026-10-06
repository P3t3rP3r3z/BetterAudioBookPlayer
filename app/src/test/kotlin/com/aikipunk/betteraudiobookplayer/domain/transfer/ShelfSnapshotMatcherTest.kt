package com.aikipunk.betteraudiobookplayer.domain.transfer

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfSnapshotMatcherTest {

    @Test
    fun `JSON round trip preserves saved shelf data`() {
        val snapshot = ShelfSnapshot(
            savedAt = "2026-09-29T12:00:00",
            books = listOf(savedBook()),
        )

        val decoded = Json.decodeFromString<ShelfSnapshot>(Json.encodeToString(snapshot))

        assertEquals(snapshot, decoded)
        assertEquals(ShelfSnapshot.CURRENT_VERSION, decoded.version)
    }

    @Test
    fun `displayTitle folderName and progressFraction use restored metadata safely`() {
        val book = savedBook(
            customTitle = "  Custom Name  ",
            folderPath = "Audiobooks/Series/Book One/",
            durationMs = 10_000,
            tracks = listOf(
                SavedTrack("01.mp3", "One", 4_000),
                SavedTrack("02.mp3", "Two", 6_000),
            ),
            progress = SavedProgress(trackIndex = 1, positionMs = 1_000, speed = 1.25f, lastPlayedAt = "now"),
        )

        assertEquals("  Custom Name  ", book.displayTitle)
        assertEquals("Book One", book.folderName)
        assertEquals(0.5f, book.progressFraction, 0.0001f)
    }

    @Test
    fun `progressFraction clamps invalid durations and overrun positions`() {
        assertEquals(0f, savedBook(durationMs = 0, progress = SavedProgress(0, 1_000, 1f, "now")).progressFraction)
        assertEquals(
            1f,
            savedBook(
                durationMs = 1_000,
                tracks = listOf(SavedTrack("one.mp3", "One", 1_000)),
                progress = SavedProgress(1, 5_000, 1f, "now"),
            ).progressFraction,
        )
    }

    @Test
    fun `findSaved prefers normalized title or folder name matches`() {
        val target = savedBook(title = "The Hobbit (Unabridged)", customTitle = "Bilbo", folderPath = "Books/Hobbit")
        val imported = ShelfMatcher.ImportedBook(
            title = "the_hobbit_unabridged",
            folderName = null,
            trackFileNames = listOf("different.mp3"),
        )

        assertSame(target, ShelfMatcher.findSaved(imported, listOf(savedBook(title = "Other"), target)))
    }

    @Test
    fun `findSaved uses file overlap when names do not match and rejects ambiguous file matches`() {
        val target = savedBook(
            title = "Saved",
            tracks = listOf(SavedTrack("01.mp3", "One", 1), SavedTrack("02.mp3", "Two", 1)),
        )
        val imported = ShelfMatcher.ImportedBook("Imported", null, listOf("01.mp3", "02.mp3"))

        assertSame(target, ShelfMatcher.findSaved(imported, listOf(target)))
        assertNull(ShelfMatcher.findSaved(imported, listOf(target, target.copy(title = "Duplicate"))))
    }

    @Test
    fun `importedTrackIndex finds same file name before falling back to position`() {
        val saved = savedBook(tracks = listOf(SavedTrack("chapter-two.mp3", "Two", 1)))

        assertEquals(1, ShelfMatcher.importedTrackIndex(saved, 0, listOf("chapter-one.mp3", "Chapter Two.MP3")))
        assertEquals(0, ShelfMatcher.importedTrackIndex(saved.copy(tracks = listOf(SavedTrack(null, "Two", 1))), 0, listOf("other.mp3")))
        assertNull(ShelfMatcher.importedTrackIndex(saved, 2, listOf("chapter-one.mp3")))
    }

    @Test
    fun `candidateFolders ranks name matches before file-only matches`() {
        val book = savedBook(
            title = "Dune",
            folderPath = "Old/Dune",
            tracks = listOf(SavedTrack("01.mp3", "One", 1), SavedTrack("02.mp3", "Two", 1)),
        )
        val files = listOf(
            ShelfMatcher.AudioFile("phone", "/Audiobooks/Unknown", "01.mp3", null),
            ShelfMatcher.AudioFile("phone", "/Audiobooks/Unknown", "02.mp3", null),
            ShelfMatcher.AudioFile("sd", "/Books/Dune", "x.mp3", null),
        )

        val candidates = ShelfMatcher.candidateFolders(book, files)

        assertEquals("/Books/Dune", candidates.first().folderPath)
        assertTrue(candidates.any { it.folderPath == "/Audiobooks/Unknown" && it.matchedFiles == 2 })
    }

    private fun savedBook(
        title: String = "Book",
        customTitle: String? = null,
        folderPath: String? = "Audiobooks/Book",
        durationMs: Long = 1_000,
        tracks: List<SavedTrack> = listOf(SavedTrack("01.mp3", "One", 1_000)),
        progress: SavedProgress? = null,
    ) = SavedBook(
        title = title,
        customTitle = customTitle,
        author = "Synthetic Author",
        narrator = "Synthetic Narrator",
        publisher = "Synthetic Publisher",
        publishedYear = 2020,
        notes = "Synthetic note",
        folderPath = folderPath,
        durationMs = durationMs,
        addedAt = "2026-09-29T12:00:00",
        tracks = tracks,
        progress = progress,
        bookmarks = listOf(SavedBookmark(0, 100, "Mark", "Description", "2026-09-29T12:01:00")),
    )
}
