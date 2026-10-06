package com.aikipunk.betteraudiobookplayer.domain.library

import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import java.time.LocalDateTime

enum class BookStatus { NOT_STARTED, IN_PROGRESS, FINISHED }

/** How far through a whole book the listener is, across all of its tracks. */
data class BookProgress(
    val listenedMs: Long,
    val durationMs: Long,
    val lastPlayedAt: LocalDateTime?,
) {
    val remainingMs: Long get() = (durationMs - listenedMs).coerceAtLeast(0)

    val fraction: Float
        get() = if (durationMs <= 0) 0f else (listenedMs.toFloat() / durationMs).coerceIn(0f, 1f)

    val status: BookStatus
        get() = when {
            listenedMs < STARTED_THRESHOLD_MS -> BookStatus.NOT_STARTED
            durationMs > 0 && remainingMs <= FINISHED_THRESHOLD_MS -> BookStatus.FINISHED
            else -> BookStatus.IN_PROGRESS
        }

    companion object {
        /** Anything under this counts as not started, so an accidental tap doesn't mark a book in progress. */
        const val STARTED_THRESHOLD_MS = 1_000L

        /** Credits and silence at the very end shouldn't keep a book "in progress". */
        const val FINISHED_THRESHOLD_MS = 30_000L

        /**
         * [state] stores a position within its current track; the book position adds the length of
         * every earlier track. Falls back to the book's own duration when tracks report none.
         */
        fun of(audiobook: Audiobook, tracks: List<Track>, state: PlaybackState?): BookProgress {
            val ordered = tracks.sortedBy { it.trackNumber }
            val trackTotal = ordered.sumOf { it.duration.coerceAtLeast(0) }
            val duration = if (trackTotal > 0) trackTotal else audiobook.duration.coerceAtLeast(0)
            if (state == null) return BookProgress(0, duration, null)

            val index = ordered.indexOfFirst { it.id == state.currentTrackId }
            val before = if (index > 0) ordered.take(index).sumOf { it.duration.coerceAtLeast(0) } else 0L
            val listened = (before + state.positionMs.coerceAtLeast(0))
                .let { if (duration > 0) it.coerceAtMost(duration) else it }
            return BookProgress(listened, duration, state.lastPlayedAt)
        }
    }
}
