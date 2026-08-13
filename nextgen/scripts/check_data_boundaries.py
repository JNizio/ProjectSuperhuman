#!/usr/bin/env python3
"""Guard Project Superhuman's Android health-data read boundary.

This intentionally scans routed/adjacent production source files rather than every historical
screen kept in the repository. Legacy/unrouted implementations are migration inventory, not the
contract new production code is allowed to copy.
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "androidApp" / "src" / "main" / "java" / "com" / "projectsuperhuman" / "next"

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
}

# Settings is deliberately omitted: backup/export/reset is an administrative whole-vault use case.
FORBIDDEN = {
    "unscoped latest(metric)": re.compile(r"NativeDataHub\s*\.\s*latest\s*\(\s*\""),
    "unscoped between(metric, ...)": re.compile(r"NativeDataHub\s*\.\s*between\s*\(\s*\""),
    "global archive read": re.compile(r"NativeDataHub\s*\.\s*(?:allValuesAsync|allValues)\s*\("),
    "feature SQL repository construction": re.compile(r"\bSqlHealthRepository\s*\("),
}


def main() -> int:
    missing = sorted(name for name in PRODUCTION_FILES if not (ANDROID / name).exists())
    if missing:
        raise SystemExit("Architecture guard production file list is stale; missing: " + ", ".join(missing))

    violations: list[str] = []
    for name in sorted(PRODUCTION_FILES):
        path = ANDROID / name
        text = path.read_text(encoding="utf-8")
        for label, pattern in FORBIDDEN.items():
            for match in pattern.finditer(text):
                line = text.count("\n", 0, match.start()) + 1
                violations.append(f"{path.relative_to(ROOT)}:{line}: {label}")

    if violations:
        print("Data Vault architecture boundary violations:")
        for violation in violations:
            print(f"  - {violation}")
        print("\nSingle-domain production code must use NativeDomainData(domain); cross-domain code must declare domains explicitly.")
        return 1

    print(f"Data Vault architecture guard passed for {len(PRODUCTION_FILES)} routed/adjacent production files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
