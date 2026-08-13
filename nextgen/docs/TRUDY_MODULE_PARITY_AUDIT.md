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

`NativeDomainData` is the preferred Android UI/module read facade. It is constructed with exactly one `HealthDomain` and exposes:

- latest metric
- latest state for the domain
- bounded metric history
- paged domain history
- paged metric history
- complete paged history for one domain when preserving existing semantics genuinely requires all rows
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

`NativeHydration.kt` uses `NativeDomainData(HYDRATION)` for `hydration_goal_ml`, `water_intake_ml`, `water_total_l`, bounded visible history and the post-write daily total calculation. Signed correction semantics and writes are unchanged.

### Body

`NativeBodyParity.kt` uses `NativeDomainData(BODY)` for bounded weight, body-fat and waist history. `BodyDashboardAdvanced.kt` uses the same domain facade for selectable composition trends. Existing explicitly domain-scoped latest-state reads used by Body/OKOK remain valid.

### Mindfulness / Breathwork

`NativeMindfulnessParity.kt` reads `mindfulness_session_minutes` through `NativeDomainData(MINDFULNESS)`. Breathwork remains a separate user-facing surface but continues writing its compatibility session record into `MINDFULNESS`.

### Exercise / Training

`NativeExerciseParity.kt` reads completed `exercise_set` history through `NativeDomainData(EXERCISE)`. `ActiveWorkoutStore` remains transient crash/resume state only.

### Nutrition

The shell-routed `NativeNutritionExperienceV2Page` was already explicitly Nutrition-domain scoped for its health-history reads. Diary writes still use `saveFood`, linked `diaryEntryId` rows remain intact, and the food catalogue/local override stores remain reference/product data rather than longitudinal health storage.

## Prompt 2 production migration status

### Clinical

`NativeClinicalParity.kt` now owns a `NativeDomainData(CLINICAL)` reader.

The following production reads are domain safe:

- latest Clinical results -> `clinicalData.latestState()`
- remembered reference range for an OCR draft -> `clinicalData.latest(draft.metric)`
- duplicate comparison source -> `clinicalData.allHistory()`

The previous OCR duplicate path loaded the application-wide archive and filtered to Clinical afterward. The replacement pages through only the Clinical domain. Duplicate matching itself is unchanged: same metric ID, numeric value tolerance, case-normalised unit, `rangeLow`, and `rangeHigh`. There was no timestamp criterion in the existing duplicate predicate, so none was invented during migration.

Open-ended `clinical.*` metrics remain supported. The ICD-11 conditions catalogue remains a separate reference/search database and is not longitudinal user-health storage.

### Sleep

The current branch was already safer than the earlier audit suggested. The routed Sleep production readers contain no application-wide archive scan:

- `NativeSleepStore.loadLatest()` uses `latestForDomain(SLEEP)` and domain-taking `pageForMetric(SLEEP, ...)` calls.
- The latest dashboard keeps the existing 32-row `sleep_total_minutes` and `sleep_score` histories used for recent averages.
- `NativeHistoricalSleepStore.loadLatestWakeDate()` uses a one-row Sleep metric query.
- `NativeHistoricalSleepStore.loadMonth()` uses `domainBetween(SLEEP, from, to)` rather than an all-domain archive.

The history screen intentionally queries the requested month plus a **10-day baseline lookback**. That lookback is preserved because the historical dashboard uses prior nights for baseline/personal context; replacing it with an arbitrary shorter window would change interpretation behavior.

Sleep reconstruction, analysis, personal-model and intelligence classes remain algorithms over supplied Sleep data rather than alternate persistence layers. Health Connect import semantics were not changed.

### Home aggregation

`NativeLiveHome.kt` remains a read-only cross-domain aggregator. It is deliberately not assigned to a fake Home `HealthDomain`.

The Home snapshot now creates explicit readers for:

- `SLEEP`
- `HYDRATION`
- `NUTRITION`
- `EXERCISE`
- `BODY`
- `CLINICAL`
- `MINDFULNESS`

Former metric-only reads for food, hydration, workouts and body history now go through their owning domain facade. Latest-state semantics for Sleep, Body, Clinical and Mindfulness are preserved through `latestState()` rather than by imposing a row cap. This specifically preserves the old Home behavior, including the fact that Mindfulness Home minutes were calculated from the latest value per Mindfulness metric rather than summing every session row.

Home remains read-only and creates no derived persistence store.

## Remaining production migration debt

The target areas for Prompt 2 no longer require normal metric-only or application-wide archive reads.

Remaining intentionally separate work:

- `NativeSettingsParity.kt` may use archive-wide reads for backup/export/administrative operations. These are not normal module reads.
- Compatibility overloads inside `NativeDataHub` remain available until the final cleanup pass, so older non-routed/reference code is not broken opportunistically.
- The final cleanup should search all remaining production/reference callers before deprecating or removing helpers.

## Domain audit

### Clinical

- Longitudinal lab results are shared `CLINICAL` `HealthValue` rows.
- OCR/import UI models are capture/review models, not persistence.
- Open-ended `clinical.*` marker IDs remain supported.
- OCR range recall and duplicate detection are now Clinical-domain scoped.
- ICD-11 remains reference data.

### Blood Pressure

- The domain participates in all five parity surfaces.
- Canonical systolic, diastolic and pulse IDs are reserved in `CoreMetricRegistry` with legacy aliases.
- Native capture remains deferred; moving that capture path is a separate product migration.

### Body

- Smart-scale/body observations write into `BODY`.
- UI snapshots remain presentation adapters.
- Normal routed Body history/trends are domain scoped.

### Sleep

- Sleep history lives in `SLEEP`; Health Connect remains the source adapter.
- `sleep_time_minutes` remains a compatibility alias of `sleep_total_minutes` at the shared metric boundary.
- Routed current-night and historical Sleep readers are explicitly domain scoped and bounded to the existing required ranges.

### Nutrition

- Food diary writes are linked first-class Data Vault rows.
- The shell-routed V2 screen uses explicit Nutrition-domain reads.
- Food catalogue/reference storage remains separate from logged nutrition history.

### Hydration

- Signed intake events, compatibility totals and goals live in `HYDRATION`.
- Normal routed reads use `NativeDomainData(HYDRATION)`.

### Exercise

- Completed workouts/sets and imported activity observations live in `EXERCISE`.
- `ActiveWorkoutStore` remains transient recovery state only.
- Normal routed training history is domain scoped.

### Mindfulness

- Session and self-report values live in `MINDFULNESS`.
- Breathwork currently reuses the mindfulness session metric for compatibility.
- Normal routed mindfulness history is domain scoped.

## Domain-isolation regression coverage

`ModuleParityTest` covers:

- all domains exposing the same five surfaces
- empty history/data-gap state
- multi-metric state
- stable pagination
- cautious trend generation
- stale data quality
- read-time alias canonicalisation without rewriting stored rows
- identical metric strings in different domains without leakage
- signed Hydration corrections/history
- Exercise set-history metadata
- linked Nutrition diary metadata
- open-ended Clinical metric IDs remaining visible only in the Clinical port
- Sleep alias canonicalisation and paged Sleep history ordering/domain isolation

Home's production aggregator itself now makes domain ownership explicit in code rather than relying on metric-name uniqueness. A later extraction into a platform-independent Home query service would make mixed-domain snapshot logic directly common-testable, but that extraction is not required for this conservative migration and was intentionally not introduced as unrelated refactoring.

## Cross-cutting findings

### Direct SQL access

No current native module screen instantiates `SqlHealthRepository`. SQL ownership remains centralised in the shared data layer and Android `NativeDataHub`.

### Compatibility helpers

Metric-only and archive-wide compatibility helpers remain defined until the final cleanup pass. New normal feature code should not add callers to them.

### Bespoke read models

Sleep, Body, Nutrition, Exercise, Home and other feature-rich screens may keep presentation snapshots/history structures. They are acceptable when they transform Data Vault observations for presentation. Trudy must not depend on those Android classes.

## Trudy boundary

Trudy should receive `ModuleParitySnapshot` (or individual five-surface calls) plus bounded cross-domain query/insight services. It should not know about Compose screens, Health Connect record classes, SQLDelight queries, OCR models, smart-scale protocol objects or transient workout state.

A stable rule follows:

> If a health domain writes canonical `HealthValue` observations through the ingestion pipeline and exposes a domain `ModuleDataPort`, it participates in the common Trudy-facing architecture.

## Final cleanup order

1. Search the entire routed production tree for remaining metric-only/global compatibility reads.
2. Classify administrative/export, migration/reference, and genuine production callers separately.
3. Deprecate or remove compatibility helpers only when no legitimate production caller depends on them.
4. Run shared tests and Android compilation when execution infrastructure is available.
5. Build the Experiment Engine and Trudy only against shared contracts, never module UI classes.
