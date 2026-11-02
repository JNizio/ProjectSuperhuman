package com.projectsuperhuman.next

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val EnvPermissionNavy: Color get() = superhumanTextPrimary
private val EnvPermissionBlue: Color get() = superhumanBlue
private val EnvPermissionTeal: Color get() = superhumanGreen
private val EnvPermissionMuted: Color get() = superhumanTextMuted
private val EnvPermissionBorder: Color get() = superhumanBorder
private val EnvPermissionBg: Color get() = superhumanBackground

@Composable
internal fun NativeEnvironmentalRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(context.hasEnvironmentalLocationPermission()) }
    var permissionAttempted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            context.hasEnvironmentalLocationPermission()
        permissionAttempted = true
    }

    if (permissionGranted) {
        NativeEnvironmentalPage(onBack = onBack)
        return
    }

    EnvironmentalPermissionPage(
        onBack = onBack,
        permissionAttempted = permissionAttempted,
        onRequestPermission = {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        },
        onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            )
        },
        onCheckAgain = {
            permissionGranted = context.hasEnvironmentalLocationPermission()
        }
    )
}

private fun Context.hasEnvironmentalLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
private fun EnvironmentalPermissionPage(
    onBack: () -> Unit,
    permissionAttempted: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onCheckAgain: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(EnvPermissionBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.superhumanTopButton(onClick = onBack)
                    .semantics { contentDescription = "Back to home" },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = EnvPermissionNavy, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Environment", color = EnvPermissionNavy, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text("Recovery, movement & daily context", color = EnvPermissionMuted, fontSize = 12.sp)
            }
        }

        Column(
            Modifier.fillMaxWidth().midnightCardSurface(accent = EnvPermissionBlue)
                .padding(19.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("LOCAL CONDITIONS", color = EnvPermissionBlue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .8.sp)
            Text("See your environment", color = EnvPermissionNavy, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Text("Enable nearby weather, air quality and daylight data.", color = EnvPermissionMuted, fontSize = 13.sp, lineHeight = 19.sp)
            Text("Location is coarsened before requests; precise coordinates aren't stored in your Data Vault.", color = EnvPermissionMuted, fontSize = 11.sp, lineHeight = 16.sp)
        }

        PermissionBenefit("Weather", "Temperature, wind and precipitation", EnvPermissionBlue)
        PermissionBenefit("Exposure", "UV and air quality", Color(0xFFAA87F7))
        PermissionBenefit("Daylight", "Daylight and your sleep routine", EnvPermissionTeal)

        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    EnvPermissionBlue,
                    RoundedCornerShape(18.dp)
                )
                .clickable(onClick = onRequestPermission)
                .semantics { contentDescription = "Allow location for Environmental data" },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (permissionAttempted) "Ask for location again" else "Enable local environmental data",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
        }

        if (permissionAttempted) {
            Text(
                "If Android no longer shows the permission prompt, enable Location for Project Superhuman in system settings.",
                color = EnvPermissionMuted,
                fontSize = 9.sp,
                lineHeight = 14.sp
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PermissionSecondaryButton("Open app settings", onOpenSettings)
                PermissionSecondaryButton("Check again", onCheckAgain)
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun PermissionBenefit(title: String, body: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .midnightCardSurface(accent = accent)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(31.dp)
                .background(accent, RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = EnvPermissionNavy, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(body, color = EnvPermissionMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun PermissionSecondaryButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(superhumanSurfaceSoft, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = EnvPermissionNavy, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
