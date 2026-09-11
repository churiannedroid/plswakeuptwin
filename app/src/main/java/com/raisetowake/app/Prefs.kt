package com.raisetowake.app

/**
 * Small, single source of truth for the SharedPreferences file name and
 * keys shared across [MainActivity], [WakeService], and [BootReceiver].
 */
object Prefs {
    const val NAME = "raise_to_wake_prefs"

    /** Whether the user wants the service running. Set authoritatively by [WakeService]. */
    const val SERVICE_ENABLED = "service_enabled"

    /** Lift-detection sensitivity, 0f (least sensitive) .. 1f (most sensitive). */
    const val SENSITIVITY = "sensitivity"

    const val DEFAULT_SENSITIVITY = 0.5f
}
