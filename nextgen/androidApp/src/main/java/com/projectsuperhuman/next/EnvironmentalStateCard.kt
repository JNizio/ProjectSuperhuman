package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EnvironmentalStateCard(
    testTag: String,
    title: String,
    message: String,
    accent: Color,
    loading: Boolean = false
) {
    val shape = RoundedCornerShape(27.dp)
    Column(
        Modifier.fillMaxWidth().testTag(testTag)
            .background(Color.White, shape)
            .border(1.dp, Color(0xFFE1E9EF), shape)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(24.dp), color = accent, strokeWidth = 2.dp)
            } else {
                Box(Modifier.size(24.dp).background(accent.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(7.dp).background(accent, CircleShape))
                }
            }
            Spacer(Modifier.width(11.dp))
            Text(title, color = Color(0xFF0B1F35), fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(9.dp))
        Text(message, color = Color(0xFF748294), fontSize = 10.sp, lineHeight = 15.sp)
    }
}
