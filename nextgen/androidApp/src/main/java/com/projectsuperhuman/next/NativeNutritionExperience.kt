package com.projectsuperhuman.next

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val NxBlue = Color(0xFF0D6CB4)
private val NxInk = Color(0xFF102A43)
private val NxMuted = Color(0xFF6B7C8F)
private val NxGreen = Color(0xFF168A78)
private val NxAmber = Color(0xFFD98B2B)
private val NxPurple = Color(0xFF6547C9)
private val NxBg = Color(0xFFF6F9FC)
private val NxBorder = Color(0xFFE2EAF2)
private val NxSoftBlue = Color(0xFFEAF3FA)
private val NxSoftGreen = Color(0xFFECF8F1)
private val NxSoftAmber = Color(0xFFFFF6E7)

private enum class NxView { DIARY, NUTRIENTS, INSIGHTS }

private data class NxDiaryEntry(
    val id: String,
    val foodId: String,
    val timestampEpochMs: Long,
    val name: String,
    val grams: Double,
    val meal: String,
    val source: String,
    val barcode: String?,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fibre: Double,
    val sugar: Double,
    val micronutrientCount: Int
)

private data class NxMicro(
    val id: String,
    val label: String,
    val value: Double,
    val unit: String,
    val knownEntries: Int
)

private data class NxDay(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fibre: Double = 0.0,
    val sugar: Double = 0.0,
    val entries: List<NxDiaryEntry> = emptyList(),
    val micros: List<NxMicro> = emptyList(),
    val records: List<HealthValue> = emptyList()
)

private data class NxGoals(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fibre: Double? = null
)

private data class NxReference(
    val id: String,
    val label: String,
    val value: Double,
    val unit: String
)

/*
 * EU Regulation 1169/2011 Annex XIII adult nutrient reference values.
 * These are labelling reference values, deliberately not presented as personalised medical targets.
 */
private val nxEuReferences = listOf(
    NxReference("vitamin_a", "Vitamin A", 800.0, "µg"),
    NxReference("vitamin_d", "Vitamin D", 5.0, "µg"),
    NxReference("vitamin_e", "Vitamin E", 12.0, "mg"),
    NxReference("vitamin_k", "Vitamin K", 75.0, "µg"),
    NxReference("vitamin_c", "Vitamin C", 80.0, "mg"),
    NxReference("vitamin_b1", "Vitamin B1", 1.1, "mg"),
    NxReference("vitamin_b2", "Vitamin B2", 1.4, "mg"),
    NxReference("niacin", "Niacin (B3)", 16.0, "mg"),
    NxReference("vitamin_b6", "Vitamin B6", 1.4, "mg"),
    NxReference("folate", "Folate (B9)", 200.0, "µg"),
    NxReference("vitamin_b12", "Vitamin B12", 2.5, "µg"),
    NxReference("biotin", "Biotin (B7)", 50.0, "µg"),
    NxReference("pantothenic_acid", "Pantothenic acid", 6.0, "mg"),
    NxReference("potassium", "Potassium", 2000.0, "mg"),
    NxReference("chloride", "Chloride", 800.0, "mg"),
    NxReference("calcium", "Calcium", 800.0, "mg"),
    NxReference("phosphorus", "Phosphorus", 700.0, "mg"),
    NxReference("magnesium", "Magnesium", 375.0, "mg"),
    NxReference("iron", "Iron", 14.0, "mg"),
    NxReference("zinc", "Zinc", 10.0, "mg"),
    NxReference("copper", "Copper", 1.0, "mg"),
    NxReference("manganese", "Manganese", 2.0, "mg"),
    NxReference("selenium", "Selenium", 55.0, "µg"),
    NxReference("iodine", "Iodine", 150.0, "µg")
)

private val nxReferenceById = nxEuReferences.associateBy { it.id }
private val nxHighlightOrder = listOf("magnesium", "potassium", "calcium", "vitamin_d", "iron", "folate", "vitamin_b12", "zinc")
private val nxMeals = listOf("Breakfast", "Lunch", "Dinner", "Snack")

@Composable
internal fun NativeNutritionExperiencePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageBarcodeScanner = remember { BarcodeScanning.getClient() }

    var view by remember { mutableStateOf(NxView.DIARY) }
    var day by remember { mutableStateOf(NxDay()) }
    var goals by remember { mutableStateOf(NxGoals()) }
    var query by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<NativeFood>>(emptyList()) }
    var selectedFood by remember { mutableStateOf<NativeFood?>(null) }
    var portionText by remember { mutableStateOf("100") }
    var meal by remember { mutableStateOf("Breakfast") }
    var searching by remember { mutableStateOf(false) }
    var lookingUp by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Nutrition is fully native") }

    suspend fun refresh() {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        day = nxBuildDay(NativeDataHub.domainBetween(HealthDomain.NUTRITION, from, to))
        goals = nxLoadGoals()
    }

    suspend fun lookupBarcode(raw: String) {
        val digits = raw.filter(Char::isDigit)
        if (digits.length !in setOf(8, 12, 13, 14)) {
            status = "That doesn't look like a supported food barcode"
            return
        }
        barcode = digits
        lookingUp = true
        status = "Looking up product…"
        val product = NativeFoodCatalog.lookupBarcode(digits)
        lookingUp = false
        if (product == null) {
            status = "Product not found, or Open Food Facts is unavailable"
        } else {
            selectedFood = product
            results = listOf(product)
            portionText = product.servingSize.nxFirstNumber()?.let(::nxEditable) ?: "100"
            status = "Found ${product.name}"
        }
    }

    val liveScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf { it.isNotBlank() }?.let { raw -> scope.launch { lookupBarcode(raw) } }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = "Scanning image for a barcode…"
        runCatching { InputImage.fromFilePath(context, uri) }
            .onSuccess { image ->
                imageBarcodeScanner.process(image)
                    .addOnSuccessListener { codes ->
                        val raw = codes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                        if (raw == null) status = "No barcode found in that image"
                        else scope.launch { lookupBarcode(raw) }
                    }
                    .addOnFailureListener { status = "Couldn't read a barcode from that image" }
            }
            .onFailure { status = "Couldn't open that image" }
    }

    DisposableEffect(Unit) { onDispose { imageBarcodeScanner.close() } }

    LaunchedEffect(Unit) {
        NativeFoodCatalog.all(context)
        refresh()
    }

    Column(
        Modifier.fillMaxSize().background(NxBg).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NxHeader(onBack)
        NxTabs(view) { view = it }

        when (view) {
            NxView.DIARY -> {
                NxMacroHero(day, goals)
                NxQuickLogCard(
                    query = query,
                    onQueryChange = { query = it },
                    searching = searching,
                    onSearch = {
                        scope.launch {
                            if (query.trim().length < 2) {
                                status = "Type at least two characters to search"
                                return@launch
                            }
                            searching = true
                            status = "Searching foods and products…"
                            val found = NativeFoodCatalog.search(context, query)
                            results = found.foods
                            selectedFood = null
                            searching = false
                            status = when {
                                results.isEmpty() && !found.remoteAvailable -> "No local matches · Open Food Facts unavailable"
                                results.isEmpty() -> "No matching foods found"
                                !found.remoteAvailable -> "${results.size} local matches · Open Food Facts unavailable"
                                found.remoteCount > 0 -> "${results.size} matches · local + Open Food Facts"
                                else -> "${results.size} local matches"
                            }
                        }
                    },
                    onLiveScan = {
                        liveScanLauncher.launch(
                            ScanOptions().setPrompt("Scan a food barcode").setBeepEnabled(false).setOrientationLocked(true)
                        )
                    },
                    onImageScan = { imagePicker.launch("image/*") }
                )

                if (results.isNotEmpty()) {
                    NxSearchResults(results, selectedFood?.id) { food ->
                        selectedFood = food
                        portionText = food.servingSize.nxFirstNumber()?.let(::nxEditable) ?: "100"
                    }
                }

                selectedFood?.let { food ->
                    NxSelectedFood(
                        food = food,
                        portionText = portionText,
                        onPortionChange = { portionText = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                        meal = meal,
                        onMealChange = { meal = it },
                        onAdd = {
                            scope.launch {
                                val amount = portionText.toDoubleOrNull()?.coerceIn(1.0, 5000.0) ?: 100.0
                                NativeDataHub.saveFood(food, amount, meal)
                                selectedFood = null
                                status = "Added ${food.name} to $meal"
                                refresh()
                            }
                        }
                    )
                }

                NxDiary(day) { entry ->
                    scope.launch {
                        val matchingRows = day.records.filter { row ->
                            val storedId = row.metadata["diaryEntryId"]
                            if (entry.id.startsWith("legacy:")) {
                                storedId == null && row.timestampEpochMs == entry.timestampEpochMs && row.metadata["foodId"] == entry.foodId
                            } else storedId == entry.id
                        }
                        NativeDataHub.deleteValues(matchingRows)
                        status = "Removed ${entry.name}"
                        refresh()
                    }
                }

                NxManualBarcodeCard(
                    barcode = barcode,
                    onChange = { barcode = it.filter(Char::isDigit).take(14) },
                    lookingUp = lookingUp,
                    onLookup = { scope.launch { lookupBarcode(barcode) } }
                )
            }

            NxView.NUTRIENTS -> NxNutrients(day)

            NxView.INSIGHTS -> NxInsights(
                day = day,
                goals = goals,
                onGoalsSaved = { saved ->
                    scope.launch {
                        nxSaveGoals(saved)
                        status = "Nutrition targets updated"
                        refresh()
                    }
                }
            )
        }

        Text(status, color = NxMuted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 2.dp))
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun NxHeader(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.superhumanTopButton(onClick = onBack), contentAlignment = Alignment.Center) {
            Text("←", color = NxBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Nutrition", color = NxInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("Diary · nutrients · patterns", color = NxMuted, fontSize = 10.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("TODAY", color = NxBlue, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM")), color = NxInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NxTabs(view: NxView, onChange: (NxView) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, NxBorder, RoundedCornerShape(18.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        NxTab("Diary", view == NxView.DIARY, Modifier.weight(1f)) { onChange(NxView.DIARY) }
        NxTab("Nutrients", view == NxView.NUTRIENTS, Modifier.weight(1f)) { onChange(NxView.NUTRIENTS) }
        NxTab("Insights", view == NxView.INSIGHTS, Modifier.weight(1f)) { onChange(NxView.INSIGHTS) }
    }
}

@Composable
private fun NxTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(if (selected) NxBlue else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else NxMuted, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun NxMacroHero(day: NxDay, goals: NxGoals) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(25.dp))
            .border(1.dp, NxBorder, RoundedCornerShape(25.dp)).padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("TODAY'S INTAKE", color = NxMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(day.kcal.roundToInt().toString(), color = NxInk, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(5.dp))
                    Text("kcal", color = NxMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            if (goals.kcal != null) {
                Text("${goals.kcal.roundToInt()} target", color = NxBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("Set targets in Insights", color = NxMuted, fontSize = 9.sp)
            }
        }

        goals.kcal?.let { NxProgress(day.kcal, it, NxBlue) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            NxMacro("Protein", day.protein, goals.protein, NxGreen, Modifier.weight(1f))
            NxMacro("Carbs", day.carbs, goals.carbs, NxBlue, Modifier.weight(1f))
            NxMacro("Fat", day.fat, goals.fat, NxAmber, Modifier.weight(1f))
        }
        NxMacro("Fibre", day.fibre, goals.fibre, NxPurple, Modifier.fillMaxWidth())
    }
}

@Composable
private fun NxMacro(label: String, value: Double, target: Double?, accent: Color, modifier: Modifier) {
    Column(modifier.background(NxBg, RoundedCornerShape(15.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label.uppercase(), color = NxMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
        Text("${nxOneDecimal(value)} g", color = NxInk, fontSize = 15.sp, fontWeight = FontWeight.Black)
        if (target != null) {
            NxProgress(value, target, accent)
            Text("of ${nxOneDecimal(target)} g", color = NxMuted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun NxProgress(value: Double, target: Double, accent: Color) {
    val fraction = if (target > 0.0) (value / target).toFloat().coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(6.dp).background(NxBorder, RoundedCornerShape(99.dp))) {
        if (fraction > 0f) {
            Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(accent, RoundedCornerShape(99.dp)))
        }
    }
}

@Composable
private fun NxQuickLogCard(
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    onSearch: () -> Unit,
    onLiveScan: () -> Unit,
    onImageScan: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, NxBorder, RoundedCornerShape(22.dp)).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Quick log", color = NxInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("Generic foods + Open Food Facts", color = NxMuted, fontSize = 9.sp)
            }
            Text("FAST", color = NxGreen, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search food or product") }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NxAction(if (searching) "Searching…" else "Search", NxGreen, Modifier.weight(1.35f), !searching, onSearch)
            NxAction("Scan", NxBlue, Modifier.weight(1f), true, onLiveScan)
            NxAction("Photo", NxPurple, Modifier.weight(1f), true, onImageScan)
        }
    }
}

@Composable
private fun NxAction(label: String, accent: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier.background(accent.copy(alpha = if (enabled) 1f else .45f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun NxSearchResults(foods: List<NativeFood>, selectedId: String?, onSelect: (NativeFood) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, NxBorder, RoundedCornerShape(22.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text("SEARCH RESULTS", color = NxMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        foods.take(14).forEach { food ->
            Row(
                Modifier.fillMaxWidth().background(if (food.id == selectedId) NxSoftGreen else NxBg, RoundedCornerShape(15.dp))
                    .clickable { onSelect(food) }.padding(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(food.name, color = NxInk, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    val source = buildString {
                        if (food.brand.isNotBlank()) append(food.brand).append(" · ")
                        append(food.source)
                    }
                    Text(source, color = NxMuted, fontSize = 8.sp)
                    Text(
                        "${food.kcal.roundToInt()} kcal · ${nxOneDecimal(food.protein)}P · ${nxOneDecimal(food.carbs)}C · ${nxOneDecimal(food.fat)}F / ${food.unit}",
                        color = NxMuted, fontSize = 9.sp
                    )
                }
                Text("+", color = NxGreen, fontSize = 21.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun NxSelectedFood(
    food: NativeFood,
    portionText: String,
    onPortionChange: (String) -> Unit,
    meal: String,
    onMealChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    val grams = portionText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val factor = grams / 100.0
    Column(
        Modifier.fillMaxWidth().background(NxSoftGreen, RoundedCornerShape(22.dp))
            .border(1.dp, NxGreen.copy(alpha = .22f), RoundedCornerShape(22.dp)).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(food.name, color = NxInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(food.brand.ifBlank { food.source }, color = NxMuted, fontSize = 9.sp)
            }
            if (food.micronutrients.isNotEmpty()) {
                Text("${food.micronutrients.size} micros", color = NxGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }

        OutlinedTextField(
            value = portionText,
            onValueChange = onPortionChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Portion (g / ml equivalent)") }
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NxMiniValue("KCAL", (food.kcal * factor).roundToInt().toString(), Modifier.weight(1f))
            NxMiniValue("PROTEIN", "${nxOneDecimal(food.protein * factor)}g", Modifier.weight(1f))
            NxMiniValue("CARBS", "${nxOneDecimal(food.carbs * factor)}g", Modifier.weight(1f))
            NxMiniValue("FAT", "${nxOneDecimal(food.fat * factor)}g", Modifier.weight(1f))
        }

        Text("ADD TO", color = NxMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            nxMeals.forEach { item ->
                val active = meal == item
                Box(
                    Modifier.background(if (active) NxBlue else Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, if (active) NxBlue else NxBorder, RoundedCornerShape(12.dp))
                        .clickable { onMealChange(item) }.padding(horizontal = 13.dp, vertical = 9.dp)
                ) {
                    Text(item, color = if (active) Color.White else NxMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        NxAction("Add to $meal", NxGreen, Modifier.fillMaxWidth(), true, onAdd)
    }
}

@Composable
private fun NxMiniValue(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp)) {
        Text(label, color = NxMuted, fontSize = 6.sp, fontWeight = FontWeight.Black)
        Text(value, color = NxInk, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun NxDiary(day: NxDay, onRemove: (NxDiaryEntry) -> Unit) {
    if (day.entries.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
                .border(1.dp, NxBorder, RoundedCornerShape(22.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Nothing logged yet", color = NxInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("Search or scan a food above. Your diary stays on-device.", color = NxMuted, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        nxMeals.forEach { mealName ->
            val entries = day.entries.filter { it.meal.equals(mealName, ignoreCase = true) }
            if (entries.isNotEmpty()) NxMealCard(mealName, entries, onRemove)
        }
        val other = day.entries.filter { entry -> nxMeals.none { it.equals(entry.meal, ignoreCase = true) } }
        if (other.isNotEmpty()) NxMealCard("Other", other, onRemove)
    }
}

@Composable
private fun NxMealCard(meal: String, entries: List<NxDiaryEntry>, onRemove: (NxDiaryEntry) -> Unit) {
    val kcal = entries.sumOf { it.kcal }.roundToInt()
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, NxBorder, RoundedCornerShape(22.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(meal.uppercase(), color = NxInk, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
            Text("$kcal kcal", color = NxMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        entries.sortedBy { it.timestampEpochMs }.forEach { entry ->
            Row(
                Modifier.fillMaxWidth().background(NxBg, RoundedCornerShape(14.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(entry.name, color = NxInk, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "${nxOneDecimal(entry.grams)} g · ${entry.kcal.roundToInt()} kcal · ${nxOneDecimal(entry.protein)}P · ${nxOneDecimal(entry.carbs)}C · ${nxOneDecimal(entry.fat)}F",
                        color = NxMuted, fontSize = 8.sp
                    )
                }
                Box(
                    Modifier.background(Color.White, RoundedCornerShape(10.dp)).clickable { onRemove(entry) }
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                ) {
                    Text("Remove", color = NxAmber, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NxManualBarcodeCard(barcode: String, onChange: (String) -> Unit, lookingUp: Boolean, onLookup: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(NxSoftBlue, RoundedCornerShape(18.dp)).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text("Manual barcode", color = NxBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = barcode,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("EAN / UPC / GTIN") }
            )
            NxAction(if (lookingUp) "…" else "Lookup", NxBlue, Modifier.width(80.dp), !lookingUp, onLookup)
        }
    }
}

@Composable
private fun NxNutrients(day: NxDay) {
    val entryCount = day.entries.size.coerceAtLeast(1)
    val entriesWithAnyMicro = day.entries.count { it.micronutrientCount > 0 }
    val broadCoverage = if (day.entries.isEmpty()) 0 else (entriesWithAnyMicro * 100 / day.entries.size)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp))
                .border(1.dp, NxBorder, RoundedCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("Micronutrient coverage", color = NxInk, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Text(
                if (day.entries.isEmpty()) "Log food to build today's nutrient picture."
                else "$broadCoverage% of logged foods supplied at least one micronutrient value.",
                color = NxMuted, fontSize = 10.sp, lineHeight = 14.sp
            )
            NxProgress(broadCoverage.toDouble(), 100.0, if (broadCoverage >= 75) NxGreen else NxAmber)
            Text(
                "Missing Open Food Facts fields stay unknown — they are never treated as zero.",
                color = NxMuted, fontSize = 8.sp
            )
        }

        val microById = day.micros.associateBy { it.id }
        val highlighted = nxHighlightOrder.mapNotNull { id -> microById[id]?.let { id to it } }
        if (highlighted.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
                    .border(1.dp, NxBorder, RoundedCornerShape(22.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("NEEDS ATTENTION", color = NxMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                highlighted
                    .sortedBy { (id, micro) -> nxReferenceById[id]?.let { micro.value / it.value } ?: 99.0 }
                    .forEach { (_, micro) -> NxMicroRow(micro, entryCount) }
            }
        }

        Column(
            Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
                .border(1.dp, NxBorder, RoundedCornerShape(22.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("ALL TRACKED MICRONUTRIENTS", color = NxMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            if (day.micros.isEmpty()) {
                Text("No micronutrient values were available in today's logged foods.", color = NxMuted, fontSize = 10.sp)
            } else {
                day.micros.sortedBy { it.label }.forEach { NxMicroRow(it, entryCount) }
            }
        }

        Text(
            "Reference percentages use EU adult nutrient reference values for food labelling, not personalised medical targets.",
            color = NxMuted, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 3.dp)
        )
    }
}

@Composable
private fun NxMicroRow(micro: NxMicro, totalEntries: Int) {
    val reference = nxReferenceById[micro.id]
    val pct = reference?.let { (micro.value / it.value * 100.0).roundToInt() }
    val coverage = if (totalEntries > 0) (micro.knownEntries * 100 / totalEntries).coerceIn(0, 100) else 0
    val accent = when {
        pct == null -> NxPurple
        pct < 50 -> NxAmber
        pct < 85 -> NxBlue
        else -> NxGreen
    }

    Column(Modifier.fillMaxWidth().background(NxBg, RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(micro.label, color = NxInk, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Text("${nxPretty(micro.value)} ${micro.unit} · data from $coverage% of foods", color = NxMuted, fontSize = 8.sp)
            }
            Text(pct?.let { "$it%" } ?: "tracked", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        reference?.let { NxProgress(micro.value, it.value, accent) }
    }
}

@Composable
private fun NxInsights(day: NxDay, goals: NxGoals, onGoalsSaved: (NxGoals) -> Unit) {
    val entryCount = day.entries.size
    val microCoverage = if (entryCount == 0) 0 else day.entries.count { it.micronutrientCount > 0 } * 100 / entryCount

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp))
                .border(1.dp, NxBorder, RoundedCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("Today's nutrition picture", color = NxInk, fontSize = 19.sp, fontWeight = FontWeight.Black)
            NxInsightLine(
                title = if (entryCount == 0) "Start with one meal" else "$entryCount foods logged",
                detail = if (entryCount == 0) "Logging creates the evidence layer for future nutrition ↔ sleep and recovery patterns."
                else "${day.kcal.roundToInt()} kcal · ${nxOneDecimal(day.protein)} g protein · ${nxOneDecimal(day.fibre)} g fibre",
                accent = NxBlue
            )
            NxInsightLine(
                title = if (microCoverage >= 75) "Good micronutrient visibility" else "Micronutrient picture is partial",
                detail = "$microCoverage% of today's foods include at least one micronutrient field. Missing fields remain unknown.",
                accent = if (microCoverage >= 75) NxGreen else NxAmber
            )
            if (goals.kcal == null) {
                NxInsightLine(
                    title = "Set your macro targets",
                    detail = "Targets are intentionally not guessed from a generic 2,000 kcal diet.",
                    accent = NxPurple
                )
            } else {
                val delta = day.kcal - goals.kcal
                NxInsightLine(
                    title = if (delta <= 0) "${(-delta).roundToInt()} kcal remaining" else "${delta.roundToInt()} kcal above target",
                    detail = "Based on the target you set in Project Superhuman.",
                    accent = if (delta <= 0) NxGreen else NxAmber
                )
            }
        }

        NxGoalEditor(goals, onGoalsSaved)

        Column(
            Modifier.fillMaxWidth().background(NxSoftBlue, RoundedCornerShape(22.dp)).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Where this is going", color = NxBlue, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(
                "Because foods, macros and micronutrients are stored as time-series data, this screen can later surface relationships such as meal timing vs sleep, protein consistency vs training recovery, or magnesium intake vs deep sleep — with transparent confidence rather than pretending correlation proves causation.",
                color = NxMuted, fontSize = 9.sp, lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun NxInsightLine(title: String, detail: String, accent: Color) {
    Row(Modifier.fillMaxWidth().background(NxBg, RoundedCornerShape(14.dp)).padding(11.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(7.dp).height(36.dp).background(accent, RoundedCornerShape(99.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = NxInk, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Text(detail, color = NxMuted, fontSize = 8.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun NxGoalEditor(goals: NxGoals, onSave: (NxGoals) -> Unit) {
    var kcal by remember(goals) { mutableStateOf(goals.kcal?.let(::nxEditable) ?: "") }
    var protein by remember(goals) { mutableStateOf(goals.protein?.let(::nxEditable) ?: "") }
    var carbs by remember(goals) { mutableStateOf(goals.carbs?.let(::nxEditable) ?: "") }
    var fat by remember(goals) { mutableStateOf(goals.fat?.let(::nxEditable) ?: "") }
    var fibre by remember(goals) { mutableStateOf(goals.fibre?.let(::nxEditable) ?: "") }

    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, NxBorder, RoundedCornerShape(22.dp)).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text("Your daily targets", color = NxInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text("Optional and user-controlled. Leave a field blank to remove that target.", color = NxMuted, fontSize = 9.sp)
        NxGoalField("Calories (kcal)", kcal) { kcal = nxGoalText(it) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NxGoalField("Protein (g)", protein, Modifier.weight(1f)) { protein = nxGoalText(it) }
            NxGoalField("Carbs (g)", carbs, Modifier.weight(1f)) { carbs = nxGoalText(it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NxGoalField("Fat (g)", fat, Modifier.weight(1f)) { fat = nxGoalText(it) }
            NxGoalField("Fibre (g)", fibre, Modifier.weight(1f)) { fibre = nxGoalText(it) }
        }
        NxAction("Save targets", NxBlue, Modifier.fillMaxWidth(), true) {
            onSave(
                NxGoals(
                    kcal = kcal.toDoubleOrNull()?.takeIf { it > 0 },
                    protein = protein.toDoubleOrNull()?.takeIf { it > 0 },
                    carbs = carbs.toDoubleOrNull()?.takeIf { it > 0 },
                    fat = fat.toDoubleOrNull()?.takeIf { it > 0 },
                    fibre = fibre.toDoubleOrNull()?.takeIf { it > 0 }
                )
            )
        }
    }
}

@Composable
private fun NxGoalField(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = modifier, singleLine = true, label = { Text(label) })
}

private suspend fun nxLoadGoals(): NxGoals = NxGoals(
    kcal = NativeDataHub.latest(HealthDomain.NUTRITION, "nutrition_goal_kcal")?.value,
    protein = NativeDataHub.latest(HealthDomain.NUTRITION, "nutrition_goal_protein_g")?.value,
    carbs = NativeDataHub.latest(HealthDomain.NUTRITION, "nutrition_goal_carbs_g")?.value,
    fat = NativeDataHub.latest(HealthDomain.NUTRITION, "nutrition_goal_fat_g")?.value,
    fibre = NativeDataHub.latest(HealthDomain.NUTRITION, "nutrition_goal_fibre_g")?.value
)

private suspend fun nxSaveGoals(goals: NxGoals) {
    val existing = NativeDataHub.latestForDomain(HealthDomain.NUTRITION).filter { it.metric.startsWith("nutrition_goal_") }
    if (existing.isNotEmpty()) NativeDataHub.deleteValues(existing)

    suspend fun save(metric: String, value: Double?, unit: String) {
        if (value != null) NativeDataHub.saveMetric(
            domain = HealthDomain.NUTRITION,
            metric = metric,
            value = value,
            unit = unit,
            source = "native-nutrition-goals"
        )
    }
    save("nutrition_goal_kcal", goals.kcal, "kcal")
    save("nutrition_goal_protein_g", goals.protein, "g")
    save("nutrition_goal_carbs_g", goals.carbs, "g")
    save("nutrition_goal_fat_g", goals.fat, "g")
    save("nutrition_goal_fibre_g", goals.fibre, "g")
}

private fun nxBuildDay(rows: List<HealthValue>): NxDay {
    val foodRows = rows.filter { it.metric.startsWith("food_") }
    val kcalRows = foodRows.filter { it.metric == "food_kcal" }
    val groupedByEntry = kcalRows.groupBy { row ->
        row.metadata["diaryEntryId"] ?: "legacy:${row.timestampEpochMs}:${row.metadata["foodId"].orEmpty()}"
    }

    fun metricFor(entryId: String, metric: String, fallbackMeta: String? = null): Double {
        val direct = foodRows.firstOrNull { row ->
            val id = row.metadata["diaryEntryId"] ?: "legacy:${row.timestampEpochMs}:${row.metadata["foodId"].orEmpty()}"
            id == entryId && row.metric == metric
        }?.value
        if (direct != null) return direct
        if (fallbackMeta != null) {
            return groupedByEntry[entryId]?.firstOrNull()?.metadata?.get(fallbackMeta)?.toDoubleOrNull() ?: 0.0
        }
        return 0.0
    }

    val entries = groupedByEntry.mapNotNull { (entryId, group) ->
        val kcal = group.maxByOrNull { it.timestampEpochMs } ?: return@mapNotNull null
        NxDiaryEntry(
            id = entryId,
            foodId = kcal.metadata["foodId"].orEmpty(),
            timestampEpochMs = kcal.timestampEpochMs,
            name = kcal.metadata["name"] ?: "Food",
            grams = kcal.metadata["grams"]?.toDoubleOrNull() ?: 100.0,
            meal = kcal.metadata["meal"] ?: "Diary",
            source = kcal.metadata["sourceName"] ?: kcal.source,
            barcode = kcal.metadata["barcode"]?.takeIf { it.isNotBlank() },
            kcal = kcal.value,
            protein = metricFor(entryId, "food_protein"),
            carbs = metricFor(entryId, "food_carbs", "carbs"),
            fat = metricFor(entryId, "food_fat", "fat"),
            fibre = metricFor(entryId, "food_fibre", "fibre"),
            sugar = metricFor(entryId, "food_sugar", "sugar"),
            micronutrientCount = foodRows.count { row ->
                val id = row.metadata["diaryEntryId"]
                id == entryId && row.metric.startsWith("food_micro_")
            }
        )
    }.sortedByDescending { it.timestampEpochMs }

    val microRows = foodRows.filter { it.metric.startsWith("food_micro_") }
    val micros = microRows.groupBy { it.metric }.map { (metric, nutrientRows) ->
        val id = metric.removePrefix("food_micro_")
        val reference = nxReferenceById[id]
        NxMicro(
            id = id,
            label = nutrientRows.firstOrNull()?.metadata?.get("nutrientLabel") ?: reference?.label ?: id.replace('_', ' ').replaceFirstChar { it.uppercase() },
            value = nutrientRows.sumOf { it.value },
            unit = nutrientRows.firstOrNull()?.unit ?: reference?.unit ?: "",
            knownEntries = nutrientRows.mapNotNull { it.metadata["diaryEntryId"] }.distinct().size
        )
    }

    return NxDay(
        kcal = entries.sumOf { it.kcal },
        protein = entries.sumOf { it.protein },
        carbs = entries.sumOf { it.carbs },
        fat = entries.sumOf { it.fat },
        fibre = entries.sumOf { it.fibre },
        sugar = entries.sumOf { it.sugar },
        entries = entries,
        micros = micros,
        records = rows
    )
}

private fun nxGoalText(value: String): String = value.filter { it.isDigit() || it == '.' }.take(7)
private fun nxOneDecimal(value: Double): String = ((value * 10.0).roundToInt() / 10.0).toString()
private fun nxPretty(value: Double): String = if (value >= 100.0) value.roundToInt().toString() else nxOneDecimal(value)
private fun nxEditable(value: Double): String = if (value % 1.0 == 0.0) value.roundToInt().toString() else nxOneDecimal(value)
private fun String.nxFirstNumber(): Double? = Regex("([0-9]+(?:[.,][0-9]+)?)").find(this)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
