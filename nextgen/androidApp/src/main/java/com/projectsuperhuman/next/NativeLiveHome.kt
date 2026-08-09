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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val HomeNavy = Color(0xFF082D66)
private val HomeBlue = Color(0xFF0D6CB4)
private val HomeInk = Color(0xFF0B1F35)
private val HomeMuted = Color(0xFF64748B)
private val HomeGreen = Color(0xFF168A78)
private val HomePurple = Color(0xFF6547C9)
private val HomeOrange = Color(0xFFD97706)
private val HomeRed = Color(0xFFCA3A3A)
private val HomeBg = Color(0xFFF6F9FC)

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
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        snapshot = loadNativeHomeSnapshot()
    }

    Column(
        Modifier.fillMaxSize().background(HomeBg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HomeOverview(snapshot, openClinical)

        Text("TODAY", color = HomeMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LiveMetricCard(
                "Sleep",
                snapshot.sleepScore?.let { "$it / 100" } ?: "—",
                snapshot.sleepMinutes?.let { formatMinutesHome(it) } ?: "No synced sleep",
                Color(0xFFF3F0FB), HomePurple, Modifier.weight(1f), openSleep
            )
            LiveMetricCard(
                "Water",
                "%.2f L".format(snapshot.waterLitres),
                "Logged today",
                Color(0xFFEAF4FC), HomeBlue, Modifier.weight(1f), openNutrition
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LiveMetricCard(
                "Nutrition",
                "${snapshot.caloriesToday} kcal",
                "${snapshot.proteinToday} g protein",
                Color(0xFFECF8F1), HomeGreen, Modifier.weight(1f), openNutrition
            )
            LiveMetricCard(
                "Training",
                if (snapshot.workoutsToday > 0) "${snapshot.workoutsToday} workout${if (snapshot.workoutsToday == 1) "" else "s"}" else "Ready",
                if (snapshot.workoutVolumeToday > 0) "${snapshot.workoutVolumeToday} kg-reps" else "No workout today",
                Color(0xFFFFF4E8), HomeOrange, Modifier.weight(1f), openExercise
            )
        }

        Text("HEALTH HUB", color = HomeMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        LiveHubRow(
            "Clinical",
            if (snapshot.clinicalMarkers == 0) "No native markers yet" else "${snapshot.clinicalMarkers} markers · ${snapshot.clinicalAlerts} alert${if (snapshot.clinicalAlerts == 1) "" else "s"}",
            "CL", Color(0xFFEAF3FF), if (snapshot.clinicalAlerts > 0) HomeRed else HomeBlue, openClinical
        )
        LiveHubRow(
            "Body & Progress",
            snapshot.bodyWeightKg?.let { "Latest weight %.1f kg".format(it) } ?: "Add weight & measurements",
            "BP", Color(0xFFEDF8F5), HomeGreen, openBody
        )
        LiveHubRow(
            "Blood Pressure",
            "Deferred · existing tools remain available",
            "HR", Color(0xFFFFF0F0), HomeRed, openBloodPressure
        )
        LiveHubRow(
            "Mindfulness",
            if (snapshot.mindfulnessMinutesToday > 0) "${snapshot.mindfulnessMinutesToday} min today" else "Breathing, meditation & stress",
            "MN", Color(0xFFF4F1FC), HomePurple, openMindfulness
        )

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Live dashboard", color = HomeNavy, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Text("All values above are calculated from the shared native database, not placeholder counters.", color = HomeMuted, fontSize = 9.sp, lineHeight = 14.sp)
                }
                Text("REFRESH", color = HomeBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable { refreshKey++ }.padding(8.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
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
        ?: NativeDataHub.latest("water_total_l")?.takeIf { it.timestampEpochMs >= start }?.value
        ?: 0.0
    val workouts = NativeDataHub.between("workout_session", start, now)
    val volumes = NativeDataHub.between("workout_volume", start, now)
    val mindfulness = NativeDataHub.latestForDomain(HealthDomain.MINDFULNESS)

    fun sleepMetric(name: String) = sleep.firstOrNull { it.metric == name }?.value
    fun bodyMetric(name: String) = body.firstOrNull { it.metric == name }?.value

    val sleepScore = sleepMetric("sleep_score")?.roundToInt()
    val sleepMinutes = sleepMetric("sleep_total_minutes")?.roundToInt()
    val clinicalAlerts = clinical.count { it.metadata["status"] == "LOW" || it.metadata["status"] == "HIGH" }
    val clinicalWithRange = clinical.count { it.metadata["status"] == "LOW" || it.metadata["status"] == "HIGH" || it.metadata["status"] == "NORMAL" }

    val mindfulnessToday = mindfulness
        .filter { it.timestampEpochMs >= start }
        .filter { it.metric.contains("minute", ignoreCase = true) || it.unit == "min" }
        .sumOf { it.value }.roundToInt()

    val signals = mutableListOf<Int>()
    sleepScore?.let { signals += it.coerceIn(0, 100) }
    if (water > 0) signals += ((water / 2.5) * 100.0).roundToInt().coerceIn(0, 100)
    if (clinicalWithRange > 0) signals += (((clinicalWithRange - clinicalAlerts).toDouble() / clinicalWithRange) * 100.0).roundToInt().coerceIn(0, 100)
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

@Composable
private fun HomeOverview(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Color(0xFFE7F2FF), Color.White)), RoundedCornerShape(26.dp)
        ).clickable(onClick = onClick).padding(20.dp)
    ) {
        Text("SUPERHUMAN OVERVIEW", color = HomeBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Health overview", color = HomeInk, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text(
                    snapshot.overviewScore?.let { "Live score from ${snapshot.signalsUsed} available signals" } ?: "Add more data to calculate a daily score",
                    color = HomeMuted, fontSize = 11.sp
                )
            }
            Box(Modifier.width(70.dp).height(64.dp).background(Color.White, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(snapshot.overviewScore?.toString() ?: "—", color = HomeNavy, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("SCORE", color = HomeMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            HomePill("Clinical", if (snapshot.clinicalAlerts > 0) "${snapshot.clinicalAlerts} alert" else if (snapshot.clinicalMarkers > 0) "Clear" else "No data", Modifier.weight(1f))
            HomePill("Sleep", snapshot.sleepScore?.let { "$it/100" } ?: "No data", Modifier.weight(1f))
            HomePill("Body", snapshot.bodyWeightKg?.let { "%.1f kg".format(it) } ?: "No data", Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomePill(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 10.dp)) {
        Text(label, color = HomeMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(value, color = HomeInk, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun LiveMetricCard(title: String, value: String, subtitle: String, background: Color, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.background(background, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(15.dp)) {
        Text(title.uppercase(), color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
        Spacer(Modifier.height(9.dp))
        Text(value, color = HomeInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = HomeMuted, fontSize = 9.sp)
    }
}

@Composable
private fun LiveHubRow(title: String, subtitle: String, initials: String, background: Color, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(19.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(42.dp).height(42.dp).background(background, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Text(initials, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = HomeInk, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = HomeMuted, fontSize = 9.sp)
        }
        Text("›", color = accent, fontSize = 24.sp)
    }
}

private fun formatMinutesHome(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
