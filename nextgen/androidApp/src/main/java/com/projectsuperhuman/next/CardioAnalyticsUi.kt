package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun CardioAnalysisRangeSelector(
    selectedRange: CardioAnalysisRange,
    onSelected: (CardioAnalysisRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CardioAnalysisRange.entries.forEach { range ->
            val active = range == selectedRange
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .background(
                        if (active) superhumanGreen else superhumanSurfaceSoft,
                        RoundedCornerShape(14.dp)
                    )
                    .border(
                        1.dp,
                        if (active) superhumanGreen else superhumanBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .semantics {
                        selected = active
                        role = Role.Button
                        contentDescription = range.label + if (active) ", selected" else ""
                    }
                    .clickable { onSelected(range) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    range.label,
                    color = if (active) androidx.compose.ui.graphics.Color.White else superhumanTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun CardioUnitSelector(
    selected: CardioUnitSystem,
    onSelected: (CardioUnitSystem) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CardioUnitSystem.entries.forEach { units ->
            val active = units == selected
            val label = if (units == CardioUnitSystem.METRIC) "Metric" else "Imperial"
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .background(
                        if (active) superhumanBlue.copy(alpha = .16f) else superhumanSurfaceSoft,
                        RoundedCornerShape(13.dp)
                    )
                    .border(
                        1.dp,
                        if (active) superhumanBlue else superhumanBorder,
                        RoundedCornerShape(13.dp)
                    )
                    .semantics {
                        this.selected = active
                        role = Role.Button
                    }
                    .clickable { onSelected(units) }
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Text(
                    label,
                    color = if (active) superhumanBlue else superhumanTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun CardioAnalyticsProgressPanel(
    sessions: List<CardioSession>,
    modifier: Modifier = Modifier
) {
    var range by remember { mutableStateOf(CardioAnalysisRange.WEEKS_4) }
    var unitSystem by remember { mutableStateOf(CardioUnitSystem.METRIC) }
    var selectedActivityDetail by remember { mutableStateOf<CardioActivityType?>(null) }

    val filtered = remember(sessions, range) {
        CardioTrendEngine.filterRange(sessions, range)
    }
    val minutesTrend = remember(sessions, range) {
        CardioTrendEngine.volumeTrend(sessions, range, CardioTrendMetric.MINUTES)
    }
    val distanceTrend = remember(sessions, range) {
        CardioTrendEngine.volumeTrend(sessions, range, CardioTrendMetric.DISTANCE_KM)
    }
    val intensity = remember(filtered) { CardioAnalyticsEngine.intensityDistribution(filtered) }
    val consistency = remember(sessions, range) {
        CardioAnalyticsEngine.consistency(sessions, range)
    }
    val load = remember(sessions) { CardioAnalyticsEngine.loadAnalytics(sessions) }
    val activitiesInRange = remember(filtered) {
        filtered.map { it.activity }.distinct().sortedBy { it.displayName }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(22.dp))
            .border(1.dp, superhumanBorder, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "LONGITUDINAL ANALYTICS",
            color = superhumanTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            "Change the range once; volume, intensity and consistency use the same window.",
            color = superhumanTextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        CardioAnalysisRangeSelector(range, { range = it })
        CardioUnitSelector(unitSystem, { unitSystem = it })

        CardioTrendChart(
            series = minutesTrend,
            title = "Training minutes",
            valueFormatter = { value -> value.roundToInt().toString() + " min" }
        )

        if (distanceTrend.points.any { it.value != null }) {
            CardioTrendChart(
                series = distanceTrend,
                title = "Recorded distance",
                valueFormatter = { value ->
                    val display = CardioUnits.displayDistance(value, unitSystem)
                    String.format(Locale.getDefault(), "%.1f %s", display.first, display.second)
                }
            )
        }

        CardioLoadQualityCard(load)
        CardioIntensityCard(intensity)

        Column(
            Modifier
                .fillMaxWidth()
                .background(superhumanSurfaceSoft, RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Text(
                "Consistency",
                color = superhumanTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            AnalyticsFactRow("Sessions / week", String.format(Locale.getDefault(), "%.1f", consistency.sessionsPerWeek))
            AnalyticsFactRow("Active weeks", consistency.activeWeeks.toString() + " of " + consistency.totalWeeks)
            AnalyticsFactRow("Average weekly minutes", consistency.averageWeeklyMinutes.roundToInt().toString())
            consistency.averageWeeklyDistanceKm?.let { km ->
                val display = CardioUnits.displayDistance(km, unitSystem)
                AnalyticsFactRow(
                    "Average weekly distance",
                    String.format(Locale.getDefault(), "%.1f %s", display.first, display.second)
                )
            }
            AnalyticsFactRow(
                "Rolling 28-day frequency",
                String.format(Locale.getDefault(), "%.1f / week", consistency.rolling28DaySessionsPerWeek)
            )
        }

        if (activitiesInRange.isNotEmpty()) {
            Text(
                "Activity detail",
                color = superhumanTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Choose a discipline to reveal only the trends it actually records.",
                color = superhumanTextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(activitiesInRange) { activity ->
                    AnalyticsFilterChip(
                        text = activity.displayName,
                        selected = selectedActivityDetail == activity
                    ) {
                        selectedActivityDetail = if (selectedActivityDetail == activity) null else activity
                    }
                }
            }
            selectedActivityDetail
                ?.takeIf { it in activitiesInRange }
                ?.let { activity ->
                    CardioActivityProgressPanel(
                        activity = activity,
                        sessions = sessions,
                        range = range,
                        unitSystem = unitSystem
                    )
                }
        }
    }
}

@Composable
internal fun CardioActivityProgressPanel(
    activity: CardioActivityType,
    sessions: List<CardioSession>,
    range: CardioAnalysisRange,
    unitSystem: CardioUnitSystem = CardioUnitSystem.METRIC,
    modifier: Modifier = Modifier
) {
    val matching = remember(sessions, activity, range) {
        CardioTrendEngine.filterRange(sessions, range).filter { it.activity == activity }
    }
    val metrics = remember(matching, activity) {
        CardioTrendEngine.availableActivityMetrics(activity, matching)
    }
    val orderedMetrics = remember(metrics) {
        listOf(
            CardioTrendMetric.PACE_SEC_PER_KM,
            CardioTrendMetric.SPEED_KMH,
            CardioTrendMetric.AVG_HEART_RATE,
            CardioTrendMetric.MAX_HEART_RATE,
            CardioTrendMetric.CADENCE,
            CardioTrendMetric.ELEVATION_GAIN_M,
            CardioTrendMetric.RPE,
            CardioTrendMetric.DURATION_MINUTES,
            CardioTrendMetric.DISTANCE_KM
        ).filter { it in metrics }
    }
    var selectedMetric by remember(activity, range) { mutableStateOf<CardioTrendMetric?>(null) }
    val activeMetric = selectedMetric?.takeIf { it in orderedMetrics } ?: orderedMetrics.firstOrNull()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            activity.displayName + " trends",
            color = superhumanTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
        if (matching.isEmpty()) {
            AnalyticsEmpty("No " + activity.displayName.lowercase() + " sessions in this range.")
            return@Column
        }

        val totalMinutes = matching.sumOf { it.durationSeconds } / 60.0
        val distances = matching.mapNotNull { it.distanceKm }
        Column(
            Modifier
                .fillMaxWidth()
                .background(superhumanSurfaceSoft, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            AnalyticsFactRow("Sessions", matching.size.toString())
            AnalyticsFactRow("Minutes", totalMinutes.roundToInt().toString())
            if (distances.isNotEmpty()) {
                val display = CardioUnits.displayDistance(distances.sum(), unitSystem)
                AnalyticsFactRow(
                    "Distance",
                    String.format(Locale.getDefault(), "%.1f %s", display.first, display.second)
                )
            }
        }

        if (orderedMetrics.isEmpty()) {
            AnalyticsEmpty("No additional recorded metrics are available for this activity.")
            return@Column
        }

        Text("Trend metric", color = superhumanTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(orderedMetrics) { metric ->
                AnalyticsFilterChip(
                    text = activityTrendMetricLabel(metric, activity),
                    selected = activeMetric == metric
                ) {
                    selectedMetric = metric
                }
            }
        }

        activeMetric?.let { metric ->
            val series = CardioTrendEngine.activityTrend(sessions, activity, range, metric) ?: return@let
            CardioTrendChart(
                series = series,
                title = activityTrendMetricLabel(metric, activity),
                valueFormatter = activityTrendValueFormatter(metric, activity, unitSystem)
            )
        }
    }
}

@Composable
internal fun CardioGoalsPanel(
    goals: List<CardioGoal>,
    sessions: List<CardioSession>,
    modifier: Modifier = Modifier
) {
    val progress = remember(goals, sessions) {
        CardioAnalyticsEngine.goalProgress(goals, sessions)
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(22.dp))
            .border(1.dp, superhumanBorder, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Optional goals",
            color = superhumanTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            "Targets are separate from your recorded training and can be changed without rewriting history.",
            color = superhumanTextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        if (progress.isEmpty()) {
            AnalyticsEmpty("No cardio goals set. Your analytics work normally without them.")
        } else {
            progress.forEach { item ->
                val fraction = item.progressFraction.toFloat().coerceIn(0f, 1f)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(superhumanSurfaceSoft, RoundedCornerShape(15.dp))
                        .semantics {
                            contentDescription = item.goal.type.label + ", " +
                                goalValue(item.current, item.goal.type) + " of " +
                                goalValue(item.goal.target, item.goal.type)
                        }
                        .padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.goal.type.label,
                            color = superhumanTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            goalValue(item.current, item.goal.type) + " / " + goalValue(item.goal.target, item.goal.type),
                            color = superhumanTextMuted,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(superhumanBorder.copy(alpha = .55f), RoundedCornerShape(8.dp))
                    ) {
                        if (fraction > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .height(8.dp)
                                    .background(superhumanGreen, RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CardioAnalyticsHistoryPanel(
    sessions: List<CardioSession>,
    onOpenSession: (CardioSession) -> Unit,
    modifier: Modifier = Modifier
) {
    var range by remember { mutableStateOf(CardioAnalysisRange.ALL_TIME) }
    var selectedActivity by remember { mutableStateOf<CardioActivityType?>(null) }
    var selectedWorkoutType by remember { mutableStateOf<CardioWorkoutType?>(null) }
    var selectedSource by remember { mutableStateOf<String?>(null) }
    var showAdvancedFilters by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var pagesVisible by remember { mutableIntStateOf(1) }

    val availableSources = remember(sessions) {
        sessions.map { it.source.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }
    val filter = remember(range, selectedActivity, selectedWorkoutType, selectedSource, query) {
        CardioHistoryFilter(
            range = range,
            activities = selectedActivity?.let { setOf(it) }.orEmpty(),
            workoutTypes = selectedWorkoutType?.let { setOf(it) }.orEmpty(),
            sources = selectedSource?.let { setOf(it) }.orEmpty(),
            query = query
        )
    }
    val pages = remember(sessions, filter, pagesVisible) {
        (0 until pagesVisible).map {
            CardioHistoryEngine.filterAndPage(sessions, filter, page = it, pageSize = 30)
        }
    }
    val visibleItems = remember(pages) { pages.flatMap { it.items } }
    val total = pages.firstOrNull()?.totalItems ?: 0
    val hasMore = pages.lastOrNull()?.hasMore == true

    Column(
        modifier
            .fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(22.dp))
            .border(1.dp, superhumanBorder, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "History",
            color = superhumanTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
        CardioAnalysisRangeSelector(range, {
            range = it
            pagesVisible = 1
        })
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it.take(80)
                pagesVisible = 1
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search notes or activity") }
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                AnalyticsFilterChip(
                    text = "All activities",
                    selected = selectedActivity == null
                ) {
                    selectedActivity = null
                    pagesVisible = 1
                }
            }
            items(CardioActivityType.entries.toList()) { activity ->
                AnalyticsFilterChip(
                    text = activity.displayName,
                    selected = selectedActivity == activity
                ) {
                    selectedActivity = activity
                    pagesVisible = 1
                }
            }
        }

        AnalyticsFilterChip(
            text = if (showAdvancedFilters) "Hide extra filters" else "More filters",
            selected = showAdvancedFilters
        ) {
            showAdvancedFilters = !showAdvancedFilters
        }
        if (showAdvancedFilters) {
            Text("Workout type", color = superhumanTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AnalyticsFilterChip("All types", selectedWorkoutType == null) {
                        selectedWorkoutType = null
                        pagesVisible = 1
                    }
                }
                items(CardioWorkoutType.entries.toList()) { type ->
                    AnalyticsFilterChip(type.label, selectedWorkoutType == type) {
                        selectedWorkoutType = type
                        pagesVisible = 1
                    }
                }
            }
            if (availableSources.isNotEmpty()) {
                Text("Source / provider", color = superhumanTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        AnalyticsFilterChip("All sources", selectedSource == null) {
                            selectedSource = null
                            pagesVisible = 1
                        }
                    }
                    items(availableSources) { source ->
                        AnalyticsFilterChip(source, selectedSource == source) {
                            selectedSource = source
                            pagesVisible = 1
                        }
                    }
                }
            }
        }

        Text(
            total.toString() + if (total == 1) " matching session" else " matching sessions",
            color = superhumanTextMuted,
            fontSize = 12.sp
        )

        if (visibleItems.isEmpty()) {
            AnalyticsEmpty("No sessions match these filters.")
        } else {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleItems, key = { it.id }) { session ->
                    AnalyticsHistoryRow(session) { onOpenSession(session) }
                }
                if (hasMore) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .background(superhumanSurfaceSoft, RoundedCornerShape(14.dp))
                                .clickable { pagesVisible += 1 }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Load 30 more cardio sessions"
                                }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Load more",
                                color = superhumanGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CardioAnalyticsRecordsPanel(
    sessions: List<CardioSession>,
    laps: List<CardioLap> = emptyList(),
    modifier: Modifier = Modifier
) {
    val records = remember(sessions, laps) { CardioRecordsEngine.allRecords(sessions, laps) }
    val sortedRecords = remember(records) {
        records.toSortedMap(compareBy<CardioActivityType> { it.displayName })
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(22.dp))
            .border(1.dp, superhumanBorder, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Verified records",
            color = superhumanTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            "Exact-distance records unlock only from real lap, split, route or external exact-distance timing.",
            color = superhumanTextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        if (records.isEmpty()) {
            AnalyticsEmpty("No saved cardio records yet.")
        } else {
            sortedRecords.forEach { (activity, activityRecords) ->
                Text(
                    activity.displayName,
                    color = superhumanTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                activityRecords.forEach { record ->
                    RecordRow(record)
                }
            }
        }
    }
}

@Composable
internal fun CardioSessionComparisonPanel(
    current: CardioSession,
    history: List<CardioSession>,
    modifier: Modifier = Modifier
) {
    val comparison = remember(current, history) {
        CardioComparisonEngine.compareWithPrevious(current, history)
    } ?: return

    Column(
        modifier
            .fillMaxWidth()
            .background(superhumanSurfaceSoft, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .semantics {
                contentDescription = "Comparison with previous similar session. " + comparison.description
            }
    ) {
        Text(
            "Compare with previous",
            color = superhumanTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            comparison.description,
            color = superhumanGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        comparison.deltas.take(5).forEach { delta ->
            AnalyticsFactRow(
                comparisonMetricLabel(delta.metric),
                formatComparisonValue(delta.previous, delta.unit) + " → " + formatComparisonValue(delta.current, delta.unit)
            )
        }
        Text(
            "Descriptive comparison only; it does not establish cause or a fitness diagnosis.",
            color = superhumanTextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
internal fun CardioPostWorkoutAnalyticsPanel(
    session: CardioSession,
    history: List<CardioSession>,
    laps: List<CardioLap> = emptyList(),
    modifier: Modifier = Modifier
) {
    val summary = remember(session, history, laps) {
        CardioAnalyticsEngine.postWorkoutSummary(session, history, laps)
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(22.dp))
            .border(1.dp, superhumanBorder, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Post-workout analysis",
            color = superhumanTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
        AnalyticsFactRow("Duration", analyticsFormatDuration(summary.durationSeconds))
        summary.distanceKm?.let { AnalyticsFactRow("Distance", String.format(Locale.getDefault(), "%.2f km", it)) }
        summary.avgHeartRate?.let { AnalyticsFactRow("Average HR", it.toString() + " bpm") }
        summary.maxHeartRate?.let { AnalyticsFactRow("Max HR", it.toString() + " bpm") }
        summary.rpe?.let { AnalyticsFactRow("RPE", String.format(Locale.getDefault(), "%.1f / 10", it)) }
        AnalyticsFactRow("Load source", summary.load.source.label)
        if (summary.load.source == CardioLoadSource.MEASURED_ZONES) {
            AnalyticsFactRow(
                "Measured zone coverage",
                String.format(Locale.getDefault(), "%.0f%%", summary.load.zoneCoveragePercent)
            )
        }
        if (summary.newRecords.isNotEmpty()) {
            Text(
                summary.newRecords.size.toString() + if (summary.newRecords.size == 1) " new record" else " new records",
                color = superhumanGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        summary.comparison?.let {
            Text(
                it.description,
                color = superhumanTextPrimary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun CardioLoadQualityCard(load: CardioLoadAnalytics) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(superhumanSurfaceSoft, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text("Cardio load quality", color = superhumanTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        AnalyticsFactRow("7-day load", load.recent7DayLoad.roundToInt().toString())
        AnalyticsFactRow("Baseline", load.baselineQuality.label)
        AnalyticsFactRow(
            "Baseline depth",
            load.baselineScoredSessions.toString() + " scored · " +
                load.baselineActiveWeeks.toString() + " active weeks · " +
                load.baselineSpanDays.toString() + " days"
        )
        AnalyticsFactRow("Scored sessions", load.scoredSessions.toString() + " of " + load.totalSessions)
        AnalyticsFactRow(
            "Measured-zone coverage",
            String.format(Locale.getDefault(), "%.0f%%", load.measuredZoneCoveragePercent)
        )
        if (load.unclassifiedSeconds > 0) {
            AnalyticsFactRow("Unclassified duration", analyticsFormatDuration(load.unclassifiedSeconds))
        }
        load.loadRatio?.let {
            AnalyticsFactRow("Recent / baseline ratio", String.format(Locale.getDefault(), "%.2f", it))
        }
        Text(
            "Measured zones are used first; otherwise RPE × minutes is used. A ratio is withheld until the baseline is usable.",
            color = superhumanTextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun CardioIntensityCard(distribution: CardioIntensityDistribution) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(superhumanSurfaceSoft, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .semantics {
                contentDescription = "Heart-rate intensity distribution with unclassified time shown separately."
            }
    ) {
        Text("Measured intensity", color = superhumanTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        (1..5).forEach { zone ->
            AnalyticsFactRow("Zone " + zone, analyticsFormatDuration(distribution.zoneSeconds[zone] ?: 0))
        }
        AnalyticsFactRow("Unclassified", analyticsFormatDuration(distribution.unclassifiedSeconds))
        Text(
            "Workout type and RPE are not converted into heart-rate zones.",
            color = superhumanTextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun AnalyticsHistoryRow(session: CardioSession, onOpen: () -> Unit) {
    val whenText = remember(session.endedAt) {
        Instant.ofEpochMilli(session.endedAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(superhumanSurfaceSoft, RoundedCornerShape(15.dp))
            .semantics {
                role = Role.Button
                contentDescription = session.activity.displayName + ", " + whenText + ", " +
                    analyticsFormatDuration(session.durationSeconds)
            }
            .clickable { onOpen() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(session.activity.displayName, color = superhumanTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(whenText, color = superhumanTextMuted, fontSize = 12.sp)
            val facts = buildList {
                add(analyticsFormatDuration(session.durationSeconds))
                session.distanceKm?.let { add(String.format(Locale.getDefault(), "%.1f km", it)) }
                session.avgHeartRate?.let { add(it.toString() + " bpm avg") }
            }
            Text(facts.joinToString(" · "), color = superhumanTextMuted, fontSize = 12.sp)
        }
        Text("›", color = superhumanGreen, fontSize = 24.sp)
    }
}

@Composable
private fun AnalyticsFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .background(
                if (selected) superhumanGreen.copy(alpha = .15f) else superhumanSurfaceSoft,
                RoundedCornerShape(13.dp)
            )
            .border(
                1.dp,
                if (selected) superhumanGreen else superhumanBorder,
                RoundedCornerShape(13.dp)
            )
            .semantics {
                this.selected = selected
                role = Role.Button
            }
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) superhumanGreen else superhumanTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RecordRow(record: CardioRecord) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(superhumanSurfaceSoft, RoundedCornerShape(14.dp))
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(9.dp)
                .background(
                    if (record.verified) superhumanGreen else superhumanBorder,
                    CircleShape
                )
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(record.label, color = superhumanTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (!record.verified) {
                Text(
                    record.lockedReason ?: "Unavailable",
                    color = superhumanTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
        Text(
            if (record.verified) formatRecordValue(record) else "Locked",
            color = if (record.verified) superhumanGreen else superhumanTextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun AnalyticsFactRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = superhumanTextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(value, color = superhumanTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
private fun AnalyticsEmpty(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(superhumanSurfaceSoft, RoundedCornerShape(15.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = superhumanTextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

private fun activityTrendMetricLabel(
    metric: CardioTrendMetric,
    activity: CardioActivityType
): String = when (metric) {
    CardioTrendMetric.PACE_SEC_PER_KM -> when (activity.paceMode) {
        CardioPaceMode.PER_500M -> "500 m split"
        CardioPaceMode.PER_100M -> "100 m pace"
        else -> "Pace"
    }
    CardioTrendMetric.SPEED_KMH -> "Speed"
    CardioTrendMetric.AVG_HEART_RATE -> "Average HR"
    CardioTrendMetric.MAX_HEART_RATE -> "Max HR"
    CardioTrendMetric.CADENCE -> "Cadence"
    CardioTrendMetric.ELEVATION_GAIN_M -> "Elevation gain"
    CardioTrendMetric.RPE -> "RPE"
    CardioTrendMetric.DURATION_MINUTES -> "Duration"
    CardioTrendMetric.DISTANCE_KM -> "Distance"
    else -> metric.label
}

private fun activityTrendValueFormatter(
    metric: CardioTrendMetric,
    activity: CardioActivityType,
    unitSystem: CardioUnitSystem
): (Double) -> String = when (metric) {
    CardioTrendMetric.PACE_SEC_PER_KM -> { value ->
        val seconds = if (activity.paceMode == CardioPaceMode.PER_KM && unitSystem == CardioUnitSystem.IMPERIAL) {
            CardioUnits.secondsPerKmToSecondsPerMile(value)
        } else value
        CardioUnits.formatPace(seconds.roundToInt())
    }
    CardioTrendMetric.SPEED_KMH -> { value ->
        val display = CardioUnits.displaySpeed(value, unitSystem)
        String.format(Locale.getDefault(), "%.1f %s", display.first, display.second)
    }
    CardioTrendMetric.AVG_HEART_RATE,
    CardioTrendMetric.MAX_HEART_RATE -> { value -> value.roundToInt().toString() + " bpm" }
    CardioTrendMetric.CADENCE -> { value -> value.roundToInt().toString() + " /min" }
    CardioTrendMetric.ELEVATION_GAIN_M -> { value ->
        val display = CardioUnits.displayElevation(value, unitSystem)
        String.format(Locale.getDefault(), "%.0f %s", display.first, display.second)
    }
    CardioTrendMetric.RPE -> { value -> String.format(Locale.getDefault(), "%.1f", value) }
    CardioTrendMetric.DURATION_MINUTES -> { value -> value.roundToInt().toString() + " min" }
    CardioTrendMetric.DISTANCE_KM -> { value ->
        val display = CardioUnits.displayDistance(value, unitSystem)
        String.format(Locale.getDefault(), "%.1f %s", display.first, display.second)
    }
    else -> { value -> String.format(Locale.getDefault(), "%.1f", value) }
}

private fun goalValue(value: Double, type: CardioGoalType): String = when (type) {
    CardioGoalType.WEEKLY_MINUTES,
    CardioGoalType.WEEKLY_ZONE_2_MINUTES -> value.roundToInt().toString() + " min"
    CardioGoalType.WEEKLY_DISTANCE_KM -> String.format(Locale.getDefault(), "%.1f km", value)
    CardioGoalType.WEEKLY_SESSIONS -> value.roundToInt().toString()
}

private fun analyticsFormatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return if (hours > 0) {
        hours.toString() + ":" + minutes.toString().padStart(2, '0') + ":" + secs.toString().padStart(2, '0')
    } else {
        minutes.toString() + ":" + secs.toString().padStart(2, '0')
    }
}

private fun comparisonMetricLabel(metric: CardioComparisonMetric): String = when (metric) {
    CardioComparisonMetric.PACE -> "Pace"
    CardioComparisonMetric.SPEED -> "Speed"
    CardioComparisonMetric.SPLIT_500M -> "500 m split"
    CardioComparisonMetric.PACE_100M -> "100 m pace"
    CardioComparisonMetric.AVG_HEART_RATE -> "Average HR"
    CardioComparisonMetric.DISTANCE -> "Distance"
    CardioComparisonMetric.DURATION -> "Duration"
    CardioComparisonMetric.ELEVATION -> "Elevation"
}

private fun formatComparisonValue(value: Double, unit: String): String = when (unit) {
    "sec/km", "sec/500m", "sec/100m" -> CardioUnits.formatPace(value.roundToInt())
    "sec" -> analyticsFormatDuration(value.roundToInt())
    "bpm" -> value.roundToInt().toString() + " bpm"
    "km" -> String.format(Locale.getDefault(), "%.2f km", value)
    "km/h" -> String.format(Locale.getDefault(), "%.1f km/h", value)
    "m" -> value.roundToInt().toString() + " m"
    else -> String.format(Locale.getDefault(), "%.1f %s", value, unit)
}

private fun formatRecordValue(record: CardioRecord): String {
    val value = record.value ?: return "—"
    return when (record.unit) {
        "sec", "sec/km", "sec/500m", "sec/100m" -> CardioUnits.formatPace(value.roundToInt())
        "km" -> String.format(Locale.getDefault(), "%.1f km", value)
        "km/h" -> String.format(Locale.getDefault(), "%.1f km/h", value)
        else -> String.format(Locale.getDefault(), "%.1f %s", value, record.unit)
    }
}
