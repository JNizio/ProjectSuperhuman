package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EnvironmentalWhyCard() {
    Column(
        Modifier.fillMaxWidth()
            .background(Color(0xFFF1F7FB), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text("CONTEXT", color = Color(0xFF0D6CB4), fontSize = 9.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text("Useful alongside your daily patterns", color = Color(0xFF0B1F35), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text("Local conditions add another layer of context when reviewing sleep, activity and routines.", color = Color(0xFF748294), fontSize = 9.sp, lineHeight = 14.sp)
    }
}
