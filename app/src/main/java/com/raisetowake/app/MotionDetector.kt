package com.raisetowake.app

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

class MotionDetector(
    initialSensitivity: Float,
    private val onRaiseDetected: () -> Unit
) : SensorEventListener {

    private var deltaThreshold: Float = computeThreshold(initialSensitivity)

    private var lastZ = 0f
    private var lastMagnitude = 0f
    private var lastTriggerTime = 0L
    private var isInitialized = false

    fun updateSensitivity(newSensitivity: Float) {
        deltaThreshold = computeThreshold(newSensitivity)
    }

    private fun computeThreshold(value: Float): Float {
        val clamped = value.coerceIn(Prefs.MIN_SENSITIVITY, Prefs.MAX_SENSITIVITY)
        return (2.5f - clamped * 0.1f).coerceAtLeast(0.3f)
    }

    fun start(sensorManager: SensorManager) {
    // true = request the WAKE_UP variant so events are delivered
    // even while the screen is off and the CPU is suspended.
    val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) // fallback if no wake-up variant exists

    sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
}

    fun stop(sensorManager: SensorManager) {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
    if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

    val x = event.values[0]
    val y = event.values[1]
    val z = event.values[2]
    val magnitude = sqrt(x * x + y * y + z * z)

    android.util.Log.d("MotionDetector", "x=$x y=$y z=$z fallingZ=${lastZ - z}")

    if (!isInitialized) {
        lastZ = z
        lastMagnitude = magnitude
        isInitialized = true
        return
    }

    // ... rest of the function stays the same

        // Z DROPS as the phone rotates from lying flat toward upright —
        // so the trigger direction is (lastZ - z), not (z - lastZ).
        val fallingZ = lastZ - z
        val deltaMagnitude = abs(magnitude - lastMagnitude)

        val now = System.currentTimeMillis()
        val cooledDown = (now - lastTriggerTime) > COOLDOWN_MS

        // Lift gesture: Z falling away from "flat" toward "upright",
        // a real motion burst (not just noise), and Y indicating the
        // phone is now tilted toward vertical (rejects pocket jostling).
        if (cooledDown && fallingZ > deltaThreshold && deltaMagnitude > (deltaThreshold * 0.5f)) {
            lastTriggerTime = now
            onRaiseDetected()
        }

        lastZ = z
        lastMagnitude = magnitude
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val COOLDOWN_MS = 1500L
    }
}
