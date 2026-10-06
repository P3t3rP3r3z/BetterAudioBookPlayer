package com.aikipunk.betteraudiobookplayer.domain.bookmark

import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.model.Track

/** Formats bookmarks for sharing and export. */
object BookmarkExport {

    /** "Ch. 3 • 01:02:03", or just the time for single-file books. */
    fun location(bookmark: Bookmark, tracks: List<Track>): String {
        val time = formatTime(bookmark.positionMs)
        if (tracks.size <= 1) return time
        val index = tracks.indexOfFirst { it.id == bookmark.trackId }
        return if (index >= 0) "Ch. ${index + 1} • $time" else time
    }

    /** Plain text for the share sheet. */
    fun shareText(bookTitle: String, bookmark: Bookmark, tracks: List<Track>): String = buildString {
        append(bookmark.title)
        append("\n")
        append("$bookTitle — ${location(bookmark, tracks)}")
        bookmark.description?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(it.trim()) }
    }

    /** Markdown document of every bookmark, grouped by chapter for multi-chapter books. */
    fun markdown(bookTitle: String, author: String?, bookmarks: List<Bookmark>, tracks: List<Track>): String =
        buildString {
            append("# ").append(bookTitle).append(" — Bookmarks\n")
            author?.takeIf { it.isNotBlank() }?.let { append("\n_").append(it).append("_\n") }
            val chapterIndex = tracks.withIndex().associate { (i, t) -> t.id to i }
            val sorted = bookmarks.sortedWith(compareBy({ chapterIndex[it.trackId] ?: Int.MAX_VALUE }, { it.positionMs }))
            val groups = if (tracks.size > 1) sorted.groupBy { chapterIndex[it.trackId] } else mapOf(null to sorted)
            groups.forEach { (index, group) ->
                if (index != null) {
                    append("\n## Chapter ").append(index + 1)
                    tracks[index].title.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
                    append("\n")
                }
                group.forEach { bookmark ->
                    append("\n- **").append(formatTime(bookmark.positionMs)).append("** ").append(bookmark.title).append("\n")
                    bookmark.description?.takeIf { it.isNotBlank() }?.trim()?.lines()?.forEach { line ->
                        append("  > ").append(line).append("\n")
                    }
                }
            }
        }

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds / 60) % 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
    }
}
