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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.projectsuperhuman.next.core.HealthDomain
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Home orchestration only. Visual language lives in dedicated Home component files so future
 * sessions can change dashboard visuals without disturbing repository/data calculations.
 */
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
    HYDRATION("hydration"),
    CLINICAL("clinical"),
    EXERCISE("exercise"),
    BODY("body"),
    SLEEP("sleep"),
    NUTRITION("nutrition"),
    QUICK_LINKS("quick_links"),
    BLOOD_PRESSURE("blood_pressure")
}

private val defaultHomeTileOrder = listOf(
    HomeTile.HYDRATION,
    HomeTile.CLINICAL,
    HomeTile.EXERCISE,
    HomeTile.BODY,
    HomeTile.SLEEP,
    HomeTile.NUTRITION,
    HomeTile.QUICK_LINKS,
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
    topContent: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(NativeHomeSnapshot()) }
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
            // The Today hero stays fixed at the top; only module tiles can be reordered.
            LegacyHomeHero(snapshot)

            tileOrder.forEach { tile ->
                key(tile) {
                    ReorderableHomeTile(
                        tile = tile,
                        order = tileOrder,
                        onOrderChanged = { saveHomeTileOrder(context, tileOrder) }
                    ) {
                        when (tile) {
                            HomeTile.HYDRATION -> PremiumHomeHydrationTile(snapshot, openHydration)
                            HomeTile.CLINICAL -> LegacyClinicalCard(snapshot, openClinical)
                            HomeTile.EXERCISE -> LegacyTrainingCard(snapshot, openExercise)
                            HomeTile.BODY -> LegacyBodyCard(snapshot, Modifier.fillMaxWidth(), openBody)
                            HomeTile.SLEEP -> HomeSleepInsightTile(snapshot, openSleep)
                            HomeTile.NUTRITION -> LegacyNutritionCard(snapshot, openNutrition)
                            HomeTile.QUICK_LINKS -> LegacyHomeLinks(
                                openMindfulness = openMindfulness,
                                openExercise = openExercise,
                                openInsights = openClinical
                            )
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
    order: MutableList<HomeTile>,
    onOrderChanged: () -> Unit,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var tileHeightPx by remember { mutableStateOf(0) }

    Box(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { tileHeightPx = it.size.height }
            .zIndex(if (dragging) 10f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = if (dragging) 1.018f else 1f
                scaleY = if (dragging) 1.018f else 1f
                alpha = if (dragging) 0.96f else 1f
            }
            .pointerInput(tile) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        dragOffsetY = 0f
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragCancel = {
                        dragging = false
                        dragOffsetY = 0f
                    },
                    onDragEnd = {
                        dragging = false
                        dragOffsetY = 0f
                        onOrderChanged()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY += dragAmount.y

                        val currentIndex = order.indexOf(tile)
                        if (currentIndex < 0 || tileHeightPx <= 0) return@detectDragGesturesAfterLongPress

                        // A little under half a tile gives deliberate movement without feeling sticky.
                        val threshold = tileHeightPx * 0.42f
                        if (abs(dragOffsetY) < threshold) return@detectDragGesturesAfterLongPress

                        val targetIndex = when {
                            dragOffsetY > 0f && currentIndex < order.lastIndex -> currentIndex + 1
                            dragOffsetY < 0f && currentIndex > 0 -> currentIndex - 1
                            else -> currentIndex
                        }

                        if (targetIndex != currentIndex) {
                            order.removeAt(currentIndex)
                            order.add(targetIndex, tile)
                            dragOffsetY = 0f
                            onOrderChanged()
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

    // If a future release adds a tile, preserve the user's order and append the new tile safely.
    return (parsed + defaultHomeTileOrder.filterNot(parsed::contains)).ifEmpty { defaultHomeTileOrder }
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

    val sleep = NativeDataHub.latestForDomain(HealthDomain.SLEEP)
    val body = NativeDataHub.latestForDomain(HealthDomain.BODY)
    val clinical = NativeDataHub.latestForDomain(HealthDomain.CLINICAL)
    val kcal = NativeDataHub.between("food_kcal", start, now).sumOf { it.value }.roundToInt()
    val protein = NativeDataHub.between("food_protein", start, now).sumOf { it.value }.roundToInt()
    val waterGoalMl = NativeDataHub.latest("hydration_goal_ml")?.value?.roundToInt()?.coerceIn(1500, 6000) ?: 3600
    val waterEvents = NativeDataHub.between("water_intake_ml", start, now)
    val rawWaterLitres = if (waterEvents.isNotEmpty()) {
        waterEvents.sumOf { it.value } / 1000.0
    } else {
        NativeDataHub.between("water_total_l", start, now).maxByOrNull { it.timestampEpochMs }?.value
            ?: NativeDataHub.latest("water_total_l")?.takeIf { it.timestampEpochMs >= start }?.value
            ?: 0.0
    }
    val water = rawWaterLitres.coerceIn(0.0, waterGoalMl / 1000.0)

    val workouts = NativeDataHub.between("workout_session", start, now)
    val sets = NativeDataHub.between("exercise_set", start, now)
    val volumes = NativeDataHub.between("workout_volume", start, now)
    val mindfulness = NativeDataHub.latestForDomain(HealthDomain.MINDFULNESS)
    val bodyHistory = NativeDataHub.between("body_weight_kg", thirtyDaysAgo, now).sortedBy { it.timestampEpochMs }

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
