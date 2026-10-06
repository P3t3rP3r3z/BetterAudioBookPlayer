package com.aikipunk.betteraudiobookplayer.data.filediscovery

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaMetadataRetriever

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderAudiobookImporterProviderTest {
    @Test
    fun `scan traverses document tree filters audio and images and sorts chapters naturally`() = runTest {
        FakeDocsProvider.install(defaultTree())
        val importer = FolderAudiobookImporter(ApplicationProvider.getApplicationContext())

        val scan = importer.scan(FakeDocsProvider.treeUri())

        assertEquals("Dune Folder", scan.folderName)
        assertEquals(4, scan.audioFileCount)
        assertEquals(listOf("CD1/Chapter 1.mp3", "CD1/Chapter 2.mp3", "CD1/Chapter 10.mp3", "Loose Book.m4b"), scan.audio.map { it.relativePath })
        assertEquals(listOf("cover.jpg", "CD1/folder.png", "Loose Book.jpg"), scan.images.map { it.relativePath })
    }

    @Test
    fun `scan throws NoAudioFilesException for a tree without audio`() = runTest {
        FakeDocsProvider.install(
            mapOf(
                "root" to FakeDoc("root", "Empty Folder", DocumentsContract.Document.MIME_TYPE_DIR, children = listOf("cover")),
                "cover" to FakeDoc("cover", "cover.jpg", "image/jpeg"),
            ),
        )
        val importer = FolderAudiobookImporter(ApplicationProvider.getApplicationContext())

        val result = runCatching { importer.scan(FakeDocsProvider.treeUri()) }

        assertTrue(result.exceptionOrNull() is NoAudioFilesException)
        assertTrue(result.exceptionOrNull()?.message?.contains("Empty Folder") == true)
    }

    @Test
    fun `build chapters creates one audiobook with folder cover and metadata ordered by chapter`() = runTest {
        FakeDocsProvider.install(defaultTree())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importer = FolderAudiobookImporter(context)
        metadata("cd1-1", title = "Opening", album = "Metadata Album", duration = "1000", author = "Writer", narrator = "Reader", year = "2024")
        metadata("cd1-2", title = "Middle", duration = "2000")
        metadata("cd1-10", title = "Finale", duration = "3000")
        metadata("loose", title = "Loose", duration = "4000")
        val scan = importer.scan(FakeDocsProvider.treeUri())

        val imported = importer.build(scan, FolderImportMode.CHAPTERS).single()

        assertEquals("Metadata Album", imported.audiobook.title)
        assertEquals("Writer", imported.audiobook.author)
        assertEquals("Reader", imported.audiobook.narrator)
        assertEquals(2024, imported.audiobook.publishedYear)
        assertEquals(10_000L, imported.audiobook.duration)
        assertEquals(FakeDocsProvider.docUri("cover").toString(), imported.audiobook.coverArtPath)
        assertEquals(listOf("Opening", "Middle", "Finale", "Loose"), imported.tracks.map { it.title })
        assertEquals(listOf(1, 2, 3, 4), imported.tracks.map { it.trackNumber })
    }

    @Test
    fun `build books splits loose audio and subfolders with matching covers`() = runTest {
        FakeDocsProvider.install(defaultTree())
        val importer = FolderAudiobookImporter(ApplicationProvider.getApplicationContext())
        metadata("cd1-1", duration = "1000")
        metadata("cd1-2", duration = "2000")
        metadata("cd1-10", duration = "3000")
        metadata("loose", duration = "4000")
        val scan = importer.scan(FakeDocsProvider.treeUri())

        val imported = importer.build(scan, FolderImportMode.BOOKS).sortedBy { it.audiobook.title }

        assertEquals(listOf("CD1", "Loose Book"), imported.map { it.audiobook.title })
        val cd = imported.first { it.audiobook.title == "CD1" }
        val loose = imported.first { it.audiobook.title == "Loose Book" }
        assertEquals(3, cd.tracks.size)
        assertEquals(FakeDocsProvider.docUri("cd1-cover").toString(), cd.audiobook.coverArtPath)
        assertEquals(1, loose.tracks.size)
        assertEquals(FakeDocsProvider.docUri("loose-cover").toString(), loose.audiobook.coverArtPath)
        assertNotNull(loose.tracks.single().id)
    }

    private fun metadata(
        id: String,
        title: String? = null,
        album: String? = null,
        duration: String = "0",
        author: String? = null,
        narrator: String? = null,
        year: String? = null,
    ) {
        val uri = FakeDocsProvider.docUri(id).toString()
        ShadowMediaMetadataRetriever.addMetadata(uri, MediaMetadataRetriever.METADATA_KEY_DURATION, duration)
        title?.let { ShadowMediaMetadataRetriever.addMetadata(uri, MediaMetadataRetriever.METADATA_KEY_TITLE, it) }
        album?.let { ShadowMediaMetadataRetriever.addMetadata(uri, MediaMetadataRetriever.METADATA_KEY_ALBUM, it) }
        author?.let { ShadowMediaMetadataRetriever.addMetadata(uri, MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, it) }
        narrator?.let { ShadowMediaMetadataRetriever.addMetadata(uri, MediaMetadataRetriever.METADATA_KEY_COMPOSER, it) }
        year?.let { ShadowMediaMetadataRetriever.addMetadata(uri, MediaMetadataRetriever.METADATA_KEY_YEAR, it) }
    }

    private fun defaultTree() = mapOf(
        "root" to FakeDoc("root", "Dune Folder", DocumentsContract.Document.MIME_TYPE_DIR, children = listOf("cover", "cd1", "loose", "loose-cover", "notes", "hidden")),
        "cover" to FakeDoc("cover", "cover.jpg", "image/jpeg"),
        "cd1" to FakeDoc("cd1", "CD1", DocumentsContract.Document.MIME_TYPE_DIR, children = listOf("cd1-10", "cd1-2", "cd1-1", "cd1-cover")),
        "cd1-10" to FakeDoc("cd1-10", "Chapter 10.mp3", "audio/mpeg"),
        "cd1-2" to FakeDoc("cd1-2", "Chapter 2.mp3", "audio/mpeg"),
        "cd1-1" to FakeDoc("cd1-1", "Chapter 1.mp3", "audio/mpeg"),
        "cd1-cover" to FakeDoc("cd1-cover", "folder.png", "image/png"),
        "loose" to FakeDoc("loose", "Loose Book.m4b", "application/octet-stream"),
        "loose-cover" to FakeDoc("loose-cover", "Loose Book.jpg", "image/jpeg"),
        "notes" to FakeDoc("notes", "notes.txt", "text/plain"),
        "hidden" to FakeDoc("hidden", ".hidden.mp3", "audio/mpeg"),
    )
}

data class FakeDoc(
    val id: String,
    val name: String,
    val mimeType: String,
    val children: List<String> = emptyList(),
)

class FakeDocsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, queryArgs: Bundle?, cancellationSignal: CancellationSignal?): Cursor? =
        query(uri, projection, null, null, null)

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val columns = projection ?: arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val cursor = MatrixCursor(columns)
        val segments = uri.pathSegments
        if (segments.lastOrNull() == "children") {
            val parentId = segments[segments.lastIndex - 1]
            docs[parentId]?.children.orEmpty().forEach { childId -> cursor.addDoc(columns, docs.getValue(childId)) }
        } else {
            cursor.addDoc(columns, docs.getValue(DocumentsContract.getDocumentId(uri)))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = null

    private fun MatrixCursor.addDoc(columns: Array<out String>, doc: FakeDoc) {
        addRow(columns.map { column ->
            when (column) {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID -> doc.id
                DocumentsContract.Document.COLUMN_DISPLAY_NAME -> doc.name
                DocumentsContract.Document.COLUMN_MIME_TYPE -> doc.mimeType
                else -> null
            }
        }.toTypedArray())
    }

    companion object {
        private const val AUTHORITY = "com.example.fakedocs"
        lateinit var docs: Map<String, FakeDoc>

        fun install(docs: Map<String, FakeDoc>) {
            this.docs = docs
            ShadowMediaMetadataRetriever.reset()
            Robolectric.buildContentProvider(FakeDocsProvider::class.java).create(AUTHORITY)
        }

        fun treeUri(): Uri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root")
        fun docUri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri(), id)
    }
}
