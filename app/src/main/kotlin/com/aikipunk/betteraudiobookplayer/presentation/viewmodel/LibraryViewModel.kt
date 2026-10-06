package com.aikipunk.betteraudiobookplayer.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aikipunk.betteraudiobookplayer.data.filediscovery.FolderAudiobookImporter
import com.aikipunk.betteraudiobookplayer.data.filediscovery.FolderImportMode
import com.aikipunk.betteraudiobookplayer.data.filediscovery.FolderScan
import com.aikipunk.betteraudiobookplayer.data.library.AudiobookLibraryManager
import com.aikipunk.betteraudiobookplayer.data.preferences.UserPreferences
import com.aikipunk.betteraudiobookplayer.data.transfer.AudioFileFinder
import com.aikipunk.betteraudiobookplayer.data.transfer.ShelfBackup
import com.aikipunk.betteraudiobookplayer.domain.transfer.SavedBook
import com.aikipunk.betteraudiobookplayer.domain.transfer.ShelfMatcher
import com.aikipunk.betteraudiobookplayer.domain.library.BookDetails
import com.aikipunk.betteraudiobookplayer.domain.library.BookProgress
import com.aikipunk.betteraudiobookplayer.domain.library.LibraryFilter
import com.aikipunk.betteraudiobookplayer.domain.library.LibraryShelf
import com.aikipunk.betteraudiobookplayer.domain.library.LibrarySort
import com.aikipunk.betteraudiobookplayer.domain.library.LibraryViewMode
import com.aikipunk.betteraudiobookplayer.domain.library.ShelfBook
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.repository.PlaybackStateRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.AudiobookRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.TrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class LibraryUiState(
    val audiobooks: List<Audiobook> = emptyList(),
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val error: String? = null,
    val lastRefreshed: LocalDateTime? = null,
    val folderImportPrompt: FolderImportPrompt? = null,
    /** Every book with its listening progress, unfiltered. */
    val shelf: List<ShelfBook> = emptyList(),
    /** [shelf] after search, filter and sort. */
    val visibleBooks: List<ShelfBook> = emptyList(),
    val continueListening: ShelfBook? = null,
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val sort: LibrarySort = LibrarySort.RECENT,
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
    /** Books from the previous phone still waiting for their audio to be imported. */
    val pendingRestore: List<SavedBook> = emptyList(),
    val restoreSearch: RestoreSearch? = null,
    /** One-shot snackbar text. */
    val message: String? = null,
)

/** Result of looking for a saved book's audio on this phone. */
data class RestoreSearch(
    val book: SavedBook,
    val isSearching: Boolean = true,
    val candidates: List<ShelfMatcher.FolderCandidate> = emptyList(),
)

/** Shown when a picked folder has several audio files and we need to ask how to import them. */
data class FolderImportPrompt(
    val folderName: String,
    val audioFileCount: Int,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val audiobookRepository: AudiobookRepository,
    private val trackRepository: TrackRepository,
    private val folderImporter: FolderAudiobookImporter,
    private val libraryManager: AudiobookLibraryManager,
    private val playbackStateRepository: PlaybackStateRepository,
    private val userPreferences: UserPreferences,
    private val shelfBackup: ShelfBackup,
    private val audioFileFinder: AudioFileFinder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var pendingScan: FolderScan? = null

    /** The saved book the folder picker was opened for, so its folder imports without asking. */
    private var restoreTarget: SavedBook? = null

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(LibraryFilter.ALL)

    init {
        loadCachedAudiobooks()
        viewModelScope.launch {
            shelfBackup.pending.collect { pending -> _uiState.update { it.copy(pendingRestore = pending) } }
        }
    }

    /** Looks for [book]'s audio folder in the phone's media index. */
    fun findOnPhone(book: SavedBook) {
        _uiState.update { it.copy(restoreSearch = RestoreSearch(book)) }
        viewModelScope.launch {
            val candidates = try {
                ShelfMatcher.candidateFolders(book, audioFileFinder.allAudioFiles())
            } catch (e: SecurityException) {
                emptyList()
            }
            _uiState.update { state ->
                if (state.restoreSearch?.book != book) state
                else state.copy(restoreSearch = RestoreSearch(book, isSearching = false, candidates = candidates))
            }
        }
    }

    /** The folder picker is about to open for [book] (null: any folder). */
    fun setRestoreTarget(book: SavedBook?) {
        restoreTarget = book
        _uiState.update { it.copy(restoreSearch = null) }
    }

    fun dismissRestoreSearch() = _uiState.update { it.copy(restoreSearch = null) }

    fun discardRestore() = shelfBackup.discardPending()

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    fun loadCachedAudiobooks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val shelf = combine(
                audiobookRepository.getAllAudiobooksFlow(),
                trackRepository.getAllTracksFlow(),
                playbackStateRepository.getAllPlaybackStatesFlow(),
            ) { audiobooks, tracks, states ->
                val tracksByBook = tracks.groupBy { it.audiobookId }
                val stateByBook = states.associateBy { it.audiobookId }
                audiobooks to audiobooks.map { book ->
                    ShelfBook(book, BookProgress.of(book, tracksByBook[book.id].orEmpty(), stateByBook[book.id]))
                }
            }
            val controls = combine(
                query,
                filter,
                userPreferences.librarySort,
                userPreferences.libraryViewMode,
            ) { q, f, sort, mode -> ShelfControls(q, f, sort, mode) }
            try {
                combine(shelf, controls) { (audiobooks, books), c ->
                    Triple(audiobooks, books, c)
                }.collect { (audiobooks, books, c) ->
                    // Only the shelf fields; import/restore fields are owned by other updates
                    _uiState.update { current ->
                        current.copy(
                            audiobooks = audiobooks,
                            shelf = books,
                            visibleBooks = LibraryShelf.arrange(books, c.query, c.filter, c.sort),
                            continueListening = LibraryShelf.continueListening(books),
                            query = c.query,
                            filter = c.filter,
                            sort = c.sort,
                            viewMode = c.viewMode,
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load audiobooks",
                )
            }
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(value: LibraryFilter) {
        filter.value = value
    }

    fun setSort(value: LibrarySort) = userPreferences.librarySort.set(value)

    fun setViewMode(value: LibraryViewMode) = userPreferences.libraryViewMode.set(value)

    fun markFinished(audiobook: Audiobook) {
        viewModelScope.launch { libraryManager.markFinished(audiobook) }
    }

    fun resetProgress(audiobook: Audiobook) {
        viewModelScope.launch { libraryManager.resetProgress(audiobook) }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Scans the folder picked via ACTION_OPEN_DOCUMENT_TREE. A single audio file is imported
     * immediately; multiple files prompt the user to choose chapters vs. separate books.
     */
    fun importFolder(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, error = null)
            try {
                val scan = folderImporter.scan(treeUri)
                val target = restoreTarget.also { restoreTarget = null }
                // A restored book's own folder: its files are its chapters
                val isRestoredBookFolder = target != null && scan.audioFileCount == target.tracks.size
                if (scan.audioFileCount > 1 && !isRestoredBookFolder) {
                    pendingScan = scan
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        folderImportPrompt = FolderImportPrompt(scan.folderName, scan.audioFileCount),
                    )
                } else {
                    saveImport(scan, FolderImportMode.CHAPTERS)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = "Failed to import folder: ${e.message}",
                )
            }
        }
    }

    fun confirmFolderImport(mode: FolderImportMode) {
        val scan = pendingScan ?: return
        pendingScan = null
        _uiState.value = _uiState.value.copy(folderImportPrompt = null, isScanning = true)
        viewModelScope.launch {
            try {
                saveImport(scan, mode)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = "Failed to import folder: ${e.message}",
                )
            }
        }
    }

    fun cancelFolderImport() {
        pendingScan = null
        _uiState.value = _uiState.value.copy(folderImportPrompt = null)
    }

    private suspend fun saveImport(scan: FolderScan, mode: FolderImportMode) {
        var restored = 0
        folderImporter.build(scan, mode).forEach { (book, tracks) ->
            // Re-importing refreshes an existing book instead of failing on a duplicate key
            val existing = audiobookRepository.getAudiobookById(book.id)
            if (existing != null) {
                trackRepository.deleteTracksForAudiobook(book.id)
                // Keep what the user added; only file-derived fields are refreshed
                audiobookRepository.updateAudiobook(
                    book.copy(
                        notes = existing.notes,
                        customTitle = existing.customTitle,
                        createdAt = existing.createdAt,
                        // Details the user edited win over the file's tags
                        author = existing.author ?: book.author,
                        narrator = existing.narrator ?: book.narrator,
                        publisher = existing.publisher ?: book.publisher,
                        publishedYear = existing.publishedYear ?: book.publishedYear,
                    ),
                )
                trackRepository.addTracks(tracks)
            } else {
                audiobookRepository.addAudiobook(book)
                trackRepository.addTracks(tracks)
                if (shelfBackup.restoreInto(book, tracks) != null) restored++
            }
        }
        _uiState.update {
            it.copy(
                isScanning = false,
                lastRefreshed = LocalDateTime.now(),
                message = when (restored) {
                    0 -> it.message
                    1 -> "Restored progress and bookmarks for 1 book"
                    else -> "Restored progress and bookmarks for $restored books"
                },
            )
        }
    }

    fun saveDetails(audiobook: Audiobook, details: BookDetails) {
        viewModelScope.launch { libraryManager.updateDetails(audiobook, details) }
    }

    fun removeAudiobook(audiobook: Audiobook) {
        viewModelScope.launch { libraryManager.remove(audiobook) }
    }
}

private data class ShelfControls(
    val query: String,
    val filter: LibraryFilter,
    val sort: LibrarySort,
    val viewMode: LibraryViewMode,
)
