package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Explore contains destinations, never fabricated analytics. Existing domain
 * screens remain the source of truth for readings, graphs and history.
 */
@Composable
internal fun ExploreTrendsPage(
    onBack: () -> Unit,
    onSleep: () -> Unit,
    onExercise: () -> Unit,
    onVitals: () -> Unit,
    onBody: () -> Unit,
    onEmotional: () -> Unit
) {
    ExploreHubScaffold(
        title = "Trends",
        subtitle = "Your recorded health history, in one place",
        onBack = onBack
    ) {
        Text(
            "Explore your data",
            color = superhumanTextPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Open a category to review its available measurements and history. Trends are based on your saved data, not example patterns.",
            color = superhumanTextMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        ExploreHubDestination("Sleep", "Sleep quality, duration and stages",
            R.drawable.tabler_moon, onSleep)
        ExploreHubDestination("Exercise", "Cardio, strength and training history",
            R.drawable.tabler_barbell, onExercise)
        ExploreHubDestination("Vitals", "Heart rate and recorded measurements",
            R.drawable.tabler_activity_heartbeat, onVitals)
        ExploreHubDestination("Body", "Body composition and body measurements",
            R.drawable.tabler_user, onBody)
        ExploreHubDestination("Emotional", "Mood and emotional check-ins",
            R.drawable.tabler_chart_line, onEmotional)
    }
}

@Composable
internal fun ExploreInterventionsPage(
    onBack: () -> Unit,
    onMindfulness: () -> Unit,
    onBreathwork: () -> Unit
) {
    ExploreHubScaffold(
        title = "Interventions",
        subtitle = "Practices to support your wellbeing",
        onBack = onBack
    ) {
        Text("Choose a practice", color = superhumanTextPrimary,
            fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Start a guided session or log a mindfulness practice. Your existing tools and saved sessions are retained.",
            color = superhumanTextMuted, fontSize = 12.sp, lineHeight = 18.sp
        )
        ExploreHubDestination(
            title = "Mindfulness",
            subtitle = "Meditation, body scans and reflection",
            icon = R.drawable.tabler_user,
            onClick = onMindfulness
        )
        ExploreHubDestination(
            title = "Breathwork",
            subtitle = "Open the existing guided breathing routine",
            icon = R.drawable.tabler_activity_heartbeat,
            onClick = onBreathwork
        )
        Text(
            "Practise at a comfortable pace. Stop any breathing exercise if you feel dizzy or unwell.",
            color = superhumanTextMuted, fontSize = 11.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ExploreHubScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .background(superhumanBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SuperhumanLayout.pageHorizontal)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SuperhumanModuleBackButton(onBack)
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, color = superhumanTextPrimary, fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = superhumanTextMuted, fontSize = 11.sp,
                    lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.height(7.dp))
        content()
    }
}

@Composable
private fun ExploreHubDestination(
    title: String,
    subtitle: String,
    icon: Int,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val accent = if (SuperhumanAppearance.darkMode) Color(0xFFB6A0EE) else Color(0xFF7662B9)
    Row(
        Modifier.fillMaxWidth()
            .background(superhumanSurfaceElevated, shape)
            .superhumanClickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(46.dp).background(superhumanSurfaceSoft, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = accent,
                modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = superhumanTextPrimary,
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = superhumanTextMuted,
                fontSize = 11.sp, lineHeight = 16.sp)
        }
        Icon(painterResource(R.drawable.tabler_chevron_right),
            contentDescription = null, tint = superhumanTextMuted,
            modifier = Modifier.size(20.dp))
    }
}
