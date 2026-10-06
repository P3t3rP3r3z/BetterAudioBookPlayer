package com.aikipunk.betteraudiobookplayer.data.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class VolumeFadeController(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
    private val sleep: suspend (Long) -> Unit,
    private val applyVolume: (Float) -> Unit,
) {
    private var fadeJob: Job? = null

    val isRunning: Boolean
        get() = fadeJob?.isActive == true

    fun start(durationMs: Long, enabled: Boolean, currentVolume: Float, targetVolume: () -> Float) {
        cancel()
        if (!enabled || durationMs <= 0L) return

        fadeJob = scope.launch {
            val startTime = nowMs()
            while (true) {
                sleep(TICK_MS)
                val elapsed = (nowMs() - startTime).coerceAtLeast(0L)
                val progress = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(MIN_PROGRESS, MAX_PROGRESS)
                val latestTarget = targetVolume()
                applyVolume(currentVolume + (latestTarget - currentVolume) * progress)
                if (progress >= MAX_PROGRESS) break
            }
        }
    }

    fun cancel() {
        fadeJob?.cancel()
        fadeJob = null
    }

    companion object {
        const val TICK_MS = 50L
        private const val MIN_PROGRESS = 0f
        private const val MAX_PROGRESS = 1f
    }
}
