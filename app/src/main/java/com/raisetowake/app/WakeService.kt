package com.raisetowake.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that keeps [MotionDetector] alive in the background
 * and turns the screen on when a lift gesture is detected.
 *
 * Sensor registration is toggled by screen state: we only actually listen
 * to the accelerometer while the screen is OFF (there's nothing to wake
 * while it's already on), which is both correct and meaningfully cheaper
 * on battery than listening unconditionally.
 */
class WakeService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var motionDetector: MotionDetector
    private lateinit var prefs: SharedPreferences
    private lateinit var powerManager: PowerManager

    private var screenStateReceiverRegistered = false
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> motionDetector.start()
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> motionDetector.stop()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        val sensitivity = prefs.getFloat(Prefs.SENSITIVITY, Prefs.DEFAULT_SENSITIVITY)
        motionDetector = MotionDetector(applicationContext, sensitivity) { onLiftDetected() }
        prefs.registerOnSharedPreferenceChangeListener(this)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // These are protected system broadcasts (only the OS can send them),
        // so RECEIVER_NOT_EXPORTED is the correct, safe choice on API 33+.
        ContextCompat.registerReceiver(this, screenStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenStateReceiverRegistered = true

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        startForegroundCompat()
        prefs.edit().putBoolean(Prefs.SERVICE_ENABLED, true).apply()

        // If the screen happens to already be off when we're (re)started,
        // start listening immediately rather than waiting for the next
        // ACTION_SCREEN_OFF broadcast.
        if (!powerManager.isInteractive) {
            motionDetector.start()
        }
        return START_STICKY
    }

    private fun onLiftDetected() {
        acquireWakeScreen()
    }

    private fun acquireWakeScreen() {
        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "$packageName:RaiseToWakeLock"
        )
        // SCREEN_BRIGHT_WAKE_LOCK / ACQUIRE_CAUSES_WAKEUP / ON_AFTER_RELEASE
        // are deprecated (API 17+) but remain fully functional through
        // Android 14/15 and are exactly what's needed here: turn the screen
        // on like a power-button press would, then let it follow the
        // user's normal screen-off timeout via ON_AFTER_RELEASE. The
        // modern replacement (Activity.setTurnScreenOn / setShowWhenLocked)
        // requires launching an Activity over the lock screen, which is a
        // heavier, more intrusive UX than simply waking the display.
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MILLIS)
        Log.d(TAG, "Lift detected - screen wake requested")
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == Prefs.SENSITIVITY) {
            motionDetector.updateSensitivity(prefs.getFloat(Prefs.SENSITIVITY, Prefs.DEFAULT_SENSITIVITY))
        }
    }

    private fun stopSelfCleanly() {
        prefs.edit().putBoolean(Prefs.SERVICE_ENABLED, false).apply()
        motionDetector.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        motionDetector.stop()
        if (screenStateReceiverRegistered) {
            unregisterReceiver(screenStateReceiver)
            screenStateReceiverRegistered = false
        }
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.notification_stop_action), stopIntent)
            .build()
    }

    companion object {
        private const val TAG = "WakeService"
        const val CHANNEL_ID = "raise_to_wake_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.raisetowake.app.action.STOP"
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 8_000L

        fun startIntent(context: Context): Intent = Intent(context, WakeService::class.java)

        fun stopIntent(context: Context): Intent =
            Intent(context, WakeService::class.java).setAction(ACTION_STOP)
    }
}
