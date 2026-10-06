package com.aikipunk.betteraudiobookplayer.domain.playback

/** Detects a deliberate shake of the device. */
interface ShakeDetector {
    /** Start listening; [onShake] is called on the main thread. Calling again replaces the callback. */
    fun start(onShake: () -> Unit)

    fun stop()

    /** Short haptic confirmation so the user knows the shake was registered (eyes may be closed). */
    fun acknowledge()
}
