package com.aikipunk.betteraudiobookplayer.service

import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import androidx.test.core.app.ApplicationProvider
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.testutil.FakeAudiobookRepository
import com.aikipunk.betteraudiobookplayer.testutil.FakePlaybackStateRepository
import com.aikipunk.betteraudiobookplayer.testutil.FakeTrackRepository
import com.aikipunk.betteraudiobookplayer.testutil.audiobook
import com.aikipunk.betteraudiobookplayer.testutil.track
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaBrowseTreeTest {
    @Test
    fun `root and root params expose Android Auto folders as browsable grid`() {
        val tree = tree()
        val root = tree.root()
        val extras = tree.rootParams().extras

        assertEquals("root", root.mediaId)
        assertEquals("Better Audiobook Player", root.mediaMetadata.title.toString())
        assertTrue(root.mediaMetadata.isBrowsable == true)
        assertEquals(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM, extras?.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE))
        assertEquals(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM, extras?.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE))
    }

    @Test
    fun `root children are continue listening and library folders`() = runTest {
        val children = tree().children("root").orEmpty()

        assertEquals(listOf("continue", "library"), children.map { it.mediaId })
        assertTrue(children.all { it.mediaMetadata.isBrowsable == true })
        assertTrue(children.none { it.mediaMetadata.isPlayable == true })
    }

    @Test
    fun `library children are sorted by title and include completion status`() = runTest {
        val books = listOf(audiobook("book2", title = "The Zebra", duration = 100_000), audiobook("book1", title = "Alpha", duration = 100_000))
        val tracks = listOf(track("b1t1", "book1", duration = 100_000), track("b2t1", "book2", duration = 100_000))
        val states = listOf(PlaybackState("book2", "b2t1", 99_000), PlaybackState("book1", "b1t1", 0))
        val items = tree(books, tracks, states).children("library").orEmpty()

        assertEquals(listOf("book:book1", "book:book2"), items.map { it.mediaId })
        assertEquals(MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED, completion(items[0]))
        assertEquals(MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED, completion(items[1]))
        assertTrue(items.all { it.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_AUDIO_BOOK })
        assertTrue(items.all { it.mediaMetadata.isPlayable == true })
    }

    @Test
    fun `continue listening contains only in progress books sorted by recent activity`() = runTest {
        val books = listOf(audiobook("old", title = "Old", duration = 100_000), audiobook("new", title = "New", duration = 100_000), audiobook("done", title = "Done", duration = 100_000))
        val tracks = listOf(track("ot", "old", duration = 100_000), track("nt", "new", duration = 100_000), track("dt", "done", duration = 100_000))
        val states = listOf(
            PlaybackState("old", "ot", 10_000, lastPlayedAt = LocalDateTime.of(2026, 1, 1, 1, 0)),
            PlaybackState("new", "nt", 20_000, lastPlayedAt = LocalDateTime.of(2026, 1, 2, 1, 0)),
            PlaybackState("done", "dt", 99_000, lastPlayedAt = LocalDateTime.of(2026, 1, 3, 1, 0)),
        )

        val items = tree(books, tracks, states).children("continue").orEmpty()

        assertEquals(listOf("book:new", "book:old"), items.map { it.mediaId })
        assertEquals(MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED, completion(items[0]))
        assertEquals(0.2, items[0].mediaMetadata.extras?.getDouble(MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE) ?: -1.0, 0.001)
    }

    @Test
    fun `item resolves folders and book items while unknown ids return null`() = runTest {
        val item = tree(listOf(audiobook("book1")), listOf(track("t1")), emptyList()).item("book:book1")

        assertEquals("Library", tree().item("library")?.mediaMetadata?.title.toString())
        assertEquals("book:book1", item?.mediaId)
        assertNull(tree().item("book:"))
        assertNull(tree().children("not-a-folder"))
    }

    @Test
    fun `search filters books and bestMatch returns first match`() = runTest {
        val tree = tree(listOf(audiobook("book1", title = "Space Journey"), audiobook("book2", title = "Ocean Story")), listOf(track("t1"), track("t2", "book2")), emptyList())

        assertEquals(listOf("book:book1"), tree.search("space").map { it.mediaId })
        assertEquals("book:book2", tree.bestMatch("ocean")?.mediaId)
        assertTrue(tree.search("absent").isEmpty())
    }

    @Test
    fun `bestMatch understands spoken requests with filler words`() = runTest {
        val tree = tree(listOf(audiobook("book1", title = "Space Journey"), audiobook("book2", title = "Ocean Story")), listOf(track("t1"), track("t2", "book2")), emptyList())

        assertEquals("book:book2", tree.bestMatch("the audiobook Ocean Story by Author")?.mediaId)
        assertEquals("book2", tree.bestMatchBookId("play ocean story"))
        assertNull(tree.bestMatchBookId("Moby Dick"))
    }

    @Test
    fun `book media id round trips valid ids and rejects invalid ids`() {
        assertEquals("book:abc", MediaBrowseTree.bookMediaId("abc"))
        assertEquals("abc", MediaBrowseTree.bookIdOf("book:abc"))
        assertNull(MediaBrowseTree.bookIdOf("abc"))
        assertNull(MediaBrowseTree.bookIdOf("book:"))
    }

    private fun completion(item: androidx.media3.common.MediaItem): Int =
        item.mediaMetadata.extras?.getInt(MediaConstants.EXTRAS_KEY_COMPLETION_STATUS) ?: -1

    private fun tree(
        books: List<com.aikipunk.betteraudiobookplayer.domain.model.Audiobook> = emptyList(),
        tracks: List<com.aikipunk.betteraudiobookplayer.domain.model.Track> = emptyList(),
        states: List<PlaybackState> = emptyList(),
    ) = MediaBrowseTree(
        ApplicationProvider.getApplicationContext(),
        FakeAudiobookRepository(books),
        FakeTrackRepository(tracks),
        FakePlaybackStateRepository(states),
    )
}
