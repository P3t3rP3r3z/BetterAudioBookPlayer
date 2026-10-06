package com.aikipunk.betteraudiobookplayer.data.transfer

import androidx.core.net.toUri
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import com.aikipunk.betteraudiobookplayer.domain.repository.AudiobookRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.BookmarkRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.PlaybackStateRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.TrackRepository
import com.aikipunk.betteraudiobookplayer.domain.transfer.SavedBook
import com.aikipunk.betteraudiobookplayer.domain.transfer.SavedBookmark
import com.aikipunk.betteraudiobookplayer.domain.transfer.SavedProgress
import com.aikipunk.betteraudiobookplayer.domain.transfer.SavedTrack
import com.aikipunk.betteraudiobookplayer.domain.transfer.ShelfMatcher
import com.aikipunk.betteraudiobookplayer.domain.transfer.ShelfSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Keeps a copy of the bookshelf (details, progress, bookmarks and notes) in app storage.
 * Android backup / new-phone transfer carries it (see res/xml backup rules); on a phone whose
 * library is empty it becomes a pending restore, applied to each matching book as it's imported.
 */
@Singleton
class ShelfBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audiobookRepository: AudiobookRepository,
    private val trackRepository: TrackRepository,
    private val playbackStateRepository: PlaybackStateRepository,
    private val bookmarkRepository: BookmarkRepository,
) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir = File(context.filesDir, DIR_NAME)
    private val snapshotFile = File(dir, SNAPSHOT_FILE)
    private val pendingFile = File(dir, PENDING_FILE)
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val fileNameCache = ConcurrentHashMap<String, String?>()

    private val _pending = MutableStateFlow<List<SavedBook>>(emptyList())

    /** Books from the previous phone that haven't been matched to an import yet. */
    val pending: StateFlow<List<SavedBook>> = _pending.asStateFlow()

    internal constructor(
        context: Context,
        audiobookRepository: AudiobookRepository,
        trackRepository: TrackRepository,
        playbackStateRepository: PlaybackStateRepository,
        bookmarkRepository: BookmarkRepository,
        scope: CoroutineScope,
    ) : this(context, audiobookRepository, trackRepository, playbackStateRepository, bookmarkRepository) {
        this.scope = scope
    }

    /** Call once at app start: checks for a restore, then keeps the snapshot up to date. */
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            try {
                mutex.withLock { detectRestore() }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read the saved bookshelf", e)
            } finally {
                ready.complete(Unit)
            }
            combine(
                audiobookRepository.getAllAudiobooksFlow(),
                trackRepository.getAllTracksFlow(),
                playbackStateRepository.getAllPlaybackStatesFlow(),
                bookmarkRepository.getAllBookmarksFlow(),
                _pending,
            ) { books, tracks, states, bookmarks, pending -> Shelf(books, tracks, states, bookmarks, pending) }
                .debounce(SAVE_DEBOUNCE_MS)
                .collect { shelf ->
                    try {
                        mutex.withLock { write(snapshotFile, snapshotOf(shelf)) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Couldn't save the bookshelf", e)
                    }
                }
        }
    }

    /** Waits for the startup check, so callers see an accurate [pending]. */
    suspend fun awaitReady() = ready.await()

    /** Forgets the books from the previous phone. */
    fun discardPending() {
        _pending.value = emptyList()
        scope.launch { mutex.withLock { pendingFile.delete() } }
    }

    /**
     * Restores details, progress and bookmarks onto a just-imported book if it matches a pending
     * one. Returns the restored book, or null if it didn't match.
     */
    suspend fun restoreInto(book: Audiobook, tracks: List<Track>): SavedBook? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pendingBooks = _pending.value
            if (pendingBooks.isEmpty()) return@withLock null
            val fileNames = tracks.map { fileNameOf(it.filePath) }
            val imported = ShelfMatcher.ImportedBook(
                title = book.title,
                folderName = folderPathOf(tracks.map { it.filePath })?.substringAfterLast('/'),
                trackFileNames = fileNames,
            )
            val saved = ShelfMatcher.findSaved(imported, pendingBooks) ?: return@withLock null
            apply(saved, book, tracks, fileNames)
            val remaining = pendingBooks - saved
            _pending.value = remaining
            if (remaining.isEmpty()) pendingFile.delete() else write(pendingFile, snapshot(remaining))
            saved
        }
    }

    private suspend fun apply(saved: SavedBook, book: Audiobook, tracks: List<Track>, fileNames: List<String?>) {
        audiobookRepository.updateAudiobook(
            book.copy(
                customTitle = saved.customTitle,
                author = saved.author ?: book.author,
                narrator = saved.narrator ?: book.narrator,
                publisher = saved.publisher ?: book.publisher,
                publishedYear = saved.publishedYear ?: book.publishedYear,
                notes = saved.notes,
                createdAt = saved.addedAt?.let(::parseTime) ?: book.createdAt,
            ),
        )
        saved.progress?.let { progress ->
            val index = ShelfMatcher.importedTrackIndex(saved, progress.trackIndex, fileNames) ?: return@let
            val lastPlayedAt = parseTime(progress.lastPlayedAt) ?: LocalDateTime.now()
            val current = playbackStateRepository.getPlaybackState(book.id)
            // Whichever phone listened most recently wins
            if (current == null || current.lastPlayedAt.isBefore(lastPlayedAt)) {
                playbackStateRepository.savePlaybackState(
                    PlaybackState(
                        audiobookId = book.id,
                        currentTrackId = tracks[index].id,
                        positionMs = clampTo(tracks[index], progress.positionMs),
                        speed = progress.speed,
                        isPlaying = false,
                        lastPlayedAt = lastPlayedAt,
                    ),
                )
            }
        }
        saved.bookmarks.forEach { bookmark ->
            val index = ShelfMatcher.importedTrackIndex(saved, bookmark.trackIndex, fileNames) ?: return@forEach
            bookmarkRepository.addBookmark(
                Bookmark(
                    id = UUID.randomUUID().toString(),
                    audiobookId = book.id,
                    trackId = tracks[index].id,
                    positionMs = clampTo(tracks[index], bookmark.positionMs),
                    title = bookmark.title,
                    description = bookmark.description,
                    createdAt = parseTime(bookmark.createdAt) ?: LocalDateTime.now(),
                ),
            )
        }
    }

    // The new phone's files may be a different rip; never land past the end of a track
    private fun clampTo(track: Track, positionMs: Long): Long =
        if (track.duration > 0) positionMs.coerceIn(0, track.duration - 1) else positionMs.coerceAtLeast(0)

    private suspend fun detectRestore() {
        if (pendingFile.exists()) {
            _pending.value = read(pendingFile)?.books.orEmpty()
            return
        }
        // Library empty but a saved shelf exists: restored from backup or a new phone
        val saved = read(snapshotFile)?.books.orEmpty()
        if (saved.isNotEmpty() && audiobookRepository.getAllAudiobooksFlow().first().isEmpty()) {
            snapshotFile.copyTo(pendingFile, overwrite = true)
            _pending.value = saved
        }
    }

    private fun snapshotOf(shelf: Shelf): ShelfSnapshot {
        val tracksByBook = shelf.tracks.groupBy { it.audiobookId }
        val stateByBook = shelf.states.associateBy { it.audiobookId }
        val bookmarksByBook = shelf.bookmarks.groupBy { it.audiobookId }
        val books = shelf.books.map { book ->
            val tracks = tracksByBook[book.id].orEmpty().sortedBy { it.trackNumber }
            val indexOf = tracks.withIndex().associate { (i, t) -> t.id to i }
            val state = stateByBook[book.id]
            SavedBook(
                title = book.title,
                customTitle = book.customTitle,
                author = book.author,
                narrator = book.narrator,
                publisher = book.publisher,
                publishedYear = book.publishedYear,
                notes = book.notes,
                folderPath = folderPathOf(tracks.map { it.filePath }),
                durationMs = book.duration,
                addedAt = book.createdAt.toString(),
                tracks = tracks.map { SavedTrack(fileNameOf(it.filePath), it.title, it.duration) },
                progress = state?.let { s ->
                    indexOf[s.currentTrackId]?.let { index ->
                        SavedProgress(index, s.positionMs, s.speed, s.lastPlayedAt.toString())
                    }
                },
                bookmarks = bookmarksByBook[book.id].orEmpty().mapNotNull { b ->
                    indexOf[b.trackId]?.let { index ->
                        SavedBookmark(index, b.positionMs, b.title, b.description, b.createdAt.toString())
                    }
                },
            )
        }
        // Books still waiting for their files stay in the snapshot so another move doesn't lose them
        return snapshot(books + shelf.pending)
    }

    private fun snapshot(books: List<SavedBook>) = ShelfSnapshot(savedAt = LocalDateTime.now().toString(), books = books)

    private fun read(file: File): ShelfSnapshot? =
        file.takeIf { it.exists() }?.readText()?.let { json.decodeFromString(ShelfSnapshot.serializer(), it) }

    /** Writes via a temp file so a crash never leaves half a snapshot. */
    private fun write(file: File, snapshot: ShelfSnapshot) {
        dir.mkdirs()
        val temp = File(dir, "${file.name}.tmp")
        temp.writeText(json.encodeToString(ShelfSnapshot.serializer(), snapshot))
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    /** "Audiobooks/Dune" for chapters in storage folders; null when the provider hides paths. */
    private fun folderPathOf(trackUris: List<String>): String? {
        val dirs = trackUris.mapNotNull { documentPath(it)?.substringBeforeLast('/', "") }
        if (dirs.isEmpty()) return null
        val parts = dirs.map { it.split('/') }
        val common = parts.first().indices
            .takeWhile { i -> parts.all { it.getOrNull(i) == parts.first()[i] } }
            .map { parts.first()[it] }
        return common.joinToString("/").takeIf { it.isNotBlank() }
    }

    private fun fileNameOf(trackUri: String): String? = fileNameCache.getOrPut(trackUri) {
        documentPath(trackUri)?.substringAfterLast('/')
            ?: queryDisplayName(trackUri)
    }

    /** "primary:Audiobooks/Dune/01.mp3" → "Audiobooks/Dune/01.mp3" (external storage documents). */
    private fun documentPath(trackUri: String): String? {
        val uri = trackUri.toUri()
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return uri.takeIf { it.scheme == "file" }?.path?.trimStart('/')
        return docId.substringAfter(':', "").takeIf { it.contains('/') || it.contains('.') }
    }

    private fun queryDisplayName(trackUri: String): String? = try {
        context.contentResolver.query(trackUri.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    private fun parseTime(value: String): LocalDateTime? = runCatching { LocalDateTime.parse(value) }.getOrNull()

    private data class Shelf(
        val books: List<Audiobook>,
        val tracks: List<Track>,
        val states: List<PlaybackState>,
        val bookmarks: List<Bookmark>,
        val pending: List<SavedBook>,
    )

    private companion object {
        const val TAG = "ShelfBackup"
        const val DIR_NAME = "bookshelf"
        const val SNAPSHOT_FILE = "bookshelf.json"
        const val PENDING_FILE = "restore-pending.json"
        const val SAVE_DEBOUNCE_MS = 2_000L
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
