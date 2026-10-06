package com.aikipunk.betteraudiobookplayer.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeRewindTest {

    @Test
    fun `rewindMs returns none for unknown or invalid pause durations`() {
        assertEquals(0, ResumeRewind.rewindMs(null))
        assertEquals(0, ResumeRewind.rewindMs(-1))
    }

    @Test
    fun `rewindMs uses short rewind below threshold and long rewind at threshold`() {
        assertEquals(ResumeRewind.SHORT_PAUSE_REWIND_MS, ResumeRewind.rewindMs(ResumeRewind.LONG_PAUSE_THRESHOLD_MS - 1))
        assertEquals(ResumeRewind.LONG_PAUSE_REWIND_MS, ResumeRewind.rewindMs(ResumeRewind.LONG_PAUSE_THRESHOLD_MS))
    }

    @Test
    fun `resumePosition subtracts rewind without going below zero`() {
        assertEquals(7_000, ResumeRewind.resumePosition(10_000, 1_000))
        assertEquals(0, ResumeRewind.resumePosition(2_000, 1_000))
        assertEquals(40_000, ResumeRewind.resumePosition(100_000, ResumeRewind.LONG_PAUSE_THRESHOLD_MS))
    }
}
