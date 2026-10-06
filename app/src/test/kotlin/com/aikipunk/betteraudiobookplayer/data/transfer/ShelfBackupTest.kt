package com.aikipunk.betteraudiobookplayer.data.transfer

import android.content.Context
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import com.aikipunk.betteraudiobookplayer.domain.transfer.SavedBook
import com.aikipunk.betteraudiobookplayer.domain.transfer.SavedBookmark
import com.aikipunk.betteraudiobookplayer.domain.transfer.SavedProgress
import com.aikipunk.betteraudiobookplayer.domain.transfer.SavedTrack
import com.aikipunk.betteraudiobookplayer.domain.transfer.ShelfSnapshot
import com.aikipunk.betteraudiobookplayer.testutil.observable.FakeAudiobookRepository
import com.aikipunk.betteraudiobookplayer.testutil.observable.FakeBookmarkRepository
import com.aikipunk.betteraudiobookplayer.testutil.observable.FakePlaybackStateRepository
import com.aikipunk.betteraudiobookplayer.testutil.observable.FakeTrackRepository
import com.aikipunk.betteraudiobookplayer.testutil.testContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShelfBackupTest {
    private lateinit var context: Context
    private lateinit var dir: File
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setUp() {
        val filesRoot = File("build/test-files/shelf-backup-${System.nanoTime()}").apply { mkdirs() }
        context = testContext(filesDir = filesRoot)
        dir = File(context.filesDir, "bookshelf")
        dir.deleteRecursively()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `detectRestore uses existing pending file`() = runTest {
        val saved = SavedBook("Pending Book")
        write("restore-pending.json", listOf(saved))
        val backup = backup(scope = CoroutineScope(StandardTestDispatcher(testScheduler)))

        backup.start()
        advanceUntilIdle()

        assertEquals(listOf(saved), backup.pending.value)
    }

    @Test
    fun `detectRestore creates pending from snapshot only when library is empty`() = runTest {
        val saved = SavedBook("From Snapshot")
        write("bookshelf.json", listOf(saved))
        val backup = backup(scope = CoroutineScope(StandardTestDispatcher(testScheduler)))

        backup.start()
        advanceUntilIdle()

        assertEquals(listOf(saved), backup.pending.value)
        assertTrue(File(dir, "restore-pending.json").exists())
    }

    @Test
    fun `detectRestore ignores snapshot when library is not empty`() = runTest {
        write("bookshelf.json", listOf(SavedBook("From Snapshot")))
        val backup = backup(books = FakeAudiobookRepository(listOf(book("existing"))), scope = CoroutineScope(StandardTestDispatcher(testScheduler)))

        backup.start()
        advanceUntilIdle()

        assertTrue(backup.pending.value.isEmpty())
        assertFalse(File(dir, "restore-pending.json").exists())
    }

    @Test
    fun `restoreInto applies details newer progress clamped positions and bookmarks with new ids`() = runTest {
        val saved = savedBook(
            progress = SavedProgress(1, 99_999, 1.4f, "2026-01-10T12:00:00"),
            bookmarks = listOf(SavedBookmark(0, -500, "Start", "desc", "2026-01-02T00:00:00")),
        )
        write("restore-pending.json", listOf(saved))
        val books = FakeAudiobookRepository()
        val playback = FakePlaybackStateRepository(
            listOf(PlaybackState("imported", "old", 1, lastPlayedAt = LocalDateTime.parse("2026-01-01T00:00:00"))),
        )
        val bookmarks = FakeBookmarkRepository()
        val backup = backup(books = books, playback = playback, bookmarks = bookmarks, scope = CoroutineScope(StandardTestDispatcher(testScheduler)))
        backup.start()
        advanceUntilIdle()
        val imported = book("imported", title = "Original", author = null)
        val importedTracks = listOf(
            track("new-1", "imported", "file:///Audiobooks/Original/01.mp3", duration = 1_000),
            track("new-2", "imported", "file:///Audiobooks/Original/02.mp3", duration = 2_000),
        )

        val restored = backup.restoreInto(imported, importedTracks)
        advanceUntilIdle()

        assertEquals(saved, restored)
        val updated = books.updates.single()
        assertEquals("Custom", updated.customTitle)
        assertEquals("Saved Author", updated.author)
        assertEquals("Narrator", updated.narrator)
        assertEquals("Publisher", updated.publisher)
        assertEquals(2024, updated.publishedYear)
        assertEquals("Notes", updated.notes)
        val restoredState = playback.states.value.single()
        assertEquals("new-2", restoredState.currentTrackId)
        assertEquals(1_999L, restoredState.positionMs)
        assertEquals(1.4f, restoredState.speed)
        val restoredBookmark = bookmarks.bookmarks.value.single()
        assertNotEquals("Start", restoredBookmark.id)
        assertEquals("new-1", restoredBookmark.trackId)
        assertEquals(0L, restoredBookmark.positionMs)
        assertEquals("Start", restoredBookmark.title)
        assertTrue(backup.pending.value.isEmpty())
        assertFalse(File(dir, "restore-pending.json").exists())
    }

    @Test
    fun `restoreInto keeps newer local progress and rewrites remaining pending file`() = runTest {
        val match = savedBook(title = "Original")
        val remaining = savedBook(title = "Other")
        write("restore-pending.json", listOf(match, remaining))
        val playback = FakePlaybackStateRepository(
            listOf(PlaybackState("imported", "local", 123, lastPlayedAt = LocalDateTime.parse("2026-02-01T00:00:00"))),
        )
        val backup = backup(playback = playback, scope = CoroutineScope(StandardTestDispatcher(testScheduler)))
        backup.start()
        advanceUntilIdle()

        backup.restoreInto(book("imported", title = "Original"), listOf(track("new-1", "imported", "file:///Original/01.mp3")))
        advanceUntilIdle()

        assertEquals("local", playback.states.value.single().currentTrackId)
        assertEquals(listOf(remaining), backup.pending.value)
        val rewritten = json.decodeFromString(ShelfSnapshot.serializer(), File(dir, "restore-pending.json").readText())
        assertEquals(listOf("Other"), rewritten.books.map { it.title })
    }

    @Test
    fun `discardPending clears state and deletes file`() = runTest {
        write("restore-pending.json", listOf(SavedBook("Pending")))
        val backup = backup(scope = CoroutineScope(StandardTestDispatcher(testScheduler)))
        backup.start()
        advanceUntilIdle()

        backup.discardPending()
        advanceUntilIdle()

        assertTrue(backup.pending.value.isEmpty())
        assertFalse(File(dir, "restore-pending.json").exists())
    }

    @Test
    fun `snapshot writing includes still pending books`() = runTest {
        val pending = savedBook(title = "Still Pending")
        write("restore-pending.json", listOf(pending))
        val book = book("local", title = "Local")
        val track = track("track-local", "local", "file:///Books/Local/01.mp3", duration = 7_000)
        val state = PlaybackState("local", "track-local", 1_234, 1.1f, lastPlayedAt = LocalDateTime.parse("2026-03-01T00:00:00"))
        val bookmark = Bookmark("bm", "local", "track-local", 2_000, "Quote", createdAt = LocalDateTime.parse("2026-03-02T00:00:00"))
        val backup = backup(
            books = FakeAudiobookRepository(listOf(book)),
            tracks = FakeTrackRepository(listOf(track)),
            playback = FakePlaybackStateRepository(listOf(state)),
            bookmarks = FakeBookmarkRepository(listOf(bookmark)),
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        )

        backup.start()
        advanceUntilIdle()
        advanceTimeBy(2_100)
        runCurrent()

        val snapshot = json.decodeFromString(ShelfSnapshot.serializer(), File(dir, "bookshelf.json").readText())
        assertEquals(listOf("Local", "Still Pending"), snapshot.books.map { it.title })
        val local = snapshot.books.first()
        assertEquals("01.mp3", local.tracks.single().fileName)
        assertEquals(1_234L, local.progress?.positionMs)
        assertEquals("Quote", local.bookmarks.single().title)
    }

    private fun backup(
        books: FakeAudiobookRepository = FakeAudiobookRepository(),
        tracks: FakeTrackRepository = FakeTrackRepository(),
        playback: FakePlaybackStateRepository = FakePlaybackStateRepository(),
        bookmarks: FakeBookmarkRepository = FakeBookmarkRepository(),
        scope: CoroutineScope = CoroutineScope(StandardTestDispatcher()),
    ) = ShelfBackup(context, books, tracks, playback, bookmarks, scope)

    private fun write(name: String, books: List<SavedBook>) {
        dir.mkdirs()
        File(dir, name).writeText(json.encodeToString(ShelfSnapshot.serializer(), ShelfSnapshot(savedAt = "2026-01-01T00:00:00", books = books)))
    }

    private fun book(id: String, title: String = "Original", author: String? = "Author") = Audiobook(
        id = id,
        title = title,
        author = author,
        duration = 3_000,
        coverArtPath = null,
        filePath = "file:///Audiobooks/$title",
    )

    private fun track(id: String, bookId: String, path: String, duration: Long = 3_000) = Track(
        id = id,
        audiobookId = bookId,
        filePath = path,
        title = id,
        duration = duration,
        trackNumber = id.takeLast(1).toIntOrNull() ?: 1,
    )

    private fun savedBook(
        title: String = "Original",
        progress: SavedProgress? = SavedProgress(0, 100, 1.0f, "2026-01-01T00:00:00"),
        bookmarks: List<SavedBookmark> = emptyList(),
    ) = SavedBook(
        title = title,
        customTitle = "Custom",
        author = "Saved Author",
        narrator = "Narrator",
        publisher = "Publisher",
        publishedYear = 2024,
        notes = "Notes",
        folderPath = "Audiobooks/$title",
        durationMs = 3_000,
        addedAt = "2025-12-31T00:00:00",
        tracks = listOf(SavedTrack("01.mp3", "One", 1_000), SavedTrack("02.mp3", "Two", 2_000)),
        progress = progress,
        bookmarks = bookmarks,
    )
}
