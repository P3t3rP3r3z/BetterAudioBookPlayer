package com.aikipunk.betteraudiobookplayer.data.filediscovery

import androidx.core.net.toUri
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.aikipunk.betteraudiobookplayer.domain.library.BookDetails
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedAudiobook(
    val audiobook: Audiobook,
    val tracks: List<Track>,
)

enum class FolderImportMode {
    /** The whole folder is one book; every audio file is a chapter. */
    CHAPTERS,

    /** Each loose audio file is a book, and each sub-folder is a book. */
    BOOKS,
}

class NoAudioFilesException(folderName: String) :
    IllegalStateException("No audio files found in \"$folderName\"")

internal data class FolderEntry(val uri: Uri, val name: String, val relativePath: String)

/** Result of listing a picked folder, before the user decides how to import it. */
class FolderScan internal constructor(
    val treeUri: Uri,
    val folderName: String,
    internal val audio: List<FolderEntry>,
    internal val images: List<FolderEntry>,
) {
    val audioFileCount: Int get() = audio.size
}

/**
 * Imports a folder chosen with ACTION_OPEN_DOCUMENT_TREE.
 * Call [scan] first, then [build] with the mode the user picked.
 */
@Singleton
class FolderAudiobookImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Cleans up after a book is removed from the library: deletes the app's cached cover copy
     * and gives back folder access if no remaining book comes from the same folder.
     * The user's audio files are never touched.
     */
    fun releaseBook(removed: Audiobook, remainingSourcePaths: List<String>) {
        File(File(context.filesDir, "covers"), "cover_${removed.id}").delete()

        val treeUri = removed.filePath.substringBefore('#')
        val stillUsed = remainingSourcePaths.any { it.substringBefore('#') == treeUri }
        if (!stillUsed && treeUri.startsWith("content://")) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    treeUri.toUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    private data class TrackInfo(
        val durationMs: Long,
        val title: String?,
        val album: String?,
        val author: String?,
        val narrator: String?,
        val year: Int? = null,
    )

    suspend fun scan(treeUri: Uri): FolderScan = withContext(Dispatchers.IO) {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val folderName = queryDisplayName(DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId))
            ?: "Audiobook"

        val audio = mutableListOf<FolderEntry>()
        val images = mutableListOf<FolderEntry>()
        collect(treeUri, rootDocId, "", audio, images)
        if (audio.isEmpty()) throw NoAudioFilesException(folderName)

        FolderScan(
            treeUri = treeUri,
            folderName = folderName,
            audio = audio.sortedWith(compareBy(FolderBookRules.naturalOrder) { it.relativePath }),
            images = images,
        )
    }

    suspend fun build(scan: FolderScan, mode: FolderImportMode): List<ImportedAudiobook> =
        withContext(Dispatchers.IO) {
            // Keep read access across app restarts
            context.contentResolver.takePersistableUriPermission(
                scan.treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            when (mode) {
                FolderImportMode.CHAPTERS -> listOf(buildAsSingleBook(scan))
                FolderImportMode.BOOKS -> buildAsSeparateBooks(scan)
            }
        }

    private fun buildAsSingleBook(scan: FolderScan): ImportedAudiobook {
        val topLevelImages = scan.images.filter { '/' !in it.relativePath }
        val cover = FolderBookRules.pickCoverIndex(topLevelImages.map { it.name })
            ?.let { topLevelImages[it].uri.toString() }
        return buildBook(
            bookId = scan.treeUri.toString().hashCode().toString(),
            fallbackTitle = scan.folderName,
            chapters = scan.audio,
            folderCover = cover,
            sourcePath = scan.treeUri.toString(),
        )
    }

    private fun buildAsSeparateBooks(scan: FolderScan): List<ImportedAudiobook> {
        val topLevelImages = scan.images.filter { '/' !in it.relativePath }
        return scan.audio
            .groupBy { FolderBookRules.bookGroupKey(it.relativePath) }
            .map { (key, chapters) ->
                val isLooseFile = chapters.size == 1 && chapters.first().relativePath == key
                val cover: String?
                val fallbackTitle: String
                if (isLooseFile) {
                    fallbackTitle = FolderBookRules.titleFromFileName(key)
                    cover = FolderBookRules.matchingCoverIndex(key, topLevelImages.map { it.name })
                        ?.let { topLevelImages[it].uri.toString() }
                } else {
                    fallbackTitle = key
                    val folderImages = scan.images.filter { it.relativePath == "$key/${it.name}" }
                    cover = FolderBookRules.pickCoverIndex(folderImages.map { it.name })
                        ?.let { folderImages[it].uri.toString() }
                }
                buildBook(
                    bookId = "${scan.treeUri}#$key".hashCode().toString(),
                    fallbackTitle = fallbackTitle,
                    chapters = chapters,
                    folderCover = cover,
                    sourcePath = "${scan.treeUri}#$key",
                )
            }
    }

    private fun buildBook(
        bookId: String,
        fallbackTitle: String,
        chapters: List<FolderEntry>,
        folderCover: String?,
        sourcePath: String,
    ): ImportedAudiobook {
        val infos = chapters.mapIndexed { index, entry -> readTrackInfo(entry.uri, index) }
        val tracks = chapters.mapIndexed { index, entry ->
            Track(
                // Scoped to the book so the same file can't collide across import modes
                id = "$bookId:${entry.uri}".hashCode().toString(),
                audiobookId = bookId,
                filePath = entry.uri.toString(),
                title = infos[index].title ?: FolderBookRules.titleFromFileName(entry.name),
                duration = infos[index].durationMs,
                trackNumber = index + 1,
            )
        }
        val audiobook = Audiobook(
            id = bookId,
            title = infos.first().album ?: fallbackTitle,
            author = infos.firstNotNullOfOrNull { it.author },
            narrator = infos.firstNotNullOfOrNull { it.narrator },
            publishedYear = infos.firstNotNullOfOrNull { it.year },
            duration = tracks.sumOf { it.duration },
            coverArtPath = folderCover ?: saveEmbeddedCover(chapters.first().uri, bookId),
            filePath = sourcePath,
            totalTracks = tracks.size,
        )
        return ImportedAudiobook(audiobook, tracks)
    }

    private fun collect(
        treeUri: Uri,
        parentDocId: String,
        relativeDir: String,
        audio: MutableList<FolderEntry>,
        images: MutableList<FolderEntry>,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
            ?: error("Unable to list folder contents")
        cursor.use {
            while (it.moveToNext()) {
                val docId = it.getString(0)
                val name = it.getString(1) ?: continue
                val mime = it.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!name.startsWith(".")) collect(treeUri, docId, "$relativeDir$name/", audio, images)
                    continue
                }
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                when {
                    FolderBookRules.isAudio(name, mime) -> audio += FolderEntry(uri, name, relativeDir + name)
                    FolderBookRules.isImage(name, mime) -> images += FolderEntry(uri, name, relativeDir + name)
                }
            }
        }
    }

    private fun readTrackInfo(uri: Uri, index: Int): TrackInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            TrackInfo(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() },
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() },
                author = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                    ?.takeIf { it.isNotBlank() },
                // Audiobook tools (Audible, OpenAudible, m4b-tool) conventionally store the narrator as composer
                narrator = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                    ?.takeIf { it.isNotBlank() },
                year = BookDetails.parseYear(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)),
            )
        } catch (e: RuntimeException) {
            // Unreadable tags shouldn't block the import; fall back to file name and unknown duration
            Log.w(TAG, "Could not read metadata for chapter ${index + 1}", e)
            TrackInfo(0L, null, null, null, null)
        } finally {
            retriever.release()
        }
    }

    private fun saveEmbeddedCover(audioUri: Uri, bookId: String): String? {
        val retriever = MediaMetadataRetriever()
        val bytes = try {
            retriever.setDataSource(context, audioUri)
            retriever.embeddedPicture
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not read embedded cover art", e)
            null
        } finally {
            retriever.release()
        } ?: return null

        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
        val coverFile = File(coversDir, "cover_$bookId")
        coverFile.writeBytes(bytes)
        return Uri.fromFile(coverFile).toString()
    }

    private fun queryDisplayName(documentUri: Uri): String? =
        context.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

    private companion object {
        const val TAG = "FolderImporter"
    }
}
