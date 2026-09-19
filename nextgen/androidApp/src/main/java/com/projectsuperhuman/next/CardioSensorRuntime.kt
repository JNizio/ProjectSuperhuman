package com.projectsuperhuman.next

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class CardioLiveSensorMetrics(
    val providerType: CardioSensorProviderType = CardioSensorProviderType.NONE,
    val connection: CardioSensorConnectionState = CardioSensorConnectionState.NO_SENSOR,
    val sourceLabel: String = "NO SENSOR",
    val currentHeartRateBpm: Int? = null,
    val averageHeartRateBpm: Int? = null,
    val minHeartRateBpm: Int? = null,
    val maxHeartRateBpm: Int? = null,
    val currentZone: Int? = null,
    val heartRateCoveragePct: Double = 0.0,
    val lastSampleAgeMs: Long? = null,
    val message: String = "Timer-only cardio"
)

/**
 * Integration seam for Cardio UI/ViewModels. It owns no workout persistence. An active workout can
 * continue when a sensor disconnects; only sensor collection changes.
 */
internal object CardioSensorRuntime {
    private const val PREFS = "superhuman_cardio_sensors"
    private const val PREF_PROVIDER = "preferred_provider"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val collector = CardioSessionHeartRateCollector()

    private val _state = MutableStateFlow(CardioSensorState())
    val state: StateFlow<CardioSensorState> = _state.asStateFlow()

    private val _liveMetrics = MutableStateFlow(CardioLiveSensorMetrics())
    val liveMetrics: StateFlow<CardioLiveSensorMetrics> = _liveMetrics.asStateFlow()

    private val _bleDevices = MutableStateFlow<List<BleHeartRateDevice>>(emptyList())
    val bleDevices: StateFlow<List<BleHeartRateDevice>> = _bleDevices.asStateFlow()

    private val _bleSensorState = MutableStateFlow(
        CardioSensorState(
            providerType = CardioSensorProviderType.BLE_HEART_RATE,
            connection = CardioSensorConnectionState.DISCONNECTED,
            message = "No BLE heart-rate sensor connected"
        )
    )
    val bleSensorState: StateFlow<CardioSensorState> = _bleSensorState.asStateFlow()

    private var appContext: Context? = null
    private var h19cProvider: H19cCardioSensorProvider? = null
    private var bleProvider: GenericBleHeartRateProvider? = null
    private var selectedProvider: CardioSensorProvider? = null
    private var selectedType: CardioSensorProviderType = CardioSensorProviderType.NONE
    private var providerStateJob: Job? = null
    private var providerSamplesJob: Job? = null
    private var freshnessJob: Job? = null
    private var bleDevicesJob: Job? = null
    private var bleStateJob: Job? = null
    private var activeSessionId: String? = null
    private var activeSessionStartedAt: Long? = null
    private var lastStoppedSummary = CardioHeartRateSummary()

    @Synchronized
    fun initialize(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        h19cProvider = H19cCardioSensorProvider(ctx)
        bleProvider = GenericBleHeartRateProvider(AndroidBleHeartRateClient(ctx))
        bleDevicesJob = scope.launch {
            bleProvider?.scannedDevices?.collect { _bleDevices.value = it }
        }
        bleStateJob = scope.launch {
            bleProvider?.state?.collect { raw ->
                _bleSensorState.value = raw.withFreshness(System.currentTimeMillis())
            }
        }
        val saved = runCatching {
            CardioSensorProviderType.valueOf(
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(PREF_PROVIDER, CardioSensorProviderType.NONE.name)
                    ?: CardioSensorProviderType.NONE.name
            )
        }.getOrDefault(CardioSensorProviderType.NONE)
        bindProvider(saved, persist = false)

        freshnessJob = scope.launch {
            while (true) {
                delay(1_000L)
                val now = System.currentTimeMillis()
                selectedProvider?.state?.value?.let { raw ->
                    _state.value = raw.withFreshness(now)
                }
                bleProvider?.state?.value?.let { raw ->
                    _bleSensorState.value = raw.withFreshness(now)
                }
                refreshLiveMetrics(now)
            }
        }
    }

    fun preferredProviderType(): CardioSensorProviderType = selectedType

    fun hasSavedBleDevice(): Boolean = bleProvider?.hasSavedDevice() == true
    fun savedBleDeviceName(): String? = bleProvider?.savedDeviceName()
    fun bleRequiredPermissions(): Array<String> = bleProvider?.requiredPermissions() ?: emptyArray()
    fun hasBlePermissions(): Boolean = bleProvider?.hasPermissions() == true

    fun requiredPermissions(): Array<String> = when (selectedType) {
        CardioSensorProviderType.H19C -> H19cWearableRuntime.requiredPermissions()
        CardioSensorProviderType.BLE_HEART_RATE -> bleProvider?.requiredPermissions() ?: emptyArray()
        else -> emptyArray()
    }

    fun hasRequiredPermissions(context: Context): Boolean = when (selectedType) {
        CardioSensorProviderType.H19C -> H19cWearableRuntime.hasPermissions(context)
        CardioSensorProviderType.BLE_HEART_RATE -> bleProvider?.hasPermissions() == true
        else -> true
    }

    suspend fun reconnectPreferred() {
        ensureInitialized()
        when (selectedType) {
            CardioSensorProviderType.H19C,
            CardioSensorProviderType.BLE_HEART_RATE -> {
                selectedProvider?.connect()
                resumeProviderCollectionIfSessionActive()
            }
            else -> Unit
        }
    }

    suspend fun selectNone() {
        ensureInitialized()
        selectedProvider?.stopSession(System.currentTimeMillis())
        bindProvider(CardioSensorProviderType.NONE, persist = true)
    }

    suspend fun selectH19c(connect: Boolean = true) {
        ensureInitialized()
        selectedProvider?.stopSession(System.currentTimeMillis())
        bindProvider(CardioSensorProviderType.H19C, persist = true)
        if (connect) selectedProvider?.connect()
    }

    suspend fun selectBle(connectPreferred: Boolean = false) {
        ensureInitialized()
        selectedProvider?.stopSession(System.currentTimeMillis())
        bindProvider(CardioSensorProviderType.BLE_HEART_RATE, persist = true)
        if (connectPreferred) selectedProvider?.connect()
    }

    suspend fun scanBle() {
        ensureInitialized()
        if (selectedType != CardioSensorProviderType.BLE_HEART_RATE) {
            bindProvider(CardioSensorProviderType.BLE_HEART_RATE, persist = true)
        }
        bleProvider?.scan()
    }

    suspend fun connectBle(sensorId: String) {
        ensureInitialized()
        if (selectedType != CardioSensorProviderType.BLE_HEART_RATE) {
            bindProvider(CardioSensorProviderType.BLE_HEART_RATE, persist = true)
        }
        bleProvider?.connectDevice(sensorId)
        resumeProviderCollectionIfSessionActive()
    }

    suspend fun disconnectSelected() {
        ensureInitialized()
        selectedProvider?.disconnect()
        refreshLiveMetrics(System.currentTimeMillis())
    }

    suspend fun reconnectBleDevice() {
        ensureInitialized()
        bleProvider?.connect()
    }

    suspend fun disconnectBle() {
        ensureInitialized()
        bleProvider?.disconnect()
        if (selectedType == CardioSensorProviderType.BLE_HEART_RATE) {
            _state.value = bleProvider?.state?.value ?: CardioSensorState()
        }
        refreshLiveMetrics(System.currentTimeMillis())
    }

    suspend fun forgetBleDevice() {
        ensureInitialized()
        bleProvider?.forget()
        if (selectedType == CardioSensorProviderType.BLE_HEART_RATE) {
            bindProvider(CardioSensorProviderType.NONE, persist = true)
        }
        _bleSensorState.value = CardioSensorState(
            providerType = CardioSensorProviderType.BLE_HEART_RATE,
            connection = CardioSensorConnectionState.DISCONNECTED,
            message = "No saved BLE heart-rate sensor"
        )
    }

    fun startSession(sessionId: String, startedAtEpochMs: Long) {
        ensureInitialized()
        activeSessionId = sessionId
        activeSessionStartedAt = startedAtEpochMs
        collector.start(sessionId, startedAtEpochMs)
        lastStoppedSummary = CardioHeartRateSummary()
        selectedProvider?.startSession(sessionId, startedAtEpochMs)
        refreshLiveMetrics(startedAtEpochMs)
    }

    fun pauseSession(atEpochMs: Long = System.currentTimeMillis()) {
        collector.pause(atEpochMs)
        refreshLiveMetrics(atEpochMs)
    }

    fun resumeSession(atEpochMs: Long = System.currentTimeMillis()) {
        collector.resume(atEpochMs)
        refreshLiveMetrics(atEpochMs)
    }

    fun stopSession(endedAtEpochMs: Long = System.currentTimeMillis()): CardioHeartRateSummary {
        if (activeSessionId == null) return lastStoppedSummary
        selectedProvider?.stopSession(endedAtEpochMs)
        lastStoppedSummary = collector.stop(endedAtEpochMs)
        activeSessionId = null
        activeSessionStartedAt = null
        refreshLiveMetrics(endedAtEpochMs, lastStoppedSummary)
        return lastStoppedSummary
    }

    fun snapshot(nowEpochMs: Long = System.currentTimeMillis()): CardioHeartRateSummary =
        if (activeSessionId != null) collector.snapshot(nowEpochMs) else lastStoppedSummary

    fun setZoneScheme(zoneScheme: CardioHrZoneScheme) {
        collector.setZoneScheme(zoneScheme)
        refreshLiveMetrics(System.currentTimeMillis())
    }

    private fun bindProvider(type: CardioSensorProviderType, persist: Boolean) {
        providerStateJob?.cancel()
        providerSamplesJob?.cancel()

        selectedType = when (type) {
            CardioSensorProviderType.HEALTH_CONNECT -> CardioSensorProviderType.NONE
            else -> type
        }
        selectedProvider = when (selectedType) {
            CardioSensorProviderType.H19C -> h19cProvider
            CardioSensorProviderType.BLE_HEART_RATE -> bleProvider
            else -> null
        }

        if (persist) {
            appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
                ?.putString(PREF_PROVIDER, selectedType.name)
                ?.apply()
        }

        val provider = selectedProvider
        if (provider == null) {
            _state.value = CardioSensorState()
            _liveMetrics.value = CardioLiveSensorMetrics()
            return
        }

        providerStateJob = scope.launch {
            provider.state.collect { sensorState ->
                _state.value = sensorState.withFreshness(System.currentTimeMillis())
                refreshLiveMetrics(System.currentTimeMillis())
            }
        }
        providerSamplesJob = scope.launch {
            provider.heartRateSamples.collect { sample ->
                collector.accept(sample)
                refreshLiveMetrics(sample.timestampEpochMs)
            }
        }

        activeSessionId?.let { id ->
            provider.startSession(id, activeSessionStartedAt ?: System.currentTimeMillis())
        }
    }

    private fun resumeProviderCollectionIfSessionActive() {
        val id = activeSessionId ?: return
        selectedProvider?.startSession(id, activeSessionStartedAt ?: System.currentTimeMillis())
    }

    private fun refreshLiveMetrics(
        nowEpochMs: Long,
        forcedSummary: CardioHeartRateSummary? = null
    ) {
        val sensor = _state.value.withFreshness(nowEpochMs)
        val summary = forcedSummary ?: if (activeSessionId != null) collector.snapshot(nowEpochMs) else lastStoppedSummary
        val fresh = sensor.freshHeartRate(nowEpochMs)
        val sourceLabel = when (sensor.providerType) {
            CardioSensorProviderType.H19C -> "H19C"
            CardioSensorProviderType.BLE_HEART_RATE -> sensor.provenance?.deviceName ?: "BLE HR"
            CardioSensorProviderType.HEALTH_CONNECT -> "HEALTH CONNECT"
            CardioSensorProviderType.NONE -> "NO SENSOR"
        }
        _liveMetrics.value = CardioLiveSensorMetrics(
            providerType = sensor.providerType,
            connection = sensor.connection,
            sourceLabel = sourceLabel.uppercase(),
            currentHeartRateBpm = fresh,
            averageHeartRateBpm = summary.averageBpm,
            minHeartRateBpm = summary.minBpm,
            maxHeartRateBpm = summary.maxBpm,
            currentZone = if (fresh != null) summary.currentZone else null,
            heartRateCoveragePct = summary.heartRateCoveragePct,
            lastSampleAgeMs = sensor.sampleAgeMs(nowEpochMs),
            message = sensor.message
        )
    }

    private fun ensureInitialized() {
        check(appContext != null) { "CardioSensorRuntime.initialize(context) must be called first" }
    }
}
