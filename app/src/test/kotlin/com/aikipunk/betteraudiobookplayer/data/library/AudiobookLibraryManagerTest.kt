package com.aikipunk.betteraudiobookplayer.data.library

import com.aikipunk.betteraudiobookplayer.data.filediscovery.FolderAudiobookImporter
import com.aikipunk.betteraudiobookplayer.domain.audio.AudioPlayer
import com.aikipunk.betteraudiobookplayer.domain.library.BookDetails
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import com.aikipunk.betteraudiobookplayer.domain.playback.PlaybackProgressTracker
import com.aikipunk.betteraudiobookplayer.domain.repository.AudiobookRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.BookmarkRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.PlaybackStateRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.TrackRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class AudiobookLibraryManagerTest {
    private lateinit var audiobookRepository: AudiobookRepository
    private lateinit var trackRepository: TrackRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var playbackStateRepository: PlaybackStateRepository
    private lateinit var progressTracker: PlaybackProgressTracker
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var folderImporter: FolderAudiobookImporter
    private lateinit var manager: AudiobookLibraryManager

    @Before
    fun setUp() {
        audiobookRepository = mockk(relaxed = true)
        trackRepository = mockk(relaxed = true)
        bookmarkRepository = mockk(relaxed = true)
        playbackStateRepository = mockk(relaxed = true)
        progressTracker = mockk(relaxed = true)
        audioPlayer = mockk(relaxed = true)
        folderImporter = mockk(relaxed = true)
        manager = AudiobookLibraryManager(
            audiobookRepository,
            trackRepository,
            bookmarkRepository,
            playbackStateRepository,
            progressTracker,
            audioPlayer,
            folderImporter,
        )
    }

    @Test
    fun `updateDetails applies editable book details before saving`() = runTest {
        val saved = slot<Audiobook>()
        coEvery { audiobookRepository.updateAudiobook(capture(saved)) } just runs

        manager.updateDetails(book(), BookDetails(title = "Custom", author = "Author", year = "2020"))

        assertEquals("Custom", saved.captured.customTitle)
        assertEquals("Author", saved.captured.author)
        assertEquals(2020, saved.captured.publishedYear)
    }

    @Test
    fun `updateDetails renames bookmarks still named after the old title`() = runTest {
        val audiobook = book()
        val named = bookmark("b1", "File Title")
        val chapter = bookmark("b2", "Chapter 2")
        val custom = bookmark("b3", "Great quote")
        coEvery { bookmarkRepository.getBookmarksForAudiobook(audiobook.id) } returns listOf(named, chapter, custom)

        manager.updateDetails(audiobook, BookDetails(title = "  Dune Messiah "))

        coVerify(exactly = 1) { bookmarkRepository.updateBookmark(named.copy(title = "Dune Messiah")) }
        coVerify(exactly = 1) { bookmarkRepository.updateBookmark(any()) }
    }

    @Test
    fun `updateDetails renames bookmarks back to the file title when the custom title is cleared`() = runTest {
        val audiobook = book().copy(customTitle = "Custom")
        val named = bookmark("b1", "Custom")
        coEvery { bookmarkRepository.getBookmarksForAudiobook(audiobook.id) } returns listOf(named)

        manager.updateDetails(audiobook, BookDetails(title = ""))

        coVerify { bookmarkRepository.updateBookmark(named.copy(title = "File Title")) }
    }

    @Test
    fun `updateDetails leaves bookmarks alone when the title is unchanged`() = runTest {
        val audiobook = book()
        coEvery { bookmarkRepository.getBookmarksForAudiobook(audiobook.id) } returns listOf(bookmark("b1", "File Title"))

        manager.updateDetails(audiobook, BookDetails(title = "File Title", author = "Someone"))

        coVerify(exactly = 0) { bookmarkRepository.updateBookmark(any()) }
    }

    @Test
    fun `markFinished seeks loaded book and saves tracker instead of writing repository state`() = runTest {
        val audiobook = book()
        coEvery { trackRepository.getTracksForAudiobook(audiobook.id) } returns listOf(track("first", 1, 1_000), track("last", 2, 5_000))
        every { progressTracker.currentAudiobookId() } returns audiobook.id
        every { audioPlayer.currentPlaylistKey() } returns audiobook.id

        manager.markFinished(audiobook)

        coVerify { audioPlayer.pause() }
        coVerify { audioPlayer.seekToItem(1, 5_000) }
        verify { progressTracker.saveNow() }
        coVerify(exactly = 0) { playbackStateRepository.savePlaybackState(any()) }
    }

    @Test
    fun `markFinished saves end of final track for unloaded book preserving speed`() = runTest {
        val audiobook = book()
        val savedState = slot<PlaybackState>()
        coEvery { trackRepository.getTracksForAudiobook(audiobook.id) } returns listOf(track("last", 2, 5_000), track("first", 1, 1_000))
        every { progressTracker.currentAudiobookId() } returns null
        every { audioPlayer.currentPlaylistKey() } returns null
        coEvery { playbackStateRepository.getPlaybackState(audiobook.id) } returns PlaybackState(audiobook.id, "first", speed = 1.5f)
        coEvery { playbackStateRepository.savePlaybackState(capture(savedState)) } just runs

        manager.markFinished(audiobook)

        assertEquals("last", savedState.captured.currentTrackId)
        assertEquals(5_000, savedState.captured.positionMs)
        assertEquals(1.5f, savedState.captured.speed, 0.0001f)
        assertEquals(false, savedState.captured.isPlaying)
    }

    @Test
    fun `markFinished does nothing when a book has no tracks`() = runTest {
        coEvery { trackRepository.getTracksForAudiobook("book") } returns emptyList()

        manager.markFinished(book())

        coVerify(exactly = 0) { playbackStateRepository.savePlaybackState(any()) }
        coVerify(exactly = 0) { audioPlayer.pause() }
    }

    @Test
    fun `resetProgress seeks loaded book to beginning or deletes unloaded state`() = runTest {
        val audiobook = book()
        every { progressTracker.currentAudiobookId() } returns audiobook.id andThen null
        every { audioPlayer.currentPlaylistKey() } returns audiobook.id andThen null

        manager.resetProgress(audiobook)
        manager.resetProgress(audiobook)

        coVerify { audioPlayer.pause() }
        coVerify { audioPlayer.seekToItem(0, 0) }
        verify { progressTracker.saveNow() }
        coVerify { playbackStateRepository.deletePlaybackState(audiobook.id) }
    }

    @Test
    fun `remove stops loaded book deletes related records and releases folder access with remaining paths`() = runTest {
        val audiobook = book(filePath = "content://synthetic/tree#Book")
        every { progressTracker.currentAudiobookId() } returns audiobook.id
        coEvery { audiobookRepository.getAllAudiobooksFlow() } returns flowOf(listOf(book(id = "remaining", filePath = "content://synthetic/tree#Other")))

        manager.remove(audiobook)

        verify { progressTracker.clearSession(audiobook.id) }
        coVerify { audioPlayer.stop() }
        coVerify { bookmarkRepository.deleteBookmarksForAudiobook(audiobook.id) }
        coVerify { playbackStateRepository.deletePlaybackState(audiobook.id) }
        coVerify { trackRepository.deleteTracksForAudiobook(audiobook.id) }
        coVerify { audiobookRepository.deleteAudiobook(audiobook.id) }
        verify { folderImporter.releaseBook(audiobook, listOf("content://synthetic/tree#Other")) }
    }

    @Test
    fun `remove skips player stop when another book is loaded`() = runTest {
        val audiobook = book()
        every { progressTracker.currentAudiobookId() } returns "other"
        coEvery { audiobookRepository.getAllAudiobooksFlow() } returns flowOf(emptyList())

        manager.remove(audiobook)

        coVerify(exactly = 0) { audioPlayer.stop() }
        verify(exactly = 0) { progressTracker.clearSession(audiobook.id) }
        verify { folderImporter.releaseBook(audiobook, emptyList()) }
    }

    private fun book(id: String = "book", filePath: String = "/synthetic/book") = Audiobook(
        id = id,
        title = "File Title",
        author = null,
        duration = 6_000,
        coverArtPath = null,
        filePath = filePath,
        createdAt = LocalDateTime.of(2026, 9, 29, 12, 0),
        totalTracks = 2,
    )

    private fun bookmark(id: String, title: String) = Bookmark(
        id = id,
        audiobookId = "book",
        trackId = "first",
        positionMs = 1_000,
        title = title,
        createdAt = LocalDateTime.of(2026, 9, 29, 12, 0),
    )

    private fun track(id: String, number: Int, duration: Long) = Track(id, "book", "/synthetic/$id.mp3", "Track $id", duration, number)
}
