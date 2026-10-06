package com.aikipunk.betteraudiobookplayer.domain.playback

import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.aikipunk.betteraudiobookplayer.data.preferences.UserPreferences
import com.aikipunk.betteraudiobookplayer.domain.audio.PlaybackState
import com.aikipunk.betteraudiobookplayer.testutil.FakeAudioPlayer
import com.aikipunk.betteraudiobookplayer.testutil.FakeShakeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SleepTimerTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var player: FakeAudioPlayer
    private lateinit var shakeDetector: FakeShakeDetector
    private lateinit var preferences: UserPreferences
    private lateinit var timer: SleepTimer

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        player = FakeAudioPlayer()
        shakeDetector = FakeShakeDetector()
        preferences = UserPreferences(ApplicationProvider.getApplicationContext())
        preferences.setSleepMarker(null)
        preferences.setShakeToExtendSleepTimer(true)
        timer = SleepTimer(player, shakeDetector, preferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fadeVolume is full before fade window and linear in last thirty seconds`() {
        assertEquals(1f, SleepTimer.fadeVolume(30_000), 0.0001f)
        assertEquals(0.5f, SleepTimer.fadeVolume(15_000), 0.0001f)
        assertEquals(0f, SleepTimer.fadeVolume(-1), 0.0001f)
    }

    @Test
    fun `countdown only advances while playing`() = runTimerTest {
        player.setPlaying(false)
        timer.start(1_000)

        advanceTimerBy(1_000)
        assertEquals(SleepTimerState.Countdown(1_000), timer.state.value)
        assertEquals(0, player.pauseCalls)

        player.setPlaying(true)
        advanceTimerBy(1_000)

        assertEquals(SleepTimerState.Off, timer.state.value)
        assertEquals(1, player.pauseCalls)
    }

    @Test
    fun `countdown fades volume near end then pauses and rewinds sixty seconds`() = runTimerTest {
        preferences.setShakeToExtendSleepTimer(false)
        player.setPlaying(true)
        player.setPosition(90_000)

        timer.start(1_000)
        advanceTimerBy(1_000)

        assertEquals(1, player.pauseCalls)
        assertEquals(listOf(30_000L), player.seekCalls)
        assertTrue(player.volumes.any { it in 0f..0.04f })
        assertEquals(1f, player.volumes.last(), 0.0001f)
        assertFalse(shakeDetector.listening)
    }

    @Test
    fun `extend adds time to active countdown`() = runTimerTest {
        player.setPlaying(true)
        timer.start(1_000)
        advanceTimerBy(250)

        timer.extend(2_000)
        assertEquals(SleepTimerState.Countdown(2_750), timer.state.value)

        advanceTimerBy(2_500)
        assertTrue(timer.state.value is SleepTimerState.Countdown)
        assertEquals(0, player.pauseCalls)
    }

    @Test
    fun `end of chapter expires on natural rollover but manual jump retargets next track`() = runTimerTest {
        preferences.setShakeToExtendSleepTimer(false)
        player.setPlaying(true)
        player.setDuration(120_000)
        player.setPosition(30_000)
        timer.startEndOfChapter()
        advanceTimerBy(250)

        player.setItemIndex(1)
        advanceTimerBy(250)
        assertEquals(0, player.pauseCalls)
        assertTrue(timer.state.value is SleepTimerState.EndOfChapter)

        player.setPosition(119_000)
        advanceTimerBy(250)
        player.setItemIndex(2)
        advanceTimerBy(250)

        assertEquals(SleepTimerState.Off, timer.state.value)
        assertEquals(1, player.pauseCalls)
        assertTrue(player.seekCalls.isEmpty())
    }

    @Test
    fun `end of chapter expires when playback reports ended`() = runTimerTest {
        preferences.setShakeToExtendSleepTimer(false)
        player.setPlaying(true)
        timer.startEndOfChapter()
        advanceTimerBy(250)

        player.setState(PlaybackState.ENDED)
        advanceTimerBy(250)

        assertEquals(SleepTimerState.Off, timer.state.value)
        assertEquals(1, player.pauseCalls)
    }

    @Test
    fun `shake in grace window restarts playback with an extension`() = runTimerTest {
        player.setPlaying(true)
        player.setPosition(65_000)
        timer.start(250)
        advanceTimerBy(250)
        assertEquals(SleepTimerState.Off, timer.state.value)
        assertTrue(shakeDetector.listening)

        shakeDetector.shake()
        testScheduler.runCurrent()

        assertEquals(1, shakeDetector.acknowledged)
        assertEquals(1, player.playCalls)
        assertEquals(SleepTimerState.Countdown(SleepTimer.EXTEND_MS), timer.state.value)
    }

    @Test
    fun `cancel stops timers shake listening and restores volume`() = runTimerTest {
        player.setPlaying(true)
        timer.start(1_000)
        advanceTimerBy(250)

        timer.cancel()
        advanceUntilIdle()

        assertEquals(SleepTimerState.Off, timer.state.value)
        assertFalse(shakeDetector.listening)
        assertEquals(1f, player.volumes.last(), 0.0001f)
    }

    @Test
    fun `starting the timer marks where the listener was`() = runTimerTest {
        loadBook("book", index = 2, positionMs = 90_000)

        timer.start(60_000)

        assertEquals(SleepMarker("book", 2, 90_000), preferences.sleepMarker.value)
    }

    @Test
    fun `each use of the timer moves the single marker`() = runTimerTest {
        loadBook("book", index = 0, positionMs = 10_000)
        timer.start(60_000)

        player.setItemIndex(1)
        player.setPosition(20_000)
        timer.startEndOfChapter()
        assertEquals(SleepMarker("book", 1, 20_000), preferences.sleepMarker.value)

        player.setPosition(30_000)
        timer.extend()
        assertEquals(SleepMarker("book", 1, 30_000), preferences.sleepMarker.value)
    }

    @Test
    fun `resuming the book after the timer stopped it clears the marker`() = runTimerTest {
        loadBook("book", index = 0, positionMs = 10_000)
        player.setPlaying(true)
        timer.start(250)
        advanceTimerBy(250)
        assertEquals(SleepTimerState.Off, timer.state.value)
        assertEquals(SleepMarker("book", 0, 10_000), preferences.sleepMarker.value)

        player.play()
        testScheduler.runCurrent()

        assertNull(preferences.sleepMarker.value)
    }

    @Test
    fun `pressing play while the timer runs keeps the marker`() = runTimerTest {
        loadBook("book", index = 0, positionMs = 10_000)
        timer.start(60_000)

        player.play()
        testScheduler.runCurrent()

        assertEquals(SleepMarker("book", 0, 10_000), preferences.sleepMarker.value)
    }

    @Test
    fun `shaking awake in the grace window moves the marker instead of clearing it`() = runTimerTest {
        loadBook("book", index = 0, positionMs = 10_000)
        player.setPlaying(true)
        timer.start(250)
        advanceTimerBy(250)
        player.setPosition(70_000)

        shakeDetector.shake()
        testScheduler.runCurrent()

        assertEquals(SleepMarker("book", 0, 70_000), preferences.sleepMarker.value)
    }

    @Test
    fun `playing a different book keeps the marker`() = runTimerTest {
        loadBook("book", index = 0, positionMs = 10_000)
        timer.start(60_000)
        timer.cancel()
        player.playlistKey = "other"

        player.play()
        testScheduler.runCurrent()

        assertEquals(SleepMarker("book", 0, 10_000), preferences.sleepMarker.value)
    }

    @Test
    fun `no marker without a loaded book`() = runTimerTest {
        timer.start(60_000)

        assertNull(preferences.sleepMarker.value)
    }

    private fun loadBook(key: String, index: Int, positionMs: Long) {
        player.playlistKey = key
        player.setItemIndex(index)
        player.setPosition(positionMs)
    }

    private fun runTimerTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            block()
        } finally {
            timer.cancel()
            testScheduler.runCurrent()
        }
    }

    private fun TestScope.advanceTimerBy(ms: Long) {
        testScheduler.runCurrent()
        var remaining = ms
        while (remaining > 0) {
            val step = minOf(250L, remaining)
            ShadowSystemClock.advanceBy(Duration.ofMillis(step))
            testScheduler.advanceTimeBy(step)
            testScheduler.runCurrent()
            remaining -= step
        }
    }
}
