#!/usr/bin/env python3
"""Project Superhuman native UI policy: never reintroduce Android press ripples.

This is a source guard, not a screenshot test. It protects default Compose,
Material 3, and Android View press-highlight entry points. It intentionally
does not remove keyboard accessibility focus or intentional selected states.
"""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[2]
native = root / "nextgen/androidApp/src/main/java/com/projectsuperhuman/next"
theme = native / "SuperhumanTheme.kt"
interactions = native / "SuperhumanInteractions.kt"
style = root / "app/src/main/res/values/styles.xml"

failures: list[str] = []

source_theme = theme.read_text(encoding="utf-8")
source_interactions = interactions.read_text(encoding="utf-8")
source_style = style.read_text(encoding="utf-8")

if not re.search(r"LocalIndication\s+provides\s+SuperhumanNoIndication", source_theme):
    failures.append("Global foundation no-ripple indication is missing")
if not re.search(r"LocalRippleConfiguration\s+provides\s+null", source_theme):
    failures.append("Global Material 3 ripple suppression is missing")
if not re.search(
    r'<item\s+name="android:colorControlHighlight">#00000000</item>',
    source_style,
):
    failures.append("Android View colorControlHighlight must remain transparent")

for path in sorted(native.rglob("*.kt")):
    code = path.read_text(encoding="utf-8")
    # Do not mistake comments for ripple implementations.
    cleaned = re.sub(r"/\*.*?\*/|//[^\n]*", "", code, flags=re.DOTALL)
    if re.search(r"(?<![A-Za-z])(?:rememberRipple|ripple)\s*\(", cleaned):
        failures.append(f"{path.relative_to(root)}: explicit ripple call is prohibited")
    if path != theme and re.search(
        r"Local(?:RippleConfiguration|Indication)\s+provides", cleaned
    ):
        failures.append(f"{path.relative_to(root)}: don't override the no-ripple policy")
    if re.search(r"indication\s*=\s*(?!null\b)", cleaned):
        failures.append(f"{path.relative_to(root)}: clickable must not supply a highlight")
    if "collectIsPressedAsState" in code and path != interactions:
        # Press state can be useful, but custom press highlights must be reviewed.
        print(f"REVIEW {path.relative_to(root)}: pressed-state visuals require design review")

if re.search(r"drawRect\s*\(", source_interactions):
    failures.append("Shared click modifiers must never draw a rectangular press overlay")

if failures:
    print("Press highlight policy FAILED:")
    for failure in failures:
        print(f" - {failure}")
    sys.exit(1)
print("Press highlight policy OK: native Compose, Material 3, and View ripple guards present")
