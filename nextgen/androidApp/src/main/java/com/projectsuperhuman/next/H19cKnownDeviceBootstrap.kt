package com.projectsuperhuman.next

import android.content.Context

/**
 * Known physical H19C used while bringing up the direct BLE integration.
 *
 * The address is deliberately stored through the same SharedPreferences contract used by
 * [H19cWearableRuntime], so the existing reconnect path can bypass BLE discovery and connect
 * straight to the user's watch. This is a bring-up aid, not a general device registry.
 */
internal object H19cKnownDeviceBootstrap {
    const val DEVICE_NAME = "H19C"
    const val DEVICE_ADDRESS = "E2:50:05:EF:2E:67"

    private const val PREFS = "project_superhuman_h19c"
    private const val PREF_ADDRESS = "saved_address"
    private const val PREF_NAME = "saved_name"

    fun seed(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(PREF_ADDRESS, null) == DEVICE_ADDRESS &&
            prefs.getString(PREF_NAME, null) == DEVICE_NAME
        ) return

        prefs.edit()
            .putString(PREF_ADDRESS, DEVICE_ADDRESS)
            .putString(PREF_NAME, DEVICE_NAME)
            .apply()
    }
}
