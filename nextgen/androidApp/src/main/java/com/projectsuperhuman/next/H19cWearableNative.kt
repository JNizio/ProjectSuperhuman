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
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class H19cConnectionPhase {
    IDLE, SCANNING, CONNECTING, DISCOVERING, READY, ERROR
}

internal data class H19cWearableState(
    val phase: H19cConnectionPhase = H19cConnectionPhase.IDLE,
    val status: String = "Ready to connect",
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val firmware: String? = null,
    val batteryPercent: Int? = null,
    val heartRateBpm: Int? = null,
    val lastHeartRateEpochMs: Long? = null,
    val bloodOxygenPercent: Int? = null,
    val steps: Int? = null,
    val distanceMeters: Int? = null,
    val activeCaloriesKcal: Int? = null,
    val liveHeartRate: Boolean = false,
    val protocolDetected: Boolean = false,
    val lastSyncEpochMs: Long? = null
) {
    val connected: Boolean get() = phase == H19cConnectionPhase.READY
}

internal object H19cWearableRuntime {
    const val SOURCE = "h19c-direct-ble"

    private const val PREFS = "project_superhuman_h19c"
    private const val PREF_ADDRESS = "saved_address"
    private const val PREF_NAME = "saved_name"
    private const val SCAN_TIMEOUT_MS = 12_000L
    private const val LIVE_HR_DELAY_MS = 3_000L

    private val DAFIT_SERVICE = UUID.fromString("0000feea-0000-1000-8000-00805f9b34fb")
    private val STEPS_CHAR = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")
    private val DATA_OUT_CHAR = UUID.fromString("0000fee2-0000-1000-8000-00805f9b34fb")
    private val DATA_IN_CHAR = UUID.fromString("0000fee3-0000-1000-8000-00805f9b34fb")
    private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    private val DEVICE_INFO_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    private val HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val MANUFACTURER_NAME = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
    private val MODEL_NUMBER = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    private val FIRMWARE_REVISION = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")

    private const val CMD_SYNC_SLEEP = 50
    private const val CMD_SYNC_PAST_SLEEP_AND_STEP = 51
    private const val CMD_MEASURE_SPO2 = 107
    private const val CMD_MEASURE_HR = 109

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(H19cWearableState())
    val state: StateFlow<H19cWearableState> = _state.asStateFlow()

    private var appContext: Context? = null
    private var scannerCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var dataOut: BluetoothGattCharacteristic? = null
    private var dataIn: BluetoothGattCharacteristic? = null
    private var stepsCharacteristic: BluetoothGattCharacteristic? = null
    private var packetAssembler = PacketAssembler()
    private val opQueue = ArrayDeque<GattOp>()
    private var operationInFlight = false
    private val sourceCounter = AtomicLong(0L)

    private sealed interface GattOp {
        data class Read(val characteristic: BluetoothGattCharacteristic) : GattOp
        data class Notify(val characteristic: BluetoothGattCharacteristic, val enabled: Boolean = true) : GattOp
        data class Write(val characteristic: BluetoothGattCharacteristic, val bytes: ByteArray) : GattOp
    }

    fun initialize(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (_state.value.deviceAddress == null) {
            _state.value = _state.value.copy(
                deviceAddress = prefs.getString(PREF_ADDRESS, null),
                deviceName = prefs.getString(PREF_NAME, null),
                status = if (prefs.contains(PREF_ADDRESS)) "Saved H19C ready to reconnect" else "Ready to connect"
            )
        }
    }

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasPermissions(context: Context): Boolean =
        requiredPermissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    fun scanAndConnect(context: Context) {
        initialize(context)
        if (!hasPermissions(context)) {
            fail("Bluetooth permission is required")
            return
        }

        disconnectGatt(updateState = false)
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            fail("Turn Bluetooth on, then try again")
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            fail("Bluetooth LE scanner is unavailable")
            return
        }

        _state.value = _state.value.copy(phase = H19cConnectionPhase.SCANNING, status = "Scanning for H19C / FEEA wearable…")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = runCatching { device.name }.getOrNull() ?: result.scanRecord?.deviceName
                val advertisesDaFit = result.scanRecord?.serviceUuids?.any { it.uuid == DAFIT_SERVICE } == true
                val nameLooksRight = name?.contains("H19", ignoreCase = true) == true
                if (!advertisesDaFit && !nameLooksRight) return
                stopScan()
                connect(context.applicationContext, device, name)
            }

            override fun onScanFailed(errorCode: Int) {
                stopScan()
                fail("Bluetooth scan failed ($errorCode)")
            }
        }
        scannerCallback = callback
        scanner.startScan(callback)
        mainHandler.postDelayed({
            if (_state.value.phase == H19cConnectionPhase.SCANNING) {
                stopScan()
                fail("No H19C found. Keep it nearby and close Da Fit if it is connected.")
            }
        }, SCAN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    fun reconnectSaved(context: Context) {
        initialize(context)
        if (!hasPermissions(context)) {
            fail("Bluetooth permission is required")
            return
        }
        val address = _state.value.deviceAddress
            ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_ADDRESS, null)
        if (address.isNullOrBlank()) {
            scanAndConnect(context)
            return
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            fail("Turn Bluetooth on, then try again")
            return
        }
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            scanAndConnect(context)
            return
        }
        disconnectGatt(updateState = false)
        connect(context.applicationContext, device, _state.value.deviceName)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        setLiveHeartRate(false)
        disconnectGatt(updateState = true)
    }

    fun refreshActivity() {
        if (!requireReady()) return
        stepsCharacteristic?.let { enqueue(GattOp.Read(it)) }
        sendCommand(CMD_SYNC_SLEEP)
        sendCommand(CMD_SYNC_PAST_SLEEP_AND_STEP, byteArrayOf(1))
        sendCommand(CMD_SYNC_PAST_SLEEP_AND_STEP, byteArrayOf(2))
        sendCommand(CMD_SYNC_PAST_SLEEP_AND_STEP, byteArrayOf(3))
        sendCommand(CMD_SYNC_PAST_SLEEP_AND_STEP, byteArrayOf(4))
        _state.value = _state.value.copy(status = "Syncing activity and sleep…")
    }

    fun measureHeartRate() {
        if (!requireReady()) return
        sendCommand(CMD_MEASURE_HR, byteArrayOf(0))
        _state.value = _state.value.copy(status = "Measuring heart rate…")
    }

    fun measureBloodOxygen() {
        if (!requireReady()) return
        sendCommand(CMD_MEASURE_SPO2, byteArrayOf(0))
        _state.value = _state.value.copy(status = "Measuring blood oxygen…")
    }

    fun setLiveHeartRate(enabled: Boolean) {
        val wasEnabled = _state.value.liveHeartRate
        _state.value = _state.value.copy(
            liveHeartRate = enabled,
            status = when {
                enabled && _state.value.connected -> "Live heart-rate sampling enabled"
                !enabled && wasEnabled -> "Live heart-rate sampling stopped"
                else -> _state.value.status
            }
        )
        mainHandler.removeCallbacks(liveHeartRateRunnable)
        if (enabled && _state.value.connected) measureHeartRate()
        if (!enabled && _state.value.connected) sendCommand(CMD_MEASURE_HR, byteArrayOf(0xFF.toByte()))
    }

    private val liveHeartRateRunnable = Runnable {
        if (_state.value.liveHeartRate && _state.value.connected) measureHeartRate()
    }

    @SuppressLint("MissingPermission")
    private fun connect(context: Context, device: BluetoothDevice, discoveredName: String?) {
        _state.value = _state.value.copy(
            phase = H19cConnectionPhase.CONNECTING,
            status = "Connecting to ${discoveredName ?: "H19C"}…",
            deviceName = discoveredName ?: _state.value.deviceName,
            deviceAddress = device.address
        )
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) fail("Couldn’t create Bluetooth connection")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    this@H19cWearableRuntime.gatt = gatt
                    _state.value = _state.value.copy(
                        phase = H19cConnectionPhase.DISCOVERING,
                        status = "Connected · discovering H19C services…"
                    )
                    if (!gatt.discoverServices()) failAndClose("Couldn’t discover wearable services")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    clearGattQueue()
                    if (_state.value.phase != H19cConnectionPhase.ERROR && _state.value.phase != H19cConnectionPhase.IDLE) {
                        _state.value = _state.value.copy(
                            phase = H19cConnectionPhase.IDLE,
                            status = if (status == BluetoothGatt.GATT_SUCCESS) "H19C disconnected" else "H19C connection dropped ($status)",
                            liveHeartRate = false
                        )
                    }
                    runCatching { gatt.close() }
                    if (this@H19cWearableRuntime.gatt === gatt) this@H19cWearableRuntime.gatt = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndClose("Wearable service discovery failed ($status)")
                return
            }
            val service = gatt.getService(DAFIT_SERVICE)
            val out = service?.getCharacteristic(DATA_OUT_CHAR)
            val input = service?.getCharacteristic(DATA_IN_CHAR)
            val steps = service?.getCharacteristic(STEPS_CHAR)
            if (service == null || out == null || input == null) {
                failAndClose("H19C found, but its BLE protocol differs from the FEEA Da Fit family")
                return
            }
            dataOut = out
            dataIn = input
            stepsCharacteristic = steps
            packetAssembler = PacketAssembler()
            clearGattQueue()

            enqueue(GattOp.Notify(input, true))
            steps?.let { enqueue(GattOp.Notify(it, true)) }

            gatt.getService(DEVICE_INFO_SERVICE)?.let { info ->
                info.getCharacteristic(MANUFACTURER_NAME)?.let { enqueue(GattOp.Read(it)) }
                info.getCharacteristic(MODEL_NUMBER)?.let { enqueue(GattOp.Read(it)) }
                info.getCharacteristic(FIRMWARE_REVISION)?.let { enqueue(GattOp.Read(it)) }
            }
            gatt.getService(HEART_RATE_SERVICE)?.getCharacteristic(HEART_RATE_MEASUREMENT)?.let { enqueue(GattOp.Notify(it, true)) }
            gatt.getService(BATTERY_SERVICE)?.getCharacteristic(BATTERY_LEVEL)?.let { enqueue(GattOp.Read(it)) }
            steps?.let { enqueue(GattOp.Read(it)) }

            markReady()
            mainHandler.postDelayed({ if (_state.value.connected) refreshActivity() }, 700L)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleCharacteristic(characteristic.uuid, characteristic.value ?: return)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristic(characteristic.uuid, characteristic.value ?: byteArrayOf())
            }
            completeOperation()
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = _state.value.copy(status = "Wearable command write failed ($status)")
            }
            completeOperation()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS && descriptor.characteristic.uuid == DATA_IN_CHAR) {
                failAndClose("Couldn’t enable H19C data notifications ($status)")
                return
            }
            completeOperation()
        }
    }

    private fun markReady() {
        val context = appContext ?: return
        val address = _state.value.deviceAddress ?: return
        val name = _state.value.deviceName ?: "H19C"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREF_ADDRESS, address)
            .putString(PREF_NAME, name)
            .apply()
        _state.value = _state.value.copy(
            phase = H19cConnectionPhase.READY,
            status = "H19C direct BLE connected",
            protocolDetected = true,
            deviceName = name
        )
    }

    private fun handleCharacteristic(uuid: UUID, bytes: ByteArray) {
        when (uuid) {
            STEPS_CHAR -> if (bytes.size >= 9) handleSteps(bytes, daysAgo = 0)
            DATA_IN_CHAR -> packetAssembler.add(bytes)?.let { packet -> handlePacket(packet.command, packet.payload) }
            HEART_RATE_MEASUREMENT -> handleStandardHeartRate(bytes)
            BATTERY_LEVEL -> if (bytes.isNotEmpty()) {
                _state.value = _state.value.copy(batteryPercent = bytes[0].toInt() and 0xFF)
            }
            MANUFACTURER_NAME -> updateDeviceInfo(manufacturer = decodeUtf8(bytes))
            MODEL_NUMBER -> updateDeviceInfo(model = decodeUtf8(bytes))
            FIRMWARE_REVISION -> updateDeviceInfo(firmware = decodeUtf8(bytes))
        }
    }

    private fun handleStandardHeartRate(bytes: ByteArray) {
        if (bytes.size < 2) return
        val flags = bytes[0].toInt() and 0xFF
        val bpm = if ((flags and 0x01) == 0) {
            bytes[1].toInt() and 0xFF
        } else if (bytes.size >= 3) {
            (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
        } else return
        if (bpm !in 20..260) return
        val now = System.currentTimeMillis()
        _state.value = _state.value.copy(
            heartRateBpm = bpm,
            lastHeartRateEpochMs = now,
            lastSyncEpochMs = now,
            status = "Heart rate · $bpm bpm"
        )
        persistPoint(HealthDomain.EXERCISE, "heart_rate_bpm", bpm.toDouble(), "bpm", now, "heart-rate-standard")
    }

    private fun updateDeviceInfo(manufacturer: String? = null, model: String? = null, firmware: String? = null) {
        _state.value = _state.value.copy(
            manufacturer = manufacturer ?: _state.value.manufacturer,
            model = model ?: _state.value.model,
            firmware = firmware ?: _state.value.firmware,
            protocolDetected = _state.value.protocolDetected || manufacturer?.startsWith("MOYOUNG", ignoreCase = true) == true
        )
    }

    private fun handlePacket(command: Int, payload: ByteArray) {
        when (command) {
            CMD_MEASURE_HR -> {
                val bpm = payload.firstOrNull()?.toInt()?.and(0xFF) ?: return
                if (bpm in 20..260) {
                    val now = System.currentTimeMillis()
                    _state.value = _state.value.copy(
                        heartRateBpm = bpm,
                        lastHeartRateEpochMs = now,
                        lastSyncEpochMs = now,
                        status = if (_state.value.liveHeartRate) "Live heart rate · $bpm bpm" else "Heart rate · $bpm bpm"
                    )
                    persistPoint(HealthDomain.EXERCISE, "heart_rate_bpm", bpm.toDouble(), "bpm", now, "heart-rate")
                    if (_state.value.liveHeartRate) {
                        mainHandler.removeCallbacks(liveHeartRateRunnable)
                        mainHandler.postDelayed(liveHeartRateRunnable, LIVE_HR_DELAY_MS)
                    }
                }
            }
            CMD_MEASURE_SPO2 -> {
                val percent = payload.firstOrNull()?.toInt()?.and(0xFF) ?: return
                if (percent in 40..100) {
                    val now = System.currentTimeMillis()
                    _state.value = _state.value.copy(
                        bloodOxygenPercent = percent,
                        lastSyncEpochMs = now,
                        status = "Blood oxygen · $percent%"
                    )
                    persistPoint(HealthDomain.BODY, "blood_oxygen_percent", percent.toDouble(), "%", now, "spo2")
                    persistDailyOxygenSummary(percent.toDouble(), now)
                }
            }
            CMD_SYNC_SLEEP -> persistSleep(daysAgo = 0, payload)
            CMD_SYNC_PAST_SLEEP_AND_STEP -> {
                if (payload.isEmpty()) return
                val kind = payload[0].toInt() and 0xFF
                val data = payload.copyOfRange(1, payload.size)
                when (kind) {
                    1 -> handleSteps(data, daysAgo = 2)
                    2 -> handleSteps(data, daysAgo = 1)
                    3 -> persistSleep(daysAgo = 2, data)
                    4 -> persistSleep(daysAgo = 1, data)
                }
            }
        }
    }

    private fun handleSteps(data: ByteArray, daysAgo: Int) {
        if (data.size < 9) return
        val steps = uint24Le(data, 0)
        val distance = uint24Le(data, 3)
        val calories = uint24Le(data, 6)
        if (steps !in 0..200_000) return
        if (daysAgo == 0) {
            _state.value = _state.value.copy(
                steps = steps,
                distanceMeters = distance,
                activeCaloriesKcal = calories,
                lastSyncEpochMs = System.currentTimeMillis(),
                status = "Activity synced · $steps steps"
            )
        }
        persistActivitySummary(daysAgo, steps, distance, calories)
    }

    private fun persistActivitySummary(daysAgo: Int, steps: Int, distance: Int, calories: Int) {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone).minusDays(daysAgo.toLong())
        val timestamp = if (daysAgo == 0) {
            System.currentTimeMillis()
        } else {
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        }
        ioScope.launch {
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = timestamp
            val metrics = setOf("steps", "calories_burned_active_kcal", "wearable_distance_m")
            val old = buildList {
                metrics.forEach { metric ->
                    addAll(
                        NativeDataHub.between(HealthDomain.EXERCISE, metric, start, end)
                            .filter { it.source == SOURCE && it.metadata["summaryDate"] == date.toString() }
                    )
                }
            }
            if (old.isNotEmpty()) NativeDataHub.deleteValues(old)
            val common = deviceMetadata() + mapOf(
                "summaryDate" to date.toString(),
                "summaryType" to "daily-total",
                "sourceLastModifiedMs" to System.currentTimeMillis().toString()
            )
            NativeDataHub.saveValues(
                listOf(
                    HealthValue(HealthDomain.EXERCISE, "steps", steps.toDouble(), "count", timestamp, SOURCE, common + ("sourceRecordId" to "h19c:steps:$date")),
                    HealthValue(HealthDomain.EXERCISE, "calories_burned_active_kcal", calories.toDouble(), "kcal", timestamp, SOURCE, common + ("sourceRecordId" to "h19c:active-calories:$date")),
                    HealthValue(HealthDomain.EXERCISE, "wearable_distance_m", distance.toDouble(), "m", timestamp, SOURCE, common + ("sourceRecordId" to "h19c:distance:$date"))
                )
            )
        }
    }

    private fun persistPoint(
        domain: HealthDomain,
        metric: String,
        value: Double,
        unit: String,
        timestamp: Long,
        kind: String
    ) {
        ioScope.launch {
            NativeDataHub.saveValues(
                listOf(
                    HealthValue(
                        domain = domain,
                        metric = metric,
                        value = value,
                        unit = unit,
                        timestampEpochMs = timestamp,
                        source = SOURCE,
                        metadata = deviceMetadata() + mapOf(
                            "summaryType" to "latest-reading",
                            "sourceRecordId" to "h19c:$kind:$timestamp:${sourceCounter.incrementAndGet()}"
                        )
                    )
                )
            )
        }
    }

    private fun persistDailyOxygenSummary(value: Double, timestamp: Long) {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(timestamp), zone)
        ioScope.launch {
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
            val existingSamples = NativeDataHub.between(HealthDomain.BODY, "blood_oxygen_percent", start, end)
                .filter { it.source == SOURCE }
            val samples = existingSamples.map { it.value } +
                if (existingSamples.any { it.timestampEpochMs == timestamp }) emptyList() else listOf(value)
            if (samples.isEmpty()) return@launch
            val definitions = listOf(
                Triple("blood_oxygen_avg_percent", samples.average(), "avg"),
                Triple("blood_oxygen_min_percent", samples.minOrNull() ?: value, "min"),
                Triple("blood_oxygen_max_percent", samples.maxOrNull() ?: value, "max")
            )
            val metricNames = definitions.map { it.first }.toSet()
            val old = metricNames.flatMap { metric ->
                NativeDataHub.between(HealthDomain.BODY, metric, start, end)
                    .filter { it.source == SOURCE && it.metadata["summaryDate"] == date.toString() }
            }
            if (old.isNotEmpty()) NativeDataHub.deleteValues(old)
            NativeDataHub.saveValues(definitions.map { (metric, number, suffix) ->
                HealthValue(
                    HealthDomain.BODY,
                    metric,
                    number,
                    "%",
                    timestamp,
                    SOURCE,
                    deviceMetadata() + mapOf(
                        "summaryDate" to date.toString(),
                        "summaryType" to "daily-summary",
                        "sourceRecordId" to "h19c:oxygen-$suffix:$date"
                    )
                )
            })
        }
    }

    private data class SleepTransition(val type: Int, val epochMs: Long)

    private fun persistSleep(daysAgo: Int, data: ByteArray) {
        if (data.size < 6 || data.size % 3 != 0) return
        val zone = ZoneId.systemDefault()
        val targetEndDate = LocalDate.now(zone).minusDays(daysAgo.toLong())
        val raw = data.asList().chunked(3).mapNotNull { triple ->
            if (triple.size != 3) return@mapNotNull null
            val type = triple[0].toInt() and 0xFF
            val hour = triple[1].toInt() and 0xFF
            val minute = triple[2].toInt() and 0xFF
            if (type !in 0..2 || hour !in 0..23 || minute !in 0..59) null else Triple(type, hour, minute)
        }
        if (raw.size < 2) return

        var date = if (raw.first().second >= 12) targetEndDate.minusDays(1) else targetEndDate
        var previousMinuteOfDay = raw.first().second * 60 + raw.first().third
        val transitions = raw.mapIndexed { index, triple ->
            val minuteOfDay = triple.second * 60 + triple.third
            if (index > 0 && minuteOfDay + 120 < previousMinuteOfDay) date = date.plusDays(1)
            previousMinuteOfDay = minuteOfDay
            val epoch = date.atTime(triple.second, triple.third).atZone(zone).toInstant().toEpochMilli()
            SleepTransition(triple.first, epoch)
        }.sortedBy { it.epochMs }

        var lightMinutes = 0L
        var deepMinutes = 0L
        var sleepStart: Long? = null
        var sleepEnd: Long? = null
        for (i in 0 until transitions.lastIndex) {
            val current = transitions[i]
            val next = transitions[i + 1]
            val duration = ((next.epochMs - current.epochMs) / 60_000L).coerceIn(0L, 720L)
            if (current.type == 1) {
                lightMinutes += duration
                if (sleepStart == null) sleepStart = current.epochMs
            } else if (current.type == 2) {
                deepMinutes += duration
                if (sleepStart == null) sleepStart = current.epochMs
            }
            if (current.type in 1..2 && next.type == 0) sleepEnd = next.epochMs
        }
        val total = lightMinutes + deepMinutes
        val start = sleepStart ?: return
        val end = sleepEnd ?: transitions.last().epochMs
        if (total <= 0 || total > 24L * 60L || end <= start) return

        ioScope.launch {
            val dayStart = targetEndDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = targetEndDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
            val metrics = listOf(
                "sleep_total_minutes",
                "sleep_light_minutes",
                "sleep_deep_minutes",
                "sleep_start_epoch_ms",
                "sleep_end_epoch_ms"
            )
            val old = metrics.flatMap { metric ->
                NativeDataHub.between(HealthDomain.SLEEP, metric, dayStart, dayEnd)
                    .filter { it.source == SOURCE && it.metadata["summaryDate"] == targetEndDate.toString() }
            }
            if (old.isNotEmpty()) NativeDataHub.deleteValues(old)
            val meta = deviceMetadata() + mapOf(
                "summaryDate" to targetEndDate.toString(),
                "summaryType" to "sleep-summary",
                "stageSource" to "h19c-light-deep-only"
            )
            val ts = end
            NativeDataHub.saveValues(
                listOf(
                    HealthValue(HealthDomain.SLEEP, "sleep_total_minutes", total.toDouble(), "min", ts, SOURCE, meta + ("sourceRecordId" to "h19c:sleep-total:$targetEndDate")),
                    HealthValue(HealthDomain.SLEEP, "sleep_light_minutes", lightMinutes.toDouble(), "min", ts, SOURCE, meta + ("sourceRecordId" to "h19c:sleep-light:$targetEndDate")),
                    HealthValue(HealthDomain.SLEEP, "sleep_deep_minutes", deepMinutes.toDouble(), "min", ts, SOURCE, meta + ("sourceRecordId" to "h19c:sleep-deep:$targetEndDate")),
                    HealthValue(HealthDomain.SLEEP, "sleep_start_epoch_ms", start.toDouble(), "ms", ts, SOURCE, meta + ("sourceRecordId" to "h19c:sleep-start:$targetEndDate")),
                    HealthValue(HealthDomain.SLEEP, "sleep_end_epoch_ms", end.toDouble(), "ms", ts, SOURCE, meta + ("sourceRecordId" to "h19c:sleep-end:$targetEndDate"))
                )
            )
            if (daysAgo == 0) {
                _state.value = _state.value.copy(
                    lastSyncEpochMs = System.currentTimeMillis(),
                    status = "Sleep synced · ${total / 60}h ${total % 60}m"
                )
            }
        }
    }

    private fun deviceMetadata(): Map<String, String> = buildMap {
        put("transport", "ble-direct")
        put("protocolService", "FEEA")
        _state.value.deviceAddress?.let { put("deviceAddress", it) }
        _state.value.deviceName?.let { put("deviceName", it) }
        _state.value.manufacturer?.let { put("manufacturer", it) }
        _state.value.model?.let { put("model", it) }
        _state.value.firmware?.let { put("firmware", it) }
    }

    private fun sendCommand(command: Int, payload: ByteArray = byteArrayOf()) {
        val characteristic = dataOut ?: return
        buildPacket(command, payload).toList().chunked(20).forEach { chunk ->
            val bytes = ByteArray(20)
            chunk.forEachIndexed { index, byte -> bytes[index] = byte }
            enqueue(GattOp.Write(characteristic, bytes))
        }
    }

    private fun buildPacket(command: Int, payload: ByteArray): ByteArray {
        val length = payload.size + 5
        return ByteArray(length).also { packet ->
            packet[0] = 0xFE.toByte()
            packet[1] = 0xEA.toByte()
            packet[2] = 16
            packet[3] = length.toByte()
            packet[4] = command.toByte()
            payload.copyInto(packet, destinationOffset = 5)
        }
    }

    private fun enqueue(op: GattOp) {
        synchronized(opQueue) { opQueue.addLast(op) }
        mainHandler.post(::processNextOperation)
    }

    @SuppressLint("MissingPermission")
    private fun processNextOperation() {
        if (operationInFlight) return
        val currentGatt = gatt ?: return
        val op = synchronized(opQueue) { if (opQueue.isEmpty()) null else opQueue.removeFirst() } ?: return
        operationInFlight = true
        val started = when (op) {
            is GattOp.Read -> currentGatt.readCharacteristic(op.characteristic)
            is GattOp.Write -> {
                op.characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                op.characteristic.value = op.bytes
                currentGatt.writeCharacteristic(op.characteristic)
            }
            is GattOp.Notify -> {
                val local = currentGatt.setCharacteristicNotification(op.characteristic, op.enabled)
                val descriptor = op.characteristic.getDescriptor(CCCD)
                if (!local || descriptor == null) {
                    false
                } else {
                    descriptor.value = if (op.enabled) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    currentGatt.writeDescriptor(descriptor)
                }
            }
        }
        if (!started) {
            operationInFlight = false
            if (op is GattOp.Notify && op.characteristic.uuid == DATA_IN_CHAR) {
                failAndClose("Couldn’t enable H19C notifications")
            } else {
                mainHandler.post(::processNextOperation)
            }
        }
    }

    private fun completeOperation() {
        operationInFlight = false
        mainHandler.post(::processNextOperation)
    }

    private fun clearGattQueue() {
        synchronized(opQueue) { opQueue.clear() }
        operationInFlight = false
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        val context = appContext ?: return
        val callback = scannerCallback ?: return
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        runCatching { scanner?.stopScan(callback) }
        scannerCallback = null
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGatt(updateState: Boolean) {
        clearGattQueue()
        dataOut = null
        dataIn = null
        stepsCharacteristic = null
        packetAssembler = PacketAssembler()
        val current = gatt
        gatt = null
        if (current != null) {
            runCatching { current.disconnect() }
            mainHandler.postDelayed({ runCatching { current.close() } }, 300L)
        }
        if (updateState) {
            _state.value = _state.value.copy(
                phase = H19cConnectionPhase.IDLE,
                status = "H19C disconnected",
                liveHeartRate = false
            )
        }
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(phase = H19cConnectionPhase.ERROR, status = message, liveHeartRate = false)
    }

    @SuppressLint("MissingPermission")
    private fun failAndClose(message: String) {
        fail(message)
        clearGattQueue()
        runCatching { gatt?.disconnect() }
    }

    private fun requireReady(): Boolean {
        if (!_state.value.connected || gatt == null || dataOut == null) {
            _state.value = _state.value.copy(status = "Connect the H19C first")
            return false
        }
        return true
    }

    private fun decodeUtf8(bytes: ByteArray): String? =
        runCatching { String(bytes, StandardCharsets.UTF_8).trim('\u0000', ' ', '\n', '\r', '\t') }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun uint24Le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16)

    private data class ParsedPacket(val command: Int, val payload: ByteArray)

    private class PacketAssembler {
        private var expectedLength = 0
        private var buffer = ByteArray(0)
        private var position = 0

        fun add(fragment: ByteArray): ParsedPacket? {
            if (fragment.isEmpty()) return null
            if (position == 0) {
                if (fragment.size < 5 || fragment[0] != 0xFE.toByte() || fragment[1] != 0xEA.toByte()) return null
                val high = fragment[2].toInt() and 0xFF
                val lengthHigh = if (high == 16) 0 else (high - 32).coerceAtLeast(0)
                expectedLength = (lengthHigh shl 8) or (fragment[3].toInt() and 0xFF)
                if (expectedLength !in 5..8192) {
                    reset()
                    return null
                }
                buffer = ByteArray(expectedLength)
            }
            val copy = minOf(fragment.size, expectedLength - position)
            if (copy > 0) fragment.copyInto(buffer, position, 0, copy)
            position += copy
            if (position < expectedLength) return null
            val command = buffer[4].toInt() and 0xFF
            val payload = buffer.copyOfRange(5, expectedLength)
            reset()
            return ParsedPacket(command, payload)
        }

        private fun reset() {
            expectedLength = 0
            buffer = ByteArray(0)
            position = 0
        }
    }
}

@Composable
internal fun H19cWearableCard() {
    val context = LocalContext.current
    val state by H19cWearableRuntime.state.collectAsState()
    val permissions = remember { H19cWearableRuntime.requiredPermissions() }

    H19cWearableRuntime.initialize(context)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it } && state.deviceAddress != null) {
            H19cWearableRuntime.reconnectSaved(context)
        }
    }

    fun reconnectSaved() {
        if (state.deviceAddress == null) return
        if (H19cWearableRuntime.hasPermissions(context)) {
            H19cWearableRuntime.reconnectSaved(context)
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    val accent = Color(0xFF0D6CB4)
    val green = Color(0xFF168A78)
    val muted = Color(0xFF64748B)
    val ink = Color(0xFF0B1F35)

    Column(
        Modifier.fillMaxWidth()
            .background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, accent.copy(alpha = .14f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("H19C wearable", color = ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(state.status, color = muted, fontSize = 9.sp, lineHeight = 13.sp)
            }
            Text(
                if (state.connected) "CONNECTED" else "DIRECT BLE",
                color = if (state.connected) green else accent,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black
            )
        }

        if (state.deviceName != null || state.deviceAddress != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull(state.deviceName, state.model, state.firmware?.let { "FW $it" }).joinToString(" · "),
                color = muted,
                fontSize = 8.sp
            )
        }

        if (state.connected) {
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                H19cStat("HR", state.heartRateBpm?.let { "$it bpm" } ?: "—", Modifier.weight(1f))
                H19cStat("STEPS", state.steps?.toString() ?: "—", Modifier.weight(1f))
                H19cStat("SpO₂", state.bloodOxygenPercent?.let { "$it%" } ?: "—", Modifier.weight(1f))
                H19cStat("BATTERY", state.batteryPercent?.let { "$it%" } ?: "—", Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                H19cAction("SYNC", accent, Modifier.weight(1f)) { H19cWearableRuntime.refreshActivity() }
                H19cAction("MEASURE HR", Color(0xFFD46072), Modifier.weight(1f)) { H19cWearableRuntime.measureHeartRate() }
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                H19cAction(
                    if (state.liveHeartRate) "STOP LIVE HR" else "LIVE HR",
                    if (state.liveHeartRate) Color(0xFFCA3A3A) else green,
                    Modifier.weight(1f)
                ) { H19cWearableRuntime.setLiveHeartRate(!state.liveHeartRate) }
                H19cAction("MEASURE SpO₂", Color(0xFF20A7C4), Modifier.weight(1f)) { H19cWearableRuntime.measureBloodOxygen() }
            }
        }

        Spacer(Modifier.height(11.dp))
        H19cAction(
            when (state.phase) {
                H19cConnectionPhase.SCANNING -> "SCANNING…"
                H19cConnectionPhase.CONNECTING, H19cConnectionPhase.DISCOVERING -> "CONNECTING…"
                H19cConnectionPhase.READY -> "DISCONNECT"
                else -> if (state.deviceAddress != null) "RECONNECT H19C" else "FIND H19C"
            },
            if (state.connected) Color(0xFFCA3A3A) else accent,
            Modifier.fillMaxWidth(),
            enabled = state.phase !in setOf(H19cConnectionPhase.SCANNING, H19cConnectionPhase.CONNECTING, H19cConnectionPhase.DISCOVERING)
        ) {
            if (state.connected) H19cWearableRuntime.disconnect() else connect()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Direct BLE writes H19C heart rate, steps, activity calories, SpO₂ and supported sleep stages into the Project Superhuman Data Vault. Da Fit is not required for this connection.",
            color = muted,
            fontSize = 8.sp,
            lineHeight = 12.sp
        )
        state.manufacturer?.let {
            Spacer(Modifier.height(4.dp))
            Text("Protocol manufacturer: $it", color = muted, fontSize = 7.sp)
        }
    }
}

@Composable
internal fun H19cMiniMetricCard(metric: HomeMiniMetric) {
    val context = LocalContext.current
    val state by H19cWearableRuntime.state.collectAsState()
    val permissions = remember { H19cWearableRuntime.requiredPermissions() }
    H19cWearableRuntime.initialize(context)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) H19cWearableRuntime.scanAndConnect(context)
    }

    fun connect() {
        if (H19cWearableRuntime.hasPermissions(context)) {
            if (state.deviceAddress != null) H19cWearableRuntime.reconnectSaved(context)
            else H19cWearableRuntime.scanAndConnect(context)
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    val accent = when (metric) {
        HomeMiniMetric.HEART_RATE -> Color(0xFFD46072)
        HomeMiniMetric.STEPS -> Color(0xFF0D6CB4)
        HomeMiniMetric.BLOOD_OXYGEN -> Color(0xFF20A7C4)
        HomeMiniMetric.CALORIES -> Color(0xFFE08A2E)
    }

    Column(
        Modifier.fillMaxWidth()
            .background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, accent.copy(alpha = .15f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text("H19C DIRECT", color = accent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(5.dp))
        Text(
            if (state.connected) "${state.deviceName ?: "H19C"} connected" else "Connect H19C directly",
            color = Color(0xFF123D70),
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(3.dp))
        Text(state.status, color = Color(0xFF748294), fontSize = 8.sp, lineHeight = 12.sp)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (!state.connected) {
                H19cAction(
                    if (state.deviceAddress != null) "RECONNECT" else "ADD IN SMART DEVICES",
                    accent,
                    Modifier.weight(1f),
                    enabled = state.deviceAddress != null
                ) { reconnectSaved() }
            } else {
                when (metric) {
                    HomeMiniMetric.HEART_RATE -> {
                        H19cAction("MEASURE", accent, Modifier.weight(1f)) { H19cWearableRuntime.measureHeartRate() }
                        H19cAction(if (state.liveHeartRate) "STOP LIVE" else "LIVE HR", accent, Modifier.weight(1f)) {
                            H19cWearableRuntime.setLiveHeartRate(!state.liveHeartRate)
                        }
                    }
                    HomeMiniMetric.STEPS, HomeMiniMetric.CALORIES ->
                        H19cAction("SYNC ACTIVITY", accent, Modifier.weight(1f)) { H19cWearableRuntime.refreshActivity() }
                    HomeMiniMetric.BLOOD_OXYGEN ->
                        H19cAction("MEASURE SpO₂", accent, Modifier.weight(1f)) { H19cWearableRuntime.measureBloodOxygen() }
                }
            }
        }
    }
}

@Composable
private fun H19cStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.background(Color(0xFFF6F9FC), RoundedCornerShape(13.dp)).padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(label, color = Color(0xFF748294), fontSize = 6.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(2.dp))
        Text(value, color = Color(0xFF123D70), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun H19cAction(
    label: String,
    accent: Color,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier.height(38.dp)
            .background(accent.copy(alpha = if (enabled) .12f else .05f), RoundedCornerShape(13.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = accent.copy(alpha = if (enabled) 1f else .45f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black
        )
    }
}
