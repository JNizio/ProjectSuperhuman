package com.projectsuperhuman.next

import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text as MlText
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

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
    val sourceText: String = "",
    val confidence: Double = 0.0,
    val rangeSource: String = "",
    val unitSource: String = "",
    val comparator: String = ""
) {
    val metric: String get() = "clinical.${slug(name)}"
}

@Composable
internal fun NativeClinicalParityScreen(onBack: () -> Unit, openLegacy: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf<List<HealthValue>>(emptyList()) }
    var drafts by remember { mutableStateOf<List<ClinicalDraft>>(emptyList()) }
    var importState by remember { mutableStateOf("Choose one or several screenshots.") }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var detectedText by remember { mutableStateOf("") }

    suspend fun refresh() {
        saved = NativeDataHub.latestForDomain(HealthDomain.CLINICAL)
            .filter { it.metric.startsWith("clinical.") }
            .sortedBy { it.metric }
    }

    LaunchedEffect(Unit) { refresh() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        selectedUris = uris
        detectedText = ""
        importState = "Reading ${uris.size} screenshot${if (uris.size == 1) "" else "s"}…"
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val collected = mutableListOf<ClinicalDraft>()
        val raw = mutableListOf<String>()
        var remaining = uris.size

        fun finishOne() {
            remaining--
            if (remaining > 0) return
            recognizer.close()
            scope.launch {
                val enriched = collected
                    .groupBy { it.metric + "|" + it.value }
                    .mapNotNull { (_, matches) -> matches.maxByOrNull { it.confidence } }
                    .map { draft ->
                        if (draft.low.isNotBlank() || draft.high.isNotBlank()) draft
                        else {
                            val previous = NativeDataHub.latest(draft.metric)
                            val rememberedLow = previous?.metadata?.get("rangeLow").orEmpty()
                            val rememberedHigh = previous?.metadata?.get("rangeHigh").orEmpty()
                            if (rememberedLow.isBlank() && rememberedHigh.isBlank()) draft
                            else draft.copy(low = rememberedLow, high = rememberedHigh, rangeSource = "remembered")
                        }
                    }
                drafts = enriched.sortedBy { it.name.lowercase(Locale.ROOT) }
                detectedText = raw.joinToString("\n\n").take(5000)
                val withRanges = enriched.count { it.low.isNotBlank() || it.high.isNotBlank() }
                val withUnits = enriched.count { it.unit.isNotBlank() }
                importState = if (enriched.isEmpty()) "No structured results detected yet." else "Found ${enriched.size} results · $withUnits units · $withRanges ranges"
            }
        }

        uris.forEach { uri ->
            try {
                val image = InputImage.fromFilePath(context, uri)
                recognizer.process(image)
                    .addOnSuccessListener { text ->
                        raw += text.text
                        collected += parseClinicalText(text)
                        finishOne()
                    }
                    .addOnFailureListener { finishOne() }
            } catch (_: Exception) { finishOne() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(ClinicalBg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ClinicalHeader(onBack)

        Text("CLINICAL IMPORT", color = ClinicalBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
        Text("Add your results.", color = ClinicalInk, fontSize = 31.sp, fontWeight = FontWeight.Black, lineHeight = 35.sp)
        Text(
            "Choose one or several screenshots. Project Superhuman will read the text automatically, identify supported lab markers, and turn them into editable structured data before anything is saved.",
            color = ClinicalMuted, fontSize = 14.sp, lineHeight = 21.sp
        )

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ClinicalButton("Choose screenshots", ClinicalBlue) { launcher.launch("image/*") }
            Text(importState, color = ClinicalMuted, fontSize = 9.sp, lineHeight = 13.sp)

            if (selectedUris.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    selectedUris.take(3).forEach { uri ->
                        ClinicalScreenshotPreview(uri, Modifier.weight(1f))
                    }
                }
            }

            if (detectedText.isNotBlank()) {
                Text("Detected text", color = ClinicalInk, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text("The OCR output is shown for verification; only structured fields below are saved.", color = ClinicalMuted, fontSize = 9.sp, lineHeight = 13.sp)
                Box(Modifier.fillMaxWidth().background(ClinicalBg, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text(detectedText, color = ClinicalInk, fontSize = 9.sp, lineHeight = 13.sp)
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Review before saving", color = ClinicalInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text("Recognised fields are editable before they become part of your health history.", color = ClinicalMuted, fontSize = 9.sp, lineHeight = 13.sp)

            if (drafts.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                    Text("No results detected yet.", color = ClinicalMuted, fontSize = 12.sp)
                }
            } else {
                drafts.forEachIndexed { index, draft ->
                    ClinicalLegacyReviewRow(
                        draft = draft,
                        onChange = { changed -> drafts = drafts.toMutableList().also { it[index] = changed } },
                        onRemove = { drafts = drafts.toMutableList().also { it.removeAt(index) } }
                    )
                }
            }

            Box(
                Modifier.fillMaxWidth().background(Color(0xFFEAF2FA), RoundedCornerShape(16.dp)).clickable {
                    drafts = drafts + ClinicalDraft(name = "", value = "", unit = "", confidence = 1.0)
                }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+ Add result manually", color = ClinicalNavy, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }

            ClinicalButton("Confirm & save", ClinicalBlue) {
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
                            source = "native-clinical-ocr-v112",
                            metadata = mapOf(
                                "displayName" to d.name.trim(),
                                "rangeLow" to d.low.trim(),
                                "rangeHigh" to d.high.trim(),
                                "status" to statusOf(number, low, high),
                                "rangeSource" to d.rangeSource.ifBlank { if (d.low.isNotBlank() || d.high.isNotBlank()) "reviewed" else "none" },
                                "unitSource" to d.unitSource,
                                "ocrConfidence" to String.format(Locale.US, "%.2f", d.confidence),
                                "rawOcr" to d.sourceText.take(700),
                                "qualifier" to d.comparator
                            )
                        )
                    }
                    drafts = emptyList()
                    selectedUris = emptyList()
                    detectedText = ""
                    importState = "Saved to your clinical history."
                    refresh()
                }
            }
        }

        if (saved.isNotEmpty()) {
            Text("LATEST RESULTS", color = ClinicalMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            saved.take(8).forEach { ClinicalResultRow(it) }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ClinicalScreenshotPreview(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }
    Box(modifier.aspectRatio(0.78f).clip(RoundedCornerShape(14.dp)).background(ClinicalBg), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Clinical screenshot preview", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text("Preview unavailable", color = ClinicalMuted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun ClinicalLegacyReviewRow(draft: ClinicalDraft, onChange: (ClinicalDraft) -> Unit, onRemove: () -> Unit) {
    val value = draft.value.toDoubleOrNull()
    val low = draft.low.toDoubleOrNull()
    val high = draft.high.toDoubleOrNull()
    val status = if (value == null) "CHECK" else statusOf(value, low, high)
    val statusLabel = when (status) { "NORMAL" -> "Within range"; "HIGH" -> "High"; "LOW" -> "Low"; else -> "Check range" }
    val statusColor = when (status) { "NORMAL" -> ClinicalGood; "HIGH", "LOW" -> ClinicalBad; else -> ClinicalWarn }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClinicalChip(statusLabel, statusColor, Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            Text("Remove", color = Color(0xFFA95C5C), fontSize = 12.sp, modifier = Modifier.clickable(onClick = onRemove).padding(6.dp))
        }
        OutlinedTextField(draft.name, { onChange(draft.copy(name = it)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("TEST") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(draft.value, { onChange(draft.copy(value = it.filterClinicalNumber())) }, Modifier.weight(1f), singleLine = true, label = { Text("VALUE") })
            OutlinedTextField(draft.unit, { onChange(draft.copy(unit = it, unitSource = "reviewed")) }, Modifier.weight(1f), singleLine = true, label = { Text("UNIT") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(draft.low, { onChange(draft.copy(low = it.filterClinicalNumber(), rangeSource = "reviewed")) }, Modifier.weight(1f), singleLine = true, label = { Text("LOW") })
            OutlinedTextField(draft.high, { onChange(draft.copy(high = it.filterClinicalNumber(), rangeSource = "reviewed")) }, Modifier.weight(1f), singleLine = true, label = { Text("HIGH") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (draft.unit.isNotBlank()) ClinicalChip(if (draft.unitSource == "screenshot") "Unit read" else "Unit inferred", ClinicalBlue)
            if (draft.low.isNotBlank() || draft.high.isNotBlank()) ClinicalChip(if (draft.rangeSource == "screenshot") "Range read" else "Range remembered", ClinicalNavy)
            if (draft.comparator.isNotBlank()) ClinicalChip("${draft.comparator} result", ClinicalNavy)
            if (draft.confidence in 0.0..0.72) ClinicalChip("Check OCR", ClinicalWarn)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE6EDF4)))
    }
}

@Composable
private fun ClinicalChip(text: String, accent: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(accent.copy(alpha = 0.10f), RoundedCornerShape(99.dp)).padding(horizontal = 9.dp, vertical = 5.dp)) {
        Text(text, color = accent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ClinicalHeader(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.superhumanTopButton(onClick = onBack), contentAlignment = Alignment.Center) {
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
            Column(Modifier.weight(1f)) {
                Text(status, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                val provenance = buildList {
                    if (draft.unit.isNotBlank()) add(if (draft.unitSource == "screenshot") "unit read" else "unit inferred")
                    if (draft.low.isNotBlank() || draft.high.isNotBlank()) add(if (draft.rangeSource == "screenshot") "range read" else "range remembered")
                    if (draft.confidence > 0) add("${(draft.confidence * 100).toInt()}% match")
                }.joinToString(" · ")
                if (provenance.isNotBlank()) Text(provenance, color = ClinicalMuted, fontSize = 8.sp)
            }
            Text("REMOVE", color = ClinicalBad, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onRemove).padding(6.dp))
        }
        OutlinedTextField(draft.name, { onChange(draft.copy(name = it)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Test") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(draft.value, { onChange(draft.copy(value = it.filterClinicalNumber())) }, Modifier.weight(1f), singleLine = true, label = { Text("Result") })
            OutlinedTextField(draft.unit, { onChange(draft.copy(unit = it, unitSource = "reviewed")) }, Modifier.weight(1f), singleLine = true, label = { Text("Unit") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(draft.low, { onChange(draft.copy(low = it.filterClinicalNumber(), rangeSource = "reviewed")) }, Modifier.weight(1f), singleLine = true, label = { Text("Range low") })
            OutlinedTextField(draft.high, { onChange(draft.copy(high = it.filterClinicalNumber(), rangeSource = "reviewed")) }, Modifier.weight(1f), singleLine = true, label = { Text("Range high") })
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
            val qualifier = value.metadata["qualifier"].orEmpty()
            Text("${qualifier}${trimNumber(value.value)} ${value.unit}", color = ClinicalInk, fontSize = 12.sp, fontWeight = FontWeight.Black)
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

private data class OcrLine(val text: String, val block: Int, val top: Int, val left: Int)
private data class RangeHit(val low: String = "", val high: String = "")
private data class ValueHit(val value: String, val lineIndex: Int, val confidence: Double, val comparator: String = "")

private val LAB_DEFS = CLINICAL_MARKER_REGISTRY

private fun parseClinicalText(ocr: MlText): List<ClinicalDraft> {
    val lines = extractOcrLines(ocr)
    if (lines.isEmpty()) return parseClinicalTextFallback(ocr.text)
    val out = mutableListOf<ClinicalDraft>()

    for (i in lines.indices) {
        val heading = lines[i]
        val def = markerDef(heading.text) ?: continue
        if (!headingLooksReal(heading.text, def)) continue

        var end = minOf(lines.size, i + 12)
        for (j in i + 1 until end) {
            val lineText = lines[j].text
            val nextDef = markerDef(lineText)
            if (nextDef != null && headingLooksReal(lineText, nextDef)) {
                end = j
                break
            }
            if (isNhsSectionBoundary(lineText)) {
                end = j
                break
            }
        }
        val window = lines.subList(i, end)
        val valueHit = findResult(window, def) ?: continue
        val blockText = window.joinToString("\n") { it.text }
        val range = findRange(blockText)
        val unitRead = findUnit(blockText, def.unit)
        val confidence = (valueHit.confidence + markerConfidence(heading.text, def) + if (range.low.isNotBlank() || range.high.isNotBlank()) .08 else 0.0 + if (unitRead.first.isNotBlank()) .05 else 0.0).coerceIn(.0, .99)

        val draft = ClinicalDraft(
            name = def.name,
            value = valueHit.value,
            unit = unitRead.first.ifBlank { def.unit },
            low = range.low,
            high = range.high,
            sourceText = blockText,
            confidence = confidence,
            rangeSource = if (range.low.isNotBlank() || range.high.isNotBlank()) "screenshot" else "",
            unitSource = if (unitRead.first.isNotBlank()) "screenshot" else if (def.unit.isNotBlank()) "marker database" else "",
            comparator = valueHit.comparator
        )
        if (out.none { it.metric == draft.metric && it.value == draft.value }) out += draft
    }

    return out
}

private fun extractOcrLines(ocr: MlText): List<OcrLine> {
    val all = mutableListOf<OcrLine>()
    ocr.textBlocks.forEachIndexed { blockIndex, block ->
        block.lines.forEach { line ->
            val box = line.boundingBox
            val text = line.text.replace('–', '-').replace('—', '-').trim()
            if (text.length > 1 && !clinicalBoilerplate(text)) {
                all += OcrLine(text, blockIndex, box?.top ?: Int.MAX_VALUE / 4, box?.left ?: 0)
            }
        }
    }
    return all.sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left })
}

private fun findResult(window: List<OcrLine>, def: ClinicalMarkerDef): ValueHit? {
    val numberRx = Regex("(?<![A-Za-z])([<>]=?\\s*)?(-?\\d+(?:[.,]\\d+)?)(?![A-Za-z])")
    for (offset in window.indices) {
        val raw = window[offset].text
        if (offset > 0 && markerDef(raw) != null && headingLooksReal(raw, markerDef(raw)!!)) break
        if (looksLikeRangeLine(raw)) continue
        val candidate = if (offset == 0) stripAliases(raw, def.aliases) else raw
        val hits = numberRx.findAll(candidate).toList()
        if (hits.isEmpty()) continue
        for (hit in hits) {
            val v = hit.groupValues[2].replace(',', '.')
            val number = v.toDoubleOrNull() ?: continue
            if (looksLikeDateNumber(candidate, hit.range.first)) continue
            if (abs(number) > 1_000_000) continue
            val hasExpectedUnit = def.unit.isNotBlank() && unitRegexFor(def.unit).containsMatchIn(raw.replace(" ", ""))
            val confidence = when {
                offset == 0 && hasExpectedUnit -> .86
                offset <= 2 && hasExpectedUnit -> .88
                offset <= 2 -> .78
                else -> .68
            }
            val comparator = hit.groupValues[1].replace(" ", "").trim()
            return ValueHit(v, offset, confidence, comparator)
        }
    }
    return null
}

private fun findRange(text: String): RangeHit {
    val s = text.replace(',', '.').replace('–', '-').replace('—', '-')
    val explicit = listOf(
        Regex("(?:reference|normal|healthy|expected)\\s*(?:range|interval)?[^\\d<>-]{0,45}(?:is\\s*)?(?:between\\s*)?(-?\\d+(?:\\.\\d+)?)\\s*(?:and|to|-)\\s*(-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),
        Regex("\\bbetween\\s+(-?\\d+(?:\\.\\d+)?)\\s+(?:and|to)\\s+(-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
    )
    explicit.forEach { rx ->
        rx.find(s)?.let { return RangeHit(it.groupValues[1], it.groupValues[2]) }
    }

    val labelledLines = s.lines().filter { Regex("range|reference|normal|interval", RegexOption.IGNORE_CASE).containsMatchIn(it) }
    val pairRx = Regex("(-?\\d+(?:\\.\\d+)?)\\s*(?:-|to|–)\\s*(-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
    labelledLines.forEach { line -> pairRx.find(line)?.let { return RangeHit(it.groupValues[1], it.groupValues[2]) } }

    val less = Regex("(?:reference|normal|range)[^\\d<>]{0,35}(?:<|less than|up to)\\s*(-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(s)
    if (less != null) return RangeHit(high = less.groupValues[1])
    val greater = Regex("(?:reference|normal|range)[^\\d<>]{0,35}(?:>|greater than|at least)\\s*(-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(s)
    if (greater != null) return RangeHit(low = greater.groupValues[1])

    return RangeHit()
}

private fun findUnit(text: String, expected: String): Pair<String, Double> {
    val compact = text.replace(" ", "")
    val units = listOf(
        "10^12/L" to Regex("10[\\*x×^]?12/L", RegexOption.IGNORE_CASE),
        "10^9/L" to Regex("10[\\*x×^]?9/L", RegexOption.IGNORE_CASE),
        "mL/min/1.73m2" to Regex("mL/min/1[.]?73m(?:2|\\^2)", RegexOption.IGNORE_CASE),
        "mmol/mol" to Regex("mmol/mol", RegexOption.IGNORE_CASE),
        "mg/mmol" to Regex("mg/mmol", RegexOption.IGNORE_CASE),
        "umol/L" to Regex("(?:µmol|umol)/L", RegexOption.IGNORE_CASE),
        "mmol/L" to Regex("mmol/L", RegexOption.IGNORE_CASE),
        "pmol/L" to Regex("pmol/L", RegexOption.IGNORE_CASE),
        "nmol/L" to Regex("nmol/L", RegexOption.IGNORE_CASE),
        "mIU/L" to Regex("mIU/L", RegexOption.IGNORE_CASE),
        "mU/L" to Regex("mU/L", RegexOption.IGNORE_CASE),
        "IU/mL" to Regex("IU/mL", RegexOption.IGNORE_CASE),
        "IU/L" to Regex("IU/L", RegexOption.IGNORE_CASE),
        "U/L" to Regex("U/L", RegexOption.IGNORE_CASE),
        "ng/mL" to Regex("ng/mL", RegexOption.IGNORE_CASE),
        "ng/L" to Regex("ng/L", RegexOption.IGNORE_CASE),
        "ug/L" to Regex("(?:µg|ug)/L", RegexOption.IGNORE_CASE),
        "mg/L" to Regex("mg/L", RegexOption.IGNORE_CASE),
        "g/L" to Regex("g/L", RegexOption.IGNORE_CASE),
        "L/L" to Regex("L/L", RegexOption.IGNORE_CASE),
        "mm/h" to Regex("mm/h", RegexOption.IGNORE_CASE),
        "mmHg" to Regex("mmHg", RegexOption.IGNORE_CASE),
        "fL" to Regex("\\bfL\\b", RegexOption.IGNORE_CASE),
        "pg" to Regex("\\bpg\\b", RegexOption.IGNORE_CASE),
        "%" to Regex("%")
    )
    units.forEach { (unit, rx) -> if (rx.containsMatchIn(compact)) return unit to .95 }
    if (Regex("\\b(?:sec|second|seconds)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return "sec" to .9
    if (expected == "Ratio" && Regex("\\bratio\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return "Ratio" to .85
    return "" to 0.0
}

private fun findGenericRows(lines: List<OcrLine>): List<ClinicalDraft> {
    val out = mutableListOf<ClinicalDraft>()
    val numberRx = Regex("^\\s*([<>]=?\\s*)?(-?\\d+(?:[.,]\\d+)?)\\s*.*$")
    for (i in 0 until lines.size - 1) {
        val heading = lines[i].text.trim()
        if (!looksGenericHeading(heading) || markerDef(heading) != null) continue
        val next = lines.getOrNull(i + 1)?.text.orEmpty()
        val hit = numberRx.find(next) ?: continue
        if (looksLikeRangeLine(next)) continue
        val value = hit.groupValues[2].replace(',', '.')
        if (value.toDoubleOrNull() == null) continue
        val chunk = lines.subList(i, minOf(lines.size, i + 6)).joinToString("\n") { it.text }
        val range = findRange(chunk)
        val unit = findUnit(chunk, "").first
        if (range.low.isBlank() && range.high.isBlank() && unit.isBlank()) continue
        val draft = ClinicalDraft(
            name = heading.take(90),
            value = value,
            unit = unit,
            low = range.low,
            high = range.high,
            sourceText = chunk,
            confidence = .58 + if (unit.isNotBlank()) .08 else 0.0 + if (range.low.isNotBlank() || range.high.isNotBlank()) .08 else 0.0,
            rangeSource = if (range.low.isNotBlank() || range.high.isNotBlank()) "screenshot" else "",
            unitSource = if (unit.isNotBlank()) "screenshot" else ""
        )
        if (out.none { it.metric == draft.metric && it.value == draft.value }) out += draft
    }
    return out
}

private fun parseClinicalTextFallback(raw: String): List<ClinicalDraft> {
    val lines = raw.lines().map { it.replace('–', '-').replace('—', '-').trim() }.filter { it.length > 1 && !clinicalBoilerplate(it) }
    val fake = lines.mapIndexed { i, s -> OcrLine(s, 0, i * 20, 0) }
    val out = mutableListOf<ClinicalDraft>()
    for (i in fake.indices) {
        val def = markerDef(fake[i].text) ?: continue
        if (!headingLooksReal(fake[i].text, def)) continue
        val window = fake.subList(i, minOf(fake.size, i + 9))
        val value = findResult(window, def) ?: continue
        val block = window.joinToString("\n") { it.text }
        val range = findRange(block)
        val unit = findUnit(block, def.unit).first
        out += ClinicalDraft(def.name, value.value, unit.ifBlank { def.unit }, range.low, range.high, block, .7, if (range.low.isNotBlank() || range.high.isNotBlank()) "screenshot" else "", if (unit.isNotBlank()) "screenshot" else "marker database", value.comparator)
    }
    return out.distinctBy { it.metric + "|" + it.value }
}

private fun markerDef(text: String): ClinicalMarkerDef? {
    val q = labNorm(text)
    if (q.isBlank()) return null
    var best: Pair<ClinicalMarkerDef, Double>? = null
    LAB_DEFS.forEach { def ->
        def.aliases.forEach { aliasRaw ->
            val alias = labNorm(aliasRaw)
            val score = when {
                q == alias -> 1.0
                alias.length > 4 && q.startsWith("$alias ") -> .97
                alias.length > 4 && q.contains(alias) -> .93
                alias.length <= 4 && Regex("(?:^|\\s)${Regex.escape(alias)}(?:$|\\s)").containsMatchIn(q) -> .92
                abs(q.length - alias.length) <= max(2, alias.length / 7) && editDistance(q, alias) <= max(1, alias.length / 10) -> .82
                else -> 0.0
            }
            if (score > (best?.second ?: 0.0)) best = def to score
        }
    }
    return best?.takeIf { it.second >= .82 }?.first
}

private fun markerConfidence(text: String, def: ClinicalMarkerDef): Double {
    val q = labNorm(text)
    var best = .0
    def.aliases.forEach { a0 ->
        val a = labNorm(a0)
        best = max(best, when {
            q == a -> .98
            q.startsWith("$a ") || q.contains(a) -> .93
            editDistance(q, a) <= max(1, a.length / 10) -> .82
            else -> .0
        })
    }
    return best
}

private fun headingLooksReal(text: String, def: ClinicalMarkerDef): Boolean {
    val s = text.trim()
    if (s.isBlank() || s.length > 110 || clinicalBoilerplate(s)) return false
    val q = labNorm(s)
    return def.aliases.any { a0 ->
        val a = labNorm(a0)
        q == a || q.startsWith("$a ") || q.contains(a) || editDistance(q, a) <= max(1, a.length / 10)
    }
}

private fun looksGenericHeading(text: String): Boolean {
    val s = text.trim()
    if (s.length !in 3..80 || clinicalBoilerplate(s)) return false
    if (s.count(Char::isLetter) < 3 || s.count(Char::isDigit) > s.length / 4) return false
    if (Regex("(?:range|reference|result|date|time|page|status|normal|high|low|screening test|function test|professional.s comment)", RegexOption.IGNORE_CASE).containsMatchIn(s)) return false
    if (Regex("^(?:m?iu|mu|u|mmol|umol|µmol|pmol|nmol|mg|ug|µg|ng|g|fl|pg)(?:/|$)|^ratio[.]?$|^sec[.]?$", RegexOption.IGNORE_CASE).containsMatchIn(s.replace(" ", ""))) return false
    return true
}

private fun stripAliases(text: String, aliases: List<String>): String {
    var out = text
    aliases.sortedByDescending { it.length }.forEach { alias ->
        out = out.replace(Regex(Regex.escape(alias), RegexOption.IGNORE_CASE), " ")
    }
    return out
}

private fun isNhsSectionBoundary(text: String): Boolean {
    val s = labNorm(text)
    if (s.isBlank()) return false
    return listOf(
        "learn more about",
        "view test result history",
        "healthcare professional s comment",
        "healthcare professional's comment",
        "help with abbreviations",
        "give feedback about the nhs app",
        "you may see medical abbreviations",
        "app help",
        "home messages profile"
    ).any { s.contains(labNorm(it)) }
}

private fun looksLikeRangeLine(text: String): Boolean {
    if (Regex("range|reference|normal interval|expected", RegexOption.IGNORE_CASE).containsMatchIn(text)) return true
    return Regex("\\d+(?:[.,]\\d+)?\\s*(?:-|to)\\s*\\d+(?:[.,]\\d+)?", RegexOption.IGNORE_CASE).containsMatchIn(text)
}

private fun looksLikeDateNumber(text: String, start: Int): Boolean {
    val around = text.substring(maxOf(0, start - 4), minOf(text.length, start + 12))
    return Regex("\\d{1,2}[/.\\-]\\d{1,2}[/.\\-](?:20)?\\d{2}").containsMatchIn(around)
}

private fun clinicalBoilerplate(line: String): Boolean = Regex(
    "(?:learn more about|lab tests online|view test result history|app help|home messages profile|your results|nhs app|page \\d+|download|print|contact your gp)",
    RegexOption.IGNORE_CASE
).containsMatchIn(line)

private fun unitRegexFor(unit: String): Regex = when (unit) {
    "10^12/L" -> Regex("10[\\*x×^]?12/L", RegexOption.IGNORE_CASE)
    "10^9/L" -> Regex("10[\\*x×^]?9/L", RegexOption.IGNORE_CASE)
    "umol/L" -> Regex("(?:µmol|umol)/L", RegexOption.IGNORE_CASE)
    "sec" -> Regex("(?:sec|second|seconds|\\bs\\b)", RegexOption.IGNORE_CASE)
    "Ratio" -> Regex("ratio", RegexOption.IGNORE_CASE)
    else -> Regex(Regex.escape(unit), RegexOption.IGNORE_CASE)
}

private fun labNorm(value: String): String = value
    .lowercase(Locale.ROOT)
    .replace('–', '-')
    .replace('—', '-')
    .replace('’', '\'')
    .replace(Regex("[^a-z0-9%+./^* -]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun editDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
            current[j] = minOf(
                current[j - 1] + 1,
                previous[j] + 1,
                previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            )
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[b.length]
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
