package com.aikipunk.betteraudiobookplayer.presentation.viewmodel

import android.content.Context
import android.net.Uri
import com.aikipunk.betteraudiobookplayer.data.filediscovery.FolderAudiobookImporter
import com.aikipunk.betteraudiobookplayer.data.filediscovery.FolderImportMode
import com.aikipunk.betteraudiobookplayer.data.filediscovery.FolderScan
import com.aikipunk.betteraudiobookplayer.data.filediscovery.ImportedAudiobook
import com.aikipunk.betteraudiobookplayer.data.library.AudiobookLibraryManager
import com.aikipunk.betteraudiobookplayer.data.preferences.UserPreferences
import com.aikipunk.betteraudiobookplayer.testutil.testContext
import com.aikipunk.betteraudiobookplayer.data.transfer.AudioFileFinder
import com.aikipunk.betteraudiobookplayer.data.transfer.ShelfBackup
import com.aikipunk.betteraudiobookplayer.domain.library.BookDetails
import com.aikipunk.betteraudiobookplayer.domain.library.LibraryFilter
import com.aikipunk.betteraudiobookplayer.domain.library.LibrarySort
import com.aikipunk.betteraudiobookplayer.domain.library.LibraryViewMode
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import com.aikipunk.betteraudiobookplayer.domain.transfer.SavedBook
import com.aikipunk.betteraudiobookplayer.domain.transfer.SavedTrack
import com.aikipunk.betteraudiobookplayer.domain.transfer.ShelfMatcher
import com.aikipunk.betteraudiobookplayer.testutil.observable.FakeAudiobookRepository
import com.aikipunk.betteraudiobookplayer.testutil.observable.FakePlaybackStateRepository
import com.aikipunk.betteraudiobookplayer.testutil.observable.FakeTrackRepository
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private lateinit var books: FakeAudiobookRepository
    private lateinit var tracks: FakeTrackRepository
    private lateinit var states: FakePlaybackStateRepository
    private lateinit var folderImporter: FolderAudiobookImporter
    private lateinit var libraryManager: AudiobookLibraryManager
    private lateinit var prefs: UserPreferences
    private lateinit var shelfBackup: ShelfBackup
    private lateinit var audioFileFinder: AudioFileFinder
    private lateinit var pending: MutableStateFlow<List<SavedBook>>

    private val first = Audiobook("a", "The Alpha", "Ann", 100_000, null, "file://a", createdAt = LocalDateTime.parse("2026-01-01T00:00:00"))
    private val second = Audiobook("b", "Beta", "Bob", 10_000, null, "file://b", createdAt = LocalDateTime.parse("2026-01-02T00:00:00"))
    private val firstTrack = Track("ta", "a", "file://a/1.mp3", "One", 100_000, 1)
    private val secondTrack = Track("tb", "b", "file://b/1.mp3", "One", 10_000, 1)

    @Before
    fun setUp() {
        books = FakeAudiobookRepository(listOf(first, second))
        tracks = FakeTrackRepository(listOf(firstTrack, secondTrack))
        states = FakePlaybackStateRepository(
            listOf(PlaybackState("a", "ta", 5_000, lastPlayedAt = LocalDateTime.parse("2026-01-03T00:00:00"))),
        )
        folderImporter = mockk()
        libraryManager = mockk(relaxed = true)
        prefs = UserPreferences(testContext())
        pending = MutableStateFlow(emptyList())
        shelfBackup = mockk(relaxed = true) {
            every { this@mockk.pending } returns this@LibraryViewModelTest.pending
            every { discardPending() } just Runs
        }
        audioFileFinder = mockk()
    }

    @Test
    fun `shelf state applies search filter sort and view mode`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setQuery("ann alpha")
        vm.setFilter(LibraryFilter.IN_PROGRESS)
        vm.setSort(LibrarySort.TITLE)
        vm.setViewMode(LibraryViewMode.LIST)
        advanceUntilIdle()

        assertEquals(listOf(first.id), vm.uiState.value.visibleBooks.map { it.audiobook.id })
        assertEquals(first.id, vm.uiState.value.continueListening?.audiobook?.id)
        assertEquals("ann alpha", vm.uiState.value.query)
        assertEquals(LibraryFilter.IN_PROGRESS, vm.uiState.value.filter)
        assertEquals(LibrarySort.TITLE, vm.uiState.value.sort)
        assertEquals(LibraryViewMode.LIST, vm.uiState.value.viewMode)
    }

    @Test
    fun `importFolder prompts for multiple loose files`() = runTest {
        val uri = Uri.parse("content://tree/many")
        val scan = scan("Many", 3)
        coEvery { folderImporter.scan(uri) } returns scan
        val vm = viewModel()

        vm.importFolder(uri)
        advanceUntilIdle()

        assertEquals(FolderImportPrompt("Many", 3), vm.uiState.value.folderImportPrompt)
        coVerify(exactly = 0) { folderImporter.build(any(), any()) }
    }

    @Test
    fun `importFolder skips prompt when file count matches restore target`() = runTest {
        val uri = Uri.parse("content://tree/restored")
        val saved = SavedBook("Restored", tracks = listOf(SavedTrack("1.mp3", "One", 1), SavedTrack("2.mp3", "Two", 1)))
        val scan = scan("Restored", 2)
        val imported = importedBook("restored")
        coEvery { folderImporter.scan(uri) } returns scan
        coEvery { folderImporter.build(scan, FolderImportMode.CHAPTERS) } returns listOf(imported)
        coEvery { shelfBackup.restoreInto(imported.audiobook, imported.tracks) } returns saved
        val vm = viewModel()

        vm.setRestoreTarget(saved)
        vm.importFolder(uri)
        advanceUntilIdle()

        assertNull(vm.uiState.value.folderImportPrompt)
        assertEquals("Restored progress and bookmarks for 1 book", vm.uiState.value.message)
        assertEquals(imported.audiobook, books.getAudiobookById("restored"))
    }

    @Test
    fun `confirmFolderImport saves each import and restores plural message`() = runTest {
        val uri = Uri.parse("content://tree/books")
        val scan = scan("Books", 2)
        val firstImport = importedBook("new-1")
        val secondImport = importedBook("new-2")
        coEvery { folderImporter.scan(uri) } returns scan
        coEvery { folderImporter.build(scan, FolderImportMode.BOOKS) } returns listOf(firstImport, secondImport)
        coEvery { shelfBackup.restoreInto(any(), any()) } returnsMany listOf(SavedBook("One"), SavedBook("Two"))
        val vm = viewModel()

        vm.importFolder(uri)
        advanceUntilIdle()
        vm.confirmFolderImport(FolderImportMode.BOOKS)
        advanceUntilIdle()

        assertEquals("Restored progress and bookmarks for 2 books", vm.uiState.value.message)
        assertTrue(books.books.value.any { it.id == "new-1" })
        assertTrue(books.books.value.any { it.id == "new-2" })
    }

    @Test
    fun `findOnPhone publishes candidates and dismiss clears search`() = runTest {
        val saved = SavedBook("Alpha", tracks = listOf(SavedTrack("01.mp3", "One", 1)))
        coEvery { audioFileFinder.allAudioFiles() } returns listOf(
            ShelfMatcher.AudioFile("external_primary", "Audiobooks/Alpha", "01.mp3", "Alpha"),
            ShelfMatcher.AudioFile("external_primary", "Audiobooks/Other", "x.mp3", null),
        )
        val vm = viewModel()

        vm.findOnPhone(saved)
        advanceUntilIdle()

        val search = vm.uiState.value.restoreSearch
        assertEquals(saved, search?.book)
        assertFalse(search?.isSearching ?: true)
        assertEquals("Audiobooks/Alpha", search?.candidates?.single()?.folderPath)
        vm.dismissRestoreSearch()
        assertNull(vm.uiState.value.restoreSearch)
    }

    @Test
    fun `discardRestore consumeMessage cancelFolderImport and clearError update collaborators and state`() = runTest {
        val vm = viewModel()
        vm.discardRestore()
        vm.consumeMessage()
        vm.cancelFolderImport()
        vm.clearError()
        advanceUntilIdle()

        verify { shelfBackup.discardPending() }
        assertNull(vm.uiState.value.message)
        assertNull(vm.uiState.value.folderImportPrompt)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `saveDetails remove markFinished and resetProgress delegate to library manager`() = runTest {
        val vm = viewModel()
        val details = BookDetails(title = "Edited")

        vm.saveDetails(first, details)
        vm.removeAudiobook(first)
        vm.markFinished(first)
        vm.resetProgress(first)
        advanceUntilIdle()

        coVerify { libraryManager.updateDetails(first, details) }
        coVerify { libraryManager.remove(first) }
        coVerify { libraryManager.markFinished(first) }
        coVerify { libraryManager.resetProgress(first) }
    }

    @Test
    fun `pending restore flow is mirrored into ui state`() = runTest {
        val vm = viewModel()
        val saved = SavedBook("Moved Phone")
        pending.value = listOf(saved)
        advanceUntilIdle()

        assertEquals(listOf(saved), vm.uiState.value.pendingRestore)
    }

    private fun viewModel() = LibraryViewModel(
        audiobookRepository = books,
        trackRepository = tracks,
        folderImporter = folderImporter,
        libraryManager = libraryManager,
        playbackStateRepository = states,
        userPreferences = prefs,
        shelfBackup = shelfBackup,
        audioFileFinder = audioFileFinder,
    )

    private fun scan(name: String, count: Int): FolderScan = mockk {
        every { folderName } returns name
        every { audioFileCount } returns count
    }

    private fun importedBook(id: String): ImportedAudiobook {
        val book = Audiobook(id, "Book $id", null, 1_000, null, "content://$id")
        val track = Track("track-$id", id, "content://$id/01.mp3", "One", 1_000, 1)
        return ImportedAudiobook(book, listOf(track))
    }
}
