package com.projectsuperhuman.next

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * App-level bootstrap for already configured direct devices.
 *
 * Pairing and permissions remain owned by Settings -> Smart Devices, but saved devices may
 * reconnect on app launch when the user has not explicitly disconnected them.
 */
internal object SmartDeviceRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        val appContext = context.applicationContext
        H19cWearableRuntime.initialize(appContext)
        CardioSensorRuntime.initialize(appContext)

        if (
            H19cWearableRuntime.state.value.deviceAddress != null &&
            H19cWearableRuntime.shouldAutoReconnect(appContext) &&
            H19cWearableRuntime.hasPermissions(appContext)
        ) {
            scope.launch {
                H19cWearableRuntime.reconnectSaved(appContext)
            }
        }

        if (
            CardioSensorRuntime.hasSavedBleDevice() &&
            CardioSensorRuntime.shouldAutoReconnectBle() &&
            CardioSensorRuntime.hasBlePermissions()
        ) {
            scope.launch {
                CardioSensorRuntime.reconnectBleDevice()
            }
        }

        if (OkokScaleManager.isEnabled(appContext) && OkokScaleManager.hasPermissions(appContext)) {
            scope.launch {
                val body = NativeDataHub.latestForDomain(com.projectsuperhuman.next.core.HealthDomain.BODY)
                val heightCm = body.firstOrNull { it.metric == "body_height_cm" }?.value
                val male = body.firstOrNull { it.metric == "body_sex_code" }?.value?.let { it >= 0.5 }
                OkokScaleManager.setProfile(heightCm, male)
                OkokScaleManager.startAutoTracking(appContext)
            }
        }
    }
}
