package com.projectsuperhuman.next

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-level bootstrap for already configured direct devices.
 *
 * Pairing and permissions remain owned by Settings -> Smart Devices, but saved devices may
 * reconnect on app launch when the user has not explicitly disconnected them.
 */
internal object SmartDeviceRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _liveHeartRateReadings = MutableStateFlow<List<SmartDeviceReading>>(emptyList())
    val liveHeartRateReadings: StateFlow<List<SmartDeviceReading>> = _liveHeartRateReadings.asStateFlow()

    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        val appContext = context.applicationContext
        H19cWearableRuntime.initialize(appContext)
        CardioSensorRuntime.initialize(appContext)

        fun publishHeartRateReadings() {
            _liveHeartRateReadings.value = buildList {
                SmartDeviceObservationMapper.h19cHeartRate(H19cWearableRuntime.state.value)?.let { add(it) }
                SmartDeviceObservationMapper.bleHeartRate(CardioSensorRuntime.bleSensorState.value)?.let { add(it) }
            }.distinctBy { reading ->
                reading.provenance.device.deviceId
                    ?: "${reading.provenance.sourceLabel}:${reading.provenance.device.displayName}"
            }
        }

        scope.launch {
            H19cWearableRuntime.state.collect {
                publishHeartRateReadings()
            }
        }
        scope.launch {
            CardioSensorRuntime.bleSensorState.collect {
                publishHeartRateReadings()
            }
        }
        publishHeartRateReadings()

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
