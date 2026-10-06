package com.aikipunk.betteraudiobookplayer

import android.app.Application
import com.aikipunk.betteraudiobookplayer.data.transfer.ShelfBackup
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AudiobookPlayerApp : Application() {

    @Inject
    lateinit var shelfBackup: ShelfBackup

    override fun onCreate() {
        super.onCreate()
        shelfBackup.start()
    }
}
