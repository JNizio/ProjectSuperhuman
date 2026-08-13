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

## Domain audit

### Clinical

- Longitudinal lab results are stored as `HealthValue` rows in the shared `CLINICAL` domain.
- OCR/import UI models remain domain-specific because they are capture/review models, not alternate persistence.
- Existing `clinical.*` marker IDs remain open-ended so previously unseen lab tests are not rejected by a static registry.
- Compatibility debt retained: the OCR duplicate-check path currently performs a broad archive read and filters Clinical rows. Replace this later with a targeted domain/metric query after the capture flow has dedicated tests.
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

### Sleep

- Sleep history is persisted in the shared `SLEEP` domain and Health Connect import remains the source adapter.
- Reconstruction, personal-model and sleep-intelligence classes remain domain algorithms. They may consume shared data but should not become alternate stores.
- `sleep_time_minutes` is treated as a compatibility alias of `sleep_total_minutes` at the shared metric boundary.

### Nutrition

- Food diary writes already become linked first-class `HealthValue` rows through `NativeDataHub.saveFood` and the shared ingestion pipeline.
- Calories, protein, carbohydrate, fat, fibre and sugar now have explicit canonical registry definitions.
- Multiple existing nutrition UI implementations are intentionally retained during this pass. Consolidating screens is separate from consolidating persistence.

### Hydration

- Signed `water_intake_ml` events, compatibility daily totals and goals all live in the shared `HYDRATION` domain.
- Current hydration screen history is a domain-specific presentation built from Data Vault rows.
- Compatibility debt retained: some Android screens still call the older unscoped `NativeDataHub.latest(metric)` / `between(metric, ...)` helpers. Those helpers still read the same SQL Data Vault, but should be progressively replaced with domain-scoped ports to eliminate any future metric-name collision risk.

### Exercise

- Completed sets/workouts and Health Connect activity/vitals write to the shared `EXERCISE` domain.
- Heart-rate and calorie IDs currently emitted by Samsung Health import are registered centrally, including compatibility aliases.
- `ActiveWorkoutStore` is intentionally retained: it is transient crash/resume state for an unfinished workout, not a competing longitudinal health archive. Completed workout data still belongs in the Data Vault.
- Exercise-specific progress/read models remain presentation/domain logic and should progressively consume parity/query services where that reduces duplicate history work.

### Mindfulness

- Session minutes and current self-report metrics write to the shared `MINDFULNESS` domain.
- Existing guided-session UI remains presentation logic.
- Breathwork currently reuses a mindfulness session metric for compatibility. Product design now treats Breathwork as a separate user-facing module; adding a distinct storage domain/metric vocabulary should be done as a deliberate schema/domain evolution rather than silently changing old records in this parity pass.

## Cross-cutting findings

### Direct SQL access

No current native module screen was found instantiating `SqlHealthRepository`. SQL ownership is centralised in the shared data layer and Android `NativeDataHub`, which is the intended boundary.

### Legacy compatibility helpers

A number of Android screens still use the unscoped compatibility helpers on `NativeDataHub`, especially home aggregation and some Body, Nutrition, Hydration, Exercise, Mindfulness and Clinical reads. They are not separate storage and therefore do not violate the single-source-of-truth rule, but they are architectural debt. Convert them incrementally to `NativeDataHub.module(domain)` / `NativeModuleParity` rather than attempting a high-risk all-at-once UI rewrite.

### Bespoke read models

Sleep, Body, Nutrition, Exercise and other feature-rich screens contain bespoke UI snapshots/history structures. These are acceptable when they transform shared Data Vault observations for presentation. New cross-module consumers must not depend on those Android classes. Trudy should depend only on the shared parity/query/insight interfaces.

## Trudy boundary

Trudy should receive `ModuleParitySnapshot` (or individual five-surface calls) plus bounded cross-domain query/insight services. It should not know about Compose screens, Health Connect record classes, SQLDelight queries, OCR models, smart-scale protocol objects or transient workout state.

This gives future features one stable rule:

> If a new health domain can write canonical `HealthValue` observations through the ingestion pipeline and expose a domain `ModuleDataPort`, it automatically participates in the common Trudy-facing architecture.

## Follow-up migration order

1. Keep converting old unscoped Android Data Vault reads to domain-scoped ports when touching those screens for normal feature work.
2. Replace Clinical's full-archive duplicate scan with a bounded targeted query after adding capture-flow regression tests.
3. Retire duplicate presentation/history calculations only where the shared query/parity engine offers the same semantics.
4. Add new domains such as Environment, Phone Usage and Mood through the same ingestion/parity contract from day one.
5. Build the Experiment Engine and Trudy only against shared contracts; never against module UI classes.
