package com.projectsuperhuman.next

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LifestyleNavy = Color(0xFF082D66)
private val LifestyleBlue = Color(0xFF0D6CB4)
private val LifestyleInk = Color(0xFF0B1F35)
private val LifestyleMuted = Color(0xFF64748B)

@Composable
fun NativeExercisePage(onBack: () -> Unit, openLegacy: () -> Unit) {
    NativeExerciseParityScreen(onBack, openLegacy)
}

@Composable
fun NativeMindfulnessPage(onBack: () -> Unit, openLegacy: () -> Unit) {
    NativeMindfulnessParityScreen(onBack, openLegacy)
}

@Composable
private fun NativeModuleFrame(title: String, subtitle: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(Color.White, RoundedCornerShape(13.dp)).clickable(onClick = onBack).padding(horizontal = 15.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text("‹", color = LifestyleNavy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, color = LifestyleNavy, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = LifestyleMuted, fontSize = 9.sp)
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun Hero(kicker: String, title: String, body: String, background: Color, accent: Color) {
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(background, Color.White)), RoundedCornerShape(24.dp)).padding(19.dp)) {
        Text(kicker, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
        Spacer(Modifier.height(7.dp))
        Text(title, color = LifestyleInk, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text(body, color = LifestyleMuted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun StatCard(label: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(label.uppercase(), color = LifestyleMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(value, color = LifestyleInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = LifestyleMuted, fontSize = 9.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = LifestyleMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, modifier = Modifier.padding(start = 2.dp, top = 3.dp))
}

@Composable
private fun ActionRow(title: String, subtitle: String, initials: String, background: Color, accent: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(19.dp)).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.background(background, RoundedCornerShape(13.dp)).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(initials, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = LifestyleInk, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = LifestyleMuted, fontSize = 9.sp)
        }
        Text("›", color = accent, fontSize = 23.sp)
    }
}

@Composable
private fun BridgeNote(text: String, openLegacy: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).clickable(onClick = openLegacy).padding(15.dp)) {
        Text("Migration bridge", color = LifestyleNavy, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(text, color = LifestyleMuted, fontSize = 9.sp, lineHeight = 14.sp)
    }
}
