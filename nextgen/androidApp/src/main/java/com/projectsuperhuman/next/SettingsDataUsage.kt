package com.projectsuperhuman.next

import android.content.Context
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val UsageNavy = Color(0xFF082D66)
private val UsageMuted = Color(0xFF64748B)
private val UsageBorder = Color(0xFFE3EAF0)
private val UsageBg = Color(0xFFF6F9FC)

private data class UsageSlice(
    val label: String,
    val bytes: Long,
    val color: Color,
    val detail: String? = null
)

private data class DataUsageSnapshot(
    val totalLocalBytes: Long,
    val storageSlices: List<UsageSlice>,
    val domainSlices: List<UsageSlice>,
    val recordCount: Long
)

@Composable
internal fun SettingsDataUsageCard() {
    val context = LocalContext.current.applicationContext
    var refreshKey by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<DataUsageSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        snapshot = null
        error = null
        runCatching { loadDataUsage(context) }
            .onSuccess { snapshot = it }
            .onFailure { error = it.message ?: "Could not read local storage" }
    }

    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, UsageBorder, RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Data usage", color = UsageNavy, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("See what is using local app storage", color = UsageMuted, fontSize = 10.sp)
            }
            Box(
                Modifier.background(UsageBg, RoundedCornerShape(12.dp)).clickable { refreshKey++ }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("REFRESH", color = UsageNavy, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(Modifier.height(13.dp))

        when {
            error != null -> Text(error.orEmpty(), color = Color(0xFFCA3A3A), fontSize = 9.sp)
            snapshot == null -> Text("Calculating local storage…", color = UsageMuted, fontSize = 9.sp)
            else -> DataUsageContent(requireNotNull(snapshot))
        }
    }
}

@Composable
private fun DataUsageContent(snapshot: DataUsageSnapshot) {
    Text(formatBytes(snapshot.totalLocalBytes), color = UsageNavy, fontSize = 28.sp, fontWeight = FontWeight.Black)
    Text("local Project Superhuman data", color = UsageMuted, fontSize = 9.sp)
    Spacer(Modifier.height(11.dp))

    UsageBar(snapshot.storageSlices)
    Spacer(Modifier.height(10.dp))
    snapshot.storageSlices.filter { it.bytes > 0L }.forEach { UsageLegendRow(it) }

    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(UsageBorder))
    Spacer(Modifier.height(13.dp))

    Text("DATA VAULT BY CATEGORY", color = UsageMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
    Spacer(Modifier.height(3.dp))
    Text(
        "${snapshot.recordCount} stored health values · coloured by health category",
        color = UsageMuted,
        fontSize = 9.sp
    )
    Spacer(Modifier.height(10.dp))

    if (snapshot.domainSlices.isEmpty()) {
        Text("No Data Vault records yet.", color = UsageMuted, fontSize = 9.sp)
    } else {
        UsageBar(snapshot.domainSlices)
        Spacer(Modifier.height(10.dp))
        snapshot.domainSlices.forEach { UsageLegendRow(it) }
        Spacer(Modifier.height(7.dp))
        Text(
            "Category sizes are estimated from each category's share of stored records. The total SQLite Data Vault size above is measured from the actual database files.",
            color = UsageMuted,
            fontSize = 8.sp,
            lineHeight = 12.sp
        )
    }
}

@Composable
private fun UsageBar(slices: List<UsageSlice>) {
    val visible = slices.filter { it.bytes > 0L }
    val total = visible.sumOf { it.bytes }.coerceAtLeast(1L)
    Canvas(
        Modifier.fillMaxWidth().height(12.dp)
            .background(Color(0xFFEDF2F6), RoundedCornerShape(8.dp))
    ) {
        var left = 0f
        visible.forEachIndexed { index, slice ->
            val width = if (index == visible.lastIndex) {
                size.width - left
            } else {
                size.width * (slice.bytes.toDouble() / total.toDouble()).toFloat()
            }
            if (width > 0f) {
                drawRect(color = slice.color, topLeft = androidx.compose.ui.geometry.Offset(left, 0f), size = androidx.compose.ui.geometry.Size(width, size.height))
                left += width
            }
        }
    }
}

@Composable
private fun UsageLegendRow(slice: UsageSlice) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(slice.color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(slice.label, color = UsageNavy, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                slice.detail?.let { Text(it, color = UsageMuted, fontSize = 7.5.sp) }
            }
        }
        Text(formatBytes(slice.bytes), color = UsageMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

private suspend fun loadDataUsage(context: Context): DataUsageSnapshot = withContext(Dispatchers.IO) {
    val diagnostics = NativeDataHub.diagnostics()
    val db = context.getDatabasePath("project_superhuman.db")
    val vaultBytes = fileBytes(db) + fileBytes(File(db.path + "-wal")) + fileBytes(File(db.path + "-shm"))
    val filesBytes = directoryBytes(context.filesDir) + directoryBytes(context.noBackupFilesDir)
    val cacheBytes = directoryBytes(context.cacheDir) + directoryBytes(context.codeCacheDir)
    val preferencesBytes = directoryBytes(File(context.applicationInfo.dataDir, "shared_prefs"))

    val storage = listOf(
        UsageSlice("Data Vault", vaultBytes, Color(0xFF0D6CB4), "SQLite health history"),
        UsageSlice("Models & files", filesBytes, Color(0xFF8B79C8), "Downloaded/private app files"),
        UsageSlice("Cache", cacheBytes, Color(0xFF168A78), "Temporary reusable data"),
        UsageSlice("Settings & diagnostics", preferencesBytes, Color(0xFFF09B4C), "Preferences and small logs")
    )

    val totalRecords = diagnostics.recordsByDomain.values.sum()
    val domains = diagnostics.recordsByDomain.entries
        .filter { it.value > 0L }
        .sortedByDescending { it.value }
        .map { (domain, count) ->
            val estimated = if (totalRecords <= 0L || vaultBytes <= 0L) 0L
            else (vaultBytes.toDouble() * count.toDouble() / totalRecords.toDouble()).toLong()
            UsageSlice(
                label = domainLabel(domain),
                bytes = estimated.coerceAtLeast(1L),
                color = domainColor(domain),
                detail = "$count record${if (count == 1L) "" else "s"}"
            )
        }

    DataUsageSnapshot(
        totalLocalBytes = storage.sumOf { it.bytes },
        storageSlices = storage,
        domainSlices = domains,
        recordCount = diagnostics.totalRecords
    )
}

private fun fileBytes(file: File): Long = runCatching { if (file.isFile) file.length() else 0L }.getOrDefault(0L)

private fun directoryBytes(directory: File): Long = runCatching {
    if (!directory.exists()) 0L else directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

private fun domainLabel(domain: HealthDomain): String = when (domain) {
    HealthDomain.CLINICAL -> "Clinical"
    HealthDomain.BLOOD_PRESSURE -> "Blood pressure"
    HealthDomain.BODY -> "Body"
    HealthDomain.SLEEP -> "Sleep"
    HealthDomain.NUTRITION -> "Nutrition"
    HealthDomain.HYDRATION -> "Hydration"
    HealthDomain.EXERCISE -> "Exercise"
    HealthDomain.MINDFULNESS -> "Mindfulness"
    HealthDomain.EMOTIONAL -> "Emotional"
    HealthDomain.ENVIRONMENT -> "Environment"
}

private fun domainColor(domain: HealthDomain): Color = when (domain) {
    HealthDomain.CLINICAL -> Color(0xFFD05B5B)
    HealthDomain.BLOOD_PRESSURE -> Color(0xFFB94B68)
    HealthDomain.BODY -> Color(0xFF4E7FB8)
    HealthDomain.SLEEP -> Color(0xFF7568B8)
    HealthDomain.NUTRITION -> Color(0xFFE2984E)
    HealthDomain.HYDRATION -> Color(0xFF36A5C6)
    HealthDomain.EXERCISE -> Color(0xFF3E9B67)
    HealthDomain.MINDFULNESS -> Color(0xFF39A394)
    HealthDomain.EMOTIONAL -> Color(0xFFA96FB5)
    HealthDomain.ENVIRONMENT -> Color(0xFF45A7D8)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}
