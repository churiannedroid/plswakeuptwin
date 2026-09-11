package com.raisetowake.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Detects an upward "lift" gesture from raw accelerometer data.
 *
 * ### Why [Sensor.TYPE_ACCELEROMETER] instead of the sensors named in the spec
 * [Sensor.TYPE_SIGNIFICANT_MOTION] reports a one-shot event when the device's
 * *location* appears to have changed (e.g. you started walking) — it isn't
 * shaped like a lift gesture and would fire for the wrong reasons (walking
 * with a still phone in your hand) while missing an actual lift on a device
 * that's otherwise stationary. [Sensor.TYPE_WAKE_GESTURE] is a
 * manufacturer-reserved system sensor: `getDefaultSensor()` returns `null`
 * for it on virtually every device because it's wired directly into the
 * platform's own wake handling, not exposed for third-party apps to
 * register against. Using either would make detection worse or simply not
 * work, so this class builds lift detection directly on top of the
 * accelerometer, which is universally available and lets us tune the
 * gesture shape and sensitivity ourselves.
 *
 * ### Algorithm
 * 1. Raw accelerometer samples are low-pass filtered to isolate the
 *    gravity vector (removes hand tremor / vibration noise).
 * 2. Each filtered sample is pushed into a small ring buffer together with
 *    its timestamp, covering the last [WINDOW_MILLIS].
 * 3. On every new sample we compute the angle between the *current* gravity
 *    vector and the *oldest* one still in the window. A large angle change
 *    in a short window means the phone's orientation changed quickly —
 *    i.e. it was picked up / tilted toward the user.
 * 4. A cooldown prevents rapid repeat triggers, and an optional proximity
 *    sensor gate suppresses triggers while the phone is covered (e.g. in a
 *    pocket or bag), on top of the user-configurable angle threshold.
 *
 * This is intentionally simple (no FFT, no matrices, fixed-size ring
 * buffer) so it stays cheap to run continuously while the screen is off.
 */
class MotionDetector(
    context: Context,
    private var sensitivity: Float,
    private val onLiftDetected: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    // Low-pass filtered gravity vector.
    private val gravity = floatArrayOf(0f, 0f, 0f)
    private var gravityInitialized = false

    private data class Sample(val timestampMillis: Long, val x: Float, val y: Float, val z: Float)

    private val window = ArrayDeque<Sample>()

    // True while the proximity sensor reports "far" (uncovered). Defaults
    // to true so devices without a proximity sensor are never blocked.
    @Volatile
    private var isUncovered = true

    private var lastTriggerMillis = 0L

    var isListening = false
        private set

    fun updateSensitivity(newSensitivity: Float) {
        sensitivity = newSensitivity.coerceIn(0f, 1f)
    }

    fun start() {
        if (isListening) return
        val registered = accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: false
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        isListening = registered
        window.clear()
        gravityInitialized = false
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val maxRange = event.sensor.maximumRange
                isUncovered = maxRange <= 0f || event.values[0] >= maxRange
            }
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometerSample(event)
        }
    }

    private fun handleAccelerometerSample(event: SensorEvent) {
        val rawX = event.values[0]
        val rawY = event.values[1]
        val rawZ = event.values[2]

        if (!gravityInitialized) {
            gravity[0] = rawX
            gravity[1] = rawY
            gravity[2] = rawZ
            gravityInitialized = true
        } else {
            gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * rawX
            gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * rawY
            gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * rawZ
        }

        // event.timestamp is nanoseconds on a monotonic clock; fine to use
        // purely for relative window math (never compared against wall time).
        val nowMillis = event.timestamp / 1_000_000L

        window.addLast(Sample(nowMillis, gravity[0], gravity[1], gravity[2]))
        while (window.isNotEmpty() && nowMillis - window.first().timestampMillis > WINDOW_MILLIS) {
            window.removeFirst()
        }

        val reference = window.firstOrNull() ?: return
        val elapsedSinceReference = nowMillis - reference.timestampMillis
        if (elapsedSinceReference < MIN_WINDOW_MILLIS_FOR_TRIGGER) return

        val angleDegrees = angleBetween(
            reference.x, reference.y, reference.z,
            gravity[0], gravity[1], gravity[2]
        )

        // sensitivity 0f -> MAX_THRESHOLD_DEGREES (hardest to trigger)
        // sensitivity 1f -> MIN_THRESHOLD_DEGREES (easiest to trigger)
        val thresholdDegrees =
            MAX_THRESHOLD_DEGREES - sensitivity * (MAX_THRESHOLD_DEGREES - MIN_THRESHOLD_DEGREES)

        if (angleDegrees >= thresholdDegrees && isUncovered) {
            val nowWall = System.currentTimeMillis()
            if (nowWall - lastTriggerMillis >= COOLDOWN_MILLIS) {
                lastTriggerMillis = nowWall
                onLiftDetected()
            }
        }
    }

    private fun angleBetween(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float
    ): Double {
        val dot = (ax * bx + ay * by + az * bz).toDouble()
        val magA = sqrt((ax * ax + ay * ay + az * az).toDouble())
        val magB = sqrt((bx * bx + by * by + bz * bz).toDouble())
        if (magA == 0.0 || magB == 0.0) return 0.0
        val cosTheta = (dot / (magA * magB)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosTheta))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op: accuracy changes don't affect this gesture-shape detector.
    }

    companion object {
        private const val ALPHA = 0.8f
        private const val WINDOW_MILLIS = 600L
        private const val MIN_WINDOW_MILLIS_FOR_TRIGGER = 150L
        private const val COOLDOWN_MILLIS = 2000L

        // Angle-change thresholds, in degrees, over the WINDOW_MILLIS window.
        private const val MIN_THRESHOLD_DEGREES = 12.0 // sensitivity = 1.0
        private const val MAX_THRESHOLD_DEGREES = 55.0 // sensitivity = 0.0
    }
}
