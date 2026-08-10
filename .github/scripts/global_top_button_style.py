from pathlib import Path
import re

root = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next')
changed = []

for path in root.glob('*.kt'):
    s = path.read_text()
    before = s

    # Standard one-line top back/profile controls.
    s = re.sub(
        r'Modifier[^\n]*\.(?:clickable|superhumanClickable)\(onClick = onBack\)',
        'Modifier.superhumanTopButton(onClick = onBack)',
        s,
    )
    s = re.sub(
        r'Modifier[^\n]*\.(?:clickable|superhumanClickable)\(onClick = onProfile\)',
        'Modifier.superhumanTopButton(onClick = onProfile)',
        s,
    )

    # Multiline home/settings cog tile in NativeTopBar.
    s = re.sub(
        r'Modifier\.width\(48\.dp\)\.height\(48\.dp\)\s*'
        r'\.clip\(RoundedCornerShape\(17\.dp\)\)\s*'
        r'\.background\(Color\.White\)\s*'
        r'\.border\(1\.dp, Color\(0xFFE1E8EE\), RoundedCornerShape\(17\.dp\)\)\s*'
        r'\.superhumanClickable\(onClick = onSettings\)',
        'Modifier.superhumanTopButton(onClick = onSettings)',
        s,
        flags=re.S,
    )

    # Any remaining compact one-line settings/header actions should share the same surface.
    s = re.sub(
        r'Modifier[^\n]*\.(?:clickable|superhumanClickable)\(onClick = onSettings\)',
        'Modifier.superhumanTopButton(onClick = onSettings)',
        s,
    )

    if s != before:
        path.write_text(s)
        changed.append(str(path))

print('Global top-button style applied to:')
for p in changed:
    print(' -', p)
