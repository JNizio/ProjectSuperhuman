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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private data class PopularDeviceConnection(
    val id: String,
    val mark: String,
    val name: String,
    val companionApp: String,
    val description: String
)

private val popularDeviceConnections = listOf(
    PopularDeviceConnection("samsung", "S", "Galaxy Watch", "Samsung Health", "Samsung watches"),
    PopularDeviceConnection("fitbit", "F", "Fitbit / Pixel Watch", "Fitbit", "Fitbit & Pixel wearables"),
    PopularDeviceConnection("garmin", "G", "Garmin", "Garmin Connect", "Garmin watches & trackers · Android 14+"),
    PopularDeviceConnection("oura", "O", "Oura Ring", "Oura", "Oura rings"),
    PopularDeviceConnection("whoop", "W", "WHOOP", "WHOOP", "WHOOP wearables"),
    PopularDeviceConnection("other", "+", "Other wearable", "your device app", "Any Health Connect source")
)

private enum class DeviceBluetoothAction {
    NONE,
    H19C_ADD,
    H19C_RECONNECT,
    BLE_SCAN,
    BLE_RECONNECT,
    SCALE_ENABLE
}

@Composable
internal fun SmartDevicesHub(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    H19cWearableRuntime.initialize(context)
    CardioSensorRuntime.initialize(context)

    val h19c by H19cWearableRuntime.state.collectAsState()
    val bleSensor by CardioSensorRuntime.bleSensorState.collectAsState()
    val bleDevices by CardioSensorRuntime.bleDevices.collectAsState()

    var healthConnected by remember { mutableStateOf(false) }
    var healthAllPermissions by remember { mutableStateOf(false) }
    var healthStatus by remember { mutableStateOf("Checking Health Connect…") }
    var healthSyncing by remember { mutableStateOf(false) }
    var selectedPopularDevice by remember { mutableStateOf<PopularDeviceConnection?>(null) }
    var pendingBluetoothAction by remember { mutableStateOf(DeviceBluetoothAction.NONE) }
    var confirmForgetH19c by remember { mutableStateOf(false) }
    var confirmForgetBle by remember { mutableStateOf(false) }
    var confirmDisableScale by remember { mutableStateOf(false) }
    var confirmDisconnectHealth by remember { mutableStateOf(false) }

    val bleSaved = CardioSensorRuntime.hasSavedBleDevice()
    val bleSavedName = CardioSensorRuntime.savedBleDeviceName()
    val scaleEnabled = OkokScaleManager.isEnabled(context)

    suspend fun refreshHealthState() {
        when (GlobalHealthConnect.availability(context)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                healthConnected = GlobalHealthConnect.hasAnyCorePermission(context)
                healthAllPermissions = GlobalHealthConnect.hasAllCorePermissions(context)
                healthStatus = when {
                    healthAllPermissions -> "Connected · all requested health permissions available"
                    healthConnected -> "Connected · some health permissions are missing"
                    else -> "Not connected · add compatible health apps here"
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

    fun beginHealthConnectSetup(connection: PopularDeviceConnection?) {
        selectedPopularDevice = connection
        scope.launch {
            when {
                GlobalHealthConnect.availability(context) != HealthConnectClient.SDK_AVAILABLE -> refreshHealthState()
                healthConnected -> syncHealthConnect()
                else -> healthPermissionLauncher.launch(GlobalHealthConnect.requestPermissions(context))
            }
        }
    }

    fun runBluetoothAction(action: DeviceBluetoothAction) {
        pendingBluetoothAction = DeviceBluetoothAction.NONE
        when (action) {
            DeviceBluetoothAction.H19C_ADD -> H19cWearableRuntime.scanAndConnect(context)
            DeviceBluetoothAction.H19C_RECONNECT -> H19cWearableRuntime.reconnectSaved(context)
            DeviceBluetoothAction.BLE_SCAN -> scope.launch {
                CardioSensorRuntime.selectBle(connectPreferred = false)
                CardioSensorRuntime.scanBle()
            }
            DeviceBluetoothAction.BLE_RECONNECT -> scope.launch {
                CardioSensorRuntime.selectBle(connectPreferred = true)
            }
            DeviceBluetoothAction.SCALE_ENABLE -> {
                OkokScaleManager.enableAndStart(context)
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
        val granted = permissions.isEmpty() || permissions.all { permission ->
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (granted) runBluetoothAction(action)
        else {
            pendingBluetoothAction = action
            bluetoothPermissionLauncher.launch(permissions)
        }
    }

    LaunchedEffect(Unit) {
        refreshHealthState()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(superhumanBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.superhumanTopButton(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("‹", color = superhumanBrandText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Devices & connections", color = superhumanTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(
                    "Add wearables, sensors, scales and health apps.",
                    color = superhumanTextMuted,
                    fontSize = 10.sp
                )
            }
        }

        Column(
            Modifier.fillMaxWidth().background(superhumanBrandText, RoundedCornerShape(24.dp)).padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "One connection layer",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "Fast workout sensors stay direct. Watches and health apps can share history through Health Connect.",
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = .74f),
                fontSize = 9.sp,
                lineHeight = 14.sp
            )
        }

        SmartDeviceSectionLabel("QUICK CONNECT")
        Text(
            "Choose the brand you already use. Each shortcut shows the real route instead of pretending every watch has a direct connection.",
            color = superhumanTextMuted,
            fontSize = 9.sp,
            lineHeight = 14.sp
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            items(popularDeviceConnections, key = { it.id }) { connection ->
                PopularConnectionCard(
                    connection = connection,
                    selected = selectedPopularDevice?.id == connection.id,
                    onClick = { beginHealthConnectSetup(connection) }
                )
            }
        }

        selectedPopularDevice?.let { selected ->
            Column(
                Modifier.fillMaxWidth()
                    .background(superhumanSurface, RoundedCornerShape(18.dp))
                    .border(1.dp, superhumanBorder, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(selected.name + " setup", color = superhumanTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text(
                    "1. In " + selected.companionApp + ", enable Health Connect sharing.  2. Grant Project Superhuman the health permissions you want.  3. Return here and sync.",
                    color = superhumanTextMuted,
                    fontSize = 9.sp,
                    lineHeight = 14.sp
                )
                Text(
                    "Route: " + selected.companionApp + " → Health Connect → Project Superhuman. This is not presented as a direct vendor connection.",
                    color = superhumanTextPrimary,
                    fontSize = 8.sp,
                    lineHeight = 12.sp
                )
            }
        }

        SmartDeviceSectionLabel("DIRECT DEVICES")

        DeviceHubCard(
            title = h19c.deviceName ?: "H19C / Da Fit wearable",
            subtitle = buildString {
                append(h19c.status)
                h19c.lastHeartRateEpochMs?.let { append(" · HR ").append(deviceFreshness(it)) }
            },
            status = when (h19c.phase) {
                H19cConnectionPhase.READY -> "CONNECTED"
                H19cConnectionPhase.SCANNING -> "SCANNING"
                H19cConnectionPhase.CONNECTING, H19cConnectionPhase.DISCOVERING -> "CONNECTING"
                H19cConnectionPhase.ERROR -> "ERROR"
                else -> if (h19c.deviceAddress != null) "SAVED" else "NOT ADDED"
            },
            healthy = h19c.connected,
            capabilities = "Watch · direct Bluetooth · HR · SpO₂ · steps · sleep" +
                (h19c.batteryPercent?.let { " · battery $it%" } ?: ""),
            action = when {
                h19c.connected -> "DISCONNECT"
                h19c.deviceAddress != null -> "RECONNECT"
                else -> "ADD WATCH"
            },
            onAction = {
                confirmForgetH19c = false
                when {
                    h19c.connected -> H19cWearableRuntime.disconnect()
                    h19c.deviceAddress != null -> ensureBluetoothPermissions(
                        DeviceBluetoothAction.H19C_RECONNECT,
                        H19cWearableRuntime.requiredPermissions()
                    )
                    else -> ensureBluetoothPermissions(
                        DeviceBluetoothAction.H19C_ADD,
                        H19cWearableRuntime.requiredPermissions()
                    )
                }
            },
            secondaryAction = if (h19c.deviceAddress != null) {
                if (confirmForgetH19c) "TAP TO CONFIRM FORGET" else "FORGET DEVICE"
            } else null,
            secondaryDanger = true,
            onSecondaryAction = {
                if (!confirmForgetH19c) {
                    confirmForgetH19c = true
                } else {
                    H19cWearableRuntime.forget(context)
                    confirmForgetH19c = false
                }
            }
        )

        val bleConnected = bleSensor.connection == CardioSensorConnectionState.CONNECTED
        DeviceHubCard(
            title = bleSensor.provenance?.deviceName ?: bleSavedName ?: "Bluetooth heart-rate sensor",
            subtitle = buildString {
                append(bleSensor.message)
                bleSensor.lastSampleEpochMs?.let { append(" · HR ").append(deviceFreshness(it)) }
            },
            status = when (bleSensor.connection) {
                CardioSensorConnectionState.CONNECTED -> "CONNECTED"
                CardioSensorConnectionState.SCANNING -> "SCANNING"
                CardioSensorConnectionState.CONNECTING -> "CONNECTING"
                CardioSensorConnectionState.RECONNECTING -> "RECONNECTING"
                CardioSensorConnectionState.STALE -> "STALE"
                CardioSensorConnectionState.ERROR -> "ERROR"
                else -> if (bleSaved) "SAVED" else "NOT ADDED"
            },
            healthy = bleConnected,
            capabilities = "Chest strap / armband · direct Bluetooth · live heart rate · Cardio telemetry",
            action = when {
                bleConnected -> "DISCONNECT"
                bleSaved -> "RECONNECT"
                else -> "ADD HR SENSOR"
            },
            onAction = {
                confirmForgetBle = false
                when {
                    bleConnected -> scope.launch { CardioSensorRuntime.disconnectBle() }
                    bleSaved -> ensureBluetoothPermissions(
                        DeviceBluetoothAction.BLE_RECONNECT,
                        CardioSensorRuntime.bleRequiredPermissions()
                    )
                    else -> ensureBluetoothPermissions(
                        DeviceBluetoothAction.BLE_SCAN,
                        CardioSensorRuntime.bleRequiredPermissions()
                    )
                }
            },
            secondaryAction = if (bleSaved) {
                if (confirmForgetBle) "TAP TO CONFIRM FORGET" else "FORGET DEVICE"
            } else null,
            secondaryDanger = true,
            onSecondaryAction = {
                if (!confirmForgetBle) {
                    confirmForgetBle = true
                } else {
                    scope.launch { CardioSensorRuntime.forgetBleDevice() }
                    confirmForgetBle = false
                }
            }
        )

        if (bleDevices.isNotEmpty() && !bleConnected) {
            Text(
                "NEARBY HEART-RATE SENSORS",
                color = superhumanTextMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = .8.sp
            )
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
                        Text(device.displayName, color = superhumanTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.heightIn(min = 3.dp))
                        Text("Signal ${device.rssi} dBm · tap to connect", color = superhumanTextMuted, fontSize = 9.sp)
                    }
                }
            }
        }

        DeviceHubCard(
            title = "OKOK smart scale",
            subtitle = OkokScaleManager.status,
            status = when {
                !scaleEnabled -> "DISABLED"
                OkokScaleManager.listening -> "LISTENING"
                OkokScaleManager.lastSavedAt != null -> "READY"
                else -> "ENABLED"
            },
            healthy = scaleEnabled && (OkokScaleManager.listening || OkokScaleManager.lastSavedAt != null),
            capabilities = "Scale · direct Bluetooth · weight · impedance · body composition" +
                (OkokScaleManager.lastSavedAt?.let { " · last measurement ${deviceFreshness(it)}" } ?: ""),
            action = when {
                OkokScaleManager.listening -> "DISCONNECT"
                !scaleEnabled -> "ENABLE SCALE"
                else -> "LISTEN NOW"
            },
            onAction = {
                confirmDisableScale = false
                if (OkokScaleManager.listening) {
                    OkokScaleManager.stopAutoTracking()
                } else {
                    ensureBluetoothPermissions(
                        DeviceBluetoothAction.SCALE_ENABLE,
                        OkokScaleManager.requiredPermissions()
                    )
                }
            },
            secondaryAction = if (scaleEnabled) {
                if (confirmDisableScale) "TAP TO CONFIRM DISABLE" else "FORGET / DISABLE"
            } else null,
            secondaryDanger = true,
            onSecondaryAction = {
                if (!confirmDisableScale) {
                    confirmDisableScale = true
                } else {
                    OkokScaleManager.forget(context)
                    confirmDisableScale = false
                }
            }
        )

        SmartDeviceSectionLabel("HEALTH DATA BRIDGE")

        DeviceHubCard(
            title = "Health Connect",
            subtitle = healthStatus,
            status = when {
                healthAllPermissions -> "CONNECTED"
                healthConnected -> "PARTIAL"
                else -> "NOT CONNECTED"
            },
            healthy = healthConnected,
            capabilities = "Compatible watch/app history · sleep · HR · steps · calories · workouts",
            action = when {
                healthSyncing -> "SYNCING…"
                healthConnected -> "SYNC NOW"
                else -> "CONNECT"
            },
            actionEnabled = !healthSyncing,
            onAction = {
                confirmDisconnectHealth = false
                scope.launch {
                    when {
                        GlobalHealthConnect.availability(context) != HealthConnectClient.SDK_AVAILABLE ->
                            refreshHealthState()
                        healthConnected -> syncHealthConnect()
                        else -> healthPermissionLauncher.launch(GlobalHealthConnect.requestPermissions(context))
                    }
                }
            },
            secondaryAction = if (healthConnected) {
                if (confirmDisconnectHealth) "TAP TO CONFIRM DISCONNECT" else "DISCONNECT"
            } else null,
            secondaryDanger = true,
            onSecondaryAction = {
                if (!confirmDisconnectHealth) {
                    confirmDisconnectHealth = true
                } else {
                    scope.launch {
                        val disconnected = GlobalHealthConnect.disconnect(context)
                        healthStatus = if (disconnected) {
                            "Disconnected · previously imported history remains in Project Superhuman"
                        } else {
                            "Could not revoke Health Connect access"
                        }
                        refreshHealthState()
                    }
                    confirmDisconnectHealth = false
                }
            }
        )

        Column(
            Modifier
                .fillMaxWidth()
                .background(superhumanSurfaceSoft, RoundedCornerShape(16.dp))
                .padding(13.dp)
        ) {
            Text("SOURCE RULES", color = superhumanTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.heightIn(min = 4.dp))
            Text(
                "Direct devices are the fast path for live physiology. Health Connect is treated as delayed historical/backfill data. Disconnecting or forgetting a device never deletes measurements already stored in the Data Vault.",
                color = superhumanTextPrimary,
                fontSize = 9.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun PopularConnectionCard(
    connection: PopularDeviceConnection,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .width(178.dp)
            .background(if (selected) superhumanBlue.copy(alpha = .09f) else superhumanSurface, shape)
            .border(1.dp, if (selected) superhumanBlue.copy(alpha = .45f) else superhumanBorder, shape)
            .semantics {
                role = Role.Button
                contentDescription = "Set up " + connection.name + " through Health Connect"
            }
            .superhumanClickable(onClick = onClick)
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(34.dp)
                    .heightIn(min = 34.dp)
                    .background(superhumanBrandText, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(connection.mark, color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
            Text("SET UP", color = superhumanBlue, fontSize = 7.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 8.dp))
        }
        Text(connection.name, color = superhumanTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text(connection.description, color = superhumanTextMuted, fontSize = 8.sp, lineHeight = 11.sp)
        Text("via Health Connect", color = superhumanBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmartDeviceSectionLabel(label: String) {
    Text(
        label,
        color = superhumanTextMuted,
        fontSize = 8.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.0.sp
    )
}

@Composable
private fun DeviceBadge(label: String, accent: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .background(accent.copy(alpha = .12f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = accent, fontSize = 8.sp, fontWeight = FontWeight.Black)
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
    onAction: () -> Unit,
    secondaryAction: String? = null,
    secondaryDanger: Boolean = false,
    onSecondaryAction: () -> Unit = {}
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
                Text(title, color = superhumanTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text(capabilities, color = superhumanTextMuted, fontSize = 8.sp, lineHeight = 12.sp)
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

        Text(subtitle, color = superhumanTextMuted, fontSize = 9.sp, lineHeight = 13.sp)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeviceAction(
                label = action,
                enabled = actionEnabled,
                danger = action == "DISCONNECT",
                modifier = Modifier.weight(1f),
                onClick = onAction
            )
            secondaryAction?.let { label ->
                DeviceAction(
                    label = label,
                    enabled = actionEnabled,
                    danger = secondaryDanger,
                    modifier = Modifier.weight(1f),
                    onClick = onSecondaryAction
                )
            }
        }
    }
}

@Composable
private fun DeviceAction(
    label: String,
    enabled: Boolean,
    danger: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val accent = if (danger) superhumanRed else superhumanBlue
    Box(
        modifier
            .heightIn(min = 46.dp)
            .background(
                if (enabled) accent.copy(alpha = .12f) else superhumanBorder.copy(alpha = .35f),
                RoundedCornerShape(13.dp)
            )
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) accent else superhumanTextMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black
        )
    }
}

private fun deviceFreshness(timestampEpochMs: Long): String {
    val ageMs = (System.currentTimeMillis() - timestampEpochMs).coerceAtLeast(0L)
    return when {
        ageMs < 1_000L -> "live"
        ageMs < 60_000L -> "${ageMs / 1_000L}s ago"
        ageMs < 3_600_000L -> "${ageMs / 60_000L}m ago"
        ageMs < 86_400_000L -> "${ageMs / 3_600_000L}h ago"
        else -> "saved history"
    }
}
