# Project Superhuman — Data Vault Architecture

## Canonical mental model

**Module Engines → Data Vault → Interpretation Engine**

This is the simple model to preserve as the application grows.

Internally, the implementation is deliberately more structured:

**Input Source → Decoder → Validation/Normalisation/Deduplication → ModuleDataPort → Data Vault → Query/Aggregation Layer → Interpretation Engine**

External sources (Health Connect, OCR imports, Open Food Facts, scale/watch integrations, future sensors) feed the appropriate module/data port through the ingestion boundary. They do not become separate permanent data silos.

## Why this exists

Project Superhuman is expected to retain years of heterogeneous personal health data and may eventually contain millions or tens of millions of observations. The architecture must stay understandable and fast without requiring any developer to hold every module in context at once.

## Hard rules

1. **The Data Vault is the source of truth.** Modules own behaviour and interpretation specific to their domain, not separate private databases.
2. **No new module creates its own storage universe.** New modules receive a domain-scoped `ModuleDataPort`.
3. **Normal UI and engine code must not load the whole archive.** `allValues()` is reserved for export, backup and controlled migration work.
4. **Queries are bounded.** Use domain + metric + time ranges, latest values, pagination, or aggregates.
5. **Interpretation reads through a dedicated read boundary.** It must not reach into module implementation details.
6. **Heavy interpretation runs off the UI thread and should be incremental.** New data invalidates only affected summaries/insights.
7. **Raw data is retained where useful; summaries accelerate repeated work.** Daily/weekly aggregates can answer long-history questions without rescanning raw sensor rows.
8. **Every observation keeps provenance.** Domain, metric, unit, timestamp, source and source record identity remain attached to the value.
9. **Schema evolution must be non-destructive.** Existing user histories survive upgrades.
10. **Data belongs to the user.** Architecture should continue to support local-first storage and portable backup/export.
11. **Known metrics use the canonical Metric Registry.** Metric IDs, canonical units, aggregation behaviour and safe storage bounds live in one place.
12. **Never relabel a numeric unit without converting its value.** Incompatible known units are rejected; unregistered metrics are preserved rather than guessed.
13. **Backup restore bypasses ingestion normalisation.** A backup must restore the user's original stored semantics exactly.

## Current storage backbone

The shared SQLDelight database stores `health_value` observations plus clinical ranges, scientific insights, daily aggregates, import checkpoints and migration logs.

Important indexes include:

- `(metric, timestamp)`
- `(domain, timestamp)`
- `(domain, metric, timestamp)`
- `(domain, source, timestamp)`
- source/source-record deduplication

The composite domain indexes are also installed at runtime with `CREATE INDEX IF NOT EXISTS`, so existing installs gain the performance improvement without clearing app data.

## Ingestion boundary

`DataIngestionPipeline` is the standard path for new observations:

1. Source/platform decoder emits `HealthValue` candidates.
2. Basic validity checks reject blank metric IDs, non-finite values and invalid timestamps.
3. `CoreMetricRegistry` resolves known metrics and aliases.
4. Known compatible units are converted to canonical units with the numeric value converted at the same time.
5. Known safe storage bounds are checked.
6. Provenance metadata records the ingestion pipeline and any original metric/unit/value that was normalised.
7. Duplicate records inside the incoming batch are collapsed.
8. The batch is grouped by `HealthDomain` and written through the matching `ModuleDataPort`.
9. The Data Vault's persistent source/source-record identity remains the final deduplication boundary.

Unknown metrics are not discarded. This is particularly important for Clinical imports: a new NHS laboratory marker can be preserved exactly and registered later without losing the user's result.

## Metric Registry

`CoreMetricRegistry` is the canonical catalogue for stable app-owned metrics. A `MetricDefinition` describes:

- canonical metric ID;
- owning `HealthDomain`;
- canonical unit;
- aliases;
- aggregation strategy (`LAST`, `SUM`, `AVERAGE`, `MIN_MAX_AVG`, `NONE`);
- whether the metric is derived;
- optional safe storage bounds.

Do not scatter new app-owned metric semantics through UI code. Add stable metrics to the registry when a module owns their meaning. Dynamic external metrics may remain unregistered until a reliable catalogue exists.

## Scale strategy

### Hot path

Screens and module engines query only the rows they need. Examples:

- latest weight
- sleep rows for a selected month
- ferritin results over five years
- today's nutrition events
- last 30 days of mood observations

### Warm path

Daily/weekly aggregates answer trend questions such as averages, min/max, sums, first/last and baseline comparisons.

### Cold path

Full raw-history scans are allowed only for deliberate operations such as export, backup, migration, repair, or one-off maintenance.

## Interpretation Engine access

The Interpretation Engine should think in requests rather than database scans:

- "Give me sleep continuity for the last 30 days"
- "Give me reflux symptom observations and meal timing for the same window"
- "Give me the user's ferritin trajectory over two years"
- "Give me aggregate exercise load by week"

The Query/Aggregation layer decides whether that request is best served from raw observations or precomputed summaries.

## Module boundary

A module should know:

- its own domain behaviour;
- its registered metric/data contracts;
- its platform/source decoder;
- its `ModuleDataPort`.

A module should **not** need to know:

- the SQL schema;
- how another module persists data;
- how the Interpretation Engine works internally;
- how millions of historical rows are physically organised.

## Developer breadcrumb

When adding a feature:

1. Decide which `HealthDomain` owns it.
2. Define/reuse a stable metric in `CoreMetricRegistry` when the app owns that metric's semantics.
3. Decode source data into `HealthValue` candidates without writing to SQL.
4. Send new observations through `DataIngestionPipeline` / the Data Vault gateway.
5. Read with the smallest useful bounded query.
6. Add an aggregate only if repeated long-range calculation justifies it.
7. Do not introduce `allValues()` into a normal screen, module engine or interpretation path.
8. Keep source/provenance metadata and stable source record IDs whenever available.
9. Never invent or silently change an external unit.
10. Add a migration/compatibility path before changing persisted semantics.

This file is the architectural breadcrumb for future Project Superhuman development.
