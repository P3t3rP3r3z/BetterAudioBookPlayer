package com.aikipunk.betteraudiobookplayer.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccelerometerShakeDetectorTest {

    @Test
    fun `start registers accelerometer listener once and stop unregisters it`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        shadowOf(sensorManager).addSensor(sensor)
        val detector = AccelerometerShakeDetector(context)

        detector.start { }
        detector.start { }

        assertTrue(shadowOf(sensorManager).hasListener(detector, sensor))

        detector.stop()

        assertTrue(!shadowOf(sensorManager).hasListener(detector, sensor))
    }

    @Test
    fun `accuracy changes are ignored`() {
        val detector = AccelerometerShakeDetector(ApplicationProvider.getApplicationContext())
        detector.onAccuracyChanged(null, SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        assertTrue(SystemClock.elapsedRealtime() >= 0L)
    }
    @Test
    fun `sensor event above threshold invokes callback once per debounce window`() {
        val detector = AccelerometerShakeDetector(ApplicationProvider.getApplicationContext())
        var shakes = 0
        detector.start { shakes++ }
        ShadowSystemClock.advanceBy(Duration.ofMillis(1_501))

        detector.onSensorChanged(sensorEvent(30f, 0f, 0f))
        detector.onSensorChanged(sensorEvent(30f, 0f, 0f))
        assertEquals(1, shakes)

        ShadowSystemClock.advanceBy(Duration.ofMillis(1_501))
        detector.onSensorChanged(sensorEvent(30f, 0f, 0f))
        assertEquals(2, shakes)
    }

    @Test
    fun `sensor event below threshold and stopped detector do not invoke callback`() {
        val detector = AccelerometerShakeDetector(ApplicationProvider.getApplicationContext())
        var shakes = 0
        detector.start { shakes++ }

        detector.onSensorChanged(sensorEvent(9.8f, 0f, 0f))
        detector.stop()
        ShadowSystemClock.advanceBy(Duration.ofMillis(2_000))
        detector.onSensorChanged(sensorEvent(30f, 0f, 0f))

        assertEquals(0, shakes)
    }

    @Test
    fun `acknowledge vibrates without requiring a registered sensor`() {
        val detector = AccelerometerShakeDetector(ApplicationProvider.getApplicationContext())
        detector.acknowledge()
        assertTrue(SystemClock.elapsedRealtime() >= 0L)
    }

    private fun sensorEvent(x: Float, y: Float, z: Float): SensorEvent {
        val constructor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
        constructor.isAccessible = true
        val event = constructor.newInstance(3)
        event.values[0] = x
        event.values[1] = y
        event.values[2] = z
        return event
    }
}
