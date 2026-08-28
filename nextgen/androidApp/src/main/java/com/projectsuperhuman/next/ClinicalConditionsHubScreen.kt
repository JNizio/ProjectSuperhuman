package com.projectsuperhuman.next

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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ClinicalHubNavy get() = superhumanBrandText
private val ClinicalHubBlue get() = superhumanBlue
private val ClinicalHubInk get() = superhumanTextPrimary
private val ClinicalHubMuted get() = superhumanTextMuted
private val ClinicalHubBg get() = superhumanBackground
private val ClinicalHubGood get() = superhumanGreen
private val ClinicalHubAlert get() = if (SuperhumanAppearance.darkMode) Color(0xFFFF9A9A) else Color(0xFFB45C5C)
private val ClinicalHubLab get() = if (SuperhumanAppearance.darkMode) Color(0xFFA99BFF) else Color(0xFF7158D9)
private val ClinicalHubBorder get() = superhumanBorder
private val ClinicalHubSurface get() = superhumanSurface
private val ClinicalHubSurfaceElevated get() = superhumanSurfaceElevated
private val ClinicalHubSoft get() = superhumanSurfaceSoft

private val ClinicalOverviewColors: List<Color>
    get() = if (SuperhumanAppearance.darkMode) {
        listOf(Color(0xFF10283A), ClinicalHubSurfaceElevated, ClinicalHubSurface)
    } else {
        listOf(Color(0xFFEAF4FB), Color(0xFFF7FAFD), Color.White)
    }

private val ClinicalLabColors: List<Color>
    get() = if (SuperhumanAppearance.darkMode) {
        listOf(Color(0xFF211E39), ClinicalHubSurface)
    } else {
        listOf(Color(0xFFF3F1FF), Color.White)
    }

/**
 * Clinical landing screen.
 *
 * The dashboard stays intentionally lightweight: it reads current profile/lab summaries only.
 * The large ICD catalogue is prepared only when the user explicitly opens Add condition.
 */
@Composable
internal fun NativeClinicalHubScreen(onBack: () -> Unit, openLabs: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vault = remember { ClinicalConditionVault(context) }
    val clinicalData = remember { NativeDomainData.forDomain(HealthDomain.CLINICAL) }

    var active by remember { mutableStateOf<List<ActiveClinicalCondition>>(emptyList()) }
    var labValues by remember { mutableStateOf<List<HealthValue>>(emptyList()) }
    var showAddCondition by remember { mutableStateOf(false) }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ClinicalConditionSearchResult>>(emptyList()) }
    var catalogueCount by remember { mutableStateOf(0) }
    var catalogueState by remember { mutableStateOf("Medical terminology loads when you search") }

    suspend fun refreshClinicalSummary() {
        active = ClinicalConditionProfileStore.active()
        labValues = clinicalData.latestState().filter(::isClinicalLabMarker)
    }

    LaunchedEffect(Unit) {
        refreshClinicalSummary()
    }

    // The searchable medical catalogue is not touched while the user is only viewing Clinical.
    LaunchedEffect(showAddCondition) {
        if (!showAddCondition) return@LaunchedEffect

        catalogueState = "Preparing medical terminology…"
        withContext(Dispatchers.IO) {
            vault.seedStarterCatalogue()
            catalogueCount = vault.count()
        }

        catalogueState = if (catalogueCount >= 1_000) {
            "Medical terminology available offline"
        } else {
            "Starter terminology ready · expanding the offline index…"
        }

        if (catalogueCount < 1_000) {
            val sync = ClinicalConditionCatalogSync.ensureLargeLocalCatalogue(context)
            catalogueCount = withContext(Dispatchers.IO) { vault.count() }
            catalogueState = sync.fold(
                onSuccess = { "WHO ICD-11 terminology available offline" },
                onFailure = { "Starter terminology available offline · full index will retry later" }
            )
        }
    }

    LaunchedEffect(showAddCondition, query, catalogueCount) {
        results = if (!showAddCondition || query.trim().length < 2) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                ClinicalConditionHierarchy.search(vault, query)
            }
        }
    }

    if (showAddCondition) {
        ClinicalConditionAddFlow(
            query = query,
            onQueryChange = { query = it },
            results = results,
            active = active,
            catalogueState = catalogueState,
            onBack = {
                showAddCondition = false
                query = ""
                results = emptyList()
            },
            onAdd = { result, generalResult ->
                scope.launch {
                    val condition = result.condition
                    if (!ClinicalConditionHierarchy.isGeneral(condition)) {
                        val generalId = generalResult?.condition?.id
                        if (generalId != null) {
                            active.firstOrNull { it.id == generalId }
                                ?.let { ClinicalConditionProfileStore.remove(it) }
                        }
                    }
                    ClinicalConditionProfileStore.add(condition)
                    refreshClinicalSummary()
                }
            }
        )
        return
    }

    val flaggedCount = labValues.count { it.metadata["status"] in setOf("HIGH", "LOW") }
    val latestLabEpoch = labValues.maxOfOrNull { it.timestampEpochMs }
    val latestLabDate = formatClinicalDate(latestLabEpoch)

    Column(
        Modifier.fillMaxSize()
            .background(ClinicalHubBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ClinicalHubHeader(onBack = onBack)

        ClinicalOverviewCard(
            conditionCount = active.size,
            labMarkerCount = labValues.size,
            flaggedCount = flaggedCount,
            lastResults = latestLabDate,
            onRecentClick = if (labValues.isNotEmpty()) openLabs else null
        )

        ClinicalQuickActions(
            onAddCondition = { showAddCondition = true },
            onImportLabs = openLabs
        )

        ConditionsSummaryCard(
            active = active,
            onAddCondition = { showAddCondition = true },
            onRemove = { condition ->
                scope.launch {
                    ClinicalConditionProfileStore.remove(condition)
                    refreshClinicalSummary()
                }
            }
        )

        LabSummaryCard(
            labValues = labValues,
            flaggedCount = flaggedCount,
            lastResults = latestLabDate,
            onClick = openLabs
        )

        ClinicalContextNote(activeCount = active.size)

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ClinicalHubHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.superhumanTopButton(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = ClinicalHubNavy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "Clinical",
                color = ClinicalHubInk,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "Your medical history & clinical data",
                color = ClinicalHubMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ClinicalOverviewCard(
    conditionCount: Int,
    labMarkerCount: Int,
    flaggedCount: Int,
    lastResults: String,
    onRecentClick: (() -> Unit)?
) {
    Column(
        Modifier.fillMaxWidth()
            .background(
                Brush.linearGradient(ClinicalOverviewColors),
                RoundedCornerShape(24.dp)
            )
            .border(1.dp, ClinicalHubBlue.copy(alpha = .12f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "CLINICAL OVERVIEW",
            color = ClinicalHubBlue,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ClinicalMetricBlock(
                value = conditionCount.toString(),
                label = "Conditions",
                modifier = Modifier.weight(1f)
            )
            ClinicalMetricBlock(
                value = labMarkerCount.toString(),
                label = "Lab markers",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ClinicalMetricBlock(
                value = flaggedCount.toString(),
                label = "Out of range",
                accent = if (flaggedCount > 0) ClinicalHubAlert else ClinicalHubNavy,
                modifier = Modifier.weight(1f)
            )
            ClinicalMetricBlock(
                value = lastResults,
                label = "Last results",
                modifier = Modifier.weight(1f)
            )
        }

        val currentPicture = when {
            flaggedCount > 0 ->
                "$flaggedCount latest lab marker${if (flaggedCount == 1) "" else "s"} outside recorded reference ranges"
            labMarkerCount > 0 ->
                "Latest lab markers are ready to review"
            conditionCount > 0 ->
                "$conditionCount condition${if (conditionCount == 1) "" else "s"} recorded"
            else ->
                "Add a condition or import labs to build your clinical picture"
        }

        Row(
            Modifier.fillMaxWidth()
                .background(
                    if (SuperhumanAppearance.darkMode) ClinicalHubSurfaceElevated else Color.White.copy(alpha = .82f),
                    RoundedCornerShape(14.dp)
                )
                .then(
                    if (onRecentClick != null) Modifier.superhumanClickable(onClick = onRecentClick)
                    else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "CURRENT PICTURE",
                    color = ClinicalHubMuted,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .8.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    currentPicture,
                    color = ClinicalHubNavy,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (onRecentClick != null) {
                Spacer(Modifier.width(8.dp))
                Text("→", color = ClinicalHubBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ClinicalMetricBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = ClinicalHubNavy
) {
    Column(
        modifier.background(
            if (SuperhumanAppearance.darkMode) ClinicalHubSurfaceElevated else Color.White.copy(alpha = .76f),
            RoundedCornerShape(15.dp)
        ).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(value, color = accent, fontSize = 19.sp, fontWeight = FontWeight.Black)
        Text(label, color = ClinicalHubMuted, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ClinicalQuickActions(
    onAddCondition: () -> Unit,
    onImportLabs: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            "QUICK ACTIONS",
            color = ClinicalHubMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ClinicalQuickAction(
                label = "+ Condition",
                modifier = Modifier.weight(1f),
                onClick = onAddCondition
            )
            ClinicalQuickAction(
                label = "Import labs",
                modifier = Modifier.weight(1f),
                accent = ClinicalHubLab,
                onClick = onImportLabs
            )
        }
    }
}

@Composable
private fun ClinicalQuickAction(
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = ClinicalHubBlue,
    onClick: () -> Unit
) {
    Box(
        modifier.background(ClinicalHubSurface, RoundedCornerShape(15.dp))
            .border(1.dp, accent.copy(alpha = .15f), RoundedCornerShape(15.dp))
            .superhumanClickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ConditionsSummaryCard(
    active: List<ActiveClinicalCondition>,
    onAddCondition: () -> Unit,
    onRemove: (ActiveClinicalCondition) -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .background(ClinicalHubSurface, RoundedCornerShape(22.dp))
            .border(1.dp, ClinicalHubBorder, RoundedCornerShape(22.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "CONDITIONS",
                    color = ClinicalHubBlue,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    if (active.isEmpty()) "No conditions recorded" else "${active.size} recorded",
                    color = ClinicalHubNavy,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }
            ClinicalStatusChip(
                text = if (active.isEmpty()) "EMPTY" else "${active.size}",
                accent = if (active.isEmpty()) ClinicalHubMuted else ClinicalHubBlue
            )
        }

        if (active.isEmpty()) {
            Text(
                "Add conditions you want Project Superhuman to consider as clinical context.",
                color = ClinicalHubMuted,
                fontSize = 9.sp,
                lineHeight = 13.sp
            )
        } else {
            active.forEach { condition ->
                RecordedConditionRow(
                    condition = condition,
                    onRemove = { onRemove(condition) }
                )
            }
        }

        Box(
            Modifier.fillMaxWidth()
                .background(ClinicalHubBlue.copy(alpha = .07f), RoundedCornerShape(14.dp))
                .superhumanClickable(onClick = onAddCondition)
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("+ Add condition", color = ClinicalHubBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun RecordedConditionRow(
    condition: ActiveClinicalCondition,
    onRemove: () -> Unit
) {
    val general = ClinicalConditionHierarchy.isGeneral(condition)
    val friendly = if (general) {
        condition.title
    } else {
        ClinicalConditionSearchEngine.friendlyTitle(condition.title)
    }

    Row(
        Modifier.fillMaxWidth()
            .background(ClinicalHubSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                friendly,
                color = ClinicalHubNavy,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
            val secondary = when {
                general -> "General condition · exact type not specified"
                condition.code.isNotBlank() -> condition.code
                else -> "Clinical condition"
            }
            Text(
                secondary,
                color = if (general) ClinicalHubBlue else ClinicalHubMuted,
                fontSize = 8.sp,
                lineHeight = 11.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            ClinicalStatusChip(
                text = if (general) "GENERAL" else "RECORDED",
                accent = if (general) ClinicalHubLab else ClinicalHubBlue
            )
            Text(
                "Remove",
                color = ClinicalHubAlert,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.superhumanClickable(onClick = onRemove)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun LabSummaryCard(
    labValues: List<HealthValue>,
    flaggedCount: Int,
    lastResults: String,
    onClick: () -> Unit
) {
    val rangedCount = labValues.count { it.metadata["status"] in setOf("NORMAL", "HIGH", "LOW") }

    Row(
        Modifier.fillMaxWidth()
            .background(
                Brush.linearGradient(ClinicalLabColors),
                RoundedCornerShape(22.dp)
            )
            .border(1.dp, ClinicalHubLab.copy(alpha = .14f), RoundedCornerShape(22.dp))
            .superhumanClickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(40.dp).height(40.dp)
                .background(ClinicalHubLab.copy(alpha = .09f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("▤", color = ClinicalHubLab, fontSize = 21.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "BLOOD TESTS",
                    color = ClinicalHubLab,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                if (flaggedCount > 0) {
                    Spacer(Modifier.width(7.dp))
                    ClinicalStatusChip("OUT OF RANGE", ClinicalHubAlert)
                }
            }
            Spacer(Modifier.height(2.dp))
            if (labValues.isEmpty()) {
                Text(
                    "No blood tests imported yet",
                    color = ClinicalHubNavy,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Import results to review ranges and trends.",
                    color = ClinicalHubMuted,
                    fontSize = 9.sp
                )
            } else {
                Text(
                    "${labValues.size} marker${if (labValues.size == 1) "" else "s"} tracked",
                    color = ClinicalHubNavy,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                val detail = buildString {
                    when {
                        flaggedCount > 0 -> append("$flaggedCount outside recorded ranges")
                        rangedCount > 0 -> append("$rangedCount with recorded range status")
                        else -> append("Saved clinical markers")
                    }
                    if (lastResults != "—") append(" · Last $lastResults")
                }
                Text(detail, color = ClinicalHubMuted, fontSize = 9.sp, lineHeight = 13.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("→", color = ClinicalHubLab, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ClinicalContextNote(activeCount: Int) {
    Column(
        Modifier.fillMaxWidth()
            .background(ClinicalHubSurface, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            "CLINICAL CONTEXT",
            color = ClinicalHubMuted,
            fontSize = 7.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = .9.sp
        )
        Text(
            if (activeCount > 0) {
                "Recorded conditions can provide context to relevant insights."
            } else {
                "Add only conditions you want represented in your clinical profile."
            },
            color = ClinicalHubNavy,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Project Superhuman does not turn symptoms or measurements into diagnoses automatically.",
            color = ClinicalHubMuted,
            fontSize = 8.sp,
            lineHeight = 12.sp
        )
    }
}

@Composable
private fun ClinicalConditionAddFlow(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<ClinicalConditionSearchResult>,
    active: List<ActiveClinicalCondition>,
    catalogueState: String,
    onBack: () -> Unit,
    onAdd: (ClinicalConditionSearchResult, ClinicalConditionSearchResult?) -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .background(ClinicalHubBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.superhumanTopButton(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = ClinicalHubNavy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Add condition",
                    color = ClinicalHubInk,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Search by name, abbreviation or ICD-11 code",
                    color = ClinicalHubMuted,
                    fontSize = 10.sp
                )
            }
        }

        Column(
            Modifier.fillMaxWidth()
                .background(ClinicalHubSurface, RoundedCornerShape(22.dp))
                .border(1.dp, ClinicalHubBorder, RoundedCornerShape(22.dp))
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                "FIND A CONDITION",
                color = ClinicalHubBlue,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Text("⌕", color = ClinicalHubMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                },
                placeholder = { Text("Search conditions…") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ClinicalHubInk,
                    unfocusedTextColor = ClinicalHubInk,
                    focusedContainerColor = ClinicalHubSurface,
                    unfocusedContainerColor = ClinicalHubSurface,
                    focusedBorderColor = ClinicalHubBlue,
                    unfocusedBorderColor = ClinicalHubBorder,
                    cursorColor = ClinicalHubBlue,
                    focusedPlaceholderColor = ClinicalHubMuted,
                    unfocusedPlaceholderColor = ClinicalHubMuted
                )
            )
            Text(
                catalogueState,
                color = ClinicalHubMuted,
                fontSize = 8.sp,
                lineHeight = 11.sp
            )
        }

        if (query.trim().length < 2) {
            Column(
                Modifier.fillMaxWidth()
                    .background(ClinicalHubBlue.copy(alpha = .05f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    "Search naturally",
                    color = ClinicalHubNavy,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Everyday names, abbreviations and ICD-11 codes are supported. Choose a general condition when the exact subtype is unknown.",
                    color = ClinicalHubMuted,
                    fontSize = 9.sp,
                    lineHeight = 13.sp
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth()
                    .background(ClinicalHubSurface, RoundedCornerShape(22.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "BEST MATCHES",
                    color = ClinicalHubMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                if (results.isEmpty()) {
                    Text(
                        "No matching condition found. Try a simpler name or an ICD-11 code.",
                        color = ClinicalHubMuted,
                        fontSize = 10.sp
                    )
                } else {
                    val generalResult = results.firstOrNull {
                        ClinicalConditionHierarchy.isGeneral(it.condition)
                    }
                    val specificResults = results.filterNot {
                        ClinicalConditionHierarchy.isGeneral(it.condition)
                    }

                    if (generalResult != null) {
                        Text(
                            "GENERAL CONDITION",
                            color = ClinicalHubBlue,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = .8.sp
                        )
                        val alreadyAdded = active.any { it.id == generalResult.condition.id }
                        ConditionSearchResultRow(
                            result = generalResult,
                            alreadyAdded = alreadyAdded,
                            general = true,
                            onAdd = { onAdd(generalResult, generalResult) }
                        )
                    }

                    if (specificResults.isNotEmpty()) {
                        if (generalResult != null) {
                            Text(
                                "MORE SPECIFIC TYPES",
                                color = ClinicalHubMuted,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = .8.sp
                            )
                        }
                        specificResults.forEach { result ->
                            val condition = result.condition
                            val alreadyAdded = active.any {
                                it.id == condition.id ||
                                    (it.code.isNotBlank() && it.code == condition.code)
                            }
                            ConditionSearchResultRow(
                                result = result,
                                alreadyAdded = alreadyAdded,
                                general = false,
                                onAdd = { onAdd(result, generalResult) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ConditionSearchResultRow(
    result: ClinicalConditionSearchResult,
    alreadyAdded: Boolean,
    general: Boolean,
    onAdd: () -> Unit
) {
    val condition = result.condition
    Row(
        Modifier.fillMaxWidth()
            .background(
                if (general) ClinicalHubBlue.copy(alpha = .055f) else ClinicalHubSoft,
                RoundedCornerShape(14.dp)
            )
            .then(
                if (general) {
                    Modifier.border(
                        1.dp,
                        ClinicalHubBlue.copy(alpha = .16f),
                        RoundedCornerShape(14.dp)
                    )
                } else {
                    Modifier
                }
            )
            .superhumanClickable(enabled = !alreadyAdded, onClick = onAdd)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                result.displayTitle,
                color = ClinicalHubNavy,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (general) {
                Text(
                    "Exact subtype not specified · family-level context",
                    color = ClinicalHubMuted,
                    fontSize = 8.sp,
                    lineHeight = 11.sp
                )
            } else {
                if (!result.displayTitle.equals(result.officialTitle, ignoreCase = true)) {
                    Text(
                        result.officialTitle,
                        color = ClinicalHubMuted,
                        fontSize = 8.sp,
                        lineHeight = 11.sp
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(condition.code, color = ClinicalHubMuted, fontSize = 7.sp)
                    Text("·", color = ClinicalHubMuted, fontSize = 7.sp)
                    Text(
                        result.matchLabel,
                        color = ClinicalHubBlue,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        ClinicalStatusChip(
            text = if (alreadyAdded) "ADDED" else "+ ADD",
            accent = if (alreadyAdded) ClinicalHubGood else ClinicalHubBlue
        )
    }
}

@Composable
private fun ClinicalStatusChip(
    text: String,
    accent: Color
) {
    Box(
        Modifier.background(accent.copy(alpha = .09f), RoundedCornerShape(99.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = accent,
            fontSize = 7.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = .35.sp
        )
    }
}

private fun isClinicalLabMarker(value: HealthValue): Boolean {
    if (!value.metric.startsWith("clinical.")) return false
    if (value.metric.startsWith("clinical.condition.")) return false
    return value.metadata["displayName"].orEmpty().isNotBlank() ||
        value.metadata.containsKey("status") ||
        value.metadata.containsKey("rangeLow") ||
        value.metadata.containsKey("rangeHigh")
}

private fun formatClinicalDate(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0L) return "—"
    return runCatching {
        SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochMs))
    }.getOrDefault("—")
}
