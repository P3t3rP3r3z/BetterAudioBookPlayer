package com.aikipunk.betteraudiobookplayer.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aikipunk.betteraudiobookplayer.data.transfer.ShelfBackup
import com.aikipunk.betteraudiobookplayer.domain.repository.AudiobookRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.PlaybackStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StartupState {
    data object Loading : StartupState
    /**
     * [audiobookId] is the most recently played book, or null if there is none.
     * [restorableBooks] is how many books a previous phone's bookshelf holds (0: nothing to offer).
     */
    data class Ready(val audiobookId: String?, val restorableBooks: Int = 0) : StartupState
}

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val playbackStateRepository: PlaybackStateRepository,
    private val audiobookRepository: AudiobookRepository,
    private val shelfBackup: ShelfBackup,
) : ViewModel() {

    private val _state = MutableStateFlow<StartupState>(StartupState.Loading)
    val state: StateFlow<StartupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val lastId = runCatching {
                playbackStateRepository.getMostRecentPlaybackState()
                    ?.audiobookId
                    ?.takeIf { audiobookRepository.getAudiobookById(it) != null }
            }.getOrNull()
            shelfBackup.awaitReady()
            _state.value = StartupState.Ready(lastId, shelfBackup.pending.value.size)
        }
    }

    /** "Start fresh": the previous phone's bookshelf won't be offered again. */
    fun discardRestore() = shelfBackup.discardPending()
}
