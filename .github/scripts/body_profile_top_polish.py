from pathlib import Path
import re

parity_path = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NativeBodyParity.kt')
dash_path = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/BodyDashboardAdvanced.kt')
gradle_path = Path('nextgen/androidApp/build.gradle.kts')

p = parity_path.read_text()

# Keep one scroll state so opening Profile can jump the user to the top immediately.
p = p.replace(
'''    var view by remember { mutableStateOf(BodyView.PROGRESS) }\n    var profileOpen by remember { mutableStateOf(false) }''',
'''    var view by remember { mutableStateOf(BodyView.PROGRESS) }\n    var profileOpen by remember { mutableStateOf(false) }\n    val scrollState = rememberScrollState()''')

p = p.replace(
'''        Modifier.fillMaxSize().background(BodyBg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),''',
'''        Modifier.fillMaxSize().background(BodyBg).verticalScroll(scrollState).padding(horizontal = 18.dp, vertical = 8.dp),''')

# Profile should appear directly below the header instead of below the hero.
old = '''        BodyHeader(onBack = onBack, onProfile = { profileOpen = !profileOpen })\n\n        BodyHero(\n            snapshot = snapshot,\n            weightChange = weightChange,\n            goalDelta = goalDelta\n        )\n\n        if (profileOpen) {\n            BodyProfileSetupCard(onSaved = { scope.launch { refresh() } })\n        }'''
new = '''        BodyHeader(onBack = onBack, onProfile = {\n            profileOpen = !profileOpen\n            if (profileOpen) scope.launch { scrollState.animateScrollTo(0) }\n        })\n\n        if (profileOpen) {\n            BodyProfileSetupCard(onSaved = { scope.launch { refresh() } })\n        }\n\n        BodyHero(\n            snapshot = snapshot,\n            weightChange = weightChange,\n            goalDelta = goalDelta\n        )'''
if old not in p:
    raise SystemExit('Body page structure pattern not found')
p = p.replace(old, new)

# Remove Android's grey press/ripple from the person button.
p = p.replace(
'''            Modifier.width(44.dp).height(44.dp).clickable(onClick = onProfile),''',
'''            Modifier.width(44.dp).height(44.dp).superhumanClickable(onClick = onProfile),''')

parity_path.write_text(p)

d = dash_path.read_text()

# Match the activity editor's height to the sex editor and align its contents cleanly.
old_activity = '''                Box(Modifier.weight(2f)) {\n                    Column(Modifier.fillMaxWidth().background(Color(0xFFF6F8FB), RoundedCornerShape(13.dp)).clickable { activityOpen = true }.padding(10.dp)) {\n                        Text("ACTIVITY", color = DashMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)\n                        Spacer(Modifier.height(3.dp))\n                        Text(activityLabels[profile.activity] + "  ▾", color = DashInk, fontSize = 10.sp, fontWeight = FontWeight.Black)\n                    }'''
new_activity = '''                Box(Modifier.weight(2f)) {\n                    Column(\n                        Modifier.fillMaxWidth().height(80.dp).background(Color(0xFFF6F8FB), RoundedCornerShape(13.dp)).superhumanClickable { activityOpen = true }.padding(horizontal = 12.dp, vertical = 10.dp),\n                        verticalArrangement = Arrangement.SpaceBetween,\n                        horizontalAlignment = Alignment.Start\n                    ) {\n                        Text("ACTIVITY", color = DashMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)\n                        Text(activityLabels[profile.activity] + "  ▾", color = DashInk, fontSize = 10.sp, fontWeight = FontWeight.Black)\n                    }'''
if old_activity not in d:
    raise SystemExit('Activity editor pattern not found')
d = d.replace(old_activity, new_activity)

# Make the sex tile use the same fixed height so the row is visually exact.
d = d.replace(
'''                Box(Modifier.weight(1f).background(Color(0xFFF6F8FB), RoundedCornerShape(13.dp)).padding(6.dp)) {''',
'''                Box(Modifier.weight(1f).height(80.dp).background(Color(0xFFF6F8FB), RoundedCornerShape(13.dp)).padding(6.dp)) {''')

dash_path.write_text(d)

g = gradle_path.read_text()
g = re.sub(r'versionCode = \d+', 'versionCode = 11102', g)
g = re.sub(r'versionName = "[^"]+"', 'versionName = "11.1.2"', g)
gradle_path.write_text(g)

print('Applied Body profile top polish 11.1.2')
