# Trudy module parity audit

This document records the conservative architecture pass that makes the current Project Superhuman health modules available through one Trudy-facing contract without changing their visible UI or creating another health database.

## Authoritative data path

The SQLDelight Data Vault remains the single source of truth for longitudinal health observations:

`source/importer -> DataIngestionPipeline -> DataVaultGateway -> domain ModuleDataPort -> health_value`

New writes are validated, normalised to registered metric IDs/units, deduplicated and then routed through the domain-scoped module port. Cross-domain interpretation receives a read-only `InterpretationDataPort`; it does not receive unrestricted SQL access.

The Android application owns the concrete SQL repository only inside `NativeDataHub`. Native module screens do not instantiate `SqlHealthRepository` or query SQLDelight directly.

## Shared five-surface API

Every current `HealthDomain` is exposed by `ModuleParityService` / `NativeModuleParity` through the same surfaces:

1. `currentState`
2. `history`
3. `derivedFeatures`
4. `insights`
5. `dataQuality`

`ModuleParitySnapshot` combines all five for consumers such as Trudy. The parity layer canonicalises registered legacy aliases on read but deliberately does not rewrite historical SQL rows.

## Android module-facing boundary

`NativeDomainData` is the preferred Android UI/module read facade. It is constructed with exactly one `HealthDomain` and exposes only:

- latest metric
- bounded metric history
- paged domain history
- paged metric history
- domain record count
- parity snapshot

The facade delegates to the existing Data Vault/gateway/parity services and exposes no repository or SQLDelight classes.

Target architecture:

`Android module UI -> NativeDomainData(domain) -> NativeDataHub domain-scoped API -> DataVaultGateway -> SQL Data Vault`

Trudy remains separate:

`Trudy / Interpretation / Experiment Engine -> ModuleParityService / interpretation interfaces`

## Prompt 1 production migration status

The first normal-screen migration pass is complete for the straightforward routed modules.

### Hydration

`NativeHydration.kt` now uses `NativeDomainData(HYDRATION)` for:

- `hydration_goal_ml`
- `water_intake_ml`
- `water_total_l`
- the bounded visible hydration history used for the seven-day and calendar presentations
- the post-write daily water-total recomputation

Signed intake/correction rows and the existing compatibility daily total remain unchanged. Writes still use the shared ingestion path.

### Body

`NativeBodyParity.kt` now uses `NativeDomainData(BODY)` for bounded weight, body-fat and waist history. `BodyDashboardAdvanced.kt` uses the same domain facade for selectable composition trends.

The existing `latestForDomain(BODY)` calls in Body and OKOK scale setup are intentionally retained because they are already explicitly domain scoped. OKOK capture/ingestion was not changed.

### Mindfulness / Breathwork

`NativeMindfulnessParity.kt` now reads `mindfulness_session_minutes` through `NativeDomainData(MINDFULNESS)`.

The guided Breathwork routine remains a separate user-facing surface but deliberately continues writing its compatibility session record into `MINDFULNESS`. No new Breathwork storage domain was introduced in this pass.

### Exercise / Training

`NativeExerciseParity.kt` now reads completed `exercise_set` history through `NativeDomainData(EXERCISE)`.

`ActiveWorkoutStore` remains untouched because it is transient crash/resume state for an unfinished session rather than longitudinal health history. Completed sets and workout summaries still write through the Data Vault ingestion path.

### Nutrition

The routed production Nutrition screen is `NativeNutritionExperienceV2Page`, opened through `NativeNutritionWithFoodEditorPage` from `NextShellActivity`.

Its health-history reads were already explicitly domain scoped before this pass:

- current-day diary rows use `domainBetween(NUTRITION, ...)`
- nutrition target lookups use the domain-taking `latest(NUTRITION, metric)` overload

Therefore no risky whole-screen rewrite was needed. Diary writes still use `saveFood`, linked `diaryEntryId` rows remain intact, and the separate food catalogue/local override storage remains reference/product data rather than longitudinal health storage.

Older Nutrition presentation implementations remain in the repository for compatibility/reference, but they are not the shell-routed production Nutrition page and were not opportunistically refactored here.

## Remaining production migration debt

The straightforward module pass intentionally leaves the more complex readers for the next phase:

- `NativeClinicalParity.kt`: OCR duplicate detection and other Clinical reads require careful bounded Clinical-domain migration.
- `NativeSleepParity.kt` / `SleepHistoryPage.kt`: broad history access must be replaced without altering reconstruction semantics.
- `NativeLiveHome.kt` and Home-specific aggregation: Home intentionally combines several domains and should use explicit per-domain readers or the Interpretation layer rather than being forced into one domain.
- `NativeSettingsParity.kt`: archive-wide reads used for backup/export/administrative operations are intentionally allowed and must remain clearly administrative.

Metric-only compatibility helpers remain available inside `NativeDataHub` for untouched legacy callers. New normal feature code should not add new callers.

## Domain audit

### Clinical

- Longitudinal lab results are stored as `HealthValue` rows in the shared `CLINICAL` domain.
- OCR/import UI models remain domain-specific because they are capture/review models, not alternate persistence.
- Existing `clinical.*` marker IDs remain open-ended so previously unseen lab tests are not rejected by a static registry.
- Remaining migration target: replace OCR duplicate checking with Clinical-domain bounded/paged history rather than a full archive read.
- The separate ICD-11 reference catalogue is a reference/search database, not a duplicate store of the user's longitudinal health observations, and is intentionally retained.

### Blood Pressure

- The domain participates in all five parity surfaces now, including the no-data/data-quality states.
- Canonical systolic, diastolic and pulse metric IDs are reserved in `CoreMetricRegistry`, with common legacy aliases.
- Native BP UI/capture remains deferred and the proven legacy capture path is intentionally retained.

### Body

- Smart-scale/body observations write into the shared `BODY` domain.
- Existing body dashboard snapshot/read models remain UI adapters; they are not a second persistence layer.
- Weight/body-fat/water/muscle/waist metric vocabulary is registered centrally.
- Samsung blood-oxygen observations are registered using the IDs emitted by the current Health Connect importer.
- Normal routed Body history/trend reads are now domain scoped.

### Sleep

- Sleep history is persisted in the shared `SLEEP` domain and Health Connect import remains the source adapter.
- Reconstruction, personal-model and sleep-intelligence classes remain domain algorithms. They may consume shared data but should not become alternate stores.
- `sleep_time_minutes` is treated as a compatibility alias of `sleep_total_minutes` at the shared metric boundary.
- `NativeSleepParity.kt` and `SleepHistoryPage.kt` still require careful bounded-domain migration.

### Nutrition

- Food diary writes become linked first-class `HealthValue` rows through `NativeDataHub.saveFood` and the shared ingestion pipeline.
- Calories, protein, carbohydrate, fat, fibre and sugar have explicit canonical registry definitions.
- The shell-routed V2 Nutrition page already uses explicit Nutrition-domain reads.
- Multiple older nutrition UI implementations remain intentionally; consolidating screens is separate from consolidating persistence.

### Hydration

- Signed `water_intake_ml` events, compatibility daily totals and goals all live in the shared `HYDRATION` domain.
- Current hydration history is a domain-specific presentation built from Data Vault rows.
- Normal routed Hydration reads are now isolated through `NativeDomainData(HYDRATION)`.

### Exercise

- Completed sets/workouts and Health Connect activity/vitals write to the shared `EXERCISE` domain.
- Heart-rate and calorie IDs emitted by Samsung Health import are registered centrally, including compatibility aliases.
- `ActiveWorkoutStore` remains transient recovery state only.
- Normal routed training history now reads through `NativeDomainData(EXERCISE)`.

### Mindfulness

- Session minutes and current self-report metrics write to the shared `MINDFULNESS` domain.
- Existing guided-session UI remains presentation logic.
- Breathwork currently reuses a mindfulness session metric for compatibility.
- Normal routed mindfulness history now reads through `NativeDomainData(MINDFULNESS)`.

## Domain-isolation regression coverage

`ModuleParityTest` includes a regression case that stores the exact same metric string in BODY and EXERCISE ports with different values. Each domain-scoped port and parity current-state result sees only its own value.

The test suite also covers:

- all current domains exposing the same five surfaces
- empty history/data-gap state
- multi-metric state
- stable pagination
- cautious trend generation
- stale data quality
- read-time legacy alias canonicalisation without rewriting stored rows
- signed Hydration corrections/history ordering
- Exercise set-history metadata preservation
- linked Nutrition diary-history metadata preservation

## Cross-cutting findings

### Direct SQL access

No current native module screen was found instantiating `SqlHealthRepository`. SQL ownership is centralised in the shared data layer and Android `NativeDataHub`, which is the intended boundary.

### Compatibility helpers

Metric-only `NativeDataHub.latest(metric)`, `between(metric, ...)` and archive-wide helpers remain available because removing them before the remaining complex callers are migrated would create unnecessary regression risk. New feature code should not add callers to them.

### Bespoke read models

Sleep, Body, Nutrition, Exercise and other feature-rich screens contain bespoke UI snapshots/history structures. These are acceptable when they transform shared Data Vault observations for presentation. New cross-module consumers must not depend on those Android classes. Trudy should depend only on the shared parity/query/insight interfaces.

## Trudy boundary

Trudy should receive `ModuleParitySnapshot` (or individual five-surface calls) plus bounded cross-domain query/insight services. It should not know about Compose screens, Health Connect record classes, SQLDelight queries, OCR models, smart-scale protocol objects or transient workout state.

This gives future features one stable rule:

> If a new health domain can write canonical `HealthValue` observations through the ingestion pipeline and expose a domain `ModuleDataPort`, it automatically participates in the common Trudy-facing architecture.

## Follow-up migration order

1. Replace Clinical OCR full-archive duplicate scanning with bounded/paged Clinical history.
2. Replace Sleep broad archive reads with bounded Sleep-domain history while preserving reconstruction semantics.
3. Migrate Home aggregation carefully because Home intentionally combines domains; use explicit per-domain readers or the Interpretation layer.
4. Keep Settings backup/export operations explicitly administrative and archive-wide.
5. Perform the final compatibility-helper cleanup only after remaining production callers have been migrated.
6. Build the Experiment Engine and Trudy only against shared contracts; never against module UI classes.
