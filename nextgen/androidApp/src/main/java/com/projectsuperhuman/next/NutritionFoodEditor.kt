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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val FoodEditNavy = Color(0xFF123D70)
private val FoodEditBlue = Color(0xFF0D6CB4)
private val FoodEditInk = Color(0xFF16334E)
private val FoodEditMuted = Color(0xFF748294)
private val FoodEditBg = Color(0xFFF8FBFD)
private val FoodEditBorder = Color(0xFFE3EAF0)
private val FoodEditGreen = Color(0xFF4AAE91)
private val FoodEditAmber = Color(0xFFD98B2B)

/**
 * Keeps the current Nutrition experience intact and adds a persistent food-data editor as a
 * lightweight secondary surface. The actual overrides are applied inside NativeFoodCatalog, so
 * every normal search, barcode lookup and diary add sees the corrected values afterwards.
 */
@Composable
internal fun NativeNutritionWithFoodEditorPage(onBack: () -> Unit) {
    var editingFoodData by remember { mutableStateOf(false) }

    if (editingFoodData) {
        FoodNutritionEditorScreen(onBack = { editingFoodData = false })
    } else {
        Box(Modifier.fillMaxSize()) {
            NativeNutritionExperienceV2Page(onBack)
            Box(
                Modifier.align(Alignment.BottomEnd)
                    .padding(18.dp)
                    .background(FoodEditNavy, RoundedCornerShape(18.dp))
                    .clickable { editingFoodData = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("EDIT FOOD DATA", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
            }
        }
    }
}

@Composable
private fun FoodNutritionEditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<NativeFood>>(emptyList()) }
    var selected by remember { mutableStateOf<NativeFood?>(null) }
    var status by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var lookingUp by remember { mutableStateOf(false) }

    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var fibre by remember { mutableStateOf("") }
    var sugar by remember { mutableStateOf("") }
    var microValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var microQuery by remember { mutableStateOf("") }

    fun load(food: NativeFood) {
        selected = food
        barcode = food.barcode.orEmpty()
        kcal = editNumber(food.kcal)
        protein = editNumber(food.protein)
        carbs = editNumber(food.carbs)
        fat = editNumber(food.fat)
        fibre = editNumber(food.fibre)
        sugar = editNumber(food.sugar)
        microValues = food.micronutrients.mapValues { editNumber(it.value.valuePer100) }
        microQuery = ""
    }

    suspend fun refreshSelectedFromSource(food: NativeFood) {
        val fresh = if (!food.barcode.isNullOrBlank()) {
            NativeFoodCatalog.lookupBarcode(food.barcode)
        } else {
            NativeFoodCatalog.search(context, food.name, limit = 36).foods.firstOrNull { it.id == food.id }
        }
        if (fresh != null) load(fresh)
    }

    LaunchedEffect(Unit) {
        NativeFoodCatalog.all(context)
    }

    Column(
        Modifier.fillMaxSize().background(FoodEditBg).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(42.dp).height(42.dp).background(Color.White, RoundedCornerShape(14.dp))
                    .border(1.dp, FoodEditBorder, RoundedCornerShape(14.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = FoodEditNavy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Food data editor", color = FoodEditInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Correct or complete product nutrition locally", color = FoodEditMuted, fontSize = 10.sp)
            }
        }

        Column(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFFEAF4FA), Color.White)),
                RoundedCornerShape(24.dp)
            ).border(1.dp, FoodEditBlue.copy(alpha = .14f), RoundedCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("FIND A FOOD", color = FoodEditBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(
                "Search the same Project Superhuman + Open Food Facts catalogue used by the diary. Your edits stay on this device and override missing or incorrect nutrition data when the product is used again.",
                color = FoodEditMuted, fontSize = 10.sp, lineHeight = 15.sp
            )
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Food or product") })
            FoodEditButton(if (searching) "Searching…" else "Search foods", FoodEditGreen, !searching) {
                scope.launch {
                    if (query.trim().length < 2) {
                        status = "Type at least two characters"
                        return@launch
                    }
                    searching = true
                    val found = NativeFoodCatalog.search(context, query)
                    results = found.foods
                    selected = null
                    searching = false
                    status = if (results.isEmpty()) "No matching food found" else ""
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    barcode,
                    { barcode = it.filter(Char::isDigit).take(14) },
                    Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Barcode") }
                )
                Box(
                    Modifier.background(FoodEditBlue.copy(alpha = if (lookingUp) .45f else 1f), RoundedCornerShape(14.dp))
                        .clickable(enabled = !lookingUp) {
                            scope.launch {
                                val digits = barcode.filter(Char::isDigit)
                                if (digits.length !in setOf(8, 12, 13, 14)) {
                                    status = "Enter a valid EAN / UPC / GTIN"
                                    return@launch
                                }
                                lookingUp = true
                                val food = NativeFoodCatalog.lookupBarcode(digits)
                                lookingUp = false
                                if (food == null) status = "Product not found"
                                else {
                                    results = listOf(food)
                                    load(food)
                                    status = ""
                                }
                            }
                        }.padding(horizontal = 14.dp, vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (lookingUp) "…" else "LOOKUP", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        if (results.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
                    .border(1.dp, FoodEditBorder, RoundedCornerShape(22.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("RESULTS", color = FoodEditMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                results.take(10).forEach { food ->
                    Row(
                        Modifier.fillMaxWidth().background(FoodEditBg, RoundedCornerShape(14.dp))
                            .clickable { load(food) }.padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(food.name, color = FoodEditInk, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            Text(
                                listOf(food.brand, food.source).filter { it.isNotBlank() }.joinToString(" · "),
                                color = FoodEditMuted, fontSize = 8.sp
                            )
                            Text(
                                "${food.kcal.roundToInt()} kcal · ${editNumber(food.protein)}P · ${editNumber(food.carbs)}C · ${editNumber(food.fat)}F / ${food.unit}",
                                color = FoodEditMuted, fontSize = 8.sp
                            )
                        }
                        Text("EDIT", color = FoodEditBlue, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        selected?.let { food ->
            val editedAlready = FoodNutritionOverrideStore.hasOverride(context, food)
            Column(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp))
                    .border(1.dp, FoodEditBorder, RoundedCornerShape(24.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(food.name, color = FoodEditInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text(
                            "Values below are per ${food.unit.removePrefix("100 ").let { "100 $it" }}",
                            color = FoodEditMuted, fontSize = 9.sp
                        )
                    }
                    if (editedAlready) {
                        Box(FoodEditGreen.copy(alpha = .10f).let { Modifier.background(it, RoundedCornerShape(99.dp)).padding(horizontal = 9.dp, vertical = 6.dp) }) {
                            Text("LOCAL EDIT", color = FoodEditGreen, fontSize = 7.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                Text("MACROS", color = FoodEditBlue, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                FoodEditFieldRow("Calories", kcal, { kcal = numericEdit(it) }, "kcal", "Protein", protein, { protein = numericEdit(it) }, "g")
                FoodEditFieldRow("Carbs", carbs, { carbs = numericEdit(it) }, "g", "Fat", fat, { fat = numericEdit(it) }, "g")
                FoodEditFieldRow("Fibre", fibre, { fibre = numericEdit(it) }, "g", "Sugar", sugar, { sugar = numericEdit(it) }, "g")

                Box(Modifier.fillMaxWidth().height(1.dp).background(FoodEditBorder))
                Text("MICRONUTRIENTS", color = FoodEditBlue, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(
                    "Only add values you actually know from the product label or another trusted source. Leaving a nutrient absent keeps it unknown rather than zero.",
                    color = FoodEditMuted, fontSize = 9.sp, lineHeight = 13.sp
                )

                microValues.toList().sortedBy { pair ->
                    editableFoodNutrients.indexOfFirst { it.id == pair.first }.let { if (it < 0) Int.MAX_VALUE else it }
                }.forEach { (id, value) ->
                    val def = editableFoodNutrients.firstOrNull { it.id == id }
                        ?: EditableNutrientDef(id, food.micronutrients[id]?.label ?: id, food.micronutrients[id]?.unit ?: "mg")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value,
                            { changed -> microValues = microValues + (id to numericEdit(changed)) },
                            Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("${def.label} (${def.unit})") }
                        )
                        Text(
                            "Remove",
                            color = FoodEditAmber,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { microValues = microValues - id }.padding(8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    microQuery,
                    { microQuery = it },
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Add a micronutrient") },
                    placeholder = { Text("e.g. magnesium, vitamin D, iron") }
                )
                if (microQuery.trim().isNotEmpty()) {
                    val matches = editableFoodNutrients.filter { def ->
                        def.id !in microValues && (def.label.contains(microQuery.trim(), ignoreCase = true) || def.id.contains(microQuery.trim(), ignoreCase = true))
                    }.take(6)
                    matches.forEach { def ->
                        Row(
                            Modifier.fillMaxWidth().background(FoodEditBg, RoundedCornerShape(12.dp))
                                .clickable {
                                    microValues = microValues + (def.id to "")
                                    microQuery = ""
                                }.padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(def.label, color = FoodEditInk, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(def.unit, color = FoodEditMuted, fontSize = 8.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("+ ADD", color = FoodEditBlue, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                FoodEditButton("Save nutrition to local database", FoodEditGreen, true) {
                    val microMap = buildMap {
                        microValues.forEach { (id, raw) ->
                            val value = raw.toDoubleOrNull()
                            if (value == null || !value.isFinite() || value < 0.0) return@forEach
                            val def = editableFoodNutrients.firstOrNull { it.id == id }
                            val existing = food.micronutrients[id]
                            put(
                                id,
                                NativeNutrient(
                                    id = id,
                                    label = def?.label ?: existing?.label ?: id,
                                    valuePer100 = value,
                                    unit = def?.unit ?: existing?.unit ?: "mg"
                                )
                            )
                        }
                    }
                    val edited = food.copy(
                        kcal = safeNutritionNumber(kcal),
                        protein = safeNutritionNumber(protein),
                        carbs = safeNutritionNumber(carbs),
                        fat = safeNutritionNumber(fat),
                        fibre = safeNutritionNumber(fibre),
                        sugar = safeNutritionNumber(sugar),
                        micronutrients = microMap
                    )
                    FoodNutritionOverrideStore.save(context, edited)
                    val applied = FoodNutritionOverrideStore.applyAll(context, listOf(edited)).first()
                    load(applied)
                    results = results.map { if (sameFood(it, applied)) applied else it }
                    status = "Saved. This product will now use your local nutrition values."
                }

                if (editedAlready) {
                    Box(
                        Modifier.fillMaxWidth().background(FoodEditAmber.copy(alpha = .08f), RoundedCornerShape(15.dp))
                            .clickable {
                                scope.launch {
                                    FoodNutritionOverrideStore.remove(context, food)
                                    refreshSelectedFromSource(food)
                                    val current = selected
                                    if (current != null) results = results.map { if (sameFood(it, current)) current else it }
                                    status = "Local edit removed; source nutrition restored."
                                }
                            }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("RESET TO SOURCE DATA", color = FoodEditAmber, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        if (status.isNotBlank()) Text(status, color = FoodEditMuted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 2.dp))
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun FoodEditFieldRow(
    leftLabel: String,
    leftValue: String,
    onLeftChange: (String) -> Unit,
    leftUnit: String,
    rightLabel: String,
    rightValue: String,
    onRightChange: (String) -> Unit,
    rightUnit: String
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            leftValue,
            onLeftChange,
            Modifier.weight(1f),
            singleLine = true,
            label = { Text("$leftLabel ($leftUnit)") }
        )
        OutlinedTextField(
            rightValue,
            onRightChange,
            Modifier.weight(1f),
            singleLine = true,
            label = { Text("$rightLabel ($rightUnit)") }
        )
    }
}

@Composable
private fun FoodEditButton(label: String, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(accent.copy(alpha = if (enabled) 1f else .45f), RoundedCornerShape(15.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}

private fun sameFood(a: NativeFood, b: NativeFood): Boolean =
    (!a.barcode.isNullOrBlank() && a.barcode == b.barcode) || a.id == b.id

private fun numericEdit(value: String): String = value.filter { it.isDigit() || it == '.' }.take(10)

private fun safeNutritionNumber(value: String): Double =
    value.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0

private fun editNumber(value: Double): String = when {
    !value.isFinite() -> "0"
    value % 1.0 == 0.0 -> value.toLong().toString()
    else -> String.format(java.util.Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
}
