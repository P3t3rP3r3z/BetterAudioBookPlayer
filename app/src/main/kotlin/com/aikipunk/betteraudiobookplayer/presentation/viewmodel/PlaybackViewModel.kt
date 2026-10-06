package com.aikipunk.betteraudiobookplayer.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.aikipunk.betteraudiobookplayer.domain.bookmark.BookmarkExport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewModelScope
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.aikipunk.betteraudiobookplayer.domain.audio.AudioPlayer
import com.aikipunk.betteraudiobookplayer.domain.audio.NowPlayingInfo
import com.aikipunk.betteraudiobookplayer.domain.audio.PlaybackState as AudioPlaybackState
import com.aikipunk.betteraudiobookplayer.domain.library.BookDetails
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.model.AudioOutput
import com.aikipunk.betteraudiobookplayer.data.audio.AudioOutputManager
import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import com.aikipunk.betteraudiobookplayer.domain.repository.AudiobookRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.BookmarkRepository
import com.aikipunk.betteraudiobookplayer.domain.playback.BookLauncher
import com.aikipunk.betteraudiobookplayer.domain.playback.PlaybackProgressTracker
import com.aikipunk.betteraudiobookplayer.domain.playback.ResumeRewind
import com.aikipunk.betteraudiobookplayer.domain.usecase.CreateBookmarkUseCase
import com.aikipunk.betteraudiobookplayer.service.PlaybackServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.aikipunk.betteraudiobookplayer.data.library.AudiobookLibraryManager
import com.aikipunk.betteraudiobookplayer.data.preferences.UserPreferences
import com.aikipunk.betteraudiobookplayer.domain.playback.SleepMarker
import com.aikipunk.betteraudiobookplayer.domain.playback.SleepTimer
import com.aikipunk.betteraudiobookplayer.domain.playback.SleepTimerState
import javax.inject.Inject

data class PlaybackUiState(
    val audiobookId: String? = null,
    val audiobook: Audiobook? = null,
    val currentTrack: Track? = null,
    val playbackState: PlaybackState? = null,
    val tracks: List<Track> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val audioPlaybackState: AudioPlaybackState = AudioPlaybackState.IDLE,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1.0f,
    /** One-shot message for a snackbar (e.g. "Bookmark added"). */
    val message: String? = null,
    /** One-shot: a bookmark was just added from the player (offers "Add note"). */
    val addedBookmark: Bookmark? = null,
    /** The loaded book was removed from the library. */
    val bookRemoved: Boolean = false,
)

@OptIn(UnstableApi::class)
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val audioPlayer: AudioPlayer,
    private val playbackServiceManager: PlaybackServiceManager,
    private val audiobookRepository: AudiobookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val createBookmarkUseCase: CreateBookmarkUseCase,
    private val progressTracker: PlaybackProgressTracker,
    private val sleepTimer: SleepTimer,
    private val userPreferences: UserPreferences,
    private val libraryManager: AudiobookLibraryManager,
    private val audioOutputManager: AudioOutputManager,
    private val bookLauncher: BookLauncher,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val audioOutputs: StateFlow<List<AudioOutput>> = audioOutputManager.outputs
    val currentAudioOutput: StateFlow<AudioOutput?> = audioOutputManager.current
    fun refreshAudioOutputs() = audioOutputManager.refresh()
    fun selectAudioOutput(output: AudioOutput) = audioOutputManager.select(output)
    fun showSystemOutputSwitcher(): Boolean = audioOutputManager.showSystemOutputSwitcher()

    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimer.state
    val shakeToExtendEnabled: StateFlow<Boolean> = userPreferences.shakeToExtendSleepTimer
    val skipBackSeconds: StateFlow<Int> = userPreferences.skipBackSeconds
    val skipForwardSeconds: StateFlow<Int> = userPreferences.skipForwardSeconds
    val sleepTimerDefaultMinutes: StateFlow<Int> = userPreferences.sleepTimerDefaultMinutes

    fun startSleepTimer(minutes: Int) = sleepTimer.start(minutes * 60_000L)
    fun startSleepTimerEndOfChapter() = sleepTimer.startEndOfChapter()
    fun extendSleepTimer() = sleepTimer.extend()
    fun cancelSleepTimer() = sleepTimer.cancel()

    /** Where the sleep timer last started, if the listener hasn't resumed since. */
    val sleepMarker: StateFlow<SleepMarker?> = userPreferences.sleepMarker

    fun jumpToSleepMarker() {
        val marker = sleepMarker.value ?: return
        val tracks = _uiState.value.tracks
        if (marker.audiobookId != _uiState.value.audiobookId || marker.itemIndex !in tracks.indices) return
        viewModelScope.launch {
            _uiState.update { it.copy(currentTrack = tracks[marker.itemIndex]) }
            audioPlayer.seekToItem(marker.itemIndex, marker.positionMs)
        }
    }

    fun saveBookDetails(details: BookDetails) {
        val book = _uiState.value.audiobook ?: return
        viewModelScope.launch { libraryManager.updateDetails(book, details) }
    }

    fun removeBook() {
        val book = _uiState.value.audiobook ?: return
        viewModelScope.launch { libraryManager.remove(book) }
    }

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var bookmarksJob: Job? = null

    init {
        viewModelScope.launch {
            audioPlayer.playbackState.collect { state ->
                _uiState.update {
                    it.copy(audioPlaybackState = state, isPlaying = state == AudioPlaybackState.PLAYING)
                }
            }
        }
        viewModelScope.launch {
            audioPlayer.currentPosition.collect { position ->
                _uiState.update { it.copy(currentPositionMs = position) }
            }
        }
        viewModelScope.launch {
            audioPlayer.duration.collect { duration ->
                _uiState.update { it.copy(durationMs = duration) }
            }
        }
        viewModelScope.launch {
            // Chapter changes (auto-advance, prev/next, system controls) update the current track
            audioPlayer.currentItemIndex.collect { index ->
                if (audioPlayer.currentPlaylistKey() != _uiState.value.audiobookId) return@collect
                _uiState.value.tracks.getOrNull(index)?.let { track ->
                    _uiState.update { it.copy(currentTrack = track) }
                }
            }
        }
        viewModelScope.launch {
            // A book started from Android Auto or other system controls replaces the one shown
            bookLauncher.openedBookId.collect { openedId ->
                val shownId = _uiState.value.audiobookId
                if (openedId != null && shownId != null && openedId != shownId) loadAudiobook(openedId)
            }
        }
        viewModelScope.launch {
            audioPlayer.playbackSpeed.collect { speed ->
                _uiState.update { it.copy(speed = speed) }
            }
        }
        viewModelScope.launch {
            audioPlayer.error.collect { error ->
                if (error != null) _uiState.update { it.copy(error = error) }
            }
        }
        viewModelScope.launch {
            audiobookRepository.getAllAudiobooksFlow().collect { books ->
                val id = _uiState.value.audiobookId ?: return@collect
                if (_uiState.value.isLoading) return@collect
                val book = books.firstOrNull { it.id == id }
                if (book == null) {
                    _uiState.update { PlaybackUiState(bookRemoved = true) }
                } else {
                    // Pick up renames and note edits
                    val previous = _uiState.value.audiobook
                    _uiState.update { it.copy(audiobook = book) }
                    val nowPlayingChanged = previous != null &&
                        (previous.displayTitle != book.displayTitle || previous.author != book.author ||
                            previous.coverArtPath != book.coverArtPath)
                    if (nowPlayingChanged && audioPlayer.currentPlaylistKey() == id) {
                        audioPlayer.updateNowPlaying(_uiState.value.tracks.map { nowPlaying(it) })
                    }
                }
            }
        }
    }

    fun loadAudiobook(audiobookId: String, initialTrackId: String? = null, autoPlay: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, audiobookId = audiobookId) }
            try {
                val opened = bookLauncher.open(audiobookId, initialTrackId)
                val selectedTrack = opened.startTrack
                val playingTrack = if (opened.wasAlreadyLoaded) {
                    opened.tracks.getOrNull(audioPlayer.currentItemIndex.value)
                } else {
                    null
                }
                _uiState.update {
                    it.copy(
                        audiobook = opened.audiobook,
                        tracks = opened.tracks,
                        playbackState = opened.savedState,
                        currentTrack = playingTrack ?: selectedTrack,
                        bookRemoved = opened.audiobook == null,
                    )
                }

                observeBookmarks(audiobookId)
                _uiState.update { it.copy(isLoading = false) }
                // A freshly loaded book always starts: the previous book's pause is asynchronous,
                // so isPlaying() may still report the old book
                val alreadyPlaying = opened.wasAlreadyLoaded && audioPlayer.isPlaying()
                if (autoPlay && selectedTrack != null && !alreadyPlaying) playCurrentTrack()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    private fun observeBookmarks(audiobookId: String) {
        bookmarksJob?.cancel()
        bookmarksJob = viewModelScope.launch {
            bookmarkRepository.getBookmarksForAudiobookFlow(audiobookId).collect { bookmarks ->
                _uiState.update { it.copy(bookmarks = bookmarks) }
            }
        }
    }

    fun playCurrentTrack() {
        viewModelScope.launch {
            val audiobookId = _uiState.value.audiobookId ?: return@launch
            val track = _uiState.value.currentTrack ?: return@launch

            // Start the foreground service for background playback
            playbackServiceManager.startPlaybackService()

            if (audioPlayer.currentPlaylistKey() != audiobookId) {
                val tracks = _uiState.value.tracks
                progressTracker.setSession(audiobookId, tracks.map { it.id }, null)
                loadBook(audiobookId, tracks, tracks.indexOf(track).coerceAtLeast(0), 0L)
            } else {
                val position = audioPlayer.currentPosition.value
                if (userPreferences.rewindOnResume.value) {
                    val resumeAt = ResumeRewind.resumePosition(position, progressTracker.pausedForMs())
                    if (resumeAt != position) audioPlayer.seekTo(resumeAt)
                }
            }
            audioPlayer.play()
        }
    }

    fun pauseAudio() {
        viewModelScope.launch { audioPlayer.pause() }
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) pauseAudio() else playCurrentTrack()
    }

    fun updatePosition(positionMs: Long) {
        viewModelScope.launch { audioPlayer.seekTo(positionMs.coerceAtLeast(0L)) }
    }

    fun skipForward(skipMs: Long = 30_000) {
        val duration = _uiState.value.durationMs
        val target = _uiState.value.currentPositionMs + skipMs
        updatePosition(if (duration > 0) target.coerceAtMost(duration) else target)
    }

    fun skipBackward(skipMs: Long = 30_000) {
        updatePosition(_uiState.value.currentPositionMs - skipMs)
    }

    /** Speed is saved per book by the progress tracker. */
    fun updateSpeed(speed: Float) {
        viewModelScope.launch { audioPlayer.setSpeed(speed) }
    }

    fun nextChapter() {
        val index = currentChapterIndex() ?: return
        if (index < _uiState.value.tracks.lastIndex) selectChapter(index + 1)
    }

    /** Restarts the chapter, or goes to the previous one if within its first few seconds. */
    fun previousChapter() {
        val index = currentChapterIndex() ?: return
        if (_uiState.value.currentPositionMs > RESTART_CHAPTER_THRESHOLD_MS || index == 0) {
            updatePosition(0L)
        } else {
            selectChapter(index - 1)
        }
    }

    fun selectChapter(index: Int) {
        viewModelScope.launch {
            val track = _uiState.value.tracks.getOrNull(index) ?: return@launch
            _uiState.update { it.copy(currentTrack = track) }
            audioPlayer.seekToItem(index, 0L)
        }
    }

    private fun currentChapterIndex(): Int? {
        val state = _uiState.value
        return state.tracks.indexOfFirst { it.id == state.currentTrack?.id }.takeIf { it >= 0 }
    }

    private suspend fun loadBook(audiobookId: String, tracks: List<Track>, startIndex: Int, positionMs: Long) {
        bookLauncher.load(audiobookId, _uiState.value.audiobook, tracks, startIndex, positionMs)
    }

    fun createBookmark(title: String, description: String? = null) {
        viewModelScope.launch {
            val audiobookId = _uiState.value.audiobookId ?: return@launch
            val trackId = _uiState.value.currentTrack?.id ?: return@launch
            val bookmark = createBookmarkUseCase(audiobookId, trackId, _uiState.value.currentPositionMs, title, description)
            _uiState.update { it.copy(addedBookmark = bookmark) }
        }
    }

    fun updateBookmark(bookmark: Bookmark) {
        viewModelScope.launch { bookmarkRepository.updateBookmark(bookmark) }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(bookmark.id)
            _uiState.update { it.copy(message = "Bookmark deleted") }
        }
    }

    fun consumeAddedBookmark() {
        _uiState.update { it.copy(addedBookmark = null) }
    }

    fun deleteBookmarks(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { bookmarkRepository.deleteBookmark(it) }
            val label = if (ids.size == 1) "Bookmark deleted" else "${ids.size} bookmarks deleted"
            _uiState.update { it.copy(message = label) }
        }
    }

    /** Writes every bookmark of the current book as Markdown to [uri] (from the system "Save as" picker). */
    fun exportBookmarks(uri: Uri) {
        val state = _uiState.value
        val book = state.audiobook ?: return
        val markdown = BookmarkExport.markdown(book.displayTitle, book.author, state.bookmarks, state.tracks)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val stream = appContext.contentResolver.openOutputStream(uri, "wt")
                        ?: error("Couldn't open the file")
                    stream.use { it.write(markdown.toByteArray()) }
                }
            }
            val message = if (result.isSuccess) {
                "Exported ${state.bookmarks.size} bookmark${if (state.bookmarks.size == 1) "" else "s"}"
            } else {
                "Export failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
            }
            _uiState.update { it.copy(message = message) }
        }
    }

    fun jumpToBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            val index = _uiState.value.tracks.indexOfFirst { it.id == bookmark.trackId }
            if (index < 0) return@launch
            _uiState.update { it.copy(currentTrack = it.tracks[index]) }
            audioPlayer.seekToItem(index, bookmark.positionMs)
        }
    }

    private fun nowPlaying(track: Track): NowPlayingInfo =
        BookLauncher.nowPlaying(_uiState.value.audiobook, _uiState.value.tracks, track)

    private companion object {
        const val RESTART_CHAPTER_THRESHOLD_MS = 3_000L
    }

    fun savePlaybackState() {
        progressTracker.saveNow()
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        // The AudioPlayer is an app-wide singleton shared with the playback service,
        // so it must not be released when a screen's ViewModel goes away.
        progressTracker.saveNow()
    }
}
