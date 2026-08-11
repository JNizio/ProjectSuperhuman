# Project Superhuman Data Vault Guide

> Purpose: this file is the backend handover map for future GPT/dev sessions.
> Read this before changing storage, imports, wearable pipelines, interpretation, or analytics.
>
> Branch at time of writing: `11.2`

## 1. What the Data Vault is

Project Superhuman is designed around one app-wide local health-data system rather than isolated feature silos.

Each feature/module owns its own domain logic, but all persistent health observations flow into one structured Data Vault. The Vault is the long-term source of truth for things such as:

- Sleep and sleep-stage summaries
- Nutrition and food-derived metrics
- Exercise/activity data
- Heart rate, steps, calories, distance and future wearable streams
- Clinical/lab data
- Body/scale data
- Mood/mindfulness/self-reported data
- Future environment, symptom, medication, condition and intervention data

The design goal is to support years of history and eventually millions/tens of millions of observations without forcing screens or intelligence code to scan the whole database.

## 2. Core architecture

```text
External / User Inputs
        |
        v
Source Decoder
        |
        v
Validation
        |
        v
Metric + Unit Normalisation
        |
        v
Deduplication
        |
        v
ModuleDataPort
        |
        v
DataVaultGateway
        |
        v
SQLDelight / SQLite Data Vault
        |
        +----------------------------+
        |                            |
        v                            v
Short-window raw queries     Daily aggregate queries
        |                            |
        +-------------+--------------+
                      |
                      v
              HealthQueryEngine
                      |
            +---------+----------+
            |         |          |
            v         v          v
   Interpretation  Intervention  Insights
      Engine         Engine       Engine
            \          |          /
             \         |         /
              +--------+--------+
                       |
                       v
                      UI
```

The important rule is that upper layers should not skip downward through this stack.

## 3. Non-negotiable architecture rules

### Rule A - normal modules do not talk directly to SQL

A feature should normally use a domain-scoped `ModuleDataPort` obtained through the gateway.

Do not add new feature code that calls SQLite/SQLDelight directly just because it is convenient.

Reason: this keeps modules independent of storage implementation and prevents cross-domain coupling.

### Rule B - the Interpretation layer does not get unrestricted database access

Interpretation/Insights must use `HealthQueryEngine`.

Do not pass `SqlHealthRepository`, database queries, or `allValues()` into intelligence code.

### Rule C - never use full-vault scans in normal screens or analytics

`allValues()` / `allValuesAsync()` exist only for compatibility, export, backup or controlled migration work.

Normal UI and analytics paths must use:

- domain + metric scoped queries
- time-bounded queries
- pagination
- aggregate-backed history

### Rule D - preserve raw observations

Derived metrics, interpretations and aggregates must not overwrite the original measurement history.

Raw imported/recorded values remain the evidence layer.

### Rule E - derived findings are not diagnoses

The intelligence layer produces cautious findings/hypotheses.

Correlation is association, not causation.

Before/after intervention changes do not prove that the intervention caused the outcome.

### Rule F - unknown Clinical metrics must not be discarded

The Clinical domain is intentionally permissive for previously unseen lab/test identifiers.

Do not reject a valid external NHS/clinical marker just because it is absent from the registry.

## 4. Main code locations

### Data contracts

`nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/core/DataVaultContracts.kt`

Contains the stable boundaries:

- `ModuleDataPort`
- `InterpretationDataPort`
- `DataVaultGateway`

A module port is already scoped to one `HealthDomain`.

### SQL gateway

`nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/data/SqlDataVaultGateway.kt`

This is the concrete bridge between the contracts and SQL repository.

It caches domain-specific ports and prevents a module from saving observations belonging to another domain.

### SQL repository

`nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/data/SqlHealthRepository.kt`

This owns storage operations such as:

- indexed latest-value reads
- bounded metric/domain reads
- pagination
- counts/diagnostics
- targeted deletes
- daily aggregate access/update

Do not let feature code depend on this class directly unless the operation is explicitly administrative/compatibility work.

### SQL schema

`nextgen/shared/src/commonMain/sqldelight/com/projectsuperhuman/next/db/HealthStore.sq`

Important tables include:

- `health_value` - raw canonical observations
- `clinical_range` - remembered Clinical reference ranges
- `daily_aggregate` - long-history summary layer
- `scientific_insight` - reserved/persistent derived-insight storage
- `import_checkpoint` - importer progress/checkpoint data
- `legacy_import_log` - migration bookkeeping

Important indexes include domain/metric/time and source/time paths.

### Android entry point

`nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NativeDataHub.kt`

This is the Android-facing composition root for the Vault.

It initialises:

- database
- repository
- large-history indexes
- gateway
- ingestion pipeline
- query engine
- Step 5 intelligence engines

Old compatibility helpers remain while modules are gradually migrated, but new code should prefer the modern boundaries.

## 5. Step 1 - Data Vault hardening

Step 1 converted the storage layer from small-app assumptions toward large-history behaviour.

Implemented principles:

- indexed domain/metric/time queries
- domain/source/time index support
- pagination
- targeted row/domain deletes
- bounded Sleep reads instead of whole-vault scans
- cheap record-count / latest-timestamp diagnostics
- no normal Sleep hot path using `allValuesAsync()`

Large-history rule:

> Never fetch years of records when the caller only needs a recent screen, one metric, one domain, or a small window.

## 6. Step 2 - Data Gateway and module contracts

Step 2 established the stable module-to-storage boundary.

Preferred pattern:

```text
Sleep Engine
    -> ModuleDataPort(SLEEP)
    -> DataVaultGateway
    -> SQL repository
```

The same applies to Nutrition, Exercise, Clinical, Body, Mindfulness and future domains.

A `ModuleDataPort(SLEEP)` must not be used to save Nutrition observations.

Mixed-domain restore/import compatibility operations may still use lower-level administrative paths when necessary.

## 7. Step 3 - ingestion pipeline and Metric Registry

The normal write pipeline is:

```text
source
 -> decode
 -> validate
 -> normalise metric/unit/value
 -> deduplicate
 -> route to domain port
 -> save
```

Core files:

- `MetricRegistry.kt`
- `IngestionPipeline.kt`

### Metric Registry responsibilities

For stable app-owned metrics it can define:

- canonical metric ID
- owning domain
- canonical unit
- aliases
- safe value bounds
- aggregation behaviour
- whether the value is derived

### Unit conversion rule

Never relabel a number without converting the number itself.

Example:

`8 hours` -> `480 min`

Do not turn `8 hours` into `8 min` merely by changing the unit string.

Incompatible known units should be rejected rather than silently changed.

### Unknown Clinical metrics

Unknown Clinical metrics are stored as unregistered observations rather than thrown away.

This is intentional because the app may encounter new NHS tests before the registry knows about them.

### Deduplication

Where a source supplies a stable external record identifier, use `sourceRecordId` metadata so repeated imports do not create duplicate observations.

## 8. Step 4 - Query/Aggregation Engine

Core file:

`nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/core/HealthQueryEngine.kt`

The intelligence layer asks health questions through this class instead of SQL.

Current query capabilities include:

- `summary()`
- `baseline()`
- `trend()`
- `comparePeriods()`
- `recentChange()`
- `alignedSeries()`
- `correlation()`

### Short histories

Short windows read indexed raw observations.

### Long histories

Long summary/trend windows use the incrementally maintained `daily_aggregate` layer.

This is critical for scaling: a five-year trend should not require loading every original heart-rate or wearable observation.

### Daily aggregates

New writes refresh only affected day/domain/metric aggregates.

Do not recalculate the user's full historical archive after every new observation.

### Correlation safety

High-resolution raw correlations remain time bounded.

Do not run an unrestricted raw pairwise correlation over years of high-frequency wearable streams.

Future long-history relationships should use appropriately aggregated/segmented series.

## 9. Step 5 - Interpretation, Intervention and Insights

Core file:

`nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/core/IntelligenceEngine.kt`

### InterpretationEngine

Produces structured `InterpretationFinding` objects for things such as:

- trends
- cross-metric relationships

Each finding carries:

- kind
- direction
- confidence
- exact evidence windows
- sample counts
- caveats
- generated timestamp
- engine version

Relationship language must remain observational.

Use wording like:

`X was associated with Y in the recorded observations.`

Do not automatically turn it into:

`X caused Y.`

### InterventionEngine

Evaluates a user-defined intervention using explicit pre/post windows.

Examples of future interventions:

- stop caffeine after 2 pm
- move final meal earlier
- begin a breathing routine
- change training frequency
- change sleep schedule

The engine compares the selected outcome before vs after the intervention.

If there are too few observations it must return `INSUFFICIENT_DATA` rather than inventing an effect.

Current statuses:

- `IMPROVED`
- `WORSENED`
- `NO_CLEAR_CHANGE`
- `INSUFFICIENT_DATA`

Every evaluation keeps confounding caveats.

### InsightsEngine

Ranks findings and decides whether evidence is strong enough to surface.

Weak findings should be held back.

The purpose is to avoid an app that bombards the user with every statistically noisy relationship it can find.

## 10. Raw data vs derived data

Keep these mentally separate.

### Raw observation

Example:

```text
Heart rate = 83 bpm
Timestamp = 2026-08-11 10:02
Source = Health Connect / Samsung Health
```

### Aggregate

Example:

```text
Daily resting-HR average = 61.4 bpm
```

### Derived analysis metric

Example:

```text
1-minute heart-rate recovery = 24 bpm
```

### Interpretation finding

Example:

```text
Higher daily step count has been associated with a lower resting heart rate
in the aligned observations.
Confidence: 0.68
```

Do not overwrite one category with another.

## 11. Adding a new module

When adding Heart Rate, Steps, Mood, Environment or another module, follow this order.

### 1. Choose/confirm the domain

Use an existing `HealthDomain` where appropriate. Add a new domain only if the data genuinely does not belong in an existing one.

### 2. Define stable metrics

For app-owned metrics, register canonical identifiers and units in the Metric Registry.

Examples:

```text
heart_rate_bpm
resting_heart_rate_bpm
steps
active_calories_kcal
distance_m
```

Names above are examples only; check the existing registry before creating duplicates.

### 3. Decode the source

For Health Connect / Samsung Health:

```text
Health Connect record
 -> decoder
 -> HealthValue(s)
```

Preserve useful timestamps and source record IDs.

Do not flatten rich time-series into only a daily total unless the source itself only exposes a daily total.

### 4. Send through the ingestion pipeline

Do not write decoded observations directly to SQL.

### 5. Read through the module port / query engine

Screens can use scoped module reads.

Cross-module analysis uses `HealthQueryEngine`.

### 6. Add tests

Test at least:

- decoding
- unit conversion
- duplicate handling
- domain routing
- bounded historical reads
- any derived calculation

### 7. Update this guide if architecture changes

Future GPT/dev sessions should not have to reverse-engineer major backend decisions.

## 12. Wearables and high-frequency data

For Fit3 / Samsung Health style data, preserve the richest structured timeline reasonably available from Health Connect.

Examples may include:

- heart-rate samples
- steps over intervals
- cadence
- distance
- calories
- workouts
- sleep
- future SpO2 or related streams when actually exposed

Do not assume every Health Connect record type is necessarily written by every Samsung device. Test the user's real device/source behaviour.

### Future C++ analytics slot

C++ may later be useful as a small numerical accelerator for high-volume signal/time-series calculations, for example:

- smoothing noisy HR series
- activity segmentation
- heart-rate recovery curves
- rolling statistics
- signal-quality estimation
- anomaly detection

Architecture rule:

```text
Health Connect -> Kotlin ingestion -> Data Vault
                              |
                              v
                    optional C++ analytics
                              |
                              v
                       derived metrics
                              |
                              v
                    Interpretation Engine
```

C++ should not own:

- the database
- UI
- permissions
- Health Connect integration
- module routing
- interpretation rules

It should receive numerical arrays/series and return compact derived results.

Do not introduce C++ merely because it is faster in theory. Profile real workloads first.

## 13. Backup and restore

Backup/restore is an administrative exception to normal ingestion behaviour.

Restore deliberately bypasses normalisation so historical backups are not semantically changed during restoration.

Do not route a byte-for-byte semantic restore through transformations that may rename metrics, convert units or reject old values.

## 14. Deletes and corrections

Prefer targeted deletes/updates.

Do not implement a correction by:

1. loading the entire archive
2. deleting everything
3. rewriting everything

That pattern will become unacceptable at large scale.

## 15. Performance expectations

The Data Vault is designed around the idea that the archive may eventually become very large.

Performance depends primarily on query shape, not merely record count.

Good:

```text
Sleep + sleep_score + last 30 days
```

Good:

```text
Heart rate + one workout window
```

Good:

```text
5 years of sleep trend via daily aggregates
```

Bad:

```text
load every value in every domain and filter in Kotlin
```

Bad:

```text
rebuild all historical aggregates after every new wearable sample
```

## 16. Testing / CI safety net

Workflow:

`.github/workflows/finish-data-vault-step1.yml`

Despite the old filename, this is now the permanent Data Vault architecture verification workflow.

It checks architectural guards and then runs:

1. shared regression tests
2. Android Kotlin compilation
3. full debug APK assembly
4. verification that the APK was produced

The workflow intentionally does not need to upload the APK as an Actions artifact in order to validate the backend.

This avoids repeatedly consuming GitHub artifact quota.

Before declaring a backend architecture change complete, the full verification chain should be green.

## 17. Important anti-patterns for future GPT/dev sessions

Do NOT:

- add a second unrelated health database for a new feature
- make every module invent its own storage model without a reason
- use `allValues()` in normal UI/analytics
- let Interpretation query SQL directly
- silently relabel units without converting values
- throw away unknown valid Clinical markers
- replace raw observations with derived interpretations
- claim correlation proves causation
- claim an intervention caused an improvement from a simple before/after comparison
- recalculate the full history on every insert
- discard time resolution from wearable streams unnecessarily
- introduce C++ as a new storage/orchestration layer

## 18. Mental model for future GPT chats

Think of the app as a data house:

- each module has its own room
- the Data Vault is the organised central archive
- ModuleDataPort is the authorised door for a room
- the Gateway controls access
- the ingestion pipeline cleans and labels incoming packages
- the Metric Registry is the naming/unit dictionary
- daily aggregates are fast historical indexes/summaries
- HealthQueryEngine is the research librarian
- InterpretationEngine forms cautious hypotheses
- InterventionEngine compares experiments over time
- InsightsEngine decides which findings are worth showing

A new module should plug into this house, not build another house beside it.

## 19. Current backend rebuild status

The staged backend rebuild consists of:

- Step 1: Data Vault hardening
- Step 2: Data Gateway + module contracts
- Step 3: ingestion pipeline + Metric Registry
- Step 4: query/aggregation + Interpretation API
- Step 5: Interpretation + Intervention + Insights foundation
- Step 6: this permanent architecture/handover guide

If a future chat is continuing development, read this file first, then inspect the current implementation files before changing the architecture.

## 20. When this guide must be updated

Update this document whenever a change materially alters:

- database schema
- module/gateway boundaries
- ingestion pipeline
- Metric Registry rules
- aggregation strategy
- query APIs
- intelligence engines
- wearable architecture
- C++ analytics boundary
- backup/restore semantics
- performance rules

The goal is that future GPT/dev sessions can understand the Data Vault in minutes rather than rediscovering it through dozens of files and old chat messages.
