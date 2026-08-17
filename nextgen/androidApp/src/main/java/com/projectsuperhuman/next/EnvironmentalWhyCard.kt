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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EnvironmentalWhyCard() {
    val palette = superhumanPalette
    Column(
        Modifier.fillMaxWidth()
            .background(palette.accentSoft, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text("CONTEXT", color = palette.blue, fontSize = 9.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text("Useful alongside your daily patterns", color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text("Local conditions add another layer of context when reviewing sleep, activity and routines.", color = palette.textMuted, fontSize = 9.sp, lineHeight = 14.sp)
    }
}
