package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

internal data class CardioProductOverviewModel(
    val fitness: CardioFitnessSnapshot,
    val readiness: CardioReadinessSnapshot,
    val week: CardioWeekIntentSnapshot
)

internal fun buildCardioProductOverviewModel(
    sessions: List<CardioSession>,
    targetMinutes: Int? = null,
    nowEpochMs: Long = System.currentTimeMillis()
): CardioProductOverviewModel {
    val fitness = CardioPersonalBaselineEngine.fitnessSnapshot(sessions, nowEpochMs)
    val load = CardioTrainingLoadEngine.latest(sessions, nowEpochMs)
    val loadAnalytics = CardioAnalyticsEngine.loadAnalytics(sessions, nowEpochMs)
    val readiness = CardioPersonalBaselineEngine.readiness(
        CardioRecoveryContext(
            trainingStressBalance = load?.trainingStressBalance
                ?.takeIf { loadAnalytics.scoredSessions > 0 }
        )
    )
    val week = CardioPersonalBaselineEngine.weekIntent(
        sessions = sessions,
        targetMinutes = targetMinutes,
        nowEpochMs = nowEpochMs
    )
    return CardioProductOverviewModel(fitness, readiness, week)
}

internal fun cardioProductSensorStatus(metrics: CardioLiveSensorMetrics): String = when (metrics.connection) {
    CardioSensorConnectionState.CONNECTED -> {
        val age = metrics.lastSampleAgeMs?.let(::cardioProductFreshness).orEmpty()
        listOfNotNull(
            metrics.sourceLabel.takeIf { it.isNotBlank() },
            metrics.currentHeartRateBpm?.let { "$it bpm" },
            age.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
    }
    CardioSensorConnectionState.RECONNECTING -> metrics.sourceLabel + " · Reconnecting"
    CardioSensorConnectionState.SCANNING -> "Searching for heart-rate sensor"
    CardioSensorConnectionState.CONNECTING -> metrics.sourceLabel + " · Connecting"
    CardioSensorConnectionState.STALE -> metrics.sourceLabel + " · Signal stale"
    CardioSensorConnectionState.ERROR -> metrics.sourceLabel + " · Sensor error"
    CardioSensorConnectionState.DISCONNECTED -> metrics.sourceLabel + " · Disconnected"
    CardioSensorConnectionState.NO_SENSOR -> "No live sensor"
}

private fun cardioProductFreshness(ageMs: Long): String = when {
    ageMs < 1_000L -> "live"
    ageMs < 60_000L -> "updated ${ageMs / 1_000L}s ago"
    else -> "updated ${ageMs / 60_000L}m ago"
}

@Composable
internal fun CardioProductOverview(
    sessions: List<CardioSession>,
    sensorMetrics: CardioLiveSensorMetrics,
    onSessions: () -> Unit,
    onFitness: () -> Unit,
    onTrends: () -> Unit,
    onTestsRecords: () -> Unit
) {
    val model = remember(sessions) { buildCardioProductOverviewModel(sessions) }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "AT A GLANCE",
            color = superhumanTextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = .8.sp
        )

        CardioGlanceCard(
            eyebrow = "FITNESS",
            value = model.fitness.trendLabel,
            detail = model.fitness.trendDeltaPercent?.let {
                val sign = if (it > 0) "+" else ""
                "$sign${String.format(Locale.US, "%.1f", it)}% pace/HR efficiency vs prior baseline"
            } ?: model.fitness.basis,
            meta = when (model.fitness.confidence) {
                CardioConfidence.INSUFFICIENT ->
                    "Building baseline · ${model.fitness.comparableSessionCount} comparable sessions"
                else -> "${model.fitness.confidence.label} confidence · ${model.fitness.comparableSessionCount} comparable sessions"
            },
            accent = superhumanGreen,
            onClick = onFitness
        )

        CardioGlanceCard(
            eyebrow = "TODAY / TRAINING CAPACITY",
            value = model.readiness.status,
            detail = model.readiness.explanation,
            meta = when {
                model.readiness.availableSignals == 0 -> "Building baseline"
                else -> "${model.readiness.availableSignals}/${model.readiness.totalSignals} signals · ${model.readiness.confidence.label} confidence"
            },
            accent = superhumanBlue,
            onClick = onTrends
        )

        CardioWeekGlance(model.week, onTrends)
        CardioSensorSourceCard(sensorMetrics)

        Text(
            "EXPLORE",
            color = superhumanTextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = .8.sp
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardioProductNavButton("SESSIONS", "History", superhumanBlue, Modifier.weight(1f), onSessions)
            CardioProductNavButton("FITNESS", "Am I fitter?", superhumanGreen, Modifier.weight(1f), onFitness)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardioProductNavButton("TRENDS", "Over time", Color(0xFF7B61C9), Modifier.weight(1f), onTrends)
            CardioProductNavButton("TESTS & RECORDS", "Verified bests", Color(0xFFC9902E), Modifier.weight(1f), onTestsRecords)
        }
    }
}

@Composable
private fun CardioGlanceCard(
    eyebrow: String,
    value: String,
    detail: String,
    meta: String,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(20.dp))
            .border(1.dp, superhumanBorder, RoundedCornerShape(20.dp))
            .semantics {
                role = Role.Button
                contentDescription = "$eyebrow. $value. $detail. $meta"
            }
            .clickable { onClick() }
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(eyebrow, color = accent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = superhumanTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("›", color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Text(detail, color = superhumanTextMuted, fontSize = 10.sp, lineHeight = 14.sp)
        Text(meta, color = accent, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CardioWeekGlance(week: CardioWeekIntentSnapshot, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(20.dp))
            .border(1.dp, superhumanBorder, RoundedCornerShape(20.dp))
            .semantics {
                role = Role.Button
                contentDescription = "This week. ${week.minutes} minutes, ${week.sessions} sessions, ${week.zone2Minutes} Zone 2 minutes."
            }
            .clickable { onClick() }
            .padding(15.dp)
    ) {
        Text("THIS WEEK", color = Color(0xFF7B61C9), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth()) {
            CardioProductMiniStat("MIN", week.minutes.toString(), Modifier.weight(1f))
            CardioProductMiniStat("SESSIONS", week.sessions.toString(), Modifier.weight(1f))
            CardioProductMiniStat("ZONE 2", week.zone2Minutes.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            week.targetMinutes?.let { week.label } ?: "No weekly target set · current training is still tracked",
            color = superhumanTextMuted,
            fontSize = 9.sp
        )
        week.progressFraction?.let { raw ->
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().height(7.dp)
                    .background(superhumanBorder.copy(alpha = .55f), RoundedCornerShape(7.dp))
            ) {
                Box(
                    Modifier.fillMaxWidth(raw.toFloat().coerceIn(0f, 1f)).height(7.dp)
                        .background(superhumanGreen, RoundedCornerShape(7.dp))
                )
            }
        }
    }
}

@Composable
private fun CardioProductMiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(end = 8.dp)) {
        Text(label, color = superhumanTextMuted, fontSize = 7.sp, fontWeight = FontWeight.Black)
        Text(value, color = superhumanTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CardioSensorSourceCard(metrics: CardioLiveSensorMetrics) {
    val status = cardioProductSensorStatus(metrics)
    val connected = metrics.connection == CardioSensorConnectionState.CONNECTED
    val accent = if (connected) superhumanGreen else superhumanBlue
    Row(
        Modifier
            .fillMaxWidth()
            .background(superhumanSurfaceSoft, RoundedCornerShape(16.dp))
            .semantics {
                role = Role.Button
                contentDescription = "$status. Manage devices."
            }
            .clickable { SmartDevicesNavigationBridge.open?.invoke() }
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(34.dp).background(accent, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(status, color = superhumanTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                if (connected) "Live source · tap to manage devices" else "Tap to manage devices",
                color = superhumanTextMuted,
                fontSize = 9.sp
            )
        }
        Text("MANAGE", color = accent, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CardioProductNavButton(
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .heightIn(min = 72.dp)
            .background(superhumanSurface, RoundedCornerShape(17.dp))
            .border(1.dp, superhumanBorder, RoundedCornerShape(17.dp))
            .semantics {
                role = Role.Button
                contentDescription = "$title. $subtitle"
            }
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Text(title, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = superhumanTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun CardioFitnessProductScreen(sessions: List<CardioSession>) {
    val fitness = remember(sessions) { CardioPersonalBaselineEngine.fitnessSnapshot(sessions) }
    val comparableActivities = remember(sessions) {
        sessions.map { it.activity }
            .distinct()
            .filter {
                it == CardioActivityType.RUNNING ||
                    it == CardioActivityType.WALKING ||
                    it == CardioActivityType.TREADMILL
            }
    }

    CardioProductSection(
        title = "AEROBIC FITNESS",
        subtitle = "Longitudinal answer to: am I getting fitter?"
    ) {
        Text(fitness.trendLabel, color = superhumanTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Black)
        fitness.trendDeltaPercent?.let {
            val sign = if (it > 0) "+" else ""
            Text(
                "$sign${String.format(Locale.US, "%.1f", it)}% pace/HR efficiency vs prior baseline",
                color = superhumanGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(fitness.basis, color = superhumanTextMuted, fontSize = 10.sp, lineHeight = 15.sp)
        Text(
            when (fitness.confidence) {
                CardioConfidence.INSUFFICIENT -> "Building baseline — ${fitness.comparableSessionCount}/4 minimum comparable sessions"
                else -> "${fitness.confidence.label} confidence · ${fitness.comparableSessionCount} comparable sessions"
            },
            color = superhumanBlue,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }

    CardioProductSection("FITNESS SIGNALS", "Only show metrics the current data can support") {
        CardioCapabilityRow(
            "Pace / HR efficiency",
            if (fitness.trendDeltaPercent != null) "Available" else "Building baseline",
            fitness.basis
        )
        CardioCapabilityRow(
            "Pace at HR / HR at pace",
            if (comparableActivities.isNotEmpty()) "Session trends available" else "Requires pace + workout HR",
            "Use like-for-like running, walking or treadmill sessions."
        )
        CardioCapabilityRow(
            "Aerobic decoupling",
            "Requires workout HR time-series",
            "Not shown as a zero when continuous HR evidence is absent."
        )
        CardioCapabilityRow(
            "HR recovery",
            "Requires post-effort HR time-series",
            "Unlock when the analytics layer exposes a valid recovery series."
        )
        CardioCapabilityRow(
            "VO₂ estimate / critical speed",
            "Unavailable",
            "Shown only when a validated calculation contract is available."
        )
    }

    comparableActivities.firstOrNull()?.let { activity ->
        CardioProductSection("${activity.displayName.uppercase()} DETAIL", "Progressive detail for recorded metrics") {
            CardioActivityProgressPanel(
                activity = activity,
                sessions = sessions,
                range = CardioAnalysisRange.MONTHS_3
            )
        }
    }
}

@Composable
internal fun CardioTrendsProductScreen(sessions: List<CardioSession>) {
    val load = remember(sessions) { CardioTrainingLoadEngine.latest(sessions) }
    CardioProductSection("TRAINING STATE", "Load values are training-management signals, not medical clearance") {
        if (load == null) {
            CardioUnlockState("Building load baseline", "Record sessions with measured zones or RPE to score training load.")
        } else {
            Row(Modifier.fillMaxWidth()) {
                CardioProductMiniStat("CTL", load.chronicLoad.roundToInt().toString(), Modifier.weight(1f))
                CardioProductMiniStat("ATL", load.acuteLoad.roundToInt().toString(), Modifier.weight(1f))
                CardioProductMiniStat("TSB", load.trainingStressBalance.roundToInt().toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${load.scoredSessionCount} scored session${if (load.scoredSessionCount == 1) "" else "s"} contributed on the latest day.",
                color = superhumanTextMuted,
                fontSize = 9.sp
            )
        }
    }
    CardioAnalyticsProgressPanel(sessions)
}

@Composable
internal fun CardioTestsAndRecordsProductScreen(sessions: List<CardioSession>) {
    CardioAnalyticsRecordsPanel(sessions)

    CardioProductSection("FIELD TESTS", "Structured tests appear only when their execution/evidence contract exists") {
        CardioCapabilityRow(
            "HRmax review candidates",
            "Evidence-gated",
            "Candidate observations require repeated valid samples; they never silently replace configured physiology."
        )
        CardioCapabilityRow(
            "Threshold test",
            "Protocol contract pending",
            "No threshold value is fabricated from ordinary session averages."
        )
        CardioCapabilityRow(
            "Critical-speed test",
            "Protocol contract pending",
            "Unlock when exact-distance efforts and the analytical model are available."
        )
    }

    CardioProductSection("RECORD EVIDENCE", "Exact-distance records require exact-distance evidence") {
        Text(
            "1 km, mile, 5 km, 10 km, 500 m and 2 km records remain unavailable until valid lap, split or route evidence supports the exact distance.",
            color = superhumanTextMuted,
            fontSize = 10.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
internal fun CardioSessionDataAndCalculationPanel(session: CardioSession) {
    var expanded by remember(session.id) { mutableStateOf(false) }
    val load = remember(session) { CardioAnalyticsEngine.loadDetail(session) }
    val sensorSource = session.extensions["sensorSourceName"]
        ?: session.extensions["sensorDeviceName"]
        ?: session.source
    val provider = session.extensions["sensorProviderType"]
    val coverage = session.extensions["heartRateCoveragePct"]?.toDoubleOrNull()
        ?: load.zoneCoveragePercent.takeIf { it > 0.0 }

    Column(
        Modifier
            .fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(19.dp))
            .border(1.dp, superhumanBorder, RoundedCornerShape(19.dp))
            .semantics {
                role = Role.Button
                contentDescription = "Data and calculation. ${if (expanded) "Expanded" else "Collapsed"}."
            }
            .clickable { expanded = !expanded }
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("DATA & CALCULATION", color = superhumanTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text("Where did these numbers come from?", color = superhumanTextMuted, fontSize = 9.sp)
            }
            Text(if (expanded) "−" else "+", color = superhumanBlue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            CardioEvidenceRow("Session source", sensorSource)
            provider?.let { CardioEvidenceRow("Provider", it.replace('_', ' ')) }
            coverage?.let { CardioEvidenceRow("HR coverage", "${String.format(Locale.US, "%.1f", it)}%") }
            CardioEvidenceRow(
                "Heart rate",
                if (session.avgHeartRate != null) "Measured / recorded" else "Unavailable"
            )
            CardioEvidenceRow(
                "Distance",
                if (session.distanceKm != null) "Recorded · ${session.source}" else "Unavailable"
            )
            CardioEvidenceRow(
                "Training load",
                load.score?.let { "${load.source.label} · ${it.roundToInt()}" } ?: "Unavailable"
            )
            CardioEvidenceRow("Session schema", "v${session.schemaVersion}")
            session.extensions["processingVersion"]?.let { CardioEvidenceRow("Processing", it) }
            Text(
                "Derived values depend on stored session evidence. Missing inputs remain unavailable rather than being back-filled with invented values.",
                color = superhumanTextMuted,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun CardioEvidenceRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = superhumanTextMuted, fontSize = 9.sp, modifier = Modifier.weight(1f))
        Text(value, color = superhumanTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CardioProductSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(20.dp))
            .border(1.dp, superhumanBorder, RoundedCornerShape(20.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = superhumanTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = superhumanTextMuted, fontSize = 9.sp, lineHeight = 13.sp)
        content()
    }
}

@Composable
private fun CardioCapabilityRow(title: String, state: String, detail: String) {
    Column(
        Modifier.fillMaxWidth()
            .background(superhumanSurfaceSoft, RoundedCornerShape(14.dp))
            .padding(11.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = superhumanTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(state, color = superhumanBlue, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
        Text(detail, color = superhumanTextMuted, fontSize = 9.sp, lineHeight = 13.sp)
    }
}

@Composable
private fun CardioUnlockState(title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth()
            .background(superhumanSurfaceSoft, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(title, color = superhumanTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = superhumanTextMuted, fontSize = 9.sp, lineHeight = 13.sp)
    }
}
