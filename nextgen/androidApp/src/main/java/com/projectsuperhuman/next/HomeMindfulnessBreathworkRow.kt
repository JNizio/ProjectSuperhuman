package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HomeMindfulnessBreathworkRow(
    snapshot: NativeHomeSnapshot,
    openMindfulness: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MindfulnessHalfTile(snapshot, Modifier.weight(1f), openMindfulness)
        BreathworkHalfTile(Modifier.weight(1f), openMindfulness)
    }
}

@Composable
private fun MindfulnessHalfTile(
    snapshot: NativeHomeSnapshot,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val minutes = snapshot.mindfulnessMinutesToday
    Column(
        modifier.height(132.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(Color(0xFFF4FAFB))
            .border(1.dp, Color(0xFFDCECEF), RoundedCornerShape(23.dp))
            .clickable(onClick = onClick)
            .padding(15.dp)
    ) {
        Text(
            "MINDFULNESS",
            color = Color(0xFF748294),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(9.dp))
        Text(
            if (minutes > 0) "$minutes min" else "Ready",
            color = Color(0xFF176B72),
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            if (minutes > 0) "mindful time today" else "Meditate · reflect · reset",
            color = Color(0xFF748294),
            fontSize = 8.sp
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(4) { index ->
                androidx.compose.foundation.layout.Box(
                    Modifier.weight(1f)
                        .height((10 + index * 5).dp)
                        .background(
                            if (minutes > 0 && index < 3) Color(0xFF5CB7AE) else Color(0xFFD9ECEB),
                            RoundedCornerShape(7.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun BreathworkHalfTile(
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier.height(132.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0B4168), Color(0xFF0F6F82), Color(0xFF1AA2A1))
                )
            )
            .border(1.dp, Color(0xFF2A8DA0), RoundedCornerShape(23.dp))
            .clickable(onClick = onClick)
            .padding(15.dp)
    ) {
        Text(
            "BREATHWORK",
            color = Color.White.copy(alpha = .70f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(9.dp))
        Text("3 rounds", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text("30 breaths · timed holds", color = Color.White.copy(alpha = .72f), fontSize = 8.sp)
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) {
                androidx.compose.foundation.layout.Box(
                    Modifier.weight(1f)
                        .height(8.dp)
                        .background(Color.White.copy(alpha = .22f), CircleShape)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Start guided routine  →", color = Color(0xFF8EF0DE), fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}
