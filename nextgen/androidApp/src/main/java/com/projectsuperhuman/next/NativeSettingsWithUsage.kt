package com.projectsuperhuman.next

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SettingsUsageBg get() = superhumanBackground
private val SettingsUsageNavy get() = superhumanBrandText
private val SettingsUsageMuted get() = superhumanTextMuted
private val SettingsUsageBlue get() = superhumanBlue

@Composable
internal fun NativeSettingsWithUsage(openLegacy: () -> Unit, openSmartDevices: () -> Unit) {
    val context = LocalContext.current
    var showUsage by remember { mutableStateOf(false) }
    val themeMode = SuperhumanAppearance.themeMode

    Column(Modifier.fillMaxSize().background(SettingsUsageBg)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsModeButton("ALL SETTINGS", selected = !showUsage, width = 150.dp) { showUsage = false }
                SettingsModeButton("DATA USAGE", selected = showUsage, width = 150.dp) { showUsage = true }
            }

            Row(
                Modifier.fillMaxWidth().background(superhumanSurfaceSoft, RoundedCornerShape(14.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppearanceModeButton("SYSTEM", themeMode == SuperhumanThemeMode.SYSTEM, Modifier.weight(1f)) {
                    SuperhumanAppearance.setThemeMode(context, SuperhumanThemeMode.SYSTEM)
                }
                AppearanceModeButton("LIGHT", themeMode == SuperhumanThemeMode.LIGHT, Modifier.weight(1f)) {
                    SuperhumanAppearance.setThemeMode(context, SuperhumanThemeMode.LIGHT)
                }
                AppearanceModeButton("DARK", themeMode == SuperhumanThemeMode.DARK, Modifier.weight(1f)) {
                    SuperhumanAppearance.setThemeMode(context, SuperhumanThemeMode.DARK)
                }
            }
            Text(
                if (themeMode == SuperhumanThemeMode.SYSTEM) "Appearance follows your Android device automatically." else "Appearance override is active. Choose SYSTEM to follow Android again.",
                color = SettingsUsageMuted,
                fontSize = 8.sp
            )
        }

        if (showUsage) {
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Storage", color = SettingsUsageNavy, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("Local storage, Data Vault footprint and category mix.", color = SettingsUsageMuted, fontSize = 11.sp)
                SettingsDataUsageCard()
                Spacer(Modifier.height(24.dp))
            }
        } else {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                NativeSettingsParity(openLegacy, openSmartDevices)
            }
        }
    }
}

@Composable
private fun SettingsModeButton(label: String, selected: Boolean, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.width(width).height(38.dp)
            .background(if (selected) SettingsUsageBlue else superhumanSurface, shape)
            .border(1.dp, if (selected) SettingsUsageBlue else superhumanBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else SettingsUsageMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = .7.sp
        )
    }
}

@Composable
private fun AppearanceModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.height(34.dp)
            .background(if (selected) superhumanSurface else Color.Transparent, shape)
            .border(1.dp, if (selected) superhumanBorder else Color.Transparent, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) SettingsUsageNavy else SettingsUsageMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = .5.sp
        )
    }
}
