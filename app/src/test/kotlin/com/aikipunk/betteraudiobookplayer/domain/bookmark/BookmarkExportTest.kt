package com.aikipunk.betteraudiobookplayer.domain.bookmark

import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BookmarkExportTest {

    @Test
    fun `location includes chapter number for multi track books and only time for single track books`() {
        val tracks = listOf(track("t1", 1, "Opening"), track("t2", 2, "Middle"))
        val bookmark = bookmark(trackId = "t2", positionMs = 3_723_000)

        assertEquals("Ch. 2 • 01:02:03", BookmarkExport.location(bookmark, tracks))
        assertEquals("01:02:03", BookmarkExport.location(bookmark, listOf(tracks.first())))
        assertEquals("01:02:03", BookmarkExport.location(bookmark.copy(trackId = "missing"), tracks))
    }

    @Test
    fun `shareText formats title book location and trimmed description`() {
        val text = BookmarkExport.shareText(
            "Synthetic Book",
            bookmark(title = "Important", description = "  Remember this  "),
            listOf(track("t1", 1, "Opening")),
        )

        assertEquals("Important\nSynthetic Book — 00:12\n\nRemember this", text)
    }

    @Test
    fun `markdown groups sorted bookmarks by chapter with quoted multiline descriptions`() {
        val tracks = listOf(track("t1", 1, "Opening"), track("t2", 2, "Middle"))
        val markdown = BookmarkExport.markdown(
            bookTitle = "Synthetic Book",
            author = "Synthetic Author",
            bookmarks = listOf(
                bookmark(id = "b2", trackId = "t2", positionMs = 2_000, title = "Second"),
                bookmark(id = "b1", trackId = "t1", positionMs = 1_000, title = "First", description = "line one\nline two"),
            ),
            tracks = tracks,
        )

        assertTrue(markdown.startsWith("# Synthetic Book — Bookmarks\n\n_Synthetic Author_"))
        assertTrue(markdown.contains("## Chapter 1: Opening\n\n- **00:01** First\n  > line one\n  > line two"))
        assertTrue(markdown.contains("## Chapter 2: Middle\n\n- **00:02** Second"))
    }

    @Test
    fun `formatTime omits hours until needed`() {
        assertEquals("00:05", BookmarkExport.formatTime(5_000))
        assertEquals("01:01", BookmarkExport.formatTime(61_000))
        assertEquals("01:00:01", BookmarkExport.formatTime(3_601_000))
    }

    private fun bookmark(
        id: String = "bookmark",
        trackId: String = "t1",
        positionMs: Long = 12_000,
        title: String = "Title",
        description: String? = null,
    ) = Bookmark(id, "book", trackId, positionMs, title, description, LocalDateTime.of(2026, 9, 29, 12, 0))

    private fun track(id: String, number: Int, title: String) = Track(id, "book", "/synthetic/$id.mp3", title, 60_000, number)
}
