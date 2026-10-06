package com.aikipunk.betteraudiobookplayer.di

import com.aikipunk.betteraudiobookplayer.data.repository.AudiobookRepositoryImpl
import com.aikipunk.betteraudiobookplayer.data.repository.BookmarkRepositoryImpl
import com.aikipunk.betteraudiobookplayer.data.repository.PlaybackStateRepositoryImpl
import com.aikipunk.betteraudiobookplayer.data.repository.TrackRepositoryImpl
import com.aikipunk.betteraudiobookplayer.domain.repository.AudiobookRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.BookmarkRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.PlaybackStateRepository
import com.aikipunk.betteraudiobookplayer.domain.repository.TrackRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Singleton
    @Binds
    abstract fun bindAudiobookRepository(impl: AudiobookRepositoryImpl): AudiobookRepository

    @Singleton
    @Binds
    abstract fun bindTrackRepository(impl: TrackRepositoryImpl): TrackRepository

    @Singleton
    @Binds
    abstract fun bindPlaybackStateRepository(impl: PlaybackStateRepositoryImpl): PlaybackStateRepository

    @Singleton
    @Binds
    abstract fun bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository
}
