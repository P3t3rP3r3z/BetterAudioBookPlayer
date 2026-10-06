package com.aikipunk.betteraudiobookplayer.data.audio

import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeFadeControllerTest {
    @Test
    fun `fade disabled does not animate`() = runFadeTest {
        val applied = mutableListOf<Float>()
        val controller = newController(applied)

        controller.start(durationMs = FADE_DURATION_MS, enabled = false, currentVolume = FULL_VOLUME) { TARGET_VOLUME }
        advanceTimeBy(FADE_DURATION_MS)
        runCurrent()

        assertTrue(applied.isEmpty())
    }

    @Test
    fun `fade enabled animates from current to target over duration`() = runFadeTest {
        val applied = mutableListOf<Float>()
        val controller = newController(applied)

        controller.start(durationMs = FADE_DURATION_MS, enabled = true, currentVolume = FULL_VOLUME) { TARGET_VOLUME }
        advanceTimeBy(FADE_DURATION_MS)
        runCurrent()

        assertTrue(applied.isNotEmpty())
        assertApprox(TARGET_VOLUME, applied.last())
        assertTrue(applied.zipWithNext().all { (before, after) -> after <= before })
    }

    @Test
    fun `fade respects halfway and full duration positions`() = runFadeTest {
        val applied = mutableListOf<Float>()
        val controller = newController(applied)

        controller.start(durationMs = FADE_DURATION_MS, enabled = true, currentVolume = FULL_VOLUME) { TARGET_VOLUME }

        advanceTimeBy(FADE_DURATION_MS / 2)
        runCurrent()
        assertApprox(HALF_VOLUME, applied.last())

        advanceTimeBy(FADE_DURATION_MS / 2)
        runCurrent()
        assertApprox(TARGET_VOLUME, applied.last())
    }

    private fun TestScope.newController(applied: MutableList<Float>) =
        VolumeFadeController(
            scope = backgroundScope,
            nowMs = { testScheduler.currentTime },
            sleep = { delay(it) },
            applyVolume = { applied += it },
        )

    private fun assertApprox(expected: Float, actual: Float) {
        assertTrue("Expected $expected but was $actual", abs(expected - actual) <= EPSILON)
    }

    private fun runFadeTest(block: suspend TestScope.() -> Unit) = runTest(StandardTestDispatcher(), testBody = block)

    companion object {
        private const val FADE_DURATION_MS = 200L
        private const val FULL_VOLUME = 1.0f
        private const val TARGET_VOLUME = 0.5f
        private const val HALF_VOLUME = 0.75f
        private const val EPSILON = 0.051f
    }
}
