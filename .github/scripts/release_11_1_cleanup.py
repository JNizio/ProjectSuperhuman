from pathlib import Path
import re

body_path = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NativeBodyParity.kt')
gradle_path = Path('nextgen/androidApp/build.gradle.kts')
manifest_path = Path('nextgen/androidApp/src/main/AndroidManifest.xml')

body = body_path.read_text()

# Remove quick-log state.
for line in [
    '    var weight by remember { mutableStateOf("") }\n',
    '    var bodyFat by remember { mutableStateOf("") }\n',
    '    var waist by remember { mutableStateOf("") }\n',
    '    var goal by remember { mutableStateOf("") }\n',
    '    var status by remember { mutableStateOf("") }\n',
    '    var logging by remember { mutableStateOf(false) }\n',
]:
    body = body.replace(line, '')

# Remove manual save function.
body = re.sub(r'\n    fun save\(\) \{.*?\n    \}\n\n    val latestWeight', '\n    val latestWeight', body, flags=re.S)

# Hero no longer exposes + LOG.
body = body.replace(
'''        BodyHero(\n            snapshot = snapshot,\n            weightChange = weightChange,\n            goalDelta = goalDelta,\n            onLog = { logging = !logging }\n        )''',
'''        BodyHero(\n            snapshot = snapshot,\n            weightChange = weightChange,\n            goalDelta = goalDelta\n        )''')

# Remove the entire quick-log card from the page.
body = re.sub(r'\n        if \(logging\) \{\n            QuickLogCard\(.*?\n        \}\n\n        when \(view\)', '\n\n        when (view)', body, flags=re.S)

# Measurements become display-only; body composition is populated by the scale.
body = body.replace('MeasurementsCard(snapshot = snapshot, onLog = { logging = true })', 'MeasurementsCard(snapshot = snapshot)')

# BodyHero signature and + LOG button.
body = body.replace(
'''private fun BodyHero(\n    snapshot: BodySnapshot,\n    weightChange: Double?,\n    goalDelta: Double?,\n    onLog: () -> Unit\n)''',
'''private fun BodyHero(\n    snapshot: BodySnapshot,\n    weightChange: Double?,\n    goalDelta: Double?\n)''')
body = re.sub(r'\n            Box\(\n                Modifier\.background\(Color\.White\.copy\(alpha = \.16f\), RoundedCornerShape\(14\.dp\)\)\.clickable\(onClick = onLog\)\.padding\(horizontal = 14\.dp, vertical = 11\.dp\)\n            \) \{\n                Text\("\+ LOG".*?\n            \}', '', body, flags=re.S)

# Remove obsolete QuickLogCard function entirely.
body = re.sub(r'\n@Composable\nprivate fun QuickLogCard\(.*?\n\}\n\n@Composable\nprivate fun ProgressTrendCard', '\n@Composable\nprivate fun ProgressTrendCard', body, flags=re.S)

# Remove Measurements EDIT action and outdated placeholder copy.
body = body.replace('private fun MeasurementsCard(snapshot: BodySnapshot, onLog: () -> Unit) {', 'private fun MeasurementsCard(snapshot: BodySnapshot) {')
body = re.sub(r'\n            Text\("EDIT", color = BodyBlue, fontSize = 9\.sp, fontWeight = FontWeight\.Black, modifier = Modifier\.clickable\(onClick = onLog\)\)', '', body)
body = body.replace(
'''        Spacer(Modifier.height(8.dp))\n        Text(\n            "Smart-scale composition metrics will plug into this view later without changing the core layout.",\n            color = BodyMuted,\n            fontSize = 8.sp,\n            lineHeight = 12.sp\n        )''',
'''        Spacer(Modifier.height(8.dp))\n        Text(\n            "Smart Scale Sync updates supported composition metrics automatically after each complete scan.",\n            color = BodyMuted,\n            fontSize = 8.sp,\n            lineHeight = 12.sp\n        )''')

body_path.write_text(body)

gradle = gradle_path.read_text()
gradle = re.sub(r'versionCode = \d+', 'versionCode = 11100', gradle)
gradle = re.sub(r'versionName = "[^"]+"', 'versionName = "11.1"', gradle)
gradle_path.write_text(gradle)

manifest = manifest_path.read_text()
if 'android:roundIcon=' not in manifest:
    manifest = manifest.replace('android:icon="@drawable/icon"', 'android:icon="@drawable/icon"\n        android:roundIcon="@drawable/icon"')
manifest_path.write_text(manifest)

print('Applied Project Superhuman 11.1 cleanup and branding enforcement')
