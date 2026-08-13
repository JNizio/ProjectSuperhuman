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

`NativeDomainData` is now the preferred Android UI/module read facade. It is constructed with exactly one `HealthDomain` and exposes only:

- latest metric
- bounded metric history
- paged domain history
- paged metric history
- domain record count
- parity snapshot

The facade delegates to the existing Data Vault/gateway/parity services and exposes no repository or SQLDelight classes. This gives Android feature code a simple migration target without changing write semantics.

Target architecture:

`Android module UI -> NativeDomainData(domain) -> NativeDataHub domain-scoped API -> DataVaultGateway -> SQL Data Vault`

Trudy remains separate:

`Trudy / Interpretation / Experiment Engine -> ModuleParityService / interpretation interfaces`

## Current NativeDataHub call-site audit

Repository code search identified the following normal Android callers still using compatibility-style metric-only reads:

- `NativeHydration.kt`
- `NativeBodyParity.kt`
- `BodyDashboardAdvanced.kt`
- `NativeMindfulnessParity.kt`
- `NativeExerciseParity.kt`
- `NativeNutrition.kt`
- `NativeLiveHome.kt`
- `NativeClinicalParity.kt`

Broad `allValuesAsync()` reads were found in:

- `NativeSettingsParity.kt` — administrative/settings use is compatible with an archive-wide operation.
- `NativeSleepParity.kt` — production read path; should be migrated to bounded Sleep-domain reads.
- `SleepHistoryPage.kt` — production read path; should be migrated to bounded Sleep-domain reads.
- `NativeClinicalParity.kt` has historically used a broad archive read for OCR duplicate checking; this must be reduced to Clinical-domain data.

These compatibility callers still read the same SQL Data Vault and therefore are not duplicate stores, but they remain migration debt because metric-only access cannot enforce domain isolation.

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
- Native BP UI/capture remains deferred and the proven legacy capture path is intentionally retained. Moving that capture path is a product migration task rather than a parity prerequisite.

### Body

- Smart-scale/body observations already write into the shared `BODY` domain.
- Existing body dashboard snapshot/read models remain UI adapters; they are not a second persistence layer.
- Weight/body-fat/water/muscle/waist metric vocabulary is registered centrally.
- Samsung blood-oxygen observations are also registered using the IDs already emitted by the current Health Connect importer.
- `NativeBodyParity.kt` and `BodyDashboardAdvanced.kt` remain priority migration callers for `NativeDomainData(BODY)`.

### Sleep

- Sleep history is persisted in the shared `SLEEP` domain and Health Connect import remains the source adapter.
- Reconstruction, personal-model and sleep-intelligence classes remain domain algorithms. They may consume shared data but should not become alternate stores.
- `sleep_time_minutes` is treated as a compatibility alias of `sleep_total_minutes` at the shared metric boundary.
- `NativeSleepParity.kt` and `SleepHistoryPage.kt` still contain broad archive reads and are high-priority bounded-domain migration targets.

### Nutrition

- Food diary writes already become linked first-class `HealthValue` rows through `NativeDataHub.saveFood` and the shared ingestion pipeline.
- Calories, protein, carbohydrate, fat, fibre and sugar have explicit canonical registry definitions.
- Multiple existing nutrition UI implementations are intentionally retained. Consolidating screens is separate from consolidating persistence.
- `NativeNutrition.kt` remains a metric-only read migration target; writes must stay through the existing ingestion path.

### Hydration

- Signed `water_intake_ml` events, compatibility daily totals and goals all live in the shared `HYDRATION` domain.
- Current hydration screen history is a domain-specific presentation built from Data Vault rows.
- `NativeHydration.kt` is the first recommended normal-screen migration to `NativeDomainData(HYDRATION)`, because its reads are naturally domain-contained and bounded.

### Exercise

- Completed sets/workouts and Health Connect activity/vitals write to the shared `EXERCISE` domain.
- Heart-rate and calorie IDs currently emitted by Samsung Health import are registered centrally, including compatibility aliases.
- `ActiveWorkoutStore` is intentionally retained: it is transient crash/resume state for an unfinished workout, not a competing longitudinal health archive. Completed workout data still belongs in the Data Vault.
- `NativeExerciseParity.kt` remains a metric-only read migration target.

### Mindfulness

- Session minutes and current self-report metrics write to the shared `MINDFULNESS` domain.
- Existing guided-session UI remains presentation logic.
- Breathwork currently reuses a mindfulness session metric for compatibility. Product design treats Breathwork as a separate user-facing module; adding a distinct storage domain/metric vocabulary should be deliberate schema/domain evolution rather than silently changing old records.
- `NativeMindfulnessParity.kt` remains a metric-only read migration target.

## Domain-isolation regression coverage

`ModuleParityTest` now includes a regression case that stores the exact same metric string in BODY and EXERCISE ports with different values. The test proves that each domain-scoped port and parity current-state result sees only its own value. This specifically guards against the collision risk that motivates retiring metric-only global reads.

Existing parity tests also cover:

- all current domains exposing the same five surfaces
- empty history/data-gap state
- multi-metric state
- stable pagination
- cautious trend generation
- stale data quality
- read-time legacy alias canonicalisation without rewriting stored rows

## Cross-cutting findings

### Direct SQL access

No current native module screen was found instantiating `SqlHealthRepository`. SQL ownership is centralised in the shared data layer and Android `NativeDataHub`, which is the intended boundary.

### Compatibility helpers

Metric-only `NativeDataHub.latest(metric)`, `between(metric, ...)` and archive-wide helpers remain available because removing them before all large UI callers are migrated would create unnecessary regression risk. New feature code should not add callers to them.

Migration should be performed module-by-module using `NativeDomainData`, with compile/regression validation after each group rather than an all-at-once rewrite of large Compose files.

### Bespoke read models

Sleep, Body, Nutrition, Exercise and other feature-rich screens contain bespoke UI snapshots/history structures. These are acceptable when they transform shared Data Vault observations for presentation. New cross-module consumers must not depend on those Android classes. Trudy should depend only on the shared parity/query/insight interfaces.

## Trudy boundary

Trudy should receive `ModuleParitySnapshot` (or individual five-surface calls) plus bounded cross-domain query/insight services. It should not know about Compose screens, Health Connect record classes, SQLDelight queries, OCR models, smart-scale protocol objects or transient workout state.

This gives future features one stable rule:

> If a new health domain can write canonical `HealthValue` observations through the ingestion pipeline and expose a domain `ModuleDataPort`, it automatically participates in the common Trudy-facing architecture.

## Follow-up migration order

1. Migrate `NativeHydration.kt` and Body readers to `NativeDomainData`.
2. Migrate Mindfulness, Exercise and Nutrition normal reads.
3. Replace Clinical OCR full-archive duplicate scanning with bounded/paged Clinical history.
4. Replace Sleep broad archive reads with bounded Sleep-domain history while preserving reconstruction semantics.
5. Migrate Home aggregation carefully because Home intentionally combines domains; it should use explicit per-domain readers or the interpretation layer rather than metric-only global helpers.
6. Keep Settings backup/export operations explicitly administrative and archive-wide.
7. Build the Experiment Engine and Trudy only against shared contracts; never against module UI classes.
