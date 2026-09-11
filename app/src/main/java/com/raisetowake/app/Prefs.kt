package com.raisetowake.app

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val PREF_NAME = "raise_to_wake_prefs"
    private const val KEY_SENSITIVITY = "sensitivity"
    private const val DEFAULT_SENSITIVITY = 2.5f

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getSensitivity(context: Context): Float {
        return getPrefs(context).getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY)
    }

    fun setSensitivity(context: Context, value: Float) {
        getPrefs(context).edit().putFloat(KEY_SENSITIVITY, value).apply()
    }
}
