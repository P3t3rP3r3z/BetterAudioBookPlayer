package com.aikipunk.betteraudiobookplayer.data.repository

import com.aikipunk.betteraudiobookplayer.data.dao.BookmarkDao
import com.aikipunk.betteraudiobookplayer.data.mapper.toDomain
import com.aikipunk.betteraudiobookplayer.data.mapper.toEntity
import com.aikipunk.betteraudiobookplayer.domain.model.Bookmark
import com.aikipunk.betteraudiobookplayer.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao,
) : BookmarkRepository {
    override fun getBookmarksForAudiobookFlow(audiobookId: String): Flow<List<Bookmark>> =
        bookmarkDao.getBookmarksForAudiobookFlow(audiobookId).map { entities -> entities.map { it.toDomain() } }

    override fun getAllBookmarksFlow(): Flow<List<Bookmark>> =
        bookmarkDao.getAllBookmarksFlow().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getBookmarksForAudiobook(audiobookId: String): List<Bookmark> =
        bookmarkDao.getBookmarksForAudiobook(audiobookId).map { it.toDomain() }

    override suspend fun getBookmarkById(id: String): Bookmark? =
        bookmarkDao.getById(id)?.toDomain()

    override suspend fun addBookmark(bookmark: Bookmark) {
        bookmarkDao.insert(bookmark.toEntity())
    }

    override suspend fun updateBookmark(bookmark: Bookmark) {
        bookmarkDao.update(bookmark.toEntity())
    }

    override suspend fun deleteBookmark(id: String) {
        bookmarkDao.getById(id)?.let { bookmarkDao.delete(it) }
    }

    override suspend fun deleteBookmarksForAudiobook(audiobookId: String) {
        bookmarkDao.deleteBookmarksForAudiobook(audiobookId)
    }
}
