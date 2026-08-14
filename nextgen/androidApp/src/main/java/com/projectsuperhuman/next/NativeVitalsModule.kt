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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val VitalsNavy = Color(0xFF082D66)
private val VitalsBlue = Color(0xFF0D6CB4)
private val VitalsCyan = Color(0xFF20A7C4)
private val VitalsRed = Color(0xFFD44C57)
private val VitalsInk = Color(0xFF0B1F35)
private val VitalsMuted = Color(0xFF64748B)
private val VitalsBorder = Color(0xFFDDE7EC)
private val VitalsBackground = Color(0xFFF8FBFD)

private enum class VitalsEntry { BLOOD_PRESSURE, TEMPERATURE, HEART_RATE }

private data class BloodPressureHistoryItem(
    val systolic: Int,
    val diastolic: Int,
    val timestampEpochMs: Long,
    val arm: String?,
    val position: String?,
    val sessionId: String?,
    val readingIndex: String?
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
    var entry by remember { mutableStateOf<VitalsEntry?>(null) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        history = try {
            loadVitalsHistory()
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
        VitalsHistorySection(history)
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
        Box(Modifier.superhumanTopButton(onClick = onBack), contentAlignment = Alignment.Center) {
            Text("←", color = VitalsNavy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Vitals", color = VitalsInk, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text("Current readings & measured history", color = VitalsMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun StatusStrip(message: String, dismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFE8F6F4), RoundedCornerShape(15.dp)).clickable(onClick = dismiss).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(message, color = Color(0xFF13776C), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("×", color = Color(0xFF13776C), fontWeight = FontWeight.Black)
    }
}

@Composable
private fun LatestVitalsSection(state: VitalsHistoryState, onRecord: (VitalsEntry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("LATEST", color = VitalsMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        LatestVitalCard(
            label = "HEART RATE",
            value = state.heartRate.firstOrNull()?.let { "${it.value.toInt()} bpm" } ?: "No reading",
            detail = state.heartRate.firstOrNull()?.timestampEpochMs?.let(::formatDateTime) ?: "Record manually or import from a wearable",
            accent = VitalsRed,
            values = state.heartRate.take(16).reversed().map { it.value },
            onRecord = { onRecord(VitalsEntry.HEART_RATE) }
        )
        LatestVitalCard(
            label = "BLOOD PRESSURE",
            value = state.bloodPressure.firstOrNull()?.let { "${it.systolic}/${it.diastolic} mmHg" } ?: "No reading",
            detail = state.bloodPressure.firstOrNull()?.let { bpMetadataLabel(it) } ?: "Record a supported, timed measurement",
            accent = VitalsBlue,
            values = state.bloodPressure.take(16).reversed().map { it.systolic.toDouble() },
            onRecord = { onRecord(VitalsEntry.BLOOD_PRESSURE) }
        )
        LatestVitalCard(
            label = "BODY TEMPERATURE",
            value = state.temperature.firstOrNull()?.let { String.format(Locale.getDefault(), "%.1f °C", it.value) } ?: "No reading",
            detail = state.temperature.firstOrNull()?.let { temperatureMetadataLabel(it) } ?: "Add the measurement site when known",
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
    Column(Modifier.fillMaxWidth().background(Color.White, shape).border(1.dp, VitalsBorder, shape).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
                Spacer(Modifier.height(4.dp))
                Text(value, color = VitalsInk, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text(detail, color = VitalsMuted, fontSize = 9.sp, maxLines = 2)
            }
            Box(
                Modifier.clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = .1f)).clickable(onClick = onRecord).padding(horizontal = 12.dp, vertical = 9.dp)
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
            Brush.linearGradient(listOf(Color(0xFFEAF5FB), Color.White)), RoundedCornerShape(22.dp)
        ).border(1.dp, Color(0xFFD4E7F1), RoundedCornerShape(22.dp)).padding(16.dp)
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

@Composable
private fun VitalsHistorySection(state: VitalsHistoryState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("HISTORY", color = VitalsMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        when {
            state.loading -> HistoryMessage("Loading vitals…")
            state.error != null -> HistoryMessage(state.error)
            state.bloodPressure.isEmpty() && state.temperature.isEmpty() && state.heartRate.isEmpty() -> HistoryMessage("No vitals recorded yet.")
            else -> {
                val rows = buildList {
                    state.bloodPressure.take(20).forEach { add(Triple(it.timestampEpochMs, "Blood pressure", "${it.systolic}/${it.diastolic} mmHg · ${bpMetadataLabel(it, includeTime = false)}")) }
                    state.temperature.take(20).forEach { add(Triple(it.timestampEpochMs, "Body temperature", "${String.format(Locale.getDefault(), "%.1f", it.value)} °C · ${it.metadata["measurementSite"]?.replaceFirstChar { char -> char.uppercase() } ?: "Site not recorded"}")) }
                    state.heartRate.take(20).forEach { add(Triple(it.timestampEpochMs, "Heart rate", "${it.value.toInt()} bpm · ${sourceLabel(it)}")) }
                }.sortedByDescending { it.first }.take(30)
                rows.forEach { (time, label, value) -> HistoryRow(label, value, formatDateTime(time)) }
            }
        }
    }
}

@Composable
private fun HistoryMessage(message: String) {
    Box(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(18.dp)) {
        Text(message, color = VitalsMuted, fontSize = 10.sp)
    }
}

@Composable
private fun HistoryRow(label: String, value: String, time: String) {
    Row(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(17.dp)).border(1.dp, VitalsBorder, RoundedCornerShape(17.dp)).padding(13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = VitalsInk, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Text(value, color = VitalsMuted, fontSize = 9.sp, maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        Text(time, color = VitalsMuted, fontSize = 8.sp)
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
    var readings by remember { mutableStateOf(emptyList<BloodPressureReadingInput>()) }
    var validation by remember { mutableStateOf<VitalsValidation?>(null) }
    var pending by remember { mutableStateOf<BloodPressureReadingInput?>(null) }
    var waitSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(waitSeconds) {
        if (waitSeconds > 0) {
            delay(1_000L)
            waitSeconds--
        }
    }

    fun add(input: BloodPressureReadingInput) {
        readings = readings + input
        systolic = ""
        diastolic = ""
        measuredAt = System.currentTimeMillis()
        validation = null
        pending = null
        waitSeconds = 60
    }

    VitalsDialog("Record blood pressure", onDismiss) {
        Text("Reading ${readings.size + 1}", color = VitalsBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Systolic", systolic, { systolic = it }, "mmHg", Modifier.weight(1f))
            NumberField("Diastolic", diastolic, { diastolic = it }, "mmHg", Modifier.weight(1f))
        }
        TimestampEditor(measuredAt) { measuredAt = it }
        ChoiceRow("Arm", BloodPressureArm.entries, arm, { arm = it }) { it.name.lowercase().replaceFirstChar { char -> char.uppercase() } }
        ChoiceRow("Position", BloodPressurePosition.entries, position, { position = it }) { it.name.lowercase().replaceFirstChar { char -> char.uppercase() } }
        OutlinedTextField(notes, { notes = it.take(200) }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
        ValidationPanel(validation, confirmLabel = "Add anyway") { pending?.let(::add) }

        val canAttempt = systolic.toIntOrNull() != null && diastolic.toIntOrNull() != null && (readings.isEmpty() || waitSeconds == 0)
        ActionButton(
            if (readings.isEmpty()) "ADD READING" else if (waitSeconds > 0) "NEXT READING IN ${waitSeconds}s" else "ADD ANOTHER READING",
            enabled = canAttempt
        ) {
            val input = BloodPressureReadingInput(systolic.toInt(), diastolic.toInt(), measuredAt, arm, position)
            val checked = VitalsValidationRules.bloodPressure(input)
            validation = checked
            when {
                !checked.canSave -> Unit
                checked.needsConfirmation -> pending = input
                else -> add(input)
            }
        }

        if (readings.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(Color(0xFFF2F7FA), RoundedCornerShape(14.dp)).padding(11.dp)) {
                readings.forEachIndexed { index, reading ->
                    Text("Reading ${index + 1}  ${reading.systolic}/${reading.diastolic} mmHg  ·  ${formatTime(reading.measuredAtEpochMs)}", color = VitalsInk, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                if (readings.size > 1) {
                    Text(
                        "Session average  ${readings.map { it.systolic }.average().toInt()}/${readings.map { it.diastolic }.average().toInt()} mmHg (display only)",
                        color = VitalsBlue, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            ActionButton("SAVE SESSION") {
                val id = UUID.randomUUID().toString()
                onSave(VitalsValueFactory.bloodPressureSession(id, readings, notes))
            }
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
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color.White).verticalScroll(rememberScrollState()).padding(18.dp),
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
    Column(Modifier.fillMaxWidth().background(Color(0xFFF5F8FA), RoundedCornerShape(14.dp)).padding(11.dp)) {
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
        modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) VitalsBlue else Color(0xFFF1F5F7)).clickable(onClick = onClick).padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (selected) Color.White else VitalsMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ValidationPanel(validation: VitalsValidation?, confirmLabel: String, onConfirm: () -> Unit) {
    validation ?: return
    val messages = if (validation.errors.isNotEmpty()) validation.errors else validation.warnings
    if (messages.isEmpty()) return
    Column(Modifier.fillMaxWidth().background(Color(0xFFFFF3E8), RoundedCornerShape(13.dp)).padding(11.dp)) {
        messages.forEach { Text(it, color = Color(0xFF9A5316), fontSize = 9.sp, lineHeight = 13.sp) }
        if (validation.needsConfirmation) {
            Text(confirmLabel.uppercase(), color = Color(0xFF9A5316), fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable(onClick = onConfirm).padding(top = 8.dp, bottom = 3.dp))
        }
    }
}

@Composable
private fun ActionButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = VitalsNavy, disabledContainerColor = Color(0xFFB7C4CE)),
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black) }
}

private suspend fun loadVitalsHistory(): VitalsHistoryState {
    val systolic = NativeDataHub.pageForMetric(HealthDomain.BLOOD_PRESSURE, VitalsMetrics.BLOOD_PRESSURE_SYSTOLIC, 200)
    val diastolic = NativeDataHub.pageForMetric(HealthDomain.BLOOD_PRESSURE, VitalsMetrics.BLOOD_PRESSURE_DIASTOLIC, 200)
    val temperatures = NativeDataHub.pageForMetric(HealthDomain.BODY, VitalsMetrics.BODY_TEMPERATURE_CELSIUS, 200)
    val heartRates = NativeDataHub.pageForMetric(HealthDomain.EXERCISE, VitalsMetrics.HEART_RATE_BPM, 200)
    val diastolicByKey = diastolic.associateBy(::bpPairingKey)
    val bp = systolic.mapNotNull { sys ->
        val dia = diastolicByKey[bpPairingKey(sys)] ?: diastolic.minByOrNull { kotlin.math.abs(it.timestampEpochMs - sys.timestampEpochMs) }?.takeIf {
            kotlin.math.abs(it.timestampEpochMs - sys.timestampEpochMs) <= 2_000L
        } ?: return@mapNotNull null
        BloodPressureHistoryItem(
            sys.value.toInt(), dia.value.toInt(), sys.timestampEpochMs,
            sys.metadata["arm"], sys.metadata["bodyPosition"], sys.metadata["vitalsSessionId"], sys.metadata["bpReadingIndex"]
        )
    }.sortedByDescending { it.timestampEpochMs }
    return VitalsHistoryState(heartRates, bp, temperatures, loading = false)
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

private fun sourceLabel(value: HealthValue): String = if (value.metadata["entryMode"] == "manual") "Manual" else value.source
private fun formatDateTime(epochMs: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(epochMs))
private fun formatTime(epochMs: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
