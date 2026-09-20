package com.projectsuperhuman.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private enum class CardioHubGlyph {
    HEART, TREND, CALENDAR, TARGET, SENSOR, RUN, WALK, BIKE, ROW, MORE, HISTORY, TROPHY, LOAD, PLUS
}

private enum class CardioHubSheet {
    FITNESS, READINESS, WEEK, QUICK_STARTS, GOALS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardioVisualHub(
    sessions: List<CardioSession>,
    sensorMetrics: CardioLiveSensorMetrics,
    showStartBar: Boolean,
    onQuickStart: (CardioActivityType) -> Unit,
    onMoreActivities: () -> Unit,
    onSessions: () -> Unit,
    onOpenSession: (CardioSession) -> Unit,
    onFitness: () -> Unit,
    onTrends: () -> Unit,
    onTestsRecords: () -> Unit,
    onLog: () -> Unit
) {
    val context = LocalContext.current
    val hubPreferences = remember(context.applicationContext) {
        CardioHubPreferences(context.applicationContext)
    }
    var recoveryContext by remember { mutableStateOf<CardioRecoveryContext?>(null) }
    var sheet by remember { mutableStateOf<CardioHubSheet?>(null) }
    var quickActivities by remember { mutableStateOf(hubPreferences.quickActivities()) }
    var goals by remember { mutableStateOf(hubPreferences.goals()) }

    LaunchedEffect(sessions) {
        val loadAnalytics = CardioAnalyticsEngine.loadAnalytics(sessions)
        val latestLoad = CardioTrainingLoadEngine.latest(sessions)
            ?.takeIf { loadAnalytics.scoredSessions > 0 }
        recoveryContext = runCatching {
            CardioNof1Repository().loadRecoveryContext(latestLoad)
        }.getOrNull()
    }

    val model = remember(sessions, recoveryContext) {
        buildCardioProductOverviewModel(
            sessions = sessions,
            recoveryContext = recoveryContext
        )
    }
    val efficiencySeries = remember(sessions) { cardioHubEfficiencySeries(sessions) }
    val loadSeries = remember(sessions) {
        CardioTrainingLoadEngine.build(sessions).takeLast(21).map { it.chronicLoad }
    }
    val zone3MinutesThisWeek = remember(sessions) { cardioHubWeekZoneMinutes(sessions, 3) }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        if (showStartBar) {
            CardioHubStartBar(
                activities = quickActivities,
                onQuickStart = onQuickStart,
                onEdit = { sheet = CardioHubSheet.QUICK_STARTS },
                onMoreActivities = onMoreActivities
            )
        }

        CardioHubOverviewPanel(
            model = model,
            efficiencySeries = efficiencySeries,
            loadSeries = loadSeries,
            zone3Minutes = zone3MinutesThisWeek,
            goals = goals,
            onEditGoals = { sheet = CardioHubSheet.GOALS },
            onFitness = onFitness,
            onReadiness = { sheet = CardioHubSheet.READINESS },
            onTrends = onTrends
        )

        CardioHubSensorStrip(sensorMetrics)

        if (sessions.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardioHubSectionHeader("RECENT", "Latest workouts", Modifier.weight(1f))
                Text(
                    "VIEW ALL ›",
                    color = superhumanBlue,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.clickable { onSessions() }.padding(vertical = 8.dp)
                )
            }
            CardioHubRecentSessions(sessions.take(4), onOpenSession)
        }

        CardioHubDeepDiveBar(
            sessionsThisWeek = model.week.sessions,
            loadValue = loadSeries.lastOrNull()?.roundToInt(),
            onSessions = onSessions,
            onTrends = onTrends,
            onRecords = onTestsRecords
        )

        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 46.dp)
                .clickable { onLog() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardioHubGlyphIcon(CardioHubGlyph.PLUS, superhumanBlue, Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Text(
                "Log completed workout",
                color = superhumanTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text("›", color = superhumanBlue, fontSize = 20.sp)
        }
    }

    sheet?.let { selected ->
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = superhumanSurface
        ) {
            when (selected) {
                CardioHubSheet.FITNESS -> CardioHubFitnessSheet(
                    model = model,
                    sparkline = efficiencySeries,
                    onOpen = {
                        sheet = null
                        onFitness()
                    }
                )
                CardioHubSheet.READINESS -> CardioHubReadinessSheet(
                    model = model,
                    context = recoveryContext,
                    onOpen = {
                        sheet = null
                        onTrends()
                    }
                )
                CardioHubSheet.WEEK -> CardioHubWeekSheet(
                    week = model.week,
                    onOpen = {
                        sheet = null
                        onTrends()
                    }
                )
                CardioHubSheet.QUICK_STARTS -> CardioHubQuickStartEditor(
                    activities = quickActivities,
                    onActivitiesChange = { updated ->
                        quickActivities = updated
                        hubPreferences.saveQuickActivities(updated)
                    },
                    onDone = { sheet = null }
                )
                CardioHubSheet.GOALS -> CardioHubGoalEditor(
                    goals = goals,
                    onSave = { updated ->
                        goals = updated
                        hubPreferences.saveGoals(updated)
                        sheet = null
                    },
                    onCancel = { sheet = null }
                )
            }
        }
    }
}

@Composable
private fun CardioHubStartBar(
    activities: List<CardioActivityType>,
    onQuickStart: (CardioActivityType) -> Unit,
    onEdit: () -> Unit,
    onMoreActivities: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(19.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("START CARDIO", color = superhumanTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text(
                "EDIT",
                color = superhumanBlue,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.clickable { onEdit() }.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            activities.take(CardioHubPreferences.QUICK_SLOT_COUNT).forEachIndexed { index, activity ->
                val accent = cardioHubQuickAccent(index)
                CardioHubStartActivityAction(
                    activity = activity,
                    accent = accent,
                    modifier = Modifier.weight(1f)
                ) { onQuickStart(activity) }
            }
            CardioHubStartMoreAction(
                modifier = Modifier.weight(.78f),
                onClick = onMoreActivities
            )
        }
    }
}

@Composable
private fun CardioHubStartActivityAction(
    activity: CardioActivityType,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .heightIn(min = 58.dp)
            .clickable { onClick() }
            .padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(34.dp)
                .background(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .15f else .08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            SuperhumanDomainIcon(
                glyph = cardioDomainGlyph(activity),
                tint = accent,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            cardioHubQuickLabel(activity),
            color = superhumanTextPrimary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun CardioHubStartMoreAction(
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .heightIn(min = 58.dp)
            .clickable { onClick() }
            .padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(34.dp)
                .background(superhumanSurfaceSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            SuperhumanDomainIcon(
                glyph = SuperhumanDomainGlyph.MORE,
                tint = superhumanTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("More", color = superhumanTextPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}


@Composable
private fun CardioHubOverviewPanel(
    model: CardioProductOverviewModel,
    efficiencySeries: List<Double>,
    loadSeries: List<Double>,
    zone3Minutes: Int,
    goals: CardioHubGoals,
    onEditGoals: () -> Unit,
    onFitness: () -> Unit,
    onReadiness: () -> Unit,
    onTrends: () -> Unit
) {
    val fitness = model.fitness
    val readiness = model.readiness
    val load = loadSeries.lastOrNull()?.roundToInt()
    val remainingBaseline = (4 - fitness.comparableSessionCount).coerceAtLeast(0)

    val headline = when {
        fitness.trendDeltaPercent == null -> "Building your cardio baseline"
        fitness.trendDeltaPercent > 1.0 -> "Fitness is trending up"
        fitness.trendDeltaPercent < -1.0 -> "Fitness trend is lower"
        else -> "Fitness looks steady"
    }
    val explanation = when {
        fitness.trendDeltaPercent == null && remainingBaseline > 0 ->
            "$remainingBaseline more comparable " + (if (remainingBaseline == 1) "session" else "sessions") + " to unlock your pace / HR trend"
        fitness.trendDeltaPercent != null ->
            "Based on comparable pace and heart-rate sessions"
        else -> "Keep training consistently to build your personal trend"
    }

    Column(
        Modifier.fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(21.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            CardioHubIconBadge(CardioHubGlyph.TREND, superhumanGreen)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("YOUR CARDIO", color = superhumanTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .6.sp)
                Text(headline, color = superhumanTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Black, lineHeight = 19.sp)
                Text(explanation, color = superhumanTextMuted, fontSize = 8.sp, lineHeight = 11.sp)
            }
            Box(
                Modifier.size(34.dp)
                    .background(superhumanSurfaceSoft, CircleShape)
                    .clickable { onEditGoals() },
                contentAlignment = Alignment.Center
            ) {
                SuperhumanDomainIcon(
                    glyph = SuperhumanDomainGlyph.MORE,
                    tint = superhumanTextMuted,
                    modifier = Modifier.size(19.dp),
                    contentDescription = "Set cardio goals"
                )
            }
        }

        Spacer(Modifier.height(13.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CardioHubGoalRing(
                label = "TOTAL",
                currentMinutes = model.week.minutes,
                goalMinutes = goals.totalMinutes,
                accent = Color(0xFF8E72D8),
                modifier = Modifier.weight(1f),
                onClick = onEditGoals
            )
            CardioHubGoalRing(
                label = "ZONE 2",
                currentMinutes = model.week.zone2Minutes,
                goalMinutes = goals.zone2Minutes,
                accent = superhumanGreen,
                modifier = Modifier.weight(1f),
                onClick = onEditGoals
            )
            CardioHubGoalRing(
                label = "ZONE 3",
                currentMinutes = zone3Minutes,
                goalMinutes = goals.zone3Minutes,
                accent = Color(0xFFD1A03D),
                modifier = Modifier.weight(1f),
                onClick = onEditGoals
            )
        }

        Spacer(Modifier.height(11.dp))
        Row(
            Modifier.fillMaxWidth()
                .background(superhumanSurfaceSoft, RoundedCornerShape(15.dp))
                .clickable { onReadiness() }
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SuperhumanDomainIcon(
                glyph = SuperhumanDomainGlyph.HEARTBEAT,
                tint = superhumanBlue,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "TODAY · ${cardioHubShortReadiness(readiness.status)} signals",
                    color = superhumanTextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "${readiness.availableSignals}/${readiness.totalSignals} inputs · ${readiness.confidence.label} confidence",
                    color = superhumanTextMuted,
                    fontSize = 8.sp
                )
            }
            CardioHubSignalDots(readiness.availableSignals, readiness.totalSignals, superhumanBlue)
            Spacer(Modifier.width(6.dp))
            Text("›", color = superhumanBlue, fontSize = 18.sp)
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.weight(1f)
                    .clickable { onFitness() }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuperhumanDomainIcon(
                    glyph = SuperhumanDomainGlyph.TREND,
                    tint = superhumanGreen,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(7.dp))
                Column {
                    Text("FITNESS", color = superhumanTextMuted, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    Text(
                        fitness.trendDeltaPercent?.let {
                            val sign = if (it > 0) "+" else ""
                            "$sign${String.format(Locale.US, "%.1f", it)}% efficiency"
                        } ?: "Baseline ${fitness.comparableSessionCount}/4",
                        color = superhumanTextPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                Modifier.weight(1f)
                    .clickable { onTrends() }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuperhumanDomainIcon(
                    glyph = SuperhumanDomainGlyph.TREND,
                    tint = Color(0xFFD1A03D),
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(7.dp))
                Column {
                    Text("LOAD", color = superhumanTextMuted, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    Text(
                        load?.let { "CTL $it" } ?: "Building",
                        color = superhumanTextPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CardioHubGoalRing(
    label: String,
    currentMinutes: Int,
    goalMinutes: Int?,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val progress = goalMinutes?.takeIf { it > 0 }?.let {
        currentMinutes.toFloat() / it.toFloat()
    }
    Column(
        modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(72.dp)) {
                val stroke = 6.dp.toPx()
                drawArc(
                    color = superhumanBorder.copy(alpha = .5f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                progress?.let { raw ->
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * raw.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    currentMinutes.toString(),
                    color = superhumanTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
                Text("MIN", color = superhumanTextMuted, fontSize = 6.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = superhumanTextPrimary, fontSize = 8.sp, fontWeight = FontWeight.Black)
        Text(
            goalMinutes?.let { "of $it min" } ?: "Set goal",
            color = if (goalMinutes == null) accent else superhumanTextMuted,
            fontSize = 7.sp,
            fontWeight = if (goalMinutes == null) FontWeight.Bold else FontWeight.Normal
        )
    }
}


@Composable
private fun CardioHubSignalDots(available: Int, total: Int, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total.coerceAtLeast(0)) { index ->
            Box(
                Modifier.size(7.dp)
                    .background(
                        if (index < available) accent else superhumanBorder,
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun CardioHubWeekCard(week: CardioWeekIntentSnapshot, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .semantics {
                role = Role.Button
                contentDescription = "This week. ${week.minutes} minutes, ${week.sessions} sessions, ${week.zone2Minutes} Zone 2 minutes."
            }
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardioHubWeekRing(week)
        Spacer(Modifier.width(12.dp))
        CardioHubCompactStat("SESSIONS", week.sessions.toString())
        Spacer(Modifier.weight(1f))
        CardioHubCompactStat("ZONE 2", "${week.zone2Minutes}m")
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text("THIS WEEK", color = superhumanTextMuted, fontSize = 7.sp, fontWeight = FontWeight.Black)
            Text(
                week.targetMinutes?.let { "${week.minutes}/$it min" } ?: "NO GOAL",
                color = if (week.targetMinutes == null) superhumanTextMuted else superhumanTextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun CardioHubWeekRing(week: CardioWeekIntentSnapshot) {
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(56.dp)) {
            val stroke = 5.dp.toPx()
            drawArc(
                color = superhumanBorder.copy(alpha = .55f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            week.progressFraction?.let {
                drawArc(
                    color = Color(0xFF8E72D8),
                    startAngle = -90f,
                    sweepAngle = 360f * it.toFloat().coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(week.minutes.toString(), color = superhumanTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text("MIN", color = superhumanTextMuted, fontSize = 6.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CardioHubSensorStrip(metrics: CardioLiveSensorMetrics) {
    val connected = metrics.connection == CardioSensorConnectionState.CONNECTED
    val accent = if (connected) superhumanGreen else superhumanBlue
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable { SmartDevicesNavigationBridge.open?.invoke() }
            .semantics {
                role = Role.Button
                contentDescription = cardioProductSensorStatus(metrics) + ". Manage devices."
            }
            .padding(horizontal = 2.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardioHubGlyphIcon(CardioHubGlyph.SENSOR, accent, Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            if (connected) {
                buildList {
                    add(metrics.sourceLabel.ifBlank { "Live HR sensor" })
                    metrics.currentHeartRateBpm?.let { add("$it bpm") }
                    metrics.currentZone?.let { add("Z$it") }
                }.joinToString(" · ")
            } else {
                "No live HR sensor"
            },
            color = superhumanTextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text("MANAGE ›", color = accent, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CardioHubQuickStart(onQuickStart: (CardioActivityType) -> Unit) {
    val actions = listOf(
        Triple(CardioActivityType.RUNNING, "Run", CardioHubGlyph.RUN),
        Triple(CardioActivityType.WALKING, "Walk", CardioHubGlyph.WALK),
        Triple(CardioActivityType.CYCLING, "Cycle", CardioHubGlyph.BIKE),
        Triple(CardioActivityType.ROWING, "Row", CardioHubGlyph.ROW)
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEachIndexed { index, (activity, label, glyph) ->
            val accent = when (index) {
                0 -> superhumanGreen
                1 -> superhumanBlue
                2 -> Color(0xFF8E72D8)
                else -> Color(0xFFD1A03D)
            }
            Column(
                Modifier.weight(1f)
                    .heightIn(min = 70.dp)
                    .background(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .12f else .07f), RoundedCornerShape(17.dp))
                    .clickable { onQuickStart(activity) }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CardioHubGlyphIcon(glyph, accent, Modifier.size(23.dp))
                Spacer(Modifier.height(5.dp))
                Text(label, color = superhumanTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun CardioHubDeepDiveBar(
    sessionsThisWeek: Int,
    loadValue: Int?,
    onSessions: () -> Unit,
    onTrends: () -> Unit,
    onRecords: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text("EXPLORE", color = superhumanTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .6.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardioHubDeepDiveItem(
                glyph = SuperhumanDomainGlyph.ROUTE,
                title = "Sessions",
                value = "$sessionsThisWeek this wk",
                accent = superhumanBlue,
                modifier = Modifier.weight(1f),
                onClick = onSessions
            )
            CardioHubMiniDivider()
            CardioHubDeepDiveItem(
                glyph = SuperhumanDomainGlyph.TREND,
                title = "Trends",
                value = loadValue?.let { "CTL $it" } ?: "Building",
                accent = Color(0xFF8E72D8),
                modifier = Modifier.weight(1f),
                onClick = onTrends
            )
            CardioHubMiniDivider()
            CardioHubDeepDiveItem(
                glyph = SuperhumanDomainGlyph.TROPHY,
                title = "Records",
                value = "Verified only",
                accent = Color(0xFFD1A03D),
                modifier = Modifier.weight(1f),
                onClick = onRecords
            )
        }
    }
}

@Composable
private fun CardioHubDeepDiveItem(
    glyph: SuperhumanDomainGlyph,
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier.heightIn(min = 50.dp)
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SuperhumanDomainIcon(glyph, accent, Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = superhumanTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(value, color = superhumanTextMuted, fontSize = 7.sp)
        }
        Text("›", color = accent, fontSize = 16.sp)
    }
}

@Composable
private fun CardioHubMiniDivider() {
    Box(Modifier.width(1.dp).height(34.dp).background(superhumanBorder.copy(alpha = .5f)))
}

@Composable
private fun CardioHubRecentSessions(sessions: List<CardioSession>, onOpen: (CardioSession) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        sessions.forEach { session ->
            CardioHubRecentSessionCard(session) { onOpen(session) }
        }
    }
}

@Composable
private fun CardioHubRecentSessionCard(session: CardioSession, onClick: () -> Unit) {
    val whenText = remember(session.endedAt) {
        Instant.ofEpochMilli(session.endedAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM"))
    }
    Column(
        Modifier.width(150.dp)
            .heightIn(min = 112.dp)
            .background(superhumanSurface, RoundedCornerShape(19.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardioHubIconBadge(cardioHubActivityGlyph(session.activity), cardioHubActivityColor(session.activity))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(session.activity.displayName, color = superhumanTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text(whenText, color = superhumanTextMuted, fontSize = 8.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            session.distanceKm?.let { String.format(Locale.US, "%.1f km", it) }
                ?: cardioHubDuration(session.durationSeconds),
            color = superhumanTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            buildList {
                if (session.distanceKm != null) add(cardioHubDuration(session.durationSeconds))
                session.avgHeartRate?.let { add("$it bpm") }
            }.joinToString(" · ").ifBlank { "Completed session" },
            color = superhumanTextMuted,
            fontSize = 8.sp
        )
        if (session.zoneSeconds.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            CardioHubZoneStrip(session.zoneSeconds)
        }
    }
}

@Composable
private fun CardioHubPreviewTile(
    glyph: CardioHubGlyph,
    title: String,
    value: String,
    detail: String,
    accent: Color,
    modifier: Modifier,
    sparkline: List<Double> = emptyList(),
    onClick: () -> Unit
) {
    Column(
        modifier
            .heightIn(min = 112.dp)
            .background(superhumanSurface, RoundedCornerShape(19.dp))
            .clickable { onClick() }
            .padding(11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardioHubGlyphIcon(glyph, accent, Modifier.size(18.dp))
            Spacer(Modifier.weight(1f))
            Text("›", color = accent, fontSize = 17.sp)
        }
        Spacer(Modifier.height(7.dp))
        Text(title, color = superhumanTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
        Text(value, color = superhumanTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Black)
        Text(detail, color = superhumanTextMuted, fontSize = 7.sp, lineHeight = 9.sp)
        if (sparkline.size >= 2) {
            Spacer(Modifier.height(5.dp))
            CardioHubSparkline(sparkline, accent, Modifier.fillMaxWidth().height(22.dp))
        }
    }
}

@Composable
private fun CardioHubFitnessSheet(
    model: CardioProductOverviewModel,
    sparkline: List<Double>,
    onOpen: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        CardioHubSheetTitle(CardioHubGlyph.TREND, "Fitness direction", superhumanGreen)
        Text(model.fitness.trendLabel, color = superhumanTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black)
        model.fitness.trendDeltaPercent?.let {
            val sign = if (it > 0) "+" else ""
            Text("$sign${String.format(Locale.US, "%.1f", it)}% pace / HR efficiency", color = superhumanGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        if (sparkline.size >= 2) CardioHubSparkline(sparkline, superhumanGreen, Modifier.fillMaxWidth().height(92.dp))
        Spacer(Modifier.height(14.dp))
        CardioHubDetailRow("Comparable sessions", model.fitness.comparableSessionCount.toString())
        CardioHubDetailRow("Confidence", model.fitness.confidence.label)
        CardioHubDetailRow("Basis", model.fitness.basis)
        Spacer(Modifier.height(14.dp))
        CardioHubSheetAction("Open fitness detail", superhumanGreen, onOpen)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CardioHubReadinessSheet(
    model: CardioProductOverviewModel,
    context: CardioRecoveryContext?,
    onOpen: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        CardioHubSheetTitle(CardioHubGlyph.HEART, "Training capacity", superhumanBlue)
        Text(cardioHubShortReadiness(model.readiness.status), color = superhumanTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(
            "${model.readiness.availableSignals}/${model.readiness.totalSignals} signals · ${model.readiness.confidence.label} confidence",
            color = superhumanBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        CardioHubSignalDetail("Resting HR", cardioHubBaselineState(context?.restingHeartRateBpm, context?.restingHeartRateBaselineBpm, lowerIsUsuallyBetter = true))
        CardioHubSignalDetail("HRV", cardioHubBaselineState(context?.hrvRmssdMs, context?.hrvBaselineRmssdMs, lowerIsUsuallyBetter = false))
        CardioHubSignalDetail("Sleep", context?.sleepScore?.let { "${it.roundToInt()}/100" } ?: "Unavailable")
        CardioHubSignalDetail("Recent load", context?.trainingStressBalance?.let { String.format(Locale.US, "%+.1f", it) } ?: "Unavailable")
        Spacer(Modifier.height(12.dp))
        Text(
            "Training/recovery signal only — not medical clearance.",
            color = superhumanTextMuted,
            fontSize = 9.sp
        )
        Spacer(Modifier.height(14.dp))
        CardioHubSheetAction("Open trends & recovery", superhumanBlue, onOpen)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CardioHubWeekSheet(week: CardioWeekIntentSnapshot, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        CardioHubSheetTitle(CardioHubGlyph.CALENDAR, "This week", Color(0xFF8E72D8))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardioHubWeekRing(week)
            Spacer(Modifier.width(18.dp))
            Column {
                Text("${week.sessions} sessions", color = superhumanTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("${week.zone2Minutes} min Zone 2", color = superhumanTextMuted, fontSize = 11.sp)
                Text(
                    week.targetMinutes?.let { "${week.minutes} / $it min" } ?: "No weekly target set",
                    color = Color(0xFF8E72D8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        CardioHubSheetAction("Open training trends", Color(0xFF8E72D8), onOpen)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun CardioFitnessHubScreen(sessions: List<CardioSession>) {
    var expanded by remember { mutableStateOf(false) }
    val fitness = remember(sessions) { CardioPersonalBaselineEngine.fitnessSnapshot(sessions) }
    val sparkline = remember(sessions) { cardioHubEfficiencySeries(sessions) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardioHubVisualMetric(
            title = "Fitness direction",
            value = fitness.trendLabel,
            accent = superhumanGreen,
            glyph = CardioHubGlyph.TREND,
            sparkline = sparkline,
            footer = fitness.trendDeltaPercent?.let {
                val sign = if (it > 0) "+" else ""
                "$sign${String.format(Locale.US, "%.1f", it)}% pace / HR efficiency"
            } ?: "${fitness.comparableSessionCount}/4 comparable sessions"
        )
        CardioHubExpandableRow(
            title = if (expanded) "Hide advanced metrics" else "Explore advanced metrics",
            detail = "Efficiency · pace/HR · decoupling · recovery · aerobic capacity",
            accent = superhumanGreen,
            expanded = expanded
        ) { expanded = !expanded }
        if (expanded) CardioFitnessProductScreen(sessions)
    }
}

@Composable
internal fun CardioTrendsHubScreen(sessions: List<CardioSession>) {
    var expanded by remember { mutableStateOf(false) }
    val points = remember(sessions) { CardioTrainingLoadEngine.build(sessions).takeLast(21) }
    val latest = points.lastOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardioHubVisualMetric(
            title = "Training load",
            value = latest?.chronicLoad?.roundToInt()?.toString() ?: "Building",
            accent = Color(0xFF8E72D8),
            glyph = CardioHubGlyph.LOAD,
            sparkline = points.map { it.chronicLoad },
            footer = latest?.let {
                "Acute ${it.acuteLoad.roundToInt()} · Balance ${it.trainingStressBalance.roundToInt()}"
            } ?: "Add scored sessions to build the trend"
        )
        CardioHubExpandableRow(
            title = if (expanded) "Hide full analytics" else "Open full trends",
            detail = "Load · intensity · volume · fitness trends",
            accent = Color(0xFF8E72D8),
            expanded = expanded
        ) { expanded = !expanded }
        if (expanded) CardioTrendsProductScreen(sessions)
    }
}

@Composable
internal fun CardioRecordsHubScreen(sessions: List<CardioSession>) {
    var expanded by remember { mutableStateOf(false) }
    val longest = sessions.mapNotNull { it.distanceKm }.maxOrNull()
    val longestDuration = sessions.maxOfOrNull { it.durationSeconds }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            CardioHubSummaryBlock(
                "LONGEST",
                longest?.let { String.format(Locale.US, "%.1f km", it) } ?: "—",
                CardioHubGlyph.TROPHY,
                Color(0xFFD1A03D),
                Modifier.weight(1f)
            )
            CardioHubSummaryBlock(
                "DURATION",
                longestDuration?.let(::cardioHubDuration) ?: "—",
                CardioHubGlyph.CALENDAR,
                superhumanBlue,
                Modifier.weight(1f)
            )
        }
        Text(
            "Session summaries above are not exact-distance records. Verified records remain evidence-gated.",
            color = superhumanTextMuted,
            fontSize = 9.sp,
            lineHeight = 13.sp
        )
        CardioHubExpandableRow(
            title = if (expanded) "Hide tests & record evidence" else "Open tests & verified records",
            detail = "Verified bests · HRmax review · field tests",
            accent = Color(0xFFD1A03D),
            expanded = expanded
        ) { expanded = !expanded }
        if (expanded) CardioTestsAndRecordsProductScreen(sessions)
    }
}

@Composable
private fun CardioHubVisualMetric(
    title: String,
    value: String,
    accent: Color,
    glyph: CardioHubGlyph,
    sparkline: List<Double>,
    footer: String
) {
    Column(
        Modifier.fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardioHubIconBadge(glyph, accent)
            Spacer(Modifier.width(9.dp))
            Text(title, color = superhumanTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        Text(value, color = superhumanTextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black)
        if (sparkline.size >= 2) {
            Spacer(Modifier.height(10.dp))
            CardioHubSparkline(sparkline, accent, Modifier.fillMaxWidth().height(92.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(footer, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CardioHubExpandableRow(
    title: String,
    detail: String,
    accent: Color,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 54.dp)
            .background(superhumanSurfaceSoft, RoundedCornerShape(17.dp))
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = superhumanTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(detail, color = superhumanTextMuted, fontSize = 8.sp)
        }
        Text(if (expanded) "−" else "+", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CardioHubSummaryBlock(
    label: String,
    value: String,
    glyph: CardioHubGlyph,
    accent: Color,
    modifier: Modifier
) {
    Column(
        modifier.background(superhumanSurface, RoundedCornerShape(19.dp)).padding(14.dp)
    ) {
        CardioHubGlyphIcon(glyph, accent, Modifier.size(20.dp))
        Spacer(Modifier.height(9.dp))
        Text(label, color = superhumanTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
        Text(value, color = superhumanTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CardioHubSectionHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, color = superhumanTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(7.dp))
        Text(subtitle, color = superhumanTextMuted, fontSize = 8.sp)
    }
}

@Composable
private fun CardioHubCompactStat(label: String, value: String, accent: Color = superhumanTextPrimary) {
    Column {
        Text(label, color = superhumanTextMuted, fontSize = 7.sp, fontWeight = FontWeight.Black)
        Text(value, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CardioHubIconBadge(glyph: CardioHubGlyph, accent: Color) {
    Box(
        Modifier.size(32.dp)
            .background(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .17f else .09f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        CardioHubGlyphIcon(glyph, accent, Modifier.size(17.dp))
    }
}

@Composable
private fun CardioHubGlyphIcon(
    glyph: CardioHubGlyph,
    tint: Color,
    modifier: Modifier = Modifier.size(20.dp)
) {
    val domainGlyph = when (glyph) {
        CardioHubGlyph.RUN -> SuperhumanDomainGlyph.RUNNING
        CardioHubGlyph.WALK -> SuperhumanDomainGlyph.WALKING
        CardioHubGlyph.BIKE -> SuperhumanDomainGlyph.CYCLING
        CardioHubGlyph.ROW -> SuperhumanDomainGlyph.ROWING
        CardioHubGlyph.TROPHY -> SuperhumanDomainGlyph.TROPHY
        CardioHubGlyph.TREND,
        CardioHubGlyph.LOAD -> SuperhumanDomainGlyph.TREND
        CardioHubGlyph.HEART -> SuperhumanDomainGlyph.HEARTBEAT
        CardioHubGlyph.MORE -> SuperhumanDomainGlyph.MORE
        else -> null
    }

    if (domainGlyph != null) {
        SuperhumanDomainIcon(
            glyph = domainGlyph,
            tint = tint,
            modifier = modifier
        )
        return
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val s = size.minDimension * .09f
        when (glyph) {
            CardioHubGlyph.CALENDAR -> {
                drawRoundRect(tint, Offset(w * .16f, h * .24f), Size(w * .68f, h * .60f), style = Stroke(s))
                drawLine(tint, Offset(w * .16f, h * .41f), Offset(w * .84f, h * .41f), s * .75f)
                drawLine(tint, Offset(w * .32f, h * .15f), Offset(w * .32f, h * .31f), s, StrokeCap.Round)
                drawLine(tint, Offset(w * .68f, h * .15f), Offset(w * .68f, h * .31f), s, StrokeCap.Round)
            }
            CardioHubGlyph.TARGET -> {
                drawCircle(tint, w * .35f, center, style = Stroke(s))
                drawCircle(tint, w * .15f, center, style = Stroke(s))
                drawCircle(tint, w * .04f, center)
            }
            CardioHubGlyph.SENSOR -> {
                drawArc(tint, 210f, 120f, false, Offset(w * .12f, h * .16f), Size(w * .76f, h * .76f), style = Stroke(s))
                drawArc(tint, 220f, 100f, false, Offset(w * .27f, h * .31f), Size(w * .46f, h * .46f), style = Stroke(s))
                drawCircle(tint, w * .055f, center = Offset(w * .5f, h * .66f))
            }
            CardioHubGlyph.HISTORY -> {
                drawCircle(tint, w * .33f, center, style = Stroke(s))
                drawLine(tint, center, Offset(w * .5f, h * .31f), s, StrokeCap.Round)
                drawLine(tint, center, Offset(w * .67f, h * .58f), s, StrokeCap.Round)
            }
            CardioHubGlyph.PLUS -> {
                drawLine(tint, Offset(w * .2f, h * .5f), Offset(w * .8f, h * .5f), s, StrokeCap.Round)
                drawLine(tint, Offset(w * .5f, h * .2f), Offset(w * .5f, h * .8f), s, StrokeCap.Round)
            }
            CardioHubGlyph.HEART,
            CardioHubGlyph.TREND,
            CardioHubGlyph.LOAD,
            CardioHubGlyph.RUN,
            CardioHubGlyph.WALK,
            CardioHubGlyph.BIKE,
            CardioHubGlyph.ROW,
            CardioHubGlyph.TROPHY,
            CardioHubGlyph.MORE -> Unit
        }
    }
}

@Composable
private fun CardioHubSparkline(values: List<Double>, accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val span = (max - min).takeIf { it > 0.000001 } ?: 1.0
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1f)
            val y = size.height - ((value - min) / span).toFloat() * size.height * .78f - size.height * .11f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun CardioHubSignalRing(available: Int, total: Int, accent: Color, label: String) {
    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(46.dp)) {
            val stroke = 5.dp.toPx()
            drawArc(superhumanBorder.copy(alpha = .55f), -90f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
            val fraction = if (total > 0) available.toFloat() / total else 0f
            drawArc(accent, -90f, 360f * fraction, false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(label, color = superhumanTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CardioHubDots(filled: Int, total: Int, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { index ->
            Box(
                Modifier.size(7.dp)
                    .background(if (index < filled) accent else superhumanBorder, CircleShape)
            )
        }
    }
}

@Composable
private fun CardioHubZoneStrip(zones: Map<Int, Int>) {
    val total = zones.values.sum().takeIf { it > 0 } ?: return
    Row(Modifier.fillMaxWidth().height(5.dp)) {
        (1..5).forEach { zone ->
            val seconds = zones[zone] ?: 0
            if (seconds > 0) {
                Box(
                    Modifier.weight(seconds.toFloat() / total)
                        .height(5.dp)
                        .background(cardioHubZoneColor(zone))
                )
            }
        }
    }
}

@Composable
private fun CardioHubSheetTitle(glyph: CardioHubGlyph, title: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CardioHubIconBadge(glyph, accent)
        Spacer(Modifier.width(10.dp))
        Text(title, color = superhumanTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun CardioHubDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = superhumanTextMuted, fontSize = 9.sp, modifier = Modifier.width(118.dp))
        Text(value, color = superhumanTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CardioHubSignalDetail(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(superhumanSurfaceSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = superhumanTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(value, color = superhumanBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CardioHubSheetAction(label: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 50.dp)
            .background(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .18f else .10f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text("›", color = accent, fontSize = 22.sp)
    }
}

private fun cardioHubEfficiencySeries(sessions: List<CardioSession>): List<Double> =
    sessions.asSequence()
        .filter {
            it.activity == CardioActivityType.RUNNING ||
                it.activity == CardioActivityType.WALKING ||
                it.activity == CardioActivityType.TREADMILL
        }
        .sortedBy { it.endedAt }
        .mapNotNull { session ->
            val pace = session.avgPaceSecPerKm?.takeIf { it > 0 } ?: return@mapNotNull null
            val hr = session.avgHeartRate?.takeIf { it > 0 } ?: return@mapNotNull null
            (1000.0 / pace) / hr
        }
        .toList()
        .takeLast(10)

private fun cardioHubShortReadiness(status: String): String = when {
    status.contains("mixed", true) -> "Mixed"
    status.contains("below", true) -> "Low"
    status.contains("normal", true) -> "Good"
    status.contains("building", true) -> "Building"
    else -> status
}

private fun cardioHubBaselineState(current: Double?, baseline: Double?, lowerIsUsuallyBetter: Boolean): String {
    if (current == null || baseline == null || baseline <= 0.0) return "Unavailable"
    val deltaPct = (current - baseline) / baseline * 100.0
    if (kotlin.math.abs(deltaPct) < 3.0) return "Near baseline"
    val favourable = if (lowerIsUsuallyBetter) deltaPct < 0 else deltaPct > 0
    return if (favourable) "Favourable vs baseline" else "Below recovery baseline"
}

private fun cardioHubDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun cardioHubActivityGlyph(activity: CardioActivityType): CardioHubGlyph = when (activity) {
    CardioActivityType.RUNNING, CardioActivityType.TREADMILL -> CardioHubGlyph.RUN
    CardioActivityType.WALKING, CardioActivityType.HIKING -> CardioHubGlyph.WALK
    CardioActivityType.CYCLING, CardioActivityType.STATIONARY_BIKE -> CardioHubGlyph.BIKE
    else -> CardioHubGlyph.MORE
}

private fun cardioHubActivityColor(activity: CardioActivityType): Color = when (activity) {
    CardioActivityType.RUNNING, CardioActivityType.TREADMILL -> superhumanGreen
    CardioActivityType.WALKING, CardioActivityType.HIKING -> superhumanBlue
    CardioActivityType.CYCLING, CardioActivityType.STATIONARY_BIKE -> Color(0xFF8E72D8)
    else -> Color(0xFFD1A03D)
}

private fun cardioHubZoneColor(zone: Int): Color = when (zone) {
    1 -> Color(0xFF6FA8DC)
    2 -> Color(0xFF67C59B)
    3 -> Color(0xFFD8B65C)
    4 -> Color(0xFFE58A5B)
    else -> Color(0xFFD9656C)
}
