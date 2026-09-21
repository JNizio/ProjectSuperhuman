package com.projectsuperhuman.next

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val N2Navy get() = superhumanBrandText
private val N2Blue get() = superhumanBlue
private val N2Cyan get() = if (SuperhumanAppearance.darkMode) superhumanAccent else Color(0xFF20A7C4)
private val N2Ink get() = superhumanTextPrimary
private val N2Muted get() = superhumanTextMuted
private val N2Bg get() = superhumanBackground
private val N2Border get() = if (SuperhumanAppearance.darkMode) superhumanBorder else Color(0xFFE3EAF0)
private val N2Green get() = if (SuperhumanAppearance.darkMode) superhumanGreen else Color(0xFF4AAE91)
private val N2Amber get() = if (SuperhumanAppearance.darkMode) Color(0xFFFFB766) else Color(0xFFD98B2B)
private val N2Purple get() = if (SuperhumanAppearance.darkMode) Color(0xFFA99BFF) else Color(0xFF7260BF)
private val N2SoftBlue get() = if (SuperhumanAppearance.darkMode) Color(0xFF102838) else Color(0xFFEAF4FA)
private val N2SoftGreen get() = if (SuperhumanAppearance.darkMode) Color(0xFF102A26) else Color(0xFFEEF8F4)
private val N2Surface get() = superhumanSurface
private val N2RowBg get() = if (SuperhumanAppearance.darkMode) superhumanSurfaceSoft else N2Bg

private enum class N2View { DIARY, NUTRIENTS, INSIGHTS }
private enum class N2Range(val label: String, val days: Long) {
    DAY("Day", 1),
    WEEK("7 days", 7),
    MONTH("30 days", 30)
}

private data class N2Entry(
    val id: String,
    val foodId: String,
    val timestamp: Long,
    val name: String,
    val amount: Double,
    val amountUnit: String,
    val meal: String,
    val kcal: Double,
    val kcalKnown: Boolean,
    val protein: Double,
    val proteinKnown: Boolean,
    val carbs: Double,
    val carbsKnown: Boolean,
    val carbohydrateDefinition: CarbohydrateDefinition,
    val fat: Double,
    val fatKnown: Boolean,
    val fibre: Double,
    val fibreKnown: Boolean,
    val micronutrientCount: Int,
    val barcode: String,
    val sourceName: String,
    val mealGroupId: String,
    val mealGroupName: String
)

private data class N2Micro(
    val id: String,
    val label: String,
    val value: Double,
    val unit: String,
    val knownEntries: Int
)

private data class N2Day(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fibre: Double = 0.0,
    val entries: List<N2Entry> = emptyList(),
    val micros: List<N2Micro> = emptyList(),
    val records: List<HealthValue> = emptyList()
) {
    val kcalComplete: Boolean get() = entries.all { it.kcalKnown }
    val proteinComplete: Boolean get() = entries.all { it.proteinKnown }
    val carbsComplete: Boolean get() {
        if (!entries.all { it.carbsKnown }) return false
        val definitions = entries.map { it.carbohydrateDefinition }.toSet()
        return CarbohydrateDefinition.UNKNOWN !in definitions && definitions.size <= 1
    }
    val fatComplete: Boolean get() = entries.all { it.fatKnown }
    val fibreComplete: Boolean get() = entries.all { it.fibreKnown }
}

private data class N2Goals(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fibre: Double? = null
)

private data class N2Reference(val id: String, val label: String, val value: Double, val unit: String)

/* EU 1169/2011 Annex XIII adult NRVs: reference context, not personalised medical targets. */
private val n2Refs = listOf(
    N2Reference("vitamin_a", "Vitamin A", 800.0, "µg"),
    N2Reference("vitamin_d", "Vitamin D", 5.0, "µg"),
    N2Reference("vitamin_e", "Vitamin E", 12.0, "mg"),
    N2Reference("vitamin_k", "Vitamin K", 75.0, "µg"),
    N2Reference("vitamin_c", "Vitamin C", 80.0, "mg"),
    N2Reference("vitamin_b1", "Vitamin B1", 1.1, "mg"),
    N2Reference("vitamin_b2", "Vitamin B2", 1.4, "mg"),
    N2Reference("niacin", "Niacin", 16.0, "mg"),
    N2Reference("vitamin_b6", "Vitamin B6", 1.4, "mg"),
    N2Reference("folate", "Folate", 200.0, "µg"),
    N2Reference("vitamin_b12", "Vitamin B12", 2.5, "µg"),
    N2Reference("biotin", "Biotin", 50.0, "µg"),
    N2Reference("pantothenic_acid", "Pantothenic acid", 6.0, "mg"),
    N2Reference("potassium", "Potassium", 2000.0, "mg"),
    N2Reference("chloride", "Chloride", 800.0, "mg"),
    N2Reference("calcium", "Calcium", 800.0, "mg"),
    N2Reference("phosphorus", "Phosphorus", 700.0, "mg"),
    N2Reference("magnesium", "Magnesium", 375.0, "mg"),
    N2Reference("iron", "Iron", 14.0, "mg"),
    N2Reference("zinc", "Zinc", 10.0, "mg"),
    N2Reference("copper", "Copper", 1.0, "mg"),
    N2Reference("manganese", "Manganese", 2.0, "mg"),
    N2Reference("selenium", "Selenium", 55.0, "µg"),
    N2Reference("iodine", "Iodine", 150.0, "µg")
)
private val n2RefById = n2Refs.associateBy { it.id }
private val n2FocusIds = listOf("magnesium", "potassium", "calcium", "vitamin_d", "iron", "folate", "vitamin_b12", "zinc")
private val n2Meals = listOf("Breakfast", "Lunch", "Dinner", "Snack")

@Composable
internal fun NativeNutritionExperienceV2Page(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageScanner = remember { BarcodeScanning.getClient() }

    var view by remember { mutableStateOf(N2View.DIARY) }
    var day by remember { mutableStateOf(N2Day()) }
    var goals by remember { mutableStateOf(N2Goals()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<NativeFood>>(emptyList()) }
    var selected by remember { mutableStateOf<NativeFood?>(null) }
    var portion by remember { mutableStateOf("100") }
    var portionUnit by remember { mutableStateOf(FoodUnit.G) }
    var meal by remember { mutableStateOf("Breakfast") }
    var searching by remember { mutableStateOf(false) }
    var foodSearchOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var showManualBarcode by remember { mutableStateOf(false) }
    var barcode by remember { mutableStateOf("") }
    var lookingUp by remember { mutableStateOf(false) }
    var showAllNutrients by remember { mutableStateOf(false) }
    var editingTargets by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now(ZoneId.systemDefault())) }
    var nutrientRange by remember { mutableStateOf(N2Range.DAY) }
    var nutrientDay by remember { mutableStateOf(N2Day()) }
    var weekDays by remember { mutableStateOf<List<N2Day>>(emptyList()) }
    var lastRemoved by remember { mutableStateOf<N2Entry?>(null) }

    suspend fun refresh() {
        val zone = ZoneId.systemDefault()
        val date = selectedDate
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        day = n2BuildDay(NativeDataHub.domainBetween(HealthDomain.NUTRITION, from, to))
        goals = n2LoadGoals()
    }

    suspend fun refreshWeek() {
        val zone = ZoneId.systemDefault()
        val startDate = selectedDate.minusDays(6)
        val from = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = selectedDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        val rows = NativeDataHub.domainBetween(HealthDomain.NUTRITION, from, to)
        weekDays = (0L..6L).map { offset ->
            val date = startDate.plusDays(offset)
            val dayFrom = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayTo = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
            n2BuildDay(rows.filter { it.timestampEpochMs in dayFrom..dayTo })
        }
    }

    suspend fun refreshNutrients() {
        val zone = ZoneId.systemDefault()
        val fromDate = selectedDate.minusDays(nutrientRange.days - 1)
        val from = fromDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = selectedDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        nutrientDay = n2BuildDay(NativeDataHub.domainBetween(HealthDomain.NUTRITION, from, to))
    }

    suspend fun repeatEntry(entry: N2Entry) {
        val matching = day.records.filter { row ->
            val id = row.metadata["diaryEntryId"]
            if (entry.id.startsWith("legacy:")) {
                id == null && row.timestampEpochMs == entry.timestamp && row.metadata["foodId"] == entry.foodId
            } else id == entry.id
        }
        if (matching.isEmpty()) {
            status = "Couldn’t repeat " + entry.name
            return
        }
        val zone = ZoneId.systemDefault()
        val timestamp = if (selectedDate == LocalDate.now(zone)) {
            System.currentTimeMillis()
        } else {
            selectedDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        }
        NativeDataHub.duplicateNutritionEntry(matching, timestamp)
        status = "Added " + entry.name + " again"
        refresh()
        refreshWeek()
        refreshNutrients()
    }

    suspend fun lookupBarcode(raw: String) {
        val digits = raw.filter(Char::isDigit)
        if (!NutritionMath.isValidBarcode(digits)) {
            status = "Barcode is invalid or has a bad check digit"
            return
        }
        lookingUp = true
        status = "Looking up product…"
        val product = NativeFoodCatalog.lookupBarcode(context, digits)
        lookingUp = false
        if (product == null) {
            status = "Product not found, or Open Food Facts is unavailable"
        } else {
            barcode = digits
            results = listOf(product)
            selected = product
            portionUnit = FoodUnitSystem.defaultUnit(product)
            portion = n2Editable(FoodUnitSystem.defaultAmount(product))
            status = ""
        }
    }

    val liveScan = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf { it.isNotBlank() }?.let { raw -> scope.launch { lookupBarcode(raw) } }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = "Reading barcode from image…"
        runCatching { InputImage.fromFilePath(context, uri) }
            .onSuccess { image ->
                imageScanner.process(image)
                    .addOnSuccessListener { codes ->
                        val raw = codes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                        if (raw == null) status = "No barcode found in that image"
                        else scope.launch { lookupBarcode(raw) }
                    }
                    .addOnFailureListener { status = "Couldn't read that image" }
            }
            .onFailure { status = "Couldn't open that image" }
    }

    DisposableEffect(Unit) { onDispose { imageScanner.close() } }
    LaunchedEffect(Unit) { NativeFoodCatalog.all(context) }
    LaunchedEffect(selectedDate) { refresh(); refreshWeek(); refreshNutrients() }
    LaunchedEffect(selectedDate, nutrientRange) { refreshNutrients() }

    if (foodSearchOpen) {
        N2FoodSearchScreen(
            query = query,
            onQueryChange = { query = it },
            foods = results,
            searching = searching,
            status = status,
            onBack = {
                foodSearchOpen = false
                status = ""
            },
            onSearch = {
                scope.launch {
                    if (query.trim().length < 2) {
                        status = "Type at least two characters"
                        return@launch
                    }
                    searching = true
                    status = "Searching…"
                    val found = NativeFoodCatalog.search(context, query)
                    results = found.foods
                    searching = false
                    status = when {
                        results.isEmpty() -> "No matching foods found"
                        !found.remoteAvailable -> "Local results shown · Open Food Facts unavailable"
                        else -> ""
                    }
                }
            },
            onSelect = { food ->
                selected = food
                portionUnit = FoodUnitSystem.defaultUnit(food)
                portion = n2Editable(FoodUnitSystem.defaultAmount(food))
                foodSearchOpen = false
                status = ""
            }
        )
        return
    }

    Column(
        Modifier.fillMaxSize().background(N2Bg).verticalScroll(rememberScrollState())
            .padding(horizontal = SuperhumanLayout.pageHorizontal, vertical = SuperhumanLayout.pageVertical),
        verticalArrangement = Arrangement.spacedBy(SuperhumanLayout.sectionGap)
    ) {
        N2Header(onBack, selectedDate)
        N2Tabs(view) { view = it }
        N2DateNav(
            date = selectedDate,
            onPrevious = { selectedDate = selectedDate.minusDays(1) },
            onToday = { selectedDate = LocalDate.now(ZoneId.systemDefault()) },
            onNext = {
                val today = LocalDate.now(ZoneId.systemDefault())
                if (selectedDate.isBefore(today)) selectedDate = selectedDate.plusDays(1)
            }
        )

        when (view) {
            N2View.DIARY -> {
                N2Hero(
                    day = day,
                    goals = goals,
                    weekDays = weekDays,
                    selectedDate = selectedDate,
                    onSelectDate = { selectedDate = it }
                )
                N2LogCard(
                    query = query,
                    onQueryChange = { query = it },
                    searching = searching,
                    showManualBarcode = showManualBarcode,
                    onToggleManualBarcode = { showManualBarcode = !showManualBarcode },
                    barcode = barcode,
                    onBarcodeChange = { barcode = it.filter(Char::isDigit).take(14) },
                    lookingUp = lookingUp,
                    onSearch = {
                        foodSearchOpen = true
                        selected = null
                        scope.launch {
                            if (query.trim().length < 2) {
                                status = "Type at least two characters"
                                return@launch
                            }
                            searching = true
                            status = "Searching…"
                            val found = NativeFoodCatalog.search(context, query)
                            results = found.foods
                            searching = false
                            status = when {
                                results.isEmpty() -> "No matching foods found"
                                !found.remoteAvailable -> "Local results shown · Open Food Facts unavailable"
                                else -> ""
                            }
                        }
                    },
                    onScan = {
                        liveScan.launch(ScanOptions().setPrompt("Scan a food barcode").setBeepEnabled(false).setOrientationLocked(true))
                    },
                    onPhoto = { imagePicker.launch("image/*") },
                    onBarcodeLookup = { scope.launch { lookupBarcode(barcode) } }
                )

                selected?.let { food ->
                    N2AddFoodCard(
                        food = food,
                        portion = portion,
                        onPortionChange = { portion = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                        portionUnit = portionUnit,
                        onPortionUnitChange = { unit ->
                            portionUnit = unit
                            portion = n2Editable(
                                if (unit.dimension == FoodMeasureDimension.DERIVED) {
                                    1.0
                                } else {
                                    FoodUnitSystem.amountForBasis(food, unit) ?: FoodUnitSystem.defaultAmount(food)
                                }
                            )
                        },
                        meal = meal,
                        onMealChange = { meal = it },
                        onFoodCorrected = { corrected ->
                            selected = corrected
                            results = results.map { if (it.id == corrected.id) corrected else it }
                            status = "Using your corrected nutrition"
                        },
                        onRemoveCorrection = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    FoodNutritionOverrideStore.remove(context, food)
                                }
                                val restored = if (!food.barcode.isNullOrBlank()) {
                                    NativeFoodCatalog.lookupBarcode(context, food.barcode.orEmpty())
                                } else {
                                    NativeFoodCatalog.search(context, food.name)
                                        .foods
                                        .firstOrNull { it.id == food.id }
                                }
                                if (restored != null) {
                                    selected = restored
                                    results = results.map { if (it.id == restored.id) restored else it }
                                    portionUnit = FoodUnitSystem.defaultUnit(restored)
                                    portion = n2Editable(FoodUnitSystem.defaultAmount(restored))
                                }
                                status = "Using original source nutrition"
                            }
                        },
                        onAdd = {
                            scope.launch {
                                val amount = portion.toDoubleOrNull()?.coerceIn(1.0, 5000.0) ?: 100.0
                                val zone = ZoneId.systemDefault()
                                val today = LocalDate.now(zone)
                                val timestamp = if (selectedDate == today) {
                                    System.currentTimeMillis()
                                } else {
                                    selectedDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
                                }
                                NativeDataHub.saveFood(
                                    food = food,
                                    amount = amount,
                                    inputUnit = portionUnit,
                                    meal = meal,
                                    timestampEpochMs = timestamp
                                )
                                selected = null
                                results = emptyList()
                                query = ""
                                status = "Added to $meal"
                                refresh()
                                refreshNutrients()
                            }
                        }
                    )
                }

                if (day.entries.isNotEmpty()) {
                    N2QuickRepeat(day.entries.take(4)) { entry ->
                        scope.launch { repeatEntry(entry) }
                    }
                }

                N2Diary(
                    day = day,
                    onDuplicate = { entry -> scope.launch { repeatEntry(entry) } },
                    onRenameGroup = { groupEntries, newName ->
                        scope.launch {
                            val ids = groupEntries.map { it.id }.toSet()
                            val matching = day.records.filter { row -> row.metadata["diaryEntryId"] in ids }
                            NativeDataHub.renameNutritionMealGroup(matching, newName)
                            status = "Meal renamed"
                            refresh()
                            refreshWeek()
                            refreshNutrients()
                        }
                    },
                    onRemove = { entry ->
                        scope.launch {
                            val matching = day.records.filter { row ->
                                val id = row.metadata["diaryEntryId"]
                                if (entry.id.startsWith("legacy:")) {
                                    id == null && row.timestampEpochMs == entry.timestamp && row.metadata["foodId"] == entry.foodId
                                } else id == entry.id
                            }
                            NativeDataHub.deleteValues(matching)
                            lastRemoved = entry
                            status = "Removed " + entry.name
                            refresh()
                            refreshNutrients()
                        }
                    }
                )

                lastRemoved?.let { removed ->
                    N2UndoBar(removed.name) {
                        scope.launch {
                            repeatEntry(removed)
                            lastRemoved = null
                        }
                    }
                }
            }

            N2View.NUTRIENTS -> N2Nutrients(
                day = nutrientDay,
                showAll = showAllNutrients,
                range = nutrientRange,
                onRangeChange = { nutrientRange = it },
                onToggleAll = { showAllNutrients = !showAllNutrients }
            )

            N2View.INSIGHTS -> N2Insights(
                day = day,
                goals = goals,
                editingTargets = editingTargets,
                onToggleTargets = { editingTargets = !editingTargets },
                onSaveTargets = { saved ->
                    scope.launch {
                        n2SaveGoals(saved)
                        editingTargets = false
                        status = "Targets saved"
                        refresh()
                    }
                }
            )
        }

        if (status.isNotBlank()) Text(status, color = N2Muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 3.dp))
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun N2Header(onBack: () -> Unit, date: LocalDate) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(SuperhumanLayout.iconTouchTarget).background(N2Surface, RoundedCornerShape(14.dp))
                .border(1.dp, N2Border, RoundedCornerShape(14.dp)).superhumanClickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.tabler_arrow_left),
                contentDescription = "Back",
                tint = N2Navy,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Nutrition", color = N2Ink, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text("Diary · nutrients · personal patterns", color = N2Muted, fontSize = 10.sp)
        }
        Text(
            date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)),
            color = N2Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun N2DateNav(
    date: LocalDate,
    onPrevious: () -> Unit,
    onToday: () -> Unit,
    onNext: () -> Unit
) {
    val today = LocalDate.now(ZoneId.systemDefault())
    Box(
        Modifier.fillMaxWidth().height(58.dp)
            .background(N2Surface, RoundedCornerShape(16.dp))
            .border(1.dp, N2Border, RoundedCornerShape(16.dp))
    ) {
        Box(
            Modifier.align(Alignment.CenterStart).size(SuperhumanLayout.iconTouchTarget)
                .clickable(onClick = onPrevious),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.tabler_chevron_left),
                contentDescription = "Previous day",
                tint = N2Ink,
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            if (date == today) "Today" else date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)),
            color = N2Ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.Center).clickable(onClick = onToday).padding(horizontal = 12.dp, vertical = 8.dp)
        )

        Box(
            Modifier.align(Alignment.CenterEnd).size(SuperhumanLayout.iconTouchTarget)
                .clickable(enabled = date.isBefore(today), onClick = onNext),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.tabler_chevron_right),
                contentDescription = "Next day",
                tint = if (date.isBefore(today)) N2Ink else N2Muted.copy(alpha = .35f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun N2Tabs(view: N2View, onChange: (N2View) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(18.dp))
            .border(1.dp, N2Border, RoundedCornerShape(18.dp)).padding(SuperhumanLayout.segmentedPadding),
        horizontalArrangement = Arrangement.spacedBy(SuperhumanLayout.segmentedGap)
    ) {
        N2Tab("Diary", view == N2View.DIARY, Modifier.weight(1f)) { onChange(N2View.DIARY) }
        N2Tab("Nutrients", view == N2View.NUTRIENTS, Modifier.weight(1f)) { onChange(N2View.NUTRIENTS) }
        N2Tab("Insights", view == N2View.INSIGHTS, Modifier.weight(1f)) { onChange(N2View.INSIGHTS) }
    }
}

@Composable
private fun N2Tab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(SuperhumanLayout.controlHeight)
            .background(if (selected) N2Blue else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (selected) Color.White else N2Muted, fontSize = 10.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun N2Hero(
    day: N2Day,
    goals: N2Goals,
    weekDays: List<N2Day>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .background(N2Surface, RoundedCornerShape(24.dp))
            .border(1.dp, N2Border, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Today's intake", color = N2Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            N2NutritionRing(day = day, goals = goals)

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                N2MacroValue("Protein", day.protein, goals.protein, N2Blue, day.proteinComplete)
                N2MacroValue("Carbs", day.carbs, goals.carbs, N2Cyan, day.carbsComplete)
                N2MacroValue("Fat", day.fat, goals.fat, N2Amber, day.fatComplete)
                N2MacroValue("Fibre", day.fibre, goals.fibre, N2Green, day.fibreComplete)
            }
        }

        if (weekDays.any { it.entries.isNotEmpty() }) {
            N2WeekChart(weekDays, goals.kcal, selectedDate, onSelectDate)
        }
    }
}

private data class N2RingMetric(
    val value: Double,
    val target: Double?,
    val color: Color,
    val complete: Boolean
)

@Composable
private fun N2NutritionRing(day: N2Day, goals: N2Goals) {
    val calorieFraction = if (goals.kcal != null && goals.kcal > 0.0) {
        (day.kcal / goals.kcal).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val macroMetrics = listOf(
        N2RingMetric(day.protein, goals.protein, N2Blue, day.proteinComplete),
        N2RingMetric(day.carbs, goals.carbs, N2Cyan, day.carbsComplete),
        N2RingMetric(day.fat, goals.fat, N2Amber, day.fatComplete),
        N2RingMetric(day.fibre, goals.fibre, N2Green, day.fibreComplete)
    )

    Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val calorieStroke = 10.dp.toPx()
            val macroStroke = 5.dp.toPx()
            val calorieInset = 18.dp.toPx()
            val macroInset = 4.dp.toPx()

            drawArc(
                color = N2Border,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(calorieInset, calorieInset),
                size = androidx.compose.ui.geometry.Size(
                    size.width - calorieInset * 2,
                    size.height - calorieInset * 2
                ),
                style = Stroke(width = calorieStroke, cap = StrokeCap.Round)
            )

            if (calorieFraction > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(N2Blue, N2Cyan, N2Blue)),
                    startAngle = -90f,
                    sweepAngle = 360f * calorieFraction,
                    useCenter = false,
                    topLeft = Offset(calorieInset, calorieInset),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - calorieInset * 2,
                        size.height - calorieInset * 2
                    ),
                    style = Stroke(width = calorieStroke, cap = StrokeCap.Round)
                )
            }

            val gap = 8f
            val segmentSweep = (360f - gap * 4f) / 4f
            macroMetrics.forEachIndexed { index, metric ->
                val start = -90f + index * (segmentSweep + gap)
                val target = metric.target
                val fraction = if (target != null && target > 0.0) {
                    (metric.value / target).toFloat().coerceIn(0f, 1f)
                } else if (metric.value > 0.0) {
                    .35f
                } else {
                    0f
                }
                val activeColor = if (metric.complete) metric.color else metric.color.copy(alpha = .55f)

                drawArc(
                    color = N2Border.copy(alpha = .82f),
                    startAngle = start,
                    sweepAngle = segmentSweep,
                    useCenter = false,
                    topLeft = Offset(macroInset, macroInset),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - macroInset * 2,
                        size.height - macroInset * 2
                    ),
                    style = Stroke(width = macroStroke, cap = StrokeCap.Round)
                )

                if (fraction > 0f) {
                    drawArc(
                        color = activeColor,
                        startAngle = start,
                        sweepAngle = segmentSweep * fraction,
                        useCenter = false,
                        topLeft = Offset(macroInset, macroInset),
                        size = androidx.compose.ui.geometry.Size(
                            size.width - macroInset * 2,
                            size.height - macroInset * 2
                        ),
                        style = Stroke(width = macroStroke, cap = StrokeCap.Round)
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                (if (day.kcalComplete) "" else "~") + day.kcal.roundToInt().toString(),
                color = N2Ink,
                fontSize = 27.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                if (day.kcalComplete) "kcal" else "kcal · partial",
                color = N2Muted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            if (goals.kcal != null) {
                Text(
                    "of " + goals.kcal.roundToInt().toString(),
                    color = N2Muted,
                    fontSize = 8.sp
                )
            }
        }
    }
}

@Composable
private fun N2MacroValue(
    label: String,
    value: Double,
    target: Double?,
    accent: Color,
    complete: Boolean
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(8.dp).background(
                if (complete) accent else accent.copy(alpha = .55f),
                CircleShape
            )
        )
        Text(
            label,
            color = N2Muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            (if (complete) "" else "~") +
                if (target != null) n2One(value) + " / " + n2One(target) + " g"
                else n2One(value) + " g",
            color = N2Ink,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun N2WeekChart(
    days: List<N2Day>,
    target: Double?,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit
) {
    val loggedDays = days.count { it.entries.isNotEmpty() }
    val completeDays = days.filter { it.entries.isNotEmpty() && it.kcalComplete }
    val average = completeDays.map { it.kcal }.average().takeIf { !it.isNaN() }

    Column(
        Modifier.fillMaxWidth()
            .background(N2RowBg, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Recent days", color = N2Ink, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text(
                    loggedDays.toString() + " of 7 days logged",
                    color = N2Muted,
                    fontSize = 8.sp
                )
            }
            if (average != null) {
                Text(
                    average.roundToInt().toString() + " kcal avg",
                    color = N2Blue,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            days.forEachIndexed { index, item ->
                val date = selectedDate.minusDays((days.lastIndex - index).toLong())
                val selected = date == selectedDate
                val hasData = item.entries.isNotEmpty()
                val kcalLabel = when {
                    !hasData -> "—"
                    item.kcalComplete -> item.kcal.roundToInt().toString()
                    else -> "~" + item.kcal.roundToInt().toString()
                }

                Column(
                    Modifier.weight(1f)
                        .background(
                            if (selected) N2Blue.copy(alpha = .18f) else N2Surface,
                            RoundedCornerShape(13.dp)
                        )
                        .border(
                            1.dp,
                            if (selected) N2Blue else N2Border,
                            RoundedCornerShape(13.dp)
                        )
                        .superhumanClickable { onSelectDate(date) }
                        .padding(vertical = 9.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                        color = if (selected) N2Blue else N2Muted,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        date.dayOfMonth.toString(),
                        color = N2Ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        kcalLabel,
                        color = when {
                            selected -> N2Blue
                            !hasData -> N2Muted
                            !item.kcalComplete -> N2Amber
                            else -> N2Ink
                        },
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        if (target != null && target > 0.0 && days.any { it.entries.isNotEmpty() }) {
            Text(
                "Tap a day to view its diary · kcal values shown below each date",
                color = N2Muted,
                fontSize = 8.sp
            )
        } else {
            Text(
                "Tap a day to view its diary",
                color = N2Muted,
                fontSize = 8.sp
            )
        }
    }
}

@Composable
private fun N2LogCard(
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    showManualBarcode: Boolean,
    onToggleManualBarcode: () -> Unit,
    barcode: String,
    onBarcodeChange: (String) -> Unit,
    lookingUp: Boolean,
    onSearch: () -> Unit,
    onScan: () -> Unit,
    onPhoto: () -> Unit,
    onBarcodeLookup: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(24.dp)).border(1.dp, N2Border, RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(SuperhumanLayout.contentGap)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Add food", color = N2Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("Search foods, scan packaging or import a barcode photo.", color = N2Muted, fontSize = 10.sp)
        }
        OutlinedTextField(query, onQueryChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Food, brand or meal") })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SuperhumanLayout.compactGap)) {
            N2IconActionButton(
                iconRes = R.drawable.tabler_search,
                label = if (searching) "Searching" else "Search",
                accent = N2Green,
                modifier = Modifier.weight(1f),
                enabled = !searching,
                onClick = onSearch
            )
            N2IconActionButton(
                iconRes = R.drawable.tabler_barcode,
                label = "Barcode",
                accent = N2Blue,
                modifier = Modifier.weight(1f),
                enabled = true,
                onClick = onScan
            )
            N2IconActionButton(
                iconRes = R.drawable.tabler_camera,
                label = "Photo",
                accent = N2Purple,
                modifier = Modifier.weight(1f),
                enabled = true,
                onClick = onPhoto
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                if (showManualBarcode) "Hide manual barcode" else "Enter barcode manually",
                color = N2Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.superhumanClickable(onClick = onToggleManualBarcode)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        if (showManualBarcode) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(barcode, onBarcodeChange, Modifier.weight(1f), singleLine = true, label = { Text("EAN / UPC / GTIN") })
                N2Button(if (lookingUp) "…" else "Lookup", N2Blue, Modifier.width(84.dp), !lookingUp, onBarcodeLookup)
            }
        }
    }
}

@Composable
private fun N2IconActionButton(
    iconRes: Int,
    label: String,
    accent: Color,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier.height(88.dp)
            .background(accent.copy(alpha = if (enabled) .13f else .05f), RoundedCornerShape(16.dp))
            .border(1.dp, accent.copy(alpha = if (enabled) .30f else .12f), RoundedCornerShape(16.dp))
            .superhumanClickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            color = if (enabled) N2Ink else N2Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun N2Button(label: String, accent: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier.background(accent.copy(alpha = if (enabled) 1f else .45f), RoundedCornerShape(15.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun N2FoodSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    foods: List<NativeFood>,
    searching: Boolean,
    status: String,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSelect: (NativeFood) -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .background(N2Bg)
            .padding(horizontal = SuperhumanLayout.pageHorizontal, vertical = SuperhumanLayout.pageVertical)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(SuperhumanLayout.iconTouchTarget)
                    .background(N2Surface, RoundedCornerShape(14.dp))
                    .border(1.dp, N2Border, RoundedCornerShape(14.dp))
                    .superhumanClickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.tabler_arrow_left),
                    contentDescription = "Back",
                    tint = N2Navy,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Search foods", color = N2Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Choose a food to return to your diary.", color = N2Muted, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Food or brand") }
            )
            N2Button(
                if (searching) "…" else "Search",
                N2Green,
                Modifier.width(88.dp),
                !searching,
                onSearch
            )
        }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(status, color = N2Muted, fontSize = 10.sp)
        }

        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Results", color = N2Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
            if (foods.isNotEmpty()) {
                Text(
                    foods.size.toString() + if (foods.size == 1) " match" else " matches",
                    color = N2Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(7.dp))

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            foods.take(8).forEach { food ->
                N2FoodSearchStrip(food = food, onSelect = { onSelect(food) })
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun N2FoodSearchStrip(food: NativeFood, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(N2Surface, RoundedCornerShape(14.dp))
            .border(1.dp, N2Border, RoundedCornerShape(14.dp))
            .superhumanClickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                food.name,
                color = N2Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Text(
                buildString {
                    if (food.brand.isNotBlank()) append(food.brand).append(" · ")
                    append(FoodEvidenceEngine.userFacingSourceLabel(food))
                },
                color = N2Muted,
                fontSize = 9.sp,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                if (food.kcalKnown) food.kcal.roundToInt().toString() + " kcal" else "— kcal",
                color = N2Ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                (if (food.proteinKnown) n2One(food.protein) + "P" else "—P") + " · " +
                    (if (food.carbsKnown) n2One(food.carbs) + "C" else "—C") + " · " +
                    (if (food.fatKnown) n2One(food.fat) + "F" else "—F"),
                color = N2Muted,
                fontSize = 9.sp,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(8.dp))
        Text("›", color = N2Blue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun N2AddFoodCard(
    food: NativeFood,
    portion: String,
    onPortionChange: (String) -> Unit,
    portionUnit: FoodUnit,
    onPortionUnitChange: (FoodUnit) -> Unit,
    meal: String,
    onMealChange: (String) -> Unit,
    onFoodCorrected: (NativeFood) -> Unit,
    onRemoveCorrection: () -> Unit,
    onAdd: () -> Unit
) {
    val amount = portion.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val conversion = FoodUnitSystem.convert(food, amount, portionUnit)
    val factor = conversion?.factor ?: 0.0
    val availableUnits = FoodUnitSystem.availableUnits(food)
    val context = LocalContext.current
    val correctionScope = rememberCoroutineScope()
    var editingNutrition by remember(food.id, food.sourceRevision) { mutableStateOf(false) }
    var kcalText by remember(food.id, food.sourceRevision) {
        mutableStateOf(if (food.kcalKnown) n2CorrectionText(food.kcal) else "")
    }
    var proteinText by remember(food.id, food.sourceRevision) {
        mutableStateOf(if (food.proteinKnown) n2CorrectionText(food.protein) else "")
    }
    var carbsText by remember(food.id, food.sourceRevision) {
        mutableStateOf(if (food.carbsKnown) n2CorrectionText(food.carbs) else "")
    }
    var fatText by remember(food.id, food.sourceRevision) {
        mutableStateOf(if (food.fatKnown) n2CorrectionText(food.fat) else "")
    }
    var fibreText by remember(food.id, food.sourceRevision) {
        mutableStateOf(if (food.fibreKnown) n2CorrectionText(food.fibre) else "")
    }
    var sugarText by remember(food.id, food.sourceRevision) {
        mutableStateOf(if (food.sugarKnown) n2CorrectionText(food.sugar) else "")
    }
    var saturatedFatText by remember(food.id, food.sourceRevision) {
        mutableStateOf(if (food.saturatedFatKnown) n2CorrectionText(food.saturatedFat) else "")
    }
    var saltText by remember(food.id, food.sourceRevision) {
        mutableStateOf(if (food.saltKnown) n2CorrectionText(food.salt) else "")
    }
    var sodiumText by remember(food.id, food.sourceRevision) {
        mutableStateOf(if (food.sodiumKnown) n2CorrectionText(food.sodiumMg) else "")
    }
    Column(
        Modifier.fillMaxWidth().background(N2SoftGreen, RoundedCornerShape(24.dp)).border(1.dp, N2Green.copy(alpha = .24f), RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Column {
            Text(food.name, color = N2Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
            if (food.hasVerifiedEnglishName && food.originalName.isNotBlank() && !food.originalName.equals(food.name, ignoreCase = true)) {
                Text(food.originalName, color = N2Muted, fontSize = 10.sp, maxLines = 1)
            }
            Text(food.brand.ifBlank { FoodEvidenceEngine.userFacingSourceLabel(food) }, color = N2Muted, fontSize = 10.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            N2FoodStat(if (food.kcalKnown) (food.kcal * factor).roundToInt().toString() else "—", "kcal", Modifier.weight(1f))
            N2FoodStat(if (food.proteinKnown) "${n2One(food.protein * factor)}g" else "—", "protein", Modifier.weight(1f))
            N2FoodStat(if (food.carbsKnown) "${n2One(food.carbs * factor)}g" else "—", "carbs", Modifier.weight(1f))
            N2FoodStat(if (food.fatKnown) "${n2One(food.fat * factor)}g" else "—", "fat", Modifier.weight(1f))
        }
        OutlinedTextField(
            portion,
            onPortionChange,
            Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Amount (${portionUnit.symbol})") }
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            availableUnits.forEach { unit ->
                val active = portionUnit == unit
                Box(
                    Modifier.background(if (active) N2Blue else N2Surface, RoundedCornerShape(13.dp))
                        .border(1.dp, if (active) N2Blue else N2Border, RoundedCornerShape(13.dp))
                        .superhumanClickable { onPortionUnitChange(unit) }
                        .padding(horizontal = 13.dp, vertical = 9.dp)
                ) {
                    Text(
                        unit.symbol,
                        color = if (active) Color.White else N2Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        val unitContext = buildList {
            if (food.servingQuantity != null && food.servingQuantityUnit != null) {
                add("1 serving = " + FoodUnitSystem.formatAmount(food.servingQuantity, food.servingQuantityUnit))
            }
            if (food.productQuantity != null && food.productQuantityUnit != null) {
                add("1 package = " + FoodUnitSystem.formatAmount(food.productQuantity, food.productQuantityUnit))
            }
            if (food.densityGPerMl != null && food.densityApproximate) {
                add("mass/volume conversion uses approximate density")
            }
        }
        if (unitContext.isNotEmpty()) {
            Text(unitContext.joinToString(" · "), color = N2Muted, fontSize = 9.sp)
        }
        FoodEvidenceEngine.userFacingDataQualityMessage(food)?.let { message ->
            Text(
                message,
                color = N2Amber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            if (editingNutrition) "Hide nutrition correction" else "Correct nutrition data",
            color = N2Blue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.superhumanClickable { editingNutrition = !editingNutrition }.padding(vertical = 4.dp)
        )

        if (editingNutrition) {
            Column(
                Modifier.fillMaxWidth()
                    .background(N2Surface, RoundedCornerShape(16.dp))
                    .border(1.dp, N2Border, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Enter values for the food's " + FoodUnitSystem.describeBasis(food) + ". Leave a field blank if it is unknown.",
                    color = N2Muted,
                    fontSize = 9.sp
                )
                val correctionFields = listOf(
                    Triple("Calories", kcalText, { value: String -> kcalText = value }),
                    Triple("Protein (g)", proteinText, { value: String -> proteinText = value }),
                    Triple("Carbs (g)", carbsText, { value: String -> carbsText = value }),
                    Triple("Fat (g)", fatText, { value: String -> fatText = value }),
                    Triple("Fibre (g)", fibreText, { value: String -> fibreText = value }),
                    Triple("Sugars (g)", sugarText, { value: String -> sugarText = value }),
                    Triple("Saturated fat (g)", saturatedFatText, { value: String -> saturatedFatText = value }),
                    Triple("Salt (g)", saltText, { value: String -> saltText = value }),
                    Triple("Sodium (mg)", sodiumText, { value: String -> sodiumText = value })
                )
                correctionFields.forEach { (label, value, setter) ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { raw ->
                            setter(raw.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.replace(',', '.').take(8))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(label) }
                    )
                }

                val correctionValues = listOf(
                    kcalText, proteinText, carbsText, fatText, fibreText, sugarText,
                    saturatedFatText, saltText, sodiumText
                )
                val invalidCorrection = correctionValues.any { raw ->
                    raw.isNotBlank() && (raw.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } != true)
                }

                N2Button(
                    "Save correction",
                    N2Blue,
                    Modifier.fillMaxWidth(),
                    !invalidCorrection
                ) {
                    if (invalidCorrection) return@N2Button
                    val corrected = food.copy(
                        kcal = kcalText.toDoubleOrNull() ?: 0.0,
                        kcalKnown = kcalText.isNotBlank(),
                        protein = proteinText.toDoubleOrNull() ?: 0.0,
                        proteinKnown = proteinText.isNotBlank(),
                        carbs = carbsText.toDoubleOrNull() ?: 0.0,
                        carbsKnown = carbsText.isNotBlank(),
                        fat = fatText.toDoubleOrNull() ?: 0.0,
                        fatKnown = fatText.isNotBlank(),
                        fibre = fibreText.toDoubleOrNull() ?: 0.0,
                        fibreKnown = fibreText.isNotBlank(),
                        sugar = sugarText.toDoubleOrNull() ?: 0.0,
                        sugarKnown = sugarText.isNotBlank(),
                        saturatedFat = saturatedFatText.toDoubleOrNull() ?: 0.0,
                        saturatedFatKnown = saturatedFatText.isNotBlank(),
                        salt = saltText.toDoubleOrNull() ?: 0.0,
                        saltKnown = saltText.isNotBlank(),
                        sodiumMg = sodiumText.toDoubleOrNull() ?: 0.0,
                        sodiumKnown = sodiumText.isNotBlank(),
                        // This editor changes common label fields only. Preserve source micronutrients.
                        micronutrients = emptyMap(),
                        nutritionIntegrityWarning = null,
                        sourceWarnings = emptyList()
                    )
                    correctionScope.launch {
                        val applied = withContext(Dispatchers.IO) {
                            FoodNutritionOverrideStore.save(context, food, corrected)
                            FoodNutritionOverrideStore.applyIfAttached(food)
                        }
                        onFoodCorrected(applied)
                        editingNutrition = false
                    }
                }

                if (food.sourceType == FoodDataSourceType.USER_CORRECTED) {
                    Text(
                        "Use original source data",
                        color = N2Amber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.superhumanClickable {
                            editingNutrition = false
                            onRemoveCorrection()
                        }.padding(vertical = 5.dp)
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            n2Meals.forEach { item ->
                val active = meal == item
                Box(
                    Modifier.background(if (active) N2Blue else N2Surface, RoundedCornerShape(13.dp))
                        .border(1.dp, if (active) N2Blue else N2Border, RoundedCornerShape(13.dp))
                        .clickable { onMealChange(item) }.padding(horizontal = 14.dp, vertical = 9.dp)
                ) { Text(item, color = if (active) Color.White else N2Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
        }
        N2Button("Add to $meal", N2Green, Modifier.fillMaxWidth(), true, onAdd)
    }
}

@Composable
private fun N2FoodStat(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(N2Surface, RoundedCornerShape(13.dp)).padding(9.dp)) {
        Text(value, color = N2Navy, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(label, color = N2Muted, fontSize = 8.sp)
    }
}

@Composable
private fun N2QuickRepeat(entries: List<N2Entry>, onRepeat: (N2Entry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Quick repeat", color = N2Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text("Recent foods", color = N2Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            entries.distinctBy { it.foodId }.take(4).forEach { entry ->
                Column(
                    Modifier.width(148.dp).background(N2Surface, RoundedCornerShape(16.dp))
                        .border(1.dp, N2Border, RoundedCornerShape(16.dp))
                        .clickable { onRepeat(entry) }.padding(SuperhumanLayout.compactCardPadding)
                ) {
                    Text(entry.name, color = N2Ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (if (entry.kcalKnown) entry.kcal.roundToInt().toString() + " kcal" else "— kcal") +
                            " · " + n2One(entry.amount) + " " + entry.amountUnit,
                        color = N2Muted,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.height(7.dp))
                    Text("+ Add again", color = N2Blue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun N2UndoBar(name: String, onUndo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(N2Ink, RoundedCornerShape(15.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name + " removed", color = Color.White, fontSize = 9.sp, modifier = Modifier.weight(1f), maxLines = 1)
        Text(
            "UNDO",
            color = N2Cyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.clickable(onClick = onUndo).padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun N2Diary(
    day: N2Day,
    onDuplicate: (N2Entry) -> Unit,
    onRenameGroup: (List<N2Entry>, String) -> Unit,
    onRemove: (N2Entry) -> Unit
) {
    if (day.entries.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(22.dp))
                .border(1.dp, N2Border, RoundedCornerShape(22.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Nothing logged yet", color = N2Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text("Search, scan or add a recent food above.", color = N2Muted, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Diary", color = N2Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(
                day.entries.size.toString() + " items · " +
                    (if (day.kcalComplete) "" else "~") + day.kcal.roundToInt().toString() + " kcal",
                color = N2Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        n2Meals.forEach { meal ->
            val entries = day.entries.filter { it.meal.equals(meal, ignoreCase = true) }
            if (entries.isNotEmpty()) N2MealCard(meal, entries, onDuplicate, onRenameGroup, onRemove)
        }
        val other = day.entries.filter { e -> n2Meals.none { it.equals(e.meal, ignoreCase = true) } }
        if (other.isNotEmpty()) N2MealCard("Other", other, onDuplicate, onRenameGroup, onRemove)
    }
}

@Composable
private fun N2MealCard(
    meal: String,
    entries: List<N2Entry>,
    onDuplicate: (N2Entry) -> Unit,
    onRenameGroup: (List<N2Entry>, String) -> Unit,
    onRemove: (N2Entry) -> Unit
) {
    var expanded by remember(meal, entries.size) { mutableStateOf(true) }
    val mealKcal = entries.sumOf { it.kcal }
    val mealProtein = entries.sumOf { it.protein }
    val mealKcalComplete = entries.all { it.kcalKnown }
    val mealProteinComplete = entries.all { it.proteinKnown }

    Column(
        Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(20.dp))
            .border(1.dp, N2Border, RoundedCornerShape(20.dp)).padding(SuperhumanLayout.cardPadding),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().superhumanClickable { expanded = !expanded }.padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(meal, color = N2Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(
                    entries.size.toString() + " items · " +
                        (if (mealProteinComplete) "" else "~") + n2One(mealProtein) + " g protein",
                    color = N2Muted,
                    fontSize = 10.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    (if (mealKcalComplete) "" else "~") + mealKcal.roundToInt().toString() + " kcal",
                    color = N2Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                Box(
                    Modifier.size(34.dp).background(N2RowBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (expanded) R.drawable.tabler_chevron_up else R.drawable.tabler_chevron_down
                        ),
                        contentDescription = if (expanded) "Collapse meal" else "Expand meal",
                        tint = N2Muted,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        if (expanded) {
            val grouped = entries.sortedBy { it.timestamp }.groupBy { entry ->
                entry.mealGroupId.ifBlank { "entry:" + entry.id }
            }
            grouped.values.forEach { groupEntries ->
                val groupName = groupEntries.firstOrNull()?.mealGroupName.orEmpty()
                if (groupEntries.size > 1 && groupName.isNotBlank()) {
                    val groupKcal = groupEntries.sumOf { it.kcal }
                    val groupKcalComplete = groupEntries.all { it.kcalKnown }
                    var editingGroupName by remember(groupEntries.first().mealGroupId, groupName) { mutableStateOf(false) }
                    var groupNameDraft by remember(groupEntries.first().mealGroupId, groupName) { mutableStateOf(groupName) }

                    if (editingGroupName) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = groupNameDraft,
                                onValueChange = { groupNameDraft = it.take(80) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Meal name") }
                            )
                            Text(
                                "Save",
                                color = N2Blue,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.clickable {
                                    val trimmed = groupNameDraft.trim()
                                    if (trimmed.isNotBlank()) {
                                        onRenameGroup(groupEntries, trimmed)
                                        editingGroupName = false
                                    }
                                }.padding(8.dp)
                            )
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = 3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(groupName, color = N2Ink, fontSize = 14.sp, fontWeight = FontWeight.Black)
                                    Box(
                                        Modifier.size(32.dp).superhumanClickable { editingGroupName = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.tabler_pencil),
                                            contentDescription = "Edit meal name",
                                            tint = N2Blue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(groupEntries.size.toString() + " ingredients", color = N2Muted, fontSize = 9.sp)
                            }
                            Text(
                                (if (groupKcalComplete) "" else "~") + groupKcal.roundToInt().toString() + " kcal",
                                color = N2Muted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                groupEntries.forEach { entry ->
                    Row(
                        Modifier.fillMaxWidth().background(N2RowBg, RoundedCornerShape(13.dp))
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, color = N2Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Text(
                                n2One(entry.amount) + " " + entry.amountUnit + " · " +
                                    (if (entry.kcalKnown) entry.kcal.roundToInt().toString() + " kcal" else "— kcal") + " · " +
                                    (if (entry.proteinKnown) n2One(entry.protein) + "P" else "—P") + " · " +
                                    (if (entry.carbsKnown) n2One(entry.carbs) + "C" else "—C") + " · " +
                                    (if (entry.fatKnown) n2One(entry.fat) + "F" else "—F"),
                                color = N2Muted,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                        Text(
                            "＋",
                            color = N2Blue,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.superhumanClickable { onDuplicate(entry) }.padding(8.dp)
                        )
                        Text(
                            "×",
                            color = N2Muted,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.superhumanClickable { onRemove(entry) }.padding(8.dp)
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
        }
    }
}

@Composable
private fun N2Nutrients(
    day: N2Day,
    showAll: Boolean,
    range: N2Range,
    onRangeChange: (N2Range) -> Unit,
    onToggleAll: () -> Unit
) {
    val entryCount = day.entries.size
    val withMicro = day.entries.count { it.micronutrientCount > 0 }
    val coverage = if (entryCount == 0) 0 else withMicro * 100 / entryCount
    val byId = day.micros.associateBy { it.id }

    Column(verticalArrangement = Arrangement.spacedBy(SuperhumanLayout.sectionGap)) {
        Row(
            Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(17.dp))
                .border(1.dp, N2Border, RoundedCornerShape(17.dp)).padding(SuperhumanLayout.segmentedPadding),
            horizontalArrangement = Arrangement.spacedBy(SuperhumanLayout.segmentedGap)
        ) {
            N2Range.entries.forEach { item ->
                val active = item == range
                Box(
                    Modifier.weight(1f)
                        .background(if (active) N2Blue else Color.Transparent, RoundedCornerShape(13.dp))
                        .clickable { onRangeChange(item) }
                        .height(SuperhumanLayout.controlHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.label, color = if (active) Color.White else N2Muted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(24.dp)).border(1.dp, N2Border, RoundedCornerShape(24.dp)).padding(SuperhumanLayout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                if (range == N2Range.DAY) "Nutrient coverage" else "Nutrient coverage · " + range.label,
                color = N2Ink,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("$coverage%", color = N2Navy, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("foods with micronutrient data", color = N2Muted, fontSize = 9.sp, modifier = Modifier.padding(bottom = 5.dp))
            }
            N2Progress(coverage.toDouble(), 100.0, if (coverage >= 75) N2Green else N2Blue)
            Text("Missing food data stays unknown — never zero.", color = N2Muted, fontSize = 9.sp)
        }

        val gaugeNutrients = n2FocusIds.mapNotNull { byId[it] }.take(4)
        if (gaugeNutrients.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(SuperhumanLayout.contentGap)
            ) {
                gaugeNutrients.forEach { micro -> N2NutrientGauge(micro, range.days.toInt()) }
            }
        }

        Column(
            Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(24.dp)).border(1.dp, N2Border, RoundedCornerShape(24.dp)).padding(SuperhumanLayout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(SuperhumanLayout.compactGap)
        ) {
            Text(
                if (range == N2Range.DAY) "Focus today" else "Average per day",
                color = N2Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                if (range == N2Range.DAY) "Key nutrients from the foods you logged." else "Daily average across the selected window.",
                color = N2Muted,
                fontSize = 9.sp
            )

            val focus = n2FocusIds.map { id -> id to byId[id] }
                .sortedBy { (id, micro) ->
                    if (micro == null) 2.0 else n2RefById[id]?.let { micro.value / it.value } ?: 1.5
                }
                .take(4)
            focus.forEach { (id, micro) ->
                val ref = n2RefById[id] ?: return@forEach
                if (micro == null) N2UnknownNutrient(ref) else N2NutrientRow(micro, entryCount, range.days.toInt())
            }
        }

        if (day.entries.isNotEmpty()) {
            N2FoodContributors(day.entries)
        }

        if (showAll) {
            Column(
                Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(24.dp)).border(1.dp, N2Border, RoundedCornerShape(24.dp)).padding(SuperhumanLayout.cardPadding),
                verticalArrangement = Arrangement.spacedBy(SuperhumanLayout.compactGap)
            ) {
                Text("All tracked nutrients", color = N2Ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                if (day.micros.isEmpty()) Text("No micronutrient values are available yet.", color = N2Muted, fontSize = 10.sp)
                else day.micros.sortedBy { it.label }.forEach { N2NutrientRow(it, entryCount, range.days.toInt()) }
            }
        }

        Box(
            Modifier.fillMaxWidth().background(N2SoftBlue, RoundedCornerShape(16.dp)).clickable(onClick = onToggleAll).padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(if (showAll) "Show less" else "Show all nutrients", color = N2Blue, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Text("Percentages use EU adult food-labelling reference values, not personalised medical targets.", color = N2Muted, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 3.dp))
    }
}

@Composable
private fun N2NutrientGauge(micro: N2Micro, referenceDays: Int) {
    val ref = n2RefById[micro.id]
    val value = micro.value / referenceDays.coerceAtLeast(1).toDouble()
    val fraction = ref?.let { (value / it.value).toFloat().coerceIn(0f, 1f) } ?: 0f
    val accent = when {
        ref == null -> N2Purple
        fraction < .5f -> N2Amber
        fraction < .85f -> N2Blue
        else -> N2Green
    }
    Column(
        Modifier.width(116.dp).height(148.dp)
            .background(N2Surface, RoundedCornerShape(18.dp))
            .border(1.dp, N2Border, RoundedCornerShape(18.dp))
            .padding(SuperhumanLayout.compactCardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                drawArc(N2Border, -90f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                if (fraction > 0f) {
                    drawArc(accent, -90f, 360f * fraction, false, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Text((fraction * 100).roundToInt().toString() + "%", color = N2Ink, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
            Text(
                micro.label,
                color = N2Ink,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                textAlign = TextAlign.Center,
                lineHeight = 11.sp
            )
        }
        Text(n2Pretty(value) + " " + micro.unit, color = N2Muted, fontSize = 8.sp)
    }
}

@Composable
private fun N2FoodContributors(entries: List<N2Entry>) {
    val top = entries.sortedByDescending { it.kcal }.take(3)
    val max = top.maxOfOrNull { it.kcal }?.coerceAtLeast(1.0) ?: 1.0
    Column(
        Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(22.dp))
            .border(1.dp, N2Border, RoundedCornerShape(22.dp)).padding(SuperhumanLayout.cardPadding),
        verticalArrangement = Arrangement.spacedBy(SuperhumanLayout.contentGap)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Top contributors", color = N2Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("Calories", color = N2Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        top.forEach { entry ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(entry.name, color = N2Ink, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(entry.kcal.roundToInt().toString(), color = N2Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.fillMaxWidth().height(5.dp).background(N2Border, CircleShape)) {
                        Box(
                            Modifier.fillMaxWidth((entry.kcal / max).toFloat().coerceIn(0f, 1f))
                                .height(5.dp).background(N2Cyan, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun N2UnknownNutrient(ref: N2Reference) {
    Row(Modifier.fillMaxWidth().background(N2RowBg, RoundedCornerShape(14.dp)).padding(SuperhumanLayout.compactCardPadding), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(ref.label, color = N2Ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text("No reliable value in today's logged foods", color = N2Muted, fontSize = 9.sp)
        }
        Text("Unknown", color = N2Purple, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun N2NutrientRow(micro: N2Micro, entryCount: Int, referenceDays: Int = 1) {
    val ref = n2RefById[micro.id]
    val divisor = referenceDays.coerceAtLeast(1).toDouble()
    val displayValue = micro.value / divisor
    val pct = ref?.let { (displayValue / it.value * 100.0).roundToInt() }
    val dataCoverage = if (entryCount > 0) (micro.knownEntries * 100 / entryCount).coerceIn(0, 100) else 0
    val accent = when {
        pct == null -> N2Purple
        pct < 50 -> N2Amber
        pct < 85 -> N2Blue
        else -> N2Green
    }
    Column(Modifier.fillMaxWidth().background(N2RowBg, RoundedCornerShape(14.dp)).padding(SuperhumanLayout.compactCardPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(micro.label, color = N2Ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    n2Pretty(displayValue) + " " + micro.unit + " · data from " + dataCoverage + "% of foods",
                    color = N2Muted,
                    fontSize = 9.sp
                )
            }
            Text(pct?.let { "$it%" } ?: "Tracked", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        ref?.let { N2Progress(displayValue, it.value, accent) }
    }
}

@Composable
private fun N2Progress(value: Double, target: Double, accent: Color) {
    val f = if (target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(N2Border)) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f).height(6.dp).background(accent))
    }
}

@Composable
private fun N2Insights(
    day: N2Day,
    goals: N2Goals,
    editingTargets: Boolean,
    onToggleTargets: () -> Unit,
    onSaveTargets: (N2Goals) -> Unit
) {
    val entryCount = day.entries.size
    val microCoverage = if (entryCount == 0) 0 else day.entries.count { it.micronutrientCount > 0 } * 100 / entryCount

    Column(verticalArrangement = Arrangement.spacedBy(SuperhumanLayout.sectionGap)) {
        Column(
            Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(24.dp)).border(1.dp, N2Border, RoundedCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("Today", color = N2Ink, fontSize = 19.sp, fontWeight = FontWeight.Black)
            N2Insight(
                if (entryCount == 0) "Start with one meal" else "$entryCount foods logged",
                if (entryCount == 0) "Your food diary is the evidence layer for future cross-module patterns."
                else (if (day.kcalComplete) "" else "~") + "${day.kcal.roundToInt()} kcal · " +
                    (if (day.proteinComplete) "" else "~") + "${n2One(day.protein)} g protein · " +
                    (if (day.fibreComplete) "" else "~") + "${n2One(day.fibre)} g fibre",
                N2Blue
            )
            N2Insight(
                if (microCoverage >= 75) "Micronutrient data present" else "Micronutrient data is sparse",
                "$microCoverage% of today's foods contain at least one reported micronutrient value. This does not mean every micronutrient is known.",
                if (microCoverage >= 75) N2Green else N2Amber
            )
        }

        Column(
            Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(24.dp)).border(1.dp, N2Border, RoundedCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(SuperhumanLayout.contentGap)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Daily targets", color = N2Ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text(if (goals.kcal == null) "Optional · nothing assumed" else "Your own targets", color = N2Muted, fontSize = 9.sp)
                }
                Text(if (editingTargets) "Close" else "Edit", color = N2Blue, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable(onClick = onToggleTargets).padding(8.dp))
            }

            if (!editingTargets) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    N2TargetSummary("Calories", goals.kcal?.roundToInt()?.toString() ?: "—", "kcal", Modifier.weight(1f))
                    N2TargetSummary("Protein", goals.protein?.let(::n2One) ?: "—", "g", Modifier.weight(1f))
                    N2TargetSummary("Fibre", goals.fibre?.let(::n2One) ?: "—", "g", Modifier.weight(1f))
                }
            } else {
                N2TargetEditor(goals, onSaveTargets)
            }
        }

        Column(Modifier.fillMaxWidth().background(N2SoftBlue, RoundedCornerShape(20.dp)).padding(SuperhumanLayout.cardPadding)) {
            Text("Project Superhuman view", color = N2Blue, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(
                "Over time, nutrition can be compared with sleep, training and recovery. Those relationships should appear here only when there is enough data and with a clear confidence level.",
                color = N2Muted, fontSize = 9.sp, lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun N2Insight(title: String, detail: String, accent: Color) {
    Row(Modifier.fillMaxWidth().background(N2RowBg, RoundedCornerShape(14.dp)).padding(SuperhumanLayout.compactCardPadding), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(6.dp).height(34.dp).background(accent, RoundedCornerShape(99.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = N2Ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text(detail, color = N2Muted, fontSize = 9.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun N2TargetSummary(label: String, value: String, unit: String, modifier: Modifier) {
    Column(
        modifier.height(68.dp).background(N2RowBg, RoundedCornerShape(14.dp))
            .padding(SuperhumanLayout.compactCardPadding),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label.uppercase(), color = N2Muted, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .6.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = N2Navy, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(3.dp))
            Text(unit, color = N2Muted, fontSize = 8.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun N2TargetEditor(goals: N2Goals, onSave: (N2Goals) -> Unit) {
    var kcal by remember(goals) { mutableStateOf(goals.kcal?.let(::n2Editable) ?: "") }
    var protein by remember(goals) { mutableStateOf(goals.protein?.let(::n2Editable) ?: "") }
    var carbs by remember(goals) { mutableStateOf(goals.carbs?.let(::n2Editable) ?: "") }
    var fat by remember(goals) { mutableStateOf(goals.fat?.let(::n2Editable) ?: "") }
    var fibre by remember(goals) { mutableStateOf(goals.fibre?.let(::n2Editable) ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(SuperhumanLayout.compactGap)) {
        OutlinedTextField(kcal, { kcal = n2GoalText(it) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Calories (kcal)") })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(protein, { protein = n2GoalText(it) }, Modifier.weight(1f), singleLine = true, label = { Text("Protein (g)") })
            OutlinedTextField(carbs, { carbs = n2GoalText(it) }, Modifier.weight(1f), singleLine = true, label = { Text("Carbs (g)") })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(fat, { fat = n2GoalText(it) }, Modifier.weight(1f), singleLine = true, label = { Text("Fat (g)") })
            OutlinedTextField(fibre, { fibre = n2GoalText(it) }, Modifier.weight(1f), singleLine = true, label = { Text("Fibre (g)") })
        }
        N2Button("Save targets", N2Blue, Modifier.fillMaxWidth(), true) {
            onSave(
                N2Goals(
                    kcal.toDoubleOrNull()?.takeIf { it > 0 },
                    protein.toDoubleOrNull()?.takeIf { it > 0 },
                    carbs.toDoubleOrNull()?.takeIf { it > 0 },
                    fat.toDoubleOrNull()?.takeIf { it > 0 },
                    fibre.toDoubleOrNull()?.takeIf { it > 0 }
                )
            )
        }
    }
}

private suspend fun n2LoadGoals(): N2Goals = N2Goals(
    NativeDataHub.latest(HealthDomain.NUTRITION, "nutrition_goal_kcal")?.value?.takeIf { it > 0 },
    NativeDataHub.latest(HealthDomain.NUTRITION, "nutrition_goal_protein_g")?.value?.takeIf { it > 0 },
    NativeDataHub.latest(HealthDomain.NUTRITION, "nutrition_goal_carbs_g")?.value?.takeIf { it > 0 },
    NativeDataHub.latest(HealthDomain.NUTRITION, "nutrition_goal_fat_g")?.value?.takeIf { it > 0 },
    NativeDataHub.latest(HealthDomain.NUTRITION, "nutrition_goal_fibre_g")?.value?.takeIf { it > 0 }
)

private suspend fun n2SaveGoals(goals: N2Goals) {
    suspend fun save(metric: String, value: Double?, unit: String) {
        NativeDataHub.saveMetric(
            domain = HealthDomain.NUTRITION,
            metric = metric,
            value = value ?: 0.0,
            unit = unit,
            source = "native-nutrition-goals",
            metadata = mapOf("enabled" to (value != null).toString())
        )
    }
    save("nutrition_goal_kcal", goals.kcal, "kcal")
    save("nutrition_goal_protein_g", goals.protein, "g")
    save("nutrition_goal_carbs_g", goals.carbs, "g")
    save("nutrition_goal_fat_g", goals.fat, "g")
    save("nutrition_goal_fibre_g", goals.fibre, "g")
}

private fun n2BuildDay(rows: List<HealthValue>): N2Day {
    val foodRows = rows.filter { it.metric.startsWith("food_") }
    val anchorRows = foodRows.filter { it.metric == "food_entry" || it.metric == "food_kcal" }
    val groups = anchorRows.groupBy { row -> row.metadata["diaryEntryId"] ?: "legacy:${row.timestampEpochMs}:${row.metadata["foodId"].orEmpty()}" }

    fun metric(entryId: String, metric: String, fallback: String? = null): Double {
        val direct = foodRows.firstOrNull { row ->
            val id = row.metadata["diaryEntryId"] ?: "legacy:${row.timestampEpochMs}:${row.metadata["foodId"].orEmpty()}"
            id == entryId && row.metric == metric
        }?.value
        if (direct != null) return direct
        return fallback?.let { key -> groups[entryId]?.firstOrNull()?.metadata?.get(key)?.toDoubleOrNull() } ?: 0.0
    }

    val entries = groups.mapNotNull { (entryId, group) ->
        val anchor = group.firstOrNull { it.metric == "food_entry" }
            ?: group.maxByOrNull { it.timestampEpochMs }
            ?: return@mapNotNull null
        val kcalValue = metric(entryId, "food_kcal")
        N2Entry(
            id = entryId,
            foodId = anchor.metadata["foodId"].orEmpty(),
            timestamp = anchor.timestampEpochMs,
            name = anchor.metadata["name"] ?: "Food",
            amount = anchor.metadata["amount"]?.toDoubleOrNull()
                ?: anchor.metadata["grams"]?.toDoubleOrNull()
                ?: 100.0,
            amountUnit = anchor.metadata["amountUnit"]
                ?: if (anchor.metadata["grams"] != null) "g" else "g",
            meal = anchor.metadata["meal"] ?: "Other",
            kcal = kcalValue,
            kcalKnown = anchor.metadata["kcalKnown"]?.toBooleanStrictOrNull()
                ?: foodRows.any { it.metadata["diaryEntryId"] == entryId && it.metric == "food_kcal" },
            protein = metric(entryId, "food_protein"),
            proteinKnown = anchor.metadata["proteinKnown"]?.toBooleanStrictOrNull()
                ?: foodRows.any { it.metadata["diaryEntryId"] == entryId && it.metric == "food_protein" },
            carbs = metric(entryId, "food_carbs", "carbs"),
            carbsKnown = anchor.metadata["carbsKnown"]?.toBooleanStrictOrNull()
                ?: foodRows.any { it.metadata["diaryEntryId"] == entryId && it.metric == "food_carbs" },
            carbohydrateDefinition = anchor.metadata["carbohydrateDefinition"]
                ?.let { runCatching { CarbohydrateDefinition.valueOf(it) }.getOrNull() }
                ?: CarbohydrateDefinition.UNKNOWN,
            fat = metric(entryId, "food_fat", "fat"),
            fatKnown = anchor.metadata["fatKnown"]?.toBooleanStrictOrNull()
                ?: foodRows.any { it.metadata["diaryEntryId"] == entryId && it.metric == "food_fat" },
            fibre = metric(entryId, "food_fibre", "fibre"),
            fibreKnown = anchor.metadata["fibreKnown"]?.toBooleanStrictOrNull()
                ?: foodRows.any { it.metadata["diaryEntryId"] == entryId && it.metric == "food_fibre" },
            micronutrientCount = foodRows.count { row ->
                row.metadata["diaryEntryId"] == entryId && !row.metadata["nutrientId"].isNullOrBlank()
            },
            barcode = anchor.metadata["barcode"].orEmpty(),
            sourceName = anchor.metadata["sourceName"].orEmpty(),
            mealGroupId = anchor.metadata["mealGroupId"].orEmpty(),
            mealGroupName = anchor.metadata["mealGroupName"].orEmpty()
        )
    }.sortedByDescending { it.timestamp }

    val microRows = foodRows.filter { !it.metadata["nutrientId"].isNullOrBlank() }
    val micros = microRows.groupBy { it.metadata["nutrientId"].orEmpty() }.mapNotNull { (id, nutrientRows) ->
        if (id.isBlank()) return@mapNotNull null
        val ref = n2RefById[id]
        N2Micro(
            id = id,
            label = nutrientRows.firstOrNull()?.metadata?.get("nutrientLabel") ?: ref?.label ?: id.replace('_', ' ').replaceFirstChar { it.uppercase() },
            value = nutrientRows.sumOf { it.value },
            unit = nutrientRows.firstOrNull()?.unit ?: ref?.unit ?: "",
            knownEntries = nutrientRows.mapNotNull { it.metadata["diaryEntryId"] }.distinct().size
        )
    }

    return N2Day(
        kcal = entries.sumOf { it.kcal },
        protein = entries.sumOf { it.protein },
        carbs = entries.sumOf { it.carbs },
        fat = entries.sumOf { it.fat },
        fibre = entries.sumOf { it.fibre },
        entries = entries,
        micros = micros,
        records = rows
    )
}

private fun n2GoalText(value: String) = value.filter { it.isDigit() || it == '.' }.take(7)
private fun n2One(value: Double) = ((value * 10.0).roundToInt() / 10.0).toString()
private fun n2Pretty(value: Double) = if (value >= 100.0) value.roundToInt().toString() else n2One(value)
private fun n2Editable(value: Double) = if (value % 1.0 == 0.0) value.roundToInt().toString() else n2One(value)
private fun n2CorrectionText(value: Double) =
    if (value % 1.0 == 0.0) value.roundToInt().toString() else value.toString()
private fun String.n2FirstNumber(): Double? = Regex("([0-9]+(?:[.,][0-9]+)?)").find(this)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
