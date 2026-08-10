from pathlib import Path

p = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NativeClinicalParity.kt')
s = p.read_text()

imports = {
    'import android.net.Uri\n': 'import android.net.Uri\nimport android.graphics.BitmapFactory\n',
    'import androidx.compose.foundation.background\n': 'import androidx.compose.foundation.background\nimport androidx.compose.foundation.Image\n',
    'import androidx.compose.foundation.layout.fillMaxWidth\n': 'import androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.aspectRatio\n',
    'import androidx.compose.foundation.shape.RoundedCornerShape\n': 'import androidx.compose.foundation.shape.RoundedCornerShape\nimport androidx.compose.ui.draw.clip\n',
    'import androidx.compose.ui.graphics.Color\n': 'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.asImageBitmap\n',
    'import androidx.compose.ui.platform.LocalContext\n': 'import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.layout.ContentScale\n',
}
for old, new in imports.items():
    if new.split('\n')[-2] not in s and old in s:
        s = s.replace(old, new, 1)

start = s.index('@Composable\ninternal fun NativeClinicalParityScreen')
end = s.index('@Composable\nprivate fun ClinicalHeader', start)

new_ui = r'''@Composable
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
                                "rawOcr" to d.sourceText.take(700)
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

'''

s = s[:start] + new_ui + s[end:]
p.write_text(s)
print('Legacy-style Clinical import UI applied')
