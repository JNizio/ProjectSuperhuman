package com.projectsuperhuman.next

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.projectsuperhuman.next.core.HealthDomain
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Home data shared by the dashboard's major cards. */
data class NativeHomeSnapshot(
    val sleepScore: Int? = null,
    val sleepMinutes: Int? = null,
    val sleepStartLabel: String? = null,
    val sleepEndLabel: String? = null,
    val waterLitres: Double = 0.0,
    val waterGoalMl: Int = 3600,
    val caloriesToday: Int = 0,
    val proteinToday: Int = 0,
    val workoutsToday: Int = 0,
    val workoutSetsToday: Int = 0,
    val workoutVolumeToday: Int = 0,
    val bodyWeightKg: Double? = null,
    val bodyWeightChange30d: Double? = null,
    val bodyWeightTrend: List<Double> = emptyList(),
    val clinicalMarkers: Int = 0,
    val clinicalAlerts: Int = 0,
    val mindfulnessMinutesToday: Int = 0
)

private enum class HomeTile(val storageKey: String) {
    EMOTIONAL("emotional"),
    ENVIRONMENT("environment"),
    HYDRATION("hydration"),
    CLINICAL("clinical"),
    EXERCISE("exercise"),
    BODY("body"),
    MINDFULNESS("mindfulness"),
    SLEEP("sleep"),
    NUTRITION("nutrition"),
    BLOOD_PRESSURE("blood_pressure")
}

private val defaultHomeTileOrder = listOf(
    HomeTile.EMOTIONAL,
    HomeTile.ENVIRONMENT,
    HomeTile.HYDRATION,
    HomeTile.CLINICAL,
    HomeTile.EXERCISE,
    HomeTile.BODY,
    HomeTile.MINDFULNESS,
    HomeTile.SLEEP,
    HomeTile.NUTRITION,
    HomeTile.BLOOD_PRESSURE
)

private const val HOME_PREFS = "project_superhuman_home"
private const val HOME_TILE_ORDER_KEY = "tile_order_v1"

@Composable
internal fun NativeLiveHome(
    openClinical: () -> Unit,
    openBody: () -> Unit,
    openSleep: () -> Unit,
    openBloodPressure: () -> Unit,
    openHydration: () -> Unit,
    openNutrition: () -> Unit,
    openExercise: () -> Unit,
    openMindfulness: () -> Unit,
    openEnvironment: () -> Unit,
    openEmotional: () -> Unit,
    openMiniMetric: (HomeMiniMetric) -> Unit,
    topContent: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(NativeHomeSnapshot()) }
    val emotionalCurrent by produceState<EmotionalPresentationSnapshot?>(null) {
        val rows = NativeDomainData.forDomain(HealthDomain.EMOTIONAL).latestState()
        value = EmotionalPresentationContract.fromCanonicalValues(rows, rows.latestEmotionalLabel())
    }
    val tileOrder = remember {
        mutableStateListOf<HomeTile>().apply { addAll(loadHomeTileOrder(context)) }
    }

    LaunchedEffect(Unit) { snapshot = loadNativeHomeSnapshot() }

    Column(
        Modifier.fillMaxSize()
            .background(Color(0xFFF8FBFD))
            .verticalScroll(rememberScrollState())
    ) {
        topContent()
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 17.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HomeDateStrip()
            HomeMiniMetricsGrid(openMiniMetric)

            tileOrder.forEach { tile ->
                key(tile) {
                    ReorderableHomeTile(
                        tile = tile,
                        onMove = { direction ->
                            val from = tileOrder.indexOf(tile)
                            val to = (from + direction).coerceIn(0, tileOrder.lastIndex)
                            if (from >= 0 && to != from) {
                                tileOrder.removeAt(from)
                                tileOrder.add(to, tile)
                                saveHomeTileOrder(context, tileOrder)
                                true
                            } else false
                        }
                    ) {
                        when (tile) {
                            HomeTile.EMOTIONAL -> HomeEmotionalTile(emotionalCurrent, openEmotional)
                            HomeTile.ENVIRONMENT -> HomeEnvironmentalTile(onClick = openEnvironment)
                            HomeTile.HYDRATION -> PremiumHomeHydrationTile(snapshot, openHydration)
                            HomeTile.CLINICAL -> LegacyClinicalCard(snapshot, openClinical)
                            HomeTile.EXERCISE -> LegacyTrainingCard(snapshot, openExercise)
                            HomeTile.BODY -> LegacyBodyCard(snapshot, Modifier.fillMaxWidth(), openBody)
                            HomeTile.MINDFULNESS -> HomeMindfulnessBreathworkRow(snapshot, openMindfulness)
                            HomeTile.SLEEP -> HomeSleepInsightTile(snapshot, openSleep)
                            HomeTile.NUTRITION -> LegacyNutritionCard(snapshot, openNutrition)
                            HomeTile.BLOOD_PRESSURE -> LegacyBloodPressureLink(openBloodPressure)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReorderableHomeTile(
    tile: HomeTile,
    onMove: (direction: Int) -> Boolean,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val swapThreshold = with(density) { 76.dp.toPx() }
    var dragging by remember(tile) { mutableStateOf(false) }
    var dragY by remember(tile) { mutableFloatStateOf(0f) }

    Box(
        Modifier.fillMaxWidth()
            .zIndex(if (dragging) 20f else 0f)
            .graphicsLayer {
                translationY = if (dragging) dragY.coerceIn(-swapThreshold, swapThreshold) else 0f
                scaleX = if (dragging) 1.02f else 1f
                scaleY = if (dragging) 1.02f else 1f
                alpha = if (dragging) .97f else 1f
                shadowElevation = if (dragging) 12.dp.toPx() else 0f
            }
            .pointerInput(tile) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        dragY = 0f
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragCancel = {
                        dragging = false
                        dragY = 0f
                    },
                    onDragEnd = {
                        dragging = false
                        dragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragY += dragAmount.y
                        if (abs(dragY) >= swapThreshold) {
                            val direction = if (dragY > 0f) 1 else -1
                            if (onMove(direction)) {
                                dragY -= direction * swapThreshold
                            } else {
                                dragY = direction * swapThreshold
                            }
                        }
                    }
                )
            }
    ) {
        content()
    }
}

private fun loadHomeTileOrder(context: Context): List<HomeTile> {
    val saved = context.getSharedPreferences(HOME_PREFS, Context.MODE_PRIVATE)
        .getString(HOME_TILE_ORDER_KEY, null)
        .orEmpty()

    val parsed = saved.split(',')
        .mapNotNull { key -> HomeTile.entries.firstOrNull { it.storageKey == key.trim() } }
        .distinct()

    val newcomers = listOf(HomeTile.EMOTIONAL, HomeTile.ENVIRONMENT).filterNot(parsed::contains)
    val rest = parsed + defaultHomeTileOrder.filterNot { it in parsed || it in newcomers }
    return (newcomers + rest).ifEmpty { defaultHomeTileOrder }
}

private fun saveHomeTileOrder(context: Context, order: List<HomeTile>) {
    context.getSharedPreferences(HOME_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(HOME_TILE_ORDER_KEY, order.joinToString(",") { it.storageKey })
        .apply()
}

private suspend fun loadNativeHomeSnapshot(): NativeHomeSnapshot {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val now = System.currentTimeMillis()
    val thirtyDaysAgo = today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()

    val sleepData = NativeDomainData.forDomain(HealthDomain.SLEEP)
    val hydrationData = NativeDomainData.forDomain(HealthDomain.HYDRATION)
    val nutritionData = NativeDomainData.forDomain(HealthDomain.NUTRITION)
    val exerciseData = NativeDomainData.forDomain(HealthDomain.EXERCISE)
    val bodyData = NativeDomainData.forDomain(HealthDomain.BODY)
    val clinicalData = NativeDomainData.forDomain(HealthDomain.CLINICAL)
    val mindfulnessData = NativeDomainData.forDomain(HealthDomain.MINDFULNESS)

    val sleep = sleepData.latestState()
    val body = bodyData.latestState()
    val clinical = clinicalData.latestState()
    val kcal = nutritionData.between("food_kcal", start, now).sumOf { it.value }.roundToInt()
    val protein = nutritionData.between("food_protein", start, now).sumOf { it.value }.roundToInt()
    val waterGoalMl = hydrationData.latest("hydration_goal_ml")?.value?.roundToInt()?.coerceIn(1500, 6000) ?: 3600
    val waterEvents = hydrationData.between("water_intake_ml", start, now)
    val rawWaterLitres = if (waterEvents.isNotEmpty()) {
        waterEvents.sumOf { it.value } / 1000.0
    } else {
        hydrationData.between("water_total_l", start, now).maxByOrNull { it.timestampEpochMs }?.value
            ?: hydrationData.latest("water_total_l")?.takeIf { it.timestampEpochMs >= start }?.value
            ?: 0.0
    }
    val water = rawWaterLitres.coerceIn(0.0, waterGoalMl / 1000.0)

    val workouts = exerciseData.between("workout_session", start, now)
    val sets = exerciseData.between("exercise_set", start, now)
    val volumes = exerciseData.between("workout_volume", start, now)
    val mindfulness = mindfulnessData.latestState()
    val bodyHistory = bodyData.between("body_weight_kg", thirtyDaysAgo, now).sortedBy { it.timestampEpochMs }

    fun sleepMetric(name: String) = sleep.firstOrNull { it.metric == name }?.value
    fun sleepText(name: String) = sleep.firstOrNull { it.metric == name }?.metadata?.get("display")
    fun bodyMetric(name: String) = body.firstOrNull { it.metric == name }?.value

    val clinicalAlerts = clinical.count { it.metadata["status"] == "LOW" || it.metadata["status"] == "HIGH" }
    val mindfulnessToday = mindfulness
        .filter { it.timestampEpochMs >= start }
        .filter { it.metric.contains("minute", true) || it.unit == "min" }
        .sumOf { it.value }.roundToInt()

    val trend = bodyHistory.map { it.value }.takeLast(12)
    val weightChange = if (bodyHistory.size >= 2) bodyHistory.last().value - bodyHistory.first().value else null

    return NativeHomeSnapshot(
        sleepScore = sleepMetric("sleep_score")?.roundToInt(),
        sleepMinutes = sleepMetric("sleep_total_minutes")?.roundToInt(),
        sleepStartLabel = sleepText("sleep_total_minutes"),
        sleepEndLabel = null,
        waterLitres = water,
        waterGoalMl = waterGoalMl,
        caloriesToday = kcal,
        proteinToday = protein,
        workoutsToday = workouts.size,
        workoutSetsToday = sets.size,
        workoutVolumeToday = volumes.sumOf { it.value }.roundToInt(),
        bodyWeightKg = bodyMetric("body_weight_kg"),
        bodyWeightChange30d = weightChange,
        bodyWeightTrend = trend,
        clinicalMarkers = clinical.size,
        clinicalAlerts = clinicalAlerts,
        mindfulnessMinutesToday = mindfulnessToday
    )
}

internal fun formatMinutesHome(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
