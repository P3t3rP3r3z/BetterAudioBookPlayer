package com.aikipunk.betteraudiobookplayer.domain.usecase

import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.audio.AudioPlayer
import com.aikipunk.betteraudiobookplayer.domain.playback.PlaybackProgressTracker
import com.aikipunk.betteraudiobookplayer.domain.repository.AudiobookRepository
import javax.inject.Inject

/** Bookmarks wherever playback is right now (used by the media notification button). */
class BookmarkCurrentPositionUseCase @Inject constructor(
    private val tracker: PlaybackProgressTracker,
    private val audioPlayer: AudioPlayer,
    private val audiobookRepository: AudiobookRepository,
    private val createBookmark: CreateBookmarkUseCase,
) {
    /** Returns the new bookmark, or null if nothing is loaded. */
    suspend operator fun invoke(): Bookmark? {
        val audiobookId = tracker.currentAudiobookId() ?: return null
        val trackIds = tracker.currentTrackIds()
        val index = audioPlayer.currentItemIndex.value
        val trackId = trackIds.getOrNull(index) ?: return null
        val title = if (trackIds.size > 1) {
            "Chapter ${index + 1}"
        } else {
            audiobookRepository.getAudiobookById(audiobookId)?.displayTitle ?: "Bookmark"
        }
        return createBookmark(audiobookId, trackId, audioPlayer.currentPosition.value, title)
    }
}
