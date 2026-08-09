package com.projectsuperhuman.next

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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

private val NutritionBlue = Color(0xFF0D6CB4)
private val NutritionInk = Color(0xFF0B1F35)
private val NutritionMuted = Color(0xFF64748B)
private val NutritionGreen = Color(0xFF168A78)
private val NutritionBg = Color(0xFFF6F9FC)
private val NutritionSoft = Color(0xFFECF8F1)

private data class TodayNutrition(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val items: List<com.projectsuperhuman.next.core.HealthValue> = emptyList()
)

@Composable
fun NativeNutritionPage(onBack: () -> Unit, openLegacy: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<NativeFood>>(emptyList()) }
    var selected by remember { mutableStateOf<NativeFood?>(null) }
    var gramsText by remember { mutableStateOf("100") }
    var status by remember { mutableStateOf("Local food database ready") }
    var today by remember { mutableStateOf(TodayNutrition()) }

    suspend fun refreshToday() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val from = cal.timeInMillis
        val to = System.currentTimeMillis() + 1
        val kcalRows = NativeDataHub.between("food_kcal", from, to)
        val proteinRows = NativeDataHub.between("food_protein", from, to)
        today = TodayNutrition(kcalRows.sumOf { it.value }, proteinRows.sumOf { it.value }, kcalRows.sortedByDescending { it.timestampEpochMs })
    }

    LaunchedEffect(Unit) {
        NativeFoodCatalog.all(context)
        refreshToday()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(NutritionBg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ModuleHeader("Nutrition", "Food, macros, micronutrients & products", onBack)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NutritionMetric("Calories", today.kcal.roundToInt().toString(), "kcal", Modifier.weight(1f))
            NutritionMetric("Protein", oneDecimal(today.protein), "g", Modifier.weight(1f))
            NutritionMetric("Foods", today.items.size.toString(), "today", Modifier.weight(1f))
        }

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp)) {
            Text("Add food", color = NutritionInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("Search the bundled UK/Poland food database directly in the native app.", color = NutritionMuted, fontSize = 10.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search food") })
            Spacer(Modifier.height(9.dp))
            ActionButton("Search foods", "Local database · no WebView required", NutritionGreen) {
                scope.launch {
                    results = NativeFoodCatalog.search(context, query)
                    selected = null
                    status = if (results.isEmpty()) "No local matches" else "${results.size} matches"
                }
            }
            if (results.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                results.take(8).forEach { food ->
                    FoodResult(food, selected?.id == food.id) { selected = food; gramsText = "100" }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(NutritionSoft, RoundedCornerShape(22.dp)).padding(16.dp)) {
            Text("Barcode products", color = NutritionGreen, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("Manual EAN/UPC lookup now calls Open Food Facts natively. Camera/image scanning keeps using the proven native scanner bridge during migration.", color = NutritionMuted, fontSize = 10.sp, lineHeight = 14.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SmallAction("Live scan", Modifier.weight(1f), openLegacy)
                SmallAction("Scan image", Modifier.weight(1f), openLegacy)
            }
            Spacer(Modifier.height(9.dp))
            OutlinedTextField(barcode, { barcode = it.filter(Char::isDigit).take(13) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("EAN / UPC barcode") })
            Spacer(Modifier.height(9.dp))
            ActionButton("Look up barcode", "Native Open Food Facts lookup", NutritionGreen) {
                scope.launch {
                    status = "Looking up product…"
                    val product = NativeFoodCatalog.lookupBarcode(barcode)
                    if (product == null) status = "Product not found or network unavailable"
                    else {
                        selected = product
                        results = listOf(product)
                        gramsText = "100"
                        status = "Product found"
                    }
                }
            }
        }

        selected?.let { food ->
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp)) {
                Text(food.name, color = NutritionInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("${food.kcal.roundToInt()} kcal · ${oneDecimal(food.protein)} g protein · ${oneDecimal(food.carbs)} g carbs · ${oneDecimal(food.fat)} g fat per 100 g", color = NutritionMuted, fontSize = 10.sp, lineHeight = 14.sp)
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(gramsText, { gramsText = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Portion (g / ml equivalent)") })
                Spacer(Modifier.height(9.dp))
                ActionButton("Add to today", "Save directly to the shared nutrition database", NutritionBlue) {
                    scope.launch {
                        val grams = gramsText.toDoubleOrNull()?.coerceIn(1.0, 5000.0) ?: 100.0
                        NativeDataHub.saveFood(food, grams)
                        status = "Added ${food.name}"
                        refreshToday()
                    }
                }
            }
        }

        Text(status, color = NutritionMuted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 2.dp))

        if (today.items.isNotEmpty()) {
            Text("TODAY'S DIARY", color = NutritionMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            today.items.take(12).forEach { row ->
                val name = row.metadata["name"] ?: "Food"
                val grams = row.metadata["grams"]?.toDoubleOrNull()?.roundToInt()
                NutritionRow(name, "${row.value.roundToInt()} kcal${grams?.let { " · $it g" } ?: ""}", "FO", NutritionGreen) { }
            }
        }

        Text("NUTRITION TOOLS", color = NutritionMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        NutritionRow("Micronutrients", "Vitamins, minerals and coverage", "MI", NutritionGreen, openLegacy)
        NutritionRow("Helpful compounds", "Functional compounds and food sources", "HC", Color(0xFF6547C9), openLegacy)
        NutritionRow("Legacy nutrition tools", "Advanced saved foods and remaining specialist views", "LG", Color(0xFFD97706), openLegacy)

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun FoodResult(food: NativeFood, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (selected) NutritionSoft else NutritionBg, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(food.name, color = NutritionInk, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text("${food.kcal.roundToInt()} kcal · P ${oneDecimal(food.protein)} · C ${oneDecimal(food.carbs)} · F ${oneDecimal(food.fat)}", color = NutritionMuted, fontSize = 9.sp)
        }
        Text("+", color = NutritionGreen, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

private fun oneDecimal(value: Double): String = ((value * 10.0).roundToInt() / 10.0).toString()

@Composable
private fun ModuleHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(42.dp).height(42.dp).background(Color.White, RoundedCornerShape(13.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Text("‹", color = NutritionBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column { Text(title, color = NutritionInk, fontSize = 24.sp, fontWeight = FontWeight.Black); Text(subtitle, color = NutritionMuted, fontSize = 10.sp) }
    }
}

@Composable
private fun NutritionMetric(title: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(18.dp)).padding(13.dp)) {
        Text(title.uppercase(), color = NutritionMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.Bottom) { Text(value, color = NutritionInk, fontSize = 18.sp, fontWeight = FontWeight.Black); Spacer(Modifier.width(3.dp)); Text(unit, color = NutritionMuted, fontSize = 9.sp) }
    }
}

@Composable
private fun ActionButton(title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(accent, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold); Text(subtitle, color = Color.White.copy(alpha = .8f), fontSize = 9.sp) }
        Text("›", color = Color.White, fontSize = 22.sp)
    }
}

@Composable
private fun SmallAction(title: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.background(Color.White, RoundedCornerShape(15.dp)).clickable(onClick = onClick).padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
        Text(title, color = NutritionGreen, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun NutritionRow(title: String, subtitle: String, initials: String, accent: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(42.dp).height(42.dp).background(accent.copy(alpha = .10f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text(initials, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, color = NutritionInk, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold); Text(subtitle, color = NutritionMuted, fontSize = 9.sp) }
        Text("›", color = accent, fontSize = 22.sp)
    }
}
