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

private val EnvPermissionNavy = Color(0xFF123D70)
private val EnvPermissionBlue = Color(0xFF0D6CB4)
private val EnvPermissionTeal = Color(0xFF168A78)
private val EnvPermissionMuted = Color(0xFF748294)
private val EnvPermissionBorder = Color(0xFFE1E9EF)
private val EnvPermissionBg = Color(0xFFF8FBFD)

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
                Text("←", color = EnvPermissionNavy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Environment", color = Color(0xFF0B1F35), fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Local context for sleep, activity & wellbeing", color = EnvPermissionMuted, fontSize = 10.sp)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFE8F4FF), Color(0xFFF3FBFA), Color.White)
                    ),
                    RoundedCornerShape(28.dp)
                )
                .border(1.dp, EnvPermissionBorder, RoundedCornerShape(28.dp))
                .padding(20.dp)
        ) {
            Text("LOCAL WEATHER", color = EnvPermissionBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.15.sp)
            Spacer(Modifier.height(8.dp))
            Text("Use your location for environmental context", color = EnvPermissionNavy, fontSize = 23.sp, fontWeight = FontWeight.Black, lineHeight = 27.sp)
            Spacer(Modifier.height(7.dp))
            Text(
                "Project Superhuman uses location only to request nearby weather and air-quality context. Coordinates are coarsened before the weather request and precise coordinates are not stored in your Data Vault.",
                color = EnvPermissionMuted,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }

        PermissionBenefit("Weather", "Temperature, feels-like, precipitation, wind and atmospheric pressure.", EnvPermissionBlue)
        PermissionBenefit("Exposure", "UV and air-quality readings add useful context to outdoor activity and wellbeing.", Color(0xFF8B79C8))
        PermissionBenefit("Daylight", "Sunrise, sunset and daylight duration help contextualise sleep timing and routine.", EnvPermissionTeal)

        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    Brush.horizontalGradient(listOf(EnvPermissionBlue, Color(0xFF20A7C4))),
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
            .background(Color.White, RoundedCornerShape(20.dp))
            .border(1.dp, EnvPermissionBorder, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(10.dp)
                .height(38.dp)
                .background(accent, RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = EnvPermissionNavy, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(2.dp))
            Text(body, color = EnvPermissionMuted, fontSize = 9.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun PermissionSecondaryButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, EnvPermissionBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = EnvPermissionNavy, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
