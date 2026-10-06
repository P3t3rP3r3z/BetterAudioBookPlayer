package com.aikipunk.betteraudiobookplayer.domain.playback

import com.aikipunk.betteraudiobookplayer.domain.audio.PlaybackState as AudioPlaybackState
import com.aikipunk.betteraudiobookplayer.testutil.FakeAudioPlayer
import com.aikipunk.betteraudiobookplayer.testutil.FakePlaybackStateRepository
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackProgressTrackerTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var player: FakeAudioPlayer
    private lateinit var repository: FakePlaybackStateRepository
    private lateinit var tracker: PlaybackProgressTracker

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        player = FakeAudioPlayer()
        repository = FakePlaybackStateRepository()
        tracker = PlaybackProgressTracker(player, repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setSession seeds current book track ids and paused time`() = runTest(dispatcher) {
        val lastPlayed = LocalDateTime.of(2026, 1, 2, 3, 4)
        tracker.setSession("book1", listOf("t1", "t2"), lastPlayed)

        assertEquals("book1", tracker.currentAudiobookId())
        assertEquals(listOf("t1", "t2"), tracker.currentTrackIds())
        assertEquals(lastPlayed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), tracker.pausedAtMs)
    }

    @Test
    fun `saveNow persists current chapter position speed and playing state`() = runTest(dispatcher) {
        player.playlistKey = "book1"
        player.setItemIndex(1)
        player.setPosition(12_345)
        player.setCurrentSpeed(1.25f)
        player.setPlaying(true)
        tracker.setSession("book1", listOf("t1", "t2"), null)

        tracker.saveNow()
        advanceUntilIdle()

        val saved = repository.savedStates.last()
        assertEquals("book1", saved.audiobookId)
        assertEquals("t2", saved.currentTrackId)
        assertEquals(12_345, saved.positionMs)
        assertEquals(1.25f, saved.speed, 0.0001f)
        assertTrue(saved.isPlaying)
    }

    @Test
    fun `saveNow ignores positions from a different loaded playlist`() = runTest(dispatcher) {
        player.playlistKey = "other"
        tracker.setSession("book1", listOf("t1"), null)

        tracker.saveNow()
        advanceUntilIdle()

        assertTrue(repository.savedStates.isEmpty())
    }

    @Test
    fun `playback stop records paused time and saves immediately`() = runTest(dispatcher) {
        player.playlistKey = "book1"
        player.setPosition(1_000)
        tracker.setSession("book1", listOf("t1"), null)
        advanceUntilIdle()

        player.setState(AudioPlaybackState.PLAYING)
        advanceUntilIdle()
        player.setState(AudioPlaybackState.PAUSED)
        advanceUntilIdle()

        assertTrue((tracker.pausedAtMs ?: 0L) > 0L)
        assertEquals(1_000, repository.savedStates.last().positionMs)
    }

    @Test
    fun `clearSession removes matching book only`() = runTest(dispatcher) {
        tracker.setSession("book1", listOf("t1"), null)
        tracker.clearSession("other")
        assertEquals("book1", tracker.currentAudiobookId())

        tracker.clearSession("book1")

        assertNull(tracker.currentAudiobookId())
        assertTrue(tracker.currentTrackIds().isEmpty())
        assertNull(tracker.pausedAtMs)
    }
}
