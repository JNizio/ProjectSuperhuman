package com.projectsuperhuman.next

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal object BleHeartRateMeasurementParser {
    fun parse(bytes: ByteArray): Int? {
        if (bytes.size < 2) return null
        val flags = bytes[0].toInt() and 0xFF
        val bpm = if ((flags and 0x01) == 0) {
            bytes[1].toInt() and 0xFF
        } else {
            if (bytes.size < 3) return null
            (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
        }
        return bpm.takeIf { it in CARDIO_HR_MIN_BPM..CARDIO_HR_MAX_BPM }
    }
}

internal data class BleHeartRateDevice(
    val sensorId: String,
    val displayName: String,
    val rssi: Int
)

internal sealed interface BleHeartRateClientEvent {
    data object ScanStarted : BleHeartRateClientEvent
    data class DeviceFound(val device: BleHeartRateDevice) : BleHeartRateClientEvent
    data class Connecting(val displayName: String?, val reconnecting: Boolean) : BleHeartRateClientEvent
    data class Connected(val displayName: String?, val sensorId: String) : BleHeartRateClientEvent
    data class HeartRatePacket(val bytes: ByteArray, val timestampEpochMs: Long) : BleHeartRateClientEvent
    data class Reconnecting(val attempt: Int) : BleHeartRateClientEvent
    data class Disconnected(val message: String) : BleHeartRateClientEvent
    data class Error(val message: String) : BleHeartRateClientEvent
}

internal interface BleHeartRateClient {
    val events: SharedFlow<BleHeartRateClientEvent>
    val scannedDevices: StateFlow<List<BleHeartRateDevice>>

    fun requiredPermissions(): Array<String>
    fun hasPermissions(): Boolean
    suspend fun scan()
    suspend fun connectDevice(sensorId: String)
    suspend fun reconnectSaved()
    suspend fun disconnect()
}

/**
 * Android transport for the Bluetooth SIG Heart Rate Service (0x180D) and Heart Rate Measurement
 * characteristic (0x2A37). Cardio processing lives above this class so tests never need a radio.
 */
internal class AndroidBleHeartRateClient(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : BleHeartRateClient {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _events = MutableSharedFlow<BleHeartRateClientEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<BleHeartRateClientEvent> = _events.asSharedFlow()
    private val _scannedDevices = MutableStateFlow<List<BleHeartRateDevice>>(emptyList())
    override val scannedDevices: StateFlow<List<BleHeartRateDevice>> = _scannedDevices.asStateFlow()

    private val discovered = mutableMapOf<String, BluetoothDevice>()
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var explicitDisconnect = false
    private var reconnectAttempts = 0

    override fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    override fun hasPermissions(): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission")
    override suspend fun scan() {
        if (!hasPermissions()) {
            _events.emit(BleHeartRateClientEvent.Error("Bluetooth permission is required"))
            return
        }
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _events.emit(BleHeartRateClientEvent.Error("Turn Bluetooth on, then try again"))
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _events.emit(BleHeartRateClientEvent.Error("Bluetooth LE scanner is unavailable"))
            return
        }
        stopScan()
        discovered.clear()
        _scannedDevices.value = emptyList()
        _events.emit(BleHeartRateClientEvent.ScanStarted)
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val advertisesHr = result.scanRecord?.serviceUuids?.any { it.uuid == HEART_RATE_SERVICE } == true
                if (!advertisesHr) return
                val device = result.device
                val rawAddress = device.address ?: return
                val sensorId = CardioSensorIds.anonymous(rawAddress) ?: return
                discovered[sensorId] = device
                val name = runCatching { device.name }.getOrNull()
                    ?: result.scanRecord?.deviceName
                    ?: "BLE heart-rate sensor"
                val item = BleHeartRateDevice(sensorId, name, result.rssi)
                _scannedDevices.value = (_scannedDevices.value.filterNot { it.sensorId == sensorId } + item)
                    .sortedByDescending { it.rssi }
                _events.tryEmit(BleHeartRateClientEvent.DeviceFound(item))
            }

            override fun onScanFailed(errorCode: Int) {
                scanCallback = null
                _events.tryEmit(BleHeartRateClientEvent.Error("Bluetooth scan failed ($errorCode)"))
            }
        }
        scanCallback = callback
        scanner.startScan(
            emptyList(),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback
        )
        scope.launch {
            delay(SCAN_TIMEOUT_MS)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connectDevice(sensorId: String) {
        if (!hasPermissions()) {
            _events.emit(BleHeartRateClientEvent.Error("Bluetooth permission is required"))
            return
        }
        val device = discovered[sensorId]
        if (device == null) {
            _events.emit(BleHeartRateClientEvent.Error("That heart-rate sensor is no longer in the scan list"))
            return
        }
        stopScan()
        explicitDisconnect = false
        reconnectAttempts = 0
        val name = runCatching { device.name }.getOrNull()
        prefs.edit()
            .putString(PREF_ADDRESS, device.address)
            .putString(PREF_NAME, name)
            .apply()
        connectGatt(device, name, reconnecting = false)
    }

    @SuppressLint("MissingPermission")
    override suspend fun reconnectSaved() {
        if (!hasPermissions()) {
            _events.emit(BleHeartRateClientEvent.Error("Bluetooth permission is required"))
            return
        }
        val address = prefs.getString(PREF_ADDRESS, null)
        if (address.isNullOrBlank()) {
            scan()
            return
        }
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _events.emit(BleHeartRateClientEvent.Error("Turn Bluetooth on, then try again"))
            return
        }
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            _events.emit(BleHeartRateClientEvent.Error("Saved heart-rate sensor is unavailable"))
            return
        }
        explicitDisconnect = false
        val name = prefs.getString(PREF_NAME, null)
        connectGatt(device, name, reconnecting = reconnectAttempts > 0)
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        explicitDisconnect = true
        reconnectAttempts = 0
        stopScan()
        val current = gatt
        gatt = null
        runCatching { current?.disconnect() }
        runCatching { current?.close() }
        _events.emit(BleHeartRateClientEvent.Disconnected("Heart-rate sensor disconnected"))
    }

    @SuppressLint("MissingPermission")
    private fun connectGatt(device: BluetoothDevice, name: String?, reconnecting: Boolean) {
        val old = gatt
        gatt = null
        runCatching { old?.disconnect() }
        runCatching { old?.close() }
        _events.tryEmit(BleHeartRateClientEvent.Connecting(name, reconnecting))
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) {
            _events.tryEmit(BleHeartRateClientEvent.Error("Couldn’t create Bluetooth heart-rate connection"))
            scheduleReconnect()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    this@AndroidBleHeartRateClient.gatt = gatt
                    reconnectAttempts = 0
                    if (!gatt.discoverServices()) {
                        _events.tryEmit(BleHeartRateClientEvent.Error("Couldn’t discover heart-rate services"))
                        closeGatt(gatt)
                        scheduleReconnect()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (this@AndroidBleHeartRateClient.gatt === gatt) this@AndroidBleHeartRateClient.gatt = null
                    closeGatt(gatt)
                    _events.tryEmit(
                        BleHeartRateClientEvent.Disconnected(
                            if (status == BluetoothGatt.GATT_SUCCESS) "Heart-rate sensor disconnected"
                            else "Heart-rate connection dropped ($status)"
                        )
                    )
                    if (!explicitDisconnect) scheduleReconnect()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _events.tryEmit(BleHeartRateClientEvent.Error("Heart-rate service discovery failed ($status)"))
                closeGatt(gatt)
                scheduleReconnect()
                return
            }
            val characteristic = gatt.getService(HEART_RATE_SERVICE)
                ?.getCharacteristic(HEART_RATE_MEASUREMENT)
            if (characteristic == null) {
                _events.tryEmit(BleHeartRateClientEvent.Error("Device does not expose the standard Heart Rate Measurement characteristic"))
                closeGatt(gatt)
                scheduleReconnect()
                return
            }
            val notifyOk = gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CCCD)
            if (!notifyOk || descriptor == null) {
                _events.tryEmit(BleHeartRateClientEvent.Error("Couldn’t enable heart-rate notifications"))
                closeGatt(gatt)
                scheduleReconnect()
                return
            }
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (!gatt.writeDescriptor(descriptor)) {
                _events.tryEmit(BleHeartRateClientEvent.Error("Couldn’t subscribe to heart-rate notifications"))
                closeGatt(gatt)
                scheduleReconnect()
                return
            }
            val address = runCatching { gatt.device.address }.getOrNull().orEmpty()
            val sensorId = CardioSensorIds.anonymous(address).orEmpty()
            val name = runCatching { gatt.device.name }.getOrNull() ?: prefs.getString(PREF_NAME, null)
            _events.tryEmit(BleHeartRateClientEvent.Connected(name, sensorId))
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT) {
                _events.tryEmit(BleHeartRateClientEvent.HeartRatePacket(characteristic.value ?: return, clock()))
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT) {
                _events.tryEmit(BleHeartRateClientEvent.HeartRatePacket(value, clock()))
            }
        }
    }

    private fun scheduleReconnect() {
        if (explicitDisconnect || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
        reconnectAttempts += 1
        val attempt = reconnectAttempts
        scope.launch {
            _events.emit(BleHeartRateClientEvent.Reconnecting(attempt))
            delay(min(10_000L, 2_000L * attempt))
            if (!explicitDisconnect) reconnectSaved()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        val callback = scanCallback ?: return
        val scanner = appContext.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        runCatching { scanner?.stopScan(callback) }
        scanCallback = null
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(target: BluetoothGatt) {
        runCatching { target.disconnect() }
        runCatching { target.close() }
        if (gatt === target) gatt = null
    }

    companion object {
        private const val PREFS = "superhuman_cardio_ble_hr"
        private const val PREF_ADDRESS = "preferred_address"
        private const val PREF_NAME = "preferred_name"
        private const val SCAN_TIMEOUT_MS = 12_000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

internal class GenericBleHeartRateProvider(
    private val client: BleHeartRateClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : CardioSensorProvider {
    override val providerType: CardioSensorProviderType = CardioSensorProviderType.BLE_HEART_RATE

    private val _state = MutableStateFlow(
        CardioSensorState(
            providerType = providerType,
            connection = CardioSensorConnectionState.DISCONNECTED,
            message = "BLE heart-rate sensor not connected"
        )
    )
    override val state: StateFlow<CardioSensorState> = _state.asStateFlow()

    private val _samples = MutableSharedFlow<CardioHeartRateSample>(extraBufferCapacity = 128)
    override val heartRateSamples: SharedFlow<CardioHeartRateSample> = _samples.asSharedFlow()

    val scannedDevices: StateFlow<List<BleHeartRateDevice>> = client.scannedDevices
    fun requiredPermissions(): Array<String> = client.requiredPermissions()
    fun hasPermissions(): Boolean = client.hasPermissions()

    private var sessionActive = false
    private var deviceName: String? = null
    private var sensorId: String? = null

    init {
        scope.launch {
            client.events.collect { event ->
                when (event) {
                    BleHeartRateClientEvent.ScanStarted -> updateConnection(
                        CardioSensorConnectionState.SCANNING,
                        "Scanning for BLE heart-rate sensors…"
                    )
                    is BleHeartRateClientEvent.DeviceFound -> Unit
                    is BleHeartRateClientEvent.Connecting -> {
                        deviceName = event.displayName ?: deviceName
                        updateConnection(
                            if (event.reconnecting) CardioSensorConnectionState.RECONNECTING else CardioSensorConnectionState.CONNECTING,
                            if (event.reconnecting) "Reconnecting to heart-rate sensor…" else "Connecting to heart-rate sensor…"
                        )
                    }
                    is BleHeartRateClientEvent.Connected -> {
                        deviceName = event.displayName ?: deviceName
                        sensorId = event.sensorId.ifBlank { sensorId ?: "" }
                        updateConnection(CardioSensorConnectionState.CONNECTED, "BLE heart-rate sensor connected")
                    }
                    is BleHeartRateClientEvent.Reconnecting -> {
                        _state.value = _state.value.copy(
                            connection = CardioSensorConnectionState.RECONNECTING,
                            message = "Reconnecting to heart-rate sensor…",
                            reconnectAttempt = event.attempt
                        )
                    }
                    is BleHeartRateClientEvent.Disconnected ->
                        updateConnection(CardioSensorConnectionState.DISCONNECTED, event.message)
                    is BleHeartRateClientEvent.Error ->
                        updateConnection(CardioSensorConnectionState.ERROR, event.message)
                    is BleHeartRateClientEvent.HeartRatePacket -> handlePacket(event)
                }
            }
        }
    }

    override suspend fun connect() {
        client.reconnectSaved()
    }

    suspend fun scan() {
        client.scan()
    }

    suspend fun connectDevice(sensorId: String) {
        client.connectDevice(sensorId)
    }

    override suspend fun disconnect() {
        sessionActive = false
        client.disconnect()
    }

    override fun startSession(sessionId: String, startedAtEpochMs: Long) {
        sessionActive = true
    }

    override fun stopSession(endedAtEpochMs: Long) {
        sessionActive = false
    }

    private fun handlePacket(event: BleHeartRateClientEvent.HeartRatePacket) {
        val bpm = BleHeartRateMeasurementParser.parse(event.bytes) ?: return
        val provenance = provenance()
        _state.value = _state.value.copy(
            connection = CardioSensorConnectionState.CONNECTED,
            currentHeartRateBpm = bpm,
            lastSampleEpochMs = event.timestampEpochMs,
            provenance = provenance,
            message = "Heart rate · $bpm bpm"
        )
        if (sessionActive) {
            _samples.tryEmit(CardioHeartRateSample(event.timestampEpochMs, bpm, provenance))
        }
    }

    private fun updateConnection(connection: CardioSensorConnectionState, message: String) {
        _state.value = _state.value.copy(
            connection = connection,
            provenance = provenance(),
            message = message,
            reconnectAttempt = if (connection == CardioSensorConnectionState.RECONNECTING) _state.value.reconnectAttempt else 0
        ).withFreshness(clock())
    }

    private fun provenance(): CardioSensorProvenance = CardioSensorProvenance(
        providerType = providerType,
        sourceName = "bluetooth-sig-heart-rate",
        transport = CardioSensorTransport.LIVE_BLE,
        deviceName = deviceName,
        anonymousSensorId = sensorId
    )
}
