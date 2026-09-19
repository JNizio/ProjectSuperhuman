package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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

/**
 * Cardio chooses among devices that have already been configured in Settings -> Smart Devices.
 * This panel deliberately owns no scanning, pairing or permission onboarding.
 */
@Composable
internal fun CardioSensorPickerPanel(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    CardioSensorRuntime.initialize(context)
    H19cWearableRuntime.initialize(context)

    val sensorState by CardioSensorRuntime.state.collectAsState()
    val h19cState by H19cWearableRuntime.state.collectAsState()

    val h19cReady = h19cState.deviceAddress != null &&
        H19cWearableRuntime.hasPermissions(context)
    val bleReady = CardioSensorRuntime.hasSavedBleDevice() &&
        CardioSensorRuntime.hasBlePermissions()

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
                    "HEART-RATE SOURCE",
                    color = superhumanTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Choose an already configured source for this workout.",
                    color = superhumanTextMuted,
                    fontSize = 10.sp
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
                if (!h19cReady) {
                    SmartDevicesNavigationBridge.open?.invoke()
                } else {
                    scope.launch { CardioSensorRuntime.selectH19c(connect = true) }
                }
            }

            SensorChoice(
                label = "BLE HR",
                selected = sensorState.providerType == CardioSensorProviderType.BLE_HEART_RATE,
                modifier = Modifier.weight(1f)
            ) {
                if (!bleReady) {
                    SmartDevicesNavigationBridge.open?.invoke()
                } else {
                    scope.launch { CardioSensorRuntime.selectBle(connectPreferred = true) }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    sensorState.providerType == CardioSensorProviderType.H19C && !h19cReady ->
                        "H19C needs setup in Smart Devices before Cardio can use it."
                    sensorState.providerType == CardioSensorProviderType.BLE_HEART_RATE && !bleReady ->
                        "Add and authorize a Bluetooth HR sensor in Smart Devices first."
                    else -> sensorState.message
                },
                color = superhumanTextMuted,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "MANAGE",
                color = superhumanBlue,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Manage smart devices"
                    }
                    .clickable { SmartDevicesNavigationBridge.open?.invoke() }
                    .padding(horizontal = 10.dp, vertical = 13.dp)
            )
        }
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
            .clickable(onClick = onClick)
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
