package com.projectsuperhuman.next

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Xml
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

internal data class ClinicalCondition(
    val id: String,
    val code: String,
    val title: String,
    val source: String,
    val sourceVersion: String
)

internal data class ActiveClinicalCondition(
    val metric: String,
    val id: String,
    val code: String,
    val title: String,
    val source: String,
    val sourceVersion: String,
    val addedEpochMs: Long
)

internal data class ConditionIntelligenceProfile(
    val activeConditions: List<ActiveClinicalCondition>,
    val contextTokens: Set<String>,
    val recommendationGuardrail: String
)

/**
 * Local clinical reference sidecar for the Data Vault.
 *
 * The searchable catalogue is reference knowledge, so it is kept separate from user observations.
 * Conditions the user actually assigns to themselves are mirrored into the main Data Vault as
 * CLINICAL HealthValues. That makes them visible to future Insights / Interpretation engines without
 * polluting physiological time-series with tens of thousands of reference rows.
 */
internal class ClinicalConditionVault(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "superhuman_clinical_reference_vault.db",
    null,
    1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE condition_catalog(
              id TEXT PRIMARY KEY NOT NULL,
              code TEXT NOT NULL,
              title TEXT NOT NULL,
              source TEXT NOT NULL,
              source_version TEXT NOT NULL,
              search_text TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX condition_catalog_title_idx ON condition_catalog(title COLLATE NOCASE)")
        db.execSQL("CREATE INDEX condition_catalog_code_idx ON condition_catalog(code COLLATE NOCASE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM condition_catalog", null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }

    fun search(query: String, limit: Int = 30): List<ClinicalCondition> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return emptyList()
        val like = "%${q.replace("%", "").replace("_", "")}%"
        return readableDatabase.rawQuery(
            """
            SELECT id, code, title, source, source_version
            FROM condition_catalog
            WHERE search_text LIKE ? OR lower(code) LIKE ?
            ORDER BY CASE WHEN lower(title) LIKE ? THEN 0 ELSE 1 END, length(title), title
            LIMIT ?
            """.trimIndent(),
            arrayOf(like, like, "${q}%", limit.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ClinicalCondition(
                            id = cursor.getString(0),
                            code = cursor.getString(1),
                            title = cursor.getString(2),
                            source = cursor.getString(3),
                            sourceVersion = cursor.getString(4)
                        )
                    )
                }
            }
        }
    }

    fun seedStarterCatalogue() {
        if (count() > 0) return
        val starter = listOf(
            "BA00|Essential hypertension", "5A11|Type 2 diabetes mellitus", "CA23|Asthma",
            "CA40|Chronic obstructive pulmonary disease", "DA63|Gastro-oesophageal reflux disease",
            "DD91|Irritable bowel syndrome", "FA20|Rheumatoid arthritis", "FA02|Osteoarthritis",
            "8A80|Migraine", "8A60|Epilepsy", "6A70|Depressive disorders", "6B00|Generalized anxiety disorder",
            "5C50|Disorders of lipoprotein metabolism or certain specified lipidaemias", "BD10|Heart failure",
            "BA80|Ischaemic heart diseases", "GB61|Chronic kidney disease", "DB94|Cirrhosis of liver",
            "4A40|Systemic lupus erythematosus", "4A44|Systemic sclerosis", "4A41|Sjögren syndrome",
            "FA21|Psoriatic arthritis", "EA90|Psoriasis", "1A40|Chronic viral hepatitis",
            "5A00|Hypothyroidism", "5A01|Hyperthyroidism", "5B5A|Coeliac disease",
            "DD71|Crohn disease", "DD70|Ulcerative colitis", "8A45|Multiple sclerosis",
            "8A00|Parkinson disease", "8A20|Alzheimer disease", "AB31|Ménière disease",
            "CA26|Bronchiectasis", "CB03|Pulmonary fibrosis", "BC81|Atrial fibrillation",
            "BA01|Hypertensive heart disease", "BA02|Hypertensive renal disease",
            "5A10|Type 1 diabetes mellitus", "5B81|Obesity", "5C64|Gout",
            "ME84|Chronic pain", "MG30|Fibromyalgia", "FA80|Spondyloarthritis",
            "6A02|Autism spectrum disorder", "6A05|Attention deficit hyperactivity disorder",
            "6C40|Disorders due to use of alcohol", "DA20|Peptic ulcer disease",
            "DA50|Gastritis", "LA86|Congenital heart disease", "BC43|Cardiomyopathy",
            "BB60|Peripheral arterial disease", "BD71|Venous insufficiency"
        ).mapIndexed { index, row ->
            val split = row.split('|', limit = 2)
            ClinicalCondition("starter-${index + 1}", split[0], split[1], "Project Superhuman starter index", "1")
        }
        replaceCatalogue(starter, clearFirst = false)
    }

    fun replaceCatalogue(rows: List<ClinicalCondition>, clearFirst: Boolean) {
        if (rows.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            if (clearFirst) writableDatabase.delete("condition_catalog", null, null)
            val values = ContentValues()
            rows.forEach { row ->
                values.clear()
                values.put("id", row.id)
                values.put("code", row.code)
                values.put("title", row.title)
                values.put("source", row.source)
                values.put("source_version", row.sourceVersion)
                values.put("search_text", "${row.title} ${row.code}".lowercase(Locale.ROOT))
                writableDatabase.insertWithOnConflict(
                    "condition_catalog",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }
}

internal object ClinicalConditionCatalogSync {
    private const val SOURCE_VERSION = "ICD-11 MMS 2026-01"
    private const val DOWNLOAD_URL = "https://icdcdn.who.int/static/releasefiles/2026-01/SimpleTabulation-ICD-11-MMS-en.zip"

    suspend fun ensureLargeLocalCatalogue(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val vault = ClinicalConditionVault(context)
            vault.seedStarterCatalogue()
            if (vault.count() >= 1_000) return@runCatching vault.count()
            val bytes = download(DOWNLOAD_URL)
            val rows = parseWhoSimpleTabulation(bytes)
            require(rows.size >= 1_000) { "WHO catalogue did not contain enough condition rows" }
            vault.replaceCatalogue(rows, clearFirst = true)
            vault.count()
        }
    }

    private fun download(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 25_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Project-Superhuman/11.2")
        }
        try {
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                input.copyTo(output)
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    /** WHO's SimpleTabulation download is a ZIP containing an XLSX workbook. */
    private fun parseWhoSimpleTabulation(outerZip: ByteArray): List<ClinicalCondition> {
        val workbook = extractFirstXlsx(outerZip)
        val files = unzipToMap(workbook)
        val shared = files["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheetEntry = files.keys.firstOrNull { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            ?: error("ICD workbook has no worksheet")
        return parseSheet(files.getValue(sheetEntry), shared)
    }

    private fun extractFirstXlsx(zipBytes: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".xlsx", ignoreCase = true)) {
                    val out = ByteArrayOutputStream()
                    zip.copyTo(out)
                    return out.toByteArray()
                }
            }
        }
        // Some WHO releases may serve the workbook bytes directly despite the .zip URL.
        if (zipBytes.size > 4 && zipBytes[0] == 'P'.code.toByte() && zipBytes[1] == 'K'.code.toByte()) {
            val files = unzipToMap(zipBytes)
            if (files.containsKey("xl/workbook.xml")) return zipBytes
        }
        error("No XLSX workbook found in ICD download")
    }

    private fun unzipToMap(bytes: ByteArray): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val data = ByteArrayOutputStream()
                    zip.copyTo(data)
                    out[entry.name] = data.toByteArray()
                }
            }
        }
        return out
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val parser = Xml.newPullParser().apply {
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }
        val strings = mutableListOf<String>()
        var current = StringBuilder()
        var insideSi = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "si") {
                    insideSi = true
                    current = StringBuilder()
                }
                XmlPullParser.TEXT -> if (insideSi) current.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "si") {
                    strings += current.toString()
                    insideSi = false
                }
            }
            event = parser.next()
        }
        return strings
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<ClinicalCondition> {
        val parser = Xml.newPullParser().apply { setInput(ByteArrayInputStream(bytes), "UTF-8") }
        val rows = mutableListOf<Map<Int, String>>()
        var currentRow = linkedMapOf<Int, String>()
        var cellRef = ""
        var cellType = ""
        var cellValue: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentRow = linkedMapOf()
                    "c" -> {
                        cellRef = parser.getAttributeValue(null, "r").orEmpty()
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        cellValue = null
                    }
                    "v", "t" -> cellValue = parser.nextText()
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "c" -> {
                        val index = columnIndex(cellRef)
                        val raw = cellValue.orEmpty()
                        val value = if (cellType == "s") raw.toIntOrNull()?.let(shared::getOrNull).orEmpty() else raw
                        currentRow[index] = value
                    }
                    "row" -> rows += currentRow
                }
            }
            event = parser.next()
        }
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().mapValues { it.value.trim().lowercase(Locale.ROOT) }
        val codeCol = header.entries.firstOrNull { it.value == "code" }?.key ?: 2
        val titleCol = header.entries.firstOrNull { it.value == "title" }?.key ?: 4
        val kindCol = header.entries.firstOrNull { it.value.contains("classkind") || it.value == "class kind" }?.key
        val foundationCol = header.entries.firstOrNull { it.value.contains("foundation") && it.value.contains("uri") }?.key

        return rows.drop(1).mapNotNull { row ->
            val code = row[codeCol].orEmpty().trim()
            val title = row[titleCol].orEmpty().trim().removePrefix("- ").trim()
            val kind = kindCol?.let(row::get).orEmpty().lowercase(Locale.ROOT)
            if (title.isBlank()) return@mapNotNull null
            if (kind.isNotBlank() && kind !in setOf("category", "modifiedcategory")) return@mapNotNull null
            if (code.isBlank()) return@mapNotNull null
            val uri = foundationCol?.let(row::get).orEmpty().trim()
            ClinicalCondition(
                id = uri.ifBlank { "icd11:$code" },
                code = code,
                title = title,
                source = "WHO ICD-11 MMS",
                sourceVersion = SOURCE_VERSION
            )
        }.distinctBy { it.id }
    }

    private fun columnIndex(ref: String): Int {
        var value = 0
        ref.takeWhile { it.isLetter() }.forEach { ch ->
            value = value * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return (value - 1).coerceAtLeast(0)
    }
}

internal object ClinicalConditionProfileStore {
    private const val METRIC_PREFIX = "clinical.condition."
    private const val SOURCE = "native-condition-profile-v1"

    suspend fun active(): List<ActiveClinicalCondition> = NativeDataHub.latestForDomain(HealthDomain.CLINICAL)
        .asSequence()
        .filter { it.metric.startsWith(METRIC_PREFIX) && it.source == SOURCE && it.value > 0.5 }
        .map { value ->
            ActiveClinicalCondition(
                metric = value.metric,
                id = value.metadata["conditionId"].orEmpty(),
                code = value.metadata["code"].orEmpty(),
                title = value.metadata["displayName"].orEmpty(),
                source = value.metadata["catalogSource"].orEmpty(),
                sourceVersion = value.metadata["catalogVersion"].orEmpty(),
                addedEpochMs = value.timestampEpochMs
            )
        }
        .sortedBy { it.title.lowercase(Locale.ROOT) }
        .toList()

    suspend fun add(condition: ClinicalCondition) {
        NativeDataHub.saveMetric(
            domain = HealthDomain.CLINICAL,
            metric = METRIC_PREFIX + safeConditionKey(condition.id, condition.code, condition.title),
            value = 1.0,
            unit = "present",
            source = SOURCE,
            metadata = mapOf(
                "conditionId" to condition.id,
                "code" to condition.code,
                "displayName" to condition.title,
                "catalogSource" to condition.source,
                "catalogVersion" to condition.sourceVersion,
                "profileType" to "chronic-condition",
                "sourceRecordId" to "condition:${safeConditionKey(condition.id, condition.code, condition.title)}"
            )
        )
    }

    suspend fun remove(condition: ActiveClinicalCondition) {
        NativeDataHub.latestForDomain(HealthDomain.CLINICAL)
            .firstOrNull { it.metric == condition.metric && it.source == SOURCE }
            ?.let { NativeDataHub.deleteValue(it) }
    }

    suspend fun intelligenceProfile(): ConditionIntelligenceProfile {
        val active = active()
        val tokens = active.flatMap { condition ->
            tokenize(condition.title) + tokenize(condition.code)
        }.toSet()
        return ConditionIntelligenceProfile(
            activeConditions = active,
            contextTokens = tokens,
            recommendationGuardrail = if (active.isEmpty()) {
                "No diagnosed chronic conditions are currently assigned."
            } else {
                "Condition-aware mode: recommendations should consider ${active.size} assigned condition${if (active.size == 1) "" else "s"}, avoid conflicting generic advice, and surface uncertainty when condition-specific evidence is unavailable."
            }
        )
    }

    private fun tokenize(value: String): List<String> = value.lowercase(Locale.ROOT)
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 3 }

    private fun safeConditionKey(id: String, code: String, title: String): String {
        val raw = id.ifBlank { code.ifBlank { title } }
        return raw.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(80)
            .ifBlank { title.hashCode().toUInt().toString(16) }
    }
}

private val ConditionNavy = Color(0xFF082D66)
private val ConditionBlue = Color(0xFF0D6CB4)
private val ConditionInk = Color(0xFF0B1F35)
private val ConditionMuted = Color(0xFF64748B)
private val ConditionBg = Color(0xFFF6F9FC)
private val ConditionGood = Color(0xFF168A78)

@Composable
internal fun NativeClinicalConditionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vault = remember { ClinicalConditionVault(context) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ClinicalCondition>>(emptyList()) }
    var active by remember { mutableStateOf<List<ActiveClinicalCondition>>(emptyList()) }
    var catalogueCount by remember { mutableStateOf(0) }
    var catalogueState by remember { mutableStateOf("Preparing local condition index…") }

    suspend fun refreshProfile() {
        active = ClinicalConditionProfileStore.active()
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            vault.seedStarterCatalogue()
            catalogueCount = vault.count()
        }
        refreshProfile()
        catalogueState = if (catalogueCount >= 1_000) {
            "$catalogueCount conditions available offline"
        } else {
            "Starter index ready · expanding from WHO ICD-11…"
        }
        if (catalogueCount < 1_000) {
            val sync = ClinicalConditionCatalogSync.ensureLargeLocalCatalogue(context)
            catalogueCount = withContext(Dispatchers.IO) { vault.count() }
            catalogueState = sync.fold(
                onSuccess = { "$it WHO ICD-11 conditions available offline" },
                onFailure = { "Starter index available offline · full WHO index will retry next time" }
            )
        }
    }

    LaunchedEffect(query, catalogueCount) {
        results = if (query.trim().length < 2) emptyList() else withContext(Dispatchers.IO) {
            vault.search(query)
        }
    }

    Column(
        Modifier.fillMaxSize().background(ConditionBg).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.superhumanTopButton(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("←", color = ConditionNavy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Clinical conditions", color = ConditionInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Your diagnoses & long-term context", color = ConditionMuted, fontSize = 10.sp)
            }
        }

        Column(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFFEAF4FB), Color.White)),
                RoundedCornerShape(26.dp)
            ).border(1.dp, ConditionBlue.copy(alpha = .12f), RoundedCornerShape(26.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("CONDITION VAULT", color = ConditionBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Text("Add conditions you have", color = ConditionNavy, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(
                "Search the local clinical index and assign diagnosed or established chronic conditions to your profile. They become structured Data Vault context for future insights and recommendations.",
                color = ConditionMuted, fontSize = 10.sp, lineHeight = 15.sp
            )
            Text(catalogueState, color = ConditionBlue, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search condition or ICD-11 code") }
            )
        }

        if (active.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("MY CONDITIONS", color = ConditionMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                active.forEach { condition ->
                    Row(
                        Modifier.fillMaxWidth().background(ConditionGood.copy(alpha = .06f), RoundedCornerShape(14.dp)).padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(condition.title, color = ConditionNavy, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            Text(condition.code.ifBlank { "Clinical condition" }, color = ConditionMuted, fontSize = 8.sp)
                        }
                        Text(
                            "Remove",
                            color = Color(0xFFA95C5C),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    ClinicalConditionProfileStore.remove(condition)
                                    refreshProfile()
                                }
                            }.padding(8.dp)
                        )
                    }
                }
            }
        }

        if (query.trim().length >= 2) {
            Column(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("SEARCH RESULTS", color = ConditionMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                if (results.isEmpty()) {
                    Text("No matching condition found.", color = ConditionMuted, fontSize = 10.sp)
                } else {
                    results.forEach { condition ->
                        val alreadyAdded = active.any { it.id == condition.id || (it.code.isNotBlank() && it.code == condition.code) }
                        Row(
                            Modifier.fillMaxWidth()
                                .background(ConditionBg, RoundedCornerShape(14.dp))
                                .clickable(enabled = !alreadyAdded) {
                                    scope.launch {
                                        ClinicalConditionProfileStore.add(condition)
                                        refreshProfile()
                                    }
                                }.padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(condition.title, color = ConditionNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("${condition.code} · ${condition.source}", color = ConditionMuted, fontSize = 7.sp)
                            }
                            Text(
                                if (alreadyAdded) "ADDED" else "+ ADD",
                                color = if (alreadyAdded) ConditionGood else ConditionBlue,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("CONDITION-AWARE INTELLIGENCE", color = ConditionBlue, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(
                if (active.isEmpty()) "No condition context assigned yet" else "${active.size} condition${if (active.size == 1) "" else "s"} now inform the Data Vault context",
                color = ConditionNavy,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "Condition records are stored as first-class Clinical profile data. Recommendation and insight code can query this context to avoid treating generic guidance as one-size-fits-all. The app does not infer a diagnosis from measurements or silently add a condition.",
                color = ConditionMuted,
                fontSize = 9.sp,
                lineHeight = 14.sp
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}
