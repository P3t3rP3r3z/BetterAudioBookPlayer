package com.aikipunk.betteraudiobookplayer.domain.playback

import android.os.SystemClock
import com.aikipunk.betteraudiobookplayer.data.preferences.UserPreferences
import com.aikipunk.betteraudiobookplayer.domain.audio.AudioPlayer
import com.aikipunk.betteraudiobookplayer.domain.audio.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SleepTimerState {
    data object Off : SleepTimerState

    /** Counts down only while audio is playing. */
    data class Countdown(val remainingMs: Long) : SleepTimerState

    /** Stops when the current chapter finishes; [remainingMs] is real time left at the current speed. */
    data class EndOfChapter(val remainingMs: Long) : SleepTimerState
}

/**
 * Pauses playback after a duration (or at the end of the chapter), fading out over the last
 * [FADE_MS]. Near the end, and for [GRACE_MS] after it stops, shaking the phone adds [EXTEND_MS]
 * (if enabled in settings). Each start or extension records a [SleepMarker]; resuming the book
 * after the timer has stopped clears it.
 */
@Singleton
class SleepTimer @Inject constructor(
    private val audioPlayer: AudioPlayer,
    private val shakeDetector: ShakeDetector,
    private val userPreferences: UserPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var graceJob: Job? = null
    private var graceUntil = 0L

    init {
        // Resuming the same book once the timer is off means the listener is awake again
        scope.launch {
            audioPlayer.playbackState.collect { playbackState ->
                val marker = userPreferences.sleepMarker.value ?: return@collect
                if (playbackState == PlaybackState.PLAYING &&
                    _state.value == SleepTimerState.Off &&
                    audioPlayer.currentPlaylistKey() == marker.audiobookId
                ) {
                    userPreferences.setSleepMarker(null)
                }
            }
        }
    }

    fun start(durationMs: Long) {
        require(durationMs > 0) { "Sleep timer duration must be positive" }
        launchCountdown(durationMs)
    }

    fun startEndOfChapter() {
        markPosition()
        resetJobs()
        restoreAppVolume()
        _state.value = SleepTimerState.EndOfChapter(Long.MAX_VALUE)
        timerJob = scope.launch { runEndOfChapter() }
    }

    fun extend(extraMs: Long = EXTEND_MS) {
        when (val current = _state.value) {
            is SleepTimerState.Countdown -> launchCountdown(current.remainingMs + extraMs)
            is SleepTimerState.EndOfChapter -> launchCountdown(current.remainingMs + extraMs)
            SleepTimerState.Off -> Unit
        }
    }

    fun cancel() {
        resetJobs()
        shakeDetector.stop()
        restoreAppVolume()
        _state.value = SleepTimerState.Off
    }

    /** Keeps a single marker at the latest point the timer was started or extended. */
    private fun markPosition() {
        val bookId = audioPlayer.currentPlaylistKey() ?: return
        userPreferences.setSleepMarker(
            SleepMarker(bookId, audioPlayer.currentItemIndex.value, audioPlayer.getCurrentPositionMs()),
        )
    }

    private fun resetJobs() {
        timerJob?.cancel()
        timerJob = null
        graceJob?.cancel()
        graceJob = null
        graceUntil = 0L
    }

    private fun launchCountdown(durationMs: Long) {
        markPosition()
        resetJobs()
        restoreAppVolume()
        audioPlayer.startVolumeFade(durationMs)
        _state.value = SleepTimerState.Countdown(durationMs)
        timerJob = scope.launch { runCountdown(durationMs) }
    }

    private suspend fun runCountdown(durationMs: Long) {
        var remaining = durationMs
        var last = now()
        while (remaining > 0) {
            delay(TICK_MS)
            val t = now()
            val playing = audioPlayer.isPlaying()
            if (playing) remaining -= t - last
            last = t
            remaining = remaining.coerceAtLeast(0)
            _state.value = SleepTimerState.Countdown(remaining)
            if (!userPreferences.gradualVolumeFade.value) {
                audioPlayer.setVolume(appVolume() * fadeVolume(remaining))
            }
            updateShakeListening(remaining, playing)
        }
        expire(rewindMs = REWIND_MS)
    }

    private suspend fun runEndOfChapter() {
        var chapterIndex = audioPlayer.currentItemIndex.value
        var lastRemaining = Long.MAX_VALUE
        var startedGradualFade = false
        while (true) {
            val index = audioPlayer.currentItemIndex.value
            if (index != chapterIndex) {
                // Natural rollover into the next chapter ends the timer; a manual jump just retargets it
                if (lastRemaining <= NATURAL_END_WINDOW_MS) {
                    expire(rewindMs = 0)
                    return
                }
                chapterIndex = index
                startedGradualFade = false
            }
            if (audioPlayer.playbackState.value == PlaybackState.ENDED) {
                expire(rewindMs = 0)
                return
            }
            val duration = audioPlayer.duration.value
            val speed = audioPlayer.getCurrentSpeed().coerceAtLeast(MIN_SPEED)
            val remaining = if (duration > 0) {
                ((duration - audioPlayer.getCurrentPositionMs()).coerceAtLeast(0) / speed).toLong()
            } else {
                Long.MAX_VALUE
            }
            lastRemaining = remaining
            val playing = audioPlayer.isPlaying()
            _state.value = SleepTimerState.EndOfChapter(remaining)
            if (userPreferences.gradualVolumeFade.value) {
                if (!startedGradualFade && remaining != Long.MAX_VALUE) {
                    audioPlayer.startVolumeFade(remaining)
                    startedGradualFade = true
                }
            } else if (playing) {
                audioPlayer.setVolume(appVolume() * fadeVolume(remaining))
            }
            updateShakeListening(remaining, playing)
            delay(TICK_MS)
        }
    }

    private suspend fun expire(rewindMs: Long) {
        timerJob = null
        val position = audioPlayer.getCurrentPositionMs()
        audioPlayer.pause()
        restoreAppVolume()
        if (rewindMs > 0) audioPlayer.seekTo((position - rewindMs).coerceAtLeast(0))
        _state.value = SleepTimerState.Off

        if (userPreferences.shakeToExtendSleepTimer.value) {
            graceUntil = now() + GRACE_MS
            shakeDetector.start(::onShake)
            graceJob = scope.launch {
                delay(GRACE_MS)
                graceUntil = 0L
                if (_state.value == SleepTimerState.Off) shakeDetector.stop()
            }
        } else {
            shakeDetector.stop()
        }
    }

    private fun updateShakeListening(remainingMs: Long, playing: Boolean) {
        val listen = userPreferences.shakeToExtendSleepTimer.value && playing && remainingMs <= SHAKE_WINDOW_MS
        if (listen) shakeDetector.start(::onShake) else shakeDetector.stop()
    }

    private fun onShake() {
        if (!userPreferences.shakeToExtendSleepTimer.value) return
        when (_state.value) {
            is SleepTimerState.Countdown, is SleepTimerState.EndOfChapter -> {
                shakeDetector.acknowledge()
                extend()
            }
            SleepTimerState.Off -> if (now() < graceUntil) {
                shakeDetector.acknowledge()
                launchCountdown(EXTEND_MS)
                scope.launch { audioPlayer.play() }
            }
        }
    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun appVolume(): Float = userPreferences.appVolume.value / APP_VOLUME_SCALE

    private fun restoreAppVolume() {
        audioPlayer.setVolume(appVolume())
    }

    companion object {
        const val FADE_MS = 30_000L
        const val EXTEND_MS = 10 * 60_000L
        const val REWIND_MS = 60_000L
        const val SHAKE_WINDOW_MS = 60_000L
        const val GRACE_MS = 60_000L

        /** End-of-chapter is only offered for tracks shorter than this (longer ones are whole books). */
        const val END_OF_CHAPTER_MAX_TRACK_MS = 2 * 60 * 60_000L

        private const val TICK_MS = 250L
        private const val NATURAL_END_WINDOW_MS = 3_000L
        private const val MIN_SPEED = 0.1f
        private const val APP_VOLUME_SCALE = 100f

        /** Player volume for the time left: full until the last [FADE_MS], then linear to silence. */
        fun fadeVolume(remainingMs: Long): Float =
            if (remainingMs >= FADE_MS) 1f else (remainingMs.toFloat() / FADE_MS).coerceIn(0f, 1f)
    }
}
