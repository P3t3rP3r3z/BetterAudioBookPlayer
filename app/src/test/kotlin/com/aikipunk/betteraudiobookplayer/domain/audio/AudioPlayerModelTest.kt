package com.aikipunk.betteraudiobookplayer.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPlayerModelTest {
    @Test
    fun `playlist item and now playing info preserve metadata`() {
        val info = NowPlayingInfo(title = "Chapter", artist = "Author", album = "Book", artworkUri = "content://cover")
        val item = PlaylistItem(source = "content://audio", nowPlaying = info)

        assertEquals("content://audio", item.source)
        assertEquals("Chapter", item.nowPlaying?.title)
        assertEquals(listOf(PlaybackState.IDLE, PlaybackState.BUFFERING, PlaybackState.READY, PlaybackState.PLAYING, PlaybackState.PAUSED, PlaybackState.ENDED, PlaybackState.ERROR), PlaybackState.values().toList())
    }
}
