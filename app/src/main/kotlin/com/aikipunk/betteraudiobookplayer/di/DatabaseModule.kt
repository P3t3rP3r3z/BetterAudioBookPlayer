package com.aikipunk.betteraudiobookplayer.di

import android.content.Context
import androidx.room.Room
import com.aikipunk.betteraudiobookplayer.data.db.AudiobookPlayerDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideAudiobookPlayerDatabase(
        @ApplicationContext context: Context,
    ): AudiobookPlayerDatabase = Room.databaseBuilder(
        context,
        AudiobookPlayerDatabase::class.java,
        "audiobook_player.db",
    ).addMigrations(
        AudiobookPlayerDatabase.MIGRATION_1_2,
        AudiobookPlayerDatabase.MIGRATION_2_3,
        AudiobookPlayerDatabase.MIGRATION_3_4,
    ).build()

    @Singleton
    @Provides
    fun provideAudiobookDao(database: AudiobookPlayerDatabase) =
        database.audiobookDao()

    @Singleton
    @Provides
    fun provideTrackDao(database: AudiobookPlayerDatabase) =
        database.trackDao()

    @Singleton
    @Provides
    fun providePlaybackStateDao(database: AudiobookPlayerDatabase) =
        database.playbackStateDao()

    @Singleton
    @Provides
    fun provideBookmarkDao(database: AudiobookPlayerDatabase) =
        database.bookmarkDao()
}
