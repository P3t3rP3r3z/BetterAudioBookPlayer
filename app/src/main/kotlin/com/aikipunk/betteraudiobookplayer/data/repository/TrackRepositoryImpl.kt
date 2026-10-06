package com.aikipunk.betteraudiobookplayer.data.repository

import com.aikipunk.betteraudiobookplayer.data.dao.TrackDao
import com.aikipunk.betteraudiobookplayer.data.mapper.toDomain
import com.aikipunk.betteraudiobookplayer.data.mapper.toEntity
import com.aikipunk.betteraudiobookplayer.domain.model.Track
import com.aikipunk.betteraudiobookplayer.domain.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TrackRepositoryImpl @Inject constructor(
    private val trackDao: TrackDao,
) : TrackRepository {
    override fun getTracksForAudiobookFlow(audiobookId: String): Flow<List<Track>> =
        trackDao.getTracksForAudiobookFlow(audiobookId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTracksForAudiobook(audiobookId: String): List<Track> =
        trackDao.getTracksForAudiobook(audiobookId).map { it.toDomain() }

    override fun getAllTracksFlow(): Flow<List<Track>> =
        trackDao.getAllFlow().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTrackById(id: String): Track? =
        trackDao.getById(id)?.toDomain()

    override suspend fun addTracks(tracks: List<Track>) {
        trackDao.insertAll(tracks.map { it.toEntity() })
    }

    override suspend fun addTrack(track: Track) {
        trackDao.insert(track.toEntity())
    }

    override suspend fun deleteTracksForAudiobook(audiobookId: String) {
        trackDao.deleteTracksForAudiobook(audiobookId)
    }
}
