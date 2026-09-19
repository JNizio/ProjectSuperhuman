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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private enum class CardioPendingSensorAction {
    NONE,
    CONNECT_H19C,
    SCAN_BLE
}

@Composable
internal fun CardioSensorPickerPanel(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sensorState by CardioSensorRuntime.state.collectAsState()
    val bleDevices by CardioSensorRuntime.bleDevices.collectAsState()
    var pendingAction by remember { mutableStateOf(CardioPendingSensorAction.NONE) }

    fun runPendingAction(action: CardioPendingSensorAction) {
        pendingAction = CardioPendingSensorAction.NONE
        scope.launch {
            when (action) {
                CardioPendingSensorAction.CONNECT_H19C -> CardioSensorRuntime.reconnectPreferred()
                CardioPendingSensorAction.SCAN_BLE -> CardioSensorRuntime.scanBle()
                CardioPendingSensorAction.NONE -> Unit
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        val action = pendingAction
        if (granted) runPendingAction(action) else pendingAction = CardioPendingSensorAction.NONE
    }

    fun ensurePermissionsThen(action: CardioPendingSensorAction) {
        val permissions = CardioSensorRuntime.requiredPermissions()
        if (permissions.isEmpty() || CardioSensorRuntime.hasRequiredPermissions(context)) {
            runPendingAction(action)
        } else {
            pendingAction = action
            permissionLauncher.launch(permissions)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(21.dp))
            .border(1.dp, superhumanBorder, RoundedCornerShape(21.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "HEART-RATE SENSOR",
                    color = superhumanTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Optional · cardio still works without a sensor",
                    color = superhumanTextMuted,
                    fontSize = 11.sp
                )
            }
            Box(
                Modifier
                    .background(
                        if (sensorState.connection == CardioSensorConnectionState.CONNECTED) {
                            superhumanGreen.copy(alpha = .14f)
                        } else {
                            superhumanSurfaceSoft
                        },
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    when (sensorState.connection) {
                        CardioSensorConnectionState.CONNECTED -> "CONNECTED"
                        CardioSensorConnectionState.STALE -> "STALE"
                        CardioSensorConnectionState.SCANNING -> "SCANNING"
                        CardioSensorConnectionState.CONNECTING -> "CONNECTING"
                        CardioSensorConnectionState.RECONNECTING -> "RECONNECTING"
                        CardioSensorConnectionState.ERROR -> "ERROR"
                        CardioSensorConnectionState.DISCONNECTED -> "OFFLINE"
                        CardioSensorConnectionState.NO_SENSOR -> "OFF"
                    },
                    color = if (sensorState.connection == CardioSensorConnectionState.CONNECTED) {
                        superhumanGreen
                    } else {
                        superhumanTextMuted
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SensorChoice(
                label = "None",
                selected = sensorState.providerType == CardioSensorProviderType.NONE,
                modifier = Modifier.weight(1f)
            ) {
                scope.launch { CardioSensorRuntime.selectNone() }
            }
            SensorChoice(
                label = "H19C",
                selected = sensorState.providerType == CardioSensorProviderType.H19C,
                modifier = Modifier.weight(1f)
            ) {
                scope.launch {
                    CardioSensorRuntime.selectH19c(connect = false)
                    ensurePermissionsThen(CardioPendingSensorAction.CONNECT_H19C)
                }
            }
            SensorChoice(
                label = "BLE HR",
                selected = sensorState.providerType == CardioSensorProviderType.BLE_HEART_RATE,
                modifier = Modifier.weight(1f)
            ) {
                scope.launch {
                    CardioSensorRuntime.selectBle(connectPreferred = false)
                    ensurePermissionsThen(CardioPendingSensorAction.SCAN_BLE)
                }
            }
        }

        if (sensorState.providerType == CardioSensorProviderType.BLE_HEART_RATE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Standard Bluetooth heart-rate straps",
                    color = superhumanTextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "SCAN",
                    color = superhumanBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Scan for Bluetooth heart-rate sensors"
                        }
                        .clickable {
                            ensurePermissionsThen(CardioPendingSensorAction.SCAN_BLE)
                        }
                        .padding(horizontal = 10.dp, vertical = 14.dp)
                )
            }

            if (bleDevices.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bleDevices, key = { it.sensorId }) { device ->
                        Column(
                            Modifier
                                .width(170.dp)
                                .heightIn(min = 62.dp)
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Signal ${device.rssi} dBm · tap to connect",
                                color = superhumanTextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        Text(
            sensorState.message,
            color = superhumanTextMuted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun SensorChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .background(
                if (selected) superhumanGreen.copy(alpha = .16f) else superhumanSurfaceSoft,
                RoundedCornerShape(13.dp)
            )
            .border(
                1.dp,
                if (selected) superhumanGreen else superhumanBorder,
                RoundedCornerShape(13.dp)
            )
            .semantics {
                this.selected = selected
                role = Role.Button
                contentDescription = label + if (selected) ", selected" else ""
            }
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) superhumanGreen else superhumanTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
