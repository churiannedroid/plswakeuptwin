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
        return (8.5f - clamped * 0.65f).coerceAtLeast(1.5f)
    }

    fun start(sensorManager: SensorManager) {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
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

        if (!isInitialized) {
            lastZ = z
            lastMagnitude = magnitude
            isInitialized = true
            return
        }

        val deltaZ = z - lastZ
        val deltaMagnitude = abs(magnitude - lastMagnitude)

        val now = System.currentTimeMillis()
        val cooledDown = (now - lastTriggerTime) > COOLDOWN_MS

        // Lift gesture: rotation from flat toward upright (rising Z),
        // paired with a motion burst above the sensitivity threshold,
        // and a minimum upward-facing tilt to reject pocket jostling.
        if (cooledDown && deltaZ > deltaThreshold && deltaMagnitude > (deltaThreshold * 0.5f) && y > 1.5f) {
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
