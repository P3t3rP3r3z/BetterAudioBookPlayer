package com.aikipunk.betteraudiobookplayer.service

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackServiceManagerTest {
    @Test
    fun `start and stop create intents for playback service`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val manager = PlaybackServiceManager(application)

        manager.startPlaybackService()
        manager.stopPlaybackService()

        assertEquals(AudiobookPlaybackService::class.java.name, shadowOf(application).nextStartedService.component?.className)
        assertEquals(AudiobookPlaybackService::class.java.name, shadowOf(application).nextStoppedService.component?.className)
    }
}
