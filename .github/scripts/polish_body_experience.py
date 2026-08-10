from pathlib import Path

body = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NativeBodyParity.kt')
s = body.read_text()

# Add profile visibility state.
needle = '    var view by remember { mutableStateOf(BodyView.PROGRESS) }\n'
if 'var profileOpen by remember' not in s:
    s = s.replace(needle, needle + '    var profileOpen by remember { mutableStateOf(false) }\n', 1)

# Wire the header profile button.
s = s.replace('        BodyHeader(onBack)\n', '        BodyHeader(onBack = onBack, onProfile = { profileOpen = !profileOpen })\n', 1)

# Hide profile details behind the profile button.
s = s.replace('        BodyProfileSetupCard(onSaved = { scope.launch { refresh() } })\n\n', '        if (profileOpen) {\n            BodyProfileSetupCard(onSaved = { scope.launch { refresh() } })\n        }\n\n', 1)

# Upgrade header signature and add a clean user button.
s = s.replace('private fun BodyHeader(onBack: () -> Unit) {', 'private fun BodyHeader(onBack: () -> Unit, onProfile: () -> Unit) {', 1)
old = '''        Column(Modifier.weight(1f)) {\n            Text("Body & Progress", color = BodyInk, fontSize = 24.sp, fontWeight = FontWeight.Black)\n            Text("Your body trends, measurements & goals", color = BodyMuted, fontSize = 10.sp)\n        }\n'''
new = '''        Column(Modifier.weight(1f)) {\n            Text("Body & Progress", color = BodyInk, fontSize = 24.sp, fontWeight = FontWeight.Black)\n            Text("Your body trends, measurements & goals", color = BodyMuted, fontSize = 10.sp)\n        }\n        Box(\n            Modifier.width(44.dp).height(44.dp).background(Color.White, RoundedCornerShape(15.dp)).clickable(onClick = onProfile),\n            contentAlignment = Alignment.Center\n        ) {\n            Text("👤", fontSize = 20.sp, textAlign = TextAlign.Center)\n        }\n'''
if old in s:
    s = s.replace(old, new, 1)
else:
    raise SystemExit('Body header insertion point not found')
body.write_text(s)

# User-facing smart-scale copy.
scale = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/OkokScaleNative.kt')
q = scale.read_text()
q = q.replace('Text("SMART SCALE", color = Color.White.copy(alpha=.60f), fontSize=8.sp, fontWeight=FontWeight.Black)', 'Text("AUTOMATIC BODY SCAN", color = Color.White.copy(alpha=.60f), fontSize=8.sp, fontWeight=FontWeight.Black)')
q = q.replace('Text(TARGET_SCALE_NAME, color=Color.White, fontSize=17.sp, fontWeight=FontWeight.Black)', 'Text("Smart Scale Sync", color=Color.White, fontSize=17.sp, fontWeight=FontWeight.Black)')
q = q.replace('if (OkokScaleManager.measurement == null) Text("Step on the scale normally. Stable readings save automatically and update your dashboard.", color=Color.White.copy(alpha=.70f), fontSize=8.sp, lineHeight=12.sp)', 'if (OkokScaleManager.measurement == null) Text("Step on your scale normally. Weight and body composition sync automatically when the reading settles.", color=Color.White.copy(alpha=.70f), fontSize=8.sp, lineHeight=12.sp)')
scale.write_text(q)

# Replace the compact bar trend with an interactive point-line chart.
dash = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/BodyDashboardAdvanced.kt')
d = dash.read_text()
for imp in [
    'import androidx.compose.foundation.Canvas\n',
    'import androidx.compose.foundation.gestures.detectTapGestures\n',
    'import androidx.compose.ui.geometry.Offset\n',
    'import androidx.compose.ui.graphics.Path\n',
    'import androidx.compose.ui.input.pointer.pointerInput\n',
    'import java.text.SimpleDateFormat\n',
    'import java.util.Date\n',
    'import java.util.Locale\n',
    'import kotlin.math.roundToInt\n'
]:
    if imp not in d:
        anchor = 'import androidx.compose.foundation.background\n' if imp.startswith('import androidx.compose.foundation.') else 'import kotlin.math.abs\n'
        if imp.startswith('import androidx.compose.ui.'):
            anchor = 'import androidx.compose.ui.Alignment\n'
        elif imp.startswith('import java.'):
            anchor = 'import kotlinx.coroutines.launch\n'
        elif imp.startswith('import kotlin.math.roundToInt'):
            anchor = 'import kotlin.math.abs\n'
        d = d.replace(anchor, anchor + imp, 1)

old_chart = '''            Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {\n                history.forEach { point ->\n                    val fraction = ((point.value - min) / span).coerceIn(0.0, 1.0)\n                    Box(Modifier.weight(1f).height((18 + 70 * fraction).dp).background(DashBlue.copy(alpha = .20f), RoundedCornerShape(6.dp)))\n                }\n            }\n'''
new_chart = '''            var selectedIndex by remember(selected.metric, history.size) { mutableStateOf(history.lastIndex) }\n            val dateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }\n            val selectedPoint = history.getOrNull(selectedIndex) ?: history.last()\n            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {\n                Text(dateFormat.format(Date(selectedPoint.timestampEpochMs)), color = DashMuted, fontSize = 8.sp)\n                Text(formatMetric(selectedPoint.value, selected), color = DashBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)\n            }\n            Canvas(\n                Modifier.fillMaxWidth().height(150.dp).pointerInput(history, selected.metric) {\n                    detectTapGestures { tap ->\n                        if (history.size > 1 && size.width > 0) {\n                            val fraction = (tap.x / size.width).coerceIn(0f, 1f)\n                            selectedIndex = (fraction * (history.size - 1)).roundToInt().coerceIn(0, history.lastIndex)\n                        }\n                    }\n                }\n            ) {\n                if (history.size > 1) {\n                    val left = 6f\n                    val right = size.width - 6f\n                    val top = 12f\n                    val bottom = size.height - 16f\n                    fun xFor(i: Int) = left + (right - left) * i / (history.size - 1).toFloat()\n                    fun yFor(v: Double) = bottom - ((v - min) / span).toFloat() * (bottom - top)\n                    val path = Path()\n                    history.forEachIndexed { index, point ->\n                        val x = xFor(index); val y = yFor(point.value)\n                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)\n                    }\n                    drawPath(path, DashBlue, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))\n                    history.forEachIndexed { index, point ->\n                        val x = xFor(index); val y = yFor(point.value)\n                        drawCircle(if (index == selectedIndex) DashBlue else DashBlue.copy(alpha = .45f), radius = if (index == selectedIndex) 8f else 5f, center = Offset(x, y))\n                        if (index == selectedIndex) drawCircle(Color.White, radius = 3f, center = Offset(x, y))\n                    }\n                }\n            }\n            Text("Tap a point to inspect that reading", color = DashMuted, fontSize = 8.sp)\n'''
if old_chart not in d:
    raise SystemExit('Trend chart block not found')
d = d.replace(old_chart, new_chart, 1)
dash.write_text(d)

# Bump native app version.
gradle = Path('nextgen/androidApp/build.gradle.kts')
g = gradle.read_text()
g = g.replace('versionCode = 11017', 'versionCode = 11018')
g = g.replace('versionName = "11.0.17"', 'versionName = "11.0.18"')
gradle.write_text(g)
