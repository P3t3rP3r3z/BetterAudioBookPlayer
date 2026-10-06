package com.aikipunk.betteraudiobookplayer.domain.playback

/**
 * How far to rewind when resuming after a pause, so the listener regains context.
 */
object ResumeRewind {
    const val SHORT_PAUSE_REWIND_MS = 3_000L
    const val LONG_PAUSE_REWIND_MS = 60_000L
    const val LONG_PAUSE_THRESHOLD_MS = 60 * 60 * 1000L

    /** @param pausedForMs time since playback stopped, or null if it was never paused. */
    fun rewindMs(pausedForMs: Long?): Long = when {
        pausedForMs == null || pausedForMs < 0 -> 0L
        pausedForMs < LONG_PAUSE_THRESHOLD_MS -> SHORT_PAUSE_REWIND_MS
        else -> LONG_PAUSE_REWIND_MS
    }

    fun resumePosition(positionMs: Long, pausedForMs: Long?): Long =
        (positionMs - rewindMs(pausedForMs)).coerceAtLeast(0L)
}
