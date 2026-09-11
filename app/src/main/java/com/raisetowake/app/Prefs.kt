package com.raisetowake.app

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val PREFS_NAME = "raise_to_wake_prefs"
    private const val KEY_SERVICE_ENABLED = "service_enabled"
    private const val KEY_SENSITIVITY = "sensitivity"

    const val DEFAULT_SENSITIVITY = 5f
    const val MIN_SENSITIVITY = 1f
    const val MAX_SENSITIVITY = 10f

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isServiceEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SERVICE_ENABLED, false)

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    fun getSensitivity(context: Context): Float =
        prefs(context).getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY)

    fun setSensitivity(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_SENSITIVITY, value).apply()
    }
}
