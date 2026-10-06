package com.aikipunk.betteraudiobookplayer.domain.library

import com.aikipunk.betteraudiobookplayer.testutil.audiobook
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceSearchTest {
    private val frankenstein = shelf(audiobook("book1", title = "Frankenstein", author = "Mary Shelley"), playedDay = 3)
    private val dracula = shelf(audiobook("book2", title = "Dracula", author = "Bram Stoker"), playedDay = 5)
    private val holmes = shelf(audiobook("book3", title = "The Adventures of Sherlock Holmes", author = "Arthur Conan Doyle"), playedDay = null)
    private val books = listOf(frankenstein, dracula, holmes)

    @Test
    fun `blank query resumes the most recently played book`() {
        assertEquals("book2", VoiceSearch.bestMatch(books, "")?.audiobook?.id)
        assertEquals("book2", VoiceSearch.bestMatch(books, "   ")?.audiobook?.id)
    }

    @Test
    fun `exact title matches`() {
        assertEquals("book1", VoiceSearch.bestMatch(books, "Frankenstein")?.audiobook?.id)
        assertEquals("book3", VoiceSearch.bestMatch(books, "sherlock holmes")?.audiobook?.id)
    }

    @Test
    fun `filler words in spoken requests are ignored`() {
        assertEquals("book1", VoiceSearch.bestMatch(books, "Frankenstein by Mary Shelley")?.audiobook?.id)
        assertEquals("book2", VoiceSearch.bestMatch(books, "the audiobook Dracula")?.audiobook?.id)
        assertEquals("book3", VoiceSearch.bestMatch(books, "the adventures of sherlock holmes audiobook")?.audiobook?.id)
    }

    @Test
    fun `author alone matches`() {
        assertEquals("book2", VoiceSearch.bestMatch(books, "bram stoker")?.audiobook?.id)
    }

    @Test
    fun `a mostly matching request still finds the book`() {
        // "Mary Shelley's Frankenstein": the possessive doesn't appear in the book's details
        assertEquals("book1", VoiceSearch.bestMatch(books, "Mary Shelley's Frankenstein")?.audiobook?.id)
    }

    @Test
    fun `unrelated request finds nothing`() {
        assertNull(VoiceSearch.bestMatch(books, "Moby Dick"))
        assertNull(VoiceSearch.bestMatch(books, "the audiobook"))
    }

    @Test
    fun `empty shelf finds nothing`() {
        assertNull(VoiceSearch.bestMatch(emptyList(), "Dracula"))
        assertNull(VoiceSearch.bestMatch(emptyList(), ""))
    }

    @Test
    fun `query is read only from play-from-search intents`() {
        assertEquals("dracula", VoiceSearch.queryFrom(VoiceSearch.ACTION_PLAY_FROM_SEARCH, "dracula"))
        assertEquals("", VoiceSearch.queryFrom(VoiceSearch.ACTION_PLAY_FROM_SEARCH, null))
        assertNull(VoiceSearch.queryFrom("android.intent.action.MAIN", "dracula"))
        assertNull(VoiceSearch.queryFrom(null, null))
    }

    private fun shelf(book: com.aikipunk.betteraudiobookplayer.domain.model.Audiobook, playedDay: Int?) = ShelfBook(
        book,
        BookProgress(
            listenedMs = if (playedDay == null) 0 else 10_000,
            durationMs = book.duration,
            lastPlayedAt = playedDay?.let { LocalDateTime.of(2026, 9, it, 12, 0) },
        ),
    )
}
