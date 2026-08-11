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
import kotlin.math.roundToInt

private val NutritionBlue = Color(0xFF0D6CB4)
private val NutritionInk = Color(0xFF0B1F35)
private val NutritionMuted = Color(0xFF64748B)
private val NutritionGreen = Color(0xFF168A78)
private val NutritionPurple = Color(0xFF6547C9)
private val NutritionOrange = Color(0xFFD97706)
private val NutritionBg = Color(0xFFF6F9FC)
private val NutritionSoft = Color(0xFFECF8F1)
private val NutritionBorder = Color(0xFFE4EBF2)

private enum class NutritionView { DIARY, MICRONUTRIENTS }

private data class FoodDiaryEntry(
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

private data class DailyMicronutrient(
    val id: String,
    val label: String,
    val value: Double,
    val unit: String
)

private data class TodayNutrition(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fibre: Double = 0.0,
    val sugar: Double = 0.0,
    val entries: List<FoodDiaryEntry> = emptyList(),
    val micronutrients: List<DailyMicronutrient> = emptyList(),
    val records: List<HealthValue> = emptyList()
)

@Composable
fun NativeNutritionPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageBarcodeScanner = remember { BarcodeScanning.getClient() }

    var view by remember { mutableStateOf(NutritionView.DIARY) }
    var query by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<NativeFood>>(emptyList()) }
    var selected by remember { mutableStateOf<NativeFood?>(null) }
    var gramsText by remember { mutableStateOf("100") }
    var selectedMeal by remember { mutableStateOf("Breakfast") }
    var status by remember { mutableStateOf("Native food diary ready") }
    var today by remember { mutableStateOf(TodayNutrition()) }
    var searching by remember { mutableStateOf(false) }
    var lookupInProgress by remember { mutableStateOf(false) }

    suspend fun refreshToday() {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        val rows = NativeDataHub.domainBetween(HealthDomain.NUTRITION, from, to)
        today = buildTodayNutrition(rows)
    }

    suspend fun lookupBarcode(code: String) {
        val digits = code.filter(Char::isDigit)
        if (digits.length !in setOf(8, 12, 13, 14)) {
            status = "That doesn't look like a supported food barcode"
            return
        }
        barcode = digits
        lookupInProgress = true
        status = "Looking up $digits in Open Food Facts…"
        val product = NativeFoodCatalog.lookupBarcode(digits)
        lookupInProgress = false
        if (product == null) {
            status = "Product not found in Open Food Facts, or the network is unavailable"
        } else {
            selected = product
            results = listOf(product)
            gramsText = product.servingSize.extractFirstNumber()?.let(::formatEditableNumber) ?: "100"
            status = "Found ${product.name}"
        }
    }

    val liveScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (!raw.isNullOrBlank()) scope.launch { lookupBarcode(raw) }
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

    DisposableEffect(Unit) {
        onDispose { imageBarcodeScanner.close() }
    }

    LaunchedEffect(Unit) {
        NativeFoodCatalog.all(context)
        refreshToday()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(NutritionBg).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NutritionHeader(onBack)
        NutritionViewToggle(view) { view = it }

        when (view) {
            NutritionView.DIARY -> {
                MacroDashboard(today)

                NativeFoodSearchCard(
                    query = query,
                    onQueryChange = { query = it },
                    barcode = barcode,
                    onBarcodeChange = { barcode = it.filter(Char::isDigit).take(14) },
                    searching = searching,
                    lookupInProgress = lookupInProgress,
                    onSearch = {
                        scope.launch {
                            if (query.trim().length < 2) {
                                status = "Type at least two characters to search"
                                return@launch
                            }
                            searching = true
                            status = "Searching Project Superhuman + Open Food Facts…"
                            val searchResult = NativeFoodCatalog.search(context, query)
                            results = searchResult.foods
                            selected = null
                            searching = false
                            status = when {
                                results.isEmpty() && !searchResult.remoteAvailable -> "No local matches · Open Food Facts is unavailable"
                                results.isEmpty() -> "No matching foods found"
                                !searchResult.remoteAvailable -> "${results.size} local matches · Open Food Facts unavailable"
                                searchResult.remoteCount > 0 -> "${results.size} matches · local + Open Food Facts"
                                else -> "${results.size} local matches"
                            }
                        }
                    },
                    onLiveScan = {
                        liveScanLauncher.launch(
                            ScanOptions()
                                .setPrompt("Scan a food barcode")
                                .setBeepEnabled(false)
                                .setOrientationLocked(true)
                        )
                    },
                    onImageScan = { imagePicker.launch("image/*") },
                    onBarcodeLookup = { scope.launch { lookupBarcode(barcode) } }
                )

                if (results.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text("SEARCH RESULTS", color = NutritionMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                        results.take(14).forEach { food ->
                            FoodResult(food, selected?.id == food.id) {
                                selected = food
                                gramsText = food.servingSize.extractFirstNumber()?.let(::formatEditableNumber) ?: "100"
                            }
                        }
                    }
                }

                selected?.let { food ->
                    SelectedFoodCard(
                        food = food,
                        gramsText = gramsText,
                        onGramsChange = { gramsText = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                        selectedMeal = selectedMeal,
                        onMealChange = { selectedMeal = it },
                        onAdd = {
                            scope.launch {
                                val grams = gramsText.toDoubleOrNull()?.coerceIn(1.0, 5_000.0) ?: 100.0
                                NativeDataHub.saveFood(food, grams, selectedMeal)
                                status = "Added ${food.name} to $selectedMeal"
                                selected = null
                                refreshToday()
                            }
                        }
                    )
                }

                if (today.entries.isEmpty()) {
                    EmptyDiaryCard()
                } else {
                    TodayDiary(
                        entries = today.entries,
                        onRemove = { entry ->
                            scope.launch {
                                val matchingRows = today.records.filter { row ->
                                    val id = row.metadata["diaryEntryId"]
                                    if (entry.id.startsWith("legacy:")) {
                                        id == null && row.timestampEpochMs == entry.timestampEpochMs && row.metadata["foodId"] == entry.foodId
                                    } else id == entry.id
                                }
                                NativeDataHub.deleteValues(matchingRows)
                                status = "Removed ${entry.name}"
                                refreshToday()
                            }
                        }
                    )
                }
            }

            NutritionView.MICRONUTRIENTS -> {
                MicronutrientDashboard(today)
            }
        }

        Text(status, color = NutritionMuted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 2.dp))
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun NutritionHeader(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.superhumanTopButton(onClick = onBack), contentAlignment = Alignment.Center) {
            Text("←", color = NutritionBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Nutrition", color = NutritionInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("Native food diary · Open Food Facts · nutrient intelligence", color = NutritionMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun NutritionViewToggle(view: NutritionView, onChange: (NutritionView) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(17.dp))
            .border(1.dp, NutritionBorder, RoundedCornerShape(17.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        NutritionTab("Food diary", view == NutritionView.DIARY, Modifier.weight(1f)) { onChange(NutritionView.DIARY) }
        NutritionTab("Micronutrients", view == NutritionView.MICRONUTRIENTS, Modifier.weight(1f)) { onChange(NutritionView.MICRONUTRIENTS) }
    }
}

@Composable
private fun NutritionTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(if (selected) NutritionBlue else Color.Transparent, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else NutritionMuted, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun MacroDashboard(today: TodayNutrition) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NutritionMetric("Calories", today.kcal.roundToInt().toString(), "kcal", Modifier.weight(1f))
            NutritionMetric("Protein", oneDecimal(today.protein), "g", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NutritionMetric("Carbs", oneDecimal(today.carbs), "g", Modifier.weight(1f))
            NutritionMetric("Fat", oneDecimal(today.fat), "g", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NutritionMetric("Fibre", oneDecimal(today.fibre), "g", Modifier.weight(1f))
            NutritionMetric("Foods", today.entries.size.toString(), "today", Modifier.weight(1f))
        }
    }
}

@Composable
private fun NativeFoodSearchCard(
    query: String,
    onQueryChange: (String) -> Unit,
    barcode: String,
    onBarcodeChange: (String) -> Unit,
    searching: Boolean,
    lookupInProgress: Boolean,
    onSearch: () -> Unit,
    onLiveScan: () -> Unit,
    onImageScan: () -> Unit,
    onBarcodeLookup: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, NutritionBorder, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Add food", color = NutritionInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(
            "One search combines generic foods stored in Project Superhuman with branded products from Open Food Facts.",
            color = NutritionMuted, fontSize = 10.sp, lineHeight = 14.sp
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search food or product") }
        )
        ActionButton(
            title = if (searching) "Searching…" else "Search foods",
            subtitle = "Local reference + Open Food Facts",
            accent = NutritionGreen,
            enabled = !searching,
            onClick = onSearch
        )

        Box(Modifier.fillMaxWidth().height(1.dp).background(NutritionBorder))
        Text("BARCODE", color = NutritionMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SmallAction("Live scan", Modifier.weight(1f), onLiveScan)
            SmallAction("Scan image", Modifier.weight(1f), onImageScan)
        }
        OutlinedTextField(
            value = barcode,
            onValueChange = onBarcodeChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("EAN / UPC / GTIN") }
        )
        ActionButton(
            title = if (lookupInProgress) "Looking up…" else "Look up barcode",
            subtitle = "Native Open Food Facts product lookup",
            accent = NutritionBlue,
            enabled = !lookupInProgress,
            onClick = onBarcodeLookup
        )
    }
}

@Composable
private fun FoodResult(food: NativeFood, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (selected) NutritionSoft else NutritionBg, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(food.name, color = NutritionInk, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            val brand = food.brand.takeIf { it.isNotBlank() }?.let { "$it · " } ?: ""
            Text(
                "$brand${food.source} · ${food.kcal.roundToInt()} kcal / ${food.unit}",
                color = NutritionMuted, fontSize = 8.sp, lineHeight = 11.sp
            )
            Text(
                "P ${oneDecimal(food.protein)} · C ${oneDecimal(food.carbs)} · F ${oneDecimal(food.fat)}" +
                    if (food.micronutrients.isNotEmpty()) " · ${food.micronutrients.size} micros" else "",
                color = NutritionMuted, fontSize = 9.sp
            )
        }
        Text("+", color = NutritionGreen, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SelectedFoodCard(
    food: NativeFood,
    gramsText: String,
    onGramsChange: (String) -> Unit,
    selectedMeal: String,
    onMealChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    val grams = gramsText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val factor = grams / 100.0
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, NutritionBorder, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Text(food.name, color = NutritionInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
            val detail = listOf(food.brand, food.quantity, food.source).filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotBlank()) Text(detail, color = NutritionMuted, fontSize = 9.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SelectedMacro("kcal", (food.kcal * factor).roundToInt().toString(), Modifier.weight(1f))
            SelectedMacro("protein", oneDecimal(food.protein * factor), Modifier.weight(1f))
            SelectedMacro("carbs", oneDecimal(food.carbs * factor), Modifier.weight(1f))
            SelectedMacro("fat", oneDecimal(food.fat * factor), Modifier.weight(1f))
        }

        Text("MEAL", color = NutritionMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf("Breakfast", "Lunch", "Dinner", "Snack").forEach { meal ->
                MealChip(meal, meal == selectedMeal) { onMealChange(meal) }
            }
        }

        OutlinedTextField(
            value = gramsText,
            onValueChange = onGramsChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Portion in g / ml") }
        )
        if (food.servingSize.isNotBlank()) {
            Text("Label serving: ${food.servingSize}", color = NutritionMuted, fontSize = 8.sp)
        }

        if (food.micronutrients.isNotEmpty()) {
            Text("MICRONUTRIENTS AVAILABLE", color = NutritionMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                food.micronutrients.values.take(8).forEach { nutrient ->
                    Box(Modifier.background(NutritionSoft, RoundedCornerShape(11.dp)).padding(horizontal = 9.dp, vertical = 7.dp)) {
                        Text(
                            "${nutrient.label} ${formatNutrient(nutrient.valuePer100 * factor, nutrient.unit)}",
                            color = NutritionGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else if (food.source == "Open Food Facts") {
            Text(
                "Open Food Facts has no micronutrient values for this product. Project Superhuman will keep them unknown rather than guess.",
                color = NutritionMuted, fontSize = 8.sp, lineHeight = 12.sp
            )
        }

        ActionButton("Add to $selectedMeal", "Save macros + available micronutrients to the Data Vault", NutritionBlue, true, onAdd)
    }
}

@Composable
private fun MealChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.background(if (selected) NutritionGreen else NutritionBg, RoundedCornerShape(99.dp))
            .border(1.dp, if (selected) NutritionGreen else NutritionBorder, RoundedCornerShape(99.dp))
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else NutritionMuted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SelectedMacro(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(NutritionBg, RoundedCornerShape(12.dp)).padding(9.dp)) {
        Text(value, color = NutritionInk, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text(label, color = NutritionMuted, fontSize = 7.sp)
    }
}

@Composable
private fun EmptyDiaryCard() {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, NutritionBorder, RoundedCornerShape(22.dp)).padding(17.dp)
    ) {
        Text("Today's diary is empty", color = NutritionInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text("Search, scan a barcode, or scan a product image above to start logging.", color = NutritionMuted, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun TodayDiary(entries: List<FoodDiaryEntry>, onRemove: (FoodDiaryEntry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("TODAY'S DIARY", color = NutritionMuted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        val order = listOf("Breakfast", "Lunch", "Dinner", "Snack", "Diary")
        entries.groupBy { it.meal }.toList()
            .sortedBy { (meal, _) -> order.indexOf(meal).takeIf { it >= 0 } ?: 99 }
            .forEach { (meal, mealEntries) ->
                Column(
                    Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp))
                        .border(1.dp, NutritionBorder, RoundedCornerShape(20.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(meal.uppercase(), color = NutritionInk, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                        Text("${mealEntries.sumOf { it.kcal }.roundToInt()} kcal", color = NutritionBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    mealEntries.forEach { entry -> DiaryEntryRow(entry) { onRemove(entry) } }
                }
            }
    }
}

@Composable
private fun DiaryEntryRow(entry: FoodDiaryEntry, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(NutritionBg, RoundedCornerShape(14.dp)).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.name, color = NutritionInk, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "${formatEditableNumber(entry.grams)} g · ${entry.kcal.roundToInt()} kcal · P ${oneDecimal(entry.protein)} · C ${oneDecimal(entry.carbs)} · F ${oneDecimal(entry.fat)}",
                color = NutritionMuted, fontSize = 8.sp, lineHeight = 11.sp
            )
            if (entry.micronutrientCount > 0) {
                Text("${entry.micronutrientCount} micronutrients captured", color = NutritionGreen, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            Modifier.background(Color.White, RoundedCornerShape(10.dp)).clickable(onClick = onRemove)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Remove", color = NutritionOrange, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun MicronutrientDashboard(today: TodayNutrition) {
    val knownFoods = today.entries.count { it.micronutrientCount > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().background(NutritionSoft, RoundedCornerShape(22.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text("Micronutrient evidence", color = NutritionGreen, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(
                "These totals only include nutrients actually supplied by the food source. Missing values stay unknown rather than being treated as zero.",
                color = NutritionMuted, fontSize = 10.sp, lineHeight = 15.sp
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MicroSummary("Known foods", knownFoods.toString(), Modifier.weight(1f))
                MicroSummary("Foods today", today.entries.size.toString(), Modifier.weight(1f))
                MicroSummary("Nutrients", today.micronutrients.size.toString(), Modifier.weight(1f))
            }
        }

        val magnesium = today.micronutrients.firstOrNull { it.id == "magnesium" }
        Column(
            Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
                .border(1.dp, NutritionBorder, RoundedCornerShape(22.dp)).padding(16.dp)
        ) {
            Text("Magnesium", color = NutritionInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text(
                magnesium?.let { "${formatNutrient(it.value, it.unit)} captured from today's logged foods" }
                    ?: "No known magnesium values captured today",
                color = if (magnesium != null) NutritionGreen else NutritionMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "This is stored as a daily time-series metric, so the future correlation engine can compare it with sleep, recovery and other outcomes.",
                color = NutritionMuted, fontSize = 9.sp, lineHeight = 13.sp
            )
        }

        if (today.micronutrients.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
                    .border(1.dp, NutritionBorder, RoundedCornerShape(22.dp)).padding(16.dp)
            ) {
                Text("No micronutrient data yet", color = NutritionInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(
                    "Many packaged-food labels only publish macros. When Open Food Facts has vitamins or minerals for a scanned/searched product, they will appear here automatically.",
                    color = NutritionMuted, fontSize = 10.sp, lineHeight = 15.sp
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
                    .border(1.dp, NutritionBorder, RoundedCornerShape(22.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("Today's known micronutrients", color = NutritionInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
                today.micronutrients.sortedWith(compareBy<DailyMicronutrient> { micronutrientSortOrder(it.id) }.thenBy { it.label })
                    .forEach { nutrient ->
                        Row(
                            Modifier.fillMaxWidth().background(NutritionBg, RoundedCornerShape(12.dp)).padding(11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(nutrient.label, color = NutritionInk, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(formatNutrient(nutrient.value, nutrient.unit), color = NutritionGreen, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
            }
        }

        Column(
            Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
                .border(1.dp, NutritionBorder, RoundedCornerShape(22.dp)).padding(16.dp)
        ) {
            Text("Helpful compounds", color = NutritionPurple, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(
                "The native pipeline is ready for compounds such as caffeine, omega-3s and choline, but Project Superhuman won't invent values when a source doesn't provide them. Choline is already captured when Open Food Facts has it.",
                color = NutritionMuted, fontSize = 9.sp, lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun MicroSummary(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color.White.copy(alpha = .72f), RoundedCornerShape(13.dp)).padding(10.dp)) {
        Text(value, color = NutritionGreen, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Text(label, color = NutritionMuted, fontSize = 7.sp)
    }
}

@Composable
private fun NutritionMetric(title: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(18.dp)).border(1.dp, NutritionBorder, RoundedCornerShape(18.dp)).padding(13.dp)) {
        Text(title.uppercase(), color = NutritionMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = NutritionInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(3.dp))
            Text(unit, color = NutritionMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun ActionButton(
    title: String,
    subtitle: String,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(if (enabled) accent else accent.copy(alpha = .45f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Color.White.copy(alpha = .8f), fontSize = 9.sp)
        }
        Text("→", color = Color.White, fontSize = 22.sp)
    }
}

@Composable
private fun SmallAction(title: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(NutritionSoft, RoundedCornerShape(15.dp)).clickable(onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(title, color = NutritionGreen, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
    }
}

private fun buildTodayNutrition(rows: List<HealthValue>): TodayNutrition {
    val kcalRows = rows.filter { it.metric == "food_kcal" }.sortedByDescending { it.timestampEpochMs }

    fun linkedRows(kcal: HealthValue): List<HealthValue> {
        val entryId = kcal.metadata["diaryEntryId"]
        return if (entryId != null) {
            rows.filter { it.metadata["diaryEntryId"] == entryId }
        } else {
            rows.filter {
                it.timestampEpochMs == kcal.timestampEpochMs &&
                    it.metadata["foodId"] == kcal.metadata["foodId"] &&
                    it.source == kcal.source
            }
        }
    }

    val entries = kcalRows.map { kcal ->
        val linked = linkedRows(kcal)
        fun metric(name: String): Double? = linked.firstOrNull { it.metric == name }?.value
        fun metaDouble(name: String): Double? = kcal.metadata[name]?.toDoubleOrNull()
        FoodDiaryEntry(
            id = kcal.metadata["diaryEntryId"] ?: "legacy:${kcal.timestampEpochMs}:${kcal.metadata["foodId"].orEmpty()}",
            foodId = kcal.metadata["foodId"].orEmpty(),
            timestampEpochMs = kcal.timestampEpochMs,
            name = kcal.metadata["name"] ?: "Food",
            grams = kcal.metadata["grams"]?.toDoubleOrNull() ?: 100.0,
            meal = kcal.metadata["meal"]?.takeIf { it.isNotBlank() } ?: "Diary",
            source = kcal.metadata["sourceName"] ?: kcal.source,
            barcode = kcal.metadata["barcode"]?.takeIf { it.isNotBlank() },
            kcal = kcal.value,
            protein = metaDouble("protein") ?: metric("food_protein") ?: 0.0,
            carbs = metaDouble("carbs") ?: metric("food_carbs") ?: 0.0,
            fat = metaDouble("fat") ?: metric("food_fat") ?: 0.0,
            fibre = metaDouble("fibre") ?: metric("food_fibre") ?: 0.0,
            sugar = metaDouble("sugar") ?: metric("food_sugar") ?: 0.0,
            micronutrientCount = kcal.metadata["micronutrientCount"]?.toIntOrNull()
                ?: linked.count { it.metadata["nutrientId"] != null }
        )
    }

    val microRows = rows.filter { it.metadata["nutrientId"] != null }
    val micros = microRows.groupBy { it.metadata["nutrientId"].orEmpty() }
        .mapNotNull { (id, values) ->
            if (id.isBlank()) return@mapNotNull null
            DailyMicronutrient(
                id = id,
                label = values.firstNotNullOfOrNull { it.metadata["nutrientLabel"] } ?: id.replace('_', ' '),
                value = values.sumOf { it.value },
                unit = values.firstOrNull()?.unit ?: ""
            )
        }

    return TodayNutrition(
        kcal = entries.sumOf { it.kcal },
        protein = entries.sumOf { it.protein },
        carbs = entries.sumOf { it.carbs },
        fat = entries.sumOf { it.fat },
        fibre = entries.sumOf { it.fibre },
        sugar = entries.sumOf { it.sugar },
        entries = entries,
        micronutrients = micros,
        records = rows
    )
}

private fun micronutrientSortOrder(id: String): Int = when (id) {
    "magnesium" -> 0
    "potassium" -> 1
    "calcium" -> 2
    "iron" -> 3
    "zinc" -> 4
    "vitamin_d" -> 5
    "vitamin_b12" -> 6
    "vitamin_c" -> 7
    "folate" -> 8
    else -> 50
}

private fun String.extractFirstNumber(): Double? {
    val match = Regex("([0-9]+(?:[.,][0-9]+)?)").find(this) ?: return null
    return match.groupValues[1].replace(',', '.').toDoubleOrNull()
}

private fun formatEditableNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.roundToInt().toString() else oneDecimal(value)

private fun oneDecimal(value: Double): String = ((value * 10.0).roundToInt() / 10.0).toString()

private fun formatNutrient(value: Double, unit: String): String {
    val shown = when {
        value >= 100.0 -> value.roundToInt().toString()
        value >= 10.0 -> oneDecimal(value)
        value >= 1.0 -> ((value * 100.0).roundToInt() / 100.0).toString()
        else -> ((value * 1000.0).roundToInt() / 1000.0).toString()
    }
    return "$shown $unit"
}
