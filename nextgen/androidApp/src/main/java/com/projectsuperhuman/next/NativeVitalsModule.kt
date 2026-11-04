package com.projectsuperhuman.next

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.vitals.BloodPressureArm
import com.projectsuperhuman.next.vitals.BloodPressurePosition
import com.projectsuperhuman.next.vitals.BloodPressureReadingInput
import com.projectsuperhuman.next.vitals.BodyTemperatureInput
import com.projectsuperhuman.next.vitals.HeartRateInput
import com.projectsuperhuman.next.vitals.TemperatureSite
import com.projectsuperhuman.next.vitals.VitalsMetrics
import com.projectsuperhuman.next.vitals.VitalsValidation
import com.projectsuperhuman.next.vitals.VitalsValidationRules
import com.projectsuperhuman.next.vitals.VitalsValueFactory
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private val VitalsNavy: Color get() = superhumanBrandText
private val VitalsBlue: Color get() = superhumanBlue
private val VitalsCyan: Color get() = if (SuperhumanAppearance.darkMode) superhumanAccent else Color(0xFF20A7C4)
private val VitalsRed: Color get() = superhumanRed
private val VitalsInk: Color get() = superhumanTextPrimary
private val VitalsMuted: Color get() = superhumanTextMuted
private val VitalsBorder: Color get() = softModuleOutline(superhumanBorder)
private val VitalsBackground: Color get() = superhumanBackground
private val VitalsSurface: Color get() = superhumanSurface
private val VitalsElevated: Color get() = superhumanSurfaceElevated
private val VitalsSoft: Color get() = superhumanSurfaceSoft

private enum class VitalsEntry { BLOOD_PRESSURE, TEMPERATURE, HEART_RATE }

private data class BloodPressureHistoryItem(
    val systolic: Int,
    val diastolic: Int,
    val timestampEpochMs: Long,
    val arm: String?,
    val position: String?,
    val sessionId: String?,
    val readingIndex: String?,
    val source: String? = null
)

private data class VitalsHistoryState(
    val heartRate: List<HealthValue> = emptyList(),
    val bloodPressure: List<BloodPressureHistoryItem> = emptyList(),
    val temperature: List<HealthValue> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

@Composable
internal fun NativeVitalsPage(onBack: () -> Unit) {
    var history by remember { mutableStateOf(VitalsHistoryState()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var historyLimit by remember { mutableIntStateOf(200) }
    var entry by remember { mutableStateOf<VitalsEntry?>(null) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey, historyLimit) {
        history = try {
            loadVitalsHistory(historyLimit)
        } catch (_: Throwable) {
            VitalsHistoryState(loading = false, error = "Vitals history could not be loaded.")
        }
    }

    fun persist(values: List<HealthValue>, message: String) {
        scope.launch {
            val result = NativeDataHub.ingestValues(values)
            saveMessage = if (result.rejected == 0) message else "The reading could not be saved. Check its values and unit."
            if (result.accepted > 0) refreshKey++
        }
    }

    Column(
        Modifier.fillMaxSize().background(VitalsBackground).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        VitalsHeader(onBack)
        saveMessage?.let { StatusStrip(it) { saveMessage = null } }
        LatestVitalsSection(history, onRecord = { entry = it })
        BloodPressureGuidanceCard()
        VitalsHistorySection(history, historyLimit, onLoadOlder = {
            historyLimit = (historyLimit + 200).coerceAtMost(2000)
        })
        Text(
            "A single reading cannot diagnose a condition. Consider symptoms, measurement quality and longer-term patterns; seek clinical advice when concerned.",
            color = VitalsMuted,
            fontSize = 9.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(22.dp))
    }

    when (entry) {
        VitalsEntry.BLOOD_PRESSURE -> BloodPressureEntryDialog(
            onDismiss = { entry = null },
            onSave = {
                persist(it, if (it.size > 2) "Blood pressure session saved." else "Blood pressure reading saved.")
                entry = null
            }
        )
        VitalsEntry.TEMPERATURE -> TemperatureEntryDialog(
            onDismiss = { entry = null },
            onSave = {
                persist(listOf(it), "Body temperature saved.")
                entry = null
            }
        )
        VitalsEntry.HEART_RATE -> HeartRateEntryDialog(
            onDismiss = { entry = null },
            onSave = {
                persist(listOf(it), "Heart rate saved.")
                entry = null
            }
        )
        null -> Unit
    }
}

@Composable
private fun VitalsHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SuperhumanModuleBackButton(onBack)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Vitals", color = VitalsInk, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text("Current readings & measured history", color = VitalsMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun StatusStrip(message: String, dismiss: () -> Unit) {
    val background = if (SuperhumanAppearance.darkMode) superhumanAccentSoft else Color(0xFFE8F6F4)
    val text = if (SuperhumanAppearance.darkMode) Color(0xFF7BD8C8) else Color(0xFF13776C)
    Row(
        Modifier.fillMaxWidth().background(background, RoundedCornerShape(15.dp)).clickable(onClick = dismiss).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(message, color = text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("×", color = text, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun LatestVitalsSection(state: VitalsHistoryState, onRecord: (VitalsEntry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("LATEST", color = VitalsMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        LatestVitalCard(
            label = "HEART RATE",
            value = state.heartRate.firstOrNull()?.let { "${it.value.toInt()} bpm" } ?: "No reading",
            detail = state.heartRate.firstOrNull()?.let { latest ->
                "${formatDateTime(latest.timestampEpochMs)} · ${sourceLabel(latest)}"
            } ?: "Record manually or import from a wearable",
            accent = VitalsRed,
            values = state.heartRate.take(16).reversed().map { it.value },
            onRecord = { onRecord(VitalsEntry.HEART_RATE) }
        )
        LatestVitalCard(
            label = "BLOOD PRESSURE",
            value = state.bloodPressure.firstOrNull()?.let { "${it.systolic}/${it.diastolic} mmHg" } ?: "No reading",
            detail = state.bloodPressure.firstOrNull()?.let {
                "${bpMetadataLabel(it)} · ${readableVitalsSource(it.source.orEmpty())}"
            } ?: "Record a supported, timed measurement",
            accent = VitalsBlue,
            values = state.bloodPressure.take(16).reversed().map { it.systolic.toDouble() },
            onRecord = { onRecord(VitalsEntry.BLOOD_PRESSURE) }
        )
        LatestVitalCard(
            label = "BODY TEMPERATURE",
            value = state.temperature.firstOrNull()?.let { String.format(Locale.getDefault(), "%.1f °C", it.value) } ?: "No reading",
            detail = state.temperature.firstOrNull()?.let {
                "${temperatureMetadataLabel(it)} · ${sourceLabel(it)}"
            } ?: "Add the measurement site when known",
            accent = VitalsCyan,
            values = state.temperature.take(16).reversed().map { it.value },
            onRecord = { onRecord(VitalsEntry.TEMPERATURE) }
        )
    }
}

@Composable
private fun LatestVitalCard(
    label: String,
    value: String,
    detail: String,
    accent: Color,
    values: List<Double>,
    onRecord: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().midnightModuleCard(accent = accent, radius = 22.dp).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
                Spacer(Modifier.height(4.dp))
                Text(value, color = VitalsInk, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text(detail, color = VitalsMuted, fontSize = 9.sp, maxLines = 2)
            }
            Box(
                Modifier.clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .18f else .1f)).clickable(onClick = onRecord).padding(horizontal = 12.dp, vertical = 9.dp)
            ) { Text("RECORD", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black) }
        }
        if (values.size > 1) {
            Spacer(Modifier.height(10.dp))
            VitalsTrend(values, accent)
        }
    }
}

@Composable
private fun VitalsTrend(values: List<Double>, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(34.dp)) {
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val range = (max - min).coerceAtLeast(.1)
        values.zipWithNext().forEachIndexed { index, pair ->
            val x1 = size.width * index / (values.size - 1)
            val x2 = size.width * (index + 1) / (values.size - 1)
            val y1 = size.height - ((pair.first - min) / range * size.height).toFloat()
            val y2 = size.height - ((pair.second - min) / range * size.height).toFloat()
            drawLine(color, Offset(x1, y1), Offset(x2, y2), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun BloodPressureGuidanceCard() {
    Column(
        Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(VitalsSoft, VitalsSurface)), RoundedCornerShape(22.dp)
        ).border(1.dp, VitalsBorder, RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Text("A BETTER BP READING", color = VitalsBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(7.dp))
        GuidanceLine("Rest quietly for at least 5 minutes first.")
        GuidanceLine("When seated, support your back and feet; support the cuffed arm at heart level.")
        GuidanceLine("Stay still and do not talk or use your phone during the measurement.")
        GuidanceLine("When repeating, take two readings at least 1 minute apart and keep both readings.")
    }
}

@Composable
private fun GuidanceLine(text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text("•", color = VitalsBlue, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(7.dp))
        Text(text, color = VitalsInk, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

private enum class VitalsHistoryFilter(val label: String) {
    ALL("All"), HEART_RATE("Heart rate"), BLOOD_PRESSURE("BP"), TEMPERATURE("Temp")
}

private data class VitalsTimelineEntry(
    val timestampEpochMs: Long,
    val kind: VitalsHistoryFilter,
    val title: String,
    val detail: String,
    val heartRateBpm: Int? = null
) {
    val date: LocalDate get() = Instant.ofEpochMilli(timestampEpochMs)
        .atZone(ZoneId.systemDefault()).toLocalDate()
}

private fun vitalsTimeline(state: VitalsHistoryState): List<VitalsTimelineEntry> = buildList {
    state.bloodPressure.forEach { bp ->
        val context = listOfNotNull(
            bp.arm, bp.position, bp.readingIndex?.let { "Reading $it" },
            readableVitalsSource(bp.source.orEmpty())
        )
            .joinToString(" · ")
        add(VitalsTimelineEntry(
            timestampEpochMs = bp.timestampEpochMs,
            kind = VitalsHistoryFilter.BLOOD_PRESSURE,
            title = "${bp.systolic}/${bp.diastolic} mmHg",
            detail = context
        ))
    }
    state.temperature.forEach { value ->
        val site = value.metadata["measurementSite"]
            ?.replaceFirstChar { it.uppercase() } ?: "Site not recorded"
        add(VitalsTimelineEntry(
            timestampEpochMs = value.timestampEpochMs,
            kind = VitalsHistoryFilter.TEMPERATURE,
            title = String.format(Locale.getDefault(), "%.1f °C", value.value),
            detail = "$site · ${sourceLabel(value)}"
        ))
    }
    state.heartRate.forEach { value ->
        val bpm = value.value.toInt()
        add(VitalsTimelineEntry(
            timestampEpochMs = value.timestampEpochMs,
            kind = VitalsHistoryFilter.HEART_RATE,
            title = "$bpm bpm",
            detail = sourceLabel(value),
            heartRateBpm = bpm
        ))
    }
}.sortedByDescending { it.timestampEpochMs }

/**
 * Browses existing persisted measurements without rewriting or aggregating away raw
 * data. One day is one card; dense heart-rate samples collapse into a single summary,
 * with every original point available on expansion.
 */
@Composable
private fun VitalsHistorySection(
    state: VitalsHistoryState,
    historyLimit: Int,
    onLoadOlder: () -> Unit
) {
    var filter by remember { mutableStateOf(VitalsHistoryFilter.ALL) }
    val timeline = remember(state) { vitalsTimeline(state) }
    val visible = remember(timeline, filter) {
        if (filter == VitalsHistoryFilter.ALL) timeline
        else timeline.filter { it.kind == filter }
    }
    val days = remember(visible) { visible.groupBy { it.date } }
    val newestDate = days.keys.firstOrNull()
    var expandedDay by remember(state) { mutableStateOf<LocalDate?>(null) }
    var collapsedNewestDay by remember(state) { mutableStateOf(false) }
    var expandedHeartDay by remember { mutableStateOf<LocalDate?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("HISTORY", color = VitalsMuted, fontSize = 9.sp,
                fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            if (!state.loading && timeline.isNotEmpty()) {
                Text("${timeline.size} loaded readings", color = VitalsMuted, fontSize = 9.sp)
            }
        }
        when {
            state.loading -> HistoryMessage("Loading vitals…")
            state.error != null -> HistoryMessage(state.error)
            timeline.isEmpty() -> HistoryMessage("No vitals recorded yet.")
            else -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VitalsHistoryFilter.entries.forEach { item ->
                        val selected = item == filter
                        Box(
                            Modifier.weight(1f)
                                .background(if (selected) VitalsSoft else VitalsSurface,
                                    RoundedCornerShape(12.dp))
                                .superhumanClickable(onClick = {
                                    filter = item
                                    expandedDay = null
                                    collapsedNewestDay = false
                                    expandedHeartDay = null
                                })
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(item.label, color = if (selected) VitalsInk else VitalsMuted,
                                fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                if (days.isEmpty()) {
                    HistoryMessage("No measurements in this category.")
                }
                days.forEach { (date, entries) ->
                    val open = expandedDay == date ||
                        (expandedDay == null && !collapsedNewestDay && date == newestDate)
                    Column(
                        Modifier.fillMaxWidth().background(VitalsSurface, RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().superhumanClickable {
                                if (open) {
                                    expandedDay = null
                                    collapsedNewestDay = date == newestDate
                                } else {
                                    expandedDay = date
                                    collapsedNewestDay = false
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())),
                                    color = VitalsInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("${entries.size} reading${if (entries.size == 1) "" else "s"}",
                                    color = VitalsMuted, fontSize = 10.sp)
                            }
                            Text(if (open) "−" else "+", fontSize = 19.sp, color = VitalsMuted)
                        }
                        if (open) {
                            // Preserve chronological ordering of the manual readings;
                            // frequent pulse samples are collapsed instead of flooding the screen.
                            entries.filter { it.kind != VitalsHistoryFilter.HEART_RATE }
                                .forEach { entry ->
                                    VitalsTimelineRow(
                                        label = if (entry.kind == VitalsHistoryFilter.BLOOD_PRESSURE)
                                            "Blood pressure" else "Body temperature",
                                        value = entry.title,
                                        detail = entry.detail,
                                        time = formatTime(entry.timestampEpochMs)
                                    )
                                }
                            val heart = entries.filter { it.kind == VitalsHistoryFilter.HEART_RATE }
                            if (heart.isNotEmpty()) {
                                val rates = heart.mapNotNull { it.heartRateBpm }
                                val isHeartOpen = expandedHeartDay == date
                                Column(
                                    Modifier.fillMaxWidth()
                                        .background(VitalsSoft, RoundedCornerShape(15.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().superhumanClickable {
                                            expandedHeartDay = if (isHeartOpen) null else date
                                        },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Heart rate · ${heart.size} readings",
                                                color = VitalsInk, fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "Latest ${rates.first()} bpm · Range ${rates.minOrNull()}–${rates.maxOrNull()} bpm",
                                                color = VitalsMuted, fontSize = 10.sp
                                            )
                                        }
                                        Text(if (isHeartOpen) "Less" else "Details",
                                            color = VitalsBlue, fontSize = 10.sp)
                                    }
                                    if (isHeartOpen) {
                                        heart.forEach { entry ->
                                            VitalsTimelineRow(
                                                label = formatTime(entry.timestampEpochMs),
                                                value = entry.title, detail = entry.detail,
                                                time = ""
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (historyLimit < 2000 && (state.heartRate.size >= historyLimit ||
                    state.bloodPressure.size >= historyLimit ||
                    state.temperature.size >= historyLimit)
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(VitalsSurface, RoundedCornerShape(17.dp))
                            .superhumanClickable(onClick = onLoadOlder)
                            .padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Load older readings", color = VitalsBlue, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMessage(message: String) {
    Box(Modifier.fillMaxWidth().background(VitalsSurface, RoundedCornerShape(18.dp))
        .padding(18.dp)) {
        Text(message, color = VitalsMuted, fontSize = 10.sp)
    }
}

@Composable
private fun VitalsTimelineRow(label: String, value: String, detail: String, time: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = VitalsMuted, fontSize = 10.sp)
            Text(value, color = VitalsInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (detail.isNotBlank()) Text(detail, color = VitalsMuted, fontSize = 10.sp, maxLines = 2)
        }
        if (time.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(time, color = VitalsMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun BloodPressureEntryDialog(onDismiss: () -> Unit, onSave: (List<HealthValue>) -> Unit) {
    var systolic by remember { mutableStateOf("") }
    var diastolic by remember { mutableStateOf("") }
    var measuredAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var arm by remember { mutableStateOf(BloodPressureArm.LEFT) }
    var position by remember { mutableStateOf(BloodPressurePosition.SITTING) }
    var notes by remember { mutableStateOf("") }
    var firstReading by remember { mutableStateOf<BloodPressureReadingInput?>(null) }
    var validation by remember { mutableStateOf<VitalsValidation?>(null) }
    var pending by remember { mutableStateOf<BloodPressureReadingInput?>(null) }
    var confirmingSecond by remember { mutableStateOf(false) }

    fun save(input: BloodPressureReadingInput) {
        onSave(VitalsValueFactory.bloodPressureSession(
            UUID.randomUUID().toString(), listOfNotNull(firstReading, input), notes
        ))
    }

    fun addSecond(input: BloodPressureReadingInput) {
        firstReading = input
        systolic = ""
        diastolic = ""
        measuredAt = System.currentTimeMillis()
        validation = null
        pending = null
        // Arm, position, and notes are retained, but remain editable.
    }

    fun check(input: BloodPressureReadingInput, forSecond: Boolean) {
        val checked = VitalsValidationRules.bloodPressure(input)
        validation = checked
        confirmingSecond = forSecond
        pending = input.takeIf { checked.needsConfirmation && checked.canSave }
        when {
            !checked.canSave -> Unit
            checked.needsConfirmation -> Unit
            forSecond -> addSecond(input)
            else -> save(input)
        }
    }

    VitalsDialog("Record blood pressure", onDismiss) {
        Text(if (firstReading == null) "Reading 1" else "Reading 2",
            color = VitalsBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
        firstReading?.let { previous ->
            Row(Modifier.fillMaxWidth().background(VitalsSoft, RoundedCornerShape(12.dp))
                    .padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("First · ${previous.systolic}/${previous.diastolic} mmHg",
                    color = VitalsInk, fontSize = 12.sp)
                Text("1 of 2", color = VitalsMuted, fontSize = 10.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Systolic", systolic, { systolic = it }, "mmHg", Modifier.weight(1f))
            NumberField("Diastolic", diastolic, { diastolic = it }, "mmHg", Modifier.weight(1f))
        }
        TimestampEditor(measuredAt) { measuredAt = it }
        ChoiceRow("Arm", BloodPressureArm.entries, arm, { arm = it }) {
            it.name.lowercase().replaceFirstChar { char -> char.uppercase() }
        }
        ChoiceRow("Position", BloodPressurePosition.entries, position, { position = it }) {
            it.name.lowercase().replaceFirstChar { char -> char.uppercase() }
        }
        OutlinedTextField(notes, { notes = it.take(200) },
            label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
        ValidationPanel(validation, confirmLabel = "Save anyway") {
            pending?.let { if (confirmingSecond) addSecond(it) else save(it) }
        }

        val ready = systolic.toIntOrNull() != null && diastolic.toIntOrNull() != null
        ActionButton(
            if (firstReading == null) "SAVE READING" else "SAVE BOTH READINGS",
            enabled = ready
        ) {
            check(
                BloodPressureReadingInput(systolic.toInt(), diastolic.toInt(), measuredAt, arm, position),
                forSecond = false
            )
        }
        if (firstReading == null) {
            Text("Add a second reading", color = VitalsBlue, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
                    .superhumanClickable {
                        if (ready) {
                            check(BloodPressureReadingInput(
                                systolic.toInt(), diastolic.toInt(), measuredAt, arm, position
                            ), forSecond = true)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 12.dp))
            Text("Optional: rest at least one minute before taking a second reading.",
                color = VitalsMuted, fontSize = 10.sp)
        } else {
            Text("Save only the first reading", color = VitalsBlue, fontSize = 12.sp,
                modifier = Modifier.superhumanClickable {
                    firstReading?.let { previous ->
                        onSave(VitalsValueFactory.bloodPressureSession(
                            UUID.randomUUID().toString(), listOf(previous), notes
                        ))
                    }
                }.padding(horizontal = 10.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun TemperatureEntryDialog(onDismiss: () -> Unit, onSave: (HealthValue) -> Unit) {
    var value by remember { mutableStateOf("") }
    var measuredAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var site by remember { mutableStateOf(TemperatureSite.ORAL) }
    var validation by remember { mutableStateOf<VitalsValidation?>(null) }
    var pending by remember { mutableStateOf<BodyTemperatureInput?>(null) }

    fun save(input: BodyTemperatureInput) = onSave(VitalsValueFactory.bodyTemperature(UUID.randomUUID().toString(), input))
    VitalsDialog("Record body temperature", onDismiss) {
        NumberField("Temperature", value, { value = it }, "°C", Modifier.fillMaxWidth(), decimal = true)
        TimestampEditor(measuredAt) { measuredAt = it }
        Text("Measurement site", color = VitalsMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        TemperatureSite.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { option -> ChoiceChip(option.name.lowercase().replaceFirstChar { char -> char.uppercase() }, site == option, { site = option }, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        ValidationPanel(validation, "Save anyway") { pending?.let(::save) }
        ActionButton("SAVE TEMPERATURE", enabled = value.toDoubleOrNull() != null) {
            val input = BodyTemperatureInput(value.toDouble(), measuredAt, site)
            val checked = VitalsValidationRules.temperature(input)
            validation = checked
            when {
                !checked.canSave -> Unit
                checked.needsConfirmation -> pending = input
                else -> save(input)
            }
        }
    }
}

@Composable
private fun HeartRateEntryDialog(onDismiss: () -> Unit, onSave: (HealthValue) -> Unit) {
    var value by remember { mutableStateOf("") }
    var measuredAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var validation by remember { mutableStateOf<VitalsValidation?>(null) }
    var pending by remember { mutableStateOf<HeartRateInput?>(null) }

    fun save(input: HeartRateInput) = onSave(VitalsValueFactory.heartRate(UUID.randomUUID().toString(), input))
    VitalsDialog("Record heart rate", onDismiss) {
        NumberField("Heart rate", value, { value = it }, "bpm", Modifier.fillMaxWidth())
        TimestampEditor(measuredAt) { measuredAt = it }
        ValidationPanel(validation, "Save anyway") { pending?.let(::save) }
        ActionButton("SAVE HEART RATE", enabled = value.toIntOrNull() != null) {
            val input = HeartRateInput(value.toInt(), measuredAt)
            val checked = VitalsValidationRules.heartRate(input)
            validation = checked
            when {
                !checked.canSave -> Unit
                checked.needsConfirmation -> pending = input
                else -> save(input)
            }
        }
    }
}

@Composable
private fun VitalsDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(VitalsSurface).border(1.dp, VitalsBorder, RoundedCornerShape(26.dp)).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = VitalsInk, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text("×", color = VitalsNavy, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onDismiss).padding(6.dp))
            }
            content()
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, suffix: String, modifier: Modifier, decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() || (decimal && it == '.') }
            if (filtered.count { it == '.' } <= 1) onChange(filtered.take(6))
        },
        label = { Text(label) },
        suffix = { Text(suffix, fontSize = 10.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun TimestampEditor(epochMs: Long, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    val calendar = remember(epochMs) { Calendar.getInstance().apply { timeInMillis = epochMs } }
    Column(Modifier.fillMaxWidth().background(VitalsElevated, RoundedCornerShape(14.dp)).padding(11.dp)) {
        Text("Measurement time", color = VitalsMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(formatDateTime(epochMs), color = VitalsInk, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 7.dp)) {
            SmallAction("CHANGE DATE") {
                DatePickerDialog(context, { _, year, month, day ->
                    val updated = Calendar.getInstance().apply { timeInMillis = epochMs; set(year, month, day) }
                    onChange(updated.timeInMillis)
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            }
            SmallAction("CHANGE TIME") {
                TimePickerDialog(context, { _, hour, minute ->
                    val updated = Calendar.getInstance().apply { timeInMillis = epochMs; set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }
                    onChange(updated.timeInMillis)
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, action: () -> Unit) {
    Text(label, color = VitalsBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = action).padding(7.dp))
}

@Composable
private fun <T> ChoiceRow(label: String, choices: List<T>, selected: T, onSelect: (T) -> Unit, text: (T) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = VitalsMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            choices.forEach { ChoiceChip(text(it), selected == it, { onSelect(it) }, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) VitalsBlue else VitalsElevated).clickable(onClick = onClick).padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (selected) Color.White else VitalsMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ValidationPanel(validation: VitalsValidation?, confirmLabel: String, onConfirm: () -> Unit) {
    validation ?: return
    val messages = if (validation.errors.isNotEmpty()) validation.errors else validation.warnings
    if (messages.isEmpty()) return
    val warningText = if (SuperhumanAppearance.darkMode) Color(0xFFFFC47A) else Color(0xFF9A5316)
    Column(Modifier.fillMaxWidth().background(superhumanWarningSurface, RoundedCornerShape(13.dp)).padding(11.dp)) {
        messages.forEach { Text(it, color = warningText, fontSize = 9.sp, lineHeight = 13.sp) }
        if (validation.needsConfirmation) {
            Text(confirmLabel.uppercase(), color = warningText, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable(onClick = onConfirm).padding(top = 8.dp, bottom = 3.dp))
        }
    }
}

@Composable
private fun ActionButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = if (SuperhumanAppearance.darkMode) Color(0xFF78B7EA) else Color(0xFF082D66), contentColor = if (SuperhumanAppearance.darkMode) Color(0xFF101820) else Color.White, disabledContainerColor = VitalsElevated, disabledContentColor = VitalsMuted.copy(alpha = .60f)),
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black) }
}

private suspend fun loadVitalsHistory(limit: Int = 200): VitalsHistoryState = withContext(Dispatchers.IO) {
    // BP pairing and ordering run on the same background dispatcher as the indexed
    // history reads, so longer histories never stall the Compose main thread.
    val systolic = NativeDataHub.pageForMetric(HealthDomain.BLOOD_PRESSURE, VitalsMetrics.BLOOD_PRESSURE_SYSTOLIC, limit)
    val diastolic = NativeDataHub.pageForMetric(HealthDomain.BLOOD_PRESSURE, VitalsMetrics.BLOOD_PRESSURE_DIASTOLIC, limit)
    val temperatures = NativeDataHub.pageForMetric(HealthDomain.BODY, VitalsMetrics.BODY_TEMPERATURE_CELSIUS, limit)
    val heartRates = NativeDataHub.pageForMetric(HealthDomain.EXERCISE, VitalsMetrics.HEART_RATE_BPM, limit)
    val diastolicByKey = diastolic.associateBy(::bpPairingKey)
    val bp = systolic.mapNotNull { sys ->
        val dia = diastolicByKey[bpPairingKey(sys)] ?: diastolic.minByOrNull { kotlin.math.abs(it.timestampEpochMs - sys.timestampEpochMs) }?.takeIf {
            kotlin.math.abs(it.timestampEpochMs - sys.timestampEpochMs) <= 2_000L
        } ?: return@mapNotNull null
        BloodPressureHistoryItem(
            sys.value.toInt(), dia.value.toInt(), sys.timestampEpochMs,
            sys.metadata["arm"], sys.metadata["bodyPosition"], sys.metadata["vitalsSessionId"], sys.metadata["bpReadingIndex"], sys.source
        )
    }.sortedByDescending { it.timestampEpochMs }
    VitalsHistoryState(heartRates, bp, temperatures, loading = false)
}

private fun bpPairingKey(value: HealthValue): String {
    val session = value.metadata["vitalsSessionId"]
    val index = value.metadata["bpReadingIndex"]
    return if (session != null && index != null) "$session:$index" else "timestamp:${value.timestampEpochMs}"
}

private fun bpMetadataLabel(item: BloodPressureHistoryItem, includeTime: Boolean = true): String {
    val metadata = listOfNotNull(
        item.arm?.replaceFirstChar { char -> char.uppercase() },
        item.position?.replaceFirstChar { char -> char.uppercase() },
        item.readingIndex?.let { "Reading $it" }
    ).joinToString(" · ")
    return listOfNotNull(if (includeTime) formatDateTime(item.timestampEpochMs) else null, metadata.takeIf { it.isNotEmpty() }).joinToString(" · ")
}

private fun temperatureMetadataLabel(value: HealthValue): String = listOfNotNull(
    formatDateTime(value.timestampEpochMs),
    value.metadata["measurementSite"]?.replaceFirstChar { char -> char.uppercase() }
).joinToString(" · ")

private fun readableVitalsSource(source: String, entryMode: String? = null): String = when {
    source.contains("synthetic", ignoreCase = true) || entryMode == "synthetic" -> "Demo data"
    entryMode == "manual" || source.contains("manual", ignoreCase = true) -> "Manual"
    source.contains("health_connect", ignoreCase = true) ||
        source.contains("health connect", ignoreCase = true) -> "Health Connect"
    source.contains("ble", ignoreCase = true) ||
        source.contains("wearable", ignoreCase = true) ||
        source.contains("h19c", ignoreCase = true) -> "Wearable"
    else -> "Imported"
}
private fun sourceLabel(value: HealthValue): String =
    readableVitalsSource(value.source, value.metadata["entryMode"])
private fun formatDateTime(epochMs: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(epochMs))
private fun formatTime(epochMs: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))