package com.aikipunk.betteraudiobookplayer.data.transfer

import android.provider.MediaStore
import com.aikipunk.betteraudiobookplayer.domain.transfer.ShelfMatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioFileFinderTest {
    @Test
    fun `folderPickerUri builds primary external storage document Uri`() {
        val uri = AudioFileFinder.folderPickerUri(
            ShelfMatcher.FolderCandidate(MediaStore.VOLUME_EXTERNAL_PRIMARY, "Audiobooks/Dune", 3, 2),
        )

        assertEquals("content://com.android.externalstorage.documents/document/primary%3AAudiobooks%2FDune", uri.toString())
    }

    @Test
    fun `folderPickerUri builds removable volume document Uri`() {
        val uri = AudioFileFinder.folderPickerUri(
            ShelfMatcher.FolderCandidate("9c33-6bba", "Books/Novel", 1, 1),
        )

        assertEquals("content://com.android.externalstorage.documents/document/9C33-6BBA%3ABooks%2FNovel", uri.toString())
    }
}
