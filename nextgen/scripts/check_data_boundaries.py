#!/usr/bin/env python3
"""Guard Project Superhuman's production health-data read boundaries.

This intentionally scans routed/adjacent production source files rather than every historical
screen kept in the repository. Legacy/unrouted implementations are migration inventory, not the
contract new production code is allowed to copy.
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "androidApp" / "src" / "main" / "java" / "com" / "projectsuperhuman" / "next"
SHARED_TRUDY = ROOT / "shared" / "src" / "commonMain" / "kotlin" / "com" / "projectsuperhuman" / "next" / "trudy"

# Production shell routes plus the dedicated files those routes compose directly.
PRODUCTION_FILES = {
    "NextShellActivity.kt",
    "NativeHealthModules.kt",
    "NativeLifestyleModules.kt",
    "NativeClinicalParity.kt",
    "NativeBodyParity.kt",
    "BodyDashboardAdvanced.kt",
    "NativeSleepParity.kt",
    "SleepHistoryPage.kt",
    "SleepAnalysisEngine.kt",
    "SleepReconstructionEngine.kt",
    "SleepPersonalModel.kt",
    "SleepIntelligenceEngine.kt",
    "SleepNightDashboardHero.kt",
    "NativeHydration.kt",
    "HydrationCalendar.kt",
    "HydrationHeroControls.kt",
    "HydrationOrb.kt",
    "NativeNutritionExperienceV2.kt",
    "NutritionFoodEditor.kt",
    "NativeExerciseParity.kt",
    "ActiveWorkoutStore.kt",
    "NativeMindfulnessParity.kt",
    "GuidedDeepBreathRoutine.kt",
    "NativeLiveHome.kt",
    "HomeDashboardComponents.kt",
    "HomeHydrationTile.kt",
    "HomeMindfulnessBreathworkRow.kt",
    "HomeMiniMetrics.kt",
    "HomeSleepInsightTile.kt",
    "UnifiedHealthMetricPages.kt",
    "SharedDomainStatus.kt",
    "NativeDomainData.kt",
    "NativeModuleParity.kt",
    "TrudyRuntimeFactory.kt",
    "SharedTrudyBackendAdapter.kt",
    "TrudyConversationController.kt",
}

# Settings is deliberately omitted: backup/export/reset is an administrative whole-vault use case.
FORBIDDEN = {
    "unscoped latest(metric)": re.compile(r"NativeDataHub\s*\.\s*latest\s*\(\s*\""),
    "unscoped between(metric, ...)": re.compile(r"NativeDataHub\s*\.\s*between\s*\(\s*\""),
    "global archive read": re.compile(r"NativeDataHub\s*\.\s*(?:allValuesAsync|allValues)\s*\("),
    "feature SQL repository construction": re.compile(r"\bSqlHealthRepository\s*\("),
}

TRUDY_NATIVE_ACCESS = re.compile(r"NativeDataHub\s*\.\s*([A-Za-z_][A-Za-z0-9_]*)\s*\(")
TRUDY_ALLOWED_NATIVE_CALL = ("TrudyRuntimeFactory.kt", "module")


def _line(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def main() -> int:
    missing = sorted(name for name in PRODUCTION_FILES if not (ANDROID / name).exists())
    if missing:
        raise SystemExit("Architecture guard production file list is stale; missing: " + ", ".join(missing))
    if not SHARED_TRUDY.exists():
        raise SystemExit("Architecture guard cannot find shared Trudy production package")

    violations: list[str] = []
    for name in sorted(PRODUCTION_FILES):
        path = ANDROID / name
        text = path.read_text(encoding="utf-8")
        for label, pattern in FORBIDDEN.items():
            for match in pattern.finditer(text):
                violations.append(f"{path.relative_to(ROOT)}:{_line(text, match.start())}: {label}")

        if name.startswith("Trudy") or name == "SharedTrudyBackendAdapter.kt":
            for match in TRUDY_NATIVE_ACCESS.finditer(text):
                method = match.group(1)
                if (name, method) != TRUDY_ALLOWED_NATIVE_CALL:
                    violations.append(
                        f"{path.relative_to(ROOT)}:{_line(text, match.start())}: "
                        f"Trudy may access NativeDataHub only through domain-scoped module(domain) composition"
                    )

    # Shared Trudy must remain storage/provider independent: no SQL repository and no Android hub.
    for path in sorted(SHARED_TRUDY.glob("*.kt")):
        text = path.read_text(encoding="utf-8")
        for label, pattern in {
            "shared Trudy SQL repository access": re.compile(r"\bSqlHealthRepository\b"),
            "shared Trudy Android data hub access": re.compile(r"\bNativeDataHub\b"),
            "shared Trudy SQLDelight access": re.compile(r"\bSqlDataVaultGateway\b|\.sq\b|HealthStoreQueries"),
        }.items():
            for match in pattern.finditer(text):
                violations.append(f"{path.relative_to(ROOT)}:{_line(text, match.start())}: {label}")

    if violations:
        print("Data Vault architecture boundary violations:")
        for violation in violations:
            print(f"  - {violation}")
        print("\nSingle-domain production code must use a domain-scoped port; Trudy may reach NativeDataHub only at its Module Parity composition boundary.")
        return 1

    shared_count = len(list(SHARED_TRUDY.glob("*.kt")))
    print(f"Data Vault architecture guard passed for {len(PRODUCTION_FILES)} Android production files and {shared_count} shared Trudy files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
