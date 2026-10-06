package com.aikipunk.betteraudiobookplayer.data.repository

import com.aikipunk.betteraudiobookplayer.data.dao.AudiobookDao
import com.aikipunk.betteraudiobookplayer.data.mapper.toDomain
import com.aikipunk.betteraudiobookplayer.data.mapper.toEntity
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook
import com.aikipunk.betteraudiobookplayer.domain.repository.AudiobookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AudiobookRepositoryImpl @Inject constructor(
    private val audiobookDao: AudiobookDao,
) : AudiobookRepository {
    override fun getAllAudiobooksFlow(): Flow<List<Audiobook>> =
        audiobookDao.getAllFlow().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAudiobookById(id: String): Audiobook? =
        audiobookDao.getById(id)?.toDomain()

    override suspend fun addAudiobook(audiobook: Audiobook) {
        audiobookDao.insert(audiobook.toEntity())
    }

    override suspend fun updateAudiobook(audiobook: Audiobook) {
        audiobookDao.update(audiobook.toEntity())
    }

    override suspend fun deleteAudiobook(id: String) {
        audiobookDao.getById(id)?.let { audiobookDao.delete(it) }
    }
}
