package com.raisetowake.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restarts [WakeService] after a reboot, but only if the user had it
 * switched on before the device restarted. Starting a foreground service
 * directly from a BOOT_COMPLETED receiver is one of the explicit
 * exemptions to Android's background-start restrictions, so this is safe
 * even on Android 12+.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != ACTION_QUICKBOOT_POWERON) {
            return
        }

        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val wasEnabled = prefs.getBoolean(Prefs.SERVICE_ENABLED, false)
        if (wasEnabled) {
            ContextCompat.startForegroundService(context, WakeService.startIntent(context))
        }
    }

    companion object {
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
