from pathlib import Path

body = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NativeBodyParity.kt')
s = body.read_text()
needle = '        BodyViewToggle(view = view, onChange = { view = it })\n'
insert = '        NativeOkokScaleCard(onSaved = { scope.launch { refresh() } })\n\n'
if insert not in s:
    if needle not in s:
        raise SystemExit('BodyViewToggle insertion point not found')
    s = s.replace(needle, insert + needle, 1)
body.write_text(s)

gradle = Path('nextgen/androidApp/build.gradle.kts')
g = gradle.read_text()
for old_code, old_name in [(11013, '11.0.13'), (11012, '11.0.12')]:
    if f'versionCode = {old_code}' in g:
        g = g.replace(f'versionCode = {old_code}', 'versionCode = 11014')
        g = g.replace(f'versionName = "{old_name}"', 'versionName = "11.0.14"')
        break
gradle.write_text(g)
