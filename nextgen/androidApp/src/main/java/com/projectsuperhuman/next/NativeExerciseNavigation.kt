package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class ExerciseDestination {
    HUB,
    STRENGTH,
    CARDIO
}

@Composable
internal fun ExerciseModuleHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.superhumanTopButton(onClick = onBack).padding(horizontal = 15.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = superhumanBrandText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = superhumanBrandText, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = superhumanTextMuted, fontSize = 9.sp)
        }
    }
}

@Composable
internal fun ExerciseModuleOption(
    title: String,
    description: String,
    badge: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.background(
                if (SuperhumanAppearance.darkMode) accent.copy(alpha = .18f) else accent.copy(alpha = .10f),
                RoundedCornerShape(16.dp)
            ).padding(horizontal = 15.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(badge, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f).padding(start = 15.dp, end = 10.dp)) {
            Text(title, color = superhumanTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(description, color = superhumanTextMuted, fontSize = 10.sp, lineHeight = 15.sp)
        }
        Text("→", color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
