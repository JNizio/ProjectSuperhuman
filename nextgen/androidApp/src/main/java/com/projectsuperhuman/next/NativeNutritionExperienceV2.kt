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
import kotlinx.coroutines.launch
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
    val grams: Double,
    val meal: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fibre: Double,
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
)

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
    var meal by remember { mutableStateOf("Breakfast") }
    var searching by remember { mutableStateOf(false) }
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
        val product = NativeFoodCatalog.lookupBarcode(digits)
        lookingUp = false
        if (product == null) {
            status = "Product not found, or Open Food Facts is unavailable"
        } else {
            barcode = digits
            results = listOf(product)
            selected = product
            portion = product.servingSize.n2FirstNumber()?.let(::n2Editable) ?: "100"
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

    Column(
        Modifier.fillMaxSize().background(N2Bg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        scope.launch {
                            if (query.trim().length < 2) {
                                status = "Type at least two characters"
                                return@launch
                            }
                            searching = true
                            status = "Searching…"
                            val found = NativeFoodCatalog.search(context, query)
                            results = found.foods
                            selected = null
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

                if (results.isNotEmpty()) N2Results(results, selected?.id) { food ->
                    selected = food
                    portion = food.servingSize.n2FirstNumber()?.let(::n2Editable) ?: "100"
                }

                selected?.let { food ->
                    N2AddFoodCard(
                        food = food,
                        portion = portion,
                        onPortionChange = { portion = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                        meal = meal,
                        onMealChange = { meal = it },
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
                                NativeDataHub.saveFood(food, amount, meal, timestampEpochMs = timestamp)
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
            Modifier.width(42.dp).height(42.dp).background(N2Surface, RoundedCornerShape(14.dp))
                .border(1.dp, N2Border, RoundedCornerShape(14.dp)).superhumanClickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Text("←", color = N2Navy, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
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
    Row(
        Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(16.dp))
            .border(1.dp, N2Border, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("‹", color = N2Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onPrevious).padding(horizontal = 10.dp, vertical = 2.dp))
        Text(
            if (date == today) "Today" else date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)),
            color = N2Ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.clickable(onClick = onToday).padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Text(
            "›",
            color = if (date.isBefore(today)) N2Ink else N2Muted.copy(alpha = .35f),
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(enabled = date.isBefore(today), onClick = onNext)
                .padding(horizontal = 10.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun N2Tabs(view: N2View, onChange: (N2View) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(18.dp)).border(1.dp, N2Border, RoundedCornerShape(18.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        N2Tab("Diary", view == N2View.DIARY, Modifier.weight(1f)) { onChange(N2View.DIARY) }
        N2Tab("Nutrients", view == N2View.NUTRIENTS, Modifier.weight(1f)) { onChange(N2View.NUTRIENTS) }
        N2Tab("Insights", view == N2View.INSIGHTS, Modifier.weight(1f)) { onChange(N2View.INSIGHTS) }
    }
}

@Composable
private fun N2Tab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(if (selected) N2Blue else Color.Transparent, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
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
        Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(24.dp))
            .border(1.dp, N2Border, RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            N2CalorieRing(day.kcal, goals.kcal)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Today's intake", color = N2Ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                N2MacroBar("Protein", day.protein, goals.protein, N2Blue)
                N2MacroBar("Carbs", day.carbs, goals.carbs, N2Cyan)
                N2MacroBar("Fat", day.fat, goals.fat, N2Amber)
                N2MacroBar("Fibre", day.fibre, goals.fibre, N2Green)
            }
        }

        if (weekDays.any { it.entries.isNotEmpty() }) {
            N2WeekChart(weekDays, goals.kcal, selectedDate, onSelectDate)
        }
    }
}

@Composable
private fun N2CalorieRing(value: Double, target: Double?) {
    val fraction = if (target != null && target > 0.0) (value / target).toFloat().coerceIn(0f, 1f) else 0f
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            drawArc(
                color = N2Border,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (fraction > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(N2Blue, N2Cyan, N2Blue)),
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.roundToInt().toString(), color = N2Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("kcal", color = N2Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            if (target != null) {
                Text("of " + target.roundToInt(), color = N2Muted, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun N2MacroBar(label: String, value: Double, target: Double?, accent: Color) {
    val fraction = if (target != null && target > 0.0) (value / target).toFloat().coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = N2Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text(
                if (target != null) n2One(value) + " / " + n2One(target) + " g" else n2One(value) + " g",
                color = N2Ink,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(N2Border)) {
            if (target != null && fraction > 0f) {
                Box(Modifier.fillMaxWidth(fraction).height(5.dp).background(accent, CircleShape))
            } else if (value > 0.0) {
                Box(Modifier.fillMaxWidth(.35f).height(5.dp).background(accent.copy(alpha = .7f), CircleShape))
            }
        }
    }
}

@Composable
private fun N2WeekChart(
    days: List<N2Day>,
    target: Double?,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit
) {
    val maxValue = maxOf(
        days.maxOfOrNull { it.kcal } ?: 0.0,
        target ?: 0.0,
        1.0
    )
    Column(
        Modifier.fillMaxWidth().background(N2RowBg, RoundedCornerShape(18.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("7-day intake", color = N2Ink, fontSize = 12.sp, fontWeight = FontWeight.Black)
                val logged = days.count { it.entries.isNotEmpty() }
                Text(logged.toString() + " of 7 days logged", color = N2Muted, fontSize = 8.sp)
            }
            val average = days.filter { it.entries.isNotEmpty() }.map { it.kcal }.average().takeIf { !it.isNaN() }
            if (average != null) {
                Text(average.roundToInt().toString() + " avg", color = N2Blue, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
        Row(
            Modifier.fillMaxWidth().height(74.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEachIndexed { index, item ->
                val h = ((item.kcal / maxValue).coerceIn(0.0, 1.0) * 52.0).coerceAtLeast(if (item.kcal > 0) 5.0 else 2.0)
                val chartDate = selectedDate.minusDays((days.lastIndex - index).toLong())
                Column(
                    Modifier.weight(1f).clickable { onSelectDate(chartDate) }.padding(horizontal = 1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(h.dp)
                            .background(
                                if (index == days.lastIndex) N2Blue else N2Cyan.copy(alpha = .55f),
                                RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)
                            )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        chartDate.dayOfWeek.name.take(1),
                        color = if (index == days.lastIndex) N2Ink else N2Muted,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Add food", color = N2Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("Search, scan or import a label", color = N2Muted, fontSize = 9.sp)
            }
            Text("FAST LOG", color = N2Blue, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
        }
        OutlinedTextField(query, onQueryChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Food, brand or meal") })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            N2ActionButton("⌕", if (searching) "Searching" else "Search", N2Green, Modifier.weight(1f), !searching, onSearch)
            N2ActionButton("▥", "Barcode", N2Blue, Modifier.weight(1f), true, onScan)
            N2CameraActionButton("Photo", N2Purple, Modifier.weight(1f), onPhoto)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                if (showManualBarcode) "Hide manual barcode" else "Enter barcode manually",
                color = N2Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onToggleManualBarcode).padding(vertical = 6.dp)
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
private fun N2CameraActionButton(
    label: String,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier.height(88.dp)
            .background(accent.copy(alpha = .13f), RoundedCornerShape(16.dp))
            .border(1.dp, accent.copy(alpha = .30f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.tabler_camera),
            contentDescription = "Photo",
            tint = accent,
            modifier = Modifier.size(21.dp)
        )
        Text(label, color = N2Ink, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun N2ActionButton(
    icon: String,
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
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icon, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(label, color = if (enabled) N2Ink else N2Muted, fontSize = 8.sp, fontWeight = FontWeight.Black)
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
private fun N2Results(foods: List<NativeFood>, selectedId: String?, onSelect: (NativeFood) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(24.dp)).border(1.dp, N2Border, RoundedCornerShape(24.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text("Results", color = N2Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
        foods.take(10).forEach { food ->
            Row(
                Modifier.fillMaxWidth().background(if (food.id == selectedId) N2SoftGreen else N2RowBg, RoundedCornerShape(14.dp))
                    .clickable { onSelect(food) }.padding(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                N2FoodThumb(food.name, 38)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(food.name, color = N2Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    if (food.hasVerifiedEnglishName && food.originalName.isNotBlank() && !food.originalName.equals(food.name, ignoreCase = true)) {
                        Text(food.originalName, color = N2Muted, fontSize = 8.sp, maxLines = 1)
                    }
                    Text(
                        buildString {
                            if (food.brand.isNotBlank()) append(food.brand).append(" · ")
                            append(food.source)
                        }, color = N2Muted, fontSize = 9.sp
                    )
                    Text(
                        "${food.kcal.roundToInt()} kcal · ${n2One(food.protein)}P · ${n2One(food.carbs)}C · ${n2One(food.fat)}F per ${food.unit}",
                        color = N2Muted, fontSize = 9.sp
                    )
                }
                Text("+", color = N2Green, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun N2AddFoodCard(
    food: NativeFood,
    portion: String,
    onPortionChange: (String) -> Unit,
    meal: String,
    onMealChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    val amount = portion.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val factor = amount / 100.0
    Column(
        Modifier.fillMaxWidth().background(N2SoftGreen, RoundedCornerShape(24.dp)).border(1.dp, N2Green.copy(alpha = .24f), RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            N2FoodThumb(food.name, 48)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(food.name, color = N2Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                if (food.hasVerifiedEnglishName && food.originalName.isNotBlank() && !food.originalName.equals(food.name, ignoreCase = true)) {
                    Text(food.originalName, color = N2Muted, fontSize = 9.sp, maxLines = 1)
                }
                Text(food.brand.ifBlank { food.source }, color = N2Muted, fontSize = 10.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            N2FoodStat((food.kcal * factor).roundToInt().toString(), "kcal", Modifier.weight(1f))
            N2FoodStat("${n2One(food.protein * factor)}g", "protein", Modifier.weight(1f))
            N2FoodStat("${n2One(food.carbs * factor)}g", "carbs", Modifier.weight(1f))
            N2FoodStat("${n2One(food.fat * factor)}g", "fat", Modifier.weight(1f))
        }
        OutlinedTextField(portion, onPortionChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Portion (g / ml)") })
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

private fun n2FoodEmoji(name: String): String {
    val n = name.lowercase()
    return when {
        listOf("yoghurt", "yogurt", "milk", "cheese", "feta", "skyr").any { it in n } -> "🥛"
        listOf("banana", "apple", "berry", "berries", "fruit", "kiwi", "orange").any { it in n } -> "🍎"
        listOf("chicken", "turkey", "beef", "pork", "ham", "steak").any { it in n } -> "🍗"
        listOf("salmon", "tuna", "fish", "shrimp").any { it in n } -> "🐟"
        listOf("egg", "omelette").any { it in n } -> "🥚"
        listOf("rice", "pasta", "noodle", "grain", "oat", "granola").any { it in n } -> "🍚"
        listOf("bread", "toast", "sandwich", "wrap").any { it in n } -> "🥪"
        listOf("lentil", "bean", "pea", "vegetable", "salad", "tomato").any { it in n } -> "🥗"
        else -> "🍽"
    }
}

@Composable
private fun N2FoodThumb(name: String, sizeDp: Int) {
    Box(
        Modifier.size(sizeDp.dp).background(N2SoftBlue, RoundedCornerShape((sizeDp / 3).dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(n2FoodEmoji(name), fontSize = (sizeDp / 2).sp)
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
            Text("Quick repeat", color = N2Ink, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text("Recent foods", color = N2Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            entries.distinctBy { it.foodId }.take(4).forEach { entry ->
                Column(
                    Modifier.width(148.dp).background(N2Surface, RoundedCornerShape(16.dp))
                        .border(1.dp, N2Border, RoundedCornerShape(16.dp))
                        .clickable { onRepeat(entry) }.padding(11.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        N2FoodThumb(entry.name, 34)
                        Spacer(Modifier.width(8.dp))
                        Text(entry.name, color = N2Ink, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        entry.kcal.roundToInt().toString() + " kcal · " + n2One(entry.grams) + " g",
                        color = N2Muted,
                        fontSize = 8.sp
                    )
                    Spacer(Modifier.height(5.dp))
                    Text("+ Add again", color = N2Blue, fontSize = 8.sp, fontWeight = FontWeight.Black)
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
            Text("Diary", color = N2Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(
                day.entries.size.toString() + " items · " + day.kcal.roundToInt().toString() + " kcal",
                color = N2Muted,
                fontSize = 9.sp,
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

    Column(
        Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(20.dp))
            .border(1.dp, N2Border, RoundedCornerShape(20.dp)).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(meal, color = N2Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text(
                    entries.size.toString() + " items · " + n2One(mealProtein) + " g protein",
                    color = N2Muted,
                    fontSize = 8.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(mealKcal.roundToInt().toString() + " kcal", color = N2Ink, fontSize = 10.sp, fontWeight = FontWeight.Black)
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
                            Column(
                                Modifier.weight(1f).clickable { editingGroupName = true }.padding(vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(groupName, color = N2Ink, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    Text("✎", color = N2Blue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(groupEntries.size.toString() + " ingredients", color = N2Muted, fontSize = 7.sp)
                            }
                            Text(groupKcal.roundToInt().toString() + " kcal", color = N2Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                groupEntries.forEach { entry ->
                    Row(
                        Modifier.fillMaxWidth().background(N2RowBg, RoundedCornerShape(13.dp))
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        N2FoodThumb(entry.name, 34)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, color = N2Ink, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Text(
                                n2One(entry.grams) + " g · " + entry.kcal.roundToInt().toString() + " kcal · " +
                                    n2One(entry.protein) + "P · " + n2One(entry.carbs) + "C · " + n2One(entry.fat) + "F",
                                color = N2Muted,
                                fontSize = 8.sp,
                                maxLines = 1
                            )
                        }
                        Text(
                            "＋",
                            color = N2Blue,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.clickable { onDuplicate(entry) }.padding(7.dp)
                        )
                        Text(
                            "×",
                            color = N2Muted,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onRemove(entry) }.padding(7.dp)
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(17.dp))
                .border(1.dp, N2Border, RoundedCornerShape(17.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            N2Range.entries.forEach { item ->
                val active = item == range
                Box(
                    Modifier.weight(1f)
                        .background(if (active) N2Blue else Color.Transparent, RoundedCornerShape(13.dp))
                        .clickable { onRangeChange(item) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.label, color = if (active) Color.White else N2Muted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(24.dp)).border(1.dp, N2Border, RoundedCornerShape(24.dp)).padding(17.dp),
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
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                gaugeNutrients.forEach { micro -> N2NutrientGauge(micro, range.days.toInt()) }
            }
        }

        Column(
            Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(24.dp)).border(1.dp, N2Border, RoundedCornerShape(24.dp)).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(24.dp)).border(1.dp, N2Border, RoundedCornerShape(24.dp)).padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
        Modifier.width(104.dp).background(N2Surface, RoundedCornerShape(18.dp))
            .border(1.dp, N2Border, RoundedCornerShape(18.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                drawArc(N2Border, -90f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                if (fraction > 0f) {
                    drawArc(accent, -90f, 360f * fraction, false, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Text((fraction * 100).roundToInt().toString() + "%", color = N2Ink, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Text(micro.label, color = N2Ink, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(n2Pretty(value) + " " + micro.unit, color = N2Muted, fontSize = 8.sp)
    }
}

@Composable
private fun N2FoodContributors(entries: List<N2Entry>) {
    val top = entries.sortedByDescending { it.kcal }.take(3)
    val max = top.maxOfOrNull { it.kcal }?.coerceAtLeast(1.0) ?: 1.0
    Column(
        Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(22.dp))
            .border(1.dp, N2Border, RoundedCornerShape(22.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Top contributors", color = N2Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("Calories", color = N2Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        top.forEach { entry ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                N2FoodThumb(entry.name, 34)
                Spacer(Modifier.width(9.dp))
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
    Row(Modifier.fillMaxWidth().background(N2RowBg, RoundedCornerShape(14.dp)).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Column(Modifier.fillMaxWidth().background(N2RowBg, RoundedCornerShape(14.dp)).padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(24.dp)).border(1.dp, N2Border, RoundedCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("Today", color = N2Ink, fontSize = 19.sp, fontWeight = FontWeight.Black)
            N2Insight(
                if (entryCount == 0) "Start with one meal" else "$entryCount foods logged",
                if (entryCount == 0) "Your food diary is the evidence layer for future cross-module patterns."
                else "${day.kcal.roundToInt()} kcal · ${n2One(day.protein)} g protein · ${n2One(day.fibre)} g fibre",
                N2Blue
            )
            N2Insight(
                if (microCoverage >= 75) "Good nutrient visibility" else "Nutrient picture is partial",
                "$microCoverage% of today's foods contain at least one micronutrient value.",
                if (microCoverage >= 75) N2Green else N2Amber
            )
        }

        Column(
            Modifier.fillMaxWidth().background(N2Surface, RoundedCornerShape(24.dp)).border(1.dp, N2Border, RoundedCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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

        Column(Modifier.fillMaxWidth().background(N2SoftBlue, RoundedCornerShape(20.dp)).padding(15.dp)) {
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
    Row(Modifier.fillMaxWidth().background(N2RowBg, RoundedCornerShape(14.dp)).padding(11.dp), verticalAlignment = Alignment.Top) {
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
    Column(modifier.background(N2RowBg, RoundedCornerShape(14.dp)).padding(10.dp)) {
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    val kcalRows = foodRows.filter { it.metric == "food_kcal" }
    val groups = kcalRows.groupBy { row -> row.metadata["diaryEntryId"] ?: "legacy:${row.timestampEpochMs}:${row.metadata["foodId"].orEmpty()}" }

    fun metric(entryId: String, metric: String, fallback: String? = null): Double {
        val direct = foodRows.firstOrNull { row ->
            val id = row.metadata["diaryEntryId"] ?: "legacy:${row.timestampEpochMs}:${row.metadata["foodId"].orEmpty()}"
            id == entryId && row.metric == metric
        }?.value
        if (direct != null) return direct
        return fallback?.let { key -> groups[entryId]?.firstOrNull()?.metadata?.get(key)?.toDoubleOrNull() } ?: 0.0
    }

    val entries = groups.mapNotNull { (entryId, group) ->
        val kcal = group.maxByOrNull { it.timestampEpochMs } ?: return@mapNotNull null
        N2Entry(
            id = entryId,
            foodId = kcal.metadata["foodId"].orEmpty(),
            timestamp = kcal.timestampEpochMs,
            name = kcal.metadata["name"] ?: "Food",
            grams = kcal.metadata["grams"]?.toDoubleOrNull() ?: 100.0,
            meal = kcal.metadata["meal"] ?: "Other",
            kcal = kcal.value,
            protein = metric(entryId, "food_protein"),
            carbs = metric(entryId, "food_carbs", "carbs"),
            fat = metric(entryId, "food_fat", "fat"),
            fibre = metric(entryId, "food_fibre", "fibre"),
            micronutrientCount = foodRows.count { row ->
                row.metadata["diaryEntryId"] == entryId && !row.metadata["nutrientId"].isNullOrBlank()
            },
            barcode = kcal.metadata["barcode"].orEmpty(),
            sourceName = kcal.metadata["sourceName"].orEmpty(),
            mealGroupId = kcal.metadata["mealGroupId"].orEmpty(),
            mealGroupName = kcal.metadata["mealGroupName"].orEmpty()
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
private fun String.n2FirstNumber(): Double? = Regex("([0-9]+(?:[.,][0-9]+)?)").find(this)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
