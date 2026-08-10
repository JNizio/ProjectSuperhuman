from pathlib import Path

body = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NativeBodyParity.kt')
s = body.read_text()

hero_tail = '''        BodyHero(
            snapshot = snapshot,
            weightChange = weightChange,
            goalDelta = goalDelta,
            onLog = { logging = !logging }
        )

        NativeOkokScaleCard(onSaved = { scope.launch { refresh() } })
'''
hero_new = '''        BodyHero(
            snapshot = snapshot,
            weightChange = weightChange,
            goalDelta = goalDelta,
            onLog = { logging = !logging }
        )

        BodyProfileSetupCard(onSaved = { scope.launch { refresh() } })

        NativeOkokScaleCard(onSaved = { scope.launch { refresh() } })
'''
if hero_tail in s and 'BodyProfileSetupCard(onSaved' not in s:
    s = s.replace(hero_tail, hero_new, 1)

old_progress = '''        when (view) {
            BodyView.PROGRESS -> {
                if (weightHistory.size >= 2) {
                    ProgressTrendCard(weightHistory, snapshot.goalKg)
                } else {
                    EmptyProgressCard(onLog = { logging = true })
                }
                RecentChangesCard(
                    weightHistory = weightHistory,
                    bodyFatHistory = bodyFatHistory,
                    waistHistory = waistHistory,
                    snapshot = snapshot
                )
            }
            BodyView.MEASUREMENTS -> {
                MeasurementsCard(snapshot = snapshot, onLog = { logging = true })
            }
        }
'''
new_progress = '''        when (view) {
            BodyView.PROGRESS -> {
                BodyOverTimeSection()
            }
            BodyView.MEASUREMENTS -> {
                MeasurementsCard(snapshot = snapshot, onLog = { logging = true })
            }
        }
'''
if old_progress in s:
    s = s.replace(old_progress, new_progress, 1)

body.write_text(s)

gradle = Path('nextgen/androidApp/build.gradle.kts')
g = gradle.read_text()
for old_code, old_name in [(11016, '11.0.16'), (11015, '11.0.15'), (11014, '11.0.14')]:
    if f'versionCode = {old_code}' in g:
        g = g.replace(f'versionCode = {old_code}', 'versionCode = 11017')
        g = g.replace(f'versionName = "{old_name}"', 'versionName = "11.0.17"')
        break
gradle.write_text(g)
