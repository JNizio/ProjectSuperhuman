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
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val SettingsNavy = Color(0xFF082D66)
private val SettingsBlue = Color(0xFF0D6CB4)
private val SettingsInk = Color(0xFF0B1F35)
private val SettingsMuted = Color(0xFF64748B)
private val SettingsGreen = Color(0xFF168A78)
private val SettingsRed = Color(0xFFCA3A3A)
private val SettingsBg = Color(0xFFF6F9FC)

@Composable
internal fun NativeSettingsParity(openLegacy: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var storedCount by remember { mutableStateOf(0L) }
    var status by remember { mutableStateOf("Data Vault ready") }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    suspend fun refreshCount() {
        storedCount = NativeDataHub.storedValueCountAsync()
    }

    LaunchedEffect(Unit) { refreshCount() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
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
                    }.onSuccess {
                        status = "Backup saved successfully"
                    }.onFailure {
                        status = "Backup export failed: ${it.message ?: "unknown error"}"
                    }
                    pendingExport = null
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
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
                    if (values.isEmpty()) {
                        status = "Backup contained no health values"
                    } else {
                        NativeDataHub.restoreValues(values, replace = false)
                        refreshCount()
                        status = "Restored ${values.size} value${if (values.size == 1) "" else "s"}. Existing data was kept."
                    }
                }.onFailure {
                    status = "Restore failed: ${it.message ?: "invalid backup"}"
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(SettingsBg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", color = SettingsInk, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text("Backup, restore, integrations and app controls.", color = SettingsMuted, fontSize = 11.sp)

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp)) {
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
                        .onSuccess { json ->
                            pendingExport = json
                            exportLauncher.launch("ProjectSuperhuman_backup_${LocalDate.now()}.json")
                        }
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
                        refreshCount()
                        status = "Native database cleared"
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(status, color = SettingsMuted, fontSize = 9.sp, lineHeight = 14.sp)
        }

        SettingsSection("Health integrations", "Health Connect sleep is native. Device-specific integrations can be added behind the same repository.")
        SettingsSection("Permissions", "Camera, barcode/OCR, Bluetooth and health permissions are requested only when the related feature needs them.")
        SettingsSection("Scientific engine", "Health scores and statuses use stored native metrics and explicit reference ranges; missing clinical ranges are not invented.")
        SettingsSection("11.0 compatibility", "Core workflows are native-first. Selected advanced legacy tools remain available from their module as a safety fallback during the 11.0 validation cycle.")
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun VaultButton(title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(accent, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Color.White.copy(alpha = .78f), fontSize = 8.sp, lineHeight = 12.sp)
        }
        Text("›", color = Color.White, fontSize = 22.sp)
    }
}

@Composable
private fun SettingsSection(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp)) {
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
            put("domain", value.domain.name)
            put("metric", value.metric)
            put("value", value.value)
            put("unit", value.unit)
            put("timestampEpochMs", value.timestampEpochMs)
            put("source", value.source)
            put("metadata", metadata)
        })
    }
    return JSONObject().apply {
        put("format", "project-superhuman-native-backup")
        put("schemaVersion", 1)
        put("exportedEpochMs", System.currentTimeMillis())
        put("valueCount", values.size)
        put("values", items)
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
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, metadataJson.optString(key, ""))
            }
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
