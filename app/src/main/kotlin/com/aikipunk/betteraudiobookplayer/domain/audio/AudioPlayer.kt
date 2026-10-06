package com.aikipunk.betteraudiobookplayer.domain.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * AudioPlayer is the domain abstraction for audio playback.
 * Implementations handle the actual playback engine (ExoPlayer, etc).
 */
interface AudioPlayer {
    /**
     * Playback state flow that emits whenever state changes.
     */
    val playbackState: StateFlow<PlaybackState>

    /**
     * Current position in milliseconds.
     */
    val currentPosition: StateFlow<Long>

    /**
     * Current playback speed.
     */
    val playbackSpeed: StateFlow<Float>

    /**
     * Duration of the current item in milliseconds.
     */
    val duration: StateFlow<Long>

    /**
     * Error state when playback fails.
     */
    val error: Flow<String?>

    /**
     * Load audio file at the given path.
     * @param filePath Absolute path to the audio file
     * @param positionMs Optional position to resume from (default 0)
     */
    suspend fun loadAudio(filePath: String, positionMs: Long = 0, nowPlaying: NowPlayingInfo? = null)

    /**
     * Load a whole book as a gapless playlist (one item per chapter file); playback advances
     * to the next chapter automatically.
     * @param key identifies the playlist (the audiobook id) for [currentPlaylistKey]
     */
    suspend fun loadPlaylist(key: String, items: List<PlaylistItem>, startIndex: Int, positionMs: Long)

    /** Refreshes title/author/cover shown in system media controls without interrupting playback. */
    suspend fun updateNowPlaying(items: List<NowPlayingInfo>)

    /** Jump to a chapter in the loaded playlist. */
    suspend fun seekToItem(index: Int, positionMs: Long = 0)

    /** Key of the loaded playlist, or null if nothing (or a single file) is loaded. */
    fun currentPlaylistKey(): String?

    /** Index of the playing chapter within the loaded playlist. */
    val currentItemIndex: StateFlow<Int>

    /**
     * Start playback.
     */
    suspend fun play()

    /**
     * Pause playback.
     */
    suspend fun pause()

    /**
     * Stop playback and release resources.
     */
    suspend fun stop()

    /**
     * Seek to position in milliseconds.
     */
    suspend fun seekTo(positionMs: Long)

    /**
     * Set playback speed multiplier.
     * @param speed Speed multiplier (e.g., 1.0 = normal, 0.5 = half-speed, 2.0 = double-speed)
     */
    suspend fun setSpeed(speed: Float)

    /** Output volume of this player only, including app-level boost up to 150%. */
    fun setVolume(volume: Float)

    /** Optional gradual fade to the current app-volume target over [durationMs]. */
    fun startVolumeFade(durationMs: Long) = Unit

    /**
     * Check if player is currently playing.
     */
    fun isPlaying(): Boolean

    /** The file path / URI most recently passed to [loadAudio], or null if nothing is loaded. */
    fun currentSource(): String?

    /**
     * Get current position without waiting for Flow.
     */
    fun getCurrentPositionMs(): Long

    /**
     * Get current speed without waiting for Flow.
     */
    fun getCurrentSpeed(): Float

    /**
     * Release all resources.
     */
    fun release()
}

/**
 * Represents the current playback state.
 */
data class PlaylistItem(
    val source: String,
    val nowPlaying: NowPlayingInfo? = null,
)

/** What the system (notification, lock screen, Bluetooth, car) shows for the loaded audio. */
data class NowPlayingInfo(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val artworkUri: String? = null,
)

enum class PlaybackState {
    IDLE,           // No media loaded
    BUFFERING,      // Loading/buffering media
    READY,          // Ready to play but not playing
    PLAYING,        // Currently playing
    PAUSED,         // Paused
    ENDED,          // Finished playing
    ERROR,          // Error occurred
}
