package com.projectsuperhuman.next

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val HomeNavy = Color(0xFF123D70)
private val HomeBlue = Color(0xFF0D6CB4)
private val HomeCyan = Color(0xFF20A7C4)
private val HomeInk = Color(0xFF16334E)
private val HomeMuted = Color(0xFF748294)
private val HomeGreen = Color(0xFF5CB79E)
private val HomeRed = Color(0xFFD96767)
private val HomeCard = Color(0xFFFCFDFE)
private val HomeBg = Color(0xFFF8FBFD)

data class NativeHomeSnapshot(
    val sleepScore: Int? = null,
    val sleepMinutes: Int? = null,
    val waterLitres: Double = 0.0,
    val caloriesToday: Int = 0,
    val proteinToday: Int = 0,
    val workoutsToday: Int = 0,
    val workoutVolumeToday: Int = 0,
    val bodyWeightKg: Double? = null,
    val clinicalMarkers: Int = 0,
    val clinicalAlerts: Int = 0,
    val mindfulnessMinutesToday: Int = 0,
    val overviewScore: Int? = null,
    val signalsUsed: Int = 0
)

@Composable
internal fun NativeLiveHome(
    openClinical: () -> Unit,
    openBody: () -> Unit,
    openSleep: () -> Unit,
    openBloodPressure: () -> Unit,
    openNutrition: () -> Unit,
    openExercise: () -> Unit,
    openMindfulness: () -> Unit
) {
    var snapshot by remember { mutableStateOf(NativeHomeSnapshot()) }

    LaunchedEffect(Unit) { snapshot = loadNativeHomeSnapshot() }

    Column(
        Modifier.fillMaxSize().background(HomeBg).verticalScroll(rememberScrollState()).padding(horizontal = 17.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LegacyHero(snapshot)
        HydrationCard(snapshot, openNutrition)
        ClinicalCard(snapshot, openClinical)
        TrainingCard(snapshot, openExercise)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CompactCard(
                modifier = Modifier.fillMaxWidth(.49f),
                eyebrow = "BODY",
                value = snapshot.bodyWeightKg?.let { "%.1f kg".format(it) } ?: "—",
                subtitle = "Progress & measurements",
                accent = Color(0xFF7B6AC9),
                onClick = openBody
            )
            CompactCard(
                modifier = Modifier.fillMaxWidth(),
                eyebrow = "SLEEP",
                value = snapshot.sleepMinutes?.let { formatMinutesHome(it) } ?: "—",
                subtitle = snapshot.sleepScore?.let { "Sleep score $it" } ?: "Sync sleep data",
                accent = HomeBlue,
                onClick = openSleep
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CompactCard(
                modifier = Modifier.fillMaxWidth(.49f),
                eyebrow = "NUTRITION",
                value = "${snapshot.caloriesToday} kcal",
                subtitle = "${snapshot.proteinToday} g protein",
                accent = HomeGreen,
                onClick = openNutrition
            )
            CompactCard(
                modifier = Modifier.fillMaxWidth(),
                eyebrow = "MINDFULNESS",
                value = if (snapshot.mindfulnessMinutesToday > 0) "${snapshot.mindfulnessMinutesToday} min" else "Ready",
                subtitle = "Breathing & meditation",
                accent = Color(0xFF8C72C9),
                onClick = openMindfulness
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Blood pressure tools",
            color = HomeMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = openBloodPressure).padding(vertical = 12.dp)
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun LegacyHero(snapshot: NativeHomeSnapshot) {
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)).uppercase()
    val hydrationPct = ((snapshot.waterLitres / 3.6) * 100).roundToInt().coerceIn(0, 100)
    val target = when {
        hydrationPct < 50 -> "Today’s clearest live\ntarget"
        snapshot.sleepMinutes == null -> "Connect last night’s\nsleep"
        snapshot.workoutsToday == 0 -> "Ready for today’s\ntraining"
        else -> "Keep the day\nmoving"
    }
    val guidance = when {
        hydrationPct < 50 -> "Hydration is at $hydrationPct%. Small, regular top-ups will move this first."
        snapshot.sleepMinutes == null -> "Sleep data is missing. Sync your wearable to complete today’s recovery picture."
        snapshot.workoutsToday == 0 -> "Recovery data looks ready. Training is the clearest next performance signal."
        else -> "Your core signals are moving. Keep logging the things that change today."
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp)).background(
            Brush.linearGradient(listOf(Color(0xFF0864A7), Color(0xFF10A4C2)))
        ).padding(horizontal = 20.dp, vertical = 19.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("TODAY • $today", color = Color.White.copy(alpha = .78f), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Box(Modifier.width(42.dp).height(42.dp).background(Color.White.copy(alpha = .12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text("PS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(target, color = Color.White, fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f))) {
            Box(Modifier.fillMaxWidth((hydrationPct / 100f).coerceAtLeast(.01f)).height(5.dp).background(Color.White))
        }
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HeroSignal("HYDRATION", "$hydrationPct%")
            HeroSignal("SLEEP", snapshot.sleepMinutes?.let { formatMinutesHome(it) } ?: "—")
            HeroSignal("TRAINING", if (snapshot.workoutsToday > 0) "Done" else "Ready")
            HeroSignal("CLINICAL", "${snapshot.clinicalMarkers} markers")
        }
        Spacer(Modifier.height(17.dp))
        Text(guidance, color = Color.White.copy(alpha = .88f), fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun HeroSignal(label: String, value: String) {
    Column(Modifier.width(72.dp)) {
        Text(label, color = Color.White.copy(alpha = .64f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp, maxLines = 1)
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun HydrationCard(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val ml = (snapshot.waterLitres * 1000).roundToInt()
    val pct = ((snapshot.waterLitres / 3.6) * 100).roundToInt().coerceIn(0, 100)
    Row(
        Modifier.fillMaxWidth().background(HomeCard, RoundedCornerShape(25.dp)).border(1.dp, Color(0xFFE4EBF0), RoundedCornerShape(25.dp)).clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(104.dp)) {
            Text("HYDRATION", color = HomeMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(84.dp).height(84.dp).border(9.dp, Color(0xFFDDECF3), CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$pct%", color = HomeNavy, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("today", color = HomeMuted, fontSize = 8.sp)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("$ml ml", color = HomeNavy, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text("3,600 ml daily target", color = HomeMuted, fontSize = 10.sp)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.background(Color(0xFFE5F3FA), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text("Open tracker", color = HomeBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ClinicalCard(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val normal = (snapshot.clinicalMarkers - snapshot.clinicalAlerts).coerceAtLeast(0)
    Column(
        Modifier.fillMaxWidth().background(HomeCard, RoundedCornerShape(25.dp)).border(1.dp, Color(0xFFE4EBF0), RoundedCornerShape(25.dp)).clickable(onClick = onClick).padding(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("CLINICAL", color = HomeMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            Text("›", color = Color(0xFF8CA6B5), fontSize = 23.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(13.dp).height(13.dp).background(if (snapshot.clinicalAlerts > 0) HomeRed else HomeGreen, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("${snapshot.clinicalMarkers} markers", color = HomeNavy, fontSize = 21.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(15.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            repeat(14) { index ->
                val accent = when {
                    snapshot.clinicalMarkers == 0 -> Color(0xFFC6D4DE)
                    index < snapshot.clinicalAlerts.coerceAtMost(4) -> HomeRed
                    index % 5 == 0 -> HomeGreen
                    else -> Color(0xFFC6D4DE)
                }
                Box(Modifier.width(11.dp).height(if (accent == Color(0xFFC6D4DE)) 11.dp else 22.dp).background(accent, RoundedCornerShape(7.dp)))
            }
        }
        Spacer(Modifier.height(13.dp))
        Text("$normal normal · ${snapshot.clinicalAlerts} outside range", color = HomeMuted, fontSize = 10.sp)
    }
}

@Composable
private fun TrainingCard(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Color(0xFFFFF7F1), Color(0xFFF7F5F0))), RoundedCornerShape(25.dp)
        ).border(1.dp, Color(0xFFE9E5DF), RoundedCornerShape(25.dp)).clickable(onClick = onClick).padding(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("TRAINING", color = HomeMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            Text("›", color = Color(0xFF8CA6B5), fontSize = 23.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(if (snapshot.workoutsToday > 0) "Training logged" else "Ready when you are", color = Color(0xFF6C4132), fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(if (snapshot.workoutsToday > 0) "${snapshot.workoutsToday} workout today" else "No workout logged today", color = HomeMuted, fontSize = 10.sp)
        Spacer(Modifier.height(17.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(29.dp)) {
            TrainingStat(snapshot.workoutsToday.toString(), "workouts")
            TrainingStat(if (snapshot.workoutsToday > 0) "•" else "0", "sets")
            TrainingStat(snapshot.workoutVolumeToday.toString(), "volume")
        }
    }
}

@Composable
private fun TrainingStat(value: String, label: String) {
    Column {
        Text(value, color = Color(0xFF704431), fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text(label, color = HomeMuted, fontSize = 9.sp)
    }
}

@Composable
private fun CompactCard(modifier: Modifier, eyebrow: String, value: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Column(
        modifier.height(138.dp).background(HomeCard, RoundedCornerShape(23.dp)).border(1.dp, Color(0xFFE4EBF0), RoundedCornerShape(23.dp)).clickable(onClick = onClick).padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(eyebrow, color = HomeMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("›", color = Color(0xFF9AAEBB), fontSize = 18.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(value, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = HomeMuted, fontSize = 9.sp, lineHeight = 12.sp)
    }
}

private suspend fun loadNativeHomeSnapshot(): NativeHomeSnapshot {
    val zone = ZoneId.systemDefault()
    val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    val now = System.currentTimeMillis()
    val sleep = NativeDataHub.latestForDomain(HealthDomain.SLEEP)
    val body = NativeDataHub.latestForDomain(HealthDomain.BODY)
    val clinical = NativeDataHub.latestForDomain(HealthDomain.CLINICAL)
    val kcal = NativeDataHub.between("food_kcal", start, now).sumOf { it.value }.roundToInt()
    val protein = NativeDataHub.between("food_protein", start, now).sumOf { it.value }.roundToInt()
    val water = NativeDataHub.between("water_total_l", start, now).maxByOrNull { it.timestampEpochMs }?.value
        ?: NativeDataHub.latest("water_total_l")?.takeIf { it.timestampEpochMs >= start }?.value ?: 0.0
    val workouts = NativeDataHub.between("workout_session", start, now)
    val volumes = NativeDataHub.between("workout_volume", start, now)
    val mindfulness = NativeDataHub.latestForDomain(HealthDomain.MINDFULNESS)
    fun sleepMetric(name: String) = sleep.firstOrNull { it.metric == name }?.value
    fun bodyMetric(name: String) = body.firstOrNull { it.metric == name }?.value
    val sleepScore = sleepMetric("sleep_score")?.roundToInt()
    val sleepMinutes = sleepMetric("sleep_total_minutes")?.roundToInt()
    val clinicalAlerts = clinical.count { it.metadata["status"] == "LOW" || it.metadata["status"] == "HIGH" }
    val clinicalWithRange = clinical.count { it.metadata["status"] in listOf("LOW", "HIGH", "NORMAL") }
    val mindfulnessToday = mindfulness.filter { it.timestampEpochMs >= start }.filter { it.metric.contains("minute", true) || it.unit == "min" }.sumOf { it.value }.roundToInt()
    val signals = mutableListOf<Int>()
    sleepScore?.let { signals += it.coerceIn(0, 100) }
    if (water > 0) signals += ((water / 3.6) * 100).roundToInt().coerceIn(0, 100)
    if (clinicalWithRange > 0) signals += (((clinicalWithRange - clinicalAlerts).toDouble() / clinicalWithRange) * 100).roundToInt().coerceIn(0, 100)
    if (workouts.isNotEmpty()) signals += 100
    return NativeHomeSnapshot(
        sleepScore = sleepScore,
        sleepMinutes = sleepMinutes,
        waterLitres = water,
        caloriesToday = kcal,
        proteinToday = protein,
        workoutsToday = workouts.size,
        workoutVolumeToday = volumes.sumOf { it.value }.roundToInt(),
        bodyWeightKg = bodyMetric("body_weight_kg"),
        clinicalMarkers = clinical.size,
        clinicalAlerts = clinicalAlerts,
        mindfulnessMinutesToday = mindfulnessToday,
        overviewScore = signals.takeIf { it.size >= 2 }?.average()?.roundToInt(),
        signalsUsed = signals.size
    )
}

private fun formatMinutesHome(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
