from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
body = root / 'nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NativeBodyParity.kt'
dash = root / 'nextgen/androidApp/src/main/java/com/projectsuperhuman/next/BodyDashboardAdvanced.kt'
scale = root / 'nextgen/androidApp/src/main/java/com/projectsuperhuman/next/OkokScaleNative.kt'
gradle = root / 'nextgen/androidApp/build.gradle.kts'

# 1) Clean profile icon: no filled/highlighted container, draw a simple user glyph.
s = body.read_text()
if 'import androidx.compose.foundation.Canvas' not in s:
    s = s.replace('import androidx.compose.foundation.background\n', 'import androidx.compose.foundation.background\nimport androidx.compose.foundation.Canvas\n')
old = '''        Box(\n            Modifier.width(44.dp).height(44.dp).background(Color.White, RoundedCornerShape(15.dp)).clickable(onClick = onProfile),\n            contentAlignment = Alignment.Center\n        ) {\n            Text("👤", fontSize = 20.sp, textAlign = TextAlign.Center)\n        }'''
new = '''        Box(\n            Modifier.width(44.dp).height(44.dp).clickable(onClick = onProfile),\n            contentAlignment = Alignment.Center\n        ) {\n            Canvas(Modifier.width(24.dp).height(24.dp)) {\n                val c = Color(0xFF0D6CB4)\n                drawCircle(c, radius = size.minDimension * 0.18f, center = androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .31f))\n                drawArc(c, startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = androidx.compose.ui.geometry.Offset(size.width * .22f, size.height * .48f), size = androidx.compose.ui.geometry.Size(size.width * .56f, size.height * .42f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * .11f))\n            }\n        }'''
if old in s:
    s = s.replace(old, new)
body.write_text(s)

# 2) Keep the profile in its polished tile layout while editing.
s = dash.read_text()
if 'import androidx.compose.foundation.text.BasicTextField' not in s:
    s = s.replace('import androidx.compose.foundation.clickable\n', 'import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.text.BasicTextField\n')
# Remove no-longer-needed OutlinedTextField import if present.
s = s.replace('import androidx.compose.material3.OutlinedTextField\n', '')

start = s.index('        if (!editing) {')
end_marker = '            if (status.isNotBlank()) Text(status, color = DashMuted, fontSize = 8.sp)\n        }'
end = s.index(end_marker, start) + len(end_marker)
replacement = '''        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {\n            if (editing) {\n                ProfileEditMini("HEIGHT", profile.heightCm, "cm", Modifier.weight(1f)) { profile = profile.copy(heightCm = clean(it, 5)) }\n                ProfileEditMini("AGE", profile.age, "", Modifier.weight(1f)) { profile = profile.copy(age = clean(it, 3)) }\n                ProfileEditMini("GOAL", profile.goalKg, "kg", Modifier.weight(1f)) { profile = profile.copy(goalKg = clean(it, 6)) }\n            } else {\n                ProfileMini("HEIGHT", profile.heightCm.ifBlank { "—" } + if (profile.heightCm.isNotBlank()) " cm" else "", Modifier.weight(1f))\n                ProfileMini("AGE", profile.age.ifBlank { "—" }, Modifier.weight(1f))\n                ProfileMini("GOAL", profile.goalKg.ifBlank { "—" } + if (profile.goalKg.isNotBlank()) " kg" else "", Modifier.weight(1f))\n            }\n        }\n        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {\n            if (editing) {\n                Box(Modifier.weight(1f).background(Color(0xFFF6F8FB), RoundedCornerShape(13.dp)).padding(6.dp)) {\n                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {\n                        Text("SEX", color = DashMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)\n                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {\n                            SexChoice("Male", profile.male == true, Modifier.weight(1f)) { profile = profile.copy(male = true) }\n                            SexChoice("Female", profile.male == false, Modifier.weight(1f)) { profile = profile.copy(male = false) }\n                        }\n                    }\n                }\n                Box(Modifier.weight(2f)) {\n                    Column(Modifier.fillMaxWidth().background(Color(0xFFF6F8FB), RoundedCornerShape(13.dp)).clickable { activityOpen = true }.padding(10.dp)) {\n                        Text("ACTIVITY", color = DashMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)\n                        Spacer(Modifier.height(3.dp))\n                        Text(activityLabels[profile.activity] + "  ▾", color = DashInk, fontSize = 10.sp, fontWeight = FontWeight.Black)\n                    }\n                    DropdownMenu(expanded = activityOpen, onDismissRequest = { activityOpen = false }) {\n                        activityLabels.forEachIndexed { index, label ->\n                            DropdownMenuItem(text = { Text(label) }, onClick = { profile = profile.copy(activity = index); activityOpen = false })\n                        }\n                    }\n                }\n            } else {\n                ProfileMini("SEX", when(profile.male){true->"Male";false->"Female";null->"—"}, Modifier.weight(1f))\n                ProfileMini("ACTIVITY", activityLabels[profile.activity], Modifier.weight(2f))\n            }\n        }\n        if (editing) {\n            Box(Modifier.fillMaxWidth().background(DashGreen, RoundedCornerShape(15.dp)).clickable {\n                scope.launch {\n                    profile.heightCm.toDoubleOrNull()?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_height_cm", it, "cm") }\n                    profile.age.toDoubleOrNull()?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_age_years", it, "years") }\n                    profile.male?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_sex_code", if (it) 1.0 else 0.0, "code") }\n                    profile.goalKg.toDoubleOrNull()?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_goal_weight_kg", it, "kg") }\n                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_activity_level", profile.activity.toDouble(), "level")\n                    OkokScaleManager.setProfile(profile.heightCm.toDoubleOrNull(), profile.male)\n                    status = "Profile saved"\n                    editing = false\n                    onSaved()\n                }\n            }.padding(14.dp), contentAlignment = Alignment.Center) {\n                Text("SAVE PROFILE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)\n            }\n            if (status.isNotBlank()) Text(status, color = DashMuted, fontSize = 8.sp)\n        }'''
s = s[:start] + replacement + s[end:]

insert_at = s.index('@Composable\nprivate fun ProfileMini')
editor = '''@Composable\nprivate fun ProfileEditMini(label: String, value: String, unit: String, modifier: Modifier, onValue: (String) -> Unit) {\n    Column(modifier.background(Color(0xFFF6F8FB), RoundedCornerShape(13.dp)).padding(10.dp)) {\n        Text(label, color = DashMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)\n        Spacer(Modifier.height(3.dp))\n        Row(verticalAlignment = Alignment.CenterVertically) {\n            BasicTextField(\n                value = value,\n                onValueChange = onValue,\n                modifier = Modifier.weight(1f),\n                singleLine = true,\n                textStyle = androidx.compose.ui.text.TextStyle(color = DashInk, fontSize = 10.sp, fontWeight = FontWeight.Black),\n                decorationBox = { inner -> if (value.isBlank()) Text("—", color = DashMuted, fontSize = 10.sp) else inner() }\n            )\n            if (unit.isNotBlank()) Text(" $unit", color = DashMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)\n        }\n    }\n}\n\n'''
s = s[:insert_at] + editor + s[insert_at:]
dash.write_text(s)

# 3) Make one scale session save a complete composition set: wait for impedance and profile before committing.
s = scale.read_text()
s = s.replace('        if (stable) maybeAutoSave(weight, lastImpedance, onSaved)', '        if (stable && lastImpedance != null) maybeAutoSave(weight, lastImpedance, onSaved)')
s = s.replace('    private fun maybeAutoSave(weight: Double, impedance: Double?, onSaved: () -> Unit) {', '    private fun maybeAutoSave(weight: Double, impedance: Double?, onSaved: () -> Unit) {\n        if (impedance == null) return\n        val h = profileHeightCm\n        val male = profileMale\n        if (h == null || male == null) { status = "Add height and sex in Personal details to enable full body scan"; return }')
s = s.replace('                val h = profileHeightCm; val male = profileMale\n                if (h != null && male != null) OkokBiaEstimator.estimate(weight, impedance, h, male)?.let { b ->', '                OkokBiaEstimator.estimate(weight, impedance, h, male)?.let { b ->')
s = s.replace('            status = if (impedance != null) "Synced to Body & Progress" else "Weight synced"', '            status = "Full body scan synced"')
scale.write_text(s)

# Version bump.
s = gradle.read_text()
s = re.sub(r'versionCode = \\d+', 'versionCode = 11020', s)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "11.0.20"', s)
gradle.write_text(s)

print('Applied Body profile + full scale scan polish for 11.0.20')
