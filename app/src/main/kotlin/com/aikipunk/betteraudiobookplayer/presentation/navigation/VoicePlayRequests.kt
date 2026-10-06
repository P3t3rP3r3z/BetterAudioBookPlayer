package com.aikipunk.betteraudiobookplayer.presentation.navigation

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A book a voice request asked to play, waiting for navigation to open it in the player. */
@Singleton
class VoicePlayRequests @Inject constructor() {
    private val _pendingBookId = MutableStateFlow<String?>(null)
    val pendingBookId: StateFlow<String?> = _pendingBookId.asStateFlow()

    fun request(bookId: String) {
        _pendingBookId.value = bookId
    }

    /** Clears the request once opened, unless a newer one has arrived meanwhile. */
    fun handled(bookId: String) {
        _pendingBookId.compareAndSet(bookId, null)
    }
}
