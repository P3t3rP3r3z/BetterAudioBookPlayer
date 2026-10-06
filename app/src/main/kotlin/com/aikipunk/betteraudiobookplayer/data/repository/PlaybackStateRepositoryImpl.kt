package com.aikipunk.betteraudiobookplayer.data.repository

import com.aikipunk.betteraudiobookplayer.data.dao.PlaybackStateDao
import com.aikipunk.betteraudiobookplayer.data.mapper.toDomain
import com.aikipunk.betteraudiobookplayer.data.mapper.toEntity
import com.aikipunk.betteraudiobookplayer.domain.model.PlaybackState
import com.aikipunk.betteraudiobookplayer.domain.repository.PlaybackStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PlaybackStateRepositoryImpl @Inject constructor(
    private val playbackStateDao: PlaybackStateDao,
) : PlaybackStateRepository {
    override fun getPlaybackStateFlow(audiobookId: String): Flow<PlaybackState?> =
        playbackStateDao.getByAudiobookIdFlow(audiobookId).map { it?.toDomain() }

    override suspend fun getPlaybackState(audiobookId: String): PlaybackState? =
        playbackStateDao.getByAudiobookId(audiobookId)?.toDomain()

    override fun getAllPlaybackStatesFlow(): Flow<List<PlaybackState>> =
        playbackStateDao.getAllFlow().map { entities -> entities.map { it.toDomain() } }

    override suspend fun savePlaybackState(playbackState: PlaybackState) {
        playbackStateDao.upsert(playbackState.toEntity())
    }

    override suspend fun getMostRecentPlaybackState(): PlaybackState? =
        playbackStateDao.getMostRecent()?.toDomain()

    override suspend fun updatePosition(audiobookId: String, positionMs: Long) {
        playbackStateDao.updatePosition(audiobookId, positionMs)
    }

    override suspend fun updatePlaybackState(audiobookId: String, positionMs: Long, speed: Float, isPlaying: Boolean) {
        playbackStateDao.updatePlaybackState(audiobookId, positionMs, speed, isPlaying)
    }

    override suspend fun deletePlaybackState(audiobookId: String) {
        playbackStateDao.deleteByAudiobookId(audiobookId)
    }
}
