package com.aikipunk.betteraudiobookplayer.service

import androidx.core.net.toUri
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import com.aikipunk.betteraudiobookplayer.data.audio.CoverArtwork
import com.aikipunk.betteraudiobookplayer.domain.library.BookProgress
import com.aikipunk.betteraudiobookplayer.domain.library.BookStatus
import com.aikipunk.betteraudiobookplayer.domain.library.LibraryFilter
import com.aikipunk.betteraudiobookplayer.domain.library.LibraryShelf
import com.aikipunk.betteraudiobookplayer.domain.library.LibrarySort
import com.aikipunk.betteraudiobookplayer.domain.library.ShelfBook
import com.aikipunk.betteraudiobookplayer.domain.library.VoiceSearch
import com.aikipunk.betteraudiobookplayer.domain.repository.AudiobookRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.PlaybackStateRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.TrackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The library as seen by media browsers such as Android Auto:
 * root → "Continue listening" / "Library" → books (each book plays as a whole).
 */
@Singleton
@UnstableApi
class MediaBrowseTree @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audiobookRepository: AudiobookRepository,
    private val trackRepository: TrackRepository,
    private val playbackStateRepository: PlaybackStateRepository,
) {
    private val artworkCache = ConcurrentHashMap<String, ByteArray>()

    fun root(): MediaItem = folder(ROOT_ID, "Better Audiobook Player")

    /** Shows books as a grid of covers. */
    fun rootParams(): LibraryParams = LibraryParams.Builder()
        .setExtras(
            Bundle().apply {
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
            },
        )
        .build()

    /** Null when [parentId] isn't a folder. */
    suspend fun children(parentId: String): List<MediaItem>? = when (parentId) {
        ROOT_ID -> listOf(folder(CONTINUE_ID, "Continue listening"), folder(LIBRARY_ID, "Library"))
        CONTINUE_ID -> shelf()
            .filter { it.status == BookStatus.IN_PROGRESS }
            .let { LibraryShelf.arrange(it, "", LibraryFilter.ALL, LibrarySort.RECENT) }
            .map { bookItem(it) }
        LIBRARY_ID -> LibraryShelf.arrange(shelf(), "", LibraryFilter.ALL, LibrarySort.TITLE).map { bookItem(it) }
        else -> null
    }

    suspend fun item(mediaId: String): MediaItem? {
        if (mediaId == ROOT_ID) return root()
        children(ROOT_ID)?.firstOrNull { it.mediaId == mediaId }?.let { return it }
        val bookId = bookIdOf(mediaId) ?: return null
        return shelf().firstOrNull { it.audiobook.id == bookId }?.let { bookItem(it) }
    }

    suspend fun search(query: String): List<MediaItem> =
        LibraryShelf.arrange(shelf(), query, LibraryFilter.ALL, LibrarySort.RECENT).map { bookItem(it) }

    /** The book a voice request like "play <title>" means; an empty query resumes the latest book. */
    suspend fun bestMatch(query: String): MediaItem? = VoiceSearch.bestMatch(shelf(), query)?.let { bookItem(it) }

    suspend fun bestMatchBookId(query: String): String? = VoiceSearch.bestMatch(shelf(), query)?.audiobook?.id

    private suspend fun shelf(): List<ShelfBook> {
        val books = audiobookRepository.getAllAudiobooksFlow().first()
        val tracksByBook = trackRepository.getAllTracksFlow().first().groupBy { it.audiobookId }
        val stateByBook = playbackStateRepository.getAllPlaybackStatesFlow().first().associateBy { it.audiobookId }
        return books.map { book ->
            ShelfBook(book, BookProgress.of(book, tracksByBook[book.id].orEmpty(), stateByBook[book.id]))
        }
    }

    private suspend fun bookItem(book: ShelfBook): MediaItem {
        val audiobook = book.audiobook
        val completion = when (book.status) {
            BookStatus.NOT_STARTED -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
            BookStatus.IN_PROGRESS -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
            BookStatus.FINISHED -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(audiobook.displayTitle)
            .setArtist(audiobook.author)
            .setSubtitle(audiobook.author)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
            .setExtras(
                Bundle().apply {
                    putInt(MediaConstants.EXTRAS_KEY_COMPLETION_STATUS, completion)
                    if (book.status == BookStatus.IN_PROGRESS) {
                        putDouble(MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE, book.progress.fraction.toDouble())
                    }
                },
            )
        artwork(audiobook.coverArtPath)?.let { metadata.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
        return MediaItem.Builder()
            .setMediaId(bookMediaId(audiobook.id))
            .setMediaMetadata(metadata.build())
            .build()
    }

    // Car displays can't open the app's file/content URIs, so covers are sent as image bytes
    private suspend fun artwork(coverPath: String?): ByteArray? {
        if (coverPath.isNullOrBlank()) return null
        artworkCache[coverPath]?.let { return it }
        val uri = if (coverPath.contains("://")) coverPath.toUri() else Uri.fromFile(File(coverPath))
        return withContext(Dispatchers.IO) { CoverArtwork.encode(context, uri) }
            ?.also { artworkCache[coverPath] = it }
    }

    private fun folder(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS)
                .build(),
        )
        .build()

    companion object {
        private const val ROOT_ID = "root"
        private const val CONTINUE_ID = "continue"
        private const val LIBRARY_ID = "library"
        private const val BOOK_PREFIX = "book:"

        fun bookMediaId(audiobookId: String): String = BOOK_PREFIX + audiobookId

        fun bookIdOf(mediaId: String): String? =
            mediaId.takeIf { it.startsWith(BOOK_PREFIX) }?.removePrefix(BOOK_PREFIX)?.takeIf { it.isNotEmpty() }
    }
}
