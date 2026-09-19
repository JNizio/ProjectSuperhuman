package com.projectsuperhuman.next

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch

internal object SmartDevicesNavigationBridge {
    var open: (() -> Unit)? = null
}

internal enum class SmartDeviceFamily {
    PLATFORM_BRIDGE,
    WEARABLE,
    HEART_RATE_SENSOR,
    SCALE
}

internal enum class SmartDeviceTransport {
    HEALTH_CONNECT,
    DIRECT_BLE,
    STANDARD_BLE
}

internal data class SmartDeviceSupport(
    val id: String,
    val name: String,
    val family: SmartDeviceFamily,
    val transport: SmartDeviceTransport,
    val capabilities: List<String>,
    val nativePath: String
)

/**
 * User-facing support catalogue. This describes only integrations that actually exist in the app.
 * Vendor devices that publish through Health Connect are represented by the bridge rather than
 * pretending Project Superhuman speaks every proprietary watch protocol directly.
 */
internal object SmartDeviceCatalog {
    val supported: List<SmartDeviceSupport> = listOf(
        SmartDeviceSupport(
            id = "health-connect",
            name = "Health Connect",
            family = SmartDeviceFamily.PLATFORM_BRIDGE,
            transport = SmartDeviceTransport.HEALTH_CONNECT,
            capabilities = listOf("sleep", "heart rate", "steps", "calories", "workouts"),
            nativePath = "Android health data bridge"
        ),
        SmartDeviceSupport(
            id = "h19c-direct",
            name = "H19C / Da Fit family",
            family = SmartDeviceFamily.WEARABLE,
            transport = SmartDeviceTransport.DIRECT_BLE,
            capabilities = listOf("heart rate", "SpO₂", "steps", "activity", "sleep"),
            nativePath = "Direct BLE"
        ),
        SmartDeviceSupport(
            id = "ble-heart-rate",
            name = "Bluetooth heart-rate sensor",
            family = SmartDeviceFamily.HEART_RATE_SENSOR,
            transport = SmartDeviceTransport.STANDARD_BLE,
            capabilities = listOf("live heart rate", "cardio telemetry"),
            nativePath = "Bluetooth SIG Heart Rate Service"
        ),
        SmartDeviceSupport(
            id = "okok-scale",
            name = "OKOK smart scale",
            family = SmartDeviceFamily.SCALE,
            transport = SmartDeviceTransport.DIRECT_BLE,
            capabilities = listOf("weight", "impedance", "body composition"),
            nativePath = "Direct BLE advertisement"
        )
    )
}

private enum class DeviceBluetoothAction {
    NONE,
    H19C_CONNECT,
    BLE_SCAN,
    SCALE_LISTEN
}

@Composable
internal fun SmartDevicesHub(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    H19cWearableRuntime.initialize(context)
    CardioSensorRuntime.initialize(context)

    val h19c by H19cWearableRuntime.state.collectAsState()
    val cardioSensor by CardioSensorRuntime.state.collectAsState()
    val bleDevices by CardioSensorRuntime.bleDevices.collectAsState()

    var healthConnected by remember { mutableStateOf(false) }
    var healthAllPermissions by remember { mutableStateOf(false) }
    var healthStatus by remember { mutableStateOf("Checking Health Connect…") }
    var healthSyncing by remember { mutableStateOf(false) }
    var pendingBluetoothAction by remember { mutableStateOf(DeviceBluetoothAction.NONE) }
    var startedScaleHere by remember { mutableStateOf(false) }

    suspend fun refreshHealthState() {
        when (GlobalHealthConnect.availability(context)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                healthConnected = GlobalHealthConnect.hasAnyCorePermission(context)
                healthAllPermissions = GlobalHealthConnect.hasAllCorePermissions(context)
                healthStatus = when {
                    healthAllPermissions -> "Connected · all requested health permissions available"
                    healthConnected -> "Connected · some health permissions are still optional/missing"
                    else -> "Ready to connect compatible watches and health apps"
                }
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                healthConnected = false
                healthAllPermissions = false
                healthStatus = "Health Connect needs an update"
            }
            else -> {
                healthConnected = false
                healthAllPermissions = false
                healthStatus = "Health Connect is unavailable on this device"
            }
        }
    }

    suspend fun syncHealthConnect() {
        if (!healthConnected) {
            refreshHealthState()
            return
        }
        healthSyncing = true
        val result = GlobalHealthConnect.sync(context)
        healthSyncing = false
        healthStatus = result.message
        refreshHealthState()
    }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        scope.launch {
            refreshHealthState()
            if (healthConnected) syncHealthConnect()
        }
    }

    fun runBluetoothAction(action: DeviceBluetoothAction) {
        pendingBluetoothAction = DeviceBluetoothAction.NONE
        when (action) {
            DeviceBluetoothAction.H19C_CONNECT -> scope.launch {
                CardioSensorRuntime.selectH19c(connect = true)
            }
            DeviceBluetoothAction.BLE_SCAN -> scope.launch {
                CardioSensorRuntime.selectBle(connectPreferred = false)
                CardioSensorRuntime.scanBle()
            }
            DeviceBluetoothAction.SCALE_LISTEN -> {
                startedScaleHere = true
                OkokScaleManager.startAutoTracking(context)
            }
            DeviceBluetoothAction.NONE -> Unit
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val action = pendingBluetoothAction
        if (result.values.all { it }) runBluetoothAction(action)
        else pendingBluetoothAction = DeviceBluetoothAction.NONE
    }

    fun ensureBluetoothPermissions(action: DeviceBluetoothAction, permissions: Array<String>) {
        if (permissions.isEmpty() || permissions.all { permission ->
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        ) {
            runBluetoothAction(action)
        } else {
            pendingBluetoothAction = action
            bluetoothPermissionLauncher.launch(permissions)
        }
    }

    LaunchedEffect(Unit) {
        refreshHealthState()
    }

    DisposableEffect(Unit) {
        onDispose {
            if (startedScaleHere) OkokScaleManager.stopAutoTracking()
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(24.dp))
            .border(1.dp, superhumanBorder, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Smart Devices",
                    color = superhumanTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "One place for watches, sensors, scales and health-data bridges.",
                    color = superhumanTextMuted,
                    fontSize = 10.sp
                )
            }
            Box(
                Modifier
                    .background(superhumanBlue.copy(alpha = .12f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    "DEVICE HUB",
                    color = superhumanBlue,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Text(
            "Pair and manage devices here. Exercise, Body, Sleep and other modules consume the same native Data Vault instead of maintaining separate device accounts.",
            color = superhumanTextMuted,
            fontSize = 9.sp,
            lineHeight = 14.sp
        )

        DeviceHubCard(
            title = "Health Connect",
            subtitle = healthStatus,
            status = when {
                healthAllPermissions -> "CONNECTED"
                healthConnected -> "PARTIAL"
                else -> "NOT CONNECTED"
            },
            healthy = healthConnected,
            capabilities = "Sleep · HR · steps · calories · workouts",
            action = if (healthSyncing) "SYNCING…" else if (healthConnected) "SYNC NOW" else "CONNECT",
            actionEnabled = !healthSyncing,
            onAction = {
                scope.launch {
                    if (GlobalHealthConnect.availability(context) != HealthConnectClient.SDK_AVAILABLE) {
                        refreshHealthState()
                    } else if (healthConnected) {
                        syncHealthConnect()
                    } else {
                        healthPermissionLauncher.launch(GlobalHealthConnect.requestPermissions(context))
                    }
                }
            }
        )

        DeviceHubCard(
            title = h19c.deviceName ?: "H19C / Da Fit wearable",
            subtitle = h19c.status,
            status = when (h19c.phase) {
                H19cConnectionPhase.READY -> "CONNECTED"
                H19cConnectionPhase.SCANNING -> "SCANNING"
                H19cConnectionPhase.CONNECTING, H19cConnectionPhase.DISCOVERING -> "CONNECTING"
                H19cConnectionPhase.ERROR -> "ERROR"
                else -> if (h19c.deviceAddress != null) "SAVED" else "NOT CONNECTED"
            },
            healthy = h19c.connected,
            capabilities = "Direct BLE · HR · SpO₂ · steps · activity · sleep",
            action = if (h19c.connected) "DISCONNECT" else if (h19c.deviceAddress != null) "RECONNECT" else "ADD WATCH",
            actionEnabled = h19c.phase !in setOf(
                H19cConnectionPhase.SCANNING,
                H19cConnectionPhase.CONNECTING,
                H19cConnectionPhase.DISCOVERING
            ),
            onAction = {
                if (h19c.connected) {
                    H19cWearableRuntime.disconnect()
                } else {
                    scope.launch { CardioSensorRuntime.selectH19c(connect = false) }
                    ensureBluetoothPermissions(
                        DeviceBluetoothAction.H19C_CONNECT,
                        H19cWearableRuntime.requiredPermissions()
                    )
                }
            }
        )

        DeviceHubCard(
            title = "Bluetooth heart-rate sensors",
            subtitle = cardioSensor.message,
            status = when (cardioSensor.connection) {
                CardioSensorConnectionState.CONNECTED -> "CONNECTED"
                CardioSensorConnectionState.SCANNING -> "SCANNING"
                CardioSensorConnectionState.CONNECTING -> "CONNECTING"
                CardioSensorConnectionState.RECONNECTING -> "RECONNECTING"
                CardioSensorConnectionState.STALE -> "STALE"
                CardioSensorConnectionState.ERROR -> "ERROR"
                else -> "AVAILABLE"
            },
            healthy = cardioSensor.providerType == CardioSensorProviderType.BLE_HEART_RATE &&
                cardioSensor.connection == CardioSensorConnectionState.CONNECTED,
            capabilities = "Standard BLE HR · live Cardio telemetry",
            action = if (cardioSensor.providerType == CardioSensorProviderType.BLE_HEART_RATE &&
                cardioSensor.connection == CardioSensorConnectionState.CONNECTED) "DISCONNECT" else "SCAN",
            onAction = {
                if (cardioSensor.providerType == CardioSensorProviderType.BLE_HEART_RATE &&
                    cardioSensor.connection == CardioSensorConnectionState.CONNECTED
                ) {
                    scope.launch { CardioSensorRuntime.disconnectSelected() }
                } else {
                    scope.launch {
                        CardioSensorRuntime.selectBle(connectPreferred = false)
                        val permissions = CardioSensorRuntime.requiredPermissions()
                        ensureBluetoothPermissions(DeviceBluetoothAction.BLE_SCAN, permissions)
                    }
                }
            }
        )

        if (bleDevices.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(bleDevices, key = { it.sensorId }) { device ->
                    Column(
                        Modifier
                            .width(180.dp)
                            .heightIn(min = 70.dp)
                            .background(superhumanSurfaceSoft, RoundedCornerShape(14.dp))
                            .semantics {
                                role = Role.Button
                                contentDescription = "Connect to ${device.displayName}"
                            }
                            .clickable {
                                scope.launch { CardioSensorRuntime.connectBle(device.sensorId) }
                            }
                            .padding(11.dp)
                    ) {
                        Text(
                            device.displayName,
                            color = superhumanTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Signal ${device.rssi} dBm · tap to add",
                            color = superhumanTextMuted,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        DeviceHubCard(
            title = "OKOK smart scale",
            subtitle = OkokScaleManager.status,
            status = if (OkokScaleManager.listening) "LISTENING" else
                if (OkokScaleManager.lastSavedAt != null) "READY" else "AVAILABLE",
            healthy = OkokScaleManager.lastSavedAt != null,
            capabilities = "Direct BLE · weight · impedance · body composition",
            action = if (OkokScaleManager.listening) "STOP" else "LISTEN NOW",
            onAction = {
                if (OkokScaleManager.listening) {
                    OkokScaleManager.stopAutoTracking()
                    startedScaleHere = false
                } else {
                    ensureBluetoothPermissions(
                        DeviceBluetoothAction.SCALE_LISTEN,
                        OkokScaleManager.requiredPermissions()
                    )
                }
            }
        )

        Column(
            Modifier
                .fillMaxWidth()
                .background(superhumanSurfaceSoft, RoundedCornerShape(16.dp))
                .padding(13.dp)
        ) {
            Text(
                "COMPATIBILITY MODEL",
                color = superhumanTextMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Project Superhuman can support many watches through Health Connect, standard Bluetooth sensors through published BLE profiles, and selected devices through direct adapters. Proprietary devices still need an explicit adapter or their companion app to publish data through Health Connect.",
                color = superhumanTextPrimary,
                fontSize = 9.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun DeviceHubCard(
    title: String,
    subtitle: String,
    status: String,
    healthy: Boolean,
    capabilities: String,
    action: String,
    actionEnabled: Boolean = true,
    onAction: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(superhumanSurfaceSoft, RoundedCornerShape(18.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = superhumanTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    capabilities,
                    color = superhumanTextMuted,
                    fontSize = 8.sp,
                    lineHeight = 12.sp
                )
            }
            Box(
                Modifier
                    .background(
                        if (healthy) superhumanGreen.copy(alpha = .14f) else superhumanSurface,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    status,
                    color = if (healthy) superhumanGreen else superhumanTextMuted,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Text(
            subtitle,
            color = superhumanTextMuted,
            fontSize = 9.sp,
            lineHeight = 13.sp
        )

        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .background(
                    if (actionEnabled) superhumanBlue.copy(alpha = .12f) else superhumanBorder.copy(alpha = .35f),
                    RoundedCornerShape(13.dp)
                )
                .semantics {
                    role = Role.Button
                    contentDescription = "$action $title"
                }
                .clickable(enabled = actionEnabled, onClick = onAction)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                action,
                color = if (actionEnabled) superhumanBlue else superhumanTextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
