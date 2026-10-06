package com.aikipunk.betteraudiobookplayer.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.aikipunk.betteraudiobookplayer.data.entity.AudiobookEntity
import com.aikipunk.betteraudiobookplayer.data.entity.BookmarkEntity
import com.aikipunk.betteraudiobookplayer.data.entity.PlaybackStateEntity
import com.aikipunk.betteraudiobookplayer.data.entity.TrackEntity
import com.aikipunk.betteraudiobookplayer.data.repository.AudiobookRepositoryImpl
import com.aikipunk.betteraudiobookplayer.data.repository.BookmarkRepositoryImpl
import com.aikipunk.betteraudiobookplayer.data.repository.PlaybackStateRepositoryImpl
import com.aikipunk.betteraudiobookplayer.data.repository.TrackRepositoryImpl
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDaoRepositoryTest {
    private lateinit var db: AudiobookPlayerDatabase
    private lateinit var audiobookRepository: AudiobookRepositoryImpl
    private lateinit var trackRepository: TrackRepositoryImpl
    private lateinit var playbackRepository: PlaybackStateRepositoryImpl
    private lateinit var bookmarkRepository: BookmarkRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AudiobookPlayerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        audiobookRepository = AudiobookRepositoryImpl(db.audiobookDao())
        trackRepository = TrackRepositoryImpl(db.trackDao())
        playbackRepository = PlaybackStateRepositoryImpl(db.playbackStateDao())
        bookmarkRepository = BookmarkRepositoryImpl(db.bookmarkDao())
    }

    @After
    fun tearDown() {
        db.close()
    }


    @Test
    fun `database migrations add expected metadata columns`() {
        val sqlite = mockk<SupportSQLiteDatabase>(relaxed = true)

        AudiobookPlayerDatabase.MIGRATION_1_2.migrate(sqlite)
        AudiobookPlayerDatabase.MIGRATION_2_3.migrate(sqlite)
        AudiobookPlayerDatabase.MIGRATION_3_4.migrate(sqlite)

        verify { sqlite.execSQL("ALTER TABLE audiobooks ADD COLUMN notes TEXT") }
        verify { sqlite.execSQL("ALTER TABLE audiobooks ADD COLUMN customTitle TEXT") }
        verify { sqlite.execSQL("ALTER TABLE audiobooks ADD COLUMN narrator TEXT") }
        verify { sqlite.execSQL("ALTER TABLE audiobooks ADD COLUMN publisher TEXT") }
        verify { sqlite.execSQL("ALTER TABLE audiobooks ADD COLUMN publishedYear INTEGER") }
    }

    @Test
    fun `database exposes all DAO instances`() {
        assertTrue(db.audiobookDao().javaClass.name.contains("AudiobookDao"))
        assertTrue(db.trackDao().javaClass.name.contains("TrackDao"))
        assertTrue(db.playbackStateDao().javaClass.name.contains("PlaybackStateDao"))
        assertTrue(db.bookmarkDao().javaClass.name.contains("BookmarkDao"))
    }

    @Test
    fun `audiobook DAO inserts updates orders flows and deletes`() = runTest {
        val older = audiobookEntity("older", createdAt = TIME.minusDays(1))
        val newer = audiobookEntity("newer", createdAt = TIME)
        db.audiobookDao().insert(older)
        db.audiobookDao().insert(newer)

        assertEquals(listOf("newer", "older"), db.audiobookDao().getAll().map { it.id })
        assertEquals(listOf("newer", "older"), db.audiobookDao().getAllFlow().first().map { it.id })

        db.audiobookDao().update(newer.copy(author = "Updated Author"))
        assertEquals("Updated Author", db.audiobookDao().getById("newer")?.author)

        db.audiobookDao().delete(older)
        assertNull(db.audiobookDao().getById("older"))
    }

    @Test
    fun `track DAO inserts lists by audiobook updates and deletes by book`() = runTest {
        val first = trackEntity("t1", trackNumber = 2)
        val second = trackEntity("t2", trackNumber = 1)
        db.trackDao().insert(first)
        db.trackDao().insertAll(listOf(second, trackEntity("other", audiobookId = "other")))

        assertEquals(listOf("t2", "t1"), db.trackDao().getTracksForAudiobook("book").map { it.id })
        assertEquals(listOf("t2", "t1"), db.trackDao().getTracksForAudiobookFlow("book").first().map { it.id })
        assertEquals(listOf("book", "book", "other"), db.trackDao().getAllFlow().first().map { it.audiobookId })

        db.trackDao().update(first.copy(title = "Updated"))
        assertEquals("Updated", db.trackDao().getById("t1")?.title)
        db.trackDao().deleteTracksForAudiobook("book")
        assertTrue(db.trackDao().getTracksForAudiobook("book").isEmpty())
    }

    @Test
    fun `playback DAO upserts updates queries most recent and deletes`() = runTest {
        db.playbackStateDao().upsert(playbackEntity("older", lastPlayedAt = TIME.minusDays(1)))
        db.playbackStateDao().upsert(playbackEntity("newer", lastPlayedAt = TIME))
        db.playbackStateDao().upsert(playbackEntity("newer", positionMs = 99, lastPlayedAt = TIME.plusMinutes(1)))

        assertEquals("newer", db.playbackStateDao().getMostRecent()?.audiobookId)
        assertEquals(99L, db.playbackStateDao().getByAudiobookId("newer")?.positionMs)
        assertEquals(99L, db.playbackStateDao().getByAudiobookIdFlow("newer").first()?.positionMs)

        db.playbackStateDao().updatePosition("newer", 123)
        db.playbackStateDao().updatePlaybackState("newer", 456, 1.5f, true)
        val updated = db.playbackStateDao().getByAudiobookId("newer")
        assertEquals(456L, updated?.positionMs)
        assertEquals(1.5f, updated?.speed ?: 0f, 0.0001f)
        assertEquals(2, db.playbackStateDao().getAllFlow().first().size)

        db.playbackStateDao().deleteByAudiobookId("newer")
        assertNull(db.playbackStateDao().getByAudiobookId("newer"))
    }

    @Test
    fun `bookmark DAO inserts updates orders by audiobook and track and deletes by book`() = runTest {
        val later = bookmarkEntity("later", positionMs = 5_000)
        val earlier = bookmarkEntity("earlier", positionMs = 1_000)
        val otherTrack = bookmarkEntity("otherTrack", trackId = "track2", positionMs = 2_000)
        db.bookmarkDao().insert(later)
        db.bookmarkDao().insert(earlier)
        db.bookmarkDao().insert(otherTrack)
        db.bookmarkDao().insert(bookmarkEntity("otherBook", audiobookId = "other", trackId = "otherTrack", positionMs = 3_000))

        assertEquals(listOf("earlier", "otherTrack", "later"), db.bookmarkDao().getBookmarksForAudiobook("book").map { it.id })
        assertEquals(listOf("earlier", "otherTrack", "later"), db.bookmarkDao().getBookmarksForAudiobookFlow("book").first().map { it.id })
        assertEquals(listOf("earlier", "later"), db.bookmarkDao().getBookmarksForTrack("track").map { it.id })
        assertEquals(listOf("earlier", "otherTrack", "otherBook", "later"), db.bookmarkDao().getAllBookmarksFlow().first().map { it.id })

        db.bookmarkDao().update(later.copy(title = "Updated"))
        assertEquals("Updated", db.bookmarkDao().getById("later")?.title)
        db.bookmarkDao().delete(earlier)
        assertNull(db.bookmarkDao().getById("earlier"))
        db.bookmarkDao().deleteBookmarksForAudiobook("book")
        assertTrue(db.bookmarkDao().getBookmarksForAudiobook("book").isEmpty())
    }

    @Test
    fun `audiobook repository maps add update flow lookup and safe delete`() = runTest {
        val audiobook = audiobook("book")
        audiobookRepository.addAudiobook(audiobook)
        assertEquals(audiobook, audiobookRepository.getAudiobookById("book"))
        assertEquals(listOf(audiobook), audiobookRepository.getAllAudiobooksFlow().first())

        audiobookRepository.updateAudiobook(audiobook.copy(author = "Updated"))
        assertEquals("Updated", audiobookRepository.getAudiobookById("book")?.author)
        audiobookRepository.deleteAudiobook("missing")
        audiobookRepository.deleteAudiobook("book")
        assertNull(audiobookRepository.getAudiobookById("book"))
    }

    @Test
    fun `track repository maps add list flow lookup and delete`() = runTest {
        val first = track("t1", 2)
        val second = track("t2", 1)
        trackRepository.addTrack(first)
        trackRepository.addTracks(listOf(second, track("other", 1, audiobookId = "other")))

        assertEquals(second, trackRepository.getTrackById("t2"))
        assertEquals(listOf(second, first), trackRepository.getTracksForAudiobook("book"))
        assertEquals(listOf(second, first), trackRepository.getTracksForAudiobookFlow("book").first())
        assertEquals(3, trackRepository.getAllTracksFlow().first().size)
        trackRepository.deleteTracksForAudiobook("book")
        assertTrue(trackRepository.getTracksForAudiobook("book").isEmpty())
    }

    @Test
    fun `playback repository maps save update most recent flow and delete`() = runTest {
        playbackRepository.savePlaybackState(playback("book", lastPlayedAt = TIME.minusHours(1)))
        playbackRepository.savePlaybackState(playback("second", lastPlayedAt = TIME))
        playbackRepository.updatePosition("book", 2_000)
        playbackRepository.updatePlaybackState("book", 3_000, 1.75f, true)

        assertEquals(3_000L, playbackRepository.getPlaybackState("book")?.positionMs)
        assertEquals(1.75f, playbackRepository.getPlaybackStateFlow("book").first()?.speed ?: 0f, 0.0001f)
        assertEquals("second", playbackRepository.getMostRecentPlaybackState()?.audiobookId)
        assertEquals(2, playbackRepository.getAllPlaybackStatesFlow().first().size)
        playbackRepository.deletePlaybackState("book")
        assertNull(playbackRepository.getPlaybackState("book"))
    }

    @Test
    fun `bookmark repository maps add update list flows lookup and deletes`() = runTest {
        val first = bookmark("b1", positionMs = 2_000)
        val second = bookmark("b2", positionMs = 1_000)
        bookmarkRepository.addBookmark(first)
        bookmarkRepository.addBookmark(second)
        bookmarkRepository.addBookmark(bookmark("other", audiobookId = "other", positionMs = 3_000))

        assertEquals(second, bookmarkRepository.getBookmarkById("b2"))
        assertEquals(listOf(second, first), bookmarkRepository.getBookmarksForAudiobook("book"))
        assertEquals(listOf(second, first), bookmarkRepository.getBookmarksForAudiobookFlow("book").first())
        assertEquals(3, bookmarkRepository.getAllBookmarksFlow().first().size)

        bookmarkRepository.updateBookmark(first.copy(title = "Updated"))
        assertEquals("Updated", bookmarkRepository.getBookmarkById("b1")?.title)
        bookmarkRepository.deleteBookmark("missing")
        bookmarkRepository.deleteBookmark("b2")
        assertNull(bookmarkRepository.getBookmarkById("b2"))
        bookmarkRepository.deleteBookmarksForAudiobook("book")
        assertTrue(bookmarkRepository.getBookmarksForAudiobook("book").isEmpty())
    }

    private fun audiobookEntity(id: String, createdAt: LocalDateTime = TIME) = AudiobookEntity(
        id = id,
        title = "Title $id",
        author = "Author",
        duration = 1_000,
        coverArtPath = null,
        filePath = "/synthetic/$id",
        createdAt = createdAt,
        totalTracks = 1,
    )

    private fun trackEntity(id: String, audiobookId: String = "book", trackNumber: Int = 1) = TrackEntity(
        id = id,
        audiobookId = audiobookId,
        filePath = "/synthetic/$id.mp3",
        title = "Track $id",
        duration = 1_000,
        trackNumber = trackNumber,
    )

    private fun playbackEntity(audiobookId: String, positionMs: Long = 1_000, lastPlayedAt: LocalDateTime = TIME) =
        PlaybackStateEntity(audiobookId, "track", positionMs, 1.0f, false, lastPlayedAt)

    private fun bookmarkEntity(
        id: String,
        audiobookId: String = "book",
        trackId: String = "track",
        positionMs: Long = 1_000,
    ) = BookmarkEntity(id, audiobookId, trackId, positionMs, "Bookmark $id", null, TIME.plusNanos(positionMs))

    private fun audiobook(id: String) = Audiobook(id, "Title", "Author", 1_000, null, "/synthetic/$id", TIME, 1)

    private fun track(id: String, number: Int, audiobookId: String = "book") =
        Track(id, audiobookId, "/synthetic/$id.mp3", "Track $id", 1_000, number)

    private fun playback(audiobookId: String, lastPlayedAt: LocalDateTime = TIME) =
        PlaybackState(audiobookId, "track", 1_000, 1.0f, false, lastPlayedAt)

    private fun bookmark(id: String, audiobookId: String = "book", positionMs: Long = 1_000) =
        Bookmark(id, audiobookId, "track", positionMs, "Bookmark $id", null, TIME.plusNanos(positionMs))

    private companion object {
        val TIME: LocalDateTime = LocalDateTime.of(2026, 9, 29, 12, 0)
    }
}
