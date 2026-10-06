package com.aikipunk.betteraudiobookplayer.domain.library

import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.Year

class LibraryDomainTest {

    @Test
    fun `BookProgress computes listened duration from ordered tracks and clamps to book duration`() {
        val audiobook = book(duration = 9_000)
        val tracks = listOf(track("t2", 2, 6_000), track("t1", 1, 4_000))
        val state = PlaybackState(audiobook.id, "t2", positionMs = 8_000, lastPlayedAt = TIME)

        val progress = BookProgress.of(audiobook, tracks, state)

        assertEquals(10_000, progress.durationMs)
        assertEquals(10_000, progress.listenedMs)
        assertEquals(0, progress.remainingMs)
        assertEquals(1f, progress.fraction, 0.0001f)
        assertEquals(BookStatus.FINISHED, progress.status)
    }

    @Test
    fun `BookProgress uses audiobook duration when tracks have no duration and handles missing state`() {
        val progress = BookProgress.of(book(duration = 3_000), listOf(track("t1", 1, 0)), null)

        assertEquals(3_000, progress.durationMs)
        assertEquals(0, progress.listenedMs)
        assertEquals(BookStatus.NOT_STARTED, progress.status)
        assertNull(progress.lastPlayedAt)
    }

    @Test
    fun `BookProgress status distinguishes not started in progress and finished`() {
        assertEquals(BookStatus.NOT_STARTED, BookProgress(999, 10_000, null).status)
        assertEquals(BookStatus.IN_PROGRESS, BookProgress(1_000, 40_000, null).status)
        assertEquals(BookStatus.FINISHED, BookProgress(10_000, 40_000, null).status)
    }

    @Test
    fun `BookDetails parseYear validates plausible publication years`() {
        assertEquals(2019, BookDetails.parseYear("2019-05-14"))
        assertEquals(Year.now().value + 1, BookDetails.parseYear((Year.now().value + 1).toString()))
        assertNull(BookDetails.parseYear("999"))
        assertNull(BookDetails.parseYear("0999"))
        assertNull(BookDetails.parseYear((Year.now().value + 2).toString()))
        assertTrue(BookDetails(title = "Title", year = "").isYearValid)
        assertFalse(BookDetails(title = "Title", year = "3020").isYearValid)
    }

    @Test
    fun `BookDetails applyTo trims fields and clears unchanged title`() {
        val original = book(title = "Original", customTitle = "Existing")
        val updated = BookDetails(
            title = " Original ",
            author = " Synthetic Author ",
            narrator = " ",
            publisher = " Synthetic Publisher ",
            year = "2020",
            notes = " Notes ",
        ).applyTo(original)

        assertNull(updated.customTitle)
        assertEquals("Synthetic Author", updated.author)
        assertNull(updated.narrator)
        assertEquals("Synthetic Publisher", updated.publisher)
        assertEquals(2020, updated.publishedYear)
        assertEquals("Notes", updated.notes)
    }

    @Test
    fun `BookDetails of copies editable values from audiobook display data`() {
        val details = BookDetails.of(book(customTitle = "Custom", publishedYear = 2021, notes = "Note"))

        assertEquals("Custom", details.title)
        assertEquals("Synthetic Author", details.author)
        assertEquals("Synthetic Narrator", details.narrator)
        assertEquals("Synthetic Publisher", details.publisher)
        assertEquals("2021", details.year)
        assertEquals("Note", details.notes)
    }

    @Test
    fun `LibraryShelf matches search terms across title author narrator publisher and year`() {
        val audiobook = book(
            title = "The Space Road",
            author = "Synthetic Author",
            narrator = "Voice Narrator",
            publisher = "Fiction House",
            publishedYear = 2020,
        )

        assertTrue(LibraryShelf.matches(audiobook, "space author"))
        assertTrue(LibraryShelf.matches(audiobook, "voice fiction 2020"))
        assertFalse(LibraryShelf.matches(audiobook, "missing"))
    }

    @Test
    fun `LibraryShelf arrange filters and sorts books by requested strategy`() {
        val oldTime = TIME.minusDays(2)
        val recent = shelfBook(book(id = "recent", title = "A Recent", author = "Zed"), BookProgress(2_000, 10_000, TIME))
        val finished = shelfBook(book(id = "finished", title = "The Finished", author = null), BookProgress(10_000, 10_000, oldTime))
        val notStarted = shelfBook(book(id = "new", title = "An Alpha", author = "Alpha"), BookProgress(0, 10_000, null))

        assertEquals(listOf(recent), LibraryShelf.arrange(listOf(finished, notStarted, recent), "zed", LibraryFilter.ALL, LibrarySort.AUTHOR))
        assertEquals(listOf(notStarted), LibraryShelf.arrange(listOf(finished, notStarted, recent), "", LibraryFilter.NOT_STARTED, LibrarySort.TITLE))
        assertEquals(listOf(finished, recent), LibraryShelf.arrange(listOf(recent, finished), "", LibraryFilter.ALL, LibrarySort.PROGRESS))
    }

    @Test
    fun `LibraryShelf continueListening returns most recently played in-progress book`() {
        val older = shelfBook(book(id = "older"), BookProgress(2_000, 100_000, TIME.minusDays(1)))
        val newer = shelfBook(book(id = "newer"), BookProgress(3_000, 100_000, TIME))
        val finished = shelfBook(book(id = "finished"), BookProgress(10_000, 10_000, TIME.plusDays(1)))

        assertEquals(newer, LibraryShelf.continueListening(listOf(older, newer, finished)))
    }

    private fun shelfBook(audiobook: Audiobook, progress: BookProgress) = ShelfBook(audiobook, progress)

    private fun book(
        id: String = "book",
        title: String = "Title",
        author: String? = "Synthetic Author",
        duration: Long = 1_000,
        customTitle: String? = null,
        narrator: String? = "Synthetic Narrator",
        publisher: String? = "Synthetic Publisher",
        publishedYear: Int? = 2020,
        notes: String? = null,
    ) = Audiobook(
        id = id,
        title = title,
        author = author,
        duration = duration,
        coverArtPath = null,
        filePath = "/synthetic/$id",
        createdAt = TIME.minusHours(id.length.toLong()),
        totalTracks = 1,
        notes = notes,
        customTitle = customTitle,
        narrator = narrator,
        publisher = publisher,
        publishedYear = publishedYear,
    )

    private fun track(id: String, number: Int, duration: Long) = Track(id, "book", "/synthetic/$id.mp3", id, duration, number)

    private companion object {
        val TIME: LocalDateTime = LocalDateTime.of(2026, 9, 29, 12, 0)
    }
}
