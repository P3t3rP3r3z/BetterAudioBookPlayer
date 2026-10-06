package com.aikipunk.betteraudiobookplayer.service

import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts/stops [AudiobookPlaybackService]. The service must exist before playback starts
 * so its MediaSession can publish the notification and promote itself to foreground.
 */
@Singleton
@UnstableApi
class PlaybackServiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Safe to call on every Play (while the app is in the foreground). */
    fun startPlaybackService() {
        context.startService(Intent(context, AudiobookPlaybackService::class.java))
    }

    /** Pauses playback and stops the service (e.g. when the user quits the app). */
    fun stopPlaybackService() {
        context.stopService(Intent(context, AudiobookPlaybackService::class.java))
    }
}
