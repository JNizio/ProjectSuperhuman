package com.projectsuperhuman.next

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.projectsuperhuman.next.core.HealthDomain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
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
    val nutritionEntriesToday: Int = 0,
    val workoutsToday: Int = 0,
    val strengthWorkoutsToday: Int = 0,
    val cardioWorkoutsToday: Int = 0,
    val workoutSetsToday: Int = 0,
    val workoutVolumeToday: Int = 0,
    val trainingMinutesToday: Int = 0,
    val latestStrengthWorkoutName: String? = null,
    val cardioDistanceTodayKm: Double = 0.0,
    val cardioMinutesToday: Int = 0,
    val latestCardioActivityName: String? = null,
    val bodyWeightKg: Double? = null,
    val bodyWeightChange30d: Double? = null,
    val bodyWeightTrend: List<Double> = emptyList(),
    val clinicalMarkers: Int = 0,
    val clinicalAlerts: Int = 0,
    val mindfulnessMinutesToday: Int = 0
)

private enum class HomeTile(val storageKey: String) {
    INSIGHTS("insights"),
    EMOTIONAL("emotional"),
    ENVIRONMENT("environment"),
    EXPERIMENTS("experiments"),
    HYDRATION("hydration"),
    CLINICAL("clinical"),
    EXERCISE("exercise"),
    MINDFULNESS("mindfulness"),
    SLEEP("sleep"),
    NUTRITION("nutrition"),
    // Keep the previous storage key so existing dashboard ordering survives the tile upgrade.
    VITALS("blood_pressure")
}

private data class HomeTileBounds(
    val top: Float,
    val height: Float,
    val windowTop: Float
)

private val defaultHomeTileOrder = listOf(
    HomeTile.INSIGHTS,
    HomeTile.EMOTIONAL,
    HomeTile.ENVIRONMENT,
    HomeTile.EXPERIMENTS,
    HomeTile.HYDRATION,
    HomeTile.CLINICAL,
    HomeTile.EXERCISE,
    HomeTile.MINDFULNESS,
    HomeTile.SLEEP,
    HomeTile.NUTRITION,
    HomeTile.VITALS
)

private const val HOME_PREFS = "project_superhuman_home"
private const val HOME_TILE_ORDER_KEY = "tile_order_v1"

@Composable
internal fun NativeLiveHome(
    openClinical: () -> Unit,
    openSleep: () -> Unit,
    openVitals: () -> Unit,
    openHydration: () -> Unit,
    openNutrition: () -> Unit,
    openExercise: () -> Unit,
    openMindfulness: () -> Unit,
    openEnvironment: () -> Unit,
    openEmotional: () -> Unit,
    openInsights: () -> Unit,
    openExperiments: () -> Unit,
    openMiniMetric: (HomeMiniMetric) -> Unit,
    topContent: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val spacingPx = with(density) { 14.dp.toPx() }
    val edgeScrollZonePx = with(density) { 92.dp.toPx() }
    val edgeScrollMinStepPx = with(density) { 2.5.dp.toPx() }
    val edgeScrollMaxStepPx = with(density) { 15.dp.toPx() }
    val homeScrollState = rememberScrollState()

    var viewportTopY by remember { mutableFloatStateOf(0f) }
    var viewportBottomY by remember { mutableFloatStateOf(0f) }
    var fingerWindowY by remember { mutableFloatStateOf(Float.NaN) }

    var snapshot by remember { mutableStateOf(NativeHomeSnapshot()) }
    val emotionalCurrent by produceState<EmotionalPresentationSnapshot?>(null) {
        val rows = NativeDomainData.forDomain(HealthDomain.EMOTIONAL).latestState()
        value = EmotionalPresentationContract.fromCanonicalValues(rows, rows.latestEmotionalLabel())
    }
    val tileOrder = remember {
        mutableStateListOf<HomeTile>().apply { addAll(loadHomeTileOrder(context)) }
    }
    val tileBounds = remember { mutableStateMapOf<HomeTile, HomeTileBounds>() }

    var draggingTile by remember { mutableStateOf<HomeTile?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    var settling by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { snapshot = loadNativeHomeSnapshot() }

    fun targetIndexFor(tile: HomeTile, visualOffsetY: Float): Int {
        val fromIndex = tileOrder.indexOf(tile)
        val draggedBounds = tileBounds[tile]
        if (fromIndex < 0 || draggedBounds == null) return fromIndex.coerceAtLeast(0)

        val draggedCentre = draggedBounds.top + draggedBounds.height / 2f + visualOffsetY
        var target = fromIndex

        if (visualOffsetY > 0f) {
            for (index in (fromIndex + 1)..tileOrder.lastIndex) {
                val candidate = tileBounds[tileOrder[index]] ?: continue
                if (draggedCentre > candidate.top + candidate.height / 2f) target = index
            }
        } else if (visualOffsetY < 0f) {
            for (index in (fromIndex - 1) downTo 0) {
                val candidate = tileBounds[tileOrder[index]] ?: continue
                if (draggedCentre < candidate.top + candidate.height / 2f) target = index
            }
        }
        return target
    }

    fun finalSlotOffset(tile: HomeTile, targetIndex: Int): Float {
        val fromIndex = tileOrder.indexOf(tile)
        if (fromIndex < 0 || targetIndex == fromIndex) return 0f

        return if (targetIndex > fromIndex) {
            ((fromIndex + 1)..targetIndex).sumOf { index ->
                ((tileBounds[tileOrder[index]]?.height ?: 0f) + spacingPx).toDouble()
            }.toFloat()
        } else {
            -(targetIndex until fromIndex).sumOf { index ->
                ((tileBounds[tileOrder[index]]?.height ?: 0f) + spacingPx).toDouble()
            }.toFloat()
        }
    }

    fun finishDrag(commit: Boolean) {
        val tile = draggingTile ?: return
        if (settling) return
        val fromIndex = tileOrder.indexOf(tile)
        val targetIndex = if (commit) dragTargetIndex.coerceIn(0, tileOrder.lastIndex) else fromIndex
        val settleTo = if (commit) finalSlotOffset(tile, targetIndex) else 0f
        settling = true

        scope.launch {
            val settleAnimation = Animatable(dragOffsetY)
            settleAnimation.animateTo(
                targetValue = settleTo,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) {
                dragOffsetY = value
            }

            if (commit && fromIndex >= 0 && targetIndex != fromIndex) {
                tileOrder.removeAt(fromIndex)
                tileOrder.add(targetIndex, tile)
                saveHomeTileOrder(context, tileOrder)
            }

            draggingTile = null
            dragOffsetY = 0f
            dragTargetIndex = -1
            fingerWindowY = Float.NaN
            settling = false
        }
    }

    LaunchedEffect(draggingTile, settling) {
        while (draggingTile != null && !settling) {
            delay(16)
            val tile = draggingTile ?: break
            val fingerY = fingerWindowY
            if (fingerY.isNaN() || viewportBottomY <= viewportTopY) continue

            val bottomStart = viewportBottomY - edgeScrollZonePx
            val topEnd = viewportTopY + edgeScrollZonePx
            val signedIntensity = when {
                fingerY > bottomStart && homeScrollState.canScrollForward ->
                    ((fingerY - bottomStart) / edgeScrollZonePx).coerceIn(0f, 1f)
                fingerY < topEnd && homeScrollState.canScrollBackward ->
                    -((topEnd - fingerY) / edgeScrollZonePx).coerceIn(0f, 1f)
                else -> 0f
            }

            if (signedIntensity == 0f) continue
            val intensity = kotlin.math.abs(signedIntensity)
            val step = edgeScrollMinStepPx +
                (edgeScrollMaxStepPx - edgeScrollMinStepPx) * intensity * intensity
            val requested = if (signedIntensity > 0f) step else -step
            val consumed = homeScrollState.scrollBy(requested)

            if (consumed != 0f && draggingTile == tile && !settling) {
                dragOffsetY += consumed
                dragTargetIndex = targetIndexFor(tile, dragOffsetY)
            }
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(superhumanBackground)
            .onGloballyPositioned { coordinates ->
                viewportTopY = coordinates.positionInWindow().y
                viewportBottomY = viewportTopY + coordinates.size.height
            }
            .verticalScroll(homeScrollState)
    ) {
        topContent()
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 17.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HomeSummaryBar(openMiniMetric)

            tileOrder.forEach { tile ->
                key(tile) {
                    val draggedTile = draggingTile
                    val fromIndex = draggedTile?.let(tileOrder::indexOf) ?: -1
                    val thisIndex = tileOrder.indexOf(tile)
                    val draggedHeight = draggedTile?.let { tileBounds[it]?.height } ?: 0f
                    val neighbourShift = when {
                        draggedTile == null || tile == draggedTile || dragTargetIndex < 0 -> 0f
                        dragTargetIndex > fromIndex && thisIndex in (fromIndex + 1)..dragTargetIndex -> -(draggedHeight + spacingPx)
                        dragTargetIndex < fromIndex && thisIndex in dragTargetIndex until fromIndex -> draggedHeight + spacingPx
                        else -> 0f
                    }

                    ReorderableHomeTile(
                        tile = tile,
                        isDragging = tile == draggingTile,
                        dragOffsetY = if (tile == draggingTile) dragOffsetY else 0f,
                        neighbourShiftY = neighbourShift,
                        enabled = !settling || tile == draggingTile,
                        onMeasured = { top, height, windowTop ->
                            tileBounds[tile] = HomeTileBounds(top = top, height = height, windowTop = windowTop)
                        },
                        onDragStart = { localFingerY ->
                            if (!settling) {
                                draggingTile = tile
                                dragOffsetY = 0f
                                dragTargetIndex = tileOrder.indexOf(tile)
                                fingerWindowY = (tileBounds[tile]?.windowTop ?: viewportTopY) + localFingerY
                            }
                        },
                        onDragDelta = { deltaY ->
                            if (draggingTile == tile && !settling) {
                                fingerWindowY += deltaY
                                dragOffsetY += deltaY
                                dragTargetIndex = targetIndexFor(tile, dragOffsetY)
                            }
                        },
                        onDragEnd = { finishDrag(commit = true) },
                        onDragCancel = { finishDrag(commit = false) }
                    ) {
                        when (tile) {
                            HomeTile.INSIGHTS -> HomeInsightsTile(InsightsUiRuntime.provider.load(), openInsights)
                            HomeTile.EMOTIONAL -> HomeEmotionalTile(emotionalCurrent, openEmotional)
                            HomeTile.ENVIRONMENT -> HomeEnvironmentalTile(onClick = openEnvironment)
                            HomeTile.EXPERIMENTS -> HomeExperimentsTile(MockExperimentData.active, openExperiments)
                            HomeTile.HYDRATION -> PremiumHomeHydrationTile(snapshot, openHydration)
                            HomeTile.CLINICAL -> LegacyClinicalCard(snapshot, openClinical)
                            HomeTile.EXERCISE -> LegacyTrainingCard(snapshot, openExercise)
                            HomeTile.MINDFULNESS -> HomeMindfulnessBreathworkRow(snapshot, openMindfulness)
                            HomeTile.SLEEP -> HomeSleepInsightTile(snapshot, openSleep)
                            HomeTile.NUTRITION -> LegacyNutritionCard(snapshot, openNutrition)
                            HomeTile.VITALS -> HomeVitalsTile(onClick = openVitals)
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
    isDragging: Boolean,
    dragOffsetY: Float,
    neighbourShiftY: Float,
    enabled: Boolean,
    onMeasured: (top: Float, height: Float, windowTop: Float) -> Unit,
    onDragStart: (localFingerY: Float) -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val currentOnMeasured by rememberUpdatedState(onMeasured)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragDelta by rememberUpdatedState(onDragDelta)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    val animatedNeighbourOffset by animateFloatAsState(
        targetValue = neighbourShiftY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "home-tile-neighbour-shift"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.025f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "home-tile-lift"
    )

    Box(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                currentOnMeasured(
                    coordinates.positionInParent().y,
                    coordinates.size.height.toFloat(),
                    coordinates.positionInWindow().y
                )
            }
            .zIndex(if (isDragging) 20f else 0f)
            .graphicsLayer {
                translationY = if (isDragging) dragOffsetY else animatedNeighbourOffset
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = if (isDragging) 0.97f else 1f
                shadowElevation = if (isDragging) 12.dp.toPx() else 0f
            }
            .pointerInput(tile, enabled) {
                if (!enabled) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { startOffset ->
                        currentOnDragStart(startOffset.y)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragCancel = { currentOnDragCancel() },
                    onDragEnd = { currentOnDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDragDelta(dragAmount.y)
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

    val newcomers = listOf(HomeTile.INSIGHTS, HomeTile.EMOTIONAL, HomeTile.ENVIRONMENT, HomeTile.EXPERIMENTS).filterNot(parsed::contains)
    val existingAndDefaults = parsed + defaultHomeTileOrder.filterNot { it in parsed || it in newcomers }
    return (newcomers + existingAndDefaults).ifEmpty { defaultHomeTileOrder }
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
    val kcalRows = nutritionData.between("food_kcal", start, now)
    val kcal = kcalRows.sumOf { it.value }.roundToInt()
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
    val cardioSessions = exerciseData.between("cardio_session", start, now)
    val sets = exerciseData.between("exercise_set", start, now)
    val volumes = exerciseData.between("workout_volume", start, now)
    val strengthMinutes = workouts.sumOf { it.metadata["durationMin"]?.toIntOrNull() ?: 0 }
    val cardioMinutes = cardioSessions.sumOf { row ->
        row.metadata["durationSeconds"]?.toIntOrNull()?.div(60)
            ?: row.value.roundToInt()
    }
    val cardioDistanceKm = cardioSessions.sumOf { it.metadata["distanceKm"]?.toDoubleOrNull() ?: 0.0 }
    val latestStrengthName = workouts.maxByOrNull { it.timestampEpochMs }?.metadata?.get("workoutName")
    val latestCardioName = cardioSessions.maxByOrNull { it.timestampEpochMs }?.metadata?.get("activityName")
        ?: cardioSessions.maxByOrNull { it.timestampEpochMs }?.metadata?.get("activityType")
            ?.lowercase()?.replace('_', ' ')?.replaceFirstChar { ch -> ch.uppercase() }
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
        nutritionEntriesToday = kcalRows.size,
        workoutsToday = workouts.size + cardioSessions.size,
        strengthWorkoutsToday = workouts.size,
        cardioWorkoutsToday = cardioSessions.size,
        workoutSetsToday = sets.size,
        workoutVolumeToday = volumes.sumOf { it.value }.roundToInt(),
        trainingMinutesToday = strengthMinutes + cardioMinutes,
        latestStrengthWorkoutName = latestStrengthName,
        cardioDistanceTodayKm = cardioDistanceKm,
        cardioMinutesToday = cardioMinutes,
        latestCardioActivityName = latestCardioName,
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
