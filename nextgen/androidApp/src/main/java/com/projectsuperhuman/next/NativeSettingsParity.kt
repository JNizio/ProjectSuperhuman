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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val SettingsNavy get() = superhumanBrandText
private val SettingsBlue get() = superhumanBlue
private val SettingsInk get() = superhumanTextPrimary
private val SettingsMuted get() = superhumanTextMuted
private val SettingsGreen get() = superhumanGreen
private val SettingsRed get() = superhumanRed
private val SettingsBg get() = superhumanBackground
private val SettingsCard get() = superhumanSurface
private val SettingsSoft get() = superhumanSurfaceSoft

@Composable
internal fun NativeSettingsParity(openLegacy: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DeveloperDiagnostics.initialize(context)
    var developerMode by remember { mutableStateOf(DeveloperDiagnostics.isEnabled(context)) }
    var developerEvents by remember { mutableStateOf(DeveloperDiagnostics.latest(context, 18)) }
    var developerExportStatus by remember { mutableStateOf("") }
    var storedCount by remember { mutableStateOf(0L) }
    var syntheticCount by remember { mutableStateOf(0L) }
    var syntheticDays by remember { mutableStateOf(90) }
    var syntheticBusy by remember { mutableStateOf(false) }
    var syntheticStatus by remember { mutableStateOf("Synthetic history is isolated from genuine data by source.") }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Data Vault ready") }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmSyntheticClear by remember { mutableStateOf(false) }
    val darkMode = SuperhumanAppearance.darkMode

    suspend fun refreshCounts() {
        storedCount = NativeDataHub.storedValueCountAsync()
        syntheticCount = NativeDataHub.syntheticTestDataCount()
    }

    LaunchedEffect(Unit) { refreshCounts() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) {
            status = "Backup export cancelled"
            pendingExport = null
        } else {
            val payload = pendingExport
            if (payload == null) {
                status = "Nothing queued for export"
            } else {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { it.write(payload) }
                                ?: error("Could not open selected file")
                        }
                    }.onSuccess { status = "Backup saved successfully" }
                        .onFailure { status = "Backup export failed: ${it.message ?: "unknown error"}" }
                    pendingExport = null
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            status = "Restore cancelled"
        } else {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("Could not read selected backup")
                        decodeBackup(text)
                    }
                }.onSuccess { values ->
                    if (values.isEmpty()) status = "Backup contained no health values" else {
                        NativeDataHub.restoreValues(values, replace = false)
                        refreshCounts()
                        status = "Restored ${values.size} value${if (values.size == 1) "" else "s"}. Existing data was kept."
                    }
                }.onFailure { status = "Restore failed: ${it.message ?: "invalid backup"}" }
            }
        }
    }

    val developerExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri == null) {
            developerExportStatus = "Diagnostic export cancelled"
        } else {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val payload = DeveloperDiagnostics.exportText(context)
                        context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { it.write(payload) }
                            ?: error("Could not open selected file")
                    }
                }.onSuccess {
                    developerExportStatus = "Diagnostics log saved. Send that .txt file to ChatGPT."
                }.onFailure {
                    developerExportStatus = "Could not save diagnostics: ${it.message ?: "unknown error"}"
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(SettingsBg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", color = SettingsInk, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text("Appearance, smart devices, backup and app controls.", color = SettingsMuted, fontSize = 11.sp)

        SmartDevicesHub()

        Column(Modifier.fillMaxWidth().background(SettingsCard, RoundedCornerShape(22.dp)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Appearance", color = SettingsNavy, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(if (darkMode) "Dark mode is on" else "Light mode is on", color = SettingsMuted, fontSize = 10.sp)
                }
                Text(if (darkMode) "DARK" else "LIGHT", color = if (darkMode) superhumanAccent else SettingsBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().background(SettingsSoft, RoundedCornerShape(16.dp)).padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Dark mode", color = SettingsInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Use dark surfaces throughout Project Superhuman and match system-bar contrast.", color = SettingsMuted, fontSize = 8.sp, lineHeight = 12.sp)
                }
                Switch(
                    checked = darkMode,
                    onCheckedChange = { SuperhumanAppearance.setDarkMode(context, it) },
                    modifier = Modifier.semantics { contentDescription = "Dark mode" }
                )
            }
        }

        Column(Modifier.fillMaxWidth().background(SettingsCard, RoundedCornerShape(22.dp)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Data Vault", color = SettingsNavy, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("$storedCount shared health values stored", color = SettingsMuted, fontSize = 10.sp)
                }
                Text("NATIVE", color = SettingsGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(12.dp))
            VaultButton("Export backup", "Save a portable Project Superhuman JSON backup", SettingsBlue) {
                scope.launch {
                    runCatching { encodeBackup(NativeDataHub.allValuesAsync()) }
                        .onSuccess { json -> pendingExport = json; exportLauncher.launch("ProjectSuperhuman_backup_${LocalDate.now()}.json") }
                        .onFailure { status = "Could not prepare backup: ${it.message ?: "unknown error"}" }
                }
            }
            Spacer(Modifier.height(9.dp))
            VaultButton("Restore / import backup", "Merge a previous native backup without deleting current data", SettingsGreen) {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
            Spacer(Modifier.height(9.dp))
            VaultButton(
                if (confirmReset) "Tap again to erase all native data" else "Reset native data",
                if (confirmReset) "This permanently clears the shared native database" else "Two-tap protection prevents accidental deletion",
                SettingsRed
            ) {
                if (!confirmReset) {
                    confirmReset = true
                    status = "Reset armed. Tap the red button once more to confirm."
                } else {
                    scope.launch {
                        NativeDataHub.clearValuesAsync()
                        confirmReset = false
                        confirmSyntheticClear = false
                        refreshCounts()
                        status = "Native database cleared"
                        syntheticStatus = "No synthetic test data stored."
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(status, color = SettingsMuted, fontSize = 9.sp, lineHeight = 14.sp)
        }

        Column(Modifier.fillMaxWidth().background(SettingsCard, RoundedCornerShape(22.dp)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Developer mode", color = SettingsNavy, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(if (developerMode) "Diagnostic capture ON" else "Diagnostic capture OFF", color = if (developerMode) SettingsGreen else SettingsMuted, fontSize = 10.sp)
                }
                Text(if (developerMode) "ON" else "OFF", color = if (developerMode) SettingsGreen else SettingsMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "When enabled, Project Superhuman records a small persistent timeline of Trudy/Kokoro runtime stages and heap use. The final event is committed before native inference so it survives an app crash.",
                color = SettingsMuted, fontSize = 9.sp, lineHeight = 14.sp
            )
            Spacer(Modifier.height(10.dp))
            VaultButton(if (developerMode) "Turn developer mode off" else "Turn developer mode on", "Expose runtime diagnostics only while troubleshooting", if (developerMode) SettingsRed else SettingsBlue) {
                developerMode = !developerMode
                DeveloperDiagnostics.setEnabled(context, developerMode)
                developerEvents = DeveloperDiagnostics.latest(context, 18)
            }
            if (developerMode) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).background(SettingsSoft, RoundedCornerShape(12.dp)).clickable { developerEvents = DeveloperDiagnostics.latest(context, 18) }.padding(10.dp), contentAlignment = Alignment.Center) {
                        Text("REFRESH LOG", color = SettingsNavy, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                    Box(Modifier.weight(1f).background(superhumanErrorSurface, RoundedCornerShape(12.dp)).clickable {
                        DeveloperDiagnostics.clear(context); developerEvents = emptyList(); developerExportStatus = "Log cleared"
                    }.padding(10.dp), contentAlignment = Alignment.Center) {
                        Text("CLEAR LOG", color = SettingsRed, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(9.dp))
                VaultButton("Save diagnostics log", "Export device details and the full persistent runtime timeline as a .txt file", SettingsBlue) {
                    developerEvents = DeveloperDiagnostics.latest(context, 18)
                    developerExportLauncher.launch("ProjectSuperhuman_diagnostics_${LocalDate.now()}.txt")
                }
                if (developerExportStatus.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Text(developerExportStatus, color = SettingsMuted, fontSize = 8.sp, lineHeight = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text("DEVICE", color = SettingsMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
                Text(DeveloperDiagnostics.deviceSummary(), color = SettingsInk, fontSize = 8.sp, lineHeight = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("LATEST RUNTIME EVENTS", color = SettingsMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
                if (developerEvents.isEmpty()) {
                    Text("No diagnostic events yet. Open Trudy and reproduce the issue, then return here.", color = SettingsMuted, fontSize = 8.sp, lineHeight = 12.sp)
                } else developerEvents.forEach { event ->
                    Text(event, color = SettingsInk, fontSize = 7.5.sp, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(SettingsCard, RoundedCornerShape(22.dp)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Developer test data", color = SettingsNavy, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("$syntheticCount clearly tagged synthetic values stored", color = SettingsMuted, fontSize = 10.sp)
                }
                Text("SYNTHETIC", color = SettingsBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "Build correlated fake history for dashboards, trends, Data Vault aggregation and the Interpretation Engine. Nutrition uses realistic named breakfast, lunch and dinner entries with calories, macros and micronutrients. Generation uses the normal ingestion pipeline; genuine user records are never overwritten or cleared.",
                color = SettingsMuted, fontSize = 9.sp, lineHeight = 14.sp
            )
            Spacer(Modifier.height(12.dp))
            Text("HISTORY SPAN", color = SettingsMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30 to "30D", 90 to "90D", 180 to "180D", 365 to "1Y", 1825 to "5Y").forEach { (days, label) ->
                    SyntheticSpanButton(label, syntheticDays == days, Modifier.weight(1f)) {
                        if (!syntheticBusy) { syntheticDays = days; confirmSyntheticClear = false }
                    }
                }
            }
            if (syntheticDays == 1825) {
                Spacer(Modifier.height(7.dp))
                Text("5Y creates a large longitudinal test set and may take longer on slower devices.", color = SettingsMuted, fontSize = 8.sp, lineHeight = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            VaultButton(
                if (syntheticBusy) "Generating synthetic history…" else "Generate $syntheticDays days",
                "Sleep, real-looking meals + nutrients, body, exercise, mindfulness, hydration, clinical and wearable-style metrics",
                SettingsBlue
            ) {
                if (!syntheticBusy) scope.launch {
                    syntheticBusy = true; confirmSyntheticClear = false
                    syntheticStatus = "Generating $syntheticDays days through the Data Vault ingestion pipeline…"
                    try {
                        val result = NativeDataHub.generateSyntheticTestData(syntheticDays)
                        refreshCounts()
                        syntheticStatus = if (result.rejected == 0) {
                            "Generated ${result.accepted} synthetic values across $syntheticDays days. ${result.deduplicated} in-batch duplicate${if (result.deduplicated == 1) " was" else "s were"} skipped."
                        } else "Generated ${result.accepted} values; ${result.rejected} were rejected by normal ingestion validation."
                    } catch (t: Throwable) {
                        syntheticStatus = "Synthetic generation failed safely: ${t.message ?: "unknown error"}"
                    } finally { syntheticBusy = false }
                }
            }
            Spacer(Modifier.height(9.dp))
            VaultButton(
                if (confirmSyntheticClear) "Tap again to clear synthetic data" else "Clear synthetic test data",
                if (confirmSyntheticClear) "Only Project Superhuman synthetic-source records will be removed" else "Genuine user data and other imported sources are preserved",
                SettingsRed
            ) {
                if (syntheticBusy) return@VaultButton
                if (!confirmSyntheticClear) {
                    confirmSyntheticClear = true
                    syntheticStatus = "Synthetic cleanup armed. Tap the red button once more to confirm."
                } else scope.launch {
                    syntheticBusy = true
                    try {
                        val removed = NativeDataHub.clearSyntheticTestData()
                        refreshCounts()
                        syntheticStatus = "Cleared $removed synthetic value${if (removed == 1L) "" else "s"}. Genuine data was untouched."
                        confirmSyntheticClear = false
                    } catch (t: Throwable) {
                        syntheticStatus = "Synthetic cleanup failed safely: ${t.message ?: "unknown error"}"
                    } finally { syntheticBusy = false }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(syntheticStatus, color = SettingsMuted, fontSize = 9.sp, lineHeight = 14.sp)
        }

        ThemedH19cWearableCard()
        SettingsSection("Health integrations", "Health Connect and direct BLE wearable data share the same native Data Vault.")
        SettingsSection("Permissions", "Camera, barcode/OCR, Bluetooth and health permissions are requested only when the related feature needs them.")
        SettingsSection("Scientific engine", "Health scores and statuses use stored native metrics and explicit reference ranges; missing clinical ranges are not invented.")
        SettingsSection("11.0 compatibility", "Core workflows are native-first. Selected advanced legacy tools remain available from their module as a safety fallback during the 11.0 validation cycle.")
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun VaultButton(title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(accent, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Color.White.copy(alpha = .78f), fontSize = 8.sp, lineHeight = 12.sp)
        }
        Text("→", color = Color.White, fontSize = 22.sp)
    }
}

@Composable
private fun SyntheticSpanButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.background(if (selected) SettingsBlue else SettingsSoft, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) Color.White else SettingsNavy, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SettingsSection(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().background(SettingsCard, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Text(title, color = SettingsInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = SettingsMuted, fontSize = 9.sp, lineHeight = 14.sp)
    }
}

private fun encodeBackup(values: List<HealthValue>): String {
    val items = JSONArray()
    values.forEach { value ->
        val metadata = JSONObject()
        value.metadata.forEach { (key, item) -> metadata.put(key, item) }
        items.put(JSONObject().apply {
            put("domain", value.domain.name); put("metric", value.metric); put("value", value.value); put("unit", value.unit)
            put("timestampEpochMs", value.timestampEpochMs); put("source", value.source); put("metadata", metadata)
        })
    }
    return JSONObject().apply {
        put("format", "project-superhuman-native-backup"); put("schemaVersion", 1); put("exportedEpochMs", System.currentTimeMillis())
        put("valueCount", values.size); put("values", items)
    }.toString(2)
}

private fun decodeBackup(raw: String): List<HealthValue> {
    val root = JSONObject(raw)
    require(root.optString("format") == "project-superhuman-native-backup") { "Not a Project Superhuman native backup" }
    require(root.optInt("schemaVersion", 0) == 1) { "Unsupported backup version" }
    val items = root.getJSONArray("values")
    val out = ArrayList<HealthValue>(items.length())
    for (i in 0 until items.length()) {
        val item = items.getJSONObject(i)
        val domain = runCatching { HealthDomain.valueOf(item.getString("domain")) }.getOrNull() ?: continue
        val metadataJson = item.optJSONObject("metadata") ?: JSONObject()
        val metadata = buildMap<String, String> {
            val keys = metadataJson.keys()
            while (keys.hasNext()) { val key = keys.next(); put(key, metadataJson.optString(key, "")) }
        }
        out += HealthValue(
            domain = domain,
            metric = item.getString("metric"),
            value = item.getDouble("value"),
            unit = item.optString("unit", "value"),
            timestampEpochMs = item.getLong("timestampEpochMs"),
            source = item.optString("source", "native-restore"),
            metadata = metadata
        )
    }
    return out
}
