package com.aikipunk.betteraudiobookplayer.presentation.ui.screen

import com.aikipunk.betteraudiobookplayer.domain.playback.SleepMarker
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackLabelsTest {
    @Test
    fun `spoken duration reads naturally for screen readers`() {
        assertEquals("0 seconds", spokenDuration(0))
        assertEquals("1 second", spokenDuration(1_000))
        assertEquals("1 minute 5 seconds", spokenDuration(65_000))
        assertEquals("2 hours 1 minute", spokenDuration(7_260_000))
        assertEquals("1 hour 3 seconds", spokenDuration(3_603_000))
    }

    @Test
    fun `sleep marker label includes the chapter only for multi-chapter books`() {
        val marker = SleepMarker("book", 2, 65_000)
        assertEquals("Ch. 3 · 01:05", sleepMarkerLabel(marker, hasChapters = true))
        assertEquals("01:05", sleepMarkerLabel(marker, hasChapters = false))
    }

    private fun place(vararg centers: Int, sleep: Int? = null) =
        bookmarkCenters(centers.toList(), sleep, size = 28, minCenter = 14, maxCenter = 986)

    @Test
    fun `bookmarks clear of each other and the sleep marker stay put`() {
        assertEquals(listOf(100, 500), place(100, 500, sleep = 300))
        assertEquals(listOf(100), place(100))
    }

    @Test
    fun `overlapping bookmarks spread evenly around their spot and keep their order`() {
        assertEquals(listOf(88, 116), place(100, 104))
        assertEquals(listOf(116, 88), place(104, 100))
        assertEquals(listOf(72, 100, 128), place(98, 100, 102))
    }

    @Test
    fun `spread bookmarks stay on the timeline`() {
        assertEquals(listOf(14, 42), place(10, 12))
        assertEquals(listOf(958, 986), place(985, 986))
    }

    @Test
    fun `too many bookmarks for the width share it evenly`() {
        val centers = bookmarkCenters(List(5) { 50 }, null, size = 28, minCenter = 14, maxCenter = 86)
        assertEquals(listOf(14, 32, 50, 68, 86), centers)
    }

    @Test
    fun `overlapping bookmark is budged to its own side of the sleep marker`() {
        assertEquals(listOf(272), place(290, sleep = 300))
        assertEquals(listOf(328), place(310, sleep = 300))
        assertEquals(listOf(272), place(300, sleep = 300))
    }

    @Test
    fun `bookmarks crowding the sleep marker line up beside it`() {
        assertEquals(listOf(272, 244, 328), place(295, 290, 305, sleep = 300))
    }

    @Test
    fun `bookmarks cross to the other side of a sleep marker at the timeline edge`() {
        assertEquals(listOf(48), place(15, sleep = 20))
        assertEquals(listOf(952), place(985, sleep = 980))
    }
}
