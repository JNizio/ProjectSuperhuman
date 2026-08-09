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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NutritionBlue = Color(0xFF0D6CB4)
private val NutritionInk = Color(0xFF0B1F35)
private val NutritionMuted = Color(0xFF64748B)
private val NutritionGreen = Color(0xFF168A78)
private val NutritionBg = Color(0xFFF6F9FC)
private val NutritionSoft = Color(0xFFECF8F1)

@Composable
fun NativeNutritionPage(onBack: () -> Unit, openLegacy: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NutritionBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ModuleHeader("Nutrition", "Food, macros, micronutrients & products", onBack)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NutritionMetric("Calories", "—", "kcal", Modifier.weight(1f))
            NutritionMetric("Protein", "—", "g", Modifier.weight(1f))
            NutritionMetric("Water", "—", "L", Modifier.weight(1f))
        }

        Column(
            modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp)
        ) {
            Text("Add food", color = NutritionInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("Search generic foods and packaged products from one place.", color = NutritionMuted, fontSize = 10.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search food or product") }
            )
            Spacer(Modifier.height(10.dp))
            ActionButton("Search foods", "Generic + product search", NutritionGreen, openLegacy)
        }

        Column(
            modifier = Modifier.fillMaxWidth().background(NutritionSoft, RoundedCornerShape(22.dp)).padding(16.dp)
        ) {
            Text("Barcode scanner", color = NutritionGreen, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("EAN / UPC product lookup with the existing native ML Kit + ZXing scanner backend.", color = NutritionMuted, fontSize = 10.sp, lineHeight = 14.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SmallAction("Live scan", Modifier.weight(1f), openLegacy)
                SmallAction("Scan image", Modifier.weight(1f), openLegacy)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it.filter(Char::isDigit).take(13) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Enter barcode manually") }
            )
            Spacer(Modifier.height(10.dp))
            ActionButton("Look up barcode", "Open Food Facts product lookup", NutritionGreen, openLegacy)
        }

        Text("NUTRITION TOOLS", color = NutritionMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        NutritionRow("Daily diary", "Meals, portions and nutrition totals", "DI", NutritionBlue, openLegacy)
        NutritionRow("Micronutrients", "Vitamins, minerals and coverage", "MI", NutritionGreen, openLegacy)
        NutritionRow("Helpful compounds", "Functional compounds and food sources", "HC", Color(0xFF6547C9), openLegacy)
        NutritionRow("Product library", "Saved and recently scanned foods", "PL", Color(0xFFD97706), openLegacy)

        Column(
            modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp)
        ) {
            Text("Step 6 migration bridge", color = NutritionBlue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Nutrition now has a native destination and unified search/scanner entry point. The proven Open Food Facts lookup, portion logic and barcode result pipeline remain available through the existing tracker while they move behind the native screen.",
                color = NutritionMuted,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(10.dp))
            ActionButton("Open full tracker", "Use all current Nutrition features", NutritionBlue, openLegacy)
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ModuleHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.width(42.dp).height(42.dp).background(Color.White, RoundedCornerShape(13.dp)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text("‹", color = NutritionBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = NutritionInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = NutritionMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun NutritionMetric(title: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(18.dp)).padding(13.dp)) {
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
private fun ActionButton(title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(accent, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Color.White.copy(alpha = .8f), fontSize = 9.sp)
        }
        Text("›", color = Color.White, fontSize = 22.sp)
    }
}

@Composable
private fun SmallAction(title: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.background(Color.White, RoundedCornerShape(15.dp)).clickable(onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(title, color = NutritionGreen, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun NutritionRow(title: String, subtitle: String, initials: String, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(42.dp).height(42.dp).background(accent.copy(alpha = .10f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = NutritionInk, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = NutritionMuted, fontSize = 9.sp)
        }
        Text("›", color = accent, fontSize = 22.sp)
    }
}
