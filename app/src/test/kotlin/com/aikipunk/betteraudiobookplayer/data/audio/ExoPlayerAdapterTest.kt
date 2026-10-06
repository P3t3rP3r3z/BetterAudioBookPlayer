package com.aikipunk.betteraudiobookplayer.data.audio

import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.aikipunk.betteraudiobookplayer.data.preferences.UserPreferences
import com.aikipunk.betteraudiobookplayer.domain.audio.NowPlayingInfo
import com.aikipunk.betteraudiobookplayer.domain.audio.PlaylistItem
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExoPlayerAdapterTest {
    private lateinit var adapter: ExoPlayerAdapter
    private lateinit var preferences: UserPreferences
    private lateinit var audioDir: File

    @Before
    fun setUp() {
        audioDir = File("build/test-audio/exoplayer").apply { mkdirs() }
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        preferences = UserPreferences(ApplicationProvider.getApplicationContext())
        preferences.setAppVolume(100)
        preferences.setGradualVolumeFade(false)
        adapter = ExoPlayerAdapter(ApplicationProvider.getApplicationContext(), preferences)
        drainMain()
    }

    @After
    fun tearDown() {
        adapter.release()
        drainMain()
    }

    @Test
    fun `loadAudio prepares a playable file with metadata and clears playlist key`() = runBlocking {
        val wav = silentWav("single.wav", durationMs = 300)

        adapter.loadAudio(wav.absolutePath, 120, NowPlayingInfo(title = "Single", artist = "Narrator"))
        drainMain()
        eventually { adapter.currentSource() == wav.absolutePath }

        assertNull(adapter.currentPlaylistKey())
        assertEquals(wav.absolutePath, adapter.currentSource())
        assertEquals(0, adapter.currentItemIndex.value)
        assertEquals(120L, adapter.currentPosition.value)
        assertEquals("Single", adapter.player.currentMediaItem?.mediaMetadata?.title.toString())
        assertEquals("Narrator", adapter.player.currentMediaItem?.mediaMetadata?.artist.toString())
        assertTrue(adapter.duration.value >= 0L)
    }

    @Test
    fun `loadPlaylist clamps start index exposes selected source and updateNowPlaying replaces metadata`() = runBlocking {
        val first = silentWav("first.wav", durationMs = 250)
        val second = silentWav("second.wav", durationMs = 250)

        adapter.loadPlaylist(
            key = "book1",
            items = listOf(
                PlaylistItem(first.absolutePath, NowPlayingInfo("Old one", album = "Book")),
                PlaylistItem(second.absolutePath, NowPlayingInfo("Old two", album = "Book")),
            ),
            startIndex = 99,
            positionMs = -5,
        )
        drainMain()
        eventually { adapter.currentPlaylistKey() == "book1" }

        assertEquals("book1", adapter.currentPlaylistKey())
        assertEquals(1, adapter.currentItemIndex.value)
        assertEquals(second.absolutePath, adapter.currentSource())
        assertEquals(0L, adapter.currentPosition.value)

        adapter.updateNowPlaying(listOf(NowPlayingInfo("New one"), NowPlayingInfo("New two", artist = "Writer")))
        drainMain()
        eventually { adapter.player.getMediaItemAt(1).mediaMetadata.title.toString() == "New two" }

        assertEquals("New two", adapter.player.getMediaItemAt(1).mediaMetadata.title.toString())
        assertEquals("Writer", adapter.player.getMediaItemAt(1).mediaMetadata.artist.toString())
    }

    @Test
    fun `loadPlaylist reports its key immediately so a second open does not reload it`() = runBlocking {
        val wav = silentWav("key.wav", durationMs = 250)

        adapter.loadPlaylist("book1", listOf(PlaylistItem(wav.absolutePath)), 0, 0)

        assertEquals("book1", adapter.currentPlaylistKey())
        drainMain()
        eventually { adapter.currentSource() == wav.absolutePath }
        assertEquals("book1", adapter.currentPlaylistKey())
    }

    @Test
    fun `seekToItem updates current item and ignores out of range indexes`() = runBlocking {
        val first = silentWav("chapter-1.wav", durationMs = 400)
        val second = silentWav("chapter-2.wav", durationMs = 400)
        adapter.loadPlaylist("book1", listOf(PlaylistItem(first.absolutePath), PlaylistItem(second.absolutePath)), 0, 0)
        drainMain()
        eventually { adapter.player.mediaItemCount == 2 }

        adapter.seekToItem(1, 150)
        drainMain()
        eventually { adapter.currentItemIndex.value == 1 }

        assertEquals(1, adapter.currentItemIndex.value)
        assertEquals(150L, adapter.currentPosition.value)
        assertEquals(second.absolutePath, adapter.currentSource())

        adapter.seekToItem(4, 300)
        drainMain()
        assertEquals(1, adapter.currentItemIndex.value)
        assertEquals(150L, adapter.currentPosition.value)
    }

    @Test
    fun `play pause seek speed volume and preferred output delegate to player`() = runBlocking {
        val wav = silentWav("controls.wav", durationMs = 500)
        adapter.loadAudio(wav.absolutePath, 0)
        drainMain()
        eventually { adapter.player.mediaItemCount == 1 }

        adapter.seekTo(-50)
        adapter.setSpeed(3.0f)
        adapter.setVolume(2.0f)
        adapter.setPreferredAudioDevice(null)
        adapter.play()
        drainMain()

        assertEquals(0L, adapter.currentPosition.value)
        assertEquals(2.0f, adapter.playbackSpeed.value, 0.0001f)
        assertEquals(2.0f, adapter.getCurrentSpeed(), 0.0001f)
        assertEquals(1.0f, adapter.player.volume, 0.0001f)
        assertTrue(adapter.player.playWhenReady)

        adapter.setSpeed(0.1f)
        adapter.setVolume(-1.0f)
        adapter.pause()
        drainMain()

        assertEquals(0.5f, adapter.playbackSpeed.value, 0.0001f)
        assertEquals(0.0f, adapter.player.volume, 0.0001f)
        assertFalse(adapter.player.playWhenReady)
    }

    @Test
    fun `app volume preference changes update player volume`() {
        preferences.setAppVolume(50)
        drainMain()

        assertEquals(0.5f, adapter.player.volume, 0.0001f)

        preferences.setAppVolume(80)
        eventually { kotlin.math.abs(adapter.player.volume - 0.8f) < 0.0001f }

        assertEquals(0.8f, adapter.player.volume, 0.0001f)
    }

    @Test
    fun `stop clears playlist sources key and position and release is idempotent`() = runBlocking {
        val wav = silentWav("stop.wav", durationMs = 300)
        adapter.loadPlaylist("book1", listOf(PlaylistItem(wav.absolutePath)), 0, 100)
        drainMain()
        eventually { adapter.currentPlaylistKey() == "book1" }

        adapter.stop()
        drainMain()
        eventually { adapter.currentSource() == null && adapter.currentPlaylistKey() == null }

        assertNull(adapter.currentPlaylistKey())
        assertNull(adapter.currentSource())
        assertEquals(0L, adapter.currentPosition.value)

        adapter.release()
        drainMain()
        assertNull(adapter.currentPlaylistKey())
        assertNull(adapter.currentSource())
    }

    @Test
    fun `seek increments match audiobook skip policy`() {
        assertEquals(10_000L, ExoPlayerAdapter.SEEK_BACK_MS)
        assertEquals(30_000L, ExoPlayerAdapter.SEEK_FORWARD_MS)
    }

    private fun eventually(condition: () -> Boolean) {
        repeat(5_000) {
            drainMain()
            if (condition()) return
            Thread.yield()
        }
        assertTrue("Condition was not met", condition())
    }

    private fun drainMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun silentWav(name: String, durationMs: Int): File {
        val file = File(audioDir, name)
        val sampleRate = 8_000
        val bytesPerSample = 2
        val dataSize = sampleRate * durationMs / 1_000 * bytesPerSample
        FileOutputStream(file).use { out ->
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            out.write(intLe(36 + dataSize))
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            out.write(intLe(16))
            out.write(shortLe(1))
            out.write(shortLe(1))
            out.write(intLe(sampleRate))
            out.write(intLe(sampleRate * bytesPerSample))
            out.write(shortLe(bytesPerSample))
            out.write(shortLe(16))
            out.write("data".toByteArray(Charsets.US_ASCII))
            out.write(intLe(dataSize))
            out.write(ByteArray(dataSize))
        }
        return file
    }

    private fun intLe(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun shortLe(value: Int): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
}
