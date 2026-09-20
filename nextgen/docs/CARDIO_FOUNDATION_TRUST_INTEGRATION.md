# Cardio Foundation, Data Trust & Physiology

Branch: `cardio-nof1-foundation-trust`

This branch extends the separated Cardio architecture without moving UI or sensor ownership back into `NativeCardio.kt`.

## Canonical trust model

Raw observations are retained as evidence. `CardioRawObservation` stores original timestamp/value, canonical value/unit, ingestion time, session ID, quality/exclusion state, interpolation/resampling state, processing version and structured provenance. Invalid or suspect measurements can therefore remain stored while derivations exclude them.

`CardioProvenanceCodec` normalises provenance for generic BLE, H19C, future direct wearables, Health Connect, FIT/TCX/GPX/CSV, phone GPS, manual and derived sources. Stable source/device identifiers are preserved when available.

## Metric-specific source arbitration

Use `CardioSourceArbitrator.select(metric, candidates)`. The policy is deterministic and metric-specific. It never deletes overlapping evidence. The returned `CardioSourceSelection` includes the selected source, all considered sources, the policy version and a human-readable reason suitable for later UI/Trudy explanation.

No device is globally designated “best”. For example, direct BLE is strongly preferred for live heart rate, while phone/device GPS can outrank inferred distance. Measured cycling power receives a strong priority over estimated power.

## Capability API

Use `CardioCapabilityDetector.detect(...)` once, then consumers can call:

- `supportsAerobicDecoupling()`
- `supportsRmssd()`
- `supportsPaceAtHeartRate()`
- `supportsPowerAnalysis()`
- `missing(requiredCapabilities)`

Missing data remains explicit rather than fabricated.

## Versioned physiology and zones

`CardioZoneEngine` supports HRR, HRmax, LTHR, three-zone and manual boundaries. Every `CardioPhysiologyProfile` has a revision ID, effective-from time, HRmax plus its source, resting HR, LTHR, optional sport, sport settings, zones and algorithm version.

`CardioPhysiologyRepository` persists revisions into the existing Exercise Data Vault as `cardio_physiology_profile_revision`. Sessions retain `physiologyRevisionId` and `zoneSchemeId`.

For historical analysis use `CardioZoneAnalysisResolver` with `ORIGINAL` or `CURRENT`. Recomputing with current zones does not alter the session's original revision.

## HRmax candidates

`CardioHrMaxCandidateEngine.detectCandidate()` checks multiple samples, sustained duration, gaps, plausible storage range, abrupt signal jumps and provenance. It returns a review candidate only; it never changes the physiology profile.

## Versioned derived evidence and recomputation

`CardioDerivedMetric` now carries algorithm version, confidence, required inputs, source IDs, caveat and generation time. `CardioDerivedEvidenceCodec` publishes that metadata through the existing Data Vault and marks it as Trudy-visible evidence.

`CardioRecomputationEngine` is capability-aware and produces deterministic keys from metric/session/algorithm/effective timestamp. Existing keys are skipped. A changed algorithm version produces a separate result. If required raw inputs are unavailable, the metric is reported unavailable rather than synthesized.

`CardioNof1Repository.publishRecomputed()` is the write boundary for planned recomputation output.

## Schema compatibility

`CardioSessionSchemaMigration` upgrades older decoded session schemas in memory to the current schema while recording the original version in extensions. Future schemas are not downgraded. Imported physical Data Vault identity remains preserved by `CardioPersistenceMapper`.

No destructive database migration is introduced by this branch; physiology revisions and derived evidence use the existing Data Vault model.

## Merge notes

Likely conflict hotspots are `CardioNof1Models.kt`, `CardioNof1Engine.kt`, `CardioNof1Repository.kt`, `CardioPersistenceMapper.kt`, and `MetricRegistry.kt` if other Cardio branches modify the same foundation.

The main analytics/UI/GPS/structured-workout agents should consume these APIs rather than duplicating provenance, source ranking, capability, physiology or recomputation logic.
