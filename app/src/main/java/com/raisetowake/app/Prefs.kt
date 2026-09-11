package com.raisetowake.app

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val NAME = "raise_to_wake_prefs"
    const val SERVICE_ENABLED = "service_enabled"
    const val SENSITIVITY = "sensitivity"
    const val DEFAULT_SENSITIVITY = 2.5f

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    fun getSensitivity(context: Context): Float {
        return getPrefs(context).getFloat(SENSITIVITY, DEFAULT_SENSITIVITY)
    }

    fun setSensitivity(context: Context, value: Float) {
        getPrefs(context).edit().putFloat(SENSITIVITY, value).apply()
    }

    fun isServiceEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(SERVICE_ENABLED, false)
    }

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(SERVICE_ENABLED, enabled).apply()
    }
}
