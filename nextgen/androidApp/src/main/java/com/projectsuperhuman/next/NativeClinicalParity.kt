package com.projectsuperhuman.next

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.launch
import java.util.Locale

private val ClinicalBlue = Color(0xFF0D6CB4)
private val ClinicalNavy = Color(0xFF082D66)
private val ClinicalInk = Color(0xFF0B1F35)
private val ClinicalMuted = Color(0xFF64748B)
private val ClinicalGood = Color(0xFF168A78)
private val ClinicalBad = Color(0xFFCA3A3A)
private val ClinicalWarn = Color(0xFFD97706)
private val ClinicalBg = Color(0xFFF6F9FC)

data class ClinicalDraft(
    val name: String,
    val value: String,
    val unit: String,
    val low: String = "",
    val high: String = "",
    val sourceText: String = ""
) {
    val metric: String get() = "clinical.${slug(name)}"
}

@Composable
internal fun NativeClinicalParityScreen(onBack: () -> Unit, openLegacy: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf<List<HealthValue>>(emptyList()) }
    var drafts by remember { mutableStateOf<List<ClinicalDraft>>(emptyList()) }
    var importState by remember { mutableStateOf("Choose one or more NHS/lab screenshots to import.") }
    var reviewing by remember { mutableStateOf(false) }

    suspend fun refresh() {
        saved = NativeDataHub.latestForDomain(HealthDomain.CLINICAL)
            .filter { it.metric.startsWith("clinical.") }
            .sortedBy { it.metric }
    }

    LaunchedEffect(Unit) { refresh() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importState = "Reading ${uris.size} screenshot${if (uris.size == 1) "" else "s"}…"
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val collected = mutableListOf<ClinicalDraft>()
        var remaining = uris.size

        fun finishOne() {
            remaining--
            if (remaining > 0) return
            recognizer.close()
            scope.launch {
                val enriched = collected.distinctBy { it.metric + "|" + it.value }.map { draft ->
                    if (draft.low.isNotBlank() || draft.high.isNotBlank()) draft
                    else {
                        val previous = NativeDataHub.latest(draft.metric)
                        draft.copy(
                            low = previous?.metadata?.get("rangeLow").orEmpty(),
                            high = previous?.metadata?.get("rangeHigh").orEmpty()
                        )
                    }
                }
                drafts = enriched
                reviewing = enriched.isNotEmpty()
                importState = if (enriched.isEmpty()) {
                    "OCR finished, but no confident lab rows were found. You can use the existing importer for this screenshot format."
                } else {
                    "Found ${enriched.size} result${if (enriched.size == 1) "" else "s"}. Review before saving."
                }
            }
        }

        uris.forEach { uri: Uri ->
            try {
                val image = InputImage.fromFilePath(context, uri)
                recognizer.process(image)
                    .addOnSuccessListener { text -> collected += parseClinicalText(text.text); finishOne() }
                    .addOnFailureListener { finishOne() }
            } catch (_: Exception) {
                finishOne()
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(ClinicalBg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ClinicalHeader(onBack)

        val abnormal = saved.count { statusOf(it.value, it.metadata["rangeLow"]?.toDoubleOrNull(), it.metadata["rangeHigh"]?.toDoubleOrNull()) != "NORMAL" && (it.metadata["rangeLow"] != null || it.metadata["rangeHigh"] != null) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ClinicalStat("MARKERS", saved.size.toString(), ClinicalBlue, Modifier.weight(1f))
            ClinicalStat("ALERTS", abnormal.toString(), if (abnormal > 0) ClinicalBad else ClinicalGood, Modifier.weight(1f))
            ClinicalStat("SOURCE", if (saved.isEmpty()) "—" else "DB", ClinicalNavy, Modifier.weight(1f))
        }

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp)) {
            Text("Clinical Import", color = ClinicalInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("On-device OCR reads result, unit and reference range. Screenshot ranges are kept as the authoritative range; if a later screenshot omits one, the last saved range for that test is reused.", color = ClinicalMuted, fontSize = 10.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(12.dp))
            ClinicalButton("Import screenshots", ClinicalBlue) { launcher.launch("image/*") }
            Spacer(Modifier.height(8.dp))
            Text(importState, color = ClinicalMuted, fontSize = 9.sp, lineHeight = 13.sp)
        }

        if (reviewing) {
            Text("REVIEW BEFORE SAVE", color = ClinicalMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            drafts.forEachIndexed { index, draft ->
                ClinicalReviewCard(
                    draft = draft,
                    onChange = { changed -> drafts = drafts.toMutableList().also { it[index] = changed } },
                    onRemove = { drafts = drafts.toMutableList().also { it.removeAt(index) } }
                )
            }
            ClinicalButton("Save ${drafts.size} reviewed result${if (drafts.size == 1) "" else "s"}", ClinicalGood) {
                scope.launch {
                    drafts.forEach { d ->
                        val number = d.value.toDoubleOrNull() ?: return@forEach
                        val low = d.low.toDoubleOrNull()
                        val high = d.high.toDoubleOrNull()
                        NativeDataHub.saveMetric(
                            domain = HealthDomain.CLINICAL,
                            metric = d.metric,
                            value = number,
                            unit = d.unit.ifBlank { "value" },
                            source = "native-clinical-ocr",
                            metadata = mapOf(
                                "displayName" to d.name.trim(),
                                "rangeLow" to d.low.trim(),
                                "rangeHigh" to d.high.trim(),
                                "status" to statusOf(number, low, high),
                                "rangeSource" to if (d.sourceText.contains("range", ignoreCase = true) || d.low.isNotBlank() || d.high.isNotBlank()) "screenshot_or_review" else "remembered",
                                "rawOcr" to d.sourceText.take(500)
                            )
                        )
                    }
                    drafts = emptyList()
                    reviewing = false
                    importState = "Saved to the native clinical database."
                    refresh()
                }
            }
        }

        Text("LATEST RESULTS", color = ClinicalMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        if (saved.isEmpty()) {
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp)) {
                Text("No native clinical results yet", color = ClinicalInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Import screenshots above, or open the existing clinical tools to view legacy results while migration continues.", color = ClinicalMuted, fontSize = 9.sp, lineHeight = 14.sp)
            }
        } else {
            saved.take(40).forEach { ClinicalResultRow(it) }
        }

        ClinicalButton("Open existing clinical tools", ClinicalNavy, openLegacy)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ClinicalHeader(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(42.dp).height(42.dp).background(Color.White, RoundedCornerShape(14.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Text("←", color = ClinicalNavy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Clinical", color = ClinicalInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("Labs, reference ranges & import", color = ClinicalMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ClinicalStat(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text(label, color = ClinicalMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(value, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ClinicalReviewCard(draft: ClinicalDraft, onChange: (ClinicalDraft) -> Unit, onRemove: () -> Unit) {
    val value = draft.value.toDoubleOrNull()
    val low = draft.low.toDoubleOrNull()
    val high = draft.high.toDoubleOrNull()
    val status = if (value == null) "CHECK" else statusOf(value, low, high)
    val accent = when (status) { "LOW", "HIGH" -> ClinicalBad; "NORMAL" -> ClinicalGood; else -> ClinicalWarn }

    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(status, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("REMOVE", color = ClinicalBad, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onRemove).padding(6.dp))
        }
        OutlinedTextField(draft.name, { onChange(draft.copy(name = it)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Test") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(draft.value, { onChange(draft.copy(value = it.filterClinicalNumber())) }, Modifier.weight(1f), singleLine = true, label = { Text("Result") })
            OutlinedTextField(draft.unit, { onChange(draft.copy(unit = it)) }, Modifier.weight(1f), singleLine = true, label = { Text("Unit") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(draft.low, { onChange(draft.copy(low = it.filterClinicalNumber())) }, Modifier.weight(1f), singleLine = true, label = { Text("Range low") })
            OutlinedTextField(draft.high, { onChange(draft.copy(high = it.filterClinicalNumber())) }, Modifier.weight(1f), singleLine = true, label = { Text("Range high") })
        }
    }
}

@Composable
private fun ClinicalResultRow(value: HealthValue) {
    val low = value.metadata["rangeLow"]?.toDoubleOrNull()
    val high = value.metadata["rangeHigh"]?.toDoubleOrNull()
    val status = statusOf(value.value, low, high)
    val accent = when (status) { "LOW", "HIGH" -> ClinicalBad; "NORMAL" -> ClinicalGood; else -> ClinicalWarn }
    val name = value.metadata["displayName"] ?: value.metric.removePrefix("clinical.").replace('_', ' ')
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(5.dp).height(38.dp).background(accent, RoundedCornerShape(99.dp)))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = ClinicalInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            val range = when { low != null && high != null -> "$low – $high ${value.unit}"; low != null -> "≥ $low ${value.unit}"; high != null -> "≤ $high ${value.unit}"; else -> "Reference range not saved" }
            Text(range, color = ClinicalMuted, fontSize = 9.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${trimNumber(value.value)} ${value.unit}", color = ClinicalInk, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(status, color = accent, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ClinicalButton(title: String, accent: Color, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(accent, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

private fun parseClinicalText(raw: String): List<ClinicalDraft> {
    val lines = raw.lines().map { it.replace('–', '-').trim() }.filter { it.length > 1 }
    val out = mutableListOf<ClinicalDraft>()
    val rangeRegex = Regex("(-?\\d+(?:[.,]\\d+)?)\\s*(?:-|to)\\s*(-?\\d+(?:[.,]\\d+)?)", RegexOption.IGNORE_CASE)
    val valueRegex = Regex("(?<![A-Za-z])(-?\\d+(?:[.,]\\d+)?)(?![A-Za-z])")
    val unitRegex = Regex("(?:g/L|mg/L|mmol/L|µmol/L|umol/L|U/L|IU/L|mU/L|pmol/L|nmol/L|ng/L|pg/mL|g/dL|mg/dL|10\\^?9/L|10\\^?12/L|%|fL|pg|seconds?|s)\\b", RegexOption.IGNORE_CASE)

    fun candidate(chunk: String): ClinicalDraft? {
        val normalized = chunk.replace(',', '.')
        val range = rangeRegex.find(normalized)
        val numbers = valueRegex.findAll(normalized).toList()
        if (numbers.isEmpty()) return null
        val rangeStart = range?.range?.first ?: Int.MAX_VALUE
        val resultMatch = numbers.firstOrNull { it.range.first < rangeStart } ?: numbers.first()
        val result = resultMatch.groupValues[1]
        if (result.toDoubleOrNull() == null) return null
        val prefix = normalized.substring(0, resultMatch.range.first).trim(' ', ':', '-', '•')
        val name = prefix.lines().lastOrNull { it.any(Char::isLetter) }?.trim() ?: return null
        if (name.length < 3 || name.matches(Regex(".*(?:date|time|page|nhs|result|range|reference).*", RegexOption.IGNORE_CASE))) return null
        if (name.count(Char::isDigit) > name.length / 3) return null
        val unit = unitRegex.find(normalized.substring(resultMatch.range.last + 1))?.value.orEmpty()
        val low = range?.groupValues?.getOrNull(1)?.replace(',', '.').orEmpty()
        val high = range?.groupValues?.getOrNull(2)?.replace(',', '.').orEmpty()
        return ClinicalDraft(name = name.take(90), value = result, unit = unit, low = low, high = high, sourceText = chunk)
    }

    for (i in lines.indices) {
        val windows = listOf(
            lines[i],
            lines.subList(i, minOf(i + 2, lines.size)).joinToString("\n"),
            lines.subList(i, minOf(i + 3, lines.size)).joinToString("\n")
        )
        windows.asSequence().mapNotNull(::candidate).firstOrNull()?.let { draft ->
            if (out.none { it.metric == draft.metric && it.value == draft.value }) out += draft
        }
    }
    return out
}

private fun statusOf(value: Double, low: Double?, high: Double?): String = when {
    low != null && value < low -> "LOW"
    high != null && value > high -> "HIGH"
    low != null || high != null -> "NORMAL"
    else -> "NO RANGE"
}

private fun String.filterClinicalNumber(): String = filter { it.isDigit() || it == '.' || it == '-' || it == ',' }.replace(',', '.')

private fun slug(value: String): String = value.lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9]+"), "_")
    .trim('_')
    .ifBlank { "marker" }

private fun trimNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
