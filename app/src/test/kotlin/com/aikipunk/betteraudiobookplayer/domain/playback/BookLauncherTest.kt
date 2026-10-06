package com.aikipunk.betteraudiobookplayer.domain.playback

import androidx.test.core.app.ApplicationProvider
import com.aikipunk.betteraudiobookplayer.data.preferences.UserPreferences
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.testutil.FakeAudioPlayer
import com.aikipunk.betteraudiobookplayer.testutil.FakeAudiobookRepository
import com.aikipunk.betteraudiobookplayer.testutil.FakePlaybackStateRepository
import com.aikipunk.betteraudiobookplayer.testutil.FakeTrackRepository
import com.aikipunk.betteraudiobookplayer.testutil.audiobook
import com.aikipunk.betteraudiobookplayer.testutil.track
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookLauncherTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var player: FakeAudioPlayer
    private lateinit var playbackRepository: FakePlaybackStateRepository
    private lateinit var launcher: BookLauncher
    private lateinit var tracker: PlaybackProgressTracker

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        player = FakeAudioPlayer()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `finished books reopen at the first track from the start`() = runTest(dispatcher) {
        val book = audiobook("book1", duration = 120_000)
        val tracks = listOf(track("t1", duration = 60_000), track("t2", number = 2, duration = 60_000))
        playbackRepository = FakePlaybackStateRepository(listOf(PlaybackState("book1", "t2", 59_000, 1.4f, false)))
        launcher = launcher(book, tracks)

        val opened = launcher.open("book1")
        advanceUntilIdle()

        assertEquals("t1", opened.startTrack?.id)
        assertEquals(0L, opened.savedState?.positionMs)
        assertEquals(0, player.playlistCalls.single().startIndex)
        assertEquals(0L, player.playlistCalls.single().positionMs)
        assertEquals(1.4f, player.playbackSpeed.value, 0.0001f)
    }

    @Test
    fun `saved position is used when saved track exists`() = runTest(dispatcher) {
        val book = audiobook("book1")
        val tracks = listOf(track("t1"), track("t2", number = 2))
        playbackRepository = FakePlaybackStateRepository(listOf(PlaybackState("book1", "t2", 12_000, 1.25f, false)))
        launcher = launcher(book, tracks)

        val opened = launcher.open("book1", initialTrackId = "t1")
        advanceUntilIdle()

        assertEquals("t2", opened.startTrack?.id)
        assertEquals(1, player.playlistCalls.single().startIndex)
        assertEquals(12_000, player.playlistCalls.single().positionMs)
    }

    @Test
    fun `already-loaded book is not reloaded but reports loaded`() = runTest(dispatcher) {
        val book = audiobook("book1")
        val tracks = listOf(track("t1"))
        playbackRepository = FakePlaybackStateRepository(listOf(PlaybackState("book1", "t1", 7_000, 1f, false)))
        player.playlistKey = "book1"
        launcher = launcher(book, tracks)

        val opened = launcher.open("book1")
        advanceUntilIdle()

        assertTrue(opened.wasAlreadyLoaded)
        assertTrue(player.playlistCalls.isEmpty())
        assertEquals("book1", launcher.openedBookId.value)
    }

    @Test
    fun `nowPlaying uses track title for chaptered books and book title for single track books`() {
        val book = audiobook("book1", title = "Folder", author = "Writer").copy(customTitle = "Display")
        val chapter = track("t1", title = "Chapter 1")

        val chapterInfo = BookLauncher.nowPlaying(book, listOf(chapter, track("t2", number = 2)), chapter)
        val singleInfo = BookLauncher.nowPlaying(book, listOf(chapter), chapter)

        assertEquals("Chapter 1", chapterInfo.title)
        assertEquals("Display", singleInfo.title)
        assertEquals("Writer", singleInfo.artist)
        assertEquals("Display", singleInfo.album)
    }

    @Test
    fun `mostRecentBookId delegates to playback repository`() = runTest(dispatcher) {
        playbackRepository = FakePlaybackStateRepository(listOf(
            PlaybackState("old", "t1", lastPlayedAt = LocalDateTime.of(2026, 1, 1, 1, 0)),
            PlaybackState("new", "t1", lastPlayedAt = LocalDateTime.of(2026, 1, 2, 1, 0)),
        ))
        launcher = launcher(audiobook("book1"), listOf(track("t1")))

        assertEquals("new", launcher.mostRecentBookId())
    }

    private fun launcher(book: com.aikipunk.betteraudiobookplayer.domain.model.Audiobook, tracks: List<com.aikipunk.betteraudiobookplayer.domain.model.Track>): BookLauncher {
        tracker = PlaybackProgressTracker(player, FakePlaybackStateRepository())
        return BookLauncher(
            player,
            FakeAudiobookRepository(listOf(book)),
            FakeTrackRepository(tracks),
            playbackRepository,
            tracker,
            UserPreferences(ApplicationProvider.getApplicationContext()).apply { setDefaultSpeed(1.1f) },
        )
    }
}
