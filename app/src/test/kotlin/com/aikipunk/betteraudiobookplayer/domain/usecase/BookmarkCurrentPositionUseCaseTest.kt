package com.aikipunk.betteraudiobookplayer.domain.usecase

import com.aikipunk.betteraudiobookplayer.domain.playback.PlaybackProgressTracker
import com.aikipunk.betteraudiobookplayer.testutil.FakeAudioPlayer
import com.aikipunk.betteraudiobookplayer.testutil.FakeAudiobookRepository
import com.aikipunk.betteraudiobookplayer.testutil.FakeBookmarkRepository
import com.aikipunk.betteraudiobookplayer.testutil.FakePlaybackStateRepository
import com.aikipunk.betteraudiobookplayer.testutil.audiobook
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkCurrentPositionUseCaseTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `returns null when no book is loaded`() = runTest(dispatcher) {
        val tracker = PlaybackProgressTracker(FakeAudioPlayer(), FakePlaybackStateRepository())
        val useCase = BookmarkCurrentPositionUseCase(tracker, FakeAudioPlayer(), FakeAudiobookRepository(), CreateBookmarkUseCase(FakeBookmarkRepository()))
        assertNull(useCase())
    }

    @Test
    fun `bookmarks current chapter position with chapter title`() = runTest(dispatcher) {
        val player = FakeAudioPlayer().apply { setItemIndex(1); setPosition(12_000); playlistKey = "book1" }
        val tracker = PlaybackProgressTracker(player, FakePlaybackStateRepository()).apply { setSession("book1", listOf("t1", "t2"), null) }
        val bookmarks = FakeBookmarkRepository()
        val useCase = BookmarkCurrentPositionUseCase(tracker, player, FakeAudiobookRepository(), CreateBookmarkUseCase(bookmarks))

        val bookmark = useCase()
        advanceUntilIdle()

        assertEquals("t2", bookmark?.trackId)
        assertEquals(12_000L, bookmark?.positionMs)
        assertEquals("Chapter 2", bookmark?.title)
        assertEquals(bookmark, bookmarks.added.single())
    }

    @Test
    fun `single track bookmark uses audiobook title`() = runTest(dispatcher) {
        val player = FakeAudioPlayer().apply { setPosition(5_000); playlistKey = "book1" }
        val tracker = PlaybackProgressTracker(player, FakePlaybackStateRepository()).apply { setSession("book1", listOf("t1"), null) }
        val useCase = BookmarkCurrentPositionUseCase(
            tracker,
            player,
            FakeAudiobookRepository(listOf(audiobook("book1", title = "Synthetic Book"))),
            CreateBookmarkUseCase(FakeBookmarkRepository()),
        )

        assertEquals("Synthetic Book", useCase()?.title)
    }
}
