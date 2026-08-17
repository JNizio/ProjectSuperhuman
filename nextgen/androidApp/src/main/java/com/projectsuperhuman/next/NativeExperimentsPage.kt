package com.projectsuperhuman.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val ExperimentNavy get() = superhumanBrandText
internal val ExperimentInk get() = superhumanTextPrimary
internal val ExperimentBlue get() = superhumanBlue
internal val ExperimentViolet get() = if (SuperhumanAppearance.darkMode) Color(0xFFA58BE6) else Color(0xFF7257B7)
internal val ExperimentTeal get() = if (SuperhumanAppearance.darkMode) Color(0xFF59C8AF) else Color(0xFF20A7A5)
internal val ExperimentAmber get() = if (SuperhumanAppearance.darkMode) Color(0xFFFFB85F) else Color(0xFFD58A32)
internal val ExperimentMuted get() = superhumanTextMuted
internal val ExperimentBorder get() = superhumanBorder
internal val ExperimentCard get() = superhumanSurfaceElevated
internal val ExperimentBackground get() = superhumanBackground

private enum class ExperimentsPage { DASHBOARD, DETAIL, CREATE }

@Composable
internal fun NativeExperimentsPage(onBack: () -> Unit) {
    var page by remember { mutableStateOf(ExperimentsPage.DASHBOARD) }
    var selected by remember { mutableStateOf(MockExperimentData.active) }
    when (page) {
        ExperimentsPage.DASHBOARD -> ExperimentDashboardScreen(MockExperimentData.active, MockExperimentData.recent, MockExperimentData.devices, onBack, { page = ExperimentsPage.CREATE }) {
            selected = it; page = ExperimentsPage.DETAIL
        }
        ExperimentsPage.DETAIL -> ExperimentDetailScreen(selected) { page = ExperimentsPage.DASHBOARD }
        ExperimentsPage.CREATE -> ExperimentCreationFlow(
            onBack = { page = ExperimentsPage.DASHBOARD },
            onPreviewProtocol = { draft -> selected = MockExperimentData.fromDraft(draft); page = ExperimentsPage.DETAIL }
        )
    }
}

@Composable
internal fun ExperimentDashboardScreen(
    active: ExperimentPresentation,
    recent: List<ExperimentPresentation>,
    devices: List<ExperimentDevice>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenExperiment: (ExperimentPresentation) -> Unit
) {
    Column(Modifier.fillMaxSize().background(ExperimentBackground)) {
        ExperimentsHeader("Experiments", "Your personal laboratory", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            ExperimentLabHero(onCreate)
            ActiveExperimentCard(active) { onOpenExperiment(active) }
            NextCheckInCard(active)
            SectionTitle("RECENT EXPERIMENTS", "Past protocols and early signals")
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) { recent.forEach { experiment -> RecentExperimentRow(experiment) { onOpenExperiment(experiment) } } }
            SectionTitle("DEVICES", "Flexible inputs for future protocols")
            DeviceCarousel(devices)
            MockBoundaryNote()
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
internal fun ExperimentsHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.superhumanTopButton(onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) { Text("←", color = ExperimentNavy, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = ExperimentNavy, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = ExperimentMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun ExperimentLabHero(onCreate: () -> Unit) {
    val gradient = if (SuperhumanAppearance.darkMode) listOf(Color(0xFF211C31), Color(0xFF111D28), Color(0xFF102A28))
        else listOf(Color(0xFFF0ECFC), Color(0xFFF6FBFD), Color(0xFFEBF8F5))
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(gradient), RoundedCornerShape(27.dp)).border(1.dp, ExperimentBorder, RoundedCornerShape(27.dp)).padding(19.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PERSONAL LAB", color = ExperimentViolet, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(6.dp)); Text("Turn questions into protocols", color = ExperimentInk, fontSize = 23.sp, fontWeight = FontWeight.Black, lineHeight = 27.sp)
            }
            LabNetworkGlyph(Modifier.size(64.dp))
        }
        Spacer(Modifier.height(7.dp)); Text("Choose one change, decide what matters, and follow it long enough to learn something useful about yourself.", color = ExperimentMuted, fontSize = 10.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(13.dp)); ExperimentPrimaryButton("CREATE EXPERIMENT", onCreate)
    }
}

@Composable
private fun LabNetworkGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val points = listOf(Offset(size.width * .18f, size.height * .68f), Offset(size.width * .43f, size.height * .25f), Offset(size.width * .78f, size.height * .42f), Offset(size.width * .70f, size.height * .78f))
        val path = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(path, ExperimentViolet.copy(alpha = .35f), style = Stroke(3f))
        points.forEachIndexed { index, point -> drawCircle(if (index == 2) ExperimentTeal else ExperimentViolet, radius = if (index == 2) 7f else 5f, center = point) }
    }
}

@Composable
private fun ActiveExperimentCard(experiment: ExperimentPresentation, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(ExperimentCard, RoundedCornerShape(26.dp)).border(1.dp, ExperimentBorder, RoundedCornerShape(26.dp)).clickable(onClick = onClick).padding(17.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("ACTIVE EXPERIMENT", color = ExperimentTeal, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                Spacer(Modifier.height(4.dp)); Text(experiment.title, color = ExperimentInk, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text(experiment.phase.label, color = ExperimentViolet, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            ExperimentProgressRing(experiment.progress.fraction, "${experiment.progress.currentDay}/${experiment.progress.totalDays}", Modifier.size(68.dp))
        }
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutcomeSummaryChip("PRIMARY", experiment.primaryOutcome?.title ?: "Not selected", Modifier.weight(1f))
            OutcomeSummaryChip("SECONDARY", experiment.outcomes.filter { it.role == ExperimentOutcomeRole.SECONDARY }.joinToString(" · ") { it.title }.ifBlank { "None" }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(11.dp)); ProtocolProgressBar(experiment.progress.fraction); Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Day ${experiment.progress.currentDay} of ${experiment.progress.totalDays}", color = ExperimentMuted, fontSize = 8.sp)
            Text("VIEW PROTOCOL  →", color = ExperimentBlue, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun OutcomeSummaryChip(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(superhumanSurfaceSoft, RoundedCornerShape(16.dp)).padding(horizontal = 11.dp, vertical = 9.dp)) {
        Text(label, color = ExperimentMuted, fontSize = 6.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
        Spacer(Modifier.height(3.dp)); Text(value, color = ExperimentNavy, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ProtocolProgressBar(progress: Float) {
    Box(Modifier.fillMaxWidth().height(7.dp).background(superhumanSurfaceSoft, RoundedCornerShape(5.dp))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(7.dp).background(Brush.horizontalGradient(listOf(ExperimentViolet, ExperimentTeal)), RoundedCornerShape(5.dp)))
    }
}

@Composable
private fun NextCheckInCard(experiment: ExperimentPresentation) {
    Row(
        Modifier.fillMaxWidth().background(if (SuperhumanAppearance.darkMode) Color(0xFF102A28) else Color(0xFFEDF8F6), RoundedCornerShape(21.dp))
            .border(1.dp, ExperimentTeal.copy(alpha = .18f), RoundedCornerShape(21.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).background(superhumanSurface, CircleShape), contentAlignment = Alignment.Center) { Text("✓", color = ExperimentTeal, fontSize = 17.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("NEXT CHECK-IN", color = ExperimentMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .85.sp)
            Text(experiment.nextCheckIn ?: "No check-in scheduled", color = ExperimentNavy, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        Text("${experiment.progress.completedCheckIns}/${experiment.progress.plannedCheckIns}", color = ExperimentTeal, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SectionTitle(label: String, subtitle: String) {
    Column(Modifier.padding(top = 5.dp, start = 2.dp)) {
        Text(label, color = ExperimentMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        Text(subtitle, color = ExperimentNavy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecentExperimentRow(experiment: ExperimentPresentation, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(superhumanSurface, RoundedCornerShape(20.dp)).border(1.dp, ExperimentBorder, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(ExperimentViolet.copy(alpha = .12f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text("●", color = ExperimentViolet, fontSize = 14.sp) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(experiment.title, color = ExperimentInk, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(experiment.primaryOutcome?.title ?: "Custom outcome", color = ExperimentMuted, fontSize = 8.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(experiment.result?.primaryChange ?: "—", color = ExperimentNavy, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(experiment.result?.confidence?.label ?: experiment.status.label, color = confidenceColor(experiment.result?.confidence), fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(7.dp)); Text("›", color = ExperimentBlue, fontSize = 20.sp)
    }
}

@Composable
private fun DeviceCarousel(devices: List<ExperimentDevice>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) { devices.forEach { DeviceCard(it, Modifier.width(154.dp)) } }
}

@Composable
internal fun DeviceCard(device: ExperimentDevice, modifier: Modifier = Modifier) {
    val tone = when (device.status) { ExperimentDeviceStatus.CONNECTED -> ExperimentTeal; ExperimentDeviceStatus.NOT_CONNECTED -> ExperimentAmber; ExperimentDeviceStatus.AVAILABLE_LATER -> ExperimentMuted }
    Column(modifier.height(116.dp).background(superhumanSurface, RoundedCornerShape(20.dp)).border(1.dp, ExperimentBorder, RoundedCornerShape(20.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(tone, CircleShape)); Spacer(Modifier.width(6.dp))
            Text(device.status.label.uppercase(), color = tone, fontSize = 6.sp, fontWeight = FontWeight.Black, letterSpacing = .55.sp)
        }
        Spacer(Modifier.height(9.dp)); Text(device.title, color = ExperimentInk, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.height(3.dp)); Text(device.description, color = ExperimentMuted, fontSize = 8.sp, lineHeight = 11.sp, maxLines = 2)
    }
}

@Composable
private fun MockBoundaryNote() {
    Text(
        "Device states and experiment results are presentation previews. No hardware connection, permission request, analysis or data saving occurs in this module yet.",
        color = ExperimentMuted, fontSize = 8.sp, lineHeight = 12.sp,
        modifier = Modifier.fillMaxWidth().background(superhumanSurfaceSoft, RoundedCornerShape(16.dp)).padding(12.dp)
    )
}

@Composable
internal fun ExperimentDetailScreen(experiment: ExperimentPresentation, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(ExperimentBackground)) {
        ExperimentsHeader("Experiment", experiment.status.label, onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ExperimentDetailHero(experiment); ProtocolQuestionCard(experiment); ExperimentTimelineCard(experiment); OutcomesCard(experiment)
            if (experiment.observations.isNotEmpty()) ObservationsCard(experiment.observations)
            experiment.notes?.let { NotesCard(it) }
            experiment.result?.let { ExperimentResultCard(experiment, it) }
            if (experiment.devices.isNotEmpty()) { SectionTitle("DEVICES & INPUTS", "Presentation-only sources"); DeviceCarousel(experiment.devices) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ExperimentDetailHero(experiment: ExperimentPresentation) {
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF171E3B), Color(0xFF243C64), Color(0xFF226C71))), RoundedCornerShape(28.dp)).padding(19.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(experiment.phase.label.uppercase(), color = Color(0xFF78DDD0), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                Spacer(Modifier.height(5.dp)); Text(experiment.title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, lineHeight = 27.sp)
            }
            ExperimentProgressRing(experiment.progress.fraction, "${experiment.progress.currentDay}/${experiment.progress.totalDays}", Modifier.size(70.dp), foreground = Color(0xFF78DDD0), textColor = Color.White)
        }
        Spacer(Modifier.height(12.dp)); Text(experiment.scheduleSummary, color = Color.White.copy(alpha = .72f), fontSize = 9.sp)
        experiment.nextCheckIn?.let { Spacer(Modifier.height(8.dp)); Text("NEXT · $it", color = Color(0xFF78DDD0), fontSize = 8.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun ProtocolQuestionCard(experiment: ExperimentPresentation) {
    Column(Modifier.experimentCard()) {
        CardEyebrow("THE QUESTION"); Spacer(Modifier.height(6.dp)); Text(experiment.hypothesis, color = ExperimentInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp)
        Spacer(Modifier.height(13.dp))
        Box(Modifier.fillMaxWidth().background(if (SuperhumanAppearance.darkMode) Color(0xFF211C31) else Color(0xFFF2F0FA), RoundedCornerShape(17.dp)).padding(12.dp)) {
            Column {
                Text("INTERVENTION", color = ExperimentViolet, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                Text(experiment.intervention.title, color = ExperimentNavy, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text(experiment.intervention.instructions, color = ExperimentMuted, fontSize = 8.sp, lineHeight = 12.sp)
            }
        }
    }
}

@Composable
private fun ExperimentTimelineCard(experiment: ExperimentPresentation) {
    Column(Modifier.experimentCard()) {
        CardEyebrow("PROTOCOL TIMELINE"); Spacer(Modifier.height(12.dp))
        experiment.timeline.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(14.dp).background(if (item.completed || item.phase == experiment.phase) ExperimentViolet else superhumanSurfaceSoft, CircleShape), contentAlignment = Alignment.Center) {
                        if (item.completed) Text("✓", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    }
                    if (index != experiment.timeline.lastIndex) Box(Modifier.width(2.dp).height(31.dp).background(superhumanDivider))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.padding(bottom = if (index == experiment.timeline.lastIndex) 0.dp else 12.dp)) {
                    Text(item.title, color = ExperimentInk, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(item.detail, color = ExperimentMuted, fontSize = 8.sp)
                }
            }
        }
    }
}

@Composable
private fun OutcomesCard(experiment: ExperimentPresentation) {
    Column(Modifier.experimentCard()) {
        CardEyebrow("SELECTED OUTCOMES"); Spacer(Modifier.height(10.dp))
        experiment.outcomes.forEachIndexed { index, outcome ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).background(if (outcome.role == ExperimentOutcomeRole.PRIMARY) ExperimentViolet.copy(alpha = .14f) else superhumanSurfaceSoft, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Text(if (outcome.role == ExperimentOutcomeRole.PRIMARY) "1" else "·", color = ExperimentViolet, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(outcome.title, color = ExperimentInk, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(outcome.role.name.lowercase().replaceFirstChar { it.uppercase() }, color = ExperimentMuted, fontSize = 7.sp)
                }
                Text(outcome.duringValue?.let { String.format("%.1f", it) } ?: "Not recorded", color = ExperimentNavy, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (index != experiment.outcomes.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(superhumanDivider))
        }
    }
}

@Composable
private fun ObservationsCard(observations: List<ExperimentObservation>) {
    Column(Modifier.experimentCard()) {
        CardEyebrow("DAILY OBSERVATIONS"); Spacer(Modifier.height(9.dp))
        observations.forEach { observation ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(8.dp).background(if (observation.completed) ExperimentTeal else ExperimentBorder, CircleShape).padding(top = 4.dp))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(observation.dayLabel, color = ExperimentMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    Text(observation.primaryValue, color = ExperimentInk, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    observation.note?.let { Text(it, color = ExperimentMuted, fontSize = 8.sp, lineHeight = 12.sp) }
                }
                Text(observation.phase.label, color = ExperimentViolet, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NotesCard(notes: String) {
    Column(Modifier.fillMaxWidth().background(superhumanWarningSurface, RoundedCornerShape(20.dp)).border(1.dp, ExperimentAmber.copy(alpha = .24f), RoundedCornerShape(20.dp)).padding(14.dp)) {
        Text("NOTES", color = ExperimentAmber, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
        Spacer(Modifier.height(4.dp)); Text(notes, color = ExperimentInk, fontSize = 9.sp, lineHeight = 14.sp)
    }
}

@Composable
internal fun ExperimentResultCard(experiment: ExperimentPresentation, result: ExperimentResultSummary) {
    val gradient = if (SuperhumanAppearance.darkMode) listOf(Color(0xFF211C31), superhumanSurfaceElevated) else listOf(Color(0xFFF2EFFA), Color.White)
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(gradient), RoundedCornerShape(25.dp)).border(1.dp, ExperimentViolet.copy(alpha = .20f), RoundedCornerShape(25.dp)).padding(17.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(if (result.isFinal) "RESULT SUMMARY" else "EARLY PREVIEW", color = ExperimentViolet, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("What changed", color = ExperimentInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            ConfidencePill(result.confidence)
        }
        Spacer(Modifier.height(11.dp)); Text(result.headline, color = ExperimentNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
        Spacer(Modifier.height(13.dp)); experiment.primaryOutcome?.let { BeforeDuringComparison(it) }
        if (result.secondaryChanges.isNotEmpty()) {
            Spacer(Modifier.height(12.dp)); Text("SECONDARY OUTCOMES", color = ExperimentMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp); Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                result.secondaryChanges.forEach { change -> Text(change, color = ExperimentNavy, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(superhumanSurface, RoundedCornerShape(12.dp)).padding(horizontal = 9.dp, vertical = 6.dp)) }
            }
        }
        Spacer(Modifier.height(12.dp)); Text(result.confidence.explanation, color = ExperimentMuted, fontSize = 8.sp, lineHeight = 12.sp)
        if (!result.isFinal) { Spacer(Modifier.height(7.dp)); Text("Incomplete experiment · this preview can change as more check-ins are added.", color = ExperimentAmber, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun BeforeDuringComparison(outcome: ExperimentOutcome) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ResultValue("BEFORE", outcome.beforeValue, outcome.unit, Modifier.weight(1f)); Text("vs", color = ExperimentMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold); ResultValue("DURING", outcome.duringValue, outcome.unit, Modifier.weight(1f))
    }
}

@Composable
private fun ResultValue(label: String, value: Double?, unit: String, modifier: Modifier) {
    Column(modifier.background(superhumanSurface, RoundedCornerShape(17.dp)).padding(11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = ExperimentMuted, fontSize = 6.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
        Text(value?.let { String.format("%.1f", it) } ?: "—", color = ExperimentInk, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(unit, color = ExperimentMuted, fontSize = 7.sp)
    }
}

@Composable
private fun ConfidencePill(confidence: ExperimentConfidence) {
    val tone = confidenceColor(confidence)
    Text(confidence.label, color = tone, fontSize = 7.sp, fontWeight = FontWeight.Black, modifier = Modifier.background(tone.copy(alpha = .11f), RoundedCornerShape(12.dp)).padding(horizontal = 9.dp, vertical = 6.dp))
}

internal fun confidenceColor(confidence: ExperimentConfidence?): Color = when (confidence) {
    ExperimentConfidence.TOO_EARLY, null -> ExperimentMuted
    ExperimentConfidence.POSSIBLE_SIGNAL -> ExperimentAmber
    ExperimentConfidence.CONSISTENT_CHANGE -> ExperimentTeal
}

@Composable
internal fun ExperimentPrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier.fillMaxWidth().height(46.dp).background(
            if (enabled) Brush.horizontalGradient(listOf(ExperimentViolet, if (SuperhumanAppearance.darkMode) Color(0xFF4B79A8) else Color(0xFF537CB7)))
            else Brush.horizontalGradient(listOf(superhumanSurfaceSoft, superhumanSurfaceSoft)), RoundedCornerShape(16.dp)
        ).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center
    ) { Text(label, color = if (enabled) Color.White else ExperimentMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp) }
}

@Composable
internal fun CardEyebrow(label: String) { Text(label, color = ExperimentMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp) }

internal fun Modifier.experimentCard(): Modifier = this.fillMaxWidth().background(ExperimentCard, RoundedCornerShape(23.dp)).border(1.dp, ExperimentBorder, RoundedCornerShape(23.dp)).padding(16.dp)

@Preview(name = "Experiments · Dashboard", showBackground = true, backgroundColor = 0xFFF8FBFD)
@Composable
private fun ExperimentsDashboardPreview() { ProjectSuperhumanTheme { ExperimentDashboardScreen(MockExperimentData.active, MockExperimentData.recent, MockExperimentData.devices, {}, {}, {}) } }

@Preview(name = "Experiments · Detail", showBackground = true, backgroundColor = 0xFFF8FBFD)
@Composable
private fun ExperimentDetailPreview() { ProjectSuperhumanTheme { ExperimentDetailScreen(MockExperimentData.active, {}) } }
