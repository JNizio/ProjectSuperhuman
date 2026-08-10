from pathlib import Path

body = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/OkokScaleNative.kt')
s = body.read_text()

if 'private const val EXACT_SCALE_MAC' not in s:
    s = s.replace(
        'private const val PREF_SCALE_NAME = "okok_scale_name"\n',
        'private const val PREF_SCALE_NAME = "okok_scale_name"\nprivate const val EXACT_SCALE_MAC = "28:FA:7A:4D:42:59"\nprivate const val EXACT_SCALE_NAME = "Bluetooth Scale1"\n'
    )

old_load = '''    fun loadPairing(context: Context) {\n        val p = context.getSharedPreferences(SCALE_PREFS, Context.MODE_PRIVATE)\n        syncedMac = normalizeMac(p.getString(PREF_SCALE_MAC, "").orEmpty())\n        syncedName = p.getString(PREF_SCALE_NAME, "").orEmpty()\n        status = if (syncedMac.isBlank()) "No scale synced" else "Scale synced"\n    }\n'''
new_load = '''    fun loadPairing(context: Context) {\n        syncedMac = EXACT_SCALE_MAC\n        syncedName = EXACT_SCALE_NAME\n        context.getSharedPreferences(SCALE_PREFS, Context.MODE_PRIVATE).edit()\n            .putString(PREF_SCALE_MAC, EXACT_SCALE_MAC)\n            .putString(PREF_SCALE_NAME, EXACT_SCALE_NAME)\n            .apply()\n        status = "Bluetooth Scale1 linked"\n    }\n'''
if old_load in s:
    s = s.replace(old_load, new_load)

old_sync = '''    fun startSync(context: Context) {\n        if (!prepareScanner(context)) return\n        resetSession(); mode = ScaleMode.SYNCING; scanning = true; candidates = emptyList(); measurement = null\n        status = "Searching — wake the scale by stepping on it"\n        beginScan(12_000)\n    }\n'''
new_sync = '''    fun startSync(context: Context) {\n        syncedMac = EXACT_SCALE_MAC\n        syncedName = EXACT_SCALE_NAME\n        context.getSharedPreferences(SCALE_PREFS, Context.MODE_PRIVATE).edit()\n            .putString(PREF_SCALE_MAC, EXACT_SCALE_MAC)\n            .putString(PREF_SCALE_NAME, EXACT_SCALE_NAME)\n            .apply()\n        measurement = null\n        candidates = emptyList()\n        status = "Bluetooth Scale1 synced — ready to measure"\n    }\n'''
if old_sync in s:
    s = s.replace(old_sync, new_sync)

old_measure_guard = '        if (syncedMac.isBlank()) { status = "Sync your scale first"; return }\n'
if old_measure_guard in s:
    s = s.replace(old_measure_guard, '        syncedMac = EXACT_SCALE_MAC; syncedName = EXACT_SCALE_NAME\n', 1)

s = s.replace('ScaleMode.MEASURING -> if (mac == syncedMac) consumeMeasurement(mac, result.rssi, raw)',
              'ScaleMode.MEASURING -> if (mac == EXACT_SCALE_MAC) consumeMeasurement(mac, result.rssi, raw)')

s = s.replace('Text(if (OkokScaleManager.syncedMac.isBlank()) "No scale linked" else "OKOK / Chipsea", color=Color.White, fontSize=18.sp, fontWeight=FontWeight.Black)',
              'Text("Bluetooth Scale1", color=Color.White, fontSize=18.sp, fontWeight=FontWeight.Black)')
s = s.replace('if (OkokScaleManager.syncedMac.isNotBlank()) Text("Synced • ${OkokScaleManager.syncedName} • ${OkokScaleManager.syncedMac.takeLast(8)}", color=Color.White.copy(alpha=.56f), fontSize=7.sp)',
              'Text("Linked • 28:FA:7A:4D:42:59", color=Color.White.copy(alpha=.56f), fontSize=7.sp)')
s = s.replace('Box(Modifier.background(Color.White.copy(alpha=.14f), RoundedCornerShape(14.dp)).clickable { if (OkokScaleManager.scanning) OkokScaleManager.stop() else if (OkokScaleManager.syncedMac.isBlank()) sync() else measure() }.padding(horizontal=14.dp, vertical=11.dp)) {\n                Text(if (OkokScaleManager.scanning) "CANCEL" else if (OkokScaleManager.syncedMac.isBlank()) "SYNC" else "MEASURE", color=Color.White, fontSize=9.sp, fontWeight=FontWeight.Black)\n            }',
              'Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {\n                Box(Modifier.background(Color.White.copy(alpha=.10f), RoundedCornerShape(14.dp)).clickable { sync() }.padding(horizontal=11.dp, vertical=11.dp)) { Text("SYNC", color=Color.White, fontSize=8.sp, fontWeight=FontWeight.Black) }\n                Box(Modifier.background(Color.White.copy(alpha=.16f), RoundedCornerShape(14.dp)).clickable { if (OkokScaleManager.scanning) OkokScaleManager.stop() else measure() }.padding(horizontal=13.dp, vertical=11.dp)) { Text(if (OkokScaleManager.scanning) "CANCEL" else "MEASURE", color=Color.White, fontSize=9.sp, fontWeight=FontWeight.Black) }\n            }')

# Remove candidate-picker UI and misleading change-scale action because this build is locked to the known device.
start = '        if (OkokScaleManager.syncedMac.isNotBlank() && !OkokScaleManager.scanning) Text("Change synced scale"'
idx = s.find(start)
if idx != -1:
    end_marker = '        if (m != null) {'
    end = s.find(end_marker, idx)
    if end != -1:
        s = s[:idx] + '        Text("SYNC confirms the known scale; MEASURE listens only to this MAC.", color=Color.White.copy(alpha=.62f), fontSize=7.sp)\n' + s[end:]

body.write_text(s)

gradle = Path('nextgen/androidApp/build.gradle.kts')
g = gradle.read_text()
g = g.replace('versionCode = 11015', 'versionCode = 11016')
g = g.replace('versionName = "11.0.15"', 'versionName = "11.0.16"')
gradle.write_text(g)
