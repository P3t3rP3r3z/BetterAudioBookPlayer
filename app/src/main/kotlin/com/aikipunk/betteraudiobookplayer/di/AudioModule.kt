package com.aikipunk.betteraudiobookplayer.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.aikipunk.betteraudiobookplayer.data.audio.ExoPlayerAdapter
import com.aikipunk.betteraudiobookplayer.data.sensor.AccelerometerShakeDetector
import com.aikipunk.betteraudiobookplayer.domain.audio.AudioPlayer
import com.aikipunk.betteraudiobookplayer.domain.playback.ShakeDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    @Singleton
    @Provides
    @UnstableApi
    fun provideAudioPlayer(adapter: ExoPlayerAdapter): AudioPlayer = adapter

    @Singleton
    @Provides
    fun provideShakeDetector(detector: AccelerometerShakeDetector): ShakeDetector = detector
}
