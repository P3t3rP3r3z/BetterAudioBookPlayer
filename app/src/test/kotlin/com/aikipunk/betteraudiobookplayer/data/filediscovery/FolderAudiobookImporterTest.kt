package com.aikipunk.betteraudiobookplayer.data.filediscovery

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FolderAudiobookImporterTest {
    @Test
    fun `releaseBook deletes cached cover and keeps shared folder permission`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importer = FolderAudiobookImporter(context)
        val cover = File(context.filesDir, "covers/cover_book").apply { parentFile?.mkdirs(); writeText("cover") }
        val book = Audiobook("book", "Book", null, 0, Uri.fromFile(cover).toString(), "content://provider/tree/book#Book")

        importer.releaseBook(book, remainingSourcePaths = listOf("content://provider/tree/book#Other"))

        assertFalse(cover.exists())
    }

    @Test
    fun `releaseBook tolerates releasing a content folder that is no longer used`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importer = FolderAudiobookImporter(context)
        val book = Audiobook("book", "Book", null, 0, null, "content://provider/tree/book#Book")

        importer.releaseBook(book, remainingSourcePaths = emptyList())
    }
}
