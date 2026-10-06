package com.aikipunk.betteraudiobookplayer.data.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.aikipunk.betteraudiobookplayer.domain.playback.ShakeDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class AccelerometerShakeDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) : ShakeDetector, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var onShake: (() -> Unit)? = null
    private var lastShakeAt = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    override fun start(onShake: () -> Unit) {
        this.onShake = onShake
        val sensor = accelerometer ?: return
        if (wakeLock == null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            // Playback may already be paused (grace period), so keep the CPU awake briefly to hear shakes
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(MAX_LISTEN_MS)
            }
        }
    }

    override fun stop() {
        onShake = null
        sensorManager.unregisterListener(this)
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    @SuppressLint("MissingPermission")
    override fun acknowledge() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        val now = SystemClock.elapsedRealtime()
        if (gForce > SHAKE_THRESHOLD_G && now - lastShakeAt > DEBOUNCE_MS) {
            lastShakeAt = now
            onShake?.invoke()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val SHAKE_THRESHOLD_G = 2.5f
        const val DEBOUNCE_MS = 1_500L
        const val VIBRATE_MS = 120L
        const val MAX_LISTEN_MS = 3 * 60_000L
        const val WAKE_LOCK_TAG = "BetterAudiobookPlayer:SleepTimerShake"
    }
}
