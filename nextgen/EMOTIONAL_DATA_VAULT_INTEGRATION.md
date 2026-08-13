# Emotional Data Vault integration (Agent 3)

## Scope

This change makes Emotional observations a first-class Data Vault domain without creating a second storage system or a competing Emotional model hierarchy.

Agent 1 owns the canonical Emotional models. Agent 3 owns only the persistence/evidence bridge described here.

## Storage decision

No SQL schema change is required.

The existing `health_value` table already retains every field Emotional analysis needs:

- domain / metric identity
- numeric value
- unit
- observation timestamp
- source / provenance
- source record identity
- arbitrary metadata

The existing `daily_aggregate` table is keyed by domain + metric + day and is refreshed automatically after normal Data Vault writes. Therefore Emotional values immediately participate in bounded raw history, long-window aggregation and cross-domain analytics.

Adding `HealthDomain.EMOTIONAL` is non-destructive because domains are stored as text. Existing rows and indexes are unchanged and no user history is rewritten.

## Temporary persistence adapter

`EmotionalDataVaultAdapter` is intentionally small and persistence-only. It:

1. routes writes through `DataIngestionPipeline`;
2. always writes to `HealthDomain.EMOTIONAL`;
3. exposes latest, bounded historical and paged historical reads through the normal domain-scoped `ModuleDataPort`;
4. preserves optional scale semantics in `HealthValue.metadata`;
5. metric-scopes a supplied `sourceEventId` into `sourceRecordId`, allowing one check-in to safely emit multiple metric rows without colliding with the Data Vault's `(source, source_record_id)` uniqueness rule.

Scale metadata keys are:

- `scaleMin`
- `scaleMax`
- `scaleLowMeaning`
- `scaleHighMeaning`

The numeric unit still belongs in `HealthValue.unit` (for example `0-10`). Meaning labels are descriptive semantics only; they must not be interpreted as diagnosis or clinical severity unless Agent 1's canonical model explicitly defines that meaning.

## Evidence and analytics compatibility

No Trudy response-generation changes are required.

The existing generic stack already accepts any `HealthDomain`:

- `SqlDataVaultGateway` creates a module port for every `HealthDomain.entries` value;
- `ModuleParityService` exposes current state, history, derived features, data quality and evidence metric IDs generically;
- `TrudyHealthContextService` maps generic `HealthValue` rows to `TrudyMetricEvidence` without a domain allow-list;
- `HealthQueryEngine` supports trend, period comparison, aligned series and cross-domain correlation by domain + metric;
- `InterpretationEngine` binds those analyses to structured `EvidenceWindow` objects containing domain, metric, time range and sample count.

That is sufficient for future questions such as:

- Has anxiety changed over the last month? -> Emotional metric trend / recent-period comparison.
- Is emotional state associated with sleep? -> Emotional vs Sleep aligned series/correlation.
- Am I happier on exercise days? -> Emotional mood metric aligned/aggregated against Exercise data.
- Does mood vary with environmental conditions? -> the same cross-domain path once the separately owned Environmental domain exists.

## Agent 1 integration requirements

When Agent 1's canonical Emotional models land:

1. Map each numeric canonical observation to `EmotionalDataVaultAdapter.saveMetric(...)`; do not add another Emotional database/repository.
2. Use stable canonical metric IDs. If aliases, canonical units or accepted ranges are needed, add those definitions to `CoreMetricRegistry` once Agent 1's vocabulary is final. This Agent 3 change intentionally does not pre-empt that vocabulary.
3. Pass the original observation/check-in ID as `sourceEventId` when available. The adapter creates a metric-scoped `sourceRecordId` for safe idempotent persistence.
4. Preserve the source name and the original observation timestamp; do not replace them with UI render time or import time.
5. Supply scale bounds/meaning when the numeric value is otherwise ambiguous. A mood `7` without knowing the scale is weaker evidence than `7` on a defined 0-10 scale.
6. If Agent 1 introduces richer non-numeric Emotional context, keep the analyzable numeric dimensions as separate `HealthValue` rows and place only compact supporting semantics in metadata. Do not flatten large text objects into metric IDs.

## Existing Mindfulness mood data

`HealthDomain.MINDFULNESS` already contains metrics such as `mood_score`, `stress_before` and `stress_after`.

This change does **not** silently migrate or relabel those rows. Their historical meaning may be tied to a mindfulness session rather than a general Emotional check-in. Reclassifying them automatically could create false longitudinal evidence.

During integration, Agent 1 must explicitly decide whether any existing Mindfulness metric is semantically equivalent to a canonical Emotional metric. If it is, add a deliberate compatibility mapping/migration with provenance preserved. If it is not, leave the historical data in Mindfulness and let cross-domain analysis treat it as a separate signal.
