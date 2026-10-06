package com.aikipunk.betteraudiobookplayer.domain.usecase

import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.repository.BookmarkRepository
import java.util.UUID
import javax.inject.Inject

class CreateBookmarkUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
) {
    suspend operator fun invoke(
        audiobookId: String,
        trackId: String,
        positionMs: Long,
        title: String,
        description: String? = null,
    ): Bookmark {
        val bookmark = Bookmark(
            id = UUID.randomUUID().toString(),
            audiobookId = audiobookId,
            trackId = trackId,
            positionMs = positionMs,
            title = title,
            description = description,
        )
        bookmarkRepository.addBookmark(bookmark)
        return bookmark
    }
}
