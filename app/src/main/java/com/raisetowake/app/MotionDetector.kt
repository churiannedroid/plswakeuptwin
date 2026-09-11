package com.raisetowake.app

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class MotionDetector(private val onRaiseDetected: () -> Unit) : SensorEventListener {
    private var lastZ = 0f
    private var lastUpdate: Long = 0

    fun start(sensorManager: SensorManager) {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop(sensorManager: SensorManager) {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val curTime = System.currentTimeMillis()
        if ((curTime - lastUpdate) > 200) {
            val y = event.values[1]
            val z = event.values[2]

            val deltaZ = z - lastZ
            if (y > 5.0f && deltaZ > 2.0f) {
                onRaiseDetected()
            }

            lastZ = z
            lastUpdate = curTime
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
