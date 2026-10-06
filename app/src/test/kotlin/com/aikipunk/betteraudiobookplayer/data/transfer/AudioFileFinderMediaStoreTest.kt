package com.aikipunk.betteraudiobookplayer.data.transfer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AudioFileFinderMediaStoreTest {
    @Test
    @Config(sdk = [34])
    fun `allAudioFiles reads scoped media store rows`() = runTest {
        FakeMediaProvider.install(
            listOf(
                FakeMediaRow(volume = "external_primary", relativePath = "Audiobooks/Dune/", displayName = "01.mp3", album = "Dune"),
                FakeMediaRow(volume = "external_primary", relativePath = "Audiobooks/Dune", displayName = "02.mp3", album = null),
                FakeMediaRow(volume = null, relativePath = "Skip", displayName = "missing-volume.mp3", album = null),
            ),
        )
        val finder = AudioFileFinder(ApplicationProvider.getApplicationContext())

        val files = finder.allAudioFiles()

        assertEquals(2, files.size)
        assertEquals("external_primary", files[0].volume)
        assertEquals("Audiobooks/Dune", files[0].folderPath)
        assertEquals("01.mp3", files[0].fileName)
        assertEquals("Dune", files[0].album)
        assertEquals("Audiobooks/Dune", files[1].folderPath)
    }

    @Test
    @Config(sdk = [28])
    fun `allAudioFiles reads legacy media paths and splits storage volumes`() = runTest {
        FakeMediaProvider.install(
            listOf(
                FakeMediaRow(data = "/storage/emulated/0/Books/Primary/01.mp3", album = "Primary"),
                FakeMediaRow(data = "/storage/9C33-6BBA/Books/Card/02.mp3", album = "Card"),
                FakeMediaRow(data = "/not-storage/Skip/03.mp3", album = "Skip"),
            ),
        )
        val finder = AudioFileFinder(ApplicationProvider.getApplicationContext())

        val files = finder.allAudioFiles()

        assertEquals(2, files.size)
        assertEquals("external_primary", files[0].volume)
        assertEquals("Books/Primary", files[0].folderPath)
        assertEquals("01.mp3", files[0].fileName)
        assertEquals("9c33-6bba", files[1].volume)
        assertEquals("Books/Card", files[1].folderPath)
        assertEquals("Card", files[1].album)
    }
}

data class FakeMediaRow(
    val volume: String? = null,
    val relativePath: String? = null,
    val displayName: String? = null,
    val album: String? = null,
    val data: String? = null,
)

class FakeMediaProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, queryArgs: Bundle?, cancellationSignal: CancellationSignal?): Cursor? =
        query(uri, projection, null, null, null)

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val columns = projection ?: emptyArray()
        return MatrixCursor(columns).apply {
            rows.forEach { media ->
                addRow(columns.map { column ->
                    when (column) {
                        MediaStore.Audio.Media.VOLUME_NAME -> media.volume
                        MediaStore.Audio.Media.RELATIVE_PATH -> media.relativePath
                        MediaStore.Audio.Media.DISPLAY_NAME -> media.displayName
                        MediaStore.Audio.Media.ALBUM -> media.album
                        @Suppress("DEPRECATION")
                        MediaStore.Audio.Media.DATA -> media.data
                        else -> null
                    }
                }.toTypedArray())
            }
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private var rows: List<FakeMediaRow> = emptyList()
        fun install(rows: List<FakeMediaRow>) {
            this.rows = rows
            Robolectric.buildContentProvider(FakeMediaProvider::class.java).create(MediaStore.AUTHORITY)
        }
    }
}
