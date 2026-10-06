package com.aikipunk.betteraudiobookplayer.presentation.viewmodel

import android.content.Context
import com.aikipunk.betteraudiobookplayer.data.audio.AudioOutputManager
import com.aikipunk.betteraudiobookplayer.data.library.AudiobookLibraryManager
import com.aikipunk.betteraudiobookplayer.data.preferences.UserPreferences
import com.aikipunk.betteraudiobookplayer.testutil.testContext
import com.aikipunk.betteraudiobookplayer.domain.audio.PlaybackState as AudioPlaybackState
import com.aikipunk.betteraudiobookplayer.domain.library.BookDetails
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import com.aikipunk.betteraudiobookplayer.domain.playback.BookLauncher
import com.aikipunk.betteraudiobookplayer.domain.playback.OpenedBook
import com.aikipunk.betteraudiobookplayer.domain.playback.PlaybackProgressTracker
import com.aikipunk.betteraudiobookplayer.domain.playback.SleepMarker
import com.aikipunk.betteraudiobookplayer.domain.playback.SleepTimer
import com.aikipunk.betteraudiobookplayer.domain.playback.SleepTimerState
import com.aikipunk.betteraudiobookplayer.domain.repository.AudiobookRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.BookmarkRepository
import com.aikipunk.betteraudiobookplayer.domain.usecase.CreateBookmarkUseCase
import com.aikipunk.betteraudiobookplayer.service.PlaybackServiceManager
import com.aikipunk.betteraudiobookplayer.testutil.observable.FakeAudioPlayer
import com.aikipunk.betteraudiobookplayer.testutil.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private lateinit var audioPlayer: FakeAudioPlayer
    private lateinit var serviceManager: PlaybackServiceManager
    private lateinit var audiobookRepository: AudiobookRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var createBookmark: CreateBookmarkUseCase
    private lateinit var progressTracker: PlaybackProgressTracker
    private lateinit var sleepTimer: SleepTimer
    private lateinit var prefs: UserPreferences
    private lateinit var libraryManager: AudiobookLibraryManager
    private lateinit var outputManager: AudioOutputManager
    private lateinit var bookLauncher: BookLauncher
    private lateinit var openedBookIds: MutableStateFlow<String?>
    private lateinit var booksFlow: MutableStateFlow<List<Audiobook>>
    private lateinit var bookmarksFlow: MutableStateFlow<List<Bookmark>>

    private val book = Audiobook("book-1", "Synthetic Book", "Author", 30_000, null, "content://book")
    private val otherBook = Audiobook("book-2", "Opened Elsewhere", "Other", 10_000, null, "content://other")
    private val tracks = listOf(
        Track("t1", book.id, "content://book/01.mp3", "One", 10_000, 1),
        Track("t2", book.id, "content://book/02.mp3", "Two", 20_000, 2),
    )

    @Before
    fun setUp() {
        audioPlayer = FakeAudioPlayer()
        serviceManager = mockk(relaxed = true)
        booksFlow = MutableStateFlow(listOf(book, otherBook))
        audiobookRepository = mockk {
            every { getAllAudiobooksFlow() } returns booksFlow
        }
        bookmarksFlow = MutableStateFlow(emptyList())
        bookmarkRepository = mockk(relaxed = true) {
            every { getBookmarksForAudiobookFlow(any()) } returns bookmarksFlow
        }
        createBookmark = mockk()
        progressTracker = mockk(relaxed = true) {
            every { pausedForMs() } returns null
        }
        sleepTimer = mockk(relaxed = true) {
            every { state } returns MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
            every { start(any()) } just Runs
            every { startEndOfChapter() } just Runs
            every { extend() } just Runs
            every { cancel() } just Runs
        }
        prefs = UserPreferences(testContext())
        libraryManager = mockk(relaxed = true)
        outputManager = mockk(relaxed = true) {
            every { outputs } returns MutableStateFlow(emptyList())
            every { current } returns MutableStateFlow(null)
        }
        openedBookIds = MutableStateFlow(null)
        bookLauncher = mockk(relaxed = true) {
            every { openedBookId } returns openedBookIds
        }
    }

    @Test
    fun `loadAudiobook populates state and starts bookmarks observation`() = runTest {
        coEvery { bookLauncher.open(book.id, null) } returns OpenedBook(book, tracks, null, tracks[0], false)
        val vm = viewModel()

        vm.loadAudiobook(book.id)
        advanceUntilIdle()
        bookmarksFlow.value = listOf(Bookmark("b1", book.id, tracks[0].id, 1500, "note"))
        advanceUntilIdle()

        assertEquals(book.id, vm.uiState.value.audiobookId)
        assertEquals(book, vm.uiState.value.audiobook)
        assertEquals(tracks[0], vm.uiState.value.currentTrack)
        assertEquals(2, vm.uiState.value.tracks.size)
        assertEquals("note", vm.uiState.value.bookmarks.single().title)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `playCurrentTrack loads playlist when another book is loaded and starts service`() = runTest {
        coEvery { bookLauncher.open(book.id, null) } returns OpenedBook(book, tracks, null, tracks[1], false)
        val vm = viewModel()
        vm.loadAudiobook(book.id, autoPlay = false)
        advanceUntilIdle()

        vm.playCurrentTrack()
        advanceUntilIdle()

        verify { serviceManager.startPlaybackService() }
        verify { progressTracker.setSession(book.id, listOf("t1", "t2"), null) }
        coVerify { bookLauncher.load(book.id, book, tracks, 1, 0L) }
        assertTrue(audioPlayer.playing)
    }

    @Test
    fun `autoplay starts a newly opened book even while the previous one still reports playing`() = runTest {
        // Pausing the previous book is asynchronous, so the player can still report playing
        audioPlayer.playing = true
        audioPlayer.playlistKey = otherBook.id
        coEvery { bookLauncher.open(book.id, null) } returns OpenedBook(book, tracks, null, tracks[0], false)
        val vm = viewModel()

        vm.loadAudiobook(book.id, autoPlay = true)
        advanceUntilIdle()

        coVerify { bookLauncher.load(book.id, book, tracks, 0, 0L) }
        verify { serviceManager.startPlaybackService() }
    }

    @Test
    fun `autoplay leaves an already loaded book alone when it is playing`() = runTest {
        audioPlayer.playing = true
        audioPlayer.playlistKey = book.id
        coEvery { bookLauncher.open(book.id, null) } returns OpenedBook(book, tracks, null, tracks[0], true)
        val vm = viewModel()

        vm.loadAudiobook(book.id, autoPlay = true)
        advanceUntilIdle()

        verify(exactly = 0) { serviceManager.startPlaybackService() }
    }

    @Test
    fun `playCurrentTrack rewinds resume position when same playlist is already loaded`() = runTest {
        coEvery { bookLauncher.open(book.id, null) } returns OpenedBook(book, tracks, null, tracks[0], true)
        every { progressTracker.pausedForMs() } returns 120_000L
        prefs.setRewindOnResume(true)
        audioPlayer.playlistKey = book.id
        audioPlayer.currentPosition.value = 90_000L
        val vm = viewModel()
        vm.loadAudiobook(book.id)
        advanceUntilIdle()

        vm.playCurrentTrack()
        advanceUntilIdle()

        assertEquals(87_000L, audioPlayer.seeks.last())
        assertTrue(audioPlayer.playing)
    }

    @Test
    fun `skipForward clamps to duration and skipBackward clamps at zero`() = runTest {
        val vm = loadedViewModel()
        audioPlayer.currentPosition.value = 9_500L
        audioPlayer.duration.value = 10_000L
        advanceUntilIdle()

        vm.skipForward(2_000L)
        advanceUntilIdle()
        vm.skipBackward(20_000L)
        advanceUntilIdle()

        assertEquals(listOf(10_000L, 0L), audioPlayer.seeks.takeLast(2))
    }

    @Test
    fun `chapter navigation selects next previous and restart`() = runTest {
        val vm = loadedViewModel()
        vm.nextChapter()
        advanceUntilIdle()
        audioPlayer.currentPosition.value = 1_000L
        advanceUntilIdle()
        vm.previousChapter()
        advanceUntilIdle()
        audioPlayer.currentPosition.value = 5_000L
        advanceUntilIdle()
        vm.previousChapter()
        advanceUntilIdle()

        assertEquals(listOf(1 to 0L, 0 to 0L), audioPlayer.itemSeeks)
        assertEquals(0L, audioPlayer.seeks.last())
    }

    @Test
    fun `updateSpeed and sleep timer controls delegate to collaborators`() = runTest {
        val vm = viewModel()

        vm.updateSpeed(1.5f)
        vm.startSleepTimer(7)
        vm.startSleepTimerEndOfChapter()
        vm.extendSleepTimer()
        vm.cancelSleepTimer()
        advanceUntilIdle()

        assertEquals(1.5f, audioPlayer.playbackSpeed.value)
        verify { sleepTimer.start(420_000L) }
        verify { sleepTimer.startEndOfChapter() }
        verify { sleepTimer.extend() }
        verify { sleepTimer.cancel() }
    }

    @Test
    fun `create delete and consume bookmark update one shot state`() = runTest {
        val created = Bookmark("bookmark-new", book.id, tracks[0].id, 1200, "Mark")
        coEvery { bookLauncher.open(book.id, null) } returns OpenedBook(book, tracks, null, tracks[0], false)
        coEvery { createBookmark(book.id, tracks[0].id, 1200, "Mark", "Desc") } returns created
        val vm = viewModel()
        vm.loadAudiobook(book.id)
        audioPlayer.currentPosition.value = 1200
        advanceUntilIdle()

        vm.createBookmark("Mark", "Desc")
        advanceUntilIdle()
        assertSame(created, vm.uiState.value.addedBookmark)
        vm.consumeAddedBookmark()
        vm.deleteBookmark(created)
        advanceUntilIdle()

        assertNull(vm.uiState.value.addedBookmark)
        assertEquals("Bookmark deleted", vm.uiState.value.message)
        coVerify { bookmarkRepository.deleteBookmark(created.id) }
    }

    @Test
    fun `openedBookId loads a book opened elsewhere`() = runTest {
        coEvery { bookLauncher.open(book.id, null) } returns OpenedBook(book, tracks, null, tracks[0], false)
        coEvery { bookLauncher.open(otherBook.id, null) } returns OpenedBook(otherBook, emptyList(), null, null, false)
        val vm = viewModel()
        vm.loadAudiobook(book.id)
        advanceUntilIdle()

        openedBookIds.value = otherBook.id
        advanceUntilIdle()

        assertEquals(otherBook.id, vm.uiState.value.audiobookId)
        assertEquals(otherBook, vm.uiState.value.audiobook)
    }

    @Test
    fun `saveBookDetails delegates current book details to library manager`() = runTest {
        val vm = loadedViewModel()
        val details = BookDetails(title = "Renamed", notes = "Synthetic note")

        vm.saveBookDetails(details)
        advanceUntilIdle()

        coVerify { libraryManager.updateDetails(book, details) }
    }

    @Test
    fun `player flows update ui state and errors can be cleared`() = runTest {
        val vm = viewModel()

        audioPlayer.playbackState.value = AudioPlaybackState.PLAYING
        audioPlayer.currentPosition.value = 1234
        audioPlayer.duration.value = 9876
        audioPlayer.playbackSpeed.value = 1.75f
        audioPlayer.error.value = "Synthetic failure"
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isPlaying)
        assertEquals(1234L, vm.uiState.value.currentPositionMs)
        assertEquals(9876L, vm.uiState.value.durationMs)
        assertEquals(1.75f, vm.uiState.value.speed)
        assertEquals("Synthetic failure", vm.uiState.value.error)
        vm.clearError()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `current item index tracks loaded chapter and repository removal marks book removed`() = runTest {
        coEvery { bookLauncher.open(book.id, null) } returns OpenedBook(book, tracks, null, tracks[0], true)
        audioPlayer.playlistKey = book.id
        val vm = viewModel()
        vm.loadAudiobook(book.id)
        advanceUntilIdle()

        audioPlayer.currentItemIndex.value = 1
        advanceUntilIdle()
        assertEquals(tracks[1], vm.uiState.value.currentTrack)

        booksFlow.value = emptyList()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.bookRemoved)
    }

    @Test
    fun `renamed loaded book refreshes now playing metadata`() = runTest {
        coEvery { bookLauncher.open(book.id, null) } returns OpenedBook(book, tracks, null, tracks[0], true)
        audioPlayer.playlistKey = book.id
        val vm = viewModel()
        vm.loadAudiobook(book.id)
        advanceUntilIdle()

        booksFlow.value = listOf(book.copy(customTitle = "Renamed"), otherBook)
        advanceUntilIdle()

        assertEquals("Renamed", vm.uiState.value.audiobook?.displayTitle)
        assertEquals(listOf("One", "Two"), audioPlayer.nowPlayingUpdates.single().map { it.title })
    }

    @Test
    fun `toggle pause remove and bookmark bulk actions delegate and update messages`() = runTest {
        val vm = loadedViewModel()
        audioPlayer.playbackState.value = AudioPlaybackState.PLAYING
        advanceUntilIdle()

        vm.togglePlayPause()
        vm.removeBook()
        vm.deleteBookmarks(setOf("one", "two"))
        advanceUntilIdle()

        assertFalse(audioPlayer.playing)
        coVerify { libraryManager.remove(book) }
        coVerify { bookmarkRepository.deleteBookmark("one") }
        coVerify { bookmarkRepository.deleteBookmark("two") }
        assertEquals("2 bookmarks deleted", vm.uiState.value.message)
        vm.consumeMessage()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `jumpToBookmark seeks to bookmark chapter`() = runTest {
        val vm = loadedViewModel()
        val bookmark = Bookmark("b", book.id, tracks[1].id, 4321, "Saved place")

        vm.jumpToBookmark(bookmark)
        advanceUntilIdle()

        assertEquals(1 to 4321L, audioPlayer.itemSeeks.single())
    }

    @Test
    fun `jumpToSleepMarker seeks to the marked chapter of this book`() = runTest {
        val vm = loadedViewModel()
        prefs.setSleepMarker(SleepMarker(book.id, 1, 9_000))

        vm.jumpToSleepMarker()
        advanceUntilIdle()

        assertEquals(1 to 9_000L, audioPlayer.itemSeeks.single())
    }

    @Test
    fun `jumpToSleepMarker ignores a marker from another book`() = runTest {
        val vm = loadedViewModel()
        prefs.setSleepMarker(SleepMarker("other", 1, 9_000))

        vm.jumpToSleepMarker()
        advanceUntilIdle()

        assertTrue(audioPlayer.itemSeeks.isEmpty())
    }

    private suspend fun TestScope.loadedViewModel(): PlaybackViewModel {
        coEvery { bookLauncher.open(book.id, null) } returns OpenedBook(book, tracks, null, tracks[0], false)
        val vm = viewModel()
        vm.loadAudiobook(book.id)
        advanceUntilIdle()
        return vm
    }

    private fun viewModel() = PlaybackViewModel(
        audioPlayer = audioPlayer,
        playbackServiceManager = serviceManager,
        audiobookRepository = audiobookRepository,
        bookmarkRepository = bookmarkRepository,
        createBookmarkUseCase = createBookmark,
        progressTracker = progressTracker,
        sleepTimer = sleepTimer,
        userPreferences = prefs,
        libraryManager = libraryManager,
        audioOutputManager = outputManager,
        bookLauncher = bookLauncher,
        appContext = testContext(),
    )
}
