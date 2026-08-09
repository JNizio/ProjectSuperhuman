package com.projectsuperhuman.next

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.m1x.HealthBridge

private val Navy = Color(0xFF082D66)
private val Blue = Color(0xFF0D63C8)
private val Bg = Color(0xFFF8FAFD)
private val Ink = Color(0xFF0B1F35)
private val Muted = Color(0xFF64748B)
private val Line = Color(0xFFDCE5EF)

class NextShellActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                SuperhumanShell(
                    openLegacy = {
                        startActivity(Intent(this, HealthBridge::class.java))
                    }
                )
            }
        }
    }
}

private enum class ShellPage { HOME, SETTINGS }

@Composable
private fun SuperhumanShell(openLegacy: () -> Unit) {
    var page by remember { mutableStateOf(ShellPage.HOME) }

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            NativeTopBar(
                title = if (page == ShellPage.HOME) "PROJECT SUPERHUMAN" else "SETTINGS",
                onSettings = { page = if (page == ShellPage.SETTINGS) ShellPage.HOME else ShellPage.SETTINGS }
            )
            when (page) {
                ShellPage.HOME -> NativeHome(openLegacy)
                ShellPage.SETTINGS -> NativeSettings(openLegacy)
            }
        }
    }
}

@Composable
private fun NativeTopBar(title: String, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(44.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(Navy, Blue)),
                        shape = RoundedCornerShape(13.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("PS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(title, color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.3.sp)
                Text("Native shell · migration mode", color = Muted, fontSize = 10.sp)
            }
        }
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(42.dp)
                .background(Color.White, RoundedCornerShape(14.dp))
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center
        ) {
            Text("⚙", color = Navy, fontSize = 19.sp)
        }
    }
}

@Composable
private fun NativeHome(openLegacy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFFEAF4FF), Color.White)),
                    RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Text("NEXT-GEN FOUNDATION", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(7.dp))
            Text("Same Superhuman.\nNew native core.", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "The new shell now owns navigation, system bars and app lifecycle. Existing modules stay available while we replace them one by one.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }

        ShellCard("Native Home", "Compose destination · active", Color(0xFFF2F7FC), Navy) {}
        ShellCard("Legacy App", "Open the complete current WebView app", Color(0xFFEAF3FF), Blue, openLegacy)
        ShellCard("Database", "Next-gen SQLite/SQLDelight foundation ready", Color(0xFFF0F8F6), Color(0xFF168A78)) {}
        ShellCard("Scientific Core", "Shared interpretation boundary ready", Color(0xFFF5F2FF), Color(0xFF6547C9)) {}

        Text(
            "Step 4 will replace this migration dashboard with the real Project Superhuman native Home design.",
            color = Muted,
            fontSize = 10.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(4.dp, 4.dp, 4.dp, 24.dp)
        )
    }
}

@Composable
private fun NativeSettings(openLegacy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Native settings", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text("Platform-level controls now live outside the WebView.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))

        SettingsRow("Data Vault", "Database, backup and migration controls")
        SettingsRow("Health integrations", "Health Connect now · HealthKit later")
        SettingsRow("Permissions", "Camera, Bluetooth and health access")
        SettingsRow("Scientific engine", "Rules, provenance and engine version")
        SettingsRow("Legacy app", "Open old settings and unmigrated features", onClick = openLegacy)

        Text(
            "The legacy branch remains frozen separately. This screen becomes the permanent settings destination as migration continues.",
            color = Muted,
            fontSize = 10.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(4.dp, 10.dp, 4.dp, 24.dp)
        )
    }
}

@Composable
private fun ShellCard(title: String, subtitle: String, background: Color, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(5.dp).height(40.dp).background(accent, RoundedCornerShape(99.dp)))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
        }
        Text("›", color = accent, fontSize = 25.sp)
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
        }
        Text("›", color = Color(0xFF8AA1B4), fontSize = 23.sp)
    }
}
