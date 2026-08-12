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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ClinicalHubNavy = Color(0xFF082D66)
private val ClinicalHubBlue = Color(0xFF0D6CB4)
private val ClinicalHubInk = Color(0xFF0B1F35)
private val ClinicalHubMuted = Color(0xFF64748B)
private val ClinicalHubBg = Color(0xFFF6F9FC)
private val ClinicalHubGood = Color(0xFF168A78)
private val ClinicalHubLab = Color(0xFF7158D9)

/**
 * Clinical landing screen. Conditions and laboratory history are peers rather than making
 * lab import look like a header action. Search uses the human-friendly layer over ICD-11,
 * while the canonical ICD condition remains the record that is stored in the Data Vault.
 */
@Composable
internal fun NativeClinicalHubScreen(onBack: () -> Unit, openLabs: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vault = remember { ClinicalConditionVault(context) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ClinicalConditionSearchResult>>(emptyList()) }
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
            ClinicalConditionSearchEngine.search(vault, query)
        }
    }

    Column(
        Modifier.fillMaxSize().background(ClinicalHubBg).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.superhumanTopButton(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("←", color = ClinicalHubNavy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Clinical", color = ClinicalHubInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Conditions, labs & long-term context", color = ClinicalHubMuted, fontSize = 10.sp)
            }
        }

        LabResultsTile(openLabs)

        Column(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFFEAF4FB), Color.White)),
                RoundedCornerShape(26.dp)
            ).border(1.dp, ClinicalHubBlue.copy(alpha = .12f), RoundedCornerShape(26.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("CONDITION VAULT", color = ClinicalHubBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Text("Add conditions you have", color = ClinicalHubNavy, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(
                "Search naturally. Everyday names, abbreviations, common synonyms and ICD-11 codes all lead to the same structured clinical record.",
                color = ClinicalHubMuted, fontSize = 10.sp, lineHeight = 15.sp
            )
            Text(catalogueState, color = ClinicalHubBlue, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search a condition") },
                placeholder = { Text("e.g. colon cancer, high blood pressure, IBS") }
            )
            Text(
                "Common names are shown first. The formal ICD-11 name stays underneath when it differs.",
                color = ClinicalHubMuted,
                fontSize = 8.sp,
                lineHeight = 12.sp
            )
        }

        if (active.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("MY CONDITIONS", color = ClinicalHubMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                active.forEach { condition ->
                    val friendly = ClinicalConditionSearchEngine.friendlyTitle(condition.title)
                    Row(
                        Modifier.fillMaxWidth().background(ClinicalHubGood.copy(alpha = .06f), RoundedCornerShape(14.dp)).padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(friendly, color = ClinicalHubNavy, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            if (!friendly.equals(condition.title, ignoreCase = true)) {
                                Text(condition.title, color = ClinicalHubMuted, fontSize = 8.sp, lineHeight = 11.sp)
                            }
                            Text(condition.code.ifBlank { "Clinical condition" }, color = ClinicalHubMuted, fontSize = 8.sp)
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
                Text("BEST MATCHES", color = ClinicalHubMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                if (results.isEmpty()) {
                    Text("No matching condition found. Try a simpler everyday name or an ICD-11 code.", color = ClinicalHubMuted, fontSize = 10.sp)
                } else {
                    results.forEach { result ->
                        val condition = result.condition
                        val alreadyAdded = active.any { it.id == condition.id || (it.code.isNotBlank() && it.code == condition.code) }
                        Row(
                            Modifier.fillMaxWidth()
                                .background(ClinicalHubBg, RoundedCornerShape(14.dp))
                                .clickable(enabled = !alreadyAdded) {
                                    scope.launch {
                                        ClinicalConditionProfileStore.add(condition)
                                        refreshProfile()
                                    }
                                }.padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(result.displayTitle, color = ClinicalHubNavy, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                if (!result.displayTitle.equals(result.officialTitle, ignoreCase = true)) {
                                    Text(result.officialTitle, color = ClinicalHubMuted, fontSize = 8.sp, lineHeight = 11.sp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(condition.code, color = ClinicalHubMuted, fontSize = 7.sp)
                                    Text("·", color = ClinicalHubMuted, fontSize = 7.sp)
                                    Text(result.matchLabel, color = ClinicalHubBlue, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                if (alreadyAdded) "ADDED" else "+ ADD",
                                color = if (alreadyAdded) ClinicalHubGood else ClinicalHubBlue,
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
            Text("CONDITION-AWARE INTELLIGENCE", color = ClinicalHubBlue, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(
                if (active.isEmpty()) "No condition context assigned yet" else "${active.size} condition${if (active.size == 1) "" else "s"} now inform the Data Vault context",
                color = ClinicalHubNavy,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "Condition records are stored as first-class Clinical profile data. Recommendation and insight code can query this context to avoid treating generic guidance as one-size-fits-all. The app does not infer a diagnosis from measurements or silently add a condition.",
                color = ClinicalHubMuted,
                fontSize = 9.sp,
                lineHeight = 14.sp
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun LabResultsTile(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFF0EDFF), Color(0xFFF8F7FF), Color.White)
                ),
                RoundedCornerShape(24.dp)
            )
            .border(1.dp, ClinicalHubLab.copy(alpha = .16f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(50.dp).height(50.dp)
                .background(ClinicalHubLab.copy(alpha = .10f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("▤", color = ClinicalHubLab, fontSize = 25.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("LAB RESULTS", color = ClinicalHubLab, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(3.dp))
            Text("Blood tests & clinical markers", color = ClinicalHubNavy, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(2.dp))
            Text("Import results, review ranges and follow marker trends", color = ClinicalHubMuted, fontSize = 9.sp, lineHeight = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text("→", color = ClinicalHubLab, fontSize = 23.sp, fontWeight = FontWeight.Bold)
    }
}
