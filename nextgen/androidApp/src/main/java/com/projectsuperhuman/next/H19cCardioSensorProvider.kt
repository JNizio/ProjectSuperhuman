package com.projectsuperhuman.next

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal object H19cCardioMapper {
    fun map(
        wearable: H19cWearableState,
        reconnecting: Boolean = false,
        nowEpochMs: Long = System.currentTimeMillis()
    ): CardioSensorState {
        val provenance = CardioSensorProvenance(
            providerType = CardioSensorProviderType.H19C,
            sourceName = H19cWearableRuntime.SOURCE,
            transport = CardioSensorTransport.LIVE_BLE,
            deviceName = wearable.deviceName,
            manufacturer = wearable.manufacturer,
            model = wearable.model,
            anonymousSensorId = CardioSensorIds.anonymous(wearable.deviceAddress)
        )
        val rawConnection = when {
            reconnecting && wearable.phase in setOf(H19cConnectionPhase.CONNECTING, H19cConnectionPhase.DISCOVERING) ->
                CardioSensorConnectionState.RECONNECTING
            wearable.phase == H19cConnectionPhase.SCANNING -> CardioSensorConnectionState.SCANNING
            wearable.phase in setOf(H19cConnectionPhase.CONNECTING, H19cConnectionPhase.DISCOVERING) ->
                CardioSensorConnectionState.CONNECTING
            wearable.phase == H19cConnectionPhase.READY -> CardioSensorConnectionState.CONNECTED
            wearable.phase == H19cConnectionPhase.ERROR -> CardioSensorConnectionState.ERROR
            else -> CardioSensorConnectionState.DISCONNECTED
        }
        return CardioSensorState(
            providerType = CardioSensorProviderType.H19C,
            connection = rawConnection,
            currentHeartRateBpm = wearable.heartRateBpm,
            lastSampleEpochMs = wearable.lastHeartRateEpochMs,
            provenance = provenance,
            message = wearable.status,
            reconnectAttempt = if (reconnecting) 1 else 0
        ).withFreshness(nowEpochMs)
    }
}

/**
 * Cardio adapter over the existing H19C runtime. It deliberately does not own H19C GATT: the
 * wearable health screens and Cardio share one connection/runtime and Cardio only observes it.
 */
internal class H19cCardioSensorProvider(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : CardioSensorProvider {
    override val providerType: CardioSensorProviderType = CardioSensorProviderType.H19C

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(CardioSensorState(providerType = providerType))
    override val state: StateFlow<CardioSensorState> = _state.asStateFlow()

    private val _samples = MutableSharedFlow<CardioHeartRateSample>(extraBufferCapacity = 64)
    override val heartRateSamples: SharedFlow<CardioHeartRateSample> = _samples.asSharedFlow()

    private var sessionActive = false
    private var enabledLiveForSession = false
    private var reconnecting = false
    private var lastEmittedKey: Pair<Long, Int>? = null

    init {
        H19cWearableRuntime.initialize(appContext)
        scope.launch {
            H19cWearableRuntime.state.collect { wearable ->
                val mapped = H19cCardioMapper.map(wearable, reconnecting, clock())
                _state.value = mapped
                if (wearable.phase in setOf(H19cConnectionPhase.READY, H19cConnectionPhase.ERROR, H19cConnectionPhase.IDLE)) {
                    reconnecting = false
                }
                if (sessionActive && wearable.connected && !wearable.liveHeartRate) {
                    enabledLiveForSession = true
                    H19cWearableRuntime.setLiveHeartRate(true)
                }
                val timestamp = wearable.lastHeartRateEpochMs
                val bpm = wearable.heartRateBpm
                if (
                    sessionActive &&
                    wearable.connected &&
                    wearable.liveHeartRate &&
                    timestamp != null &&
                    bpm != null &&
                    bpm in CARDIO_HR_MIN_BPM..CARDIO_HR_MAX_BPM
                ) {
                    val key = timestamp to bpm
                    if (key != lastEmittedKey) {
                        lastEmittedKey = key
                        _samples.tryEmit(
                            CardioHeartRateSample(
                                timestampEpochMs = timestamp,
                                bpm = bpm,
                                source = mapped.provenance ?: CardioSensorProvenance(
                                    providerType = providerType,
                                    sourceName = H19cWearableRuntime.SOURCE,
                                    transport = CardioSensorTransport.LIVE_BLE
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    override suspend fun connect() {
        reconnecting = H19cWearableRuntime.state.value.deviceAddress != null
        if (reconnecting) H19cWearableRuntime.reconnectSaved(appContext)
        else H19cWearableRuntime.scanAndConnect(appContext)
    }

    override suspend fun disconnect() {
        reconnecting = false
        sessionActive = false
        enabledLiveForSession = false
        H19cWearableRuntime.disconnect()
    }

    override fun startSession(sessionId: String, startedAtEpochMs: Long) {
        sessionActive = true
        lastEmittedKey = null
        val wearable = H19cWearableRuntime.state.value
        enabledLiveForSession = wearable.connected && !wearable.liveHeartRate
        if (wearable.connected && !wearable.liveHeartRate) {
            H19cWearableRuntime.setLiveHeartRate(true)
        }
    }

    override fun stopSession(endedAtEpochMs: Long) {
        sessionActive = false
        if (enabledLiveForSession && H19cWearableRuntime.state.value.connected) {
            H19cWearableRuntime.setLiveHeartRate(false)
        }
        enabledLiveForSession = false
    }
}
